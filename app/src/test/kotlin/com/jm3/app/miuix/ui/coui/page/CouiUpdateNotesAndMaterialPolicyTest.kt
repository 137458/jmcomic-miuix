// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.page

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.sp
import com.jm3.app.miuix.ui.COUI_UPDATE_NOTES_PREVIEW_LINES
import com.jm3.app.miuix.ui.INLINE_CODE_TAG
import com.jm3.app.miuix.ui.buildInlineCodeDisplay
import com.jm3.app.miuix.ui.coui.component.couiContourSurfaceEnabled
import com.jm3.app.miuix.ui.updateDialogNotesState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CouiUpdateNotesAndMaterialPolicyTest {

    @Test
    fun `行内代码显示转换应为代码胶囊添加呼吸间距并去掉硬矩形背景`() {
        val rawBackground = Color(0x22000000)
        val raw = buildAnnotatedString {
            append("现在更新日志中")
            val start = length
            pushStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    color = Color.Black,
                    background = rawBackground,
                ),
            )
            append("JM3-v1.1.0.apk")
            pop()
            addStringAnnotation(INLINE_CODE_TAG, "JM3-v1.1.0.apk", start, length)
            append("这种内容。")
        }

        val display = buildInlineCodeDisplay(
            source = raw,
            codeColor = Color.Blue,
            codeFontSize = 12.sp,
        )

        assertEquals(1, display.codeRanges.size)
        val range = display.codeRanges.first()
        val pillText = display.text.text.substring(range.first, range.last + 1)
        assertTrue(pillText.contains("JM3-v1.1.0.apk"))
        assertTrue(pillText.startsWith("\u200A") && pillText.endsWith("\u200A"))
        // 中文左右两侧应插入窄空格避免黏连，而中文句号前不应强加额外空格
        val beforeChar = display.text.text[range.first - 1]
        assertEquals('\u2009', beforeChar)
        val afterStart = range.last + 1
        assertEquals('\u2009', display.text.text[afterStart])
        // 原硬矩形背景色被剥离，改用胶囊自绘
        assertFalse(display.text.spanStyles.any { it.item.background == rawBackground })
    }

    @Test
    fun `混合行内代码与链接时链接区间映射必须保持准确`() {
        val url = "https://example.com/releases"
        val raw = buildAnnotatedString {
            append("见")
            val cStart = length
            pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color.Gray))
            append("JM3-v1.1.0.apk")
            pop()
            addStringAnnotation(INLINE_CODE_TAG, "JM3-v1.1.0.apk", cStart, length)
            append("与")
            withLink(LinkAnnotation.Url(url)) {
                append("发布页")
            }
            append("。")
        }

        val display = buildInlineCodeDisplay(
            source = raw,
            codeColor = Color.Blue,
            codeFontSize = 12.sp,
        )

        val links = display.text.getLinkAnnotations(0, display.text.length)
        assertEquals(1, links.size)
        val linkRange = links.first()
        assertEquals("发布页", display.text.text.substring(linkRange.start, linkRange.end))
        assertEquals(url, (linkRange.item as LinkAnnotation.Url).url)
        // 句号紧跟在链接后，不需要额外的间隔字符；“。 ”之间无误偏移
        assertEquals('。', display.text.text.last())
    }

    @Test
    fun `任何非空更新说明都应可收起且短说明收起后完全折叠`() {
        assertFalse(couiChangelogCollapsible(0))
        assertTrue(couiChangelogCollapsible(1))
        assertTrue(couiChangelogCollapsible(3))
        assertTrue(couiChangelogCollapsible(7))

        assertEquals(0, couiChangelogPreviewLimit(blockCount = 3, expanded = false))
        assertEquals(3, couiChangelogCollapsedHiddenCount(blockCount = 3))

        assertEquals(2, couiChangelogPreviewLimit(blockCount = 7, expanded = false))
        assertEquals(5, couiChangelogCollapsedHiddenCount(blockCount = 7))
        assertEquals(null, couiChangelogPreviewLimit(blockCount = 7, expanded = true))
    }

    @Test
    fun `弹窗预览行数阈值应能折叠常规更新说明并在展开与收起间切换`() {
        val notes = (1..8).joinToString("\n") { "- 第 $it 条更新说明，包含 `JM3-v1.1.0.apk`" }
        val collapsed = updateDialogNotesState(
            markdown = notes,
            expanded = false,
            previewLines = COUI_UPDATE_NOTES_PREVIEW_LINES,
        )
        assertTrue(collapsed.hiddenLineCount > 0)
        assertEquals(8 - COUI_UPDATE_NOTES_PREVIEW_LINES, collapsed.hiddenLineCount)

        val expanded = updateDialogNotesState(
            markdown = notes,
            expanded = true,
            previewLines = COUI_UPDATE_NOTES_PREVIEW_LINES,
        )
        assertEquals(0, expanded.hiddenLineCount)
        assertEquals(notes, expanded.visibleMarkdown)
    }

    @Test
    fun `COUI 轮廓凝光材质在 API 33 及以上启用且 32 及以下关闭`() {
        assertFalse(couiContourSurfaceEnabled(32))
        assertTrue(couiContourSurfaceEnabled(33))
        assertTrue(couiContourSurfaceEnabled(34))
    }
}
