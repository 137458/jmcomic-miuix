package com.jm3.core.settings

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Adapter 侧契约：实现 [SettingsKeyStore] 的 8 个原语，即自动获得全部默认值与归一规则。
 *
 * 这里用一个纯 Map 的第二个 Adapter，证明「设置语义」确实在 seam 之上，
 * 而不是被抄进了某个具体实现里。
 */
class SettingsKeyStoreAdapterTest {

    /** 第三个 Adapter 的最简形态：只有键值存取，没有任何设置语义。 */
    private class MapKeyStore(private val values: MutableMap<String, Any> = mutableMapOf()) : SettingsKeyStore {
        override suspend fun readString(key: String): String? = values[key] as? String
        override suspend fun readBoolean(key: String): Boolean? = values[key] as? Boolean
        override suspend fun readInt(key: String): Int? = values[key] as? Int
        override suspend fun readLong(key: String): Long? = values[key] as? Long
        override suspend fun writeBoolean(key: String, value: Boolean) {
            values[key] = value
        }
        override suspend fun writeInt(key: String, value: Int) {
            values[key] = value
        }
        override suspend fun writeLong(key: String, value: Long) {
            values[key] = value
        }
        override suspend fun updateString(key: String, transform: (String?) -> String?) {
            val next = transform(values[key] as? String)
            if (next == null) values.remove(key) else values[key] = next
        }
    }

    private class DelegatingSettings(store: SettingsKeyStore) : SettingsRepository, SettingsKeyStore by store

    private fun settings() = DelegatingSettings(MapKeyStore())

    @Test
    fun 只实现原语的适配器即获得搜索历史规则() = runTest {
        val s = settings()
        repeat(SettingsRepository.SEARCH_HISTORY_LIMIT + 2) { s.addSearchHistory("词$it") }
        assertEquals(SettingsRepository.SEARCH_HISTORY_LIMIT, s.searchHistory().size)
        assertEquals("词${SettingsRepository.SEARCH_HISTORY_LIMIT + 1}", s.searchHistory().first())
    }

    @Test
    fun 清空搜索历史删除键而不是留空串() = runTest {
        val s = settings()
        s.addSearchHistory("词")
        s.clearSearchHistory()
        assertEquals(emptyList(), s.searchHistory())
    }

    @Test
    fun 旧数据里的越界字号读时被夹取() = runTest {
        val store = MapKeyStore(mutableMapOf("novel_font_size" to 99))
        val s = DelegatingSettings(store)
        assertEquals(SettingsRepository.NOVEL_FONT_SIZE_MAX, s.novelFontSize())
    }

    @Test
    fun 版本写入空串等于删除() = runTest {
        val s = settings()
        s.setIgnoredUpdateVersion("1.0.0")
        s.setIgnoredUpdateVersion("  ")
        assertNull(s.ignoredUpdateVersion())
    }

    @Test
    fun 删除搜索历史对入参归一且清空后写入null删除键() = runTest {
        val rawMap = mutableMapOf<String, Any>()
        val s = DelegatingSettings(MapKeyStore(rawMap))
        s.addSearchHistory("两个 词")
        s.addSearchHistory("另一个")

        // 含首尾空白与折行的同义词也应命中删除
        s.removeSearchHistory(" 两个\n词 ")
        assertEquals(listOf("另一个"), s.searchHistory())

        // 删除最后一项时底层存储必须写 null（移除键）而非留空串
        s.removeSearchHistory(" 另一个 ")
        assertEquals(emptyList(), s.searchHistory())
        assertNull(rawMap[SettingsKeys.SEARCH_HISTORY])
    }
}
