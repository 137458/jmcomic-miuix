package com.jm3.app.miuix.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.font.FontWeight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** 更新说明的行内解析：链接必须变成可点击注解，未闭合标记必须保持原文。 */
class MarkdownNotesInlineTest {

    private val codeColor = Color(0xFF123456)
    private val linkColor = Color(0xFF3366FF)
    private val codeBackground = Color(0xFFEEEEEE)

    private fun parse(text: String) = parseInlineNotes(
        text = text,
        codeColor = codeColor,
        linkColor = linkColor,
        codeBackground = codeBackground,
    )

    @Test
    fun `link renders label and keeps url`() {
        val result = parse("见 [更新页面](https://example.com/releases)")

        assertEquals("见 更新页面", result.text)
        val links = result.getLinkAnnotations(0, result.length)
        assertEquals(1, links.size)
        // getLinkAnnotations 的元素是基类 LinkAnnotation，url 只存在于 Url 子类型
        val link = assertIs<LinkAnnotation.Url>(links.single().item)
        assertEquals("https://example.com/releases", link.url)
    }

    @Test
    fun `unclosed or non-link brackets stay literal`() {
        assertEquals("[未闭合](", parse("[未闭合](").text)
        assertEquals("[文字]", parse("[文字]").text)
        assertEquals("[x](无右括号", parse("[x](无右括号").text)
    }

    @Test
    fun `link inside bold keeps both styles`() {
        val result = parse("**[更新](https://example.com)**")

        assertEquals("更新", result.text)
        assertEquals(1, result.getLinkAnnotations(0, result.length).size)
        assertEquals(FontWeight.Bold, result.spanStyles.single().item.fontWeight)
    }

    @Test
    fun `bare http and https urls become clickable links without absorbing trailing punctuation`() {
        val result = parse("完整更新日志：https://github.com/137458/jmcomic-miuix/compare/v1.0...v1.1。")

        assertEquals("完整更新日志：https://github.com/137458/jmcomic-miuix/compare/v1.0...v1.1。", result.text)
        val links = result.getLinkAnnotations(0, result.length)
        assertEquals(1, links.size)
        val link = assertIs<LinkAnnotation.Url>(links.single().item)
        assertEquals("https://github.com/137458/jmcomic-miuix/compare/v1.0...v1.1", link.url)
    }

    @Test
    fun `bare url inside inline code stays literal code and is not linked`() {
        val result = parse("接口地址 `https://example.com/api`")

        assertEquals("接口地址 https://example.com/api", result.text)
        assertEquals(0, result.getLinkAnnotations(0, result.length).size)
    }
}
