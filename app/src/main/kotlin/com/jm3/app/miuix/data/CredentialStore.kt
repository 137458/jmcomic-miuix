package com.jm3.app.miuix.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import com.jm3.core.CredentialCipher
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

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
class KeystoreCredentialCipher : CredentialCipher {

    override fun encrypt(plain: String): String {
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

    override fun decrypt(encrypted: String): String = try {
        val combined = Base64.decode(encrypted, Base64.NO_WRAP)
        require(combined.isNotEmpty()) { "凭据密文为空" }
        // 长度只记在首字节里：128~255 会被 toInt() 读成负数，必须先按无符号解释
        val ivSize = combined[0].toInt() and 0xFF
        require(ivSize in 1 until combined.size) { "凭据密文损坏：IV 长度 $ivSize 超出密文长度 " + combined.size }
        val iv = combined.copyOfRange(1, 1 + ivSize)
        val body = combined.copyOfRange(1 + ivSize, combined.size)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, synchronized(keyLock) { secretKey() }, GCMParameterSpec(GCM_TAG_BITS, iv))
        }
        String(cipher.doFinal(body), Charsets.UTF_8)
    } catch (e: Exception) {
        // 上层只会把解不开的密文清掉，这条日志是事后区分「密钥没了」与「密文损坏」的唯一线索；
        // 只记密文长度与异常，不记密文本体与明文。
        Log.w(TAG, "凭据解密失败，密文长度 " + encrypted.length, e)
        throw e
    }

    private fun secretKey(): SecretKey {
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
        if (unusable) {
            // 删除后旧密文再也解不开，等价于用户需要重新登录一次；这比永久静默失效可接受。
            keyStore.deleteEntry(KEY_ALIAS)
        }
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
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "jm3_credentials_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
        const val KEY_SIZE_BITS = 256
        const val TAG = "KeystoreCredentialCipher"
        val keyLock = Any()
    }
}
