package com.jm3.app.miuix.ui

import com.jm3.core.AlbumDetailPort
import com.jm3.core.history.HistoryEntry
import com.jm3.core.history.InMemoryHistoryRepository
import com.jm3.core.model.AlbumDetail
import com.jm3.core.model.Chapter
import com.jm3.core.model.CommentItem
import com.jm3.core.model.CommentPage
import com.jm3.core.model.FavoriteFolderAction
import com.jm3.core.model.FavoritePage
import com.jm3.core.model.FolderOperation
import com.jm3.core.session.AuthCall
import com.jm3.core.session.SessionOutcome
import com.jm3.core.session.StartupGate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 启动闸门在测试里只负责放行与记失效次数。 */
private object NoopGate : StartupGate {
    override suspend fun awaitReady() = Unit
    override suspend fun onSessionExpired() = Unit
}

@OptIn(ExperimentalCoroutinesApi::class)
class AlbumDetailStateHolderTest {

    /** 记录失效通知次数的闸门：验证失效时账号层确实收到了通知。 */
    private class CountingGate : StartupGate {
        var expiredCalls = 0
        override suspend fun awaitReady() = Unit
        override suspend fun onSessionExpired() {
            expiredCalls++
        }
    }


    private fun fakeGateway(
        onAlbum: (String) -> AlbumDetail = { error("album not implemented") },
        onComments: (String, Int) -> CommentPage = { _, _ -> CommentPage(0, emptyList()) },
        onPostComment: (String, String) -> Unit = { _, _ -> },
        onReplyComment: (String, String, String) -> Unit = { _, _, _ -> },
        onFavorites: (Int) -> FavoritePage = { FavoritePage(1, 1, emptyList()) },
        onToggleFavorite: (String, String) -> Unit = { _, _ -> },
        onManageFolder: (FavoriteFolderAction, String, String, String) -> FolderOperation = { _, _, _, _ -> FolderOperation(true, "ok") },
        onToggleLike: (String) -> Unit = {},
        onIsTracking: (String) -> Boolean = { false },
        onSetTracking: (String) -> Unit = {},
    ): AlbumDetailPort = object : AlbumDetailPort {
        override fun album(albumId: String): AlbumDetail = onAlbum(albumId)
        override fun comments(albumId: String, page: Int): CommentPage = onComments(albumId, page)
        override fun postComment(albumId: String, text: String) = onPostComment(albumId, text)
        override fun replyComment(albumId: String, text: String, parentCommentId: String) = onReplyComment(albumId, text, parentCommentId)
        override fun toggleLike(albumId: String) = onToggleLike(albumId)
        override fun isTracking(albumId: String): Boolean = onIsTracking(albumId)
        override fun setTracking(albumId: String) = onSetTracking(albumId)
        override fun favorites(page: Int): FavoritePage = onFavorites(page)
        override fun manageFavoriteFolder(
            action: FavoriteFolderAction,
            folderId: String,
            name: String,
            albumId: String,
        ): FolderOperation = onManageFolder(action, folderId, name, albumId)
        override fun toggleFavorite(albumId: String, folderId: String) = onToggleFavorite(albumId, folderId)
    }

    private fun sampleAlbum(id: String = "123", favorited: Boolean = false, liked: Boolean = false) = AlbumDetail(
        id = id,
        title = "测试相册",
        description = "简介测试",
        authors = listOf("作者"),
        tags = listOf("同人", "恋爱"),
        works = emptyList(),
        actors = emptyList(),
        likes = "100",
        views = "200",
        coverUrl = "https://example.com/cover.jpg",
        chapters = (1..25).map { Chapter(id = "p$it", title = "第 $it 话", sortOrder = it) },
        favorited = favorited,
        liked = liked,
        related = emptyList(),
    )

    @Test
    fun 初始加载成功更新相册详情与章节展开组() = runTest {
        val testScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val messages = mutableListOf<String>()
        var clockTime = 100L
        val history = InMemoryHistoryRepository(clock = { clockTime += 1000L; clockTime }).apply {
            upsert(HistoryEntry(photoId = "p1", albumId = "123", title = "第 1 话", coverUrl = null, updatedAt = 100L))
            upsert(HistoryEntry(photoId = "p15", albumId = "123", title = "第 15 话", coverUrl = null, updatedAt = 200L))
        }

        val gateway = fakeGateway(
            onAlbum = { sampleAlbum(it, favorited = true, liked = false) },
            onComments = { _, _ ->
                CommentPage(
                    total = 1,
                    items = listOf(CommentItem(id = "c1", author = "用户", content = "好评", postDate = "2026-01-01", likes = 0, replies = emptyList()))
                )
            },
            onIsTracking = { true }
        )

        val holder = AlbumDetailStateHolder(
            albumId = "123",
            gateway = gateway,
            history = history,
            scope = testScope,
            showMessage = { messages.add(it) },
            authCall = AuthCall(NoopGate, UnconfinedTestDispatcher(testScheduler)),
            onOpenLogin = {},
        )

        holder.loadAll(userUid = "user_1")
        advanceUntilIdle()

        val state = holder.uiState.value
        val albumLoaded = state.albumState as AlbumUiState.Loaded
        assertEquals("测试相册", albumLoaded.detail.title)
        assertTrue(state.favorited)
        assertFalse(state.liked)
        assertTrue(state.tracking == true)
        assertEquals("p15", state.latestPhotoId)
        assertTrue(state.readPhotoIds.contains("p15"))
        // 组身份是组内首章 id：冷启动默认展开首组 p1，读完历史后追加 p15 所在的 p11 组
        assertEquals(setOf("p1", "p11"), state.expandedGroups)

        testScope.cancel()
    }

    @Test
    fun 初始加载失败置为Failed态() = runTest {
        val testScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val gateway = fakeGateway(
            onAlbum = { throw RuntimeException("网络连接异常") }
        )

        val holder = AlbumDetailStateHolder(
            albumId = "123",
            gateway = gateway,
            history = InMemoryHistoryRepository(),
            scope = testScope,
            showMessage = {},
            authCall = AuthCall(NoopGate, UnconfinedTestDispatcher(testScheduler)),
            onOpenLogin = {},
        )

        holder.loadAll(userUid = null)
        advanceUntilIdle()

        val state = holder.uiState.value
        val albumFailed = state.albumState as AlbumUiState.Failed
        assertEquals("详情加载失败", albumFailed.message)

        testScope.cancel()
    }

    @Test
    fun 未登录时点赞收藏追踪均拦截并跳转登录() = runTest {
        val testScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        var loginOpened = 0
        val holder = AlbumDetailStateHolder(
            albumId = "123",
            gateway = fakeGateway(),
            history = InMemoryHistoryRepository(),
            scope = testScope,
            showMessage = {},
            authCall = AuthCall(NoopGate, UnconfinedTestDispatcher(testScheduler)),
            onOpenLogin = {},
        )

        holder.onFavoriteClick(isLoggedIn = false, onOpenLogin = { loginOpened++ })
        holder.like(isLoggedIn = false, onOpenLogin = { loginOpened++ })
        holder.toggleTracking(isLoggedIn = false, onOpenLogin = { loginOpened++ })

        assertEquals(3, loginOpened)
        assertFalse(holder.uiState.value.showFolderSheet)

        testScope.cancel()
    }

    @Test
    fun 点赞成功更新liked且重复点击提示已喜欢() = runTest {
        val testScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val messages = mutableListOf<String>()
        var likeCalled = 0
        val gateway = fakeGateway(
            onToggleLike = { likeCalled++ }
        )
        val holder = AlbumDetailStateHolder(
            albumId = "123",
            gateway = gateway,
            history = InMemoryHistoryRepository(),
            scope = testScope,
            showMessage = { messages.add(it) },
            authCall = AuthCall(NoopGate, UnconfinedTestDispatcher(testScheduler)),
            onOpenLogin = {},
        )

        holder.like(isLoggedIn = true, onOpenLogin = {})
        advanceUntilIdle()

        assertTrue(holder.uiState.value.liked)
        assertEquals(1, likeCalled)
        assertEquals("已喜欢", messages.lastOrNull())

        // 再次点击
        holder.like(isLoggedIn = true, onOpenLogin = {})
        advanceUntilIdle()
        assertEquals(1, likeCalled, "已喜欢时不应重复向网关发起请求")
        assertEquals("已经喜欢过了", messages.lastOrNull())

        testScope.cancel()
    }

    @Test
    fun 阅读页取消喜欢后详情页可再次喜欢() = runTest {
        val testScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val messages = mutableListOf<String>()
        var likeCalled = 0
        // 阅读页先喜欢、随后取消：服务端最终是「未喜欢」，共享表已有明确结论
        val engagement = AlbumEngagementStore().apply {
            setLiked("123", true)
            setLiked("123", false)
        }
        val gateway = fakeGateway(
            // 详情首载拿到的还是取消前的旧快照 liked=true
            onAlbum = { sampleAlbum(it, liked = true) },
            onToggleLike = { likeCalled++ },
        )
        val holder = AlbumDetailStateHolder(
            albumId = "123",
            gateway = gateway,
            history = InMemoryHistoryRepository(),
            scope = testScope,
            showMessage = { messages.add(it) },
            authCall = AuthCall(NoopGate, UnconfinedTestDispatcher(testScheduler)),
            onOpenLogin = {},
            engagement = engagement,
        )

        holder.loadAll(userUid = "user_1")
        advanceUntilIdle()
        assertTrue(holder.uiState.value.liked, "旧快照仍显示已喜欢")

        holder.like(isLoggedIn = true, onOpenLogin = {})
        advanceUntilIdle()

        assertEquals(1, likeCalled, "共享表已取消喜欢时不能拿旧快照提前返回")
        assertTrue(holder.uiState.value.liked)
        assertEquals(true, engagement.likedOf("123"))
        assertEquals("已喜欢", messages.lastOrNull())

        testScope.cancel()
    }

    @Test
    fun 发表评论成功清空输入框并刷新评论列表() = runTest {
        val testScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val messages = mutableListOf<String>()
        var postText = ""
        var commentsFetchCount = 0
        val gateway = fakeGateway(
            onPostComment = { _, text -> postText = text },
            onComments = { _, _ ->
                commentsFetchCount++
                CommentPage(1, listOf(CommentItem("c1", "u1", "内容", "2026", 0, emptyList())))
            }
        )
        val holder = AlbumDetailStateHolder(
            albumId = "123",
            gateway = gateway,
            history = InMemoryHistoryRepository(),
            scope = testScope,
            showMessage = { messages.add(it) },
            authCall = AuthCall(NoopGate, UnconfinedTestDispatcher(testScheduler)),
            onOpenLogin = {},
        )

        holder.setCommentText("精彩作品！")
        holder.postComment()
        advanceUntilIdle()

        assertEquals("精彩作品！", postText)
        assertEquals("", holder.uiState.value.commentText)
        assertEquals("评论已发表", messages.lastOrNull())
        assertTrue(commentsFetchCount > 0)

        testScope.cancel()
    }

    private fun comment(id: String) = CommentItem(
        id = id,
        author = "用户 $id",
        content = "内容 $id",
        postDate = "2026-01-01",
        likes = 0,
        replies = emptyList(),
    )

    @Test
    fun 加载更多评论按id去重并在取满总数后停止请求() = runTest {
        val testScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val requestedPages = mutableListOf<Int>()
        val gateway = fakeGateway(
            onComments = { _, page ->
                requestedPages.add(page)
                when (page) {
                    1 -> CommentPage(total = 3, items = listOf(comment("c1"), comment("c2")))
                    2 -> CommentPage(total = 3, items = listOf(comment("c2"), comment("c3")))
                    else -> error("没有下一页却仍然请求了第 " + page + " 页")
                }
            }
        )
        val holder = AlbumDetailStateHolder(
            albumId = "123",
            gateway = gateway,
            history = InMemoryHistoryRepository(),
            scope = testScope,
            showMessage = {},
            authCall = AuthCall(NoopGate, UnconfinedTestDispatcher(testScheduler)),
            onOpenLogin = {},
        )

        holder.loadAll(userUid = null)
        advanceUntilIdle()
        val firstFeed = (holder.uiState.value.commentsState as CommentsUiState.Loaded).feed
        assertEquals(listOf("c1", "c2"), firstFeed.items.map { it.id })
        assertTrue(firstFeed.canLoadMore)

        holder.loadMoreComments()
        advanceUntilIdle()
        val secondFeed = (holder.uiState.value.commentsState as CommentsUiState.Loaded).feed
        assertEquals(listOf("c1", "c2", "c3"), secondFeed.items.map { it.id }, "重复的 c2 不应被追加第二次")
        assertFalse(secondFeed.hasMore, "已取满总数后不应再认为有下一页")
        assertFalse(secondFeed.canLoadMore)

        holder.loadMoreComments()
        advanceUntilIdle()
        assertEquals(listOf(1, 2), requestedPages, "没有下一页时不应再向网关发请求")

        testScope.cancel()
    }

    @Test
    fun 加载更多评论失败时保留已加载条目并置重试标记() = runTest {
        val testScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val gateway = fakeGateway(
            onComments = { _, page ->
                if (page == 1) {
                    CommentPage(total = 5, items = listOf(comment("c1")))
                } else {
                    throw RuntimeException("网络连接异常")
                }
            }
        )
        val holder = AlbumDetailStateHolder(
            albumId = "123",
            gateway = gateway,
            history = InMemoryHistoryRepository(),
            scope = testScope,
            showMessage = {},
            authCall = AuthCall(NoopGate, UnconfinedTestDispatcher(testScheduler)),
            onOpenLogin = {},
        )

        holder.loadAll(userUid = null)
        advanceUntilIdle()
        holder.loadMoreComments()
        advanceUntilIdle()

        val feed = (holder.uiState.value.commentsState as CommentsUiState.Loaded).feed
        assertEquals(listOf("c1"), feed.items.map { it.id }, "第二页失败不应清掉已加载的条目")
        assertTrue(feed.loadMoreFailed)
        assertFalse(feed.loadingMore)
        assertTrue(feed.canLoadMore, "失败后应允许用户重试")

        testScope.cancel()
    }

    @Test
    fun 详情加载失效时进入登录引导态并通知账号层() = runTest {
        val testScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val gate = CountingGate()
        val holder = AlbumDetailStateHolder(
            albumId = "123",
            gateway = fakeGateway(onAlbum = { throw RuntimeException("401") }),
            authCall = AuthCall(gate, UnconfinedTestDispatcher(testScheduler)),
            history = InMemoryHistoryRepository(),
            scope = testScope,
            showMessage = {},
            onOpenLogin = {},
        )

        holder.loadDetail()
        advanceUntilIdle()

        val failed = holder.uiState.value.albumState as AlbumUiState.Failed
        assertTrue(failed.sessionExpired, "失效必须与普通失败分开，整屏要换成登录引导")
        assertEquals(SessionOutcome.Expired.message, failed.message)
        assertEquals(1, gate.expiredCalls, "失效结局要交给账号层处置一次")

        testScope.cancel()
    }

    @Test
    fun 评论加载失效时评论区进入登录引导态() = runTest {
        val testScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val holder = AlbumDetailStateHolder(
            albumId = "123",
            gateway = fakeGateway(onComments = { _, _ -> throw RuntimeException("401") }),
            authCall = AuthCall(NoopGate, UnconfinedTestDispatcher(testScheduler)),
            history = InMemoryHistoryRepository(),
            scope = testScope,
            showMessage = {},
            onOpenLogin = {},
        )

        holder.loadComments()
        advanceUntilIdle()

        val failed = holder.uiState.value.commentsState as CommentsUiState.Failed
        assertTrue(failed.sessionExpired)
        assertEquals(SessionOutcome.Expired.message, failed.message)

        testScope.cancel()
    }

    @Test
    fun 点赞失效时提示失效文案并打开登录() = runTest {
        val testScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val messages = mutableListOf<String>()
        var loginOpened = 0
        val holder = AlbumDetailStateHolder(
            albumId = "123",
            gateway = fakeGateway(onToggleLike = { throw RuntimeException("401") }),
            authCall = AuthCall(NoopGate, UnconfinedTestDispatcher(testScheduler)),
            history = InMemoryHistoryRepository(),
            scope = testScope,
            showMessage = { messages.add(it) },
            onOpenLogin = { loginOpened++ },
        )

        holder.like(isLoggedIn = true, onOpenLogin = { loginOpened++ })
        advanceUntilIdle()

        assertEquals(SessionOutcome.Expired.message, messages.lastOrNull())
        assertEquals(1, loginOpened, "失效时只给一次登录入口，不能重复弹")
        assertFalse(holder.uiState.value.liked)

        testScope.cancel()
    }

    @Test
    fun 点赞普通失败给出动作前缀的原因() = runTest {
        val testScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val messages = mutableListOf<String>()
        val holder = AlbumDetailStateHolder(
            albumId = "123",
            gateway = fakeGateway(onToggleLike = { throw RuntimeException("boom") }),
            authCall = AuthCall(NoopGate, UnconfinedTestDispatcher(testScheduler)),
            history = InMemoryHistoryRepository(),
            scope = testScope,
            showMessage = { messages.add(it) },
            onOpenLogin = {},
        )

        holder.like(isLoggedIn = true, onOpenLogin = {})
        advanceUntilIdle()

        assertEquals("操作失败：请稍后重试", messages.lastOrNull())

        testScope.cancel()
    }

    @Test
    fun 已加载详情后刷新失败保留内容只提示() = runTest {
        val testScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val messages = mutableListOf<String>()
        var failNext = false
        val holder = AlbumDetailStateHolder(
            albumId = "123",
            gateway = fakeGateway(
                onAlbum = {
                    if (failNext) throw RuntimeException("boom") else sampleAlbum(it)
                },
            ),
            authCall = AuthCall(NoopGate, UnconfinedTestDispatcher(testScheduler)),
            history = InMemoryHistoryRepository(),
            scope = testScope,
            showMessage = { messages.add(it) },
            onOpenLogin = {},
        )

        holder.loadDetail()
        advanceUntilIdle()
        // 评论与追踪走默认桩，不参与本用例
        assertTrue(holder.uiState.value.albumState is AlbumUiState.Loaded)

        failNext = true
        holder.loadDetail()
        advanceUntilIdle()

        assertTrue(
            holder.uiState.value.albumState is AlbumUiState.Loaded,
            "刷新失败不该把已加载的详情顶掉",
        )
        assertEquals("刷新失败：详情加载失败", messages.lastOrNull())

        testScope.cancel()
    }
}
