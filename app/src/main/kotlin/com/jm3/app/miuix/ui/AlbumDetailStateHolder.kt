package com.jm3.app.miuix.ui

import com.jm3.core.history.HistoryRepository
import com.jm3.core.AlbumDetailPort
import com.jm3.core.appendedBy
import com.jm3.core.model.AlbumDetail
import com.jm3.core.model.CommentItem
import com.jm3.core.paging.PagedFeed
import com.jm3.core.replacedBy
import com.jm3.core.model.FavoriteFolderAction
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val CHAPTER_GROUP_SIZE = 10

sealed interface AlbumUiState {
    data object Loading : AlbumUiState
    data class Loaded(val detail: AlbumDetail) : AlbumUiState
    data class Failed(val message: String) : AlbumUiState
}

sealed interface CommentsUiState {
    data object Loading : CommentsUiState
    data class Loaded(val feed: PagedFeed<CommentItem>) : CommentsUiState
    data object Failed : CommentsUiState
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
    val expandedGroups: Set<Int> = setOf(0),
    val commentText: String = "",
    val commentBusy: Boolean = false,
    val replyTarget: CommentItem? = null,
    val replyBusy: Boolean = false,
    val pullRefreshing: Boolean = false,
)

/**
 * 详情页业务状态持有者（StateHolder / Presenter）。
 * 剥离所有网络请求、错误处理、数据合并与交互状态机，解耦 Composable 视图层并提供完整的纯 JVM 单测能力。
 */
class AlbumDetailStateHolder(
    val albumId: String,
    private val gateway: AlbumDetailPort,
    private val history: HistoryRepository,
    private val scope: CoroutineScope,
    private val showMessage: (String) -> Unit,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val _uiState = MutableStateFlow(AlbumDetailViewState())
    val uiState: StateFlow<AlbumDetailViewState> = _uiState.asStateFlow()

    fun loadAll(userUid: String?) {
        scope.launch {
            loadDetail()
            loadComments()
            loadHistory()
            loadTracking(userUid)
        }
    }

    suspend fun loadDetail() {
        val result = withContext(ioDispatcher) {
            runCatching { AlbumUiState.Loaded(gateway.album(albumId)) }
                .getOrElse { AlbumUiState.Failed(it.message ?: "加载失败") }
        }
        _uiState.update { current ->
            if (result is AlbumUiState.Loaded) {
                // 计算续读章节默认展开组
                val nextGroups = if (current.latestPhotoId != null) {
                    val index = result.detail.chapters.indexOfFirst { it.id == current.latestPhotoId }
                    if (index >= 0) current.expandedGroups + (index / CHAPTER_GROUP_SIZE) else current.expandedGroups
                } else {
                    current.expandedGroups
                }
                current.copy(
                    albumState = result,
                    favorited = result.detail.favorited,
                    liked = result.detail.liked,
                    expandedGroups = nextGroups,
                )
            } else {
                current.copy(albumState = result)
            }
        }
    }

    suspend fun loadComments() {
        val commentsResult = withContext(ioDispatcher) {
            runCatching { CommentsUiState.Loaded(PagedFeed<CommentItem>().replacedBy(gateway.comments(albumId, 1))) }
                .getOrDefault(CommentsUiState.Failed)
        }
        _uiState.update { it.copy(commentsState = commentsResult) }
    }

    suspend fun loadHistory() {
        val latest = runCatching { history.latestForAlbum(albumId) }.getOrNull()?.photoId
        val readIds = runCatching { history.readPhotoIds(albumId) }.getOrDefault(emptySet())
        _uiState.update { current ->
            val nextGroups = if (latest != null && current.albumState is AlbumUiState.Loaded) {
                val index = current.albumState.detail.chapters.indexOfFirst { it.id == latest }
                if (index >= 0) current.expandedGroups + (index / CHAPTER_GROUP_SIZE) else current.expandedGroups
            } else {
                current.expandedGroups
            }
            current.copy(
                latestPhotoId = latest,
                readPhotoIds = readIds,
                expandedGroups = nextGroups,
            )
        }
    }

    suspend fun loadTracking(userUid: String?) {
        if (userUid == null) {
            _uiState.update { it.copy(tracking = null) }
            return
        }
        val isTrack = withContext(ioDispatcher) {
            runCatching { gateway.isTracking(albumId) }.getOrNull()
        }
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
            val page = withContext(ioDispatcher) {
                runCatching { gateway.favorites(1) }.getOrNull()
            }
            if (page != null && page.folders.isNotEmpty()) {
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
            val result = withContext(ioDispatcher) {
                runCatching { gateway.toggleFavorite(albumId) }
            }
            _uiState.update { it.copy(favoriteBusy = false) }
            result
                .onSuccess {
                    _uiState.update { it.copy(favorited = false, showFolderSheet = false) }
                    showMessage("已取消收藏")
                }
                .onFailure {
                    showMessage("收藏失败：" + (it.message ?: "请稍后重试"))
                }
        }
    }

    fun pickFolder(folderId: String) {
        if (_uiState.value.folderBusy) return
        val currentlyFavorited = _uiState.value.favorited
        _uiState.update { it.copy(folderBusy = true) }
        scope.launch {
            val result = withContext(ioDispatcher) {
                runCatching {
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
            }
            _uiState.update { it.copy(folderBusy = false) }
            result
                .onSuccess {
                    val folderName = _uiState.value.folders[folderId] ?: "全部收藏"
                    _uiState.update { it.copy(favorited = true, showFolderSheet = false) }
                    showMessage("已收藏到「$folderName」")
                }
                .onFailure {
                    showMessage("收藏失败：" + (it.message ?: "请稍后重试"))
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
        if (current.liked) {
            showMessage("已经喜欢过了")
            return
        }
        _uiState.update { it.copy(likeBusy = true) }
        scope.launch {
            val result = withContext(ioDispatcher) {
                runCatching { gateway.toggleLike(albumId) }
            }
            _uiState.update { it.copy(likeBusy = false) }
            result
                .onSuccess {
                    _uiState.update { it.copy(liked = true) }
                    showMessage("已喜欢")
                }
                .onFailure {
                    showMessage("操作失败：" + (it.message ?: "请稍后重试"))
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
            val next = withContext(ioDispatcher) {
                runCatching { gateway.setTracking(albumId) }
                    .mapCatching { gateway.isTracking(albumId) }
                    .getOrNull()
            }
            _uiState.update { it.copy(trackingBusy = false, tracking = next) }
            next?.let { showMessage(if (it) "已开启连载追踪" else "已取消连载追踪") }
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
            val result = withContext(ioDispatcher) {
                runCatching { gateway.postComment(albumId, text) }
            }
            _uiState.update { it.copy(commentBusy = false) }
            result
                .onSuccess {
                    _uiState.update { it.copy(commentText = "") }
                    showMessage("评论已发表")
                    loadComments()
                }
                .onFailure {
                    showMessage("发表失败：" + (it.message ?: "请稍后重试"))
                }
        }
    }

    fun sendReply(text: String) {
        val target = _uiState.value.replyTarget ?: return
        if (text.isBlank() || _uiState.value.replyBusy) return
        _uiState.update { it.copy(replyBusy = true) }
        scope.launch {
            val result = withContext(ioDispatcher) {
                runCatching { gateway.replyComment(albumId, text, target.id) }
            }
            _uiState.update { it.copy(replyBusy = false) }
            result
                .onSuccess {
                    _uiState.update { it.copy(replyTarget = null) }
                    showMessage("回复已发送")
                    loadComments()
                }
                .onFailure {
                    showMessage("回复失败：" + (it.message ?: "请稍后重试"))
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
            val next = withContext(ioDispatcher) {
                runCatching { gateway.comments(albumId, requested.page + 1) }.getOrNull()
            }
            _uiState.update { current ->
                val currentLoaded = current.commentsState as? CommentsUiState.Loaded ?: return@update current
                val nextFeed = if (next != null) {
                    currentLoaded.feed.appendedBy(next)
                } else {
                    currentLoaded.feed.failLoadMore()
                }
                current.copy(commentsState = CommentsUiState.Loaded(nextFeed))
            }
        }
    }

    fun toggleGroup(groupIndex: Int) {
        _uiState.update { current ->
            val next = if (groupIndex in current.expandedGroups) {
                current.expandedGroups - groupIndex
            } else {
                current.expandedGroups + groupIndex
            }
            current.copy(expandedGroups = next)
        }
    }

    fun toggleDescription() {
        _uiState.update { it.copy(descriptionExpanded = !it.descriptionExpanded) }
    }
}