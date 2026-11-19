package com.jm3.app.miuix.ui.forum

import androidx.compose.runtime.mutableStateOf
import com.jm3.core.model.CommentItem
import com.jm3.core.model.CommentPage
import com.jm3.core.model.ForumScope
import com.jm3.core.session.SessionOutcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import java.net.SocketTimeoutException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [ForumScaffoldState] 的纯 JVM 用例：两皮肤论坛页共用的状态编排与文案。
 *
 * 取数、提示与导航都是注入的 lambda，所以这里不碰 Android、Miuix 与 Snackbar。
 */
class ForumScaffoldTest {
    @Test
    fun 初始态没有回复目标也没有面板且规则卡折叠() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val harness = ForumHarness(scope)

        assertEquals(0, harness.state.scopeIndex)
        assertEquals(0, harness.state.clampedScopeIndex)
        assertNull(harness.state.replyTarget)
        assertFalse(harness.state.replyBusy)
        assertFalse(harness.state.composing)
        assertFalse(harness.state.composeBusy)
        assertFalse(harness.state.rulesExpanded)
        scope.cancel()
    }

    @Test
    fun 选中越界档位只按档位数夹取() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val harness = ForumHarness(scope)

        harness.state.selectScope(1)
        assertEquals(1, harness.state.scopeIndex)
        assertEquals(1, harness.state.clampedScopeIndex)

        harness.state.selectScope(9)
        assertEquals(9, harness.state.scopeIndex)
        assertEquals(ForumScope.entries.lastIndex, harness.state.clampedScopeIndex)

        harness.state.selectScope(-3)
        assertEquals(-3, harness.state.scopeIndex)
        assertEquals(0, harness.state.clampedScopeIndex)
        scope.cancel()
    }

    @Test
    fun 未登录点发帖只打开登录页且不展开面板() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val harness = ForumHarness(scope, loggedIn = false)

        harness.state.openCompose()

        assertEquals(1, harness.loginOpened)
        assertFalse(harness.state.composing)
        assertEquals(emptyList(), harness.notices)
        scope.cancel()
    }

    @Test
    fun 已登录点发帖展开面板且不跳登录页() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val harness = ForumHarness(scope)

        harness.state.openCompose()

        assertTrue(harness.state.composing)
        assertEquals(0, harness.loginOpened)
        scope.cancel()
    }

    @Test
    fun 关闭发帖面板后回到不可见() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val harness = ForumHarness(scope, seed = fields(composing = true))

        harness.state.dismissCompose()

        assertFalse(harness.state.composing)
        scope.cancel()
    }

    @Test
    fun 规则卡来回切换折叠状态() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val harness = ForumHarness(scope)

        harness.state.toggleRules()
        assertTrue(harness.state.rulesExpanded)

        harness.state.toggleRules()
        assertFalse(harness.state.rulesExpanded)
        scope.cancel()
    }

    @Test
    fun 未登录回复楼层只打开登录页且不记录目标() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val harness = ForumHarness(scope, loggedIn = false)

        harness.state.startReply(comment(id = "c1"))

        assertEquals(1, harness.loginOpened)
        assertNull(harness.state.replyTarget)
        assertEquals(emptyList(), harness.notices)
        scope.cancel()
    }

    @Test
    fun 回复没有作品归属的楼层只提示且不记录目标() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val harness = ForumHarness(scope)

        harness.state.startReply(comment(id = "c2", albumId = null))

        assertEquals(listOf(FORUM_REPLY_NO_ALBUM), harness.notices)
        assertNull(harness.state.replyTarget)
        assertEquals(0, harness.loginOpened)
        scope.cancel()
    }

    @Test
    fun 回复有作品归属的楼层记录目标且不打扰用户() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val harness = ForumHarness(scope)

        harness.state.startReply(comment(id = "c3"))

        assertEquals("c3", harness.state.replyTarget?.id)
        assertEquals(emptyList(), harness.notices)
        assertEquals(0, harness.loginOpened)
        scope.cancel()
    }

    @Test
    fun 关闭回复面板清空目标() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val harness = ForumHarness(scope, seed = fields(replyTarget = comment(id = "c4")))

        harness.state.dismissReply()

        assertNull(harness.state.replyTarget)
        scope.cancel()
    }

    @Test
    fun 没有回复目标时发送被忽略且不调接口() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val harness = ForumHarness(scope)

        harness.sendReply("说点什么")

        assertEquals(emptyList(), harness.replyCalls)
        assertEquals(emptyList(), harness.notices)
        assertEquals(0, harness.sentCallbacks)
        scope.cancel()
    }

    @Test
    fun 空白回复被忽略且不调接口() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val harness = ForumHarness(scope, seed = fields(replyTarget = comment(id = "c5")))

        harness.sendReply("   ")

        assertEquals(emptyList(), harness.replyCalls)
        assertEquals(emptyList(), harness.notices)
        scope.cancel()
    }

    @Test
    fun 回复成功后清目标并提示已发送并回调皮肤重载() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val harness = ForumHarness(scope, seed = fields(replyTarget = comment(id = "c6", albumId = "1001")))

        harness.sendReply("我是回复")

        assertEquals(listOf(Triple("1001", "我是回复", "c6")), harness.replyCalls)
        assertEquals(listOf(FORUM_REPLY_SENT), harness.notices)
        assertNull(harness.state.replyTarget)
        assertFalse(harness.state.replyBusy)
        assertEquals(1, harness.sentCallbacks)
        scope.cancel()
    }

    @Test
    fun 回复失败时保留面板并按失败类型归类文案() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val harness = ForumHarness(
            scope,
            seed = fields(replyTarget = comment(id = "c7")),
            replyOutcome = { _, _, _ ->
                SessionOutcome.Failed("read timed out", SocketTimeoutException("read timed out"))
            },
        )

        harness.sendReply("我是回复")

        assertEquals(listOf("回复失败：网络超时，请稍后重试"), harness.notices)
        assertEquals("c7", harness.state.replyTarget?.id)
        assertEquals(0, harness.sentCallbacks)
        scope.cancel()
    }

    @Test
    fun 会话过期时回复提示登录过期文案并保留目标() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val harness = ForumHarness(
            scope,
            seed = fields(replyTarget = comment(id = "c8")),
            replyOutcome = { _, _, _ -> SessionOutcome.Expired },
        )

        harness.sendReply("我是回复")

        assertEquals(listOf(SessionOutcome.Expired.message), harness.notices)
        assertEquals("c8", harness.state.replyTarget?.id)
        assertEquals(0, harness.sentCallbacks)
        scope.cancel()
    }

    @Test
    fun 失败原因为空时用皮肤传入的兜底文案() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val harness = ForumHarness(
            scope,
            seed = fields(replyTarget = comment(id = "c9")),
            failureFallback = "网络不给力，稍后再试",
            replyOutcome = { _, _, _ -> SessionOutcome.Failed(null) },
        )

        harness.sendReply("我是回复")

        assertEquals(listOf("回复失败：网络不给力，稍后再试"), harness.notices)
        scope.cancel()
    }

    @Test
    fun 回复在途时再次发送不会二次调接口() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val gate = CompletableDeferred<SessionOutcome<Unit>>()
        val harness = ForumHarness(
            scope,
            seed = fields(replyTarget = comment(id = "c10")),
            replyOutcome = { _, _, _ -> gate.await() },
        )

        harness.sendReply("第一次")
        harness.sendReply("第二次")

        assertEquals(1, harness.replyCalls.size)
        assertTrue(harness.state.replyBusy)

        gate.complete(SessionOutcome.Ok(Unit))
        testScheduler.advanceUntilIdle()
        assertFalse(harness.state.replyBusy)
        assertEquals(listOf(FORUM_REPLY_SENT), harness.notices)
        assertEquals(1, harness.sentCallbacks)
        scope.cancel()
    }

    @Test
    fun 空白标题被忽略且不调接口() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val harness = ForumHarness(scope, seed = fields(composing = true))

        harness.postTopic("  ")

        assertEquals(emptyList(), harness.postCalls)
        assertEquals(emptyList(), harness.notices)
        assertEquals(0, harness.postedCallbacks)
        scope.cancel()
    }

    @Test
    fun 发帖成功后关面板并提示并回调皮肤重载() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val harness = ForumHarness(scope, seed = fields(composing = true))

        harness.postTopic("新帖内容")

        assertEquals(listOf("新帖内容"), harness.postCalls)
        assertEquals(listOf(FORUM_POSTED), harness.notices)
        assertFalse(harness.state.composing)
        assertFalse(harness.state.composeBusy)
        assertEquals(1, harness.postedCallbacks)
        scope.cancel()
    }

    @Test
    fun 发帖失败时保留面板并回落到兜底文案() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val harness = ForumHarness(
            scope,
            seed = fields(composing = true),
            postOutcome = { SessionOutcome.Failed("HTTP 400 Bad Request", IllegalStateException("HTTP 400 Bad Request")) },
        )

        harness.postTopic("新帖内容")

        assertEquals(listOf("发布失败：请稍后重试"), harness.notices)
        assertTrue(harness.state.composing)
        assertEquals(0, harness.postedCallbacks)
        scope.cancel()
    }

    @Test
    fun 发帖在途时重复发布不会二次调接口() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val gate = CompletableDeferred<SessionOutcome<Unit>>()
        val harness = ForumHarness(
            scope,
            seed = fields(composing = true),
            postOutcome = { gate.await() },
        )

        harness.postTopic("第一次")
        harness.postTopic("第二次")

        assertEquals(listOf("第一次"), harness.postCalls)
        assertTrue(harness.state.composeBusy)

        gate.complete(SessionOutcome.Ok(Unit))
        testScheduler.advanceUntilIdle()
        assertFalse(harness.state.composeBusy)
        assertEquals(1, harness.postedCallbacks)
        scope.cancel()
    }

    @Test
    fun 空态文案与规则提要按档位区分() {
        assertEquals("闲聊区还没有帖子", forumEmptyTitle(ForumScope.CHAT))
        assertEquals("来开第一帖，聊聊作品之外的事", forumEmptyHint(ForumScope.CHAT))
        assertEquals("暂无话题", forumEmptyTitle(ForumScope.ALL))
        assertEquals("换个档位看看，或发一条新帖", forumEmptyHint(ForumScope.ALL))
        assertEquals("暂无话题", forumEmptyTitle(ForumScope.MANHUA))
        assertEquals("换个档位看看，或发一条新帖", forumEmptyHint(ForumScope.MANHUA))
        assertEquals("闲聊区发言前请先了解规则", FORUM_RULES_HINT)
        assertEquals("禁止发布违法违规、骚扰与剧透内容；发言限 200 字，违规内容会被删除。", FORUM_RULES_TEXT)
    }

    @Test
    fun 评论页折算沿用每页十条与请求页() {
        val first = forumSlice(CommentPage(25, listOf(comment(id = "a"), comment(id = "b"))), requestPage = 1)
        assertEquals(3, first.pageCount)
        assertTrue(first.hasMore)
        assertEquals(25, first.total)
        assertEquals(1, first.page)

        val last = forumSlice(CommentPage(25, emptyList()), requestPage = 3)
        assertEquals(3, last.pageCount)
        assertFalse(last.hasMore)
        assertEquals(3, last.page)

        val unknownTotal = forumSlice(CommentPage(0, listOf(comment(id = "a"))), requestPage = 1)
        assertNull(unknownTotal.pageCount)
        assertNull(unknownTotal.total)
        assertTrue(unknownTotal.hasMore)

        val unknownTotalEmpty = forumSlice(CommentPage(0, emptyList()), requestPage = 1)
        assertNull(unknownTotalEmpty.pageCount)
        assertFalse(unknownTotalEmpty.hasMore)
    }
}

/** 把注入点全部换成可断言的记录器，避免每个用例重复接线。 */
private class ForumHarness(
    scope: CoroutineScope,
    seed: ForumScaffoldFields = fields(),
    loggedIn: Boolean = true,
    failureFallback: String = FORUM_FAILURE_FALLBACK,
    replyOutcome: suspend (String, String, String) -> SessionOutcome<Unit> = { _, _, _ -> SessionOutcome.Ok(Unit) },
    postOutcome: suspend (String) -> SessionOutcome<Unit> = { SessionOutcome.Ok(Unit) },
) {
    val notices = mutableListOf<String>()
    val replyCalls = mutableListOf<Triple<String, String, String>>()
    val postCalls = mutableListOf<String>()

    var loginOpened = 0
        private set
    var sentCallbacks = 0
        private set
    var postedCallbacks = 0
        private set

    val state = ForumScaffoldState(
        scope = scope,
        fields = seed,
        loggedIn = { loggedIn },
        openLogin = { loginOpened++ },
        notify = { message -> notices += message },
        failureFallback = failureFallback,
        replyComment = { albumId, text, commentId ->
            replyCalls += Triple(albumId, text, commentId)
            replyOutcome(albumId, text, commentId)
        },
        postComment = { text ->
            postCalls += text
            postOutcome(text)
        },
    )

    fun sendReply(text: String) = state.sendReply(text) { sentCallbacks++ }

    fun postTopic(text: String) = state.postTopic(text) { postedCallbacks++ }
}

private fun comment(id: String, albumId: String? = "1001"): CommentItem = CommentItem(
    id = id,
    author = "吧友",
    content = "楼层正文",
    postDate = null,
    likes = 0,
    replies = emptyList(),
    albumId = albumId,
)

private fun fields(
    scopeIndex: Int = 0,
    replyTarget: CommentItem? = null,
    replyBusy: Boolean = false,
    composing: Boolean = false,
    composeBusy: Boolean = false,
    rulesExpanded: Boolean = false,
): ForumScaffoldFields = ForumScaffoldFields(
    scopeIndexState = mutableStateOf(scopeIndex),
    replyTargetState = mutableStateOf(replyTarget),
    replyBusyState = mutableStateOf(replyBusy),
    composingState = mutableStateOf(composing),
    composeBusyState = mutableStateOf(composeBusy),
    rulesExpandedState = mutableStateOf(rulesExpanded),
)
