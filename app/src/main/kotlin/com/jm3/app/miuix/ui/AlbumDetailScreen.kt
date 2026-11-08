package com.jm3.app.miuix.ui

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.jm3.app.miuix.data.download.DownloadManager
import com.jm3.app.miuix.ui.glass.GlassPullToRefresh
import com.jm3.app.miuix.ui.glass.GlassScaffold
import com.jm3.app.miuix.ui.glass.GlassSmallTopAppBar
import com.jm3.app.miuix.ui.glass.GlassTopAppBar
import com.jm3.app.miuix.ui.skin.LocalSkin
import com.jm3.core.AlbumPreviewPlan
import com.jm3.core.ChapterLabels
import com.jm3.core.Jm3Gateway
import com.jm3.core.albumPreviewPlan
import com.jm3.core.download.DownloadStore
import com.jm3.core.history.HistoryRepository
import com.jm3.core.session.AccountManager
import com.jm3.core.session.AuthCall
import com.jm3.core.session.SessionOutcome
import com.jm3.core.asAlbumDetailPort
import com.jm3.core.model.AlbumDetail
import com.jm3.core.model.Chapter
import com.jm3.core.model.CommentItem
import com.jm3.core.model.CommentPage
import com.jm3.core.model.CommentSegment
import com.jm3.core.model.FavoriteFolderAction
import com.jm3.core.model.PageImage
import com.jm3.core.model.SearchResult
import com.jm3.core.model.SearchScope
import com.jm3.core.reader.ChapterGroups
import top.yukonga.miuix.kmp.nav.core.LocalNavTransitionScope
import top.yukonga.miuix.kmp.nav.transition.NavRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
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

/** 折叠态默认展示的回复条数；超出部分由「展开 N 条回复」控制。 */
private const val COMMENT_VISIBLE_REPLIES = 2
/** 回复缩进的最大层级：更深的回复不再追加缩进，否则正文宽度会被挤没。 */
private const val COMMENT_MAX_INDENT_DEPTH = 4
/** 单条评论正文最多展示的行数；超长评论折叠成省略号，避免整条铺满一屏。 */
private const val COMMENT_BODY_MAX_LINES = 12
/** 徽章图的无障碍描述：徽章本身无文字，读屏需要知道这里是一枚徽章。 */
private const val COMMENT_BADGE_DESCRIPTION = "徽章"

// AlbumUiState 和 CommentsUiState 已下沉至 AlbumDetailStateHolder 统一定义

/** 详情页：信息/简介/标签/相关作品/章节分组/评论，对齐原版 Desc + Series 能力域。 */
@Composable
internal fun AlbumDetailScreen(
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
    val context = LocalContext.current
    val user by account.user.collectAsState()
    val scope = rememberCoroutineScope()
    val skin = LocalSkin.current
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
    val refreshState = rememberPullToRefreshState(refreshThreshold = 0.18f)
    val selectionHaptic = rememberSelectionHaptic()

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
            // 进场（Incoming）与静止（Top）都不满足，避免进场结束后误起一段停在进度 0、此后不再收场的反向飞行。
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
                        // 回弹到静息（取消手势或程序化返回尚未起步）：收场并把 origin 放回去，
                        // 取消后不残留、下一次返回仍能起飞；提交中的飞行不在这里拆，等页面销毁时收场
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
                // 正向飞行或待飞尚未收场时先让它完成，避免两段飞行互相顶掉
                if (heroHost.flight != null || heroHost.pending != null) return@collect
                if (!heroHost.startBackward(albumId)) {
                    // 拿不到两端矩形就不飞，退化成普通页转场；startBackward 会移除 origin，这里放回去
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
    val effectiveCoverUrl = initialCoverUrl?.takeIf { it.isNotBlank() } ?: heroHost?.coverUrl(albumId)?.takeIf { it.isNotBlank() }
    val effectiveTitle = initialTitle?.takeIf { it.isNotBlank() } ?: heroHost?.albumTitle(albumId)?.takeIf { it.isNotBlank() }

    val loadedDetail = (uiState.albumState as? AlbumUiState.Loaded)?.detail
    // 详情页预览：登录且详情就绪后取首章前几页；未登录、无章节、请求失败一律隐藏整块
    var previewPages by remember(albumId) { mutableStateOf<List<PageImage>>(emptyList()) }
    val firstChapterId = loadedDetail?.chapters?.sortedBy { it.sortOrder }?.firstOrNull()?.id
    LaunchedEffect(albumId, user?.uid, firstChapterId) {
        previewPages = emptyList()
        if (user == null || firstChapterId == null) return@LaunchedEffect
        val outcome = authCall.call { gateway.photo(firstChapterId) }
        previewPages = (outcome as? SessionOutcome.Ok)?.value?.images.orEmpty()
    }
    val previewPlan = remember(loadedDetail?.chapters, previewPages) {
        loadedDetail?.let { albumPreviewPlan(it.chapters, previewPages) }
    }
    val currentTitle = loadedDetail?.title?.takeIf { it.isNotBlank() } ?: effectiveTitle?.takeIf { it.isNotBlank() } ?: "详情"
    val resumeId = uiState.latestPhotoId?.takeIf { id -> loadedDetail?.chapters?.any { it.id == id } == true }

    // 单栏与大屏两栏共用同一套条目构建：这些函数只描述放置哪些区块，
    // 是否拆到左右两栏由外部布局决定，避免两套列表逻辑各自漂移。
    fun LazyListScope.headerItem() {
        // 首项必须始终渲染，确保列表卡片点击后首帧完成头图测量，转场动画即时起飞
        item(key = "header") {
            DetailHeader(
                albumId = albumId,
                detail = loadedDetail,
                fallbackTitle = effectiveTitle,
                fallbackCoverUrl = effectiveCoverUrl,
                resumeId = resumeId,
                loggedIn = user != null,
                favorited = uiState.favorited,
                favoriteBusy = uiState.favoriteBusy,
                liked = sessionCache.engagement.likedOf(albumId) ?: uiState.liked,
                likeBusy = uiState.likeBusy,
                tracking = uiState.tracking,
                trackingBusy = uiState.trackingBusy,
                onRead = { photoId -> onOpenReader(photoId, null) },
                onFavorite = { stateHolder.onFavoriteClick(user != null, onOpenLogin) },
                onLike = { stateHolder.like(user != null, onOpenLogin) },
                onTrack = { stateHolder.toggleTracking(user != null, onOpenLogin) },
                onOpenSearch = onOpenSearch,
                onDownloadAll = if (loadedDetail == null || loadedDetail.chapters.isEmpty()) {
                    null
                } else {
                    // 与顶栏「下载全部」共用同一套动作与反馈，避免两处行为分叉
                    { downloadAll(loadedDetail.id, loadedDetail.chapters) }
                },
            )
        }
    }

    fun LazyListScope.previewItems() {
        // 预览紧随详情信息、位于章节之前：首章前几页，点第 i 张直达第 i 页
        previewPlan?.let { plan ->
            item(key = "preview-title") { SmallTitle(text = "预览") }
            item(key = "preview") {
                AlbumPreviewRow(
                    plan = plan,
                    imageLoader = previewImageLoader,
                    onOpenPage = { page -> onOpenReader(plan.chapterId, page) },
                )
            }
        }
    }

    fun LazyListScope.chapterItems(detail: AlbumDetail) {
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
        ChapterGroups.of(detail.chapters, Chapter::id).forEach { group ->
            item(key = "chapter-group-" + group.id) {
                ChapterGroupCard(
                    group = group.items,
                    startPosition = group.startPosition,
                    expanded = group.id in uiState.expandedGroups,
                    resumeId = resumeId,
                    readPhotoIds = uiState.readPhotoIds,
                    onToggle = {
                        selectionHaptic()
                        stateHolder.toggleGroup(group.id)
                    },
                    onRead = { photoId -> onOpenReader(photoId, null) },
                    onDownload = { chapter, title ->
                        downloadChapter(chapter, detail.id, title)
                    },
                )
            }
        }
    }

    fun LazyListScope.infoItems(detail: AlbumDetail) {
        if (detail.description.isNotBlank()) {
            item(key = "description") {
                DescriptionSection(
                    text = detail.description,
                    expanded = uiState.descriptionExpanded,
                    onToggle = { stateHolder.setDescriptionExpanded(!uiState.descriptionExpanded) },
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
                    contentPadding = PaddingValues(horizontal = SectionHorizontalPadding),
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
    }

    fun LazyListScope.tailItems(detail: AlbumDetail) {
        if (detail.related.isNotEmpty()) {
            item(key = "related-title") { SmallTitle(text = "更多相关") }
            item(key = "related") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = SectionHorizontalPadding),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(detail.related, key = { it.id }) { related ->
                        RelatedAlbumCard(related = related, onClick = { onOpenAlbum(related.id) })
                    }
                }
            }
        }
        item(key = "comments-title") {
            val total = (uiState.commentsState as? CommentsUiState.Loaded)?.feed?.total
            SmallTitle(text = if (total != null) "评论（$total）" else "评论")
        }
        item(key = "comment-composer") {
            CommentComposer(
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
                Box(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    InfiniteProgressIndicator()
                }
            }
            is CommentsUiState.Failed -> item(key = "comments-failed") {
                if (cs.sessionExpired) {
                    LoginExpiredScreen(
                        onOpenLogin = onOpenLogin,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                } else {
                    ErrorScreen(
                        message = cs.message,
                        onRetry = { stateHolder.reloadComments() },
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                }
            }
            is CommentsUiState.Loaded -> {
                item(key = "comments-card") {
                    // 卡内 CommentRow 自带 12dp：外缘保持 16dp，评论正文与标题同在 28dp
                    Card(cornerRadius = skin.shapes.cardCorner, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            if (cs.feed.items.isEmpty()) {
                                Text(
                                    text = "暂无评论，来抢沙发",
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = SectionHorizontalPadding, vertical = 16.dp),
                                )
                            }
                            for (comment in cs.feed.items) {
                                CommentRow(
                                    item = comment,
                                    onReply = {
                                        if (user == null) onOpenLogin() else stateHolder.setReplyTarget(comment)
                                    },
                                )
                            }
                            if (cs.feed.items.isNotEmpty()) {
                                LoadMoreFooter(
                                    feed = cs.feed,
                                    onLoadMore = stateHolder::loadMoreComments,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                        }
                    }
                }
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
                                            onClick = { downloadAll(loadedDetail.id, loadedDetail.chapters) },
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
                    isRefreshing = uiState.pullRefreshing,
                    onRefresh = stateHolder::refresh,
                    pullToRefreshState = refreshState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = innerPadding,
                ) {
                    // 大屏两栏：左封面+信息、右预览/章节/评论，避免单栏拉满整屏；
                    // 手机断点以下或详情未就绪时仍走原来的单栏列表
                    val wideDetail = loadedDetail?.takeIf { LocalWideLayout.current.twoPaneDetail }
                    if (wideDetail != null) {
                        Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                            TrackedLazyColumn(
                                state = listState,
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.weight(0.38f).fillMaxHeight(),
                            ) {
                                headerItem()
                                infoItems(wideDetail)
                            }
                            TrackedLazyColumn(
                                state = rightListState,
                                contentPadding = PaddingValues(0.dp),
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
                        contentPadding = innerPadding,
                        // 未分栏的大屏仍把单栏内容收进可读宽度，评论不会一行横跨整屏
                        modifier = Modifier.fillMaxSize().readableWidth(),
                    ) {
                        // 首项下移让出顶栏高度，滚动时内容从玻璃下方穿过
                        headerItem()

                        when (val s = uiState.albumState) {
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
                                    // 失效不给重试：重试必然再吃一次 401，只能去登录
                                    if (s.sessionExpired) {
                                        LoginExpiredScreen(
                                            onOpenLogin = onOpenLogin,
                                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        )
                                    } else {
                                        ErrorScreen(
                                            message = s.message,
                                            onRetry = { stateHolder.refresh() },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                        )
                                    }
                                }
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
                    // 收藏夹选择面板：未收藏=加入，已收藏=移动，底部提供取消收藏
                    OverlayBottomSheet(
                        show = uiState.showFolderSheet,
                        title = if (uiState.favorited) "移动收藏夹" else "选择收藏夹",
                        cornerRadius = skin.shapes.sheetCorner,
                        onDismissRequest = stateHolder::closeFolderSheet,
                    ) {
                        Card(cornerRadius = skin.shapes.cardCorner, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                            val allLabel = uiState.folders["0"] ?: "全部收藏"
                            BasicComponent(
                                title = allLabel,
                                summary = "默认收藏夹",
                                onClick = { stateHolder.pickFolder("0") },
                            )
                            uiState.folders.entries
                                .filter { it.key != "0" }
                                .forEach { (id, name) ->
                                    BasicComponent(
                                        title = name,
                                        onClick = { stateHolder.pickFolder(id) },
                                    )
                                }
                        }
                        if (uiState.favorited) {
                            // 水平内边距由抽屉自带，这里只补垂直间距
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                TextButton(
                                    text = "取消收藏",
                                    onClick = stateHolder::cancelFavorite,
                                    enabled = !uiState.favoriteBusy,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                    // 回复面板：标题带被回复作者，发送后刷新评论列表以显示新回复
                    CommentReplySheet(
                        show = uiState.replyTarget != null,
                        targetAuthor = uiState.replyTarget?.author,
                        busy = uiState.replyBusy,
                        onDismiss = { stateHolder.setReplyTarget(null) },
                        onSend = stateHolder::sendReply,
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
    val skin = LocalSkin.current

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            if (!coverUrl.isNullOrBlank()) {
                // 头图是「列表卡片 → 详情页」形变飞行的落点：与列表卡片共用皮肤 cardCorner，飞行两端圆角一致
                Card(
                    cornerRadius = skin.shapes.cardCorner,
                    modifier = Modifier
                        .size(width = 144.dp, height = 192.dp)
                        .albumHeroHeader(albumId, coverUrl, skin.shapes.cardCorner.value)
                ) {
                    // 与列表卡片、飞行覆盖层共用同一个 Coil 内存缓存条目（显式 key + 固定 144x192dp 解码尺寸），
                    // 命中即首帧出图；crossfade 会在命中后仍淡入一次，正是“封面闪一下”的来源之一。
                    val heroHost = LocalAlbumHeroHost.current
                    AlbumCoverImage(
                        url = coverUrl,
                        contentDescription = title,
                        modifier = Modifier.fillMaxSize(),
                        crossfade = false,
                        // 首帧出图前先铺骨架底衬，转场落点不会先露出卡片底色
                        skeleton = true,
                        // 首帧画出（或确认失败）后告诉飞行层：覆盖层要等这一步才收场
                        onState = { state ->
                            if (state is AsyncImagePainter.State.Success || state is AsyncImagePainter.State.Error) {
                                heroHost?.markHeaderReady(albumId)
                            }
                        },
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
            Card(cornerRadius = skin.shapes.cardCorner, modifier = Modifier.fillMaxWidth()) {
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
    val skin = LocalSkin.current
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
                modifier = Modifier.padding(end = SectionHorizontalPadding),
            ) {
                Icon(
                    imageVector = MiuixIcons.Copy,
                    contentDescription = "复制简介",
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Card(cornerRadius = skin.shapes.cardCorner, modifier = Modifier.fillMaxWidth().padding(horizontal = SectionHorizontalPadding)) {
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
    val skin = LocalSkin.current
    Card(cornerRadius = skin.shapes.cardCorner, modifier = Modifier.fillMaxWidth().padding(horizontal = SectionHorizontalPadding, vertical = 6.dp)) {
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
    val skin = LocalSkin.current
    Card(cornerRadius = skin.shapes.cardCorner, modifier = Modifier.fillMaxWidth().padding(horizontal = SectionHorizontalPadding, vertical = 6.dp)) {
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
        subtitle = related.authors.joinToString(" / ").ifBlank { null },
        category = related.category,
        modifier = Modifier.width(WorkCardTokens.CompactWidth),
        badge = { WorkAlbumCodeBadge(related.id) },
        footer = { WorkTagLine(related.tags) },
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
    val skin = LocalSkin.current
    Card(cornerRadius = skin.shapes.cardCorner, modifier = Modifier.fillMaxWidth().padding(horizontal = SectionHorizontalPadding, vertical = 6.dp)) {
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
                    label = "写下你的评论（最多 $COMMENT_REPLY_MAX_LENGTH 字）",
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
                        text = "${text.length}/$COMMENT_REPLY_MAX_LENGTH",
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

/** 详情页预览条：首章前几页缩略图，点第 i 张直达阅读器第 i 页。 */
@Composable
private fun AlbumPreviewRow(
    plan: AlbumPreviewPlan,
    imageLoader: ImageLoader,
    onOpenPage: (page: Int) -> Unit,
) {
    // 加载中与失败都露出灰底占位，不出现白块或高度跳动
    val placeholderColor = MiuixTheme.colorScheme.surfaceContainerHigh
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        contentPadding = PaddingValues(horizontal = SectionHorizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(plan.pages, key = { it.index }) { page ->
            AsyncImage(
                model = page,
                imageLoader = imageLoader,
                contentDescription = "预览第 ${page.index + 1} 页",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 112.dp, height = 150.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(placeholderColor)
                    .clickable { onOpenPage(page.index + 1) },
            )
        }
    }
}