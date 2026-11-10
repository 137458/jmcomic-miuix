package com.jm3.app.miuix.ui

import com.jm3.core.settings.ReaderImageQuality
import com.jm3.core.settings.ReaderPreferences
import com.jm3.core.settings.ReaderSettings
import com.jm3.core.settings.ReaderSpreadMode
import com.jm3.core.settings.SettingsKeyStore
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 内存键值：只实现存储原语，能力接口的默认实现照常跑归一。 */
private class MemorySettingsStore : SettingsKeyStore {
    val values = mutableMapOf<String, Any?>()
    val writes = mutableListOf<String>()

    override suspend fun readString(key: String): String? = values[key] as? String

    override suspend fun readBoolean(key: String): Boolean? = values[key] as? Boolean

    override suspend fun readInt(key: String): Int? = values[key] as? Int

    override suspend fun readLong(key: String): Long? = values[key] as? Long

    override suspend fun writeBoolean(key: String, value: Boolean) {
        values[key] = value
        writes += key
    }

    override suspend fun writeInt(key: String, value: Int) {
        values[key] = value
        writes += key
    }

    override suspend fun writeLong(key: String, value: Long) {
        values[key] = value
        writes += key
    }

    override suspend fun updateString(key: String, transform: (String?) -> String?) {
        val next = transform(values[key] as? String)
        if (next == null) values.remove(key) else values[key] = next
        writes += key
    }
}

private class FakeReaderSettings(store: SettingsKeyStore) : ReaderSettings, SettingsKeyStore by store

private class Harness {
    val store = MemorySettingsStore()
    val state = ReaderSettingsState(FakeReaderSettings(store))
}

class ReaderSettingsStateTest {

    @Test
    fun `空存储时读出全部默认值`() = runBlocking {
        val harness = Harness()
        harness.state.load()

        assertFalse(harness.state.horizontal)
        assertEquals(ReaderSpreadMode.SINGLE, harness.state.spread)
        assertTrue(harness.state.volumeKeyPaging)
        assertTrue(harness.state.keepScreenOn)
        assertEquals(ReaderPreferences.FOLLOW_SYSTEM_BRIGHTNESS, harness.state.brightness)
        assertEquals(ReaderImageQuality.STANDARD, harness.state.imageQuality)
        assertEquals(ReaderPreferences.DEFAULT_PREFETCH_PAGES, harness.state.prefetchPages)
        assertTrue(harness.store.writes.isEmpty())
    }

    @Test
    fun `读盘按仓库归一后的取值映射七项`() = runBlocking {
        val harness = Harness()
        harness.store.values["reader_horizontal"] = true
        harness.store.values["reader_spread"] = "DOUBLE"
        harness.store.values["reader_volume_key_paging"] = false
        harness.store.values["reader_keep_screen_on"] = false
        harness.store.values["reader_brightness"] = 42
        harness.store.values["reader_image_quality"] = "HIGH"
        harness.store.values["reader_prefetch_pages"] = 5

        harness.state.load()

        assertTrue(harness.state.horizontal)
        assertEquals(ReaderSpreadMode.DOUBLE, harness.state.spread)
        assertFalse(harness.state.volumeKeyPaging)
        assertFalse(harness.state.keepScreenOn)
        assertEquals(42, harness.state.brightness)
        assertEquals(ReaderImageQuality.HIGH, harness.state.imageQuality)
        assertEquals(5, harness.state.prefetchPages)
    }

    @Test
    fun `读盘对损坏值走仓库默认与归一`() = runBlocking {
        val harness = Harness()
        harness.store.values["reader_brightness"] = 999
        harness.store.values["reader_image_quality"] = "BROKEN"
        harness.store.values["reader_prefetch_pages"] = 7

        harness.state.load()

        assertEquals(ReaderPreferences.MAX_BRIGHTNESS, harness.state.brightness)
        assertEquals(ReaderImageQuality.STANDARD, harness.state.imageQuality)
        assertEquals(ReaderPreferences.DEFAULT_PREFETCH_PAGES, harness.state.prefetchPages)
    }

    @Test
    fun `布尔项切换立即更新并只写一次盘`() = runBlocking {
        val harness = Harness()
        harness.state.load()

        harness.state.applyVolumeKeyPaging(false)
        assertFalse(harness.state.volumeKeyPaging)
        assertEquals(listOf("reader_volume_key_paging"), harness.store.writes)

        harness.state.applyVolumeKeyPaging(false)
        assertEquals(1, harness.store.writes.size)
    }

    @Test
    fun `四个选择项各自写对应键`() = runBlocking {
        val harness = Harness()
        harness.state.load()

        harness.state.applyHorizontal(true)
        harness.state.applySpread(ReaderSpreadMode.DOUBLE)
        harness.state.applyKeepScreenOn(false)
        harness.state.applyImageQuality(ReaderImageQuality.ECONOMY)
        harness.state.applyPrefetchPages(0)

        assertTrue(harness.state.horizontal)
        assertEquals(ReaderSpreadMode.DOUBLE, harness.state.spread)
        assertFalse(harness.state.keepScreenOn)
        assertEquals(ReaderImageQuality.ECONOMY, harness.state.imageQuality)
        assertEquals(0, harness.state.prefetchPages)
        assertEquals(
            listOf(
                "reader_horizontal",
                "reader_spread",
                "reader_keep_screen_on",
                "reader_image_quality",
                "reader_prefetch_pages",
            ),
            harness.store.writes,
        )
    }

    @Test
    fun `亮度拖动只改本地值提交时才写盘`() = runBlocking {
        val harness = Harness()
        harness.state.load()

        harness.state.previewBrightness(0f)
        assertEquals(ReaderPreferences.FOLLOW_SYSTEM_BRIGHTNESS, harness.state.brightness)
        assertTrue(harness.store.writes.isEmpty())

        harness.state.previewBrightness(42.6f)
        assertEquals(43, harness.state.brightness)
        assertTrue(harness.store.writes.isEmpty())

        harness.state.commitBrightness()
        assertEquals(listOf("reader_brightness"), harness.store.writes)
        assertEquals(43, harness.store.values["reader_brightness"])
    }

    @Test
    fun `亮度提交夹在上限内`() = runBlocking {
        val harness = Harness()
        harness.state.load()

        harness.state.previewBrightness(150f)
        harness.state.commitBrightness()

        assertEquals(ReaderPreferences.MAX_BRIGHTNESS, harness.store.values["reader_brightness"])
    }
}
