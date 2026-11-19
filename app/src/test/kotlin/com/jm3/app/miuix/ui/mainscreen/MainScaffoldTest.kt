package com.jm3.app.miuix.ui.mainscreen

import com.jm3.core.settings.BottomBarForm
import com.jm3.core.settings.BottomBarSize
import com.jm3.core.settings.UiSkin
import com.jm3.core.settings.WideScreenNavigation
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 主屏骨架测试：分页表、角标口径、内容宽度、栏位解析与启动接线全在纯 kotlin.test 上跑。
 *
 * 断言落在业务输出上：角标文字与该不该出现、内容宽度算出来的 dp、底栏 token 的尺寸与形态、
 * 端口收到的启动与未读刷新调用。
 */

/** 记录启动与未读刷新调用的假端口。 */
private class FakeMainScaffoldPort : MainScaffoldPort {

    var startCalls = 0
    val unreadRefreshes = mutableListOf<Boolean>()

    override suspend fun ensureDownloadsStarted() {
        startCalls += 1
    }

    override suspend fun refreshUnread(signedIn: Boolean) {
        unreadRefreshes += signedIn
    }
}

/** 分页下标的外部存放点：模拟 rememberSaveable 里的那个 Int 状态。 */
private class TabIndexHolder {

    var index = 0

    fun state(port: MainScaffoldPort): MainScaffoldState = MainScaffoldState(
        port = port,
        tabIndex = { index },
        onTabIndexChange = { index = it },
    )
}

class MainScaffoldTest {

    @Test
    fun `分页表保持四页原有顺序与文案`() {
        assertEquals(listOf("首页", "分类", "榜单", "我的"), MainTab.entries.map { it.label })
    }

    @Test
    fun `未读角标在两位数以内原样显示`() {
        assertEquals("7", unreadBadgeText(7))
        assertEquals("99", unreadBadgeText(99))
    }

    @Test
    fun `未读角标超过上限收敛为99加`() {
        assertEquals("99+", unreadBadgeText(100))
        assertEquals("99+", unreadBadgeText(4321))
    }

    @Test
    fun `只有我的分页挂未读角标`() {
        assertEquals(MainTabBadge.None, mainTabBadge(MainTab.HOME, unread = 5, activeDownloads = 2))
        assertEquals(MainTabBadge.None, mainTabBadge(MainTab.CATEGORY, unread = 5, activeDownloads = 2))
        assertEquals(MainTabBadge.None, mainTabBadge(MainTab.RANK, unread = 5, activeDownloads = 2))
        assertEquals(MainTabBadge.Unread("5"), mainTabBadge(MainTab.MINE, unread = 5, activeDownloads = 2))
    }

    @Test
    fun `我的分页优先显示未读而不是下载`() {
        assertEquals(MainTabBadge.Unread("1"), mainTabBadge(MainTab.MINE, unread = 1, activeDownloads = 3))
    }

    @Test
    fun `没有未读时我的分页显示在途下载`() {
        assertEquals(MainTabBadge.Downloads, mainTabBadge(MainTab.MINE, unread = 0, activeDownloads = 2))
    }

    @Test
    fun `既无未读也无下载时不挂角标`() {
        assertEquals(MainTabBadge.None, mainTabBadge(MainTab.MINE, unread = 0, activeDownloads = 0))
    }

    @Test
    fun `用侧栏时内容宽度扣掉侧栏宽度`() {
        assertEquals(1112, mainContentWidthDp(widthDp = 1200, useRail = true, railWidthDp = 88))
    }

    @Test
    fun `不用侧栏时内容宽度等于窗口宽度`() {
        assertEquals(1200, mainContentWidthDp(widthDp = 1200, useRail = false, railWidthDp = 88))
    }

    @Test
    fun `量不到侧栏宽度时内容宽度等于窗口宽度`() {
        assertEquals(1200, mainContentWidthDp(widthDp = 1200, useRail = true, railWidthDp = 0))
    }

    @Test
    fun `宽高都够时默认走侧栏`() {
        val layout = resolveMainBarLayout(
            wideScreenNavigation = null,
            explicitSize = null,
            explicitForm = null,
            widthDp = 900,
            heightDp = 800,
        )
        assertTrue(layout.useRail)
        assertEquals(WideScreenNavigation.RAIL, layout.wideScreenNavigation)
    }

    @Test
    fun `矮横屏退回底栏`() {
        val layout = resolveMainBarLayout(
            wideScreenNavigation = null,
            explicitSize = null,
            explicitForm = null,
            widthDp = 900,
            heightDp = 400,
        )
        assertFalse(layout.useRail)
    }

    @Test
    fun `手机宽度即使选了侧栏也退回底栏`() {
        val layout = resolveMainBarLayout(
            wideScreenNavigation = WideScreenNavigation.RAIL,
            explicitSize = null,
            explicitForm = null,
            widthDp = 400,
            heightDp = 800,
        )
        assertFalse(layout.useRail)
    }

    @Test
    fun `用户显式选侧栏时宽屏走侧栏`() {
        val layout = resolveMainBarLayout(
            wideScreenNavigation = WideScreenNavigation.RAIL,
            explicitSize = null,
            explicitForm = null,
            widthDp = 700,
            heightDp = 700,
        )
        assertTrue(layout.useRail)
    }

    @Test
    fun `ColorOS 忽略偏大档始终用标准档底栏`() {
        val coloros = resolveMainBarLayout(
            wideScreenNavigation = null,
            explicitSize = BottomBarSize.GENEROUS,
            explicitForm = BottomBarForm.FLOATING,
            widthDp = 800,
            heightDp = 800,
            skin = UiSkin.COLOROS,
        )
        assertEquals(BottomBarSize.STANDARD, coloros.tokens.size)

        val hyperos = resolveMainBarLayout(
            wideScreenNavigation = null,
            explicitSize = BottomBarSize.GENEROUS,
            explicitForm = BottomBarForm.FLOATING,
            widthDp = 800,
            heightDp = 800,
            skin = UiSkin.HYPEROS,
        )
        assertEquals(BottomBarSize.GENEROUS, hyperos.tokens.size)
    }

    @Test
    fun `未设置形态时底栏兜底为悬浮`() {
        val layout = resolveMainBarLayout(
            wideScreenNavigation = null,
            explicitSize = null,
            explicitForm = null,
            widthDp = 400,
            heightDp = 800,
        )
        assertEquals(BottomBarForm.FLOATING, layout.tokens.form)
    }

    @Test
    fun `显式贴底形态生效`() {
        val layout = resolveMainBarLayout(
            wideScreenNavigation = null,
            explicitSize = null,
            explicitForm = BottomBarForm.DOCKED,
            widthDp = 400,
            heightDp = 800,
        )
        assertEquals(BottomBarForm.DOCKED, layout.tokens.form)
    }

    @Test
    fun `初始选中首页`() {
        val holder = TabIndexHolder()
        assertEquals(0, holder.state(FakeMainScaffoldPort()).tab)
    }

    @Test
    fun `选页切到指定分页`() {
        val holder = TabIndexHolder()
        val subject = holder.state(FakeMainScaffoldPort())

        subject.selectTab(2)

        assertEquals(2, subject.tab)
        assertEquals(2, holder.index)
    }

    @Test
    fun `越界选页被忽略`() {
        val holder = TabIndexHolder()
        val subject = holder.state(FakeMainScaffoldPort())
        subject.selectTab(3)

        subject.selectTab(-1)
        assertEquals(3, subject.tab)

        subject.selectTab(MainTab.entries.size)
        assertEquals(3, subject.tab)
    }

    @Test
    fun `启动时拉起下载队列`() = runTest {
        val port = FakeMainScaffoldPort()
        val subject = TabIndexHolder().state(port)

        subject.start()

        assertEquals(1, port.startCalls)
    }

    @Test
    fun `未读刷新按登录状态透传`() = runTest {
        val port = FakeMainScaffoldPort()
        val subject = TabIndexHolder().state(port)

        subject.refreshUnread(true)
        subject.refreshUnread(false)

        assertEquals(listOf(true, false), port.unreadRefreshes)
    }
}
