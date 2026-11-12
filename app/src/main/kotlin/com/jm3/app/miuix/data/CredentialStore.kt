package com.jm3.app.miuix.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import com.jm3.core.CredentialCipher
import java.security.InvalidKeyException
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.UnrecoverableKeyException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal interface CredentialKeyOps {
    fun getOrCreateKey(): SecretKey
    fun regenerateKey(): SecretKey
    fun encodeBase64(bytes: ByteArray): String
    fun decodeBase64(text: String): ByteArray
}

/**
 * 用 Android Keystore 的 AES-GCM 密钥加解密登录凭据。
 *
 * 为什么需要它：jmcomic 客户端把密码用内存里的随机密钥加密后只保留在客户端实例中，
 * 进程一结束就没法自动重登，服务端会话一过期就只能让用户重新输密码。
 * 这里把凭据交给系统 Keystore 保管（密钥不出 TEE/StrongBox），落盘的只有密文，
 * 冷启动解密后再交给客户端登录，等于把「免登录」延长到凭据被用户主动清除为止。
 *
 * 落盘位置不在这里：密文经 [com.jm3.core.CredentialBlobStore] 写进 jm3_settings（ENGINEERING.md 一.5）。
 */
class KeystoreCredentialCipher internal constructor(
    private val keyOps: CredentialKeyOps,
    private val cipherFactory: () -> Cipher,
) : CredentialCipher {

    constructor() : this(
        keyOps = AndroidKeystoreKeyOps,
        cipherFactory = { Cipher.getInstance(TRANSFORMATION) },
    )

    override fun encrypt(plain: String): String {
        val cipher = initEncryptCipherWithRetry()
        val body = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        // GCM 的 IV 每次都要随机且随密文一起存：首字节记长度，后面依次是 IV 与密文
        val combined = ByteArray(1 + cipher.iv.size + body.size)
        combined[0] = cipher.iv.size.toByte()
        cipher.iv.copyInto(combined, destinationOffset = 1)
        body.copyInto(combined, destinationOffset = 1 + cipher.iv.size)
        return keyOps.encodeBase64(combined)
    }

    override fun decrypt(encrypted: String): String = try {
        val combined = keyOps.decodeBase64(encrypted)
        require(combined.isNotEmpty()) { "凭据密文为空" }
        // 长度只记在首字节里：128~255 会被 toInt() 读成负数，必须先按无符号解释
        val ivSize = combined[0].toInt() and 0xFF
        require(ivSize in 1 until combined.size) { "凭据密文损坏：IV 长度 $ivSize 超出密文长度 " + combined.size }
        val iv = combined.copyOfRange(1, 1 + ivSize)
        val body = combined.copyOfRange(1 + ivSize, combined.size)
        val cipher = cipherFactory().apply {
            init(Cipher.DECRYPT_MODE, synchronized(keyLock) { keyOps.getOrCreateKey() }, GCMParameterSpec(GCM_TAG_BITS, iv))
        }
        String(cipher.doFinal(body), Charsets.UTF_8)
    } catch (e: Exception) {
        // 上层只会把解不开的密文清掉，这条日志是事后区分「密钥没了」与「密文损坏」的唯一线索；
        // 只记密文长度与异常，不记密文本体与明文。
        Log.w(TAG, "凭据解密失败，密文长度 " + encrypted.length, e)
        throw e
    }

    /** 密钥已在 Keystore 中存在但被系统判定失效时，删旧重建并重试一次加密初始化。 */
    private fun initEncryptCipherWithRetry(): Cipher {
        val key = synchronized(keyLock) { keyOps.getOrCreateKey() }
        return try {
            cipherFactory().apply { init(Cipher.ENCRYPT_MODE, key) }
        } catch (e: Exception) {
            if (!isRecoverableKeyFailure(e)) throw e
            Log.w(TAG, "Keystore 凭据密钥已失效，删除后重建并重试加密", e)
            val regenerated = synchronized(keyLock) { keyOps.regenerateKey() }
            cipherFactory().apply { init(Cipher.ENCRYPT_MODE, regenerated) }
        }
    }

    private fun isRecoverableKeyFailure(error: Exception): Boolean =
        error is InvalidKeyException ||
            error is UnrecoverableKeyException ||
            error is KeyStoreException

    private object AndroidKeystoreKeyOps : CredentialKeyOps {

        override fun getOrCreateKey(): SecretKey {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            // 记录读不出来（系统升级、备份恢复、密钥库损坏）或类型不符时，这条密钥已经无法加解密；
            // 不自清的话此后每次加密都会失败，表现是自动登录永久静默失效，且没有任何痕迹。
            var unusable = false
            val entry = runCatching { keyStore.getEntry(KEY_ALIAS, null) }
                .getOrElse { failure ->
                    Log.w(TAG, "Keystore 凭据密钥不可读，删除后重建", failure)
                    unusable = true
                    null
                }
            (entry as? KeyStore.SecretKeyEntry)?.secretKey?.let { return it }
            if (entry != null) {
                Log.w(TAG, "Keystore 凭据密钥不可用（" + entry.javaClass.simpleName + "），删除后重建")
                unusable = true
            }
            if (unusable) deleteKeyAlias(keyStore)
            return generateSecretKey()
        }

        override fun regenerateKey(): SecretKey {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            deleteKeyAlias(keyStore)
            return generateSecretKey()
        }

        override fun encodeBase64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

        override fun decodeBase64(text: String): ByteArray = Base64.decode(text, Base64.NO_WRAP)

        private fun deleteKeyAlias(keyStore: KeyStore) {
            // 删除后旧密文再也解不开，等价于用户需要重新登录一次；这比永久静默失效可接受。
            // 删除失败不中断重建：下面 generateKey 会覆盖同名条目，真失败时由下面的日志留下痕迹。
            runCatching { keyStore.deleteEntry(KEY_ALIAS) }
                .onFailure { Log.w(TAG, "Keystore 凭据密钥旧条目删除失败，继续尝试重建", it) }
        }

        private fun generateSecretKey(): SecretKey {
            // 重建失败等于自动登录彻底断掉，必须留下明确记录，而不是只往上抛一个没头没尾的异常
            try {
                val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                generator.init(
                    KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(KEY_SIZE_BITS)
                        .build(),
                )
                return generator.generateKey()
            } catch (e: Exception) {
                Log.e(TAG, "Keystore 凭据密钥重建失败，自动登录不可用", e)
                throw e
            }
        }
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "jm3_credentials_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
        const val KEY_SIZE_BITS = 256
        const val TAG = "KeystoreCredentialCipher"
        val keyLock = Any()
    }
}
