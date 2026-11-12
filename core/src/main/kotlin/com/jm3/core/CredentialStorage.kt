package com.jm3.core

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 自动登录凭据：只在登录成功后写入，用来在会话作废时静默重登。 */
@Serializable
data class SavedCredentials(val username: String, val password: String) {
    // 凭据可能被顺手打进日志或崩溃报告，密码不能出现在 toString 里
    override fun toString(): String = "SavedCredentials(username=" + username + ")"
}

/** 自动登录凭据持久化契约；实现负责加密与落盘。 */
interface CredentialStorage {
    suspend fun load(): SavedCredentials?
    suspend fun save(credentials: SavedCredentials)
    suspend fun clear()
}

/** 进程内实现：单测与不落盘场景用。 */
class InMemoryCredentialStorage(initial: SavedCredentials? = null) : CredentialStorage {

    private val lock = Mutex()
    private var value: SavedCredentials? = initial

    override suspend fun load(): SavedCredentials? = lock.withLock { value }

    override suspend fun save(credentials: SavedCredentials) {
        lock.withLock { value = credentials }
    }

    override suspend fun clear() {
        lock.withLock { value = null }
    }
}

/**
 * 凭据密文的落盘位置。
 *
 * 只收密文：加密交给 [CredentialCipher]，落盘位置由平台实现决定（Android 侧与其它设置同库，
 * 见 ENGINEERING.md 一.5）。
 */
interface CredentialBlobStore {
    /**
     * 读回落盘的密文。
     *
     * 名字带 CredentialBlob 是因为 Android 侧的实现同时还承担整库设置（SettingsRepository），
     * 泛泛的 read/write 会与别的存取方法混在一起看不出存取的是什么。
     */
    suspend fun readCredentialBlob(): String?

    /** 覆写落盘密文；null 表示删除。 */
    suspend fun writeCredentialBlob(blob: String?)
}

/** 进程内实现：单测用。 */
class InMemoryCredentialBlobStore(private var blob: String? = null) : CredentialBlobStore {

    override suspend fun readCredentialBlob(): String? = blob

    override suspend fun writeCredentialBlob(blob: String?) {
        this.blob = blob
    }
}

/** 明密文互转；Android 实现走 Keystore，单测用可逆的假实现即可覆盖存储逻辑。 */
interface CredentialCipher {
    fun encrypt(plain: String): String
    fun decrypt(encrypted: String): String
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
 * 凭据存储：明文只在加解密时经过内存，落盘的只有密文。
 *
 * 密钥丢失（系统重置、锁屏方式变更、备份还原）后密文永远解不开，读到坏数据就地清掉：
 * 留着只会每次启动白算一遍解密。
 */
class EncryptedCredentialStore(
    private val blobStore: CredentialBlobStore,
    private val cipher: CredentialCipher,
) : CredentialStorage {

    override suspend fun load(): SavedCredentials? {
        val blob = blobStore.readCredentialBlob() ?: return null
        // 纯 CPU 调用，不涉及挂起，不存在吞取消的问题
        val plain = runCatching { cipher.decrypt(blob) }.getOrNull()
        if (plain == null) {
            clear()
            return null
        }
        val credentials = CredentialCodec.decode(plain)
        if (credentials == null) {
            clear()
            return null
        }
        return credentials
    }

    override suspend fun save(credentials: SavedCredentials) {
        blobStore.writeCredentialBlob(cipher.encrypt(CredentialCodec.encode(credentials)))
    }

    override suspend fun clear() {
        blobStore.writeCredentialBlob(null)
    }
}
