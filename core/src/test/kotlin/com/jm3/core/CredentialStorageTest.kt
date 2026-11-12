package com.jm3.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 凭据存储：落盘必须是密文，坏数据一次性清干净，别把异常带进登录流程。 */
class CredentialStorageTest {

    /** 可逆假实现：密文里不能出现明文，断言落盘内容时才看得出加解密有没有真跑。 */
    private class ReversingCipher : CredentialCipher {
        override fun encrypt(plain: String): String = plain.reversed()
        override fun decrypt(encrypted: String): String = encrypted.reversed()
    }

    /** 模拟密钥丢失：解不开就抛，用来覆盖「读到坏数据」的分支。 */
    private class LostKeyCipher : CredentialCipher {
        override fun encrypt(plain: String): String = plain
        override fun decrypt(encrypted: String): String = error("密钥已失效")
    }

    @Test
    fun `保存后落盘的是密文且能原样读回`() = runBlocking {
        val blobStore = InMemoryCredentialBlobStore()
        val store = EncryptedCredentialStore(blobStore, ReversingCipher())
        val saved = SavedCredentials("alice", "s3cret")

        store.save(saved)

        val blob = blobStore.readCredentialBlob().orEmpty()
        assertEquals(true, blob.isNotEmpty())
        assertEquals(false, blob.contains("s3cret"), "落盘内容里出现了明文密码")
        assertEquals(saved, store.load())
    }

    @Test
    fun `未存过凭据时读回空`() = runBlocking {
        val store = EncryptedCredentialStore(InMemoryCredentialBlobStore(), ReversingCipher())

        assertNull(store.load())
    }

    @Test
    fun `密文解不开时清掉本地凭据且不抛异常`() = runBlocking {
        val blobStore = InMemoryCredentialBlobStore("坏掉的密文")
        val store = EncryptedCredentialStore(blobStore, LostKeyCipher())

        assertNull(store.load())
        assertNull(blobStore.readCredentialBlob(), "解不开的密文应当就地清掉，否则每次启动都要白算一遍解密")
    }

    @Test
    fun `明文不是合法凭据 JSON 时读回空并清掉坏密文`() = runBlocking {
        val blobStore = InMemoryCredentialBlobStore()
        val store = EncryptedCredentialStore(blobStore, ReversingCipher())
        blobStore.writeCredentialBlob("随便一段文本".reversed())

        assertNull(store.load())
        assertNull(blobStore.readCredentialBlob(), "解出来不是合法 JSON 时也应当就地清掉坏密文")
    }

    @Test
    fun `clear 后不再返回凭据`() = runBlocking {
        val blobStore = InMemoryCredentialBlobStore()
        val store = EncryptedCredentialStore(blobStore, ReversingCipher())
        store.save(SavedCredentials("bob", "pw"))

        store.clear()

        assertNull(store.load())
        assertNull(blobStore.readCredentialBlob())
    }

    @Test
    fun `内存实现读回初始值、保存覆盖与清空`() = runBlocking {
        val storage = InMemoryCredentialStorage(SavedCredentials("dave", "init"))

        assertEquals(SavedCredentials("dave", "init"), storage.load())

        storage.save(SavedCredentials("erin", "next"))
        assertEquals(SavedCredentials("erin", "next"), storage.load())

        storage.clear()
        assertNull(storage.load())
    }

    @Test
    fun `凭据的 toString 不暴露密码`() {
        val text = SavedCredentials("carol", "top-secret").toString()

        assertEquals(false, text.contains("top-secret"))
        assertEquals(true, text.contains("carol"))
    }

    @Test
    fun `并发 load 坏密文与 save 时串行化防止清掉新写入的凭据`() = runBlocking {
        val readStarted = CompletableDeferred<Unit>()
        val releaseRead = CompletableDeferred<Unit>()
        var storedBlob: String? = "corrupted-blob"
        var gateEnabled = true
        val blobStore = object : CredentialBlobStore {
            override suspend fun readCredentialBlob(): String? {
                val value = storedBlob
                if (gateEnabled) {
                    gateEnabled = false
                    readStarted.complete(Unit)
                    releaseRead.await()
                }
                return value
            }

            override suspend fun writeCredentialBlob(blob: String?) {
                storedBlob = blob
            }
        }
        val cipher = object : CredentialCipher {
            override fun encrypt(plain: String): String = "enc:$plain"
            override fun decrypt(encrypted: String): String {
                if (!encrypted.startsWith("enc:")) error("bad blob")
                return encrypted.removePrefix("enc:")
            }
        }
        val store = EncryptedCredentialStore(blobStore, cipher)

        val loading = launch { store.load() }
        readStarted.await()
        val fresh = SavedCredentials("alice", "new-secret")
        val saving = launch { store.save(fresh) }
        releaseRead.complete(Unit)
        loading.join()
        saving.join()

        assertEquals(fresh, store.load())
    }
}
