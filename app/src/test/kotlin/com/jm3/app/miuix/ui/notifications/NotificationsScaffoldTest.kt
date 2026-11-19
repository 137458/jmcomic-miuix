package com.jm3.app.miuix.ui.notifications

import com.jm3.core.NOTIFICATION_TYPE_FOLLOW
import com.jm3.core.model.NotificationItem
import com.jm3.core.model.NotificationPage
import com.jm3.core.session.SessionOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 信箱页状态编排骨架的契约。
 *
 * 两份皮肤共用同一套分类、文案与装载状态，所以这里钉住：装载三态（检查中/成功/失效与失败）的
 * 落点、过期响应的丢弃、标记已读的串行与翻转、账号切换的清空规则，以及各条文案的逐字输出。
 */
class NotificationsScaffoldTest {

    private fun item(id: String, read: Boolean = false, type: String? = null) = NotificationItem(
        id = id,
        title = "标题 $id",
        content = "",
        time = null,
        read = read,
        type = type,
    )

    private fun pageOf(vararg items: NotificationItem) = NotificationPage(total = items.size, items = items.toList())

    private fun loadedState(vararg items: NotificationItem): NotificationListState =
        NotificationListState()
            .loadStarted(showLoading = true)
            .loadDelivered(
                token = 1,
                outcome = SessionOutcome.Ok(pageOf(*items)),
                clearFailureOnKeptContent = false,
            )!!
            .state

    @Test
    fun `初始态是空列表且正在转圈`() {
        val idle = NotificationListState()

        assertTrue(idle.loading)
        assertNull(idle.page)
        assertTrue(idle.items.isEmpty())
        assertEquals(0, idle.unread)
        assertEquals("", idle.subtitle)
        assertEquals(listOf("全部", "追更", "站内"), idle.tabLabels)
        assertNull(idle.error)
        assertFalse(idle.sessionExpired)
        assertFalse(idle.refreshing)
        assertNull(idle.markingId)
        assertEquals(0, idle.requestToken)
        assertNull(idle.loadedUid)
    }

    @Test
    fun `装载开始时自增代号并只在需要时转圈`() {
        val first = NotificationListState().loadStarted(showLoading = true)

        assertEquals(1, first.requestToken)
        assertTrue(first.loading)
        assertEquals(2, first.loadStarted(showLoading = true).requestToken)

        val silent = NotificationListState(loading = false).loadStarted(showLoading = false)

        assertEquals(1, silent.requestToken)
        assertFalse(silent.loading)
    }

    @Test
    fun `首次装载成功落列表并带上未读数`() {
        val start = NotificationListState().loadStarted(showLoading = true)
        val result = start.loadDelivered(
            token = 1,
            outcome = SessionOutcome.Ok(pageOf(item("a"), item("b", read = true))),
            clearFailureOnKeptContent = false,
        )

        assertNull(result!!.notice)
        val state = result.state
        assertEquals(2, state.items.size)
        assertFalse(state.loading)
        assertFalse(state.refreshing)
        assertNull(state.error)
        assertFalse(state.sessionExpired)
        assertEquals(1, state.unread)
        assertEquals("1 条未读", state.subtitle)
    }

    @Test
    fun `过期响应被整条丢弃`() {
        val stale = NotificationListState().loadStarted(showLoading = true)
        val current = stale.loadStarted(showLoading = false)

        assertNull(
            current.loadDelivered(
                token = stale.requestToken,
                outcome = SessionOutcome.Ok(pageOf(item("a"))),
                clearFailureOnKeptContent = false,
            ),
        )
        // COUI 皮肤原本不设这道守卫：过期结论照样落盘，用参数保持各自行为
        assertEquals(
            1,
            current.loadDelivered(
                token = stale.requestToken,
                outcome = SessionOutcome.Ok(pageOf(item("a"))),
                clearFailureOnKeptContent = false,
                guardStaleToken = false,
            )!!.state.items.size,
        )
    }

    @Test
    fun `没有内容时会话失效落到整屏登录态`() {
        val state = NotificationListState().loadStarted(showLoading = true).loadDelivered(
            token = 1,
            outcome = SessionOutcome.Expired,
            clearFailureOnKeptContent = false,
        )!!.state

        assertTrue(state.sessionExpired)
        assertEquals(SessionOutcome.Expired.message, state.error)
        assertFalse(state.loading)
        assertNull(state.page)
    }

    @Test
    fun `没有内容时装载失败落到整屏错误`() {
        val state = NotificationListState().loadStarted(showLoading = true).loadDelivered(
            token = 1,
            outcome = SessionOutcome.Failed("boom"),
            clearFailureOnKeptContent = false,
        )!!.state

        assertFalse(state.sessionExpired)
        assertEquals("信箱加载失败", state.error)
        assertFalse(state.loading)
    }

    @Test
    fun `有内容时刷新失败保留列表并改成刷新失败提示`() {
        val base = loadedState(item("a"), item("b", read = true))
        val result = base.loadStarted(showLoading = false).loadDelivered(
            token = 2,
            outcome = SessionOutcome.Failed("boom"),
            clearFailureOnKeptContent = false,
        )!!

        assertEquals(2, result.state.items.size)
        assertFalse(result.state.loading)
        assertFalse(result.state.refreshing)
        assertEquals("刷新失败：信箱加载失败", result.notice)
        assertNull(result.state.error)
    }

    @Test
    fun `保留内容时清不清旧错误由皮肤决定`() {
        val base = loadedState(item("a")).copy(error = "旧错误")
        val outcome = SessionOutcome.Failed("boom")

        val kept = base.loadStarted(showLoading = false).loadDelivered(2, outcome, clearFailureOnKeptContent = false)!!
        val cleared = base.loadStarted(showLoading = false).loadDelivered(2, outcome, clearFailureOnKeptContent = true)!!

        assertEquals("旧错误", kept.state.error)
        assertNull(cleared.state.error)
        assertEquals(kept.notice, cleared.notice)
    }

    @Test
    fun `标记开始后同一条在途挡住第二次点击`() {
        val state = loadedState(item("a"), item("b"))
        val marking = state.markingStarted("a")

        assertEquals("a", marking!!.markingId)
        assertNull(marking.markingStarted("b"))
    }

    @Test
    fun `标记成功把该条目标成目标读态并清在途标记`() {
        val state = loadedState(item("a"), item("b")).markingStarted("a")!!.marked("a", read = true)

        assertNull(state.markingId)
        assertTrue(state.items.first { it.id == "a" }.read)
        assertFalse(state.items.first { it.id == "b" }.read)
        assertEquals(1, state.unread)
    }

    @Test
    fun `标记落定但结论被丢弃时只清在途标记`() {
        val state = loadedState(item("a")).markingStarted("a")!!.markingCleared()

        assertNull(state.markingId)
        assertFalse(state.items.first().read)
        assertEquals(1, state.unread)
    }

    @Test
    fun `展开态在同一条上往返切换`() {
        val opened = notificationExpandedToggled(emptySet(), "a")
        val both = notificationExpandedToggled(opened, "b")
        val closedB = notificationExpandedToggled(both, "b")

        assertEquals(setOf("a"), opened)
        assertEquals(setOf("a", "b"), both)
        // 往返：同一条第二次点击移除它，且不牵连其他条目
        assertEquals(setOf("a"), closedB)
        assertEquals(emptySet(), notificationExpandedToggled(opened, "a"))
        // 再点回来必须复原成同一集合（不是单向加）
        assertEquals(both, notificationExpandedToggled(closedB, "b"))
    }

    @Test
    fun `登出清空列表并作废旧请求`() {
        val base = loadedState(item("a"), item("b")).copy(markingId = "a", refreshing = true)
        val cleared = base.accountCleared()

        assertNull(cleared.page)
        assertFalse(cleared.loading)
        assertFalse(cleared.refreshing)
        assertNull(cleared.markingId)
        assertNull(cleared.loadedUid)
        assertFalse(cleared.sessionExpired)
        assertEquals(base.requestToken + 1, cleared.requestToken)
    }

    @Test
    fun `换号只在真的换人时清空列表`() {
        val base = loadedState(item("a")).accountSwitched("u1")

        assertEquals(1, base.accountSwitched("u1").items.size)
        val switched = base.accountSwitched("u2")

        assertNull(switched.page)
        assertEquals("u2", switched.loadedUid)
        assertFalse(switched.sessionExpired)
    }

    @Test
    fun `是否需要装载按内容与账号判定`() {
        assertTrue(NotificationListState().needsLoadFor("u1"))

        val loaded = loadedState(item("a")).accountSwitched("u1")

        assertFalse(loaded.needsLoadFor("u1"))
        assertTrue(loaded.needsLoadFor("u2"))
        assertTrue(loaded.copy(sessionExpired = true).needsLoadFor("u1"))
        assertTrue(loaded.copy(error = "旧错误").needsLoadFor("u1"))
    }

    @Test
    fun `页签标签带未读计数并按类型分组`() {
        val items = listOf(
            item("a", read = false, type = NOTIFICATION_TYPE_FOLLOW),
            item("b", read = true, type = "site_notice"),
            item("c", read = false, type = "site_notice"),
        )

        assertEquals(listOf("全部 2", "追更 1", "站内 1"), notificationTabLabels(items))
    }

    @Test
    fun `子页签只筛自己的类型`() {
        val items = listOf(
            item("a", type = NOTIFICATION_TYPE_FOLLOW),
            item("b", type = "site_notice"),
            item("c", type = null),
        )

        assertEquals(3, notificationItemsFor(items, NotificationTab.ALL).size)
        assertEquals(listOf("a"), notificationItemsFor(items, NotificationTab.FOLLOW).map { it.id })
        assertEquals(listOf("b"), notificationItemsFor(items, NotificationTab.NOTICE).map { it.id })
    }

    @Test
    fun `空态文案按页签给出去处`() {
        assertEquals("暂无站内消息", notificationEmptyHint(NotificationTab.NOTICE))
        assertEquals("订阅作品更新后会在这里收到通知", notificationEmptyHint(NotificationTab.FOLLOW))
        assertEquals("订阅更新与站内消息都会出现在这里", notificationEmptyHint(NotificationTab.ALL))
    }

    @Test
    fun `类型标签与展开文案逐字对应`() {
        assertEquals("漫画追踪通知", notificationTypeLabel(NOTIFICATION_TYPE_FOLLOW))
        assertEquals("站内通知", notificationTypeLabel("site_notice"))
        assertEquals("站内通知", notificationTypeLabel(null))

        assertEquals("展开全部 5 条", notificationUpdatesToggleText(expanded = false, count = 5))
        assertEquals("收起", notificationUpdatesToggleText(expanded = true, count = 5))
        assertEquals(2, NOTIFICATION_PREVIEW_UPDATES)
    }

    @Test
    fun `标记动作文案按在途与读态取词`() {
        assertEquals("处理中…", notificationMarkActionText(marking = true, read = true))
        assertEquals("处理中…", notificationMarkActionText(marking = true, read = false))
        assertEquals("标记未读", notificationMarkActionText(marking = false, read = true))
        assertEquals("标记已读", notificationMarkActionText(marking = false, read = false))
    }

    @Test
    fun `副标题只在与列表有关时给字`() {
        assertEquals("", notificationSubtitle(page = null, unread = 3))
        assertEquals("全部已读", notificationSubtitle(page = pageOf(item("a", read = true)), unread = 0))
        assertEquals("2 条未读", notificationSubtitle(page = pageOf(item("a"), item("b")), unread = 2))
    }
}
