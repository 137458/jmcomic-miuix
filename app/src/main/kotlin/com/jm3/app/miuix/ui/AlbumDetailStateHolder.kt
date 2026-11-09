package com.jm3.app.miuix.ui

import com.jm3.core.history.HistoryRepository
import com.jm3.core.AlbumDetailPort
import com.jm3.core.appendedBy
import com.jm3.core.model.AlbumDetail
import com.jm3.core.model.Chapter
import com.jm3.core.model.CommentItem
import com.jm3.core.paging.PagedFeed
import com.jm3.core.reader.ChapterGroups
import com.jm3.core.replacedBy
import com.jm3.core.runCatchingSuspending
import com.jm3.core.model.FavoriteFolderAction
import com.jm3.core.session.AuthCall
import com.jm3.core.session.SessionOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface AlbumUiState {
    data object Loading : AlbumUiState
    data class Loaded(val detail: AlbumDetail) : AlbumUiState
    /** [sessionExpired] 为真时整屏换成登录引导：会话已作废，重试同一个请求只会再吃一次 401。 */
    data class Failed(val message: String, val sessionExpired: Boolean = false) : AlbumUiState
}

sealed interface CommentsUiState {
    data object Loading : CommentsUiState
    data class Loaded(val feed: PagedFeed<CommentItem>) : CommentsUiState
    data class Failed(val message: String, val sessionExpired: Boolean = false) : CommentsUiState
}

data class AlbumDetailViewState(
    val albumState: AlbumUiState = AlbumUiState.Loading,
    val commentsState: CommentsUiState = CommentsUiState.Loading,
    val favorited: Boolean = false,
    val favoriteBusy: Boolean = false,
    val folders: Map<String, String> = emptyMap(),
    val showFolderSheet: Boolean = false,
    val folderBusy: Boolean = false,
    val liked: Boolean = false,
    val likeBusy: Boolean = false,
    val tracking: Boolean? = null,
    val trackingBusy: Boolean = false,
    val latestPhotoId: String? = null,
    val readPhotoIds: Set<String> = emptySet(),
    val descriptionExpanded: Boolean = false,
    val expandedGroups: Set<String> = emptySet(),
    val commentText: String = "",
    val commentBusy: Boolean = false,
    val replyTarget: CommentItem? = null,
    val replyBusy: Boolean = false,
    val pullRefreshing: Boolean = false,
)

/**
 * 详情页业务状态持有者（StateHolder / Presenter）。
 * 剥离所有网络请求、错误处理、数据合并与交互状态机，解耦 Composable 视图层并提供完整的纯 JVM 单测能力。
 *
 * 所有网关调用都经 [authCall]：启动闸门、IO 调度与失效处置由它统一给，这里只负责三支结局的落点。
 */
internal class AlbumDetailStateHolder(
    val albumId: String,
    private val gateway: AlbumDetailPort,
    private val authCall: AuthCall,
    private val history: HistoryRepository,
    private val scope: CoroutineScope,
    private val showMessage: (String) -> Unit,
    private val onOpenLogin: () -> Unit,
    // 与阅读页共享的喜欢状态。不给默认值：默认 new 一个独立实例会让详情页的喜欢与阅读页脱钩，
    // 生产必须传 SessionCache.engagement，单测显式传自己的实例。
    private val engagement: AlbumEngagementStore,
) {
    private val _uiState = MutableStateFlow(AlbumDetailViewState())
    val uiState: StateFlow<AlbumDetailViewState> = _uiState.asStateFlow()

    /** 动作型调用的统一落点：失效提示并给登录入口，失败给「动作：原因」。 */
    private suspend fun report(outcome: SessionOutcome<*>, failurePrefix: String) {
        outcome.notifyAction({ showMessage(it) }, failurePrefix, onExpired = onOpenLogin)
    }

    fun loadAll(userUid: String?) {
        scope.launch {
            loadDetail()
            loadComments()
            loadHistory()
            loadTracking(userUid)
        }
    }

    suspend fun loadDetail() {
        val outcome = authCall.call { gateway.album(albumId) }
        val plan = planLoad(
            outcome = outcome,
            hasContent = _uiState.value.albumState is AlbumUiState.Loaded,
            failureMessage = "详情加载失败",
        )
        when (outcome) {
            is SessionOutcome.Ok -> _uiState.update { current ->
                // 接口目录顺序不保证按排序号，先归一，分组、首章与下载都按它走
                val detail = outcome.value.copy(chapters = outcome.value.chapters.sortedBy { it.sortOrder })
                // 组身份随章节走：刷新后仍存在的组保持展开，全都不存在时回落到首组
                val groups = ChapterGroups.of(detail.chapters, Chapter::id)
                val knownIds = groups.mapTo(HashSet()) { it.id }
                val retained = current.expandedGroups.filterTo(LinkedHashSet()) { it in knownIds }
                val nextGroups = if (retained.isEmpty()) {
                    groups.firstOrNull()?.let { setOf(it.id) } ?: emptySet()
                } else {
                    retained
                }
                current.copy(
                    albumState = AlbumUiState.Loaded(detail),
                    favorited = detail.favorited,
                    liked = detail.liked,
                    expandedGroups = nextGroups,
                )
            }

            else -> {
                // 已有内容时只提示不顶屏；没有内容时失效与失败分开，失效不给重试
                if (plan.keepContent) {
                    plan.notice?.let(showMessage)
                } else {
                    val failed = AlbumUiState.Failed(
                        message = plan.message.orEmpty(),
                        sessionExpired = plan.phase == ScreenLoadPhase.Expired,
                    )
                    _uiState.update { it.copy(albumState = failed) }
                }
            }
        }
    }

    suspend fun loadComments() {
        val outcome = authCall.call { gateway.comments(albumId, 1) }
        val plan = planLoad(
            outcome = outcome,
            hasContent = _uiState.value.commentsState is CommentsUiState.Loaded,
            failureMessage = "评论加载失败",
        )
        when (outcome) {
            is SessionOutcome.Ok -> _uiState.update {
                it.copy(commentsState = CommentsUiState.Loaded(PagedFeed<CommentItem>().replacedBy(outcome.value)))
            }

            else -> if (plan.keepContent) {
                plan.notice?.let(showMessage)
            } else {
                val failed = CommentsUiState.Failed(
                    message = plan.message.orEmpty(),
                    sessionExpired = plan.phase == ScreenLoadPhase.Expired,
                )
                _uiState.update { it.copy(commentsState = failed) }
            }
        }
    }

    suspend fun loadHistory() {
        val latest = runCatchingSuspending { history.latestForAlbum(albumId) }.getOrNull()?.photoId
        val readIds = runCatchingSuspending { history.readPhotoIds(albumId) }.getOrDefault(emptySet())
        _uiState.update { current ->
            val detail = (current.albumState as? AlbumUiState.Loaded)?.detail
            val resumeGroup = if (detail != null && latest != null) {
                ChapterGroups.groupIdOf(detail.chapters, Chapter::id, latest)
            } else {
                null
            }
            current.copy(
                latestPhotoId = latest,
                readPhotoIds = readIds,
                expandedGroups = if (resumeGroup == null) {
                    current.expandedGroups
                } else {
                    current.expandedGroups + resumeGroup
                },
            )
        }
    }

    suspend fun loadTracking(userUid: String?) {
        if (userUid == null) {
            _uiState.update { it.copy(tracking = null) }
            return
        }
        // 追踪态只是头部的一个开关：拿不到就留空，不顶掉整屏也不打扰用户
        val outcome = authCall.call { gateway.isTracking(albumId) }
        val isTrack = (outcome as? SessionOutcome.Ok)?.value
        _uiState.update { it.copy(tracking = isTrack) }
    }

    fun refresh() {
        if (_uiState.value.pullRefreshing) return
        _uiState.update { it.copy(pullRefreshing = true) }
        scope.launch {
            try {
                loadDetail()
                loadComments()
                loadHistory()
            } finally {
                _uiState.update { it.copy(pullRefreshing = false) }
            }
        }
    }

    fun onFavoriteClick(isLoggedIn: Boolean, onOpenLogin: () -> Unit) {
        if (!isLoggedIn) {
            onOpenLogin()
            return
        }
        if (_uiState.value.favorited) {
            cancelFavorite()
        } else {
            openFolderSheet()
        }
    }

    fun openFolderSheet() {
        _uiState.update { it.copy(showFolderSheet = true) }
        scope.launch {
            val outcome = authCall.call { gateway.favorites(1) }
            val page = (outcome as? SessionOutcome.Ok)?.value
            if (page == null) {
                dismissFolderSheet()
                report(outcome, "收藏夹加载失败")
            } else if (page.folders.isNotEmpty()) {
                _uiState.update { it.copy(folders = page.folders) }
            }
        }
    }

    fun dismissFolderSheet() {
        _uiState.update { it.copy(showFolderSheet = false) }
    }

    fun closeFolderSheet() {
        dismissFolderSheet()
    }

    fun setDescriptionExpanded(expanded: Boolean) {
        _uiState.update { it.copy(descriptionExpanded = expanded) }
    }

    fun reloadComments() {
        scope.launch { loadComments() }
    }

    fun cancelFavorite() {
        if (_uiState.value.favoriteBusy) return
        _uiState.update { it.copy(favoriteBusy = true) }
        scope.launch {
            val outcome = authCall.call { gateway.toggleFavorite(albumId) }
            _uiState.update { it.copy(favoriteBusy = false) }
            if (outcome is SessionOutcome.Ok) {
                _uiState.update { it.copy(favorited = false, showFolderSheet = false) }
                showMessage("已取消收藏")
            } else {
                report(outcome, "收藏失败")
            }
        }
    }

    fun pickFolder(folderId: String) {
        if (_uiState.value.folderBusy) return
        val currentlyFavorited = _uiState.value.favorited
        _uiState.update { it.copy(folderBusy = true) }
        scope.launch {
            val outcome = authCall.call {
                if (currentlyFavorited) {
                    gateway.manageFavoriteFolder(
                        action = FavoriteFolderAction.MOVE,
                        folderId = folderId,
                        albumId = albumId,
                    )
                } else {
                    gateway.toggleFavorite(albumId, folderId)
                }
            }
            _uiState.update { it.copy(folderBusy = false) }
            if (outcome is SessionOutcome.Ok) {
                val folderName = _uiState.value.folders[folderId] ?: "全部收藏"
                _uiState.update { it.copy(favorited = true, showFolderSheet = false) }
                showMessage("已收藏到「$folderName」")
            } else {
                report(outcome, "收藏失败")
            }
        }
    }

    fun like(isLoggedIn: Boolean, onOpenLogin: () -> Unit) {
        if (!isLoggedIn) {
            onOpenLogin()
            return
        }
        val current = _uiState.value
        if (current.likeBusy) return
        // 阅读页取消喜欢后服务端已是未喜欢：以共享状态为准，避免这里拿旧 liked 提前返回。
        if (engagement.likedOf(albumId) ?: current.liked) {
            showMessage("已经喜欢过了")
            return
        }
        _uiState.update { it.copy(likeBusy = true) }
        scope.launch {
            val outcome = authCall.call { gateway.toggleLike(albumId) }
            _uiState.update { it.copy(likeBusy = false) }
            if (outcome is SessionOutcome.Ok) {
                engagement.setLiked(albumId, true)
                _uiState.update { it.copy(liked = true) }
                showMessage("已喜欢")
            } else {
                report(outcome, "操作失败")
            }
        }
    }

    fun toggleTracking(isLoggedIn: Boolean, onOpenLogin: () -> Unit) {
        if (!isLoggedIn) {
            onOpenLogin()
            return
        }
        if (_uiState.value.trackingBusy) return
        _uiState.update { it.copy(trackingBusy = true) }
        scope.launch {
            // 开关与回读放同一次调用里：同一道启动闸门与 IO 调度，不会一半成功一半失败
            val outcome = authCall.call {
                gateway.setTracking(albumId)
                gateway.isTracking(albumId)
            }
            _uiState.update { it.copy(trackingBusy = false) }
            if (outcome is SessionOutcome.Ok) {
                val next = outcome.value
                _uiState.update { it.copy(tracking = next) }
                showMessage(if (next) "已开启连载追踪" else "已取消连载追踪")
            } else {
                report(outcome, "追踪失败")
            }
        }
    }

    fun setCommentText(text: String) {
        _uiState.update { it.copy(commentText = text) }
    }

    fun setReplyTarget(target: CommentItem?) {
        _uiState.update { it.copy(replyTarget = target) }
    }

    fun postComment() {
        val text = _uiState.value.commentText.trim()
        if (text.isEmpty() || _uiState.value.commentBusy) return
        _uiState.update { it.copy(commentBusy = true) }
        scope.launch {
            val outcome = authCall.call { gateway.postComment(albumId, text) }
            _uiState.update { it.copy(commentBusy = false) }
            if (outcome is SessionOutcome.Ok) {
                _uiState.update { it.copy(commentText = "") }
                showMessage("评论已发表")
                loadComments()
            } else {
                report(outcome, "发表失败")
            }
        }
    }

    fun sendReply(text: String) {
        val target = _uiState.value.replyTarget ?: return
        if (text.isBlank() || _uiState.value.replyBusy) return
        _uiState.update { it.copy(replyBusy = true) }
        scope.launch {
            val outcome = authCall.call { gateway.replyComment(albumId, text, target.id) }
            _uiState.update { it.copy(replyBusy = false) }
            if (outcome is SessionOutcome.Ok) {
                _uiState.update { it.copy(replyTarget = null) }
                showMessage("回复已发送")
                loadComments()
            } else {
                report(outcome, "回复失败")
            }
        }
    }

    fun loadMoreComments() {
        val loaded = _uiState.value.commentsState as? CommentsUiState.Loaded ?: return
        val requested = loaded.feed.beginLoadMore()
        // 已经在拉或没有下一页时原样返回，不重复发请求
        if (requested === loaded.feed) return
        _uiState.update { it.copy(commentsState = CommentsUiState.Loaded(requested)) }
        scope.launch {
            val outcome = authCall.call { gateway.comments(albumId, requested.page + 1) }
            _uiState.update { current ->
                val currentLoaded = current.commentsState as? CommentsUiState.Loaded ?: return@update current
                val nextFeed = if (outcome is SessionOutcome.Ok) {
                    currentLoaded.feed.appendedBy(outcome.value)
                } else {
                    currentLoaded.feed.failLoadMore()
                }
                current.copy(commentsState = CommentsUiState.Loaded(nextFeed))
            }
            if (outcome !is SessionOutcome.Ok) report(outcome, "加载更多评论失败")
        }
    }

    fun toggleGroup(groupId: String) {
        _uiState.update { current ->
            val next = if (groupId in current.expandedGroups) {
                current.expandedGroups - groupId
            } else {
                current.expandedGroups + groupId
            }
            current.copy(expandedGroups = next)
        }
    }
}