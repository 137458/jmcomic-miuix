package com.jm3.app.miuix.ui

import androidx.activity.compose.BackHandler
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.jm3.app.miuix.Jm3Graph
import com.jm3.app.miuix.ui.glass.GlassPullToRefresh
import com.jm3.app.miuix.ui.glass.GlassScaffold
import com.jm3.app.miuix.ui.glass.GlassSmallTopAppBar
import com.jm3.app.miuix.ui.glass.GlassTopAppBar
import com.jm3.core.ChapterLabels
import com.jm3.core.mergedWith
import com.jm3.core.model.AlbumDetail
import com.jm3.core.model.Chapter
import com.jm3.core.model.CommentItem
import com.jm3.core.model.CommentPage
import com.jm3.core.model.CommentSegment
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
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TooltipBox
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.FavoritesFill
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.FolderFill
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.icon.extended.Weeks
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val CHAPTER_GROUP_SIZE = 10
private const val COMMENT_MAX_LENGTH = 200
/** 折叠态默认展示的回复条数；超出部分由「展开 N 条回复」控制。 */
private const val COMMENT_VISIBLE_REPLIES = 2
/** 回复缩进的最大层级：更深的回复不再追加缩进，否则正文宽度会被挤没。 */
private const val COMMENT_MAX_INDENT_DEPTH = 4
/** 单条评论正文最多展示的行数；超长评论折叠成省略号，避免整条铺满一屏。 */
private const val COMMENT_BODY_MAX_LINES = 12
/** 徽章图的无障碍描述：徽章本身无文字，读屏需要知道这里是一枚徽章。 */
private const val COMMENT_BADGE_DESCRIPTION = "徽章"

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
    initialTitle: String? = null,
    initialCoverUrl: String? = null,
    onBack: () -> Unit,
    onOpenReader: (String) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenSearch: (query: String, scope: SearchScope) -> Unit,
    onOpenLogin: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    BackHandler(onBack = onBack)
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
    // 回复目标与发送态：选中某条评论后由底部回复面板接管输入
    var replyTarget by remember(albumId) { mutableStateOf<CommentItem?>(null) }
    var replyBusy by remember(albumId) { mutableStateOf(false) }
    var commentsLoadingMore by remember(albumId) { mutableStateOf(false) }
    // 评论加载更多失败单独置位，供底部给出重试入口
    var commentsLoadFailed by remember(albumId) { mutableStateOf(false) }
    var pullRefreshing by remember(albumId) { mutableStateOf(false) }
    val refreshState = rememberPullToRefreshState(refreshThreshold = 0.18f)
    val scope = rememberCoroutineScope()
    val selectionHaptic = rememberSelectionHaptic()

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
                commentsLoadFailed = false
                comments = CommentsUiState.Loaded(loaded.page.mergedWith(next), loaded.lastPage + 1)
            } else {
                commentsLoadFailed = true
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

    fun sendReply(text: String) {
        val target = replyTarget ?: return
        if (text.isBlank() || replyBusy) return
        replyBusy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { Jm3Graph.gateway.replyComment(albumId, text, target.id) }
            }
            replyBusy = false
            result
                .onSuccess {
                    replyTarget = null
                    snackbarHostState.showSnackbar("回复已发送")
                    loadComments()
                }
                .onFailure { snackbarHostState.showSnackbar("回复失败：" + (it.message ?: "请稍后重试")) }
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

    val heroHost = LocalAlbumHeroHost.current
    val effectiveCoverUrl = initialCoverUrl?.takeIf { it.isNotBlank() } ?: heroHost?.coverUrl(albumId)?.takeIf { it.isNotBlank() }
    val effectiveTitle = initialTitle?.takeIf { it.isNotBlank() } ?: heroHost?.albumTitle(albumId)?.takeIf { it.isNotBlank() }

    val loadedDetail = (state as? AlbumUiState.Loaded)?.detail
    val currentTitle = loadedDetail?.title?.takeIf { it.isNotBlank() } ?: effectiveTitle?.takeIf { it.isNotBlank() } ?: "详情"
    val resumeId = latestPhotoId?.takeIf { id -> loadedDetail?.chapters?.any { it.id == id } == true }

    var rootCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    CompositionLocalProvider(
        LocalAlbumDetailRootCoordinates provides rootCoordinates,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { rootCoordinates = it },
        ) {
            GlassScaffold(
        topBar = {
            // 章节页标题常为超长作品名，展开态大标题会撑出 3 行、折叠后留下大片空缺，
            // 这里固定单行小标题（超长省略）；完整标题由页首 DetailHeader 承担。
            GlassSmallTopAppBar(
                title = currentTitle,
                navigationIcon = { BackButton(onBack = onBack) },
                // 透明底色：玻璃底色与模糊由 GlassScaffold 统一绘制
                color = Color.Transparent,
                actions = {
                    if (loadedDetail != null) {
                        // 纯图标动作挂 Tooltip：长按即可确认语义，避免误触分享/批量下载
                        TooltipBox(text = "分享") {
                            IconButton(onClick = { share(loadedDetail) }) {
                                Icon(MiuixIcons.Share, contentDescription = "分享")
                            }
                        }
                        if (loadedDetail.chapters.size > 1) {
                            TooltipBox(text = "下载全部") {
                                IconButton(
                                    onClick = {
                                        loadedDetail.chapters.forEachIndexed { index, chapter ->
                                            Jm3Graph.downloadManager.enqueueChapter(
                                                photoId = chapter.id,
                                                albumId = loadedDetail.id,
                                                title = ChapterLabels.chapterTitle(chapter.title, index),
                                            )
                                        }
                                    },
                                ) {
                                    Icon(imageVector = MiuixIcons.Download, contentDescription = "下载全部")
                                }
                            }
                        }
                    }
                },
            )
        },
        snackbarHost = { GlassSnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        GlassPullToRefresh(
            isRefreshing = pullRefreshing,
            onRefresh = ::refresh,
            pullToRefreshState = refreshState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding,
        ) {
            // 首项下移让出顶栏高度，滚动时内容从玻璃下方穿过
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = innerPadding,
            ) {
                // 首项必须始终渲染，确保列表卡片点击后首帧完成头图测量，转场动画即时起飞
                item(key = "header") {
                    DetailHeader(
                        albumId = albumId,
                        detail = loadedDetail,
                        fallbackTitle = effectiveTitle,
                        fallbackCoverUrl = effectiveCoverUrl,
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
                        onOpenSearch = onOpenSearch,
                        onDownloadAll = if (loadedDetail == null || loadedDetail.chapters.isEmpty()) {
                            null
                        } else {
                            // 与顶栏「下载全部」同一套标题兜底：空标题不能进书架
                            { loadedDetail.chapters.forEachIndexed { index, chapter ->
                                Jm3Graph.downloadManager.enqueueChapter(
                                    photoId = chapter.id,
                                    albumId = loadedDetail.id,
                                    title = ChapterLabels.chapterTitle(chapter.title, index),
                                )
                            } }
                        },
                    )
                }

                when (val s = state) {
                    is AlbumUiState.Loading -> {
                        item(key = "detail-loading") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                InfiniteProgressIndicator()
                            }
                        }
                    }
                    is AlbumUiState.Failed -> {
                        item(key = "detail-failed") {
                            ErrorScreen(
                                message = "详情加载失败：${s.message}",
                                onRetry = { scope.launch { loadDetail() } },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                            )
                        }
                    }
                    is AlbumUiState.Loaded -> {
                        val detail = s.detail
                        // 章节是作品页核心内容，紧随头部；简介、标签、作者等次要信息后置
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
                                    startPosition = groupIndex * CHAPTER_GROUP_SIZE,
                                    expanded = groupIndex in expandedGroups,
                                    resumeId = resumeId,
                                    readPhotoIds = readPhotoIds,
                                    onToggle = {
                                        selectionHaptic()
                                        expandedGroups = if (groupIndex in expandedGroups) {
                                            expandedGroups - groupIndex
                                        } else {
                                            expandedGroups + groupIndex
                                        }
                                    },
                                    onRead = { photoId -> onOpenReader(photoId) },
                                    onDownload = { chapter, title ->
                                        Jm3Graph.downloadManager.enqueueChapter(
                                            photoId = chapter.id,
                                            albumId = detail.id,
                                            title = title,
                                        )
                                    },
                                )
                            }
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
                        val validTags = detail.tags.map { it.trim() }.filter { it.isNotBlank() }
                        if (validTags.isNotEmpty()) {
                            item(key = "tags-title") { SmallTitle(text = "标签") }
                            item(key = "tags") {
                                LazyRow(
                                    modifier = Modifier.padding(bottom = 6.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    items(validTags) { tag ->
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
                        item(key = "info") {
                            AlbumInfoCard(
                                albumId = albumId,
                                detail = detail,
                                onCopy = { label, value -> copyText(label, value) },
                                onOpenSearch = onOpenSearch,
                            )
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
                                    InfiniteProgressIndicator()
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
                                    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                            if (cs.page.items.isEmpty()) {
                                                Text(
                                                    text = "暂无评论，来抢沙发",
                                                    style = MiuixTheme.textStyles.footnote1,
                                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                                )
                                            }
                                            for (comment in cs.page.items) {
                                                CommentRow(
                                                    item = comment,
                                                    onReply = {
                                                        if (user == null) onOpenLogin() else replyTarget = comment
                                                    },
                                                )
                                            }
                                            if (cs.page.items.isNotEmpty()) {
                                                LoadMoreFooter(
                                                    loadingMore = commentsLoadingMore,
                                                    hasMore = cs.page.items.size < cs.page.total,
                                                    loadMoreFailed = commentsLoadFailed,
                                                    onLoadMore = ::loadMoreComments,
                                                    modifier = Modifier.padding(top = 8.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
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
            // 回复面板：标题带被回复作者，发送后刷新评论列表以显示新回复
            CommentReplySheet(
                show = replyTarget != null,
                targetAuthor = replyTarget?.author,
                busy = replyBusy,
                onDismiss = { replyTarget = null },
                onSend = ::sendReply,
            )
        }
    }
}
}
}

/** 头部：封面（大尺寸 144x192 dp，比例 3:4，对齐列表预览与 eh 布局） + 标题/作者/热度 + 阅读按钮与高频动作台。 */
@Composable
private fun DetailHeader(
    albumId: String,
    detail: AlbumDetail?,
    fallbackTitle: String?,
    fallbackCoverUrl: String?,
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
    onOpenSearch: (String, SearchScope) -> Unit,
) {
    val title = detail?.title?.trim()?.takeIf { it.isNotBlank() } ?: fallbackTitle?.trim().orEmpty()
    val coverUrl = detail?.coverUrl?.trim()?.takeIf { it.isNotBlank() } ?: fallbackCoverUrl?.trim()

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            if (!coverUrl.isNullOrBlank()) {
                // 头图是「列表卡片 → 详情页」形变飞行的落点：卡片内封面被裁成 16dp 圆角，这里对齐同一个值
                Card(
                    modifier = Modifier
                        .size(width = 144.dp, height = 192.dp)
                        .albumHeroHeader(albumId, coverUrl, HERO_CARD_CORNER_DP)
                ) {
                    // 与列表卡片、飞行覆盖层共用同一个 Coil 内存缓存条目（显式 key + 固定 144x192dp 解码尺寸），
                    // 命中即首帧出图；crossfade 会在命中后仍淡入一次，正是“封面闪一下”的来源之一。
                    AlbumCoverImage(
                        url = coverUrl,
                        contentDescription = title,
                        modifier = Modifier.fillMaxSize(),
                        crossfade = false,
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(192.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    if (title.isNotBlank()) {
                        Text(
                            text = title,
                            style = MiuixTheme.textStyles.title4,
                            fontWeight = FontWeight.Medium,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    val validAuthors = detail?.authors.orEmpty().map { it.trim() }.filter { it.isNotBlank() }
                    if (validAuthors.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(MiuixTheme.colorScheme.surfaceContainerHigh)
                                .clickable { onOpenSearch(validAuthors.first(), SearchScope.AUTHOR) }
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Contacts,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = validAuthors.joinToString("、"),
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                if (detail != null) {
                    val metrics = buildList {
                        detail.likes?.trim()?.takeIf { it.isNotBlank() && it != "0" }?.let { add("喜欢 $it") }
                        detail.views?.trim()?.takeIf { it.isNotBlank() && it != "0" }?.let { add("浏览 $it") }
                        if (detail.commentCount > 0) add("评论 ${detail.commentCount}")
                        if (detail.pageCount > 0) add("页数 ${detail.pageCount}")
                    }
                    if (metrics.isNotEmpty()) {
                        Text(
                            text = metrics.joinToString(" · "),
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
        }
        if (detail != null) {
            Spacer(modifier = Modifier.height(16.dp))
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
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SmallTitle(text = "简介", modifier = Modifier.weight(1f))
            IconButton(
                onClick = onCopy,
                minWidth = 32.dp,
                minHeight = 32.dp,
                modifier = Modifier.padding(end = 16.dp),
            ) {
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
                    style = MiuixTheme.textStyles.body2,
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

/** 作品信息块：作者/作品/登场人物（可点进搜索）+ 禁漫车号（可复制）+ 页数。 */
@Composable
private fun AlbumInfoCard(
    albumId: String,
    detail: AlbumDetail,
    onCopy: (String, String) -> Unit,
    onOpenSearch: (String, SearchScope) -> Unit,
) {
    val validWorks = detail.works.map { it.trim() }.filter { it.isNotBlank() }
    val validActors = detail.actors.map { it.trim() }.filter { it.isNotBlank() }
    val displayId = detail.id.trim().takeIf { it.isNotBlank() && it != "-1" } ?: albumId
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        if (validWorks.isNotEmpty()) {
            BasicComponent(
                title = "作品",
                summary = validWorks.joinToString("、"),
                onClick = { onOpenSearch(validWorks.first(), SearchScope.WORK) },
            )
        }
        if (validActors.isNotEmpty()) {
            BasicComponent(
                title = "登场人物",
                summary = validActors.joinToString("、"),
                onClick = { onOpenSearch(validActors.first(), SearchScope.ACTOR) },
            )
        }
        BasicComponent(
            title = "禁漫车",
            summary = "#$displayId",
            endActions = {
                IconButton(
                    onClick = { onCopy("禁漫车号", displayId) },
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
    }
}

/** 章节组是否值得给出展开/收起入口：单话分组没有折叠意义，直接展示该话。 */
internal fun shouldShowChapterGroupToggle(size: Int): Boolean = size > 1

/** 章节组：一组 10 话，默认仅展开续读所在组；单话分组不渲染摘要行。 */
@Composable
private fun ChapterGroupCard(
    group: List<Chapter>,
    startPosition: Int,
    expanded: Boolean,
    resumeId: String?,
    readPhotoIds: Set<String>,
    onToggle: () -> Unit,
    onRead: (String) -> Unit,
    onDownload: (Chapter, String) -> Unit,
) {
    // 服务端 series 允许缺 name，直接展示会得到空白行与悬空的 " ~ "，统一按话数回落。
    val titles = group.mapIndexed { index, chapter ->
        ChapterLabels.chapterTitle(chapter.title, startPosition + index)
    }
    val showToggle = shouldShowChapterGroupToggle(group.size)
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        if (showToggle) {
            BasicComponent(
                title = ChapterLabels.groupTitle(group.map { it.title }, startPosition),
                summary = "${group.size} 话 · 点击${if (expanded) "收起" else "展开"}",
                onClick = onToggle,
            )
        }
        if (expanded || !showToggle) {
            group.forEachIndexed { index, chapter ->
                val title = titles[index]
                BasicComponent(
                    title = title,
                    summary = when {
                        chapter.id == resumeId -> "上次读到"
                        chapter.id in readPhotoIds -> "已读"
                        else -> null
                    },
                    onClick = { onRead(chapter.id) },
                    endActions = {
                        IconButton(onClick = { onDownload(chapter, title) }) {
                            Icon(MiuixIcons.Download, contentDescription = "下载")
                        }
                    },
                )
            }
        }
    }
}

/** 更多相关作品卡片：封面 + 两行标题，外观走 [WorkGridCard] 的统一契约。 */
@Composable
private fun RelatedAlbumCard(related: SearchResult, onClick: () -> Unit) {
    WorkGridCard(
        title = related.title,
        coverUrl = related.coverUrl,
        onClick = onClick,
        albumId = related.id,
        coverContentDescription = related.title,
        modifier = Modifier.width(WorkCardTokens.CompactWidth),
    )
}

/** 评论输入：未登录时引导登录；登录后限 200 字并可发表。 */
@Composable
internal fun CommentComposer(
    loggedIn: Boolean,
    text: String,
    busy: Boolean,
    onTextChange: (String) -> Unit,
    onLogin: () -> Unit,
    onSend: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
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
                        style = MiuixTheme.textStyles.footnote1,
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

/** 只读评论区单条：头像 + 等级/称号 + 徽章 + 分段正文，回复默认折叠 2 条；回复区带缩进线并可继续回复。 */
@Composable
internal fun CommentRow(
    item: CommentItem,
    depth: Int = 0,
    onReply: ((CommentItem) -> Unit)? = null,
    onOpenAlbum: ((albumId: String) -> Unit)? = null,
) {
    val lineColor = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.25f)
    val muted = MiuixTheme.colorScheme.onSurfaceVariantSummary
    // 缩进只按层级累加到上限：层级仍然逐层传下去（回复线照画），但不再继续吃掉正文宽度
    val indentDepth = depth.coerceAtMost(COMMENT_MAX_INDENT_DEPTH)
    var repliesExpanded by remember(item.id) { mutableStateOf(item.replies.size <= COMMENT_VISIBLE_REPLIES) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp + (indentDepth * 12).dp, end = 12.dp, top = 8.dp, bottom = 8.dp)
            .drawBehind {
                if (depth > 0) {
                    // 回复线：贴在缩进区外侧的竖线，标识回复归属层级
                    drawRect(
                        color = lineColor,
                        topLeft = Offset(-6.dp.toPx(), 0f),
                        size = Size(2.dp.toPx(), size.height),
                    )
                }
            },
    ) {
        // 头像列：回复越深头像越小，等级数字挂在头像下方与原版一致
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CommentAvatar(url = item.avatarUrl, size = if (depth == 0) 44.dp else 32.dp)
            item.level?.let {
                Text(
                    text = "Lv.$it",
                    style = MiuixTheme.textStyles.footnote2,
                    color = muted,
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.author,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )
                item.levelName?.let {
                    Text(
                        text = " · $it",
                        style = MiuixTheme.textStyles.footnote2,
                        color = muted,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                if (item.likes > 0) {
                    Text(
                        text = "赞 ${item.likes}",
                        style = MiuixTheme.textStyles.footnote2,
                        color = muted,
                    )
                }
            }
            item.postDate?.let {
                Text(
                    text = it,
                    style = MiuixTheme.textStyles.footnote2,
                    color = muted,
                )
            }
            if (item.badges.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (badge in item.badges) {
                        CommentAvatar(
                            url = badge,
                            size = 24.dp,
                            contentDescription = COMMENT_BADGE_DESCRIPTION,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            CommentBody(item = item, onOpenAlbum = onOpenAlbum)
            if (onReply != null) {
                TextButton(
                    text = "回复",
                    onClick = { onReply(item) },
                    minWidth = 0.dp,
                    // 默认 TextButton 是灰底灰字，混在正文里像禁用态；行内动作用透明底 + 主色字
                    colors = ButtonDefaults.textButtonColors(
                        color = Color.Transparent,
                        textColor = MiuixTheme.colorScheme.primary,
                    ),
                    minHeight = 36.dp,
                )
            }
            if (item.replies.isNotEmpty()) {
                val shown = if (repliesExpanded) item.replies else item.replies.take(COMMENT_VISIBLE_REPLIES)
                for (reply in shown) {
                    CommentRow(
                        item = reply,
                        depth = depth + 1,
                        onReply = onReply,
                        onOpenAlbum = onOpenAlbum,
                    )
                }
                if (item.replies.size > COMMENT_VISIBLE_REPLIES) {
                    TextButton(
                        text = if (repliesExpanded) "收起回复" else "展开 ${item.replies.size} 条回复",
                        colors = ButtonDefaults.textButtonColors(
                            color = Color.Transparent,
                            textColor = MiuixTheme.colorScheme.primary,
                        ),
                        onClick = { repliesExpanded = !repliesExpanded },
                        minWidth = 0.dp,
                        minHeight = 36.dp,
                    )
                }
            }
        }
    }
}

/**
 * 头像与徽章统一的圆形图片：底色圆垫在图片下方，缺图、加载中、加载失败都不会露出透明空洞。
 *
 * @param contentDescription 徽章这类无文字的图片需要可读语义；头像留 null，作者名由正文承担。
 */
@Composable
private fun CommentAvatar(url: String?, size: Dp, contentDescription: String? = null) {
    val shape = CircleShape
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(MiuixTheme.colorScheme.surfaceContainer),
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size).clip(shape),
            )
        }
    }
}

/** 正文按分段渲染：推荐作品拆成可点链接，其余按原文换行展示。 */
@Composable
private fun CommentBody(item: CommentItem, onOpenAlbum: ((albumId: String) -> Unit)?) {
    val segments = item.body.ifEmpty { listOf(CommentSegment.Text(item.content)) }
    Column(modifier = Modifier.fillMaxWidth()) {
        for (segment in segments) {
            when (segment) {
                is CommentSegment.Text -> if (segment.text.isNotBlank()) {
                    // 正文限行：超长评论会铺满整屏并顶掉后续楼层，超出部分省略
                    Text(
                        text = segment.text,
                        style = MiuixTheme.textStyles.body1,
                        maxLines = COMMENT_BODY_MAX_LINES,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                is CommentSegment.Album -> Text(
                    text = segment.label,
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.primary,
                    modifier = if (onOpenAlbum != null) {
                        Modifier.pressScale().clickable { onOpenAlbum(segment.albumId) }
                    } else {
                        Modifier
                    },
                )
            }
        }
    }
}