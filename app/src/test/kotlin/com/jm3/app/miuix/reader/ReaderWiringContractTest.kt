package com.jm3.app.miuix.reader

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * 接线契约：翻页手势开关与跳页出口的编排是否真的接上。
 *
 * 这几处都是“组合期才跑得起来”的接线（pagerGestureOverride 挂 Modifier、
 * springAnimateToPage 要帧时钟、scrollToItem 要滚动通道），JVM 单测无法执行，
 * 只能在源码层面固定住形状：开关等价于“未放大才接管”，冻结早于动画且取消也解冻。
 * 目标文件属于其它写域（本次只读），因此这些断言是特征化测试，首跑即绿。
 */
class ReaderWiringContractTest {

    private fun mainSource(relativePath: String): String {
        val base = sequenceOf(
            File("app/src/main/kotlin/com/jm3/app/miuix"),
            File("src/main/kotlin/com/jm3/app/miuix"),
        ).firstOrNull { it.isDirectory } ?: fail("找不到 app 主源码目录")
        val file = File(base, relativePath)
        assertTrue(file.isFile, "缺少源码文件 ${file.path}")
        return file.readText()
    }

    @Test
    fun `两套皮肤的翻页宿主都把手势开关接到缩放状态`() {
        listOf("ui/ReaderPageHost.kt", "ui/coui/page/CouiReaderPageContent.kt").forEach { path ->
            val src = mainSource(path)
            val calls = src.lineSequence().filter { it.contains(".pagerGestureOverride(") }.toList()
            assertEquals(1, calls.size, "$path 期望恰有一处 pagerGestureOverride 接线：$calls")
            val call = calls.single()
            val enabledByZoom = call.contains("enabled = !pageZoomed") ||
                call.contains("enabled = pagerGestureEnabled(pageZoomed)")
            assertTrue(enabledByZoom, "$path 的手势开关必须等价于“未放大才接管”，实际：$call")
            assertTrue(src.contains("userScrollEnabled = false"), "$path 必须关掉 pager 原生滚动")
        }
    }

    @Test
    fun `边缘翻页在松手复核通过之前不启动动画`() {
        val src = mainSource("reader/ReaderActions.kt")
        val turnPage = src.substringAfter("fun turnPage(", "").substringBefore("fun goToPage(", "")
        assertTrue(turnPage.contains("stillPinned()"), "翻页必须先过松手复核")
        val pin = turnPage.indexOf("stillPinned()")
        val animate = turnPage.indexOf("springAnimateToPage(")
        assertTrue(animate > 0, "翻页出口应当起 pager 动画")
        assertTrue(pin < animate, "复核必须在起动画之前")
    }

    @Test
    fun `跳页先冻结可见页回写再驱动动画且两条出口都解冻`() {
        // 只看 goToPage 的编排：文件里另有 turnPage 的动画调用，全局取下标会串到别的函数上
        val src = mainSource("reader/ReaderActions.kt")
        val goToPage = src.substringAfter("fun goToPage(", "")
        assertTrue(goToPage.isNotEmpty(), "ReaderActions 里找不到 goToPage")
        val freeze = goToPage.indexOf("session.requestJump(")
        val pagerAnimate = goToPage.indexOf("springAnimateToPage(")
        val listScroll = goToPage.indexOf("scrollToItem(")
        assertTrue(freeze >= 0, "跳页必须先向会话申请跳页（冻结可见页回写）")
        assertTrue(pagerAnimate > freeze, "冻结必须早于 pager 动画")
        assertTrue(listScroll > freeze, "冻结必须早于列表滚动")
        // 动画被取消时也要解冻，否则可见页回写永久冻结：pager 与列表各一个 finally
        val settleSites = src.split("session.onJumpSettled(target)").dropLast(1)
        assertEquals(2, settleSites.size, "pager 与列表两条出口都必须解冻")
        // 每处解冻都要挂在 finally 上：动画被取消时也必须放开可见页回写
        settleSites.forEach { before ->
            assertTrue(
                before.trimEnd().endsWith("finally {"),
                "解冻必须写在 finally 里，实际上文：${before.takeLast(32)}",
            )
        }
    }
}
