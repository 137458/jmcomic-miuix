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
import kotlin.test.assertNotNull

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

    @Test
    fun `并发加密遇到失效密钥时只重建一次且双方密文均可解密`() {
        val initialKey = aesKey()
        val bothInCipherInit = java.util.concurrent.CountDownLatch(2)
        val concurrentOps = object : CredentialKeyOps {
            @Volatile
            var current: SecretKey = initialKey
            val regenerations = java.util.concurrent.atomic.AtomicInteger(0)

            override fun getOrCreateKey(): SecretKey = current
            override fun regenerateKey(): SecretKey {
                regenerations.incrementAndGet()
                current = aesKey()
                return current
            }

            override fun encodeBase64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
            override fun decodeBase64(text: String): ByteArray = Base64.getDecoder().decode(text)
        }
        val concurrentCipher = KeystoreCredentialCipher(concurrentOps) {
            val real = Cipher.getInstance("AES/GCM/NoPadding")
            if (concurrentOps.regenerations.get() == 0) {
                bothInCipherInit.countDown()
                // 未持锁时两线程会同时卡到这里并双双判定密钥失效；持锁后仅首线程进入，次线程等待重建完成
                bothInCipherInit.await(80, java.util.concurrent.TimeUnit.MILLISECONDS)
                throw InvalidKeyException("expired key")
            }
            real
        }

        val startBarrier = java.util.concurrent.CyclicBarrier(2)
        val results = arrayOfNulls<String>(2)
        val errors = java.util.Collections.synchronizedList(mutableListOf<Throwable>())
        val threads = (0..1).map { idx ->
            Thread {
                try {
                    startBarrier.await()
                    results[idx] = concurrentCipher.encrypt("secret-$idx")
                } catch (t: Throwable) {
                    errors += t
                }
            }.apply { start() }
        }
        threads.forEach { it.join() }

        assertEquals(emptyList(), errors)
        assertEquals(1, concurrentOps.regenerations.get(), "并发遇到失效密钥时只能重建一次密钥，不能互相覆盖")
        assertEquals("secret-0", concurrentCipher.decrypt(assertNotNull(results[0])))
        assertEquals("secret-1", concurrentCipher.decrypt(assertNotNull(results[1])))
    }
}
