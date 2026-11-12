package com.jm3.app.miuix.data

import java.security.InvalidKeyException
import java.security.KeyStoreException
import java.security.UnrecoverableKeyException
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CredentialStoreTest {

    private fun aesKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    private class FakeKeyOps(initial: SecretKey) : CredentialKeyOps {
        var current: SecretKey = initial
        var regenerateCount = 0

        override fun getOrCreateKey(): SecretKey = current
        override fun regenerateKey(): SecretKey {
            regenerateCount++
            current = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
            return current
        }

        override fun encodeBase64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
        override fun decodeBase64(text: String): ByteArray = Base64.getDecoder().decode(text)
    }

    @Test
    fun `encrypt在密钥失效异常时重建密钥并重试一次后成功加解密`() {
        val failures = listOf<Exception>(
            InvalidKeyException("key invalidated"),
            UnrecoverableKeyException("unrecoverable"),
            KeyStoreException("broken entry"),
        )
        for (failure in failures) {
            val badKey = aesKey()
            val ops = FakeKeyOps(badKey)
            var attempts = 0
            val cipher = KeystoreCredentialCipher(ops) {
                val real = Cipher.getInstance("AES/GCM/NoPadding")
                if (attempts == 0) {
                    attempts++
                    // 仅在首次加密初始化时模拟 Keystore 密钥失效异常
                    throw failure
                }
                attempts++
                real
            }

            val encrypted = cipher.encrypt("user:pass")
            assertEquals(1, ops.regenerateCount, "发生 ${failure.javaClass.simpleName} 后应只重建一次密钥")
            assertEquals("user:pass", cipher.decrypt(encrypted))
        }
    }

    @Test
    fun `非密钥异常不触发重建直接抛出`() {
        val ops = FakeKeyOps(aesKey())
        val cipher = KeystoreCredentialCipher(ops) {
            throw IllegalStateException("unexpected")
        }

        assertFailsWith<IllegalStateException> { cipher.encrypt("user:pass") }
        assertEquals(0, ops.regenerateCount)
    }
}
