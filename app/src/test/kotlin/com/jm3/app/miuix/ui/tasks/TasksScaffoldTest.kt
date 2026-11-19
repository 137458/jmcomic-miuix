package com.jm3.app.miuix.ui.tasks

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import com.jm3.app.miuix.Route
import com.jm3.app.miuix.ui.list.ListLoadState
import com.jm3.app.miuix.ui.route.AppRoutes
import com.jm3.core.model.Jm3User
import com.jm3.core.model.TaskFilter
import com.jm3.core.model.TaskItem
import com.jm3.core.model.TaskPage
import com.jm3.core.model.TaskType
import com.jm3.core.session.SessionOutcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [TasksScaffoldState] 的纯 JVM 用例：两皮肤任务页共用的状态编排、佩戴门禁与文案。
 *
 * 取数、佩戴、域名解析、提示与导航都是注入的 lambda，所以这里不碰 Android 与 Miuix；
 * 两皮肤的真实差异（整屏失败文案、域名解析时机、总页数）全部由用例参数摆出。
 */
class TasksScaffoldTest {

    @Test
    fun 初始态没有页内文案也没有佩戴覆盖() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val harness = TasksHarness(scope)

        assertEquals(listOf(TaskType.TITLE, TaskType.BADGE), harness.state.types)
        assertEquals(listOf(TaskFilter.MY, TaskFilter.ALL), harness.state.filters)
        assertEquals(0, harness.state.clampedTypeIndex)
        assertEquals(0, harness.state.clampedFilterIndex)
        assertEquals("", harness.state.imageHost)
        assertNull(harness.state.equippedTitleOverride)
        assertNull(harness.state.equippedBadgeId)
        assertNull(harness.state.claimingId)
        assertNull(harness.state.extra(0))
        assertNull(harness.state.extra(1))
        assertEquals(2, harness.state.loaders.size)
        assertEquals(ListLoadState.Loading, harness.state.loaders[0].state)
        assertTrue(harness.state.loaders[0].feed.items.isEmpty())
        scope.cancel()
    }

    @Test
    fun 选中越界档位只按档位数夹取() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val harness = TasksHarness(scope)

        harness.state.selectType(1)
        assertEquals(1, harness.state.clampedTypeIndex)

        harness.state.selectType(7)
        assertEquals(TaskType.entries.lastIndex, harness.state.clampedTypeIndex)

        harness.state.selectFilter(-2)
        assertEquals(0, harness.state.clampedFilterIndex)
        scope.cancel()
    }

    @Test
    fun 首次进入该档只发起一次首屏请求并通知皮肤复位() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val harness = TasksHarness(scope)

        harness.state.enterTab()
        harness.state.enterTab()

        assertEquals(listOf(TaskType.TITLE to TaskFilter.MY), harness.fetchCalls)
        assertEquals(listOf(0), harness.tabStarts)
        assertEquals(ListLoadState.Loaded, harness.state.loaders[0].state)
        assertEquals(ListLoadState.Loading, harness.state.loaders[1].state)
        scope.cancel()
    }

    @Test
    fun 切到另一档各自只取一次首屏() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val harness = TasksHarness(scope)

        harness.state.enterTab()
        harness.state.selectType(1)
        harness.state.enterTab()
        harness.state.enterTab()

        assertEquals(listOf(TaskType.TITLE to TaskFilter.MY, TaskType.BADGE to TaskFilter.MY), harness.fetchCalls)
        assertEquals(listOf(0, 1), harness.tabStarts)
        assertEquals(ListLoadState.Loaded, harness.state.loaders[1].state)
        scope.cancel()
    }

    @Test
    fun 取回成功装载条目并按档记下服务端文案() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val harness = TasksHarness(
            scope,
            hostResolution = TaskHostResolution.BeforeRequest,
            pageCount = 1,
            pageOutcome = { _, _ -> SessionOutcome.Ok(taskPage(message = "暂未登录")) },
        )

        harness.state.enterTab()

        assertEquals(listOf(taskItem()), harness.state.loaders[0].feed.items)
        assertEquals(ListLoadState.Loaded, harness.state.loaders[0].state)
        assertEquals(TaskFeedExtra(message = "暂未登录", imageHost = "img.example.com"), harness.state.extra(0))
        assertEquals(1, harness.state.loaders[0].feed.page)
        assertFalse(harness.state.loaders[0].feed.canLoadMore)
        scope.cancel()
    }

    @Test
    fun 取回失败整屏给屏内文案且不写页内附加() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val harness = TasksHarness(scope, pageOutcome = { _, _ -> SessionOutcome.Failed(message = null, cause = null) })

        harness.state.enterTab()

        assertEquals(ListLoadState.Failed(FAILURE_MESSAGE), harness.state.loaders[0].state)
        assertNull(harness.state.extra(0))
        assertTrue(harness.state.loaders[0].feed.items.isEmpty())
        scope.cancel()
    }

    @Test
    fun 会话失效整屏给登录文案并标记失效() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val harness = TasksHarness(scope, pageOutcome = { _, _ -> SessionOutcome.Expired })

        harness.state.enterTab()

        assertEquals(
            ListLoadState.Failed(message = SessionOutcome.Expired.message, sessionExpired = true),
            harness.state.loaders[0].state,
        )
        scope.cancel()
    }

    @Test
    fun 失败后重试成功重新装载() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        var failing = true
        val harness = TasksHarness(
            scope,
            pageOutcome = { _, _ ->
                if (failing) SessionOutcome.Failed(message = null, cause = null) else SessionOutcome.Ok(taskPage(message = "已登录"))
            },
        )

        harness.state.enterTab()
        assertEquals(ListLoadState.Failed(FAILURE_MESSAGE), harness.state.loaders[0].state)

        failing = false
        harness.state.loaders[0].loadFirstPage()

        assertEquals(ListLoadState.Loaded, harness.state.loaders[0].state)
        assertEquals(listOf(taskItem()), harness.state.loaders[0].feed.items)
        assertEquals(TaskFeedExtra(message = "已登录", imageHost = "img.example.com"), harness.state.extra(0))
        scope.cancel()
    }

    @Test
    fun 请求前解析域名的皮肤连失败也把域名落到整屏态() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val harness = TasksHarness(
            scope,
            hostResolution = TaskHostResolution.BeforeRequest,
            pageOutcome = { _, _ -> SessionOutcome.Failed(message = null, cause = null) },
        )

        harness.state.enterTab()

        assertEquals("img.example.com", harness.state.imageHost)
        assertEquals(listOf(TaskHostResolution.BeforeRequest), harness.hostRequests)
        assertEquals(ListLoadState.Failed(FAILURE_MESSAGE), harness.state.loaders[0].state)
        scope.cancel()
    }

    @Test
    fun 成功后再解析域名的皮肤按档记录且不动整屏态() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val harness = TasksHarness(scope, hostResolution = TaskHostResolution.AfterSuccess)

        harness.state.enterTab()

        assertEquals("", harness.state.imageHost)
        assertEquals(TaskFeedExtra(message = null, imageHost = "img.example.com"), harness.state.extra(0))
        assertEquals(listOf(TaskHostResolution.AfterSuccess), harness.hostRequests)
        scope.cancel()
    }

    @Test
    fun 换筛选清空页内文案并让皮肤复位视觉() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val fields = tasksFields()
        val first = TasksHarness(
            scope,
            fields = fields,
            pageOutcome = { _, _ -> SessionOutcome.Ok(taskPage(message = "暂未登录")) },
        )
        first.state.enterTab()
        first.state.selectType(1)
        first.state.enterTab()
        assertEquals("暂未登录", first.state.extra(1)?.message)

        first.state.selectType(0)
        first.state.selectFilter(1)
        // 换筛选会让皮肤侧重建骨架：新骨架共用同一份可保存字段
        val second = TasksHarness(scope, fields = fields)
        second.state.enterTab()

        // 另一档的旧文案随换筛选整组作废，本次这一档重新记一遍
        assertNull(second.state.extra(1))
        assertEquals(TaskFeedExtra(message = null, imageHost = "img.example.com"), second.state.extra(0))
        assertEquals(1, second.filterResets)
        assertEquals(listOf(TaskType.TITLE to TaskFilter.ALL), second.fetchCalls)
        scope.cancel()
    }

    @Test
    fun 换账号清掉旧账号的佩戴覆盖() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val fields = tasksFields(equipped = TasksEquipped(title = "读书人", badgeId = "b7"))
        val harness = TasksHarness(scope, fields = fields)
        assertEquals("读书人", harness.state.equippedTitleOverride)

        harness.state.resetEquipped()

        assertNull(harness.state.equippedTitleOverride)
        assertNull(harness.state.equippedBadgeId)
        scope.cancel()
    }

    @Test
    fun 佩戴称号成功写覆盖并各触发一次触觉与提示() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val harness = TasksHarness(scope)

        harness.state.claim(TaskType.TITLE, taskItem(id = "t1", name = "读书人", content = "读书人"))

        assertEquals(listOf(Triple(TaskType.TITLE, "uid-1", "t1")), harness.claimCalls)
        assertEquals("读书人", harness.state.equippedTitleOverride)
        assertNull(harness.state.equippedBadgeId)
        assertNull(harness.state.claimingId)
        assertEquals(1, harness.haptics)
        assertEquals(listOf("已佩戴「读书人」"), harness.notices)
        scope.cancel()
    }

    @Test
    fun 佩戴勋章成功只写勋章id不动称号覆盖() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val harness = TasksHarness(scope)

        harness.state.claim(TaskType.BADGE, taskItem(id = "b7", name = "初来乍到"))

        assertEquals("b7", harness.state.equippedBadgeId)
        assertNull(harness.state.equippedTitleOverride)
        assertEquals(listOf("已佩戴「初来乍到」"), harness.notices)
        scope.cancel()
    }

    @Test
    fun 佩戴在途时重复点击不再发请求() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val gate = CompletableDeferred<SessionOutcome<String?>>()
        val harness = TasksHarness(scope, claimOutcome = { _, _, _ -> gate.await() })

        harness.state.claim(TaskType.TITLE, taskItem(id = "t1"))
        harness.state.claim(TaskType.TITLE, taskItem(id = "t2"))

        assertEquals(listOf(Triple(TaskType.TITLE, "uid-1", "t1")), harness.claimCalls)
        assertEquals("t1", harness.state.claimingId)

        gate.complete(SessionOutcome.Ok(null))
        testScheduler.advanceUntilIdle()

        assertNull(harness.state.claimingId)
        assertEquals("读书人", harness.state.equippedTitleOverride)
        scope.cancel()
    }

    @Test
    fun 未登录点佩戴只打开登录页且不发请求() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val harness = TasksHarness(scope, user = null)

        harness.state.claim(TaskType.TITLE, taskItem())

        assertEquals(listOf<Route>(Route.Login), harness.pushed)
        assertTrue(harness.claimCalls.isEmpty())
        assertTrue(harness.notices.isEmpty())
        assertNull(harness.state.claimingId)
        scope.cancel()
    }

    @Test
    fun 佩戴被服务端拒绝时不动覆盖且提示服务端原因() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val harness = TasksHarness(scope, claimOutcome = { _, _, _ -> SessionOutcome.Ok("没有该成就") })

        harness.state.claim(TaskType.TITLE, taskItem())

        assertNull(harness.state.equippedTitleOverride)
        assertEquals(0, harness.haptics)
        assertEquals(listOf("切换失败：没有该成就"), harness.notices)
        scope.cancel()
    }

    @Test
    fun 佩戴失败时提示兜底文案() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val harness = TasksHarness(
            scope,
            failureFallback = "请稍后重试",
            claimOutcome = { _, _, _ -> SessionOutcome.Failed(message = null, cause = null) },
        )

        harness.state.claim(TaskType.TITLE, taskItem())

        assertEquals(listOf("切换失败：请稍后重试"), harness.notices)
        assertNull(harness.state.equippedTitleOverride)
        assertEquals(0, harness.haptics)
        scope.cancel()
    }

    @Test
    fun 佩戴会话失效时提示重新登录() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val harness = TasksHarness(scope, claimOutcome = { _, _, _ -> SessionOutcome.Expired })

        harness.state.claim(TaskType.TITLE, taskItem())

        assertEquals(listOf("切换失败：" + TASK_CLAIM_EXPIRED), harness.notices)
        assertNull(harness.state.equippedTitleOverride)
        scope.cancel()
    }

    @Test
    fun 空态标题与提示按档位区分() {
        assertEquals("暂无称号", tasksEmptyTitle(TaskType.TITLE))
        assertEquals("继续阅读与收藏即可解锁称号", tasksEmptyHint(TaskType.TITLE))
        assertEquals("暂无勋章", tasksEmptyTitle(TaskType.BADGE))
        assertEquals("完成成就后可在此获得勋章", tasksEmptyHint(TaskType.BADGE))
        assertEquals("已达成", TASK_STATE_DONE)
        assertEquals("未达成", TASK_STATE_UNDONE)
    }

    @Test
    fun 佩戴判定称号按内容勋章按id() {
        val item = taskItem(id = "t1", content = "读书人")

        assertTrue(isTaskEquipped(TaskType.TITLE, item, equippedTitle = "读书人", equippedBadgeId = null))
        assertFalse(isTaskEquipped(TaskType.TITLE, item, equippedTitle = "大佬", equippedBadgeId = "t1"))
        assertFalse(isTaskEquipped(TaskType.TITLE, item, equippedTitle = null, equippedBadgeId = "t1"))
        assertTrue(isTaskEquipped(TaskType.BADGE, item, equippedTitle = "读书人", equippedBadgeId = "t1"))
        assertFalse(isTaskEquipped(TaskType.BADGE, item, equippedTitle = "读书人", equippedBadgeId = "b9"))
        assertFalse(isTaskEquipped(TaskType.BADGE, item, equippedTitle = null, equippedBadgeId = null))
    }

    @Test
    fun 佩戴门禁未登录先登录在途忽略() {
        assertEquals(TasksClaimGate.Login, gateTaskClaim(uid = null, claimingId = null))
        assertEquals(TasksClaimGate.Login, gateTaskClaim(uid = "  ", claimingId = "t1"))
        assertEquals(TasksClaimGate.Busy, gateTaskClaim(uid = "uid-1", claimingId = "t1"))
        assertEquals(TasksClaimGate.Start("uid-1"), gateTaskClaim(uid = "uid-1", claimingId = null))
    }

    @Test
    fun 佩戴落地成功写覆盖并提示已佩戴() {
        assertEquals(
            TasksClaimPlan.Equipped(equipped = TasksEquipped(title = "读书人"), message = "已佩戴「读书人」"),
            planTaskClaim(
                equipped = TasksEquipped(),
                type = TaskType.TITLE,
                item = taskItem(name = "读书人", content = "读书人"),
                outcome = SessionOutcome.Ok(null),
                failureFallback = "请稍后重试",
            ),
        )
        // 佩戴只写自己那一档：原实现每档一个变量，佩戴勋章不动称号覆盖
        assertEquals(
            TasksClaimPlan.Equipped(
                equipped = TasksEquipped(title = "读书人", badgeId = "b7"),
                message = "已佩戴「初来乍到」",
            ),
            planTaskClaim(
                equipped = TasksEquipped(title = "读书人"),
                type = TaskType.BADGE,
                item = taskItem(id = "b7", name = "初来乍到"),
                outcome = SessionOutcome.Ok(null),
                failureFallback = "请稍后重试",
            ),
        )
        // 反向同理：佩戴称号保留已有勋章 id
        assertEquals(
            TasksClaimPlan.Equipped(
                equipped = TasksEquipped(title = "读书人", badgeId = "b7"),
                message = "已佩戴「读书人」",
            ),
            planTaskClaim(
                equipped = TasksEquipped(badgeId = "b7"),
                type = TaskType.TITLE,
                item = taskItem(name = "读书人", content = "读书人"),
                outcome = SessionOutcome.Ok(null),
                failureFallback = "请稍后重试",
            ),
        )
    }

    @Test
    fun 佩戴落地失败保留服务端文案与兜底() {
        assertEquals(
            TasksClaimPlan.Rejected("切换失败：没有该成就"),
            planTaskClaim(
                equipped = TasksEquipped(),
                type = TaskType.TITLE,
                item = taskItem(),
                outcome = SessionOutcome.Ok("没有该成就"),
                failureFallback = "请稍后重试",
            ),
        )
        assertEquals(
            TasksClaimPlan.Rejected("切换失败：" + TASK_CLAIM_EXPIRED),
            planTaskClaim(
                equipped = TasksEquipped(),
                type = TaskType.TITLE,
                item = taskItem(),
                outcome = SessionOutcome.Expired,
                failureFallback = "请稍后重试",
            ),
        )
        assertEquals(
            TasksClaimPlan.Rejected("切换失败：请稍后重试"),
            planTaskClaim(
                equipped = TasksEquipped(),
                type = TaskType.TITLE,
                item = taskItem(),
                outcome = SessionOutcome.Failed(message = null, cause = null),
                failureFallback = "请稍后重试",
            ),
        )
    }

    @Test
    fun 页内附加信息按档记录后取覆盖先取且可整组作废() {
        val extras = TaskFeedExtras()
            .recorded(0, TaskFeedExtra(message = "暂未登录", imageHost = "a.example.com"))
            .recorded(1, TaskFeedExtra(imageHost = "b.example.com"))

        assertEquals(TaskFeedExtra(message = "暂未登录", imageHost = "a.example.com"), extras[0])
        assertEquals(TaskFeedExtra(imageHost = "b.example.com"), extras[1])
        assertNull(TaskFeedExtras()[0])

        val replaced = extras.recorded(0, TaskFeedExtra(message = "换一句"))
        assertEquals(TaskFeedExtra(message = "换一句"), replaced[0])
        assertEquals(TaskFeedExtra(imageHost = "b.example.com"), replaced[1])

        assertNull(extras.cleared()[0])
        assertNull(extras.cleared()[1])
    }

    @Test
    fun 成就切片永远只有第一页且不带下一页() {
        val slice = tasksFeedSlice(listOf(taskItem()), pageCount = 1)

        assertEquals(listOf(taskItem()), slice.items)
        assertEquals(1, slice.page)
        assertEquals(1, slice.pageCount)
        assertFalse(slice.hasMore)
        assertNull(slice.total)

        val unknownPageCount = tasksFeedSlice(emptyList(), pageCount = null)
        assertEquals(1, unknownPageCount.page)
        assertNull(unknownPageCount.pageCount)
        assertFalse(unknownPageCount.hasMore)
        assertTrue(unknownPageCount.items.isEmpty())
    }
}

/** 把注入点全部换成可断言的记录器，避免每个用例重复接线。 */
private class TasksHarness(
    scope: CoroutineScope,
    val fields: TasksScaffoldFields = tasksFields(),
    user: Jm3User? = taskUser(),
    failureFallback: String = "请稍后重试",
    hostResolution: TaskHostResolution = TaskHostResolution.BeforeRequest,
    pageCount: Int? = null,
    resolvedImageHost: String = "img.example.com",
    pageOutcome: suspend (TaskType, TaskFilter) -> SessionOutcome<TaskPage> = { _, _ -> SessionOutcome.Ok(taskPage()) },
    claimOutcome: suspend (TaskType, String, String) -> SessionOutcome<String?> = { _, _, _ -> SessionOutcome.Ok(null) },
) {
    val notices = mutableListOf<String>()
    val fetchCalls = mutableListOf<Pair<TaskType, TaskFilter>>()
    val claimCalls = mutableListOf<Triple<TaskType, String, String>>()
    val hostRequests = mutableListOf<TaskHostResolution>()
    val pushed = mutableListOf<Route>()
    val tabStarts = mutableListOf<Int>()

    var filterResets = 0
        private set
    var haptics = 0
        private set

    val state = TasksScaffoldState(
        scope = scope,
        fields = fields,
        failureMessage = FAILURE_MESSAGE,
        failureFallback = failureFallback,
        hostResolution = hostResolution,
        pageCount = pageCount,
        userRef = mutableStateOf<Jm3User?>(user),
        hapticRef = mutableStateOf<() -> Unit>({ haptics++ }),
        filterResetRef = mutableStateOf<() -> Unit>({ filterResets++ }),
        tabStartedRef = mutableStateOf<(Int) -> Unit>({ tab -> tabStarts += tab }),
        notify = { message -> notices += message },
        fetchTaskPage = { type, filter ->
            fetchCalls += type to filter
            pageOutcome(type, filter)
        },
        claimTask = { type, uid, taskId ->
            claimCalls += Triple(type, uid, taskId)
            claimOutcome(type, uid, taskId)
        },
        resolveImageHost = { resolution ->
            hostRequests += resolution
            resolvedImageHost
        },
        nav = AppRoutes(push = { route -> pushed += route }, replaceTop = { }),
    )
}

private const val FAILURE_MESSAGE = "成就列表加载失败"

private fun tasksFields(
    typeIndex: Int = 0,
    filterIndex: Int = 0,
    appliedFilterIndex: Int = 0,
    equipped: TasksEquipped = TasksEquipped(),
    claimingId: String? = null,
    extras: TaskFeedExtras = TaskFeedExtras(),
    imageHost: String = "",
): TasksScaffoldFields = TasksScaffoldFields(
    typeIndex = mutableIntStateOf(typeIndex),
    filterIndex = mutableIntStateOf(filterIndex),
    appliedFilterIndex = mutableIntStateOf(appliedFilterIndex),
    equipped = mutableStateOf(equipped),
    claimingId = mutableStateOf(claimingId),
    extras = mutableStateOf(extras),
    imageHost = mutableStateOf(imageHost),
)

private fun taskUser(uid: String = "uid-1", levelName: String? = "读书人"): Jm3User = Jm3User(
    uid = uid,
    username = "鲸娘",
    email = null,
    emailVerified = false,
    photoUrl = null,
    coin = 0,
    albumFavorites = 0,
    maxAlbumFavorites = 0,
    level = 3,
    levelName = levelName,
    nextLevelExp = 0L,
    currentExp = 0L,
    expPercent = 0,
)

private fun taskItem(
    id: String = "t1",
    name: String = "读书人",
    content: String = "读书人",
    done: Boolean = true,
): TaskItem = TaskItem(id = id, name = name, content = content, coin = 0, done = done, type = null)

private fun taskPage(
    items: List<TaskItem> = listOf(taskItem()),
    message: String? = null,
): TaskPage = TaskPage(items = items, message = message)
