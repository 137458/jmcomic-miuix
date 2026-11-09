// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.page

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.CouiDialogActionStyle
import com.itos.coui.dialog.CouiDialogDefaults
import com.itos.coui.dialog.Dialog
import com.itos.coui.effect.couiButtonFeedback
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.layout.Divider
import com.itos.coui.layout.Group
import com.itos.coui.layout.Item
import com.itos.coui.scroll.couiVerticalScroll
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.shape.CouiShape
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.text.rememberCouiTextStyle
import com.itos.coui.theme.CouiTheme as CouiKitTheme
import com.itos.coui.topappbar.CouiTopAppBarDefaults
import com.itos.coui.topappbar.LargeTopAppBar
import com.jm3.app.miuix.data.download.DownloadManager
import com.jm3.app.miuix.ui.AlbumCoverImage
import com.jm3.app.miuix.ui.AlbumDetailStateHolder
import com.jm3.app.miuix.ui.AlbumUiState
import com.jm3.app.miuix.ui.COMMENT_REPLY_MAX_LENGTH
import com.jm3.app.miuix.ui.CommentsUiState
import com.jm3.app.miuix.ui.GlassSnackbarHost
import com.jm3.app.miuix.ui.LocalAlbumDetailRootCoordinates
import com.jm3.app.miuix.ui.LocalAlbumHeroHost
import com.jm3.app.miuix.ui.SessionCache
import com.jm3.app.miuix.ui.albumHeroHeader
import com.jm3.app.miuix.ui.coui.component.CouiAppScaffold
import com.jm3.app.miuix.ui.coui.icon.CouiIcons
import com.jm3.app.miuix.ui.coui.theme.CouiTheme
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
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.nav.core.LocalNavTransitionScope
import top.yukonga.miuix.kmp.nav.transition.NavRole

/** 封面宽：3:4 竖版封面在 108dp 宽时正好占住头部左侧，右侧留出标题与指标。 */
private val COUI_HERO_COVER_WIDTH = 108.dp
/** 封面高：与宽度组成 3:4，和列表页封面比例保持一致。 */
private val COUI_HERO_COVER_HEIGHT = 144.dp
/** 封面圆角：头部卡片内部再收一层圆角，避免封面与卡边贴合显得生硬。 */
private val COUI_HERO_COVER_RADIUS = 16.dp
/** 头部卡片是自绘容器，圆角跟随 COUI 页面形状，保证与 Group 卡片同一视觉家族。 */
private val COUI_HERO_CORNER_DP = 16f
/** 简介折叠阈值：短简介没有折叠的必要，只有超过这个长度才给出展开入口。 */
private const val COUI_DESCRIPTION_COLLAPSE_THRESHOLD = 80
/** 折叠态默认展示的回复条数；超出部分由「展开 N 条回复」控制。 */
private const val COUI_COMMENT_VISIBLE_REPLIES = 2
/** 回复缩进的最大层级：更深的回复不再追加缩进，否则正文宽度会被挤没。 */
private const val COUI_COMMENT_MAX_INDENT_DEPTH = 4
/** 单条评论正文最多展示的行数；超长评论折叠成省略号，避免整条铺满一屏。 */
private const val COUI_COMMENT_BODY_MAX_LINES = 12
/** 徽章图的无障碍描述：徽章本身无文字，读屏需要知道这里是一枚徽章。 */
private const val COUI_COMMENT_BADGE_DESCRIPTION = "徽章"

/**
 * COUI 作品详情页：头部信息卡、阅读操作、简介、作品信息、预览、章节、相关作品与评论区。
 *
 * 业务编排全部复用 [AlbumDetailStateHolder]（加载、收藏/取消收藏、喜欢、追踪、评论分页与发送、
 * 章节分组展开态、续读记录），本文件只负责 COUI 原厂视觉：折叠大标题顶栏、Group/Item 分组行、
 * 胶囊动作、CouiTextKind 字阶与 COUI 回弹滚动。
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
        CouiAlbumDetailScreenBody(
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
private fun CouiAlbumDetailScreenBody(
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
    // 预览图与阅读页共用同一套图片加载器（含同一份缓存），避免再建第二个 loader
    val previewImageLoader = rememberReaderImageLoader(gateway, downloadFiles)

    val stateHolder = remember(albumId) {
        AlbumDetailStateHolder(
            albumId = albumId,
            gateway = gateway.asAlbumDetailPort(),
            authCall = authCall,
            history = history,
            scope = scope,
            showMessage = { message -> scope.launch { snackbarHostState.showSnackbar(message) } },
            onOpenLogin = onOpenLogin,
            engagement = sessionCache.engagement,
        )
    }
    val uiState by stateHolder.uiState.collectAsState()

    /** 批量下载：只有全部章节都已在队列/已下载时才提示「无新增」，否则报告本次实际入队数量。 */
    fun downloadAll(targetAlbumId: String, chapters: List<Chapter>) {
        if (chapters.isEmpty()) return
        var accepted = 0
        chapters.forEachIndexed { index, chapter ->
            val title = ChapterLabels.chapterTitle(chapter.title, index)
            if (downloadManager.enqueueChapter(chapter.id, targetAlbumId, title)) accepted++
        }
        scope.launch {
            snackbarHostState.showSnackbar(
                if (accepted > 0) "已加入下载队列（$accepted 个章节）" else "这些章节已在下载队列或已下载",
            )
        }
    }

    fun downloadChapter(chapter: Chapter, targetAlbumId: String, title: String) {
        val accepted = downloadManager.enqueueChapter(chapter.id, targetAlbumId, title)
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
            if (link != null) append("\n").append(link)
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
            // 起飞条件只能是「明确的退出信号」：返回手势进行中，或本页已被标记为正在出栈。
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

    val scrollState = rememberScrollState()
    // 正文可滚动范围决定了顶栏能否折叠：短页保持展开，避免标题被无意义的位移压扁
    val appBarBehavior = CouiTopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        canScroll = { scrollState.maxValue > 0 },
    )
    val density = LocalDensity.current
    // 归一化转场坐标系：NavDisplay 进场矩阵会平移整页，根坐标必须由本页自己上报
    var rootCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    CompositionLocalProvider(LocalAlbumDetailRootCoordinates provides rootCoordinates) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { rootCoordinates = it },
        ) {
            CouiAppScaffold(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(appBarBehavior.nestedScrollConnection),
                topBar = {
                    LargeTopAppBar(
                        title = {
                            // 顶栏槽位已下发 HomeTitle/Toolbar 字阶，这里必须用会继承 LocalTextStyle 的 BasicText
                            BasicText(
                                text = currentTitle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        navigationIcon = {
                            CouiTopBarIconButton(
                                icon = CouiIcons.Back,
                                description = "返回",
                                onClick = onBack,
                            )
                        },
                        actions = {
                            if (loadedDetail != null) {
                                CouiTopBarIconButton(
                                    icon = CouiIcons.Share,
                                    description = "分享",
                                    onClick = { share(loadedDetail) },
                                )
                                if (loadedDetail.chapters.size > 1) {
                                    CouiTopBarIconButton(
                                        icon = CouiIcons.Download,
                                        description = "下载全部",
                                        onClick = { downloadAll(loadedDetail.id, loadedDetail.chapters) },
                                    )
                                }
                            }
                        },
                        scrollBehavior = appBarBehavior,
                    )
                },
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .readableWidth()
                        .couiVerticalScroll(scrollState)
                        .padding(appBarBehavior.state.contentPadding(innerPadding, density))
                        .consumeWindowInsets(innerPadding)
                        .padding(bottom = 24.dp),
                ) {
                    CouiAlbumHeroCard(
                        albumId = albumId,
                        detail = loadedDetail,
                        fallbackTitle = effectiveTitle,
                        fallbackCoverUrl = effectiveCoverUrl,
                        onOpenSearch = onOpenSearch,
                    )
                    when (val albumState = uiState.albumState) {
                        is AlbumUiState.Loading -> CouiLoadingGroup()

                        is AlbumUiState.Failed -> CouiNoticeGroup(
                            message = if (albumState.sessionExpired) {
                                SessionOutcome.Expired.message
                            } else {
                                albumState.message
                            },
                            actionText = if (albumState.sessionExpired) "去登录" else "重试",
                            onAction = if (albumState.sessionExpired) onOpenLogin else stateHolder::refresh,
                        )

                        is AlbumUiState.Loaded -> {
                            val detail = albumState.detail
                            // 匿名函数而不是块内 lambda：可空函数类型下不需要依赖目标类型推断
                            val downloadAllAction: (() -> Unit)? = if (detail.chapters.isEmpty()) {
                                null
                            } else {
                                fun() { downloadAll(detail.id, detail.chapters) }
                            }
                            CouiReadActionsGroup(
                                detail = detail,
                                resumeId = resumeId,
                                loggedIn = user != null,
                                favorited = uiState.favorited,
                                liked = sessionCache.engagement.likedOf(albumId) ?: uiState.liked,
                                tracking = uiState.tracking,
                                trackingBusy = uiState.trackingBusy,
                                onRead = onOpenReader,
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
                                onDownloadAll = downloadAllAction,
                            )
                            if (detail.description.isNotBlank()) {
                                CouiDescriptionGroup(
                                    text = detail.description,
                                    expanded = uiState.descriptionExpanded,
                                    onToggle = { stateHolder.setDescriptionExpanded(!uiState.descriptionExpanded) },
                                    onCopy = { copyText("简介", detail.description) },
                                )
                            }
                            CouiChaptersGroup(
                                detail = detail,
                                expandedGroups = uiState.expandedGroups,
                                resumeId = resumeId,
                                readPhotoIds = uiState.readPhotoIds,
                                onToggleGroup = stateHolder::toggleGroup,
                                onRead = { photoId -> onOpenReader(photoId, null) },
                                onDownload = { chapter, title -> downloadChapter(chapter, detail.id, title) },
                            )
                            CouiInfoGroup(
                                detail = detail,
                                displayId = detail.id.trim().takeIf { it.isNotBlank() && it != "-1" } ?: albumId,
                                onOpenSearch = onOpenSearch,
                                onCopy = { label, value -> copyText(label, value) },
                            )
                            previewPlan?.let { plan ->
                                CouiPreviewGroup(
                                    plan = plan,
                                    imageLoader = previewImageLoader,
                                    onOpenPage = { page -> onOpenReader(plan.chapterId, page) },
                                )
                            }
                            if (detail.related.isNotEmpty()) {
                                CouiRelatedGroup(related = detail.related, onOpenAlbum = onOpenAlbum)
                            }
                            CouiCommentsGroup(
                                loggedIn = user != null,
                                commentText = uiState.commentText,
                                commentBusy = uiState.commentBusy,
                                commentsState = uiState.commentsState,
                                fallbackCount = detail.commentCount,
                                onTextChange = stateHolder::setCommentText,
                                onLogin = onOpenLogin,
                                onSend = stateHolder::postComment,
                                onReply = { comment ->
                                    if (user == null) onOpenLogin() else stateHolder.setReplyTarget(comment)
                                },
                                onReload = stateHolder::reloadComments,
                                onLoadMore = stateHolder::loadMoreComments,
                            )
                        }
                    }
                }
            }

            // 页面内的 Snackbar 需要真实底栏上方留白，GlassSnackbarHost 内部会补这段差额
            GlassSnackbarHost(
                state = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
                backdrop = null,
            )

            // 收藏夹选择面板：未收藏=加入，已收藏=移动，并保留取消收藏
            CouiFolderDialog(
                visible = uiState.showFolderSheet,
                favorited = uiState.favorited,
                folders = uiState.folders,
                onDismiss = stateHolder::closeFolderSheet,
                onPick = stateHolder::pickFolder,
                onCancelFavorite = stateHolder::cancelFavorite,
            )
            // 回复面板：标题带被回复作者，发送后由状态持有者刷新评论列表
            CouiReplyDialog(
                visible = uiState.replyTarget != null,
                targetAuthor = uiState.replyTarget?.author,
                busy = uiState.replyBusy,
                onDismiss = { stateHolder.setReplyTarget(null) },
                onSend = stateHolder::sendReply,
            )
        }
    }
}

/** 顶栏图标位：COUI 的 TopAppBarButton 需要 API 34 的工具栏背景，这里用最小可点的图标位代替。 */
@Composable
private fun CouiTopBarIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    val scheme = CouiKitTheme.colorScheme
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .couiPressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            modifier = Modifier.size(22.dp),
            tint = scheme.onSurface,
        )
    }
}

/**
 * 统一按压反馈：COUI 项目用的是原厂按压动效，不能挂 Material 涟漪，
 * 所以所有自绘可点区域都走同一条「按压 + 去涟漪点击」链。
 */
@Composable
private fun Modifier.couiPressable(
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val source = remember { MutableInteractionSource() }
    return this
        .couiButtonFeedback(
            dark = CouiKitTheme.isDark,
            interactionSource = source,
            enabled = enabled,
            list = false,
        )
        .clickable(
            interactionSource = source,
            indication = null,
            enabled = enabled,
            role = Role.Button,
            onClick = onClick,
        )
}

/** 胶囊动作：收藏/喜欢/追更/下载与评论发表共用，选中态用强调色实底。 */
@Composable
private fun CouiCapsuleAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Boolean = false,
) {
    val scheme = CouiKitTheme.colorScheme
    val background = when {
        !enabled -> scheme.onSurface.copy(alpha = 0.06f)
        accent -> scheme.accent
        else -> scheme.onSurface.copy(alpha = 0.08f)
    }
    val contentColor = when {
        !enabled -> scheme.onSurface.copy(alpha = 0.38f)
        accent -> scheme.onAccent
        else -> scheme.onSurface
    }
    Box(
        modifier = modifier
            .heightIn(min = 32.dp)
            .clip(CouiCapsuleShape)
            .background(background)
            .couiPressable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            kind = CouiTextKind.PreferenceValue,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 头部信息卡：3:4 封面 + 标题 + 作者胶囊 + 车号 + 热度指标 + 标签胶囊。
 *
 * 这里是页面唯一的自绘卡片：封面参与转场飞行，需要与 Group 卡片同形状但独立持有尺寸与裁剪，
 * 所以不用 Group 包，改为手写「同形状 + 同底色」的容器。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CouiAlbumHeroCard(
    albumId: String,
    detail: AlbumDetail?,
    fallbackTitle: String?,
    fallbackCoverUrl: String?,
    onOpenSearch: (query: String, scope: SearchScope) -> Unit,
) {
    val scheme = CouiKitTheme.colorScheme
    val heroHost = LocalAlbumHeroHost.current
    val title = detail?.title?.trim()?.takeIf { it.isNotBlank() } ?: fallbackTitle?.trim().orEmpty()
    val coverUrl = detail?.coverUrl?.trim()?.takeIf { it.isNotBlank() } ?: fallbackCoverUrl?.trim()
    val displayId = detail?.id?.trim()?.takeIf { it.isNotBlank() && it != "-1" } ?: albumId
    val authors = detail?.authors.orEmpty().map { it.trim() }.filter { it.isNotBlank() }
    val tags = detail?.tags.orEmpty().map { it.trim() }.filter { it.isNotBlank() }
    val metrics = buildList {
        detail?.likes?.takeIf { it.isNotBlank() && it != "0" }?.let { add("喜欢 $it") }
        detail?.views?.takeIf { it.isNotBlank() && it != "0" }?.let { add("浏览 $it") }
        detail?.commentCount?.takeIf { it > 0 }?.let { add("评论 $it") }
        detail?.pageCount?.takeIf { it > 0 }?.let { add("页数 $it") }
    }.joinToString(" · ")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CouiPageDefaults.horizontalMargin)
            .padding(top = CouiPageDefaults.categoryTopSpacing, bottom = CouiPageDefaults.groupSpacing)
            .clip(CouiPageDefaults.shape)
            .background(scheme.card),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Box(
                modifier = Modifier
                    .size(width = COUI_HERO_COVER_WIDTH, height = COUI_HERO_COVER_HEIGHT)
                    .clip(RoundedCornerShape(COUI_HERO_COVER_RADIUS))
                    .background(scheme.container)
                    .albumHeroHeader(albumId, coverUrl, COUI_HERO_CORNER_DP),
            ) {
                AlbumCoverImage(
                    url = coverUrl,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    crossfade = false,
                    skeleton = true,
                    onState = { state ->
                        // 头图首帧落地即告知转场层：此时共享元素的位置与尺寸才是可信的
                        if (state is AsyncImagePainter.State.Success || state is AsyncImagePainter.State.Error) {
                            heroHost?.markHeaderReady(albumId)
                        }
                    },
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title.ifBlank { "作品详情" },
                    kind = CouiTextKind.SectionTitle,
                    color = scheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Start,
                )
                if (authors.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .clip(CouiCapsuleShape)
                            .background(scheme.accent.copy(alpha = 0.14f))
                            .couiPressable { onOpenSearch(authors.first(), SearchScope.AUTHOR) }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = authors.joinToString("、"),
                            kind = CouiTextKind.Description,
                            color = scheme.accent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Start,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "禁漫车 #$displayId",
                    kind = CouiTextKind.Description,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Start,
                )
                if (metrics.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = metrics,
                        kind = CouiTextKind.Description,
                        color = scheme.onSurfaceVariant,
                        textAlign = TextAlign.Start,
                    )
                }
            }
        }
        if (tags.isNotEmpty()) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                tags.forEach { tag ->
                    Box(
                        modifier = Modifier
                            .clip(CouiCapsuleShape)
                            .background(scheme.onSurface.copy(alpha = 0.06f))
                            .couiPressable { onOpenSearch(tag, SearchScope.TAG) }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = tag,
                            kind = CouiTextKind.Description,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Start,
                        )
                    }
                }
            }
        }
    }
}

/** 阅读操作：主操作行（继续阅读/开始阅读） + 一行胶囊动作。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CouiReadActionsGroup(
    detail: AlbumDetail,
    resumeId: String?,
    loggedIn: Boolean,
    favorited: Boolean,
    liked: Boolean,
    tracking: Boolean?,
    trackingBusy: Boolean,
    onRead: (photoId: String, startPage: Int?) -> Unit,
    onFavorite: () -> Unit,
    onLike: () -> Unit,
    onTrack: () -> Unit,
    onDownloadAll: (() -> Unit)?,
) {
    val scheme = CouiKitTheme.colorScheme
    val startPhotoId = resumeId ?: detail.chapters.firstOrNull()?.id
    Group {
        Item(
            title = {
                Text(
                    text = if (resumeId != null) "继续阅读" else "开始阅读",
                    kind = CouiTextKind.PreferenceTitle,
                    color = if (startPhotoId != null) scheme.accent else scheme.onSurfaceVariant,
                    maxLines = 1,
                    textAlign = TextAlign.Start,
                )
            },
            summary = {
                Text(
                    text = when {
                        startPhotoId == null -> "暂无章节"
                        resumeId != null -> "接着上次读到的一话"
                        else -> "从第 1 话开始"
                    },
                    kind = CouiTextKind.Description,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    textAlign = TextAlign.Start,
                )
            },
            trailing = {
                Icon(
                    imageVector = CouiIcons.Forward,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = scheme.onSurfaceVariant,
                )
            },
            enabled = startPhotoId != null,
            onClick = if (startPhotoId != null) fun() { onRead(startPhotoId, null) } else null,
        )
        Divider()
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CouiCapsuleAction(
                text = if (favorited) "已收藏" else "收藏",
                accent = favorited,
                onClick = onFavorite,
            )
            CouiCapsuleAction(
                text = if (liked) "已喜欢" else "喜欢",
                accent = liked,
                onClick = onLike,
            )
            if (loggedIn && tracking != null) {
                CouiCapsuleAction(
                    text = when {
                        trackingBusy -> "处理中…"
                        tracking -> "追更中"
                        else -> "追更"
                    },
                    accent = tracking,
                    enabled = !trackingBusy,
                    onClick = onTrack,
                )
            }
            onDownloadAll?.let { download ->
                CouiCapsuleAction(text = "下载", onClick = download)
            }
        }
    }
}

/** 简介：默认折叠 4 行，超长才给展开入口；复制按钮放在分组标题行，避免正文被按钮切碎。 */
@Composable
private fun CouiDescriptionGroup(
    text: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    onCopy: () -> Unit,
) {
    val scheme = CouiKitTheme.colorScheme
    Group(
        title = "简介",
        headerActions = {
            Text(
                text = "复制",
                kind = CouiTextKind.Category,
                color = scheme.accent,
                maxLines = 1,
                modifier = Modifier
                    .couiPressable(onClick = onCopy)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
            if (text.length > COUI_DESCRIPTION_COLLAPSE_THRESHOLD) {
                Text(
                    text = if (expanded) "收起" else "显示全部",
                    kind = CouiTextKind.Category,
                    color = scheme.accent,
                    maxLines = 1,
                    modifier = Modifier
                        .couiPressable(onClick = onToggle)
                        .padding(start = 4.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
                )
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = text,
                kind = CouiTextKind.Body,
                color = scheme.onSurface,
                maxLines = if (expanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start,
            )
        }
    }
}

/** 作品信息：作者/作品/登场人物可跳搜索，车号可复制，热度指标只读展示。 */
@Composable
private fun CouiInfoGroup(
    detail: AlbumDetail,
    displayId: String,
    onOpenSearch: (query: String, scope: SearchScope) -> Unit,
    onCopy: (label: String, value: String) -> Unit,
) {
    val scheme = CouiKitTheme.colorScheme
    val authors = detail.authors.map { it.trim() }.filter { it.isNotBlank() }
    val works = detail.works.map { it.trim() }.filter { it.isNotBlank() }
    val actors = detail.actors.map { it.trim() }.filter { it.isNotBlank() }
    Group(title = "作品信息") {
        if (authors.isNotEmpty()) {
            CouiInfoRow(
                title = "作者",
                value = authors.joinToString("、"),
                onClick = { onOpenSearch(authors.first(), SearchScope.AUTHOR) },
            )
            Divider()
        }
        if (works.isNotEmpty()) {
            CouiInfoRow(
                title = "作品",
                value = works.first(),
                onClick = { onOpenSearch(works.first(), SearchScope.WORK) },
            )
            Divider()
        }
        if (actors.isNotEmpty()) {
            CouiInfoRow(
                title = "登场人物",
                value = actors.first(),
                onClick = { onOpenSearch(actors.first(), SearchScope.ACTOR) },
            )
            Divider()
        }
        Item(
            title = { Text(text = "禁漫车号", kind = CouiTextKind.PreferenceTitle, textAlign = TextAlign.Start) },
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "#$displayId",
                        kind = CouiTextKind.PreferenceValue,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = CouiIcons.Copy,
                        contentDescription = "复制车号",
                        modifier = Modifier.size(18.dp),
                        tint = scheme.accent,
                    )
                }
            },
            onClick = { onCopy("禁漫车号", displayId) },
        )
        if (detail.pageCount > 0) {
            Divider()
            CouiInfoRow(title = "页数", value = "${detail.pageCount} 页")
        }
        detail.likes?.takeIf { it.isNotBlank() && it != "0" }?.let { likes ->
            Divider()
            CouiInfoRow(title = "喜欢", value = likes)
        }
        detail.views?.takeIf { it.isNotBlank() && it != "0" }?.let { views ->
            Divider()
            CouiInfoRow(title = "浏览", value = views)
        }
        if (detail.commentCount > 0) {
            Divider()
            CouiInfoRow(title = "评论数", value = "${detail.commentCount} 条")
        }
    }
}

/** 只读信息行；onClick 非空时右侧补一枚方向角，提示可跳转。 */
@Composable
private fun CouiInfoRow(
    title: String,
    value: String,
    onClick: (() -> Unit)? = null,
) {
    val scheme = CouiKitTheme.colorScheme
    Item(
        title = { Text(text = title, kind = CouiTextKind.PreferenceTitle, textAlign = TextAlign.Start) },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = value,
                    kind = CouiTextKind.PreferenceValue,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                )
                if (onClick != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = CouiIcons.Forward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = scheme.onSurfaceVariant,
                    )
                }
            }
        },
        onClick = onClick,
    )
}

/** 预览：首章前几页缩略图，点第 i 张直达阅读器第 i 页。 */
@Composable
private fun CouiPreviewGroup(
    plan: AlbumPreviewPlan,
    imageLoader: ImageLoader,
    onOpenPage: (page: Int) -> Unit,
) {
    val scheme = CouiKitTheme.colorScheme
    Group(title = "预览") {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
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
                        .clip(RoundedCornerShape(12.dp))
                        .background(scheme.container)
                        .couiPressable { onOpenPage(page.index + 1) },
                )
            }
        }
    }
}

/**
 * 章节：每十话一组，组行负责展开收起，展开后逐话给出阅读与单话下载入口。
 * 分组与展开态都来自 [ChapterGroups] 与状态持有者，页面不新增分组逻辑。
 */
@Composable
private fun CouiChaptersGroup(
    detail: AlbumDetail,
    expandedGroups: Set<String>,
    resumeId: String?,
    readPhotoIds: Set<String>,
    onToggleGroup: (groupId: String) -> Unit,
    onRead: (photoId: String) -> Unit,
    onDownload: (chapter: Chapter, title: String) -> Unit,
) {
    val scheme = CouiKitTheme.colorScheme
    val groups = remember(detail.chapters) { ChapterGroups.of(detail.chapters, Chapter::id) }
    // 全部展开的判据只看「有折叠入口的组」，单话组本来就没有折叠态，不该影响文案
    val collapsibleGroups = groups.filter { it.items.size > 1 }
    val allExpanded = collapsibleGroups.isNotEmpty() && collapsibleGroups.all { it.id in expandedGroups }
    Group(
        title = if (detail.chapters.isEmpty()) "章节" else "章节（${detail.chapters.size}）",
        headerActions = {
            if (collapsibleGroups.size > 1) {
                Text(
                    text = if (allExpanded) "全部收起" else "全部展开",
                    kind = CouiTextKind.Category,
                    color = scheme.accent,
                    maxLines = 1,
                    modifier = Modifier
                        .couiPressable {
                            // 逐个复用状态持有者的 toggleGroup，不在页面里另建批量写入路径
                            collapsibleGroups.forEach { group ->
                                val expanded = group.id in expandedGroups
                                if (expanded != allExpanded) onToggleGroup(group.id)
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        },
    ) {
        if (groups.isEmpty()) {
            Item(
                title = {
                    Text(
                        text = "暂无章节",
                        kind = CouiTextKind.PreferenceTitle,
                        color = scheme.onSurfaceVariant,
                        textAlign = TextAlign.Start,
                    )
                },
            )
        }
        groups.forEachIndexed { groupIndex, group ->
            if (groupIndex > 0) Divider()
            val showToggle = group.items.size > 1
            val expanded = group.id in expandedGroups
            val groupTitle = ChapterLabels.groupTitle(group.items.map { it.title }, group.startPosition)
            Item(
                title = {
                    Text(
                        text = groupTitle,
                        kind = CouiTextKind.PreferenceTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Start,
                    )
                },
                summary = {
                    if (showToggle) {
                        Text(
                            text = "${group.items.size} 话 · " + if (expanded) "点击收起" else "点击展开",
                            kind = CouiTextKind.Description,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            textAlign = TextAlign.Start,
                        )
                    }
                },
                trailing = {
                    if (showToggle) {
                        Text(
                            text = if (expanded) "收起" else "展开",
                            kind = CouiTextKind.PreferenceValue,
                            color = scheme.accent,
                            maxLines = 1,
                        )
                    }
                },
                onClick = if (showToggle) fun() { onToggleGroup(group.id) } else null,
            )
            if (!showToggle || expanded) {
                group.items.forEachIndexed { indexInGroup, chapter ->
                    val position = group.startPosition + indexInGroup
                    val chapterTitle = ChapterLabels.chapterTitle(chapter.title, position)
                    val marker = when {
                        chapter.id == resumeId -> "上次读到"
                        chapter.id in readPhotoIds -> "已读"
                        else -> null
                    }
                    Divider(startIndent = 32.dp, endIndent = 16.dp)
                    Item(
                        contentPadding = PaddingValues(start = 32.dp, top = 10.dp, end = 16.dp, bottom = 10.dp),
                        title = {
                            Text(
                                text = chapterTitle,
                                kind = CouiTextKind.Body,
                                color = scheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Start,
                            )
                        },
                        summary = {
                            if (marker != null) {
                                Text(
                                    text = marker,
                                    kind = CouiTextKind.Description,
                                    color = if (marker == "上次读到") scheme.accent else scheme.onSurfaceVariant,
                                    maxLines = 1,
                                    textAlign = TextAlign.Start,
                                )
                            }
                        },
                        trailing = {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .couiPressable { onDownload(chapter, chapterTitle) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = CouiIcons.Download,
                                    contentDescription = "下载 $chapterTitle",
                                    modifier = Modifier.size(18.dp),
                                    tint = scheme.onSurfaceVariant,
                                )
                            }
                        },
                        onClick = { onRead(chapter.id) },
                    )
                }
            }
        }
    }
}

/** 相关作品：横向作品卡，容器仍是 Group，卡内只保留封面与两行文字。 */
@Composable
private fun CouiRelatedGroup(
    related: List<SearchResult>,
    onOpenAlbum: (albumId: String) -> Unit,
) {
    val scheme = CouiKitTheme.colorScheme
    Group(title = "更多相关") {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(related, key = { it.id }) { item ->
                val subtitle = item.authors.joinToString(" / ")
                Column(
                    modifier = Modifier
                        .width(104.dp)
                        .couiPressable { onOpenAlbum(item.id) },
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 104.dp, height = 138.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(scheme.container),
                    ) {
                        AsyncImage(
                            model = item.coverUrl,
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = item.title,
                        kind = CouiTextKind.PreferenceSummary,
                        color = scheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Start,
                    )
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            kind = CouiTextKind.Description,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Start,
                        )
                    }
                }
            }
        }
    }
}

/** 评论：输入区 + 列表 + 加载更多，三部分都在同一个 Group 卡片内，避免碎成多张卡。 */
@Composable
private fun CouiCommentsGroup(
    loggedIn: Boolean,
    commentText: String,
    commentBusy: Boolean,
    commentsState: CommentsUiState,
    fallbackCount: Int,
    onTextChange: (String) -> Unit,
    onLogin: () -> Unit,
    onSend: () -> Unit,
    onReply: (CommentItem) -> Unit,
    onReload: () -> Unit,
    onLoadMore: () -> Unit,
) {
    val scheme = CouiKitTheme.colorScheme
    val total = (commentsState as? CommentsUiState.Loaded)?.feed?.total
    val titleCount = total ?: fallbackCount.takeIf { it > 0 }
    Group(title = if (titleCount != null && titleCount > 0) "评论（$titleCount）" else "评论") {
        CouiCommentComposer(
            loggedIn = loggedIn,
            text = commentText,
            busy = commentBusy,
            onTextChange = onTextChange,
            onLogin = onLogin,
            onSend = onSend,
        )
        Divider()
        when (commentsState) {
            is CommentsUiState.Loading -> Item(
                title = {
                    Text(
                        text = "评论加载中…",
                        kind = CouiTextKind.PreferenceTitle,
                        color = scheme.onSurfaceVariant,
                        textAlign = TextAlign.Start,
                    )
                },
            )

            is CommentsUiState.Failed -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text(
                    text = if (commentsState.sessionExpired) {
                        SessionOutcome.Expired.message
                    } else {
                        commentsState.message
                    },
                    kind = CouiTextKind.Body,
                    color = scheme.onSurface,
                    textAlign = TextAlign.Start,
                )
                Spacer(modifier = Modifier.height(12.dp))
                CouiCapsuleAction(
                    text = if (commentsState.sessionExpired) "去登录" else "重试",
                    accent = true,
                    onClick = if (commentsState.sessionExpired) onLogin else onReload,
                )
            }

            is CommentsUiState.Loaded -> {
                val feed = commentsState.feed
                if (feed.items.isEmpty()) {
                    Item(
                        title = {
                            Text(
                                text = "暂无评论，来抢沙发",
                                kind = CouiTextKind.PreferenceTitle,
                                color = scheme.onSurfaceVariant,
                                textAlign = TextAlign.Start,
                            )
                        },
                    )
                } else {
                    feed.items.forEach { comment ->
                        Divider()
                        CouiCommentRow(item = comment, onReply = onReply)
                    }
                    Divider()
                    CouiLoadMoreRow(feed = feed, onLoadMore = onLoadMore)
                }
            }
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
    val scheme = CouiKitTheme.colorScheme
    if (!loggedIn) {
        Item(
            title = { Text(text = "登录后发表评论", kind = CouiTextKind.PreferenceTitle, textAlign = TextAlign.Start) },
            summary = {
                Text(
                    text = "登录账号即可参与讨论",
                    kind = CouiTextKind.Description,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Start,
                )
            },
            trailing = {
                Icon(
                    imageVector = CouiIcons.Forward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = scheme.onSurfaceVariant,
                )
            },
            onClick = onLogin,
        )
        return
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            textStyle = rememberCouiTextStyle(CouiTextKind.Body).copy(color = scheme.onSurface),
            cursorBrush = SolidColor(scheme.accent),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 84.dp)
                .clip(CouiShape(16.dp, 3f))
                .background(scheme.container)
                .padding(12.dp),
            decorationBox = { innerTextField ->
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (text.isEmpty()) {
                        Text(
                            text = "写下你的评论（最多 $COMMENT_REPLY_MAX_LENGTH 字）",
                            kind = CouiTextKind.Body,
                            color = scheme.onSurfaceVariant,
                            textAlign = TextAlign.Start,
                        )
                    }
                    innerTextField()
                }
            },
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "${text.length}/$COMMENT_REPLY_MAX_LENGTH",
                kind = CouiTextKind.Description,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Start,
            )
            CouiCapsuleAction(
                text = if (busy) "发表中…" else "发表",
                accent = true,
                enabled = !busy && text.isNotBlank(),
                onClick = onSend,
            )
        }
    }
}

/** 评论分页脚：把 PagedFeed 的三种尾部状态压成一行可点文字，替代旧皮肤的分页组件。 */
@Composable
private fun CouiLoadMoreRow(
    feed: PagedFeed<*>,
    onLoadMore: () -> Unit,
) {
    val scheme = CouiKitTheme.colorScheme
    val label = when {
        feed.loadingMore -> "加载中…"
        feed.loadMoreFailed -> "加载失败，点击重试"
        feed.canLoadMore -> "加载更多评论"
        else -> "没有更多评论了"
    }
    val actionable = !feed.loadingMore && (feed.canLoadMore || feed.loadMoreFailed)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            kind = CouiTextKind.PreferenceValue,
            color = if (actionable) scheme.accent else scheme.onSurfaceVariant,
            modifier = Modifier
                .clip(CouiCapsuleShape)
                .then(
                    if (actionable) {
                        Modifier.couiPressable { onLoadMore() }
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/** 评论单条：头像 + 等级/称号 + 徽章 + 分段正文，回复默认折叠 2 条；回复区带缩进线并可继续回复。 */
@Composable
private fun CouiCommentRow(
    item: CommentItem,
    depth: Int = 0,
    onReply: ((CommentItem) -> Unit)? = null,
) {
    val scheme = CouiKitTheme.colorScheme
    // 缩进只按层级累加到上限：层级仍然逐层传下去（回复线照画），但不再继续吃掉正文宽度
    val indentDepth = depth.coerceAtMost(COUI_COMMENT_MAX_INDENT_DEPTH)
    var repliesExpanded by remember(item.id) { mutableStateOf(item.replies.size <= COUI_COMMENT_VISIBLE_REPLIES) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp + (indentDepth * 12).dp, end = 16.dp, top = 10.dp, bottom = 10.dp)
            .drawBehind {
                if (depth > 0) {
                    // 回复线：贴在缩进区外侧的竖线，标识回复归属层级
                    drawRect(
                        color = scheme.outlineVariant,
                        topLeft = Offset(-6.dp.toPx(), 0f),
                        size = Size(2.dp.toPx(), size.height),
                    )
                }
            },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CouiCommentAvatar(
                url = item.avatarUrl,
                size = if (depth == 0) 40.dp else 30.dp,
                contentDescription = item.author,
            )
            item.level?.let { level ->
                Text(
                    text = "Lv.$level",
                    kind = CouiTextKind.Description,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = item.author,
                        kind = CouiTextKind.PreferenceSummary,
                        color = scheme.accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    item.levelName?.takeIf { it.isNotBlank() }?.let { levelName ->
                        Text(
                            text = "· $levelName",
                            kind = CouiTextKind.Description,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
                if (item.likes > 0) {
                    Text(
                        text = "赞 ${item.likes}",
                        kind = CouiTextKind.Description,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            item.postDate?.takeIf { it.isNotBlank() }?.let { date ->
                Text(
                    text = date,
                    kind = CouiTextKind.Description,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    textAlign = TextAlign.Start,
                )
            }
            if (item.badges.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    item.badges.forEach { badge ->
                        CouiCommentAvatar(
                            url = badge,
                            size = 22.dp,
                            contentDescription = COUI_COMMENT_BADGE_DESCRIPTION,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            CouiCommentBody(item)
            onReply?.let { reply ->
                Text(
                    text = "回复",
                    kind = CouiTextKind.PreferenceValue,
                    color = scheme.accent,
                    maxLines = 1,
                    textAlign = TextAlign.Start,
                    modifier = Modifier
                        .couiPressable { reply(item) }
                        .padding(horizontal = 2.dp, vertical = 4.dp),
                )
            }
            if (item.replies.isNotEmpty()) {
                val shown = if (repliesExpanded) {
                    item.replies
                } else {
                    item.replies.take(COUI_COMMENT_VISIBLE_REPLIES)
                }
                shown.forEach { child ->
                    CouiCommentRow(item = child, depth = depth + 1, onReply = onReply)
                }
                if (item.replies.size > COUI_COMMENT_VISIBLE_REPLIES) {
                    val hiddenCount = item.replies.size - COUI_COMMENT_VISIBLE_REPLIES
                    Text(
                        text = if (repliesExpanded) "收起回复" else "展开 $hiddenCount 条回复",
                        kind = CouiTextKind.PreferenceValue,
                        color = scheme.accent,
                        maxLines = 1,
                        textAlign = TextAlign.Start,
                        modifier = Modifier
                            .couiPressable { repliesExpanded = !repliesExpanded }
                            .padding(horizontal = 2.dp, vertical = 4.dp),
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
private fun CouiCommentAvatar(
    url: String?,
    size: Dp,
    contentDescription: String? = null,
) {
    val scheme = CouiKitTheme.colorScheme
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(scheme.container),
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape),
            )
        }
    }
}

/** 正文按分段渲染：推荐作品拆成强调链接，其余按原文换行展示。 */
@Composable
private fun CouiCommentBody(item: CommentItem) {
    val scheme = CouiKitTheme.colorScheme
    val segments = item.body.ifEmpty { listOf(CommentSegment.Text(item.content)) }
    Column(modifier = Modifier.fillMaxWidth()) {
        segments.forEach { segment ->
            when (segment) {
                is CommentSegment.Text -> if (segment.text.isNotBlank()) {
                    Text(
                        text = segment.text,
                        kind = CouiTextKind.Body,
                        color = scheme.onSurface,
                        maxLines = COUI_COMMENT_BODY_MAX_LINES,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Start,
                    )
                }

                is CommentSegment.Album -> Text(
                    text = segment.label,
                    kind = CouiTextKind.Body,
                    color = scheme.accent,
                    textAlign = TextAlign.Start,
                )
            }
        }
    }
}

/** 收藏夹选择弹窗：未收藏=加入，已收藏=移动，底部提供取消收藏。 */
@Composable
private fun CouiFolderDialog(
    visible: Boolean,
    favorited: Boolean,
    folders: Map<String, String>,
    onDismiss: () -> Unit,
    onPick: (folderId: String) -> Unit,
    onCancelFavorite: () -> Unit,
) {
    if (!visible) return
    val scheme = CouiKitTheme.colorScheme
    Dialog(
        onDismissRequest = onDismiss,
        confirmAction = CouiDialogAction(
            text = "关闭",
            onClick = onDismiss,
            style = CouiDialogActionStyle.Primary,
            colors = CouiDialogDefaults.primaryActionColors(scheme.accent),
        ),
        dark = CouiKitTheme.isDark,
        title = if (favorited) "移动收藏夹" else "选择收藏夹",
    ) {
        val allLabel = folders["0"] ?: "全部收藏"
        CouiDialogRow(
            title = allLabel,
            summary = "默认收藏夹",
            onClick = { onPick("0") },
        )
        folders.entries.filter { it.key != "0" }.forEach { entry ->
            CouiDialogRow(title = entry.value, onClick = { onPick(entry.key) })
        }
        if (favorited) {
            CouiDialogRow(title = "取消收藏", danger = true, onClick = onCancelFavorite)
        }
    }
}

/** 弹窗内的一行：标题 + 可选说明 + 方向角，按压反馈与页面一致。 */
@Composable
private fun CouiDialogRow(
    title: String,
    onClick: () -> Unit,
    summary: String? = null,
    danger: Boolean = false,
) {
    val scheme = CouiKitTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CouiShape(16.dp, 3f))
            .couiPressable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                kind = CouiTextKind.PreferenceTitle,
                color = if (danger) scheme.accent else scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start,
            )
            if (summary != null) {
                Text(
                    text = summary,
                    kind = CouiTextKind.Description,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    textAlign = TextAlign.Start,
                )
            }
        }
        Icon(
            imageVector = CouiIcons.Forward,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = scheme.onSurfaceVariant,
        )
    }
}

/** 回复弹窗：标题带被回复作者，发送后由状态持有者刷新评论列表。 */
@Composable
private fun CouiReplyDialog(
    visible: Boolean,
    targetAuthor: String?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSend: (String) -> Unit,
) {
    if (!visible) return
    val scheme = CouiKitTheme.colorScheme
    var replyText by remember { mutableStateOf("") }
    Dialog(
        onDismissRequest = onDismiss,
        confirmAction = CouiDialogAction(
            text = if (busy) "发送中…" else "发送",
            onClick = { onSend(replyText) },
            enabled = !busy && replyText.isNotBlank(),
            style = CouiDialogActionStyle.Primary,
            colors = CouiDialogDefaults.primaryActionColors(scheme.accent),
        ),
        dismissAction = CouiDialogAction(text = "取消", onClick = onDismiss),
        dark = CouiKitTheme.isDark,
        title = "回复 ${targetAuthor.orEmpty()}",
    ) {
        BasicTextField(
            value = replyText,
            onValueChange = { replyText = it },
            textStyle = rememberCouiTextStyle(CouiTextKind.Body).copy(color = scheme.onSurface),
            cursorBrush = SolidColor(scheme.accent),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 96.dp)
                .clip(CouiShape(16.dp, 3f))
                .background(scheme.container)
                .padding(12.dp),
            decorationBox = { innerTextField ->
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (replyText.isEmpty()) {
                        Text(
                            text = "写下你的回复（最多 $COMMENT_REPLY_MAX_LENGTH 字）",
                            kind = CouiTextKind.Body,
                            color = scheme.onSurfaceVariant,
                            textAlign = TextAlign.Start,
                        )
                    }
                    innerTextField()
                }
            },
        )
        Text(
            text = "${replyText.length}/$COMMENT_REPLY_MAX_LENGTH",
            kind = CouiTextKind.Description,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Start,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** 详情加载中的占位：只用一行 COUI 文字，避免引入 Miuix 的进度指示器。 */
@Composable
private fun CouiLoadingGroup() {
    val scheme = CouiKitTheme.colorScheme
    Group {
        Item(
            title = {
                Text(
                    text = "加载中…",
                    kind = CouiTextKind.PreferenceTitle,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Start,
                )
            },
        )
    }
}

/** 失败提示：文案 + 单个胶囊动作（去登录 / 重试）。 */
@Composable
private fun CouiNoticeGroup(
    message: String,
    actionText: String,
    onAction: () -> Unit,
) {
    val scheme = CouiKitTheme.colorScheme
    Group {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = message,
                kind = CouiTextKind.Body,
                color = scheme.onSurface,
                textAlign = TextAlign.Start,
            )
            Spacer(modifier = Modifier.height(12.dp))
            CouiCapsuleAction(text = actionText, accent = true, onClick = onAction)
        }
    }
}
