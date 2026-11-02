package com.jm3.app.miuix.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** 自动登录凭据：只在登录成功后写入，用来在会话作废时静默重登。 */
@Serializable
data class SavedCredentials(val username: String, val password: String) {
    // 凭据可能被顺手打进日志或崩溃报告，密码不能出现在 toString 里
    override fun toString(): String = "SavedCredentials(username=$username)"
}

/** 自动登录凭据持久化契约；实现负责加密与存储。 */
interface CredentialStorage {
    suspend fun load(): SavedCredentials?
    suspend fun save(credentials: SavedCredentials)
    suspend fun clear()
}

/** 凭据与本地 JSON 互转；decode 对损坏输入返回 null。 */
object CredentialCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(credentials: SavedCredentials): String =
        json.encodeToString(SavedCredentials.serializer(), credentials)

    fun decode(raw: String): SavedCredentials? =
        runCatching { json.decodeFromString(SavedCredentials.serializer(), raw) }.getOrNull()
}

/**
 * 用 Android Keystore 的 AES-GCM 密钥加密保存登录凭据。
 *
 * 为什么需要它：jmcomic 客户端把密码用内存里的随机密钥加密后只保留在客户端实例中，
 * 进程一结束就没法自动重登，服务端会话一过期就只能让用户重新输密码。
 * 这里把凭据交给系统 Keystore 保管（密钥不出 TEE/StrongBox），落盘的只有密文，
 * 冷启动解密后再交给客户端登录，等于把「免登录」延长到凭据被用户主动清除为止。
 */
class KeystoreCredentialStore(private val context: Context) : CredentialStorage {

    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    override suspend fun load(): SavedCredentials? = withContext(Dispatchers.IO) {
        val encrypted = prefs.getString(KEY_CREDENTIALS, null) ?: return@withContext null
        val payload = runCatching { decrypt(encrypted) }.getOrNull()
        if (payload == null) {
            // 密钥没了（系统重置、锁屏方式变更、备份还原）：密文永远解不开，留着只会每次启动白算一遍
            clear()
            return@withContext null
        }
        CredentialCodec.decode(payload)
    }

    override suspend fun save(credentials: SavedCredentials) = withContext(Dispatchers.IO) {
        val payload = CredentialCodec.encode(credentials)
        prefs.edit().putString(KEY_CREDENTIALS, encrypt(payload)).apply()
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        prefs.edit().remove(KEY_CREDENTIALS).apply()
    }

    private fun encrypt(plain: String): String {
        val key = synchronized(keyLock) { secretKey() }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }
        val body = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        // GCM 的 IV 每次都要随机且随密文一起存：首字节记长度，后面依次是 IV 与密文
        val combined = ByteArray(1 + cipher.iv.size + body.size)
        combined[0] = cipher.iv.size.toByte()
        cipher.iv.copyInto(combined, destinationOffset = 1)
        body.copyInto(combined, destinationOffset = 1 + cipher.iv.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val combined = Base64.decode(encoded, Base64.NO_WRAP)
        val ivSize = combined[0].toInt()
        val iv = combined.copyOfRange(1, 1 + ivSize)
        val body = combined.copyOfRange(1 + ivSize, combined.size)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, synchronized(keyLock) { secretKey() }, GCMParameterSpec(GCM_TAG_BITS, iv))
        }
        return String(cipher.doFinal(body), Charsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFS_NAME = "jm3_credentials"
        const val KEY_CREDENTIALS = "credentials"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "jm3_credentials_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
        const val KEY_SIZE_BITS = 256
        val keyLock = Any()
    }
}
