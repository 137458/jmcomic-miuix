package com.jm3.app.miuix.ui

import com.jm3.core.update.MarkdownBlock
import com.jm3.core.update.MarkdownBlocks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 测试更新弹窗的假数据契约。
 *
 * 长日志必须足够长、块类型足够杂，才能在真机上验证「折叠 + 限高 + 滚动」的排版；
 * 短日志则保证日常预览仍是轻量的一次点按。两组差异数据分别覆盖单包与多包版本。
 */
class UpdateTestReleaseFixtureTest {

    @Test
    fun `long notes are long enough to force scrolling`() {
        val lines = TEST_LONG_UPDATE_NOTES.lines()
        assertTrue(lines.size >= 60, "长日志应有至少 60 行，实际为 " + lines.size + " 行")
        assertTrue(lines.count { it.isNotBlank() } >= 35, "有效内容不应只有空行")
        assertTrue(lines.maxOf { it.length } >= 100, "需要一段会折行的长段落")
    }

    @Test
    fun `long notes cover every markdown block the dialog renders`() {
        val blocks = MarkdownBlocks.parse(TEST_LONG_UPDATE_NOTES)
        assertTrue(blocks.any { it is MarkdownBlock.Heading })
        assertTrue(blocks.any { it is MarkdownBlock.Bullet })
        assertTrue(blocks.any { it is MarkdownBlock.Ordered })
        assertTrue(blocks.any { it is MarkdownBlock.Divider })
        assertTrue(blocks.any { it is MarkdownBlock.Paragraph })
        assertTrue(blocks.filterIsInstance<MarkdownBlock.Heading>().any { it.level == 6 })
        assertTrue(blocks.filterIsInstance<MarkdownBlock.Heading>().any { it.level == 1 })
        assertTrue(TEST_LONG_UPDATE_NOTES.contains("**"))
        assertTrue(TEST_LONG_UPDATE_NOTES.contains("\u0060"))
        assertTrue(TEST_LONG_UPDATE_NOTES.lines().count { it.trim() == "---" } >= 2)
        assertTrue(TEST_LONG_UPDATE_NOTES.lines().count { Regex("^[0-9]+\\. ").containsMatchIn(it) } >= 5)
        assertTrue(TEST_LONG_UPDATE_NOTES.lines().count { it.startsWith("- ") } >= 10)
    }

    @Test
    fun `short fixture keeps a single apk and a short summary`() {
        assertEquals("9.9.9-test", TEST_UPDATE_RELEASE.version)
        assertEquals("2026-01-01", TEST_UPDATE_RELEASE.publishedDate)
        assertEquals(1, TEST_UPDATE_RELEASE.assets.size)
        assertEquals("jm3-9.9.9-test.apk", TEST_UPDATE_RELEASE.apk?.name)
        assertEquals(12_345_678L, TEST_UPDATE_RELEASE.apk?.size)
        assertTrue(TEST_UPDATE_RELEASE.notes.lines().size <= 10)
    }

    @Test
    fun `long fixture picks the universal apk and exposes four assets`() {
        assertEquals("9.9.9-test-long", TEST_LONG_UPDATE_RELEASE.version)
        assertEquals("2026-06-18", TEST_LONG_UPDATE_RELEASE.publishedDate)
        assertEquals(4, TEST_LONG_UPDATE_RELEASE.assets.size)
        assertNotNull(TEST_LONG_UPDATE_RELEASE.apk)
        assertEquals("jm3-9.9.9-test-universal.apk", TEST_LONG_UPDATE_RELEASE.apk?.name)
        assertEquals(48_000_000L, TEST_LONG_UPDATE_RELEASE.apk?.size)
        assertEquals(TEST_LONG_UPDATE_NOTES, TEST_LONG_UPDATE_RELEASE.notes)
    }

    @Test
    fun `test download states cover known and unknown totals`() {
        assertEquals(0.87f, TEST_DOWNLOADING_STATE.progress)
        assertEquals(2_500_000L, TEST_DOWNLOADING_STATE.speedBytesPerSec)
        assertEquals(0L, TEST_UNKNOWN_TOTAL_DOWNLOADING_STATE.total)
        assertEquals(0f, TEST_UNKNOWN_TOTAL_DOWNLOADING_STATE.progress)
        assertEquals("下载中 87%", updateDialogPrimaryText(hasApk = true, TEST_DOWNLOADING_STATE))
    }
}
