// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.page

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.jm3.app.miuix.data.download.DownloadManager
import com.jm3.app.miuix.ui.AlbumCoverImage
import com.jm3.app.miuix.ui.AlbumDetailStateHolder
import com.jm3.app.miuix.ui.AlbumUiState
import com.jm3.app.miuix.ui.COMMENT_REPLY_MAX_LENGTH
import com.jm3.app.miuix.ui.CommentsUiState
import com.jm3.app.miuix.ui.GlassSnackbarHost
import com.jm3.app.miuix.ui.LoadMoreFooter
import com.jm3.app.miuix.ui.LoadingScreen
import com.jm3.app.miuix.ui.LocalAlbumDetailRootCoordinates
import com.jm3.app.miuix.ui.LocalAlbumHeroHost
import com.jm3.app.miuix.ui.LocalWideLayout
import com.jm3.app.miuix.ui.SessionCache
import com.jm3.app.miuix.ui.TrackedLazyColumn
import com.jm3.app.miuix.ui.albumHeroHeader
import com.jm3.app.miuix.ui.coui.component.CouiBottomSheet
import com.jm3.app.miuix.ui.coui.component.CouiButton
import com.jm3.app.miuix.ui.coui.component.CouiCard
import com.jm3.app.miuix.ui.coui.component.CouiChip
import com.jm3.app.miuix.ui.coui.component.CouiCompactWorkCard
import com.jm3.app.miuix.ui.coui.component.CouiNestedCard
import com.jm3.app.miuix.ui.coui.component.CouiNoticeCard
import com.jm3.app.miuix.ui.coui.component.CouiSectionTitle
import com.jm3.app.miuix.ui.coui.component.CouiSettingsRow
import com.jm3.app.miuix.ui.coui.component.CouiSurfaceBackground
import com.jm3.app.miuix.ui.coui.component.CouiTagFlow
import com.jm3.app.miuix.ui.coui.component.CouiTextButton
import com.jm3.app.miuix.ui.coui.component.CouiTextField
import com.jm3.app.miuix.ui.coui.component.CouiTopBar
import com.jm3.app.miuix.ui.coui.component.couiBackdropSource
import com.jm3.app.miuix.ui.coui.component.couiClickable
import com.jm3.app.miuix.ui.coui.component.couiTopBarHeight
import com.jm3.app.miuix.ui.coui.component.rememberCouiBlurBackdrop
import com.jm3.app.miuix.ui.coui.theme.CouiTheme
import com.jm3.app.miuix.ui.coui.theme.CouiType
import com.jm3.app.miuix.ui.coui.theme.LocalCouiColors
import com.jm3.app.miuix.ui.coui.theme.LocalCouiShapes
import com.jm3.app.miuix.ui.readableWidth
import com.jm3.app.miuix.ui.rememberReaderImageLoader
import com.jm3.core.AlbumPreviewPlan
import com.jm3.core.ChapterLabels
import com.jm3.core.Jm3Gateway
import com.jm3.core.albumPreviewPlan
import com.jm3.core.asAlbumDetailPort
import com.jm3.core.download.DownloadStore
import com.jm3.core.history.HistoryRepository
import com.jm3.core.model.AlbumDetail
import com.jm3.core.model.Chapter
import com.jm3.core.model.CommentItem
import com.jm3.core.model.CommentSegment
import com.jm3.core.model.PageImage
import com.jm3.core.model.SearchResult
import com.jm3.core.model.SearchScope
import com.jm3.core.paging.PagedFeed
import com.jm3.core.reader.ChapterGroups
import com.jm3.core.session.AccountManager
import com.jm3.core.session.AuthCall
import com.jm3.core.session.SessionOutcome
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.nav.core.LocalNavTransitionScope
import top.yukonga.miuix.kmp.nav.transition.NavRole
import top.yukonga.miuix.kmp.squircle.squircleClip

private val CouiPagePadding = 16.dp

/** 折叠态默认展示的回复条数；超出部分由「展开 N 条回复」控制。 */
private const val COUI_COMMENT_VISIBLE_REPLIES = 2
/** 回复缩进的最大层级：更深的回复不再追加缩进，否则正文宽度会被挤没。 */
private const val COUI_COMMENT_MAX_INDENT_DEPTH = 4
/** 单条评论正文最多展示的行数；超长评论折叠成省略号，避免整条铺满一屏。 */
private const val COUI_COMMENT_BODY_MAX_LINES = 12
/** 徽章图的无障碍描述：徽章本身无文字，读屏需要知道这里是一枚徽章。 */
private const val COUI_COMMENT_BADGE_DESCRIPTION = "徽章"

/**
 * COUI 作品详情页：封面/信息头部、预览、章节分组、简介、作品信息、相关作品与评论区。
 *
 * 业务编排全部复用 [AlbumDetailStateHolder]（加载、收藏/取消收藏、喜欢、追踪、评论分页与发送、
 * 章节分组展开态、续读记录），本文件只负责 COUI 视觉：渐变背景 + 透明顶栏（滚动后磨砂）、
 * 20dp squircle 卡片、嵌套章节卡与页内底部面板。
 */
@Composable
internal fun CouiAlbumDetailScreen(
    albumId: String,
    initialTitle: String? = null,
    initialCoverUrl: String? = null,
    onBack: () -> Unit,
    onOpenReader: (photoId: String, startPage: Int?) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenSearch: (query: String, scope: SearchScope) -> Unit,
    onOpenLogin: () -> Unit,
    snackbarHostState: SnackbarHostState,
    gateway: Jm3Gateway,
    authCall: AuthCall,
    history: HistoryRepository,
    downloadManager: DownloadManager,
    downloadFiles: DownloadStore,
    account: AccountManager,
    sessionCache: SessionCache,
) {
    CouiTheme {
        CouiThemeBody(
            albumId = albumId,
            initialTitle = initialTitle,
            initialCoverUrl = initialCoverUrl,
            onBack = onBack,
            onOpenReader = onOpenReader,
            onOpenAlbum = onOpenAlbum,
            onOpenSearch = onOpenSearch,
            onOpenLogin = onOpenLogin,
            snackbarHostState = snackbarHostState,
            gateway = gateway,
            authCall = authCall,
            history = history,
            downloadManager = downloadManager,
            downloadFiles = downloadFiles,
            account = account,
            sessionCache = sessionCache,
        )
    }
}

@Composable
private fun CouiThemeBody(
    albumId: String,
    initialTitle: String?,
    initialCoverUrl: String?,
    onBack: () -> Unit,
    onOpenReader: (photoId: String, startPage: Int?) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenSearch: (query: String, scope: SearchScope) -> Unit,
    onOpenLogin: () -> Unit,
    snackbarHostState: SnackbarHostState,
    gateway: Jm3Gateway,
    authCall: AuthCall,
    history: HistoryRepository,
    downloadManager: DownloadManager,
    downloadFiles: DownloadStore,
    account: AccountManager,
    sessionCache: SessionCache,
) {
    val context = LocalContext.current
    val user by account.user.collectAsState()
    val scope = rememberCoroutineScope()
    val colors = LocalCouiColors.current
    val backdrop = rememberCouiBlurBackdrop()
    // 预览图与阅读页共用同一套图片加载器（含同一份缓存），避免再建第二个 loader
    val previewImageLoader = rememberReaderImageLoader(gateway, downloadFiles)

    val stateHolder = remember(albumId) {
        AlbumDetailStateHolder(
            albumId = albumId,
            gateway = gateway.asAlbumDetailPort(),
            authCall = authCall,
            history = history,
            scope = scope,
            showMessage = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } },
            onOpenLogin = onOpenLogin,
            engagement = sessionCache.engagement,
        )
    }
    val uiState by stateHolder.uiState.collectAsState()

    val listState = rememberLazyListState()
    // 大屏分栏时右栏独立滚动，不跟随左栏的封面与信息
    val rightListState = rememberLazyListState()

    /** 批量下载：只有全部章节都已在队列/已下载时才提示“无新增”，否则报告本次实际入队数量。 */
    fun downloadAll(albumId: String, chapters: List<Chapter>) {
        if (chapters.isEmpty()) return
        var accepted = 0
        chapters.forEachIndexed { index, chapter ->
            val title = ChapterLabels.chapterTitle(chapter.title, index)
            if (downloadManager.enqueueChapter(chapter.id, albumId, title)) accepted++
        }
        scope.launch {
            snackbarHostState.showSnackbar(
                if (accepted > 0) "已加入下载队列（$accepted 个章节）" else "这些章节已在下载队列或已下载",
            )
        }
    }

    fun downloadChapter(chapter: Chapter, albumId: String, title: String) {
        val accepted = downloadManager.enqueueChapter(chapter.id, albumId, title)
        scope.launch {
            snackbarHostState.showSnackbar(
                if (accepted) "已加入下载队列" else "该章节已在下载队列或已下载",
            )
        }
    }

    fun copyText(label: String, value: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText(label, value))
        scope.launch { snackbarHostState.showSnackbar("复制成功！") }
    }

    fun share(detail: AlbumDetail) {
        val host = gateway.usedDomain()
        val link = host?.takeIf { it.isNotBlank() }?.let { "https://" + it + "/album/" + detail.id }
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

    LaunchedEffect(albumId, user?.uid) {
        stateHolder.loadAll(user?.uid)
    }

    val heroHost = LocalAlbumHeroHost.current
    // 预测性返回：用导航层暴露的 relativeDepth 直接驱动封面逆向飞行。
    // 它与页面 alpha 同源（退出时 0 → -1），手势移动多少、封面回飞多少；取消回弹，提交时随页面销毁收场。
    val navScope by rememberUpdatedState(LocalNavTransitionScope.current)
    if (heroHost != null) {
        LaunchedEffect(heroHost, albumId) {
            // 起飞条件只能是“明确的退出信号”：返回手势进行中（gesture 非空），或本页已被标记为正在出栈（Outgoing）。
            var startFailed = false
            snapshotFlow {
                val scope = navScope
                val exit = (-scope.relativeDepth).coerceIn(0f, 1f)
                exit to (scope.gesture != null || scope.role == NavRole.Outgoing)
            }.collect { (exit, popping) ->
                val own = heroHost.flight?.takeIf {
                    it.albumId == albumId && heroHost.isBackwardFlight(it.id)
                }
                if (own != null) {
                    if (exit <= 0.01f && !popping) {
                        heroHost.finishFlight(own.id)
                        heroHost.restoreOrigin(albumId)
                        startFailed = false
                    } else {
                        heroHost.setBackwardProgress(own.id, exit)
                    }
                    return@collect
                }
                if (exit <= 0.01f) {
                    startFailed = false
                    return@collect
                }
                if (!popping || startFailed) return@collect
                if (heroHost.flight != null || heroHost.pending != null) return@collect
                if (!heroHost.startBackward(albumId)) {
                    startFailed = true
                    heroHost.restoreOrigin(albumId)
                    return@collect
                }
                heroHost.flight
                    ?.takeIf { it.albumId == albumId && heroHost.isBackwardFlight(it.id) }
                    ?.let { heroHost.setBackwardProgress(it.id, exit) }
            }
        }
        DisposableEffect(heroHost, albumId) {
            onDispose {
                val flight = heroHost.flight
                if (flight != null && flight.albumId == albumId && heroHost.isBackwardFlight(flight.id)) {
                    heroHost.finishFlight(flight.id)
                }
            }
        }
    }
    val effectiveCoverUrl = initialCoverUrl?.takeIf { it.isNotBlank() }
        ?: heroHost?.coverUrl(albumId)?.takeIf { it.isNotBlank() }
    val effectiveTitle = initialTitle?.takeIf { it.isNotBlank() }
        ?: heroHost?.albumTitle(albumId)?.takeIf { it.isNotBlank() }

    val loadedDetail = (uiState.albumState as? AlbumUiState.Loaded)?.detail
    // 详情页预览：登录且详情就绪后取首章前几页；未登录、无章节、请求失败一律隐藏整块
    var previewPages by remember(albumId) { mutableStateOf<List<PageImage>>(emptyList()) }
    val firstChapterId = loadedDetail?.chapters?.firstOrNull()?.id
    LaunchedEffect(albumId, user?.uid, firstChapterId) {
        previewPages = emptyList()
        if (user == null || firstChapterId == null) return@LaunchedEffect
        val outcome = authCall.call { gateway.photo(firstChapterId) }
        previewPages = (outcome as? SessionOutcome.Ok)?.value?.images.orEmpty()
    }
    val previewPlan = remember(loadedDetail?.chapters, previewPages) {
        loadedDetail?.let { albumPreviewPlan(it.chapters, previewPages) }
    }
    val currentTitle = loadedDetail?.title?.takeIf { it.isNotBlank() }
        ?: effectiveTitle?.takeIf { it.isNotBlank() }
        ?: "详情"
    val resumeId = uiState.latestPhotoId?.takeIf { id -> loadedDetail?.chapters?.any { it.id == id } == true }

    val scrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 8 ||
                rightListState.firstVisibleItemIndex > 0 || rightListState.firstVisibleItemScrollOffset > 8
        }
    }
    val topBarHeight = couiTopBarHeight()
    val contentPadding = PaddingValues(top = topBarHeight, bottom = 24.dp)

    // 单栏与大屏两栏共用同一套条目构建：这些函数只描述放置哪些区块，
    // 是否拆到左右两栏由外部布局决定，避免两套列表逻辑各自漂移。
    fun LazyListScope.headerItem() {
        // 首项必须始终渲染，确保列表卡片点击后首帧完成头图测量，转场动画即时起飞
        item(key = "header") {
            CouiDetailHeader(
                albumId = albumId,
                detail = loadedDetail,
                fallbackTitle = effectiveTitle,
                fallbackCoverUrl = effectiveCoverUrl,
                resumeId = resumeId,
                loggedIn = user != null,
                favorited = uiState.favorited,
                liked = sessionCache.engagement.likedOf(albumId) ?: uiState.liked,
                tracking = uiState.tracking,
                trackingBusy = uiState.trackingBusy,
                onRead = { photoId -> onOpenReader(photoId, null) },
                onOpenSearch = onOpenSearch,
                onFavorite = {
                    if (!uiState.favoriteBusy) stateHolder.onFavoriteClick(user != null, onOpenLogin)
                },
                onLike = {
                    if (!uiState.likeBusy) stateHolder.like(user != null, onOpenLogin)
                },
                onTrack = {
                    if (!uiState.trackingBusy && uiState.tracking != null) {
                        stateHolder.toggleTracking(user != null, onOpenLogin)
                    }
                },
                onDownloadAll = if (loadedDetail == null || loadedDetail.chapters.isEmpty()) {
                    null
                } else {
                    { downloadAll(loadedDetail.id, loadedDetail.chapters) }
                },
            )
        }
    }

    fun LazyListScope.previewItems() {
        // 预览紧随详情信息、位于章节之前：首章前几页，点第 i 张直达第 i 页
        previewPlan?.let { plan ->
            item(key = "preview-title") { CouiSectionTitle(text = "预览") }
            item(key = "preview") {
                CouiPreviewRow(
                    plan = plan,
                    imageLoader = previewImageLoader,
                    onOpenPage = { page -> onOpenReader(plan.chapterId, page) },
                )
            }
        }
    }

    fun LazyListScope.chapterItems(detail: AlbumDetail) {
        item(key = "chapters-title") {
            CouiSectionTitle(
                text = if (detail.chapters.isEmpty()) "章节" else "章节（${detail.chapters.size}）",
            )
        }
        if (detail.chapters.isEmpty()) {
            item(key = "chapters-empty") {
                CouiNoticeCard(message = "暂无章节")
            }
        }
        ChapterGroups.of(detail.chapters, Chapter::id).forEach { group ->
            item(key = "chapter-group-" + group.id) {
                CouiChapterGroupCard(
                    group = group.items,
                    startPosition = group.startPosition,
                    expanded = group.id in uiState.expandedGroups,
                    resumeId = resumeId,
                    readPhotoIds = uiState.readPhotoIds,
                    onToggle = { stateHolder.toggleGroup(group.id) },
                    onRead = { photoId -> onOpenReader(photoId, null) },
                    onDownload = { chapter, title -> downloadChapter(chapter, detail.id, title) },
                )
            }
        }
    }

    fun LazyListScope.infoItems(detail: AlbumDetail) {
        if (detail.description.isNotBlank()) {
            item(key = "description") {
                CouiDescriptionCard(
                    text = detail.description,
                    expanded = uiState.descriptionExpanded,
                    onToggle = { stateHolder.setDescriptionExpanded(!uiState.descriptionExpanded) },
                    onCopy = { copyText("简介", detail.description) },
                )
            }
        }
        val validTags = detail.tags.map { it.trim() }.filter { it.isNotBlank() }
        if (validTags.isNotEmpty()) {
            item(key = "tags-title") { CouiSectionTitle(text = "标签") }
            item(key = "tags") {
                CouiTagsCard(tags = validTags, onClick = { tag -> onOpenSearch(tag, SearchScope.TAG) })
            }
        }
        item(key = "info-title") { CouiSectionTitle(text = "作品信息") }
        item(key = "info") {
            CouiInfoCard(
                albumId = albumId,
                detail = detail,
                onCopy = { label, value -> copyText(label, value) },
                onOpenSearch = onOpenSearch,
            )
        }
    }

    fun LazyListScope.tailItems(detail: AlbumDetail) {
        if (detail.related.isNotEmpty()) {
            item(key = "related-title") { CouiSectionTitle(text = "更多相关") }
            item(key = "related") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = CouiPagePadding),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(detail.related, key = { it.id }) { related ->
                        CouiCompactWorkCard(
                            title = related.title,
                            coverUrl = related.coverUrl,
                            subtitle = related.authors.joinToString(" / ").ifBlank { null },
                            modifier = Modifier.width(104.dp),
                            onClick = { onOpenAlbum(related.id) },
                        )
                    }
                }
            }
        }
        item(key = "comments-title") {
            val total = (uiState.commentsState as? CommentsUiState.Loaded)?.feed?.total
            CouiSectionTitle(text = if (total != null) "评论（$total）" else "评论")
        }
        item(key = "comment-composer") {
            CouiCommentComposer(
                loggedIn = user != null,
                text = uiState.commentText,
                busy = uiState.commentBusy,
                onTextChange = stateHolder::setCommentText,
                onLogin = onOpenLogin,
                onSend = stateHolder::postComment,
            )
        }
        when (val cs = uiState.commentsState) {
            is CommentsUiState.Loading -> item(key = "comments-loading") {
                LoadingScreen(modifier = Modifier.padding(vertical = 40.dp))
            }

            is CommentsUiState.Failed -> item(key = "comments-failed") {
                CouiNoticeCard(
                    message = if (cs.sessionExpired) SessionOutcome.Expired.message else cs.message,
                    actionText = if (cs.sessionExpired) "去登录" else "重试",
                    onAction = if (cs.sessionExpired) onOpenLogin else stateHolder::reloadComments,
                )
            }

            is CommentsUiState.Loaded -> item(key = "comments-card") {
                CouiCommentsCard(
                    feed = cs.feed,
                    onReply = { comment ->
                        if (user == null) onOpenLogin() else stateHolder.setReplyTarget(comment)
                    },
                    onLoadMore = stateHolder::loadMoreComments,
                )
            }
        }
    }

    var rootCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    CompositionLocalProvider(
        LocalAlbumDetailRootCoordinates provides rootCoordinates,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { rootCoordinates = it },
        ) {
            CouiSurfaceBackground(modifier = Modifier.fillMaxSize())
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .couiBackdropSource(backdrop),
            ) {
                // 大屏两栏：左封面+信息、右预览/章节/评论，避免单栏拉满整屏；
                // 手机断点以下或详情未就绪时仍走原来的单栏列表
                val wideTwoPane = LocalWideLayout.current.twoPaneDetail
                val wideDetail = loadedDetail?.takeIf { wideTwoPane }
                if (wideDetail != null) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        TrackedLazyColumn(
                            state = listState,
                            contentPadding = contentPadding,
                            modifier = Modifier.weight(0.38f).fillMaxHeight(),
                        ) {
                            headerItem()
                            infoItems(wideDetail)
                        }
                        TrackedLazyColumn(
                            state = rightListState,
                            contentPadding = contentPadding,
                            modifier = Modifier.weight(0.62f).fillMaxHeight(),
                        ) {
                            previewItems()
                            chapterItems(wideDetail)
                            tailItems(wideDetail)
                        }
                    }
                } else {
                    TrackedLazyColumn(
                        state = listState,
                        contentPadding = contentPadding,
                        // 未分栏的大屏仍把单栏内容收进可读宽度，评论不会一行横跨整屏
                        modifier = Modifier.fillMaxSize().readableWidth(),
                    ) {
                        headerItem()

                        when (val s = uiState.albumState) {
                            is AlbumUiState.Loading -> item(key = "detail-loading") {
                                LoadingScreen(modifier = Modifier.padding(vertical = 40.dp))
                            }

                            is AlbumUiState.Failed -> item(key = "detail-failed") {
                                CouiNoticeCard(
                                    message = if (s.sessionExpired) SessionOutcome.Expired.message else s.message,
                                    actionText = if (s.sessionExpired) "去登录" else "重试",
                                    onAction = if (s.sessionExpired) onOpenLogin else stateHolder::refresh,
                                )
                            }

                            is AlbumUiState.Loaded -> {
                                val detail = s.detail
                                previewItems()
                                chapterItems(detail)
                                infoItems(detail)
                                tailItems(detail)
                            }
                        }
                    }
                }
            }

            CouiTopBar(
                title = currentTitle,
                scrolled = scrolled,
                backdrop = backdrop,
                onBack = onBack,
                actions = {
                        if (loadedDetail != null) {
                            IconButton(onClick = { share(loadedDetail) }) {
                                Icon(
                                    imageVector = MiuixIcons.Share,
                                    contentDescription = "分享",
                                    tint = colors.textPrimary,
                                )
                            }
                            if (loadedDetail.chapters.size > 1) {
                                IconButton(onClick = { downloadAll(loadedDetail.id, loadedDetail.chapters) }) {
                                    Icon(
                                        imageVector = MiuixIcons.Download,
                                        contentDescription = "下载全部",
                                        tint = colors.textPrimary,
                                    )
                                }
                            }
                        }
                    },
                )

                GlassSnackbarHost(
                    state = snackbarHostState,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    backdrop = backdrop,
                )

                // 收藏夹选择面板：未收藏=加入，已收藏=移动，底部提供取消收藏
                CouiFolderSheet(
                    visible = uiState.showFolderSheet,
                    favorited = uiState.favorited,
                    folders = uiState.folders,
                    onDismiss = stateHolder::closeFolderSheet,
                    onPick = stateHolder::pickFolder,
                    onCancelFavorite = stateHolder::cancelFavorite,
                )
                // 回复面板：标题带被回复作者，发送后刷新评论列表以显示新回复
                CouiReplySheet(
                    visible = uiState.replyTarget != null,
                    targetAuthor = uiState.replyTarget?.author,
                    busy = uiState.replyBusy,
                    onDismiss = { stateHolder.setReplyTarget(null) },
                    onSend = stateHolder::sendReply,
                )
        }
    }
}

/** 头部：3:4 封面 + 标题/作者/车号/热度 + 阅读按钮与高频动作胶囊。 */
@Composable
private fun CouiDetailHeader(
    albumId: String,
    detail: AlbumDetail?,
    fallbackTitle: String?,
    fallbackCoverUrl: String?,
    resumeId: String?,
    loggedIn: Boolean,
    favorited: Boolean,
    liked: Boolean,
    tracking: Boolean?,
    trackingBusy: Boolean,
    onRead: (String) -> Unit,
    onOpenSearch: (String, SearchScope) -> Unit,
    onFavorite: () -> Unit,
    onLike: () -> Unit,
    onTrack: () -> Unit,
    onDownloadAll: (() -> Unit)?,
) {
    val colors = LocalCouiColors.current
    val heroHost = LocalAlbumHeroHost.current
    val title = detail?.title?.trim()?.takeIf { it.isNotBlank() } ?: fallbackTitle?.trim().orEmpty()
    val coverUrl = detail?.coverUrl?.trim()?.takeIf { it.isNotBlank() } ?: fallbackCoverUrl?.trim()
    val displayId = detail?.id?.trim()?.takeIf { it.isNotBlank() && it != "-1" } ?: albumId
    val validAuthors = detail?.authors.orEmpty().map { it.trim() }.filter { it.isNotBlank() }

    CouiCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CouiPagePadding, vertical = 10.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            if (!coverUrl.isNullOrBlank()) {
                // 头图是「列表卡片 → 详情页」形变飞行的落点：两端圆角取同一档皮肤嵌套卡片曲率
                val heroRadius = LocalCouiShapes.current.nested
                Box(
                    modifier = Modifier
                        .size(width = 108.dp, height = 144.dp)
                        .squircleClip(heroRadius)
                        .background(colors.accentContainer)
                        .albumHeroHeader(albumId, coverUrl, heroRadius.value),
                ) {
                    AlbumCoverImage(
                        url = coverUrl,
                        contentDescription = title,
                        modifier = Modifier.fillMaxSize(),
                        crossfade = false,
                        skeleton = true,
                        // 首帧画出（或确认失败）后告诉飞行层：覆盖层要等这一步才收场
                        onState = { state ->
                            if (state is AsyncImagePainter.State.Success || state is AsyncImagePainter.State.Error) {
                                heroHost?.markHeaderReady(albumId)
                            }
                        },
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                if (title.isNotBlank()) {
                    BasicText(
                        text = title,
                        style = CouiType.title.copy(color = colors.textPrimary),
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (validAuthors.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(colors.accentContainer)
                            .couiClickable(ensureMinTouchTarget = false, onClick = { onOpenSearch(validAuthors.first(), SearchScope.AUTHOR) })
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Contacts,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        BasicText(
                            text = validAuthors.joinToString("、"),
                            style = CouiType.caption.copy(color = colors.accent),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                BasicText(
                    text = "禁漫车 #$displayId",
                    style = CouiType.caption.copy(color = colors.textTertiary),
                )
                if (detail != null) {
                    val metrics = buildList {
                        detail.likes?.trim()?.takeIf { it.isNotBlank() && it != "0" }?.let { add("喜欢 $it") }
                        detail.views?.trim()?.takeIf { it.isNotBlank() && it != "0" }?.let { add("浏览 $it") }
                        if (detail.commentCount > 0) add("评论 ${detail.commentCount}")
                        if (detail.pageCount > 0) add("页数 ${detail.pageCount}")
                    }
                    if (metrics.isNotEmpty()) {
                        BasicText(
                            text = metrics.joinToString(" · "),
                            style = CouiType.caption.copy(color = colors.textSecondary),
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }
        if (detail != null) {
            Spacer(modifier = Modifier.height(16.dp))
            val startId = resumeId ?: detail.chapters.firstOrNull()?.id
            CouiButton(
                text = if (resumeId != null) "继续阅读" else "开始阅读",
                onClick = { startId?.let(onRead) },
                modifier = Modifier.fillMaxWidth(),
                enabled = startId != null,
            )
            Spacer(modifier = Modifier.height(10.dp))
            // 高频动作：胶囊标签，状态用选中态容器回显
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CouiChip(
                    text = if (favorited) "已收藏" else "收藏",
                    onClick = onFavorite,
                    selected = favorited,
                )
                CouiChip(
                    text = if (liked) "已喜欢" else "喜欢",
                    onClick = onLike,
                    selected = liked,
                )
                if (loggedIn) {
                    CouiChip(
                        text = when {
                            trackingBusy -> "处理中"
                            tracking == true -> "追更中"
                            else -> "追更"
                        },
                        onClick = onTrack,
                        selected = tracking == true,
                        enabled = !trackingBusy,
                    )
                }
                if (onDownloadAll != null) {
                    CouiChip(text = "下载", onClick = onDownloadAll)
                }
            }
        }
    }
}

/** 简介：默认 4 行折叠，标题栏提供复制入口。 */
@Composable
private fun CouiDescriptionCard(
    text: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    onCopy: () -> Unit,
) {
    val colors = LocalCouiColors.current
    CouiCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CouiPagePadding, vertical = 6.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                text = "简介",
                style = CouiType.section.copy(color = colors.textPrimary),
                modifier = Modifier.weight(1f),
            )
            CouiTextButton(text = "复制", onClick = onCopy)
        }
        BasicText(
            text = text,
            style = CouiType.body.copy(color = colors.textSecondary),
            maxLines = if (expanded) Int.MAX_VALUE else 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (text.length > 80) {
            CouiTextButton(
                text = if (expanded) "收起" else "显示全部",
                onClick = onToggle,
            )
        }
    }
}

/** 标签：可点进标签检索的胶囊集合。 */
@Composable
private fun CouiTagsCard(tags: List<String>, onClick: (String) -> Unit) {
    CouiCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CouiPagePadding, vertical = 6.dp),
        contentPadding = PaddingValues(12.dp),
    ) {
        CouiTagFlow(tags = tags, onClick = onClick)
    }
}

/** 作品信息块：作者/作品/登场人物（可点进搜索）+ 禁漫车号（可复制）。 */
@Composable
private fun CouiInfoCard(
    albumId: String,
    detail: AlbumDetail,
    onCopy: (String, String) -> Unit,
    onOpenSearch: (String, SearchScope) -> Unit,
) {
    val colors = LocalCouiColors.current
    val validWorks = detail.works.map { it.trim() }.filter { it.isNotBlank() }
    val validActors = detail.actors.map { it.trim() }.filter { it.isNotBlank() }
    val displayId = detail.id.trim().takeIf { it.isNotBlank() && it != "-1" } ?: albumId
    CouiCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CouiPagePadding, vertical = 6.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
    ) {
        if (validWorks.isNotEmpty()) {
            CouiInfoRow(
                title = "作品",
                summary = validWorks.joinToString("、"),
                onClick = { onOpenSearch(validWorks.first(), SearchScope.WORK) },
            )
        }
        if (validActors.isNotEmpty()) {
            CouiInfoRow(
                title = "登场人物",
                summary = validActors.joinToString("、"),
                onClick = { onOpenSearch(validActors.first(), SearchScope.ACTOR) },
            )
        }
        CouiInfoRow(
            title = "禁漫车",
            summary = "#$displayId",
            trailing = {
                IconButton(
                    onClick = { onCopy("禁漫车号", displayId) },
                    minWidth = 36.dp,
                    minHeight = 36.dp,
                ) {
                    Icon(
                        imageVector = MiuixIcons.Copy,
                        contentDescription = "复制车号",
                        tint = colors.textTertiary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            },
        )
    }
}

@Composable
private fun CouiInfoRow(
    title: String,
    summary: String,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = LocalCouiColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.couiClickable(onClick) else Modifier)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = title,
            style = CouiType.body.copy(color = colors.textPrimary),
            modifier = Modifier.width(84.dp),
        )
        BasicText(
            text = summary,
            style = CouiType.caption.copy(color = colors.textSecondary),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) trailing()
    }
}

/** 章节组：一组 10 话，默认仅展开续读所在组；组内章节用嵌套卡片。 */
@Composable
private fun CouiChapterGroupCard(
    group: List<Chapter>,
    startPosition: Int,
    expanded: Boolean,
    resumeId: String?,
    readPhotoIds: Set<String>,
    onToggle: () -> Unit,
    onRead: (String) -> Unit,
    onDownload: (Chapter, String) -> Unit,
) {
    val colors = LocalCouiColors.current
    // 服务端 series 允许缺 name，直接展示会得到空白行与悬空的 " ~ "，统一按话数回落。
    val titles = group.mapIndexed { index, chapter ->
        ChapterLabels.chapterTitle(chapter.title, startPosition + index)
    }
    // 单话分组没有折叠意义，直接展示该话
    val showToggle = group.size > 1

    CouiCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CouiPagePadding, vertical = 6.dp),
        contentPadding = PaddingValues(12.dp),
    ) {
        if (showToggle) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .couiClickable(onToggle)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    BasicText(
                        text = ChapterLabels.groupTitle(group.map { it.title }, startPosition),
                        style = CouiType.section.copy(color = colors.textPrimary),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    BasicText(
                        text = "${group.size} 话 · 点击${if (expanded) "收起" else "展开"}",
                        style = CouiType.caption.copy(color = colors.textSecondary),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                BasicText(
                    text = if (expanded) "收起" else "展开",
                    style = CouiType.caption.copy(color = colors.accent),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
        if (expanded || !showToggle) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (showToggle) 8.dp else 0.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                group.forEachIndexed { index, chapter ->
                    val title = titles[index]
                    CouiChapterRow(
                        title = title,
                        marker = when {
                            chapter.id == resumeId -> "上次读到"
                            chapter.id in readPhotoIds -> "已读"
                            else -> null
                        },
                        onRead = { onRead(chapter.id) },
                        onDownload = { onDownload(chapter, title) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CouiChapterRow(
    title: String,
    marker: String?,
    onRead: () -> Unit,
    onDownload: () -> Unit,
) {
    val colors = LocalCouiColors.current
    CouiNestedCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .couiClickable(onRead)
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(
                    text = title,
                    style = CouiType.body.copy(color = colors.textPrimary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (marker != null) {
                    BasicText(
                        text = marker,
                        style = CouiType.caption.copy(color = colors.textTertiary),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            IconButton(onClick = onDownload, minWidth = 36.dp, minHeight = 36.dp) {
                Icon(
                    imageVector = MiuixIcons.Download,
                    contentDescription = "下载",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** 详情页预览条：首章前几页缩略图，点第 i 张直达阅读器第 i 页。 */
@Composable
private fun CouiPreviewRow(
    plan: AlbumPreviewPlan,
    imageLoader: ImageLoader,
    onOpenPage: (page: Int) -> Unit,
) {
    val colors = LocalCouiColors.current
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = CouiPagePadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(plan.pages, key = { it.index }) { page ->
            AsyncImage(
                model = page,
                imageLoader = imageLoader,
                contentDescription = "预览第 ${page.index + 1} 页",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 104.dp, height = 140.dp)
                    .squircleClip(LocalCouiShapes.current.control)
                    .background(colors.accentContainer)
                    .couiClickable(ensureMinTouchTarget = false, onClick = { onOpenPage(page.index + 1) }),
            )
        }
    }
}

/** 评论输入：未登录时引导登录；登录后限 200 字并可发表。 */
@Composable
private fun CouiCommentComposer(
    loggedIn: Boolean,
    text: String,
    busy: Boolean,
    onTextChange: (String) -> Unit,
    onLogin: () -> Unit,
    onSend: () -> Unit,
) {
    val colors = LocalCouiColors.current
    CouiCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CouiPagePadding, vertical = 6.dp),
    ) {
        if (!loggedIn) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .couiClickable(onLogin)
                    .padding(vertical = 6.dp),
            ) {
                BasicText(
                    text = "登录后发表评论",
                    style = CouiType.body.copy(color = colors.textPrimary),
                )
                BasicText(
                    text = "登录账号即可参与讨论",
                    style = CouiType.caption.copy(color = colors.textSecondary),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        } else {
            CouiTextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = "写下你的评论（最多 $COMMENT_REPLY_MAX_LENGTH 字）",
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                BasicText(
                    text = "${text.length}/$COMMENT_REPLY_MAX_LENGTH",
                    style = CouiType.caption.copy(color = colors.textTertiary),
                )
                CouiButton(
                    text = if (busy) "发表中…" else "发表",
                    onClick = onSend,
                    enabled = !busy && text.isNotBlank(),
                )
            }
        }
    }
}

/** 只读评论区：评论卡 + 页脚加载更多。 */
@Composable
private fun CouiCommentsCard(
    feed: PagedFeed<CommentItem>,
    onReply: (CommentItem) -> Unit,
    onLoadMore: () -> Unit,
) {
    val colors = LocalCouiColors.current
    CouiCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CouiPagePadding, vertical = 6.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        if (feed.items.isEmpty()) {
            BasicText(
                text = "暂无评论，来抢沙发",
                style = CouiType.caption.copy(color = colors.textSecondary),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 16.dp),
            )
        }
        feed.items.forEach { comment ->
            CouiCommentRow(item = comment, onReply = onReply)
        }
        if (feed.items.isNotEmpty()) {
            LoadMoreFooter(
                feed = feed,
                onLoadMore = onLoadMore,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** 评论单条：头像 + 等级/称号 + 徽章 + 分段正文，回复默认折叠 2 条；回复区带缩进线并可继续回复。 */
@Composable
private fun CouiCommentRow(
    item: CommentItem,
    depth: Int = 0,
    onReply: ((CommentItem) -> Unit)? = null,
) {
    val colors = LocalCouiColors.current
    // 缩进只按层级累加到上限：层级仍然逐层传下去（回复线照画），但不再继续吃掉正文宽度
    val indentDepth = depth.coerceAtMost(COUI_COMMENT_MAX_INDENT_DEPTH)
    var repliesExpanded by remember(item.id) { mutableStateOf(item.replies.size <= COUI_COMMENT_VISIBLE_REPLIES) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp + (indentDepth * 12).dp, end = 14.dp, top = 8.dp, bottom = 8.dp)
            .drawBehind {
                if (depth > 0) {
                    // 回复线：贴在缩进区外侧的竖线，标识回复归属层级
                    drawRect(
                        color = colors.divider,
                        topLeft = Offset(-6.dp.toPx(), 0f),
                        size = Size(2.dp.toPx(), size.height),
                    )
                }
            },
    ) {
        // 头像列：回复越深头像越小，等级数字挂在头像下方
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CouiCommentAvatar(url = item.avatarUrl, size = if (depth == 0) 40.dp else 30.dp)
            item.level?.let {
                BasicText(
                    text = "Lv.$it",
                    style = CouiType.caption.copy(color = colors.textTertiary),
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    text = item.author,
                    style = CouiType.body.copy(color = colors.accent),
                )
                item.levelName?.let {
                    BasicText(
                        text = " · $it",
                        style = CouiType.caption.copy(color = colors.textTertiary),
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                if (item.likes > 0) {
                    BasicText(
                        text = "赞 ${item.likes}",
                        style = CouiType.caption.copy(color = colors.textTertiary),
                    )
                }
            }
            item.postDate?.let {
                BasicText(
                    text = it,
                    style = CouiType.caption.copy(color = colors.textTertiary),
                )
            }
            if (item.badges.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (badge in item.badges) {
                        CouiCommentAvatar(
                            url = badge,
                            size = 22.dp,
                            contentDescription = COUI_COMMENT_BADGE_DESCRIPTION,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            CouiCommentBody(item = item)
            if (onReply != null) {
                CouiTextButton(text = "回复", onClick = { onReply(item) })
            }
            if (item.replies.isNotEmpty()) {
                val shown = if (repliesExpanded) item.replies else item.replies.take(COUI_COMMENT_VISIBLE_REPLIES)
                for (reply in shown) {
                    CouiCommentRow(item = reply, depth = depth + 1, onReply = onReply)
                }
                if (item.replies.size > COUI_COMMENT_VISIBLE_REPLIES) {
                    CouiTextButton(
                        text = if (repliesExpanded) "收起回复" else "展开 ${item.replies.size} 条回复",
                        onClick = { repliesExpanded = !repliesExpanded },
                    )
                }
            }
        }
    }
}

/**
 * 头像与徽章统一的圆形图片：底色圆垫在图片下方，缺图、加载中、加载失败都不会露出透明空洞。
 */
@Composable
private fun CouiCommentAvatar(url: String?, size: Dp, contentDescription: String? = null) {
    val colors = LocalCouiColors.current
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(colors.nestedCard),
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size).clip(CircleShape),
            )
        }
    }
}

/** 正文按分段渲染：推荐作品拆成强调链接，其余按原文换行展示。 */
@Composable
private fun CouiCommentBody(item: CommentItem) {
    val colors = LocalCouiColors.current
    val segments = item.body.ifEmpty { listOf(CommentSegment.Text(item.content)) }
    Column(modifier = Modifier.fillMaxWidth()) {
        for (segment in segments) {
            when (segment) {
                is CommentSegment.Text -> if (segment.text.isNotBlank()) {
                    BasicText(
                        text = segment.text,
                        style = CouiType.body.copy(color = colors.textPrimary),
                        maxLines = COUI_COMMENT_BODY_MAX_LINES,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                is CommentSegment.Album -> BasicText(
                    text = segment.label,
                    style = CouiType.body.copy(color = colors.accent),
                )
            }
        }
    }
}

/** 收藏夹选择面板：未收藏=加入，已收藏=移动，底部提供取消收藏。 */
@Composable
private fun CouiFolderSheet(
    visible: Boolean,
    favorited: Boolean,
    folders: Map<String, String>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
    onCancelFavorite: () -> Unit,
) {
    CouiBottomSheet(visible = visible, onDismiss = onDismiss) {
        val colors = LocalCouiColors.current
        BasicText(
            text = if (favorited) "移动收藏夹" else "选择收藏夹",
            style = CouiType.section.copy(color = colors.textPrimary),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        val allLabel = folders["0"] ?: "全部收藏"
        CouiSettingsRow(
            title = allLabel,
            summary = "默认收藏夹",
            onClick = { onPick("0") },
            modifier = Modifier.padding(horizontal = 20.dp),
            trailing = {
                BasicText(
                    text = "选择",
                    style = CouiType.caption.copy(color = colors.accent),
                    modifier = Modifier.padding(start = 8.dp),
                )
            },
        )
        folders.entries
            .filter { it.key != "0" }
            .forEach { (id, name) ->
                CouiSettingsRow(
                    title = name,
                    onClick = { onPick(id) },
                    modifier = Modifier.padding(horizontal = 20.dp),
                    trailing = {
                        BasicText(
                            text = "选择",
                            style = CouiType.caption.copy(color = colors.accent),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    },
                )
            }
        if (favorited) {
            CouiTextButton(
                text = "取消收藏",
                color = colors.error,
                onClick = onCancelFavorite,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

/** 回复面板：标题带被回复作者，发送后由状态持有者刷新评论列表。 */
@Composable
private fun CouiReplySheet(
    visible: Boolean,
    targetAuthor: String?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSend: (String) -> Unit,
) {
    var replyText by remember { mutableStateOf("") }
    LaunchedEffect(visible) { if (!visible) replyText = "" }
    CouiBottomSheet(visible = visible, onDismiss = onDismiss) {
        val colors = LocalCouiColors.current
        BasicText(
            text = "回复 ${targetAuthor.orEmpty()}",
            style = CouiType.section.copy(color = colors.textPrimary),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        CouiTextField(
            value = replyText,
            onValueChange = { replyText = it },
            placeholder = "写下你的回复（最多 $COMMENT_REPLY_MAX_LENGTH 字）",
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        CouiButton(
            text = if (busy) "发送中…" else "发送",
            onClick = { onSend(replyText) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            enabled = !busy && replyText.isNotBlank(),
        )
    }
}



