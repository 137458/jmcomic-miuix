package com.jm3.core.update

import kotlin.test.Test
import kotlin.test.assertEquals

class MarkdownBlocksTest {

    @Test
    fun 空输入返回空列表() {
        assertEquals(emptyList(), MarkdownBlocks.parse(""))
        assertEquals(emptyList(), MarkdownBlocks.parse("   \n\n  \n\n"))
    }

    @Test
    fun 解析标题层级() {
        val blocks = MarkdownBlocks.parse(
            """
            # 一级标题
            ### 三级标题
            ###### 六级标题
            """.trimIndent(),
        )
        assertEquals(
            listOf(
                MarkdownBlock.Heading(1, "一级标题"),
                MarkdownBlock.Heading(3, "三级标题"),
                MarkdownBlock.Heading(6, "六级标题"),
            ),
            blocks,
        )
    }

    @Test
    fun 解析无序列表() {
        val blocks = MarkdownBlocks.parse("- 修复下载中断\n* 修复安装失败\n+ 优化更新页布局")
        assertEquals(
            listOf(
                MarkdownBlock.Bullet("修复下载中断"),
                MarkdownBlock.Bullet("修复安装失败"),
                MarkdownBlock.Bullet("优化更新页布局"),
            ),
            blocks,
        )
    }

    @Test
    fun 解析有序列表并保留原序号() {
        val blocks = MarkdownBlocks.parse("1. 打开更新页\n2. 点击下载\n3. 安装")
        assertEquals(
            listOf(
                MarkdownBlock.Ordered(1, "打开更新页"),
                MarkdownBlock.Ordered(2, "点击下载"),
                MarkdownBlock.Ordered(3, "安装"),
            ),
            blocks,
        )
    }

    @Test
    fun 解析分隔线() {
        val blocks = MarkdownBlocks.parse("第一段\n\n---\n\n第二段\n***\n___")
        assertEquals(
            listOf(
                MarkdownBlock.Paragraph("第一段"),
                MarkdownBlock.Divider,
                MarkdownBlock.Paragraph("第二段"),
                MarkdownBlock.Divider,
                MarkdownBlock.Divider,
            ),
            blocks,
        )
    }

    @Test
    fun 连续非空行合并为一个段落() {
        val blocks = MarkdownBlocks.parse("第一行\n第二行\n\n第三行")
        assertEquals(
            listOf(
                MarkdownBlock.Paragraph("第一行 第二行"),
                MarkdownBlock.Paragraph("第三行"),
            ),
            blocks,
        )
    }

    @Test
    fun 列表项不会被合并进段落() {
        val blocks = MarkdownBlocks.parse("- 修复 A\n- 修复 B")
        assertEquals(
            listOf(
                MarkdownBlock.Bullet("修复 A"),
                MarkdownBlock.Bullet("修复 B"),
            ),
            blocks,
        )
    }

    @Test
    fun 块顺序与原文一致() {
        val blocks = MarkdownBlocks.parse(
            """
            ## 新增

            - 更新页重做
            - 应用内下载

            1. 打开更新页
            2. 点下载

            ---

            其余说明写成段落，
            跨两行。
            """.trimIndent(),
        )
        assertEquals(
            listOf(
                MarkdownBlock.Heading(2, "新增"),
                MarkdownBlock.Bullet("更新页重做"),
                MarkdownBlock.Bullet("应用内下载"),
                MarkdownBlock.Ordered(1, "打开更新页"),
                MarkdownBlock.Ordered(2, "点下载"),
                MarkdownBlock.Divider,
                MarkdownBlock.Paragraph("其余说明写成段落， 跨两行。"),
            ),
            blocks,
        )
    }
}
