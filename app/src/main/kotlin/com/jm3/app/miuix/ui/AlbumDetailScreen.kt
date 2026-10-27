package com.jm3.app.miuix.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.jm3.app.miuix.Jm3Graph
import com.jm3.core.mergedWith
import com.jm3.core.model.AlbumDetail
import com.jm3.core.model.Chapter
import com.jm3.core.model.CommentItem
import com.jm3.core.model.CommentPage
import com.jm3.core.model.FavoriteFolderAction
import com.jm3.core.model.SearchResult
import com.jm3.core.model.SearchScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.TooltipBox
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.FavoritesFill
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.FolderFill
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.icon.extended.Weeks
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val CHAPTER_GROUP_SIZE = 10
private const val COMMENT_MAX_LENGTH = 200

sealed interface AlbumUiState {
    data object Loading : AlbumUiState
    data class Loaded(val detail: AlbumDetail) : AlbumUiState
    data class Failed(val message: String) : AlbumUiState
}

private sealed interface CommentsUiState {
    data object Loading : CommentsUiState
    data class Loaded(val page: CommentPage, val lastPage: Int) : CommentsUiState
    data object Failed : CommentsUiState
}

/** 详情页：信息/简介/标签/相关作品/章节分组/评论，对齐原版 Desc + Series 能力域。 */
@Composable
fun AlbumDetailScreen(
    albumId: String,
    onBack: () -> Unit,
    onOpenReader: (String) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenSearch: (query: String, scope: SearchScope) -> Unit,
    onOpenLogin: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val context = LocalContext.current
    val user by Jm3Graph.account.user.collectAsState()

    var state by remember(albumId) { mutableStateOf<AlbumUiState>(AlbumUiState.Loading) }
    var comments by remember(albumId) { mutableStateOf<CommentsUiState>(CommentsUiState.Loading) }
    var favoriteBusy by remember(albumId) { mutableStateOf(false) }
    var favorited by remember(albumId) { mutableStateOf(false) }
    var folders by remember(albumId) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var showFolderSheet by remember(albumId) { mutableStateOf(false) }
    var folderBusy by remember(albumId) { mutableStateOf(false) }
    var likeBusy by remember(albumId) { mutableStateOf(false) }
    var liked by remember(albumId) { mutableStateOf(false) }
    var tracking by remember(albumId) { mutableStateOf<Boolean?>(null) }
    var trackingBusy by remember(albumId) { mutableStateOf(false) }
    var latestPhotoId by remember(albumId) { mutableStateOf<String?>(null) }
    var readPhotoIds by remember(albumId) { mutableStateOf<Set<String>>(emptySet()) }
    var descriptionExpanded by remember(albumId) { mutableStateOf(false) }
    var expandedGroups by remember(albumId) { mutableStateOf(setOf(0)) }
    var commentText by remember(albumId) { mutableStateOf("") }
    var commentBusy by remember(albumId) { mutableStateOf(false) }
    var commentsLoadingMore by remember(albumId) { mutableStateOf(false) }
    var pullRefreshing by remember(albumId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    /** 打开收藏夹选择面板；登录后拉取收藏夹名称表，失败时只保留「全部收藏」。 */
    fun openFolderSheet() {
        showFolderSheet = true
        scope.launch {
            val page = withContext(Dispatchers.IO) {
                runCatching { Jm3Graph.gateway.favorites(1) }.getOrNull()
            }
            if (page != null && page.folders.isNotEmpty()) folders = page.folders
        }
    }

    fun cancelFavorite() {
        if (favoriteBusy) return
        favoriteBusy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { Jm3Graph.gateway.toggleFavorite(albumId) }
            }
            favoriteBusy = false
            result
                .onSuccess {
                    favorited = false
                    showFolderSheet = false
                    snackbarHostState.showSnackbar("已取消收藏")
                }
                .onFailure { snackbarHostState.showSnackbar("收藏失败：" + (it.message ?: "请稍后重试")) }
        }
    }

    /** 收藏按钮：未收藏先选收藏夹（原版 FolderModal 语义），已收藏直接取消。 */
    fun onFavoriteClick() {
        if (user == null) {
            onOpenLogin()
            return
        }
        if (favorited) {
            cancelFavorite()
        } else {
            openFolderSheet()
        }
    }

    /** 选择收藏夹：未收藏则加入该夹，已收藏则移动；成功后关闭面板并回显。 */
    fun pickFolder(folderId: String) {
        if (folderBusy) return
        folderBusy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    if (favorited) {
                        Jm3Graph.gateway.manageFavoriteFolder(
                            action = FavoriteFolderAction.MOVE,
                            folderId = folderId,
                            albumId = albumId,
                        )
                    } else {
                        Jm3Graph.gateway.toggleFavorite(albumId, folderId)
                    }
                }
            }
            folderBusy = false
            result
                .onSuccess {
                    favorited = true
                    showFolderSheet = false
                    val name = folders[folderId] ?: "全部收藏"
                    snackbarHostState.showSnackbar("已收藏到「$name」")
                }
                .onFailure { snackbarHostState.showSnackbar("收藏失败：" + (it.message ?: "请稍后重试")) }
        }
    }

    fun copyText(label: String, value: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText(label, value))
        scope.launch { snackbarHostState.showSnackbar("复制成功！") }
    }

    fun like() {
        if (user == null) {
            onOpenLogin()
            return
        }
        if (likeBusy) return
        if (liked) {
            scope.launch { snackbarHostState.showSnackbar("已经喜欢过了") }
            return
        }
        likeBusy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { Jm3Graph.gateway.toggleLike(albumId) }
            }
            likeBusy = false
            result
                .onSuccess {
                    liked = true
                    snackbarHostState.showSnackbar("已喜欢")
                }
                .onFailure { snackbarHostState.showSnackbar("操作失败：" + (it.message ?: "请稍后重试")) }
        }
    }

    fun toggleTracking() {
        if (user == null) {
            onOpenLogin()
            return
        }
        if (trackingBusy) return
        trackingBusy = true
        scope.launch {
            val next = withContext(Dispatchers.IO) {
                runCatching { Jm3Graph.gateway.setTracking(albumId) }
                    .mapCatching { Jm3Graph.gateway.isTracking(albumId) }
                    .getOrNull()
            }
            trackingBusy = false
            tracking = next
            next?.let { snackbarHostState.showSnackbar(if (it) "已开启连载追踪" else "已取消连载追踪") }
        }
    }

    fun share(detail: AlbumDetail) {
        val host = Jm3Graph.gateway.usedDomain()
        val link = host?.takeIf { it.isNotBlank() }?.let { "https://$it/album/${detail.id}" }
        val text = buildString {
            append(detail.title)
            if (link != null) append('\n').append(link)
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(send, "分享作品"))
    }

    fun loadMoreComments() {
        val loaded = comments as? CommentsUiState.Loaded ?: return
        if (commentsLoadingMore || loaded.page.items.size >= loaded.page.total) return
        commentsLoadingMore = true
        scope.launch {
            val next = withContext(Dispatchers.IO) {
                runCatching { Jm3Graph.gateway.comments(albumId, loaded.lastPage + 1) }.getOrNull()
            }
            commentsLoadingMore = false
            if (next != null) {
                comments = CommentsUiState.Loaded(loaded.page.mergedWith(next), loaded.lastPage + 1)
            }
        }
    }

    suspend fun loadDetail() {
        state = withContext(Dispatchers.IO) {
            runCatching { AlbumUiState.Loaded(Jm3Graph.gateway.album(albumId)) }
                .getOrElse { AlbumUiState.Failed(it.message ?: "加载失败") }
        }
        // 详情返回后才拿到收藏/点赞态，用库返回值初始化，避免把已操作过的相册当未操作
        (state as? AlbumUiState.Loaded)?.let { loaded ->
            favorited = loaded.detail.favorited
            liked = loaded.detail.liked
        }
    }

    suspend fun loadComments() {
        comments = withContext(Dispatchers.IO) {
            runCatching { CommentsUiState.Loaded(Jm3Graph.gateway.comments(albumId, 1), 1) }
                .getOrDefault(CommentsUiState.Failed)
        }
    }

    fun postComment() {
        val text = commentText.trim()
        if (text.isEmpty() || commentBusy) return
        commentBusy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { Jm3Graph.gateway.postComment(albumId, text) }
            }
            commentBusy = false
            result
                .onSuccess {
                    commentText = ""
                    snackbarHostState.showSnackbar("评论已发表")
                    loadComments()
                }
                .onFailure { snackbarHostState.showSnackbar("发表失败：" + (it.message ?: "请稍后重试")) }
        }
    }

    suspend fun loadHistory() {
        latestPhotoId = runCatching { Jm3Graph.history.latestForAlbum(albumId) }.getOrNull()?.photoId
        readPhotoIds = runCatching { Jm3Graph.history.readPhotoIds(albumId) }.getOrDefault(emptySet())
    }

    LaunchedEffect(albumId) {
        loadDetail()
        loadComments()
        loadHistory()
    }

    // 登录态变化后重新读取追踪状态；未登录时清空，避免显示上一个账号的状态。
    LaunchedEffect(user?.uid, albumId) {
        if (user == null) {
            tracking = null
            return@LaunchedEffect
        }
        tracking = withContext(Dispatchers.IO) {
            runCatching { Jm3Graph.gateway.isTracking(albumId) }.getOrNull()
        }
    }

    // 续读目标所在的章节组默认展开，其余收起。
    LaunchedEffect(latestPhotoId, state) {
        val detail = (state as? AlbumUiState.Loaded)?.detail ?: return@LaunchedEffect
        val index = detail.chapters.indexOfFirst { it.id == latestPhotoId }
        if (index >= 0) expandedGroups = expandedGroups + index / CHAPTER_GROUP_SIZE
    }

    fun refresh() {
        if (pullRefreshing) return
        pullRefreshing = true
        scope.launch {
            try {
                loadDetail()
                loadComments()
                loadHistory()
            } finally {
                pullRefreshing = false
            }
        }
    }

    when (val s = state) {
        is AlbumUiState.Loading -> Scaffold(
            topBar = { TopAppBar(title = "详情", navigationIcon = { BackButton(onBack = onBack) }) },
        ) { innerPadding ->
            LoadingScreen(modifier = Modifier.padding(innerPadding))
        }
        is AlbumUiState.Failed -> Scaffold(
            topBar = { TopAppBar(title = "详情", navigationIcon = { BackButton(onBack = onBack) }) },
        ) { innerPadding ->
            ErrorScreen(
                message = "详情加载失败：${s.message}",
                onRetry = { scope.launch { loadDetail() } },
                modifier = Modifier.padding(innerPadding),
            )
        }
        is AlbumUiState.Loaded -> {
            val detail = s.detail
            val resumeId = latestPhotoId?.takeIf { id -> detail.chapters.any { it.id == id } }
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = detail.title,
                        navigationIcon = { BackButton(onBack = onBack) },
                        actions = {
                            // 纯图标动作挂 Tooltip：长按即可确认语义，避免误触分享/批量下载
                            TooltipBox(text = "分享") {
                                IconButton(onClick = { share(detail) }) {
                                    Icon(MiuixIcons.Share, contentDescription = "分享")
                                }
                            }
                            if (detail.chapters.size > 1) {
                                TooltipBox(text = "下载全部") {
                                    IconButton(
                                        onClick = {
                                            detail.chapters.forEach { chapter ->
                                                Jm3Graph.downloadManager.enqueueChapter(
                                                    photoId = chapter.id,
                                                    albumId = detail.id,
                                                    title = chapter.title,
                                                )
                                            }
                                        },
                                    ) {
                                        Icon(imageVector = MiuixIcons.Download, contentDescription = "下载全部")
                                    }
                                }
                            }
                        },
                    )
                },
                snackbarHost = { SnackbarHost(snackbarHostState) },
            ) { innerPadding ->
                PullToRefresh(
                    isRefreshing = pullRefreshing,
                    onRefresh = ::refresh,
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentPadding = innerPadding,
                ) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item(key = "header") {
                            DetailHeader(
                                detail = detail,
                                resumeId = resumeId,
                                loggedIn = user != null,
                                favorited = favorited,
                                favoriteBusy = favoriteBusy,
                                liked = liked,
                                likeBusy = likeBusy,
                                tracking = tracking,
                                trackingBusy = trackingBusy,
                                onRead = { photoId -> onOpenReader(photoId) },
                                onFavorite = ::onFavoriteClick,
                                onLike = ::like,
                                onTrack = ::toggleTracking,
                                onDownloadAll = if (detail.chapters.isEmpty()) {
                                    null
                                } else {
                                    { detail.chapters.forEach { chapter ->
                                        Jm3Graph.downloadManager.enqueueChapter(
                                            photoId = chapter.id,
                                            albumId = detail.id,
                                            title = chapter.title,
                                        )
                                    } }
                                },
                            )
                        }
                        item(key = "info") {
                            AlbumInfoCard(
                                detail = detail,
                                onCopy = { label, value -> copyText(label, value) },
                            )
                        }
                        if (detail.description.isNotBlank()) {
                            item(key = "description") {
                                DescriptionSection(
                                    text = detail.description,
                                    expanded = descriptionExpanded,
                                    onToggle = { descriptionExpanded = !descriptionExpanded },
                                    onCopy = { copyText("简介", detail.description) },
                                )
                            }
                        }
                        if (detail.tags.isNotEmpty()) {
                            item(key = "tags-title") { SmallTitle(text = "标签") }
                            item(key = "tags") {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    items(detail.tags) { tag ->
                                        TextButton(
                                            text = tag,
                                            onClick = { onOpenSearch(tag, SearchScope.TAG) },
                                            minWidth = 0.dp,
                                            minHeight = 32.dp,
                                        )
                                    }
                                }
                            }
                        }
                        if (detail.authors.isNotEmpty() || detail.works.isNotEmpty() || detail.actors.isNotEmpty()) {
                            item(key = "credits") {
                                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                                    if (detail.authors.isNotEmpty()) {
                                        BasicComponent(
                                            title = "作者",
                                            summary = detail.authors.joinToString("、"),
                                            onClick = { onOpenSearch(detail.authors.first(), SearchScope.AUTHOR) },
                                        )
                                    }
                                    if (detail.works.isNotEmpty()) {
                                        BasicComponent(
                                            title = "作品",
                                            summary = detail.works.joinToString("、"),
                                            onClick = { onOpenSearch(detail.works.first(), SearchScope.WORK) },
                                        )
                                    }
                                    if (detail.actors.isNotEmpty()) {
                                        BasicComponent(
                                            title = "登场人物",
                                            summary = detail.actors.joinToString("、"),
                                            onClick = { onOpenSearch(detail.actors.first(), SearchScope.ACTOR) },
                                        )
                                    }
                                }
                            }
                        }
                        if (detail.related.isNotEmpty()) {
                            item(key = "related-title") { SmallTitle(text = "更多相关") }
                            item(key = "related") {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    items(detail.related, key = { it.id }) { related ->
                                        RelatedAlbumCard(related = related, onClick = { onOpenAlbum(related.id) })
                                    }
                                }
                            }
                        }
                        item(key = "chapters-title") {
                            SmallTitle(
                                text = if (detail.chapters.isEmpty()) {
                                    "章节"
                                } else {
                                    "章节（${detail.chapters.size}）"
                                },
                            )
                        }
                        if (detail.chapters.isEmpty()) {
                            item(key = "chapters-empty") { EmptyState(title = "暂无章节") }
                        }
                        detail.chapters.chunked(CHAPTER_GROUP_SIZE).forEachIndexed { groupIndex, group ->
                            item(key = "chapter-group-$groupIndex") {
                                ChapterGroupCard(
                                    group = group,
                                    expanded = groupIndex in expandedGroups,
                                    resumeId = resumeId,
                                    readPhotoIds = readPhotoIds,
                                    onToggle = {
                                        expandedGroups = if (groupIndex in expandedGroups) {
                                            expandedGroups - groupIndex
                                        } else {
                                            expandedGroups + groupIndex
                                        }
                                    },
                                    onRead = { photoId -> onOpenReader(photoId) },
                                    onDownload = { chapter ->
                                        Jm3Graph.downloadManager.enqueueChapter(
                                            photoId = chapter.id,
                                            albumId = detail.id,
                                            title = chapter.title,
                                        )
                                    },
                                )
                            }
                        }
                        item(key = "comments-title") {
                            val total = (comments as? CommentsUiState.Loaded)?.page?.total
                            SmallTitle(text = if (total != null) "评论（$total）" else "评论")
                        }
                        item(key = "comment-composer") {
                            CommentComposer(
                                loggedIn = user != null,
                                text = commentText,
                                busy = commentBusy,
                                onTextChange = { commentText = it.take(COMMENT_MAX_LENGTH) },
                                onLogin = onOpenLogin,
                                onSend = ::postComment,
                            )
                        }
                        when (val cs = comments) {
                            is CommentsUiState.Loading -> item(key = "comments-loading") {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                            is CommentsUiState.Failed -> item(key = "comments-failed") {
                                ErrorScreen(
                                    message = "评论加载失败",
                                    onRetry = { scope.launch { loadComments() } },
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                )
                            }
                            is CommentsUiState.Loaded -> {
                                item(key = "comments-card") {
                                    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                            if (cs.page.items.isEmpty()) {
                                                Text(
                                                    text = "暂无评论，来抢沙发",
                                                    fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                                )
                                            }
                                            for (comment in cs.page.items) {
                                                CommentRow(item = comment)
                                            }
                                            if (cs.page.items.size < cs.page.total) {
                                                Box(
                                                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    if (commentsLoadingMore) {
                                                        CircularProgressIndicator(size = 20.dp)
                                                    } else {
                                                        TextButton(
                                                            text = "加载更多评论",
                                                            onClick = ::loadMoreComments,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        item(key = "bottom-space") { Spacer(modifier = Modifier.height(24.dp)) }
                    }
                }
                // 收藏夹选择面板：未收藏=加入，已收藏=移动，底部提供取消收藏
                OverlayBottomSheet(
                    show = showFolderSheet,
                    title = if (favorited) "移动收藏夹" else "选择收藏夹",
                    onDismissRequest = { showFolderSheet = false },
                ) {
                    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                        val allLabel = folders["0"] ?: "全部收藏"
                        BasicComponent(
                            title = allLabel,
                            summary = "默认收藏夹",
                            onClick = { pickFolder("0") },
                        )
                        folders.entries
                            .filter { it.key != "0" }
                            .forEach { (id, name) ->
                                BasicComponent(
                                    title = name,
                                    onClick = { pickFolder(id) },
                                )
                            }
                    }
                    if (favorited) {
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
                            TextButton(
                                text = "取消收藏",
                                onClick = ::cancelFavorite,
                                enabled = !favoriteBusy,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 头部：封面、标题、统计、阅读按钮与高频动作台。 */
@Composable
private fun DetailHeader(
    detail: AlbumDetail,
    resumeId: String?,
    loggedIn: Boolean,
    favorited: Boolean,
    favoriteBusy: Boolean,
    liked: Boolean,
    likeBusy: Boolean,
    tracking: Boolean?,
    trackingBusy: Boolean,
    onRead: (String) -> Unit,
    onFavorite: () -> Unit,
    onLike: () -> Unit,
    onTrack: () -> Unit,
    onDownloadAll: (() -> Unit)?,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        detail.coverUrl?.let { url ->
            Card(modifier = Modifier.fillMaxWidth()) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f),
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
        Text(
            text = detail.title,
            fontSize = MiuixTheme.textStyles.title4.fontSize,
            fontWeight = FontWeight.Medium,
        )
        val meta = buildList {
            if (detail.authors.isNotEmpty()) add("作者：${detail.authors.joinToString()}")
            if (detail.works.isNotEmpty()) add("社团：${detail.works.joinToString()}")
            if (detail.actors.isNotEmpty()) add("角色：${detail.actors.joinToString()}")
            if (detail.pageCount > 0) add("页数：${detail.pageCount}")
            detail.likes?.let { add("喜欢：$it") }
            detail.views?.let { add("浏览：$it") }
            if (detail.commentCount > 0) add("评论：${detail.commentCount}")
        }
        if (meta.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = meta.joinToString(" · "),
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        val startId = resumeId ?: detail.chapters.firstOrNull()?.id
        Button(
            onClick = { startId?.let(onRead) },
            enabled = startId != null,
            colors = ButtonDefaults.buttonColorsPrimary(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = if (resumeId != null) "继续阅读" else "开始阅读")
        }
        Spacer(modifier = Modifier.height(8.dp))
        // 高频动作台：四等分胶囊卡，图标 + 短标签，状态用填充图标回显
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth()) {
                ActionCell(
                    icon = if (favorited) MiuixIcons.FolderFill else MiuixIcons.Folder,
                    label = if (favorited) "已收藏" else "收藏",
                    enabled = !favoriteBusy,
                    onClick = onFavorite,
                    modifier = Modifier.weight(1f),
                )
                ActionCell(
                    icon = if (liked) MiuixIcons.FavoritesFill else MiuixIcons.Favorites,
                    label = if (liked) "已喜欢" else "喜欢",
                    enabled = !likeBusy,
                    onClick = onLike,
                    modifier = Modifier.weight(1f),
                )
                if (loggedIn) {
                    ActionCell(
                        icon = MiuixIcons.Weeks,
                        label = when {
                            trackingBusy -> "处理中"
                            tracking == true -> "追更中"
                            else -> "追更"
                        },
                        enabled = tracking != null && !trackingBusy,
                        onClick = onTrack,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (onDownloadAll != null) {
                    ActionCell(
                        icon = MiuixIcons.Download,
                        label = "下载",
                        onClick = onDownloadAll,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** 动作台单元：图标 + 标签，禁用态降灰；整格可点，热区覆盖到卡片边缘。 */
@Composable
private fun ActionCell(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val contentColor = if (enabled) {
        MiuixTheme.colorScheme.onSurfaceContainerHigh
    } else {
        MiuixTheme.colorScheme.onSurfaceVariantSummary
    }
    Column(
        modifier = modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = MiuixTheme.textStyles.footnote2.fontSize,
            color = contentColor,
        )
    }
}

/** 简介：默认 4 行折叠，标题栏提供复制入口。 */
@Composable
private fun DescriptionSection(
    text: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    onCopy: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 28.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "简介",
                fontSize = MiuixTheme.textStyles.subtitle.fontSize,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onCopy, minWidth = 32.dp, minHeight = 32.dp) {
                Icon(
                    imageVector = MiuixIcons.Copy,
                    contentDescription = "复制简介",
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    text = text,
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    maxLines = if (expanded) Int.MAX_VALUE else 4,
                    overflow = TextOverflow.Ellipsis,
                )
                if (text.length > 80) {
                    TextButton(
                        text = if (expanded) "收起" else "显示全部",
                        onClick = onToggle,
                        minWidth = 0.dp,
                        minHeight = 32.dp,
                    )
                }
            }
        }
    }
}

/** 禁漫车信息块：车号（可复制）+ 页数，对齐原版 Desc 的 forbcomics/page_count。 */
@Composable
private fun AlbumInfoCard(detail: AlbumDetail, onCopy: (String, String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        BasicComponent(
            title = "禁漫车",
            summary = "#${detail.id}",
            endActions = {
                IconButton(
                    onClick = { onCopy("禁漫车号", detail.id) },
                    minWidth = 36.dp,
                    minHeight = 36.dp,
                ) {
                    Icon(
                        imageVector = MiuixIcons.Copy,
                        contentDescription = "复制车号",
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            },
        )
        if (detail.pageCount > 0) {
            BasicComponent(title = "页数", summary = "${detail.pageCount} 页")
        }
    }
}

/** 章节组：一组 10 话，默认仅展开续读所在组。 */
@Composable
private fun ChapterGroupCard(
    group: List<Chapter>,
    expanded: Boolean,
    resumeId: String?,
    readPhotoIds: Set<String>,
    onToggle: () -> Unit,
    onRead: (String) -> Unit,
    onDownload: (Chapter) -> Unit,
) {
    val first = group.first()
    val last = group.last()
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp)) {
        BasicComponent(
            title = if (group.size > 1) "${first.title} ~ ${last.title}" else first.title,
            summary = "${group.size} 话 · 点击${if (expanded) "收起" else "展开"}",
            onClick = onToggle,
        )
        if (expanded) {
            group.forEach { chapter ->
                BasicComponent(
                    title = chapter.title,
                    summary = when {
                        chapter.id == resumeId -> "上次读到"
                        chapter.id in readPhotoIds -> "已读"
                        else -> null
                    },
                    onClick = { onRead(chapter.id) },
                    endActions = {
                        IconButton(onClick = { onDownload(chapter) }) {
                            Icon(MiuixIcons.Download, contentDescription = "下载")
                        }
                    },
                )
            }
        }
    }
}

/** 更多相关作品卡片：封面 + 两行标题。 */
@Composable
private fun RelatedAlbumCard(related: SearchResult, onClick: () -> Unit) {
    Card(modifier = Modifier.width(112.dp), onClick = onClick) {
        if (related.coverUrl != null) {
            AsyncImage(
                model = related.coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f),
            )
        }
        Text(
            text = related.title,
            fontSize = MiuixTheme.textStyles.footnote2.fontSize,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(8.dp),
        )
    }
}

/** 评论输入：未登录时引导登录；登录后限 200 字并可发表。 */
@Composable
private fun CommentComposer(
    loggedIn: Boolean,
    text: String,
    busy: Boolean,
    onTextChange: (String) -> Unit,
    onLogin: () -> Unit,
    onSend: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        if (!loggedIn) {
            BasicComponent(
                title = "登录后发表评论",
                summary = "登录账号即可参与讨论",
                onClick = onLogin,
            )
        } else {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                TextField(
                    value = text,
                    onValueChange = onTextChange,
                    label = "写下你的评论（最多 $COMMENT_MAX_LENGTH 字）",
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "${text.length}/$COMMENT_MAX_LENGTH",
                        fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Button(
                        onClick = onSend,
                        enabled = !busy && text.isNotBlank(),
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) {
                        Text(text = if (busy) "发表中…" else "发表")
                    }
                }
            }
        }
    }
}

/** 只读评论区：作者/日期/赞/内容，回复缩进展示；整体挂在单张卡片内逐条排列。 */
@Composable
private fun CommentRow(item: CommentItem, depth: Int = 0) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp + (depth * 16).dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = item.author,
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            item.postDate?.let {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = it,
                    fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = item.content, fontSize = MiuixTheme.textStyles.body1.fontSize)
        if (item.likes > 0) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "赞 ${item.likes}",
                fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
    for (reply in item.replies) {
        CommentRow(item = reply, depth = depth + 1)
    }
}
