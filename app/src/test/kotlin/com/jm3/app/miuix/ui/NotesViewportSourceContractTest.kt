package com.jm3.app.miuix.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * 说明视口的源码契约：这些约束不是风格偏好，违反会直接崩溃或把折叠入口甩出可点范围。
 *
 * 背景：COUI 通用弹窗的正文槽位本身是滚动视口（库 Dialog.kt 用 weight(1f, fill = false) + verticalScroll
 * 包裹 content），子节点以无限最大高度被测量。曾因此把说明区的滚动节点直接塞进弹窗正文，
 * 抛出 "Vertically scrollable component was measured with an infinity maximum height constraints"。
 */
class NotesViewportSourceContractTest {

    private val uiDir = sequenceOf(
        File("src/main/kotlin/com/jm3/app/miuix/ui"),
        File("app/src/main/kotlin/com/jm3/app/miuix/ui"),
    ).firstOrNull { it.isDirectory } ?: fail("未找到主源码目录，工作目录为 ${File(".").absolutePath}")

    private fun source(name: String): String {
        val file = File(uiDir, name)
        assertTrue(file.isFile, "缺少源文件 ${file.path}")
        return file.readText()
    }

    @Test
    fun `viewport applies bounded height before fade and scroll`() {
        val src = source("NotesViewport.kt")
        val heightIn = src.indexOf("heightIn(max = spec.maxHeight)")
        val fade = src.indexOf("verticalEdgeFade(")
        val scroll = src.indexOf("verticalScroll(scrollState)")

        assertTrue(heightIn > 0, "视口必须显式给出 heightIn 上界，否则折叠态会被无限高度测量")
        assertTrue(fade > 0, "视口必须在滚动节点外侧做边缘羽化")
        assertTrue(scroll > 0, "视口必须自己持有滚动节点")
        assertTrue(heightIn < fade && fade < scroll, "顺序必须是 heightIn → verticalEdgeFade → verticalScroll")
    }

    @Test
    fun `dialogs never nest their own scroll container`() {
        val src = source("UpdateDialog.kt")

        assertFalse(src.contains("couiVerticalScroll"), "COUI 弹窗正文已是滚动视口，不得再套一层滚动")
        assertFalse(src.contains("verticalScroll("), "弹窗正文不再自带滚动，说明区由 NotesScrollViewport 承担")
    }
}
