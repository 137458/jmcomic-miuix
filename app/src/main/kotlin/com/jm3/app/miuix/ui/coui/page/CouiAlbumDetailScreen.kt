// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.page

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.CouiDialogActionStyle
import com.itos.coui.dialog.CouiDialogDefaults
import com.itos.coui.dialog.Dialog
import com.itos.coui.effect.SpotlightPreset
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.layout.Divider
import com.itos.coui.layout.Group
import com.itos.coui.layout.Item
import com.itos.coui.scroll.rememberCouiOverscrollEffect
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
import com.jm3.app.miuix.ui.AlbumDetailHeroFlightEffect
import com.jm3.app.miuix.ui.AlbumDetailStateHolder
import com.jm3.app.miuix.ui.AlbumDetailUiSnapshot
import com.jm3.app.miuix.ui.AlbumDetailUiSnapshotSaver
import com.jm3.app.miuix.ui.AlbumUiState
import com.jm3.app.miuix.ui.COMMENT_REPLY_MAX_LENGTH
import com.jm3.app.miuix.ui.CommentsUiState
import com.jm3.app.miuix.ui.GlassSnackbarHost
import com.jm3.app.miuix.ui.HERO_COVER_HEIGHT_DP
import com.jm3.app.miuix.ui.HERO_COVER_WIDTH_DP
import com.jm3.app.miuix.ui.LocalAlbumDetailRootCoordinates
import com.jm3.app.miuix.ui.LocalAlbumHeroHost
import com.jm3.app.miuix.ui.LocalWideLayout
import com.jm3.app.miuix.ui.SessionCache
import com.jm3.app.miuix.ui.StringSetSaver
import com.jm3.app.miuix.ui.albumHeroHeader
import com.jm3.app.miuix.ui.rememberSavedDialogDraft
import com.jm3.app.miuix.ui.coui.component.CouiSectionTitleText
import com.jm3.app.miuix.ui.coui.component.CouiBodyText
import com.jm3.app.miuix.ui.coui.component.rememberCouiBodyTextStyle
import com.jm3.app.miuix.ui.coui.component.COUI_COMMENT_MAX_INDENT_DEPTH
import com.jm3.app.miuix.ui.coui.component.CouiAppScaffold
import com.jm3.app.miuix.ui.coui.component.CouiCommentFieldShape
import com.jm3.app.miuix.ui.coui.component.CouiCommentRow
import com.jm3.app.miuix.ui.coui.component.CouiDetailActionTile
import com.jm3.app.miuix.ui.coui.component.CouiDetailButtonShape
import com.jm3.app.miuix.ui.coui.component.CouiEmptyGroup
import com.jm3.app.miuix.ui.coui.component.CouiGroupRow
import com.jm3.app.miuix.ui.coui.component.CouiGroupTitle
import com.jm3.app.miuix.ui.coui.component.CouiHeaderCapsuleAction
import com.jm3.app.miuix.ui.coui.component.CouiLoadMoreFooter
import com.jm3.app.miuix.ui.coui.component.CouiLoadingGroup
import com.jm3.app.miuix.ui.coui.component.CouiMinTouchTarget
import com.jm3.app.miuix.ui.coui.component.CouiPrimaryActionButton
import com.jm3.app.miuix.ui.coui.component.CouiScreenFailureGroup
import com.jm3.app.miuix.ui.coui.component.CouiSelectedContainerAlpha
import com.jm3.app.miuix.ui.coui.component.CouiTagCapsulePill
import com.jm3.app.miuix.ui.coui.component.CouiTopBarAction
import com.jm3.app.miuix.ui.coui.component.CouiTopBarBackButton
import com.jm3.app.miuix.ui.coui.component.CouiTopBarTitle
import com.jm3.app.miuix.ui.coui.component.couiDialogMaterial
import com.jm3.app.miuix.ui.coui.component.couiDialogSurfaceBorder
import com.jm3.app.miuix.ui.coui.component.couiDialogSurfaceColor
import com.jm3.app.miuix.ui.coui.component.couiTopAppBarColors
import com.jm3.app.miuix.ui.coui.component.couiPressable
import com.jm3.app.miuix.ui.coui.component.couiWithBottomPadding
import com.jm3.app.miuix.ui.coui.component.rememberRetainedTarget
import com.jm3.app.miuix.ui.couiPageExtraBottomDp
import com.jm3.app.miuix.ui.coui.icon.CouiIcons
import com.jm3.app.miuix.ui.coui.theme.CouiStrings
import com.jm3.app.miuix.ui.coui.theme.CouiTheme
import com.jm3.app.miuix.ui.readableWidth
import com.jm3.app.miuix.ui.rememberReaderImageLoader
import com.jm3.app.miuix.ui.skin.LocalSkin
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
import com.jm3.core.session.AccountManager
import com.jm3.core.session.AuthCall
import com.jm3.core.session.SessionOutcome
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SnackbarHostState

/** 简介折叠阈值：短简介没有折叠的必要，只有超过这个长度才给出展开入口。 */
private const val COUI_DESCRIPTION_COLLAPSE_THRESHOLD = 80
/** 折叠态默认展示的回复条数；超出部分由「展开 N 条回复」控制。 */
private const val COUI_COMMENT_VISIBLE_REPLIES = 2
/** 回复缩进的最大层级统一由 CouiAppComponents.COUI_COMMENT_MAX_INDENT_DEPTH 提供。 */
/** 评论正文限行：详情页信息密度低于论坛，保留 12 行以容纳长评。 */
private const val COUI_COMMENT_BODY_MAX_LINES = 12

/**
 * COUI 作品详情页：头部信息卡、阅读操作、简介、预览、章节、作品信息、相关作品与评论区。
 *
 * 业务编排全部复用 [AlbumDetailStateHolder]（加载、收藏/取消收藏、喜欢、追踪、评论分页与发送、
 * 章节分组展开态、续读记录），本文件只负责 COUI 原厂视觉：折叠大标题顶栏、Group/Item 分组行、
 * 胶囊动作、CouiTextKind 字阶与 COUI 回弹。
 *
 * 整页是一张 LazyColumn：头部、操作、简介、预览条、章节、信息、相关各成 item，评论按行摊平后
 * 逐条成 item（[flattenComments]），长评论区因此只组合可见行。宽屏（[LocalWideLayout] 的
 * twoPaneDetail）拆成左「作品」右「评论」两栏，各持一份 LazyListState。
 * 唯一做不到的是 COUI 的短页强回弹：库里靠覆写 canScrollForward 伪造可滚动性，LazyListState
 * 没有这个口子，短页只保留原生回弹。
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

    var uiSnapshot by rememberSaveable(albumId, stateSaver = AlbumDetailUiSnapshotSaver) {
        mutableStateOf(AlbumDetailUiSnapshot())
    }
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
        ).apply { restoreUiSnapshot(uiSnapshot) }
    }
    val uiState by stateHolder.uiState.collectAsState()
    val commentDraftSnapshot by stateHolder.commentDraft.collectAsState()
    LaunchedEffect(
        albumId,
        commentDraftSnapshot,
        uiState.replyTarget,
        uiState.descriptionExpanded,
        uiState.expandedGroups,
        uiState.showFolderSheet,
    ) {
        uiSnapshot = stateHolder.saveUiSnapshot()
    }
    LaunchedEffect(albumId) {
        if (uiSnapshot.showFolderSheet && uiState.folders.isEmpty()) stateHolder.openFolderSheet()
    }

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
    // 预测性返回：封面逆向飞行的编排（退出进度 → 起飞/推进/收场），详情页渐隐淡出，位移由共享封面主导
    AlbumDetailHeroFlightEffect(albumId)
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
    // 评论折叠态由页面持有：摊平只认集合里的 id，展开/收起只重算行表
    var expandedReplyIds by rememberSaveable(albumId, stateSaver = StringSetSaver) {
        mutableStateOf(emptySet<String>())
    }
    val commentFeed = (uiState.commentsState as? CommentsUiState.Loaded)?.feed
    val commentRows = remember(commentFeed?.items, expandedReplyIds) {
        flattenComments(commentFeed?.items.orEmpty(), expandedReplyIds)
    }

    // 宽屏两栏：左栏作品信息与章节，右栏评论；两栏各自持有滚动状态，互不牵连
    val wideDetail = LocalWideLayout.current.twoPaneDetail
    val singleListState = rememberLazyListState()
    val mainListState = rememberLazyListState()
    val commentListState = rememberLazyListState()
    // 顶栏折叠完全跟随滚动位移：COUI 的 canScroll 只在带搜索栏的顶栏里生效（库内 hasSearchBar=false），
    // 懒列表也没有 canScrollForward 覆写口，COUI 的短页强回弹在这里无法复现，只保留原生回弹。
    val appBarBehavior = CouiTopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val density = LocalDensity.current
    // 评论分页：公共分页脚只报进度与重试，没有「点一下再拉一页」的脚位，
    // 这里沿用首页/浏览页的触底预取：最后一个可见项进入末尾提前量区间就取下一页
    val activeCommentListState = if (wideDetail) commentListState else singleListState
    val loadMoreFeed by rememberUpdatedState(commentFeed)
    CouiPrefetchEffect(listState = activeCommentListState) { lastIndex, totalCount, distance ->
        if (lastIndex < 0) return@CouiPrefetchEffect
        val feed = loadMoreFeed ?: return@CouiPrefetchEffect
        if (!feed.canLoadMore || feed.loadMoreFailed) return@CouiPrefetchEffect
        if (!shouldPrefetch(lastIndex, totalCount, distance)) return@CouiPrefetchEffect
        stateHolder.loadMoreComments()
    }
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
                        // 槽内必须接顶栏下发的字阶（LocalCouiTextStyle），否则折叠后按 16sp 比例缩成 7sp
                        title = { CouiTopBarTitle(text = currentTitle) },
                        navigationIcon = {
                            CouiTopBarBackButton(onClick = onBack)
                        },
                        actions = {
                            if (loadedDetail != null) {
                                CouiTopBarAction(
                                    icon = CouiIcons.Share,
                                    contentDescription = "分享",
                                    onClick = { share(loadedDetail) },
                                )
                                if (loadedDetail.chapters.size > 1) {
                                    CouiTopBarAction(
                                        icon = CouiIcons.Download,
                                        contentDescription = "下载全部",
                                        onClick = { downloadAll(loadedDetail.id, loadedDetail.chapters) },
                                    )
                                }
                            }
                        },
                        scrollBehavior = appBarBehavior,
                        colors = couiTopAppBarColors(),
                    )
                },
            ) { innerPadding ->
                // 顶栏补偿留白由折叠状态给出，再补页面底部的呼吸位；懒列表用内容留白而不是 padding，
                // 这样滚到顶时内容贴着顶栏滑走，而不是在列表外留一条死白。
                // 键盘弹出时底部呼吸位让位：ime 内边距已由 CouiAppScaffold 统一补，这里再留 24dp 会顶起一截空白
                val contentPadding = appBarBehavior.state
                    .contentPadding(innerPadding, density)
                    .couiWithBottomPadding(
                        couiPageExtraBottomDp(hasFab = false, isImeVisible = WindowInsets.isImeVisible).dp,
                    )
                // 章节树摊平成惰性项：每个组头、每一话各自成项，几百话也只组合可见的十几行
                val chaptersPlan = remember(
                    loadedDetail?.chapters,
                    uiState.expandedGroups,
                    resumeId,
                    uiState.readPhotoIds,
                ) {
                    loadedDetail?.let {
                        planCouiChapters(it.chapters, uiState.expandedGroups, resumeId, uiState.readPhotoIds)
                    }
                }
                // 头部：头图、操作与简介这类「一眼定调」的内容；宽屏时留在左栏
                val headItems: LazyListScope.() -> Unit = {
                    item(key = "hero") {
                        CouiAlbumHeroCard(
                            albumId = albumId,
                            detail = loadedDetail,
                            fallbackTitle = effectiveTitle,
                            fallbackCoverUrl = effectiveCoverUrl,
                            onOpenSearch = onOpenSearch,
                            onCopyId = { copiedId -> copyText("禁漫车号", copiedId) },
                        )
                    }
                    when (val albumState = uiState.albumState) {
                        is AlbumUiState.Loading -> item(key = "album-loading") {
                            CouiLoadingGroup()
                        }

                        is AlbumUiState.Failed -> item(key = "album-failed") {
                            CouiScreenFailureGroup(
                                message = albumState.message,
                                expired = albumState.sessionExpired,
                                onRetry = stateHolder::refresh,
                                onOpenLogin = onOpenLogin,
                            )
                        }

                        is AlbumUiState.Loaded -> {
                            val detail = albumState.detail
                            // 匿名函数而不是块内 lambda：可空函数类型下不需要依赖目标类型推断
                            val downloadAllAction: (() -> Unit)? = if (detail.chapters.isEmpty()) {
                                null
                            } else {
                                fun() { downloadAll(detail.id, detail.chapters) }
                            }
                            item(key = "read-actions") {
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
                                        if (!uiState.trackingBusy) stateHolder.toggleTracking(user != null, onOpenLogin)
                                    },
                                    onDownloadAll = downloadAllAction,
                                )
                            }
                            if (detail.description.isNotBlank()) {
                                item(key = "description") {
                                    CouiDescriptionGroup(
                                        text = detail.description,
                                        expanded = uiState.descriptionExpanded,
                                        onToggle = { stateHolder.setDescriptionExpanded(!uiState.descriptionExpanded) },
                                        onCopy = { copyText("简介", detail.description) },
                                    )
                                }
                            }
                        }
                    }
                }
                // 预览与章节归右栏：宽屏时读者在右栏自上而下就是「先翻页、再挑话」
                val previewItems: LazyListScope.() -> Unit = {
                    previewPlan?.let { plan ->
                        item(key = "preview") {
                            CouiPreviewGroup(
                                plan = plan,
                                imageLoader = previewImageLoader,
                                onOpenPage = { page -> onOpenReader(plan.chapterId, page) },
                            )
                        }
                    }
                }
                val chapterItems: LazyListScope.() -> Unit = {
                    val detail = loadedDetail
                    val plan = chaptersPlan
                    if (detail != null && plan != null) {
                        couiChapterItems(
                            plan = plan,
                            expandedGroups = uiState.expandedGroups,
                            onToggleGroup = stateHolder::toggleGroup,
                            onRead = { chapter -> onOpenReader(chapter.id, null) },
                            onDownload = { chapter, title -> downloadChapter(chapter, detail.id, title) },
                        )
                    }
                }
                // 作品信息留在左栏（与头部同侧），相关推荐跟着章节在右栏收尾
                val infoItems: LazyListScope.() -> Unit = {
                    val detail = loadedDetail
                    if (detail != null) {
                        item(key = "info") {
                            CouiInfoGroup(
                                detail = detail,
                                displayId = detail.id.trim().takeIf { it.isNotBlank() && it != "-1" } ?: albumId,
                                onOpenSearch = onOpenSearch,
                                onCopy = { label, value -> copyText(label, value) },
                            )
                        }
                    }
                }
                val relatedItems: LazyListScope.() -> Unit = {
                    val related = loadedDetail?.related
                    if (!related.isNullOrEmpty()) {
                        item(key = "related") {
                            CouiRelatedGroup(related = related, onOpenAlbum = onOpenAlbum)
                        }
                    }
                }
                // 评论区按项拆开：标题行、输入卡、每条评论与分页脚各自成项，长评论区才会真正按需组合
                val commentItems: LazyListScope.() -> Unit = {
                    val commentsState = uiState.commentsState
                    val commentTotal = (commentsState as? CommentsUiState.Loaded)?.feed?.total
                        ?: loadedDetail?.commentCount?.takeIf { it > 0 }
                    item(key = "comments-title") {
                        CouiGroupTitle(
                            title = if (commentTotal != null && commentTotal > 0) {
                                "评论（$commentTotal）"
                            } else {
                                "评论"
                            },
                        )
                    }
                    item(key = "comments-composer") {
                        // 草稿走独立的流：每敲一个字只重组输入卡，不惊动整页 uiState 与评论行表
                        val draft by stateHolder.commentDraft.collectAsState()
                        CouiGroupRow(isFirst = true, isLast = commentRows.isEmpty(), showDivider = false) {
                            CouiCommentComposer(
                                loggedIn = user != null,
                                text = draft,
                                busy = uiState.commentBusy,
                                onTextChange = stateHolder::setCommentText,
                                onLogin = onOpenLogin,
                                onSend = stateHolder::postComment,
                            )
                        }
                    }
                    when (commentsState) {
                        is CommentsUiState.Loading -> item(key = "comments-loading") {
                            CouiLoadingGroup(text = "评论加载中…")
                        }

                        is CommentsUiState.Failed -> item(key = "comments-failed") {
                            CouiScreenFailureGroup(
                                message = commentsState.message,
                                expired = commentsState.sessionExpired,
                                onRetry = stateHolder::reloadComments,
                                onOpenLogin = onOpenLogin,
                            )
                        }

                        is CommentsUiState.Loaded -> if (commentsState.feed.items.isEmpty()) {
                            item(key = "comments-empty") {
                                CouiEmptyGroup(text = CouiStrings.EmptyComments)
                            }
                        }
                    }
                    itemsIndexed(
                        items = commentRows,
                        key = { _, row -> row.key },
                        contentType = { _, row -> row.contentType },
                    ) { index, row ->
                        CouiGroupRow(isFirst = false, isLast = index == commentRows.lastIndex, showDivider = false) {
                            when (row) {
                                is CommentRow.Comment -> {
                                    if (row.depth == 0) Divider()
                                    CouiCommentRow(
                                        item = row.item,
                                        depth = row.depth,
                                        bodyMaxLines = COUI_COMMENT_BODY_MAX_LINES,
                                        onReply = { comment ->
                                            if (user == null) onOpenLogin() else stateHolder.setReplyTarget(comment)
                                        },
                                    )
                                }

                                is CommentRow.RepliesToggle -> CouiRepliesToggle(
                                    row = row,
                                    onToggle = {
                                        // 只记录被点开的那一条：不在集合里就退回默认的两条可见回复
                                        expandedReplyIds = if (row.parentId in expandedReplyIds) {
                                            expandedReplyIds - row.parentId
                                        } else {
                                            expandedReplyIds + row.parentId
                                        }
                                    },
                                )
                            }
                        }
                    }
                    val feed = (commentsState as? CommentsUiState.Loaded)?.feed
                    if (feed != null && feed.items.isNotEmpty()) {
                        item(key = "comments-footer") {
                            // 失败文案只走状态持有者的 snackbar；脚位只保留「重新加载」动作，避免同一条错误说两遍
                            CouiLoadMoreFooter(
                                feed = feed,
                                errorMessage = "",
                                onRetry = stateHolder::loadMoreComments,
                            )
                        }
                    }
                }
                if (wideDetail) {
                    // 宽屏两栏：左栏是「这部作品是什么」（头部 + 操作 + 简介 + 信息），
                    // 右栏是「读什么、大家怎么说」（预览 + 章节 + 相关 + 评论）；两栏各自滚动、互不牵扯
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .readableWidth()
                            .consumeWindowInsets(innerPadding),
                    ) {
                        LazyColumn(
                            state = mainListState,
                            modifier = Modifier
                                .weight(0.38f)
                                .fillMaxHeight(),
                            contentPadding = contentPadding,
                            overscrollEffect = rememberCouiOverscrollEffect(),
                        ) {
                            couiInclude(headItems)
                            couiInclude(infoItems)
                        }
                        LazyColumn(
                            state = commentListState,
                            modifier = Modifier
                                .weight(0.62f)
                                .fillMaxHeight(),
                            contentPadding = contentPadding,
                            overscrollEffect = rememberCouiOverscrollEffect(),
                        ) {
                            couiInclude(previewItems)
                            couiInclude(chapterItems)
                            couiInclude(relatedItems)
                            couiInclude(commentItems)
                        }
                    }
                } else {
                    LazyColumn(
                        state = singleListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .readableWidth()
                            .consumeWindowInsets(innerPadding),
                        contentPadding = contentPadding,
                        overscrollEffect = rememberCouiOverscrollEffect(),
                    ) {
                        couiInclude(headItems)
                        couiInclude(previewItems)
                        couiInclude(chapterItems)
                        couiInclude(infoItems)
                        couiInclude(relatedItems)
                        couiInclude(commentItems)
                    }
                }
            }

            // 页面内的 Snackbar 需要真实底栏上方留白，GlassSnackbarHost 内部会补这段差额
            GlassSnackbarHost(
                state = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
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
                targetKey = uiState.replyTarget?.id,
                busy = uiState.replyBusy,
                onDismiss = { stateHolder.setReplyTarget(null) },
                onSend = stateHolder::sendReply,
            )
        }
    }
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
            .heightIn(min = 34.dp)
            .couiPressable(
                shape = CouiCapsuleShape,
                backgroundColor = background,
                // 强调色实底按库的主按钮口径取 OpaqueMedium1，浅底胶囊用 ChipTab
                spotlight = if (accent) SpotlightPreset.OpaqueMedium1 else SpotlightPreset.ChipTab,
                enabled = enabled,
                scaleOnPress = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            kind = CouiTextKind.ItemValue,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 头部信息卡：3:4 封面 + 标题 + 作者胶囊 + 可复制车号 + 结构化热度指标 + 标签胶囊。
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
    onCopyId: (String) -> Unit,
) {
    val dark = CouiKitTheme.isDark
    val scheme = CouiKitTheme.colorScheme
    val heroHost = LocalAlbumHeroHost.current
    val title = detail?.title?.trim()?.takeIf { it.isNotBlank() } ?: fallbackTitle?.trim().orEmpty()
    val coverUrl = detail?.coverUrl?.trim()?.takeIf { it.isNotBlank() } ?: fallbackCoverUrl?.trim()
    val displayId = detail?.id?.trim()?.takeIf { it.isNotBlank() && it != "-1" } ?: albumId
    val authors = detail?.authors.orEmpty().map { it.trim() }.filter { it.isNotBlank() }
    val tags = detail?.tags.orEmpty().map { it.trim() }.filter { it.isNotBlank() }
    val statChips = buildList {
        detail?.likes?.takeIf { it.isNotBlank() && it != "0" }?.let { add("喜欢 $it") }
        detail?.views?.takeIf { it.isNotBlank() && it != "0" }?.let { add("浏览 $it") }
        detail?.commentCount?.takeIf { it > 0 }?.let { add("评论 $it") }
        detail?.pageCount?.takeIf { it > 0 }?.let { add("${it} 页") }
    }
    val metaChipBg = scheme.onSurface.copy(alpha = if (dark) 0.08f else 0.05f)
    val metaChipBorder = scheme.onSurface.copy(alpha = if (dark) 0.12f else 0.07f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CouiPageDefaults.horizontalMargin)
            .padding(top = CouiPageDefaults.categoryTopSpacing)
            .clip(CouiPageDefaults.shape)
            .background(scheme.card),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            // 没有封面就不占位：留一个空色块既不像卡片，也会把标题挤到右边（与 Miuix 详情页同口径）
            if (!coverUrl.isNullOrBlank()) {
                // 头图圆角跟着皮肤走：飞行两端的圆角必须一致，写死常量会在切皮肤时整体位移
                val heroCorner = LocalSkin.current.shapes.cardCorner
                // 头图尺寸直接取共享的解码尺寸（144x192dp）：列表卡、飞行覆盖层与头图共用同一条 Coil 内存缓存，
                // 显示尺寸一旦小于解码尺寸，缓存位图就会被当缩略图放大复用，这里不再另立 108x144 的局部常量
                Box(
                    modifier = Modifier
                        .size(width = HERO_COVER_WIDTH_DP.dp, height = HERO_COVER_HEIGHT_DP.dp)
                        .clip(RoundedCornerShape(heroCorner))
                        .background(scheme.container)
                        .albumHeroHeader(albumId, coverUrl, heroCorner.value),
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
            }
            Column(modifier = Modifier.weight(1f)) {
                CouiSectionTitleText(
                    text = title.ifBlank { "作品详情" },
                    color = scheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Start,
                )
                if (authors.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .heightIn(min = 28.dp)
                            .couiPressable(
                                shape = CouiCapsuleShape,
                                backgroundColor = scheme.accent.copy(alpha = if (dark) 0.18f else CouiSelectedContainerAlpha),
                                spotlight = SpotlightPreset.ChipTab,
                                scaleOnPress = true,
                                onClick = { onOpenSearch(authors.first(), SearchScope.AUTHOR) },
                            )
                            .border(0.5.dp, scheme.accent.copy(alpha = if (dark) 0.34f else 0.22f), CouiCapsuleShape)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = authors.joinToString("、"),
                            kind = CouiTextKind.GroupFooter,
                            color = scheme.accent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Start,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .heightIn(min = 26.dp)
                        .couiPressable(
                            shape = CouiCapsuleShape,
                            backgroundColor = metaChipBg,
                            spotlight = SpotlightPreset.ChipTab,
                            scaleOnPress = true,
                            onClick = { onCopyId(displayId) },
                        )
                        .border(0.5.dp, metaChipBorder, CouiCapsuleShape)
                        .padding(horizontal = 9.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        text = "JM #$displayId",
                        kind = CouiTextKind.GroupFooter,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Start,
                    )
                    Icon(
                        imageVector = CouiIcons.Copy,
                        contentDescription = "复制禁漫车号",
                        modifier = Modifier.size(12.dp),
                        tint = scheme.onSurfaceVariant,
                    )
                }
                if (statChips.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        statChips.forEach { stat ->
                            Box(
                                modifier = Modifier
                                    .clip(CouiCapsuleShape)
                                    .background(metaChipBg)
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                            ) {
                                Text(
                                    text = stat,
                                    kind = CouiTextKind.GroupFooter,
                                    color = scheme.onSurfaceVariant,
                                    maxLines = 1,
                                    textAlign = TextAlign.Start,
                                )
                            }
                        }
                    }
                }
            }
        }
        if (tags.isNotEmpty()) {
            Divider(startIndent = 16.dp, endIndent = 16.dp)
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                tags.forEach { tag ->
                    CouiTagCapsulePill(
                        text = tag,
                        compact = true,
                        onClick = { onOpenSearch(tag, SearchScope.TAG) },
                    )
                }
            }
        }
    }
}

/** 阅读操作中心卡：将主题高亮主阅读按钮与四项常用功能瓦片收拢进同一张卡片，缩短跳转断层。 */
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CouiPageDefaults.horizontalMargin)
            .padding(top = CouiPageDefaults.categoryTopSpacing)
            .clip(CouiPageDefaults.shape)
            .background(scheme.card)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CouiPrimaryActionButton(
            title = if (resumeId != null) "继续阅读" else "开始阅读",
            subtitle = when {
                startPhotoId == null -> CouiStrings.EmptyChapters
                resumeId != null -> "接着上次读到的一话"
                else -> "从第 1 话开始"
            },
            leadingIcon = CouiIcons.Book,
            trailingIcon = CouiIcons.Forward,
            enabled = startPhotoId != null,
            onClick = { startPhotoId?.let { onRead(it, null) } },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CouiDetailActionTile(
                icon = CouiIcons.Star,
                label = if (favorited) "已收藏" else "收藏",
                modifier = Modifier.weight(1f),
                active = favorited,
                onClick = onFavorite,
            )
            CouiDetailActionTile(
                icon = CouiIcons.Heart,
                label = if (liked) "已喜欢" else "喜欢",
                modifier = Modifier.weight(1f),
                active = liked,
                onClick = onLike,
            )
            // 追踪态是单独请求，失败过就一直是 null；入口不能跟着消失，否则用户得重进页面才能追更
            if (loggedIn) {
                CouiDetailActionTile(
                    icon = CouiIcons.Refresh,
                    label = when {
                        trackingBusy -> "处理中…"
                        tracking == true -> "追更中"
                        else -> "追更"
                    },
                    modifier = Modifier.weight(1f),
                    active = tracking == true,
                    enabled = !trackingBusy,
                    onClick = onTrack,
                )
            }
            onDownloadAll?.let { download ->
                CouiDetailActionTile(
                    icon = CouiIcons.Download,
                    label = "下载",
                    modifier = Modifier.weight(1f),
                    onClick = download,
                )
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
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CouiHeaderCapsuleAction(
                    text = "复制",
                    tinted = false,
                    onClick = onCopy,
                )
                if (text.length > COUI_DESCRIPTION_COLLAPSE_THRESHOLD) {
                    CouiHeaderCapsuleAction(
                        text = if (expanded) "收起" else "展开",
                        trailingIcon = if (expanded) CouiIcons.ChevronUp else CouiIcons.ChevronDown,
                        onClick = onToggle,
                    )
                }
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            CouiBodyText(
                text = text,
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
                value = works.joinToString("、"),
                onClick = { onOpenSearch(works.first(), SearchScope.WORK) },
            )
            Divider()
        }
        if (actors.isNotEmpty()) {
            CouiInfoRow(
                title = "登场人物",
                value = actors.joinToString("、"),
                onClick = { onOpenSearch(actors.first(), SearchScope.ACTOR) },
            )
            Divider()
        }
        Item(
            title = { Text(text = "禁漫车号", kind = CouiTextKind.ItemTitle, textAlign = TextAlign.Start) },
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "#$displayId",
                        kind = CouiTextKind.ItemValue,
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
        title = { Text(text = title, kind = CouiTextKind.ItemTitle, textAlign = TextAlign.Start) },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = value,
                    kind = CouiTextKind.ItemValue,
                    color = scheme.onSurfaceVariant,
                    maxLines = 2,
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
                val previewShape = RoundedCornerShape(LocalSkin.current.shapes.controlCorner)
                AsyncImage(
                    model = page,
                    imageLoader = imageLoader,
                    contentDescription = "预览第 ${page.index + 1} 页",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 104.dp, height = 140.dp)
                        // 缩略图不参与飞行，圆角取皮肤的控制件圆角（ENGINEERING.md:93）
                        .clip(previewShape)
                        .background(scheme.container)
                        // 缩略图铺满这个节点：光画在内容之下会被整张图盖住
                        .couiPressable(shape = previewShape, spotlightAboveContent = true) {
                            onOpenPage(page.index + 1)
                        },
                )
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
                val itemShape = RoundedCornerShape(LocalSkin.current.shapes.controlCorner)
                Column(
                    modifier = Modifier
                        .width(104.dp)
                        .clip(itemShape)
                        // 卡片上半是封面图：光必须画在内容之上才看得见
                        .couiPressable(shape = itemShape, spotlightAboveContent = true) {
                            onOpenAlbum(item.id)
                        },
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 104.dp, height = 138.dp)
                            // 缩略图不参与飞行，圆角取皮肤的控制件圆角（ENGINEERING.md:93）
                            .clip(RoundedCornerShape(LocalSkin.current.shapes.controlCorner))
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
                        kind = CouiTextKind.ItemSummary,
                        color = scheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Start,
                    )
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            kind = CouiTextKind.GroupFooter,
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
            title = { Text(text = "登录后发表评论", kind = CouiTextKind.ItemTitle, textAlign = TextAlign.Start) },
            summary = {
                Text(
                    text = "登录账号即可参与讨论",
                    kind = CouiTextKind.GroupFooter,
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
            textStyle = rememberCouiBodyTextStyle().copy(color = scheme.onSurface),
            cursorBrush = SolidColor(scheme.accent),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 84.dp)
                .clip(CouiCommentFieldShape)
                .background(scheme.background)
                .border(0.5.dp, scheme.outlineVariant, CouiCommentFieldShape)
                .padding(12.dp),
            decorationBox = { innerTextField ->
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (text.isEmpty()) {
                        CouiBodyText(
                            text = "写下你的评论（最多 $COMMENT_REPLY_MAX_LENGTH 字）",
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
                kind = CouiTextKind.GroupFooter,
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
    val scheme = CouiKitTheme.colorScheme
    Dialog(
        onDismissRequest = onDismiss,
        // 提前 return 会连同退场窗口一起撤掉：visible 交给库，弹窗在组合中留到动画播完
        visible = visible,
        confirmAction = CouiDialogAction(
            text = "关闭",
            onClick = onDismiss,
            style = CouiDialogActionStyle.Primary,
            colors = CouiDialogDefaults.primaryActionColors(scheme.accent),
        ),
        dark = CouiKitTheme.isDark,
        material = couiDialogMaterial(),
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
    val rowShape = CouiDetailButtonShape
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(rowShape)
            .couiPressable(shape = rowShape, minTouchTarget = CouiMinTouchTarget, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                kind = CouiTextKind.ItemTitle,
                color = if (danger) {
                    CouiDialogDefaults.themeColors(CouiKitTheme.isDark).destructive
                } else {
                    scheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start,
            )
            if (summary != null) {
                Text(
                    text = summary,
                    kind = CouiTextKind.GroupFooter,
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
    targetKey: String? = null,
) {
    val scheme = CouiKitTheme.colorScheme
    val retainedAuthor = rememberRetainedTarget(targetAuthor?.takeIf { it.isNotBlank() })
    var replyText by rememberSavedDialogDraft(isOpen = visible, targetKey = targetKey ?: retainedAuthor)
    Dialog(
        onDismissRequest = onDismiss,
        visible = visible,
        confirmAction = CouiDialogAction(
            text = if (busy) "发送中…" else "发送",
            onClick = { onSend(replyText) },
            enabled = !busy && replyText.isNotBlank(),
            style = CouiDialogActionStyle.Primary,
            colors = CouiDialogDefaults.primaryActionColors(scheme.accent),
        ),
        dismissAction = CouiDialogAction(text = "取消", onClick = onDismiss),
        dark = CouiKitTheme.isDark,
        material = couiDialogMaterial(),
        title = if (retainedAuthor != null) "回复 $retainedAuthor" else "回复",
    ) {
        BasicTextField(
            value = replyText,
            onValueChange = { replyText = it },
            textStyle = rememberCouiBodyTextStyle().copy(color = scheme.onSurface),
            cursorBrush = SolidColor(scheme.accent),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 96.dp)
                .clip(CouiCommentFieldShape)
                .background(couiDialogSurfaceColor())
                .border(couiDialogSurfaceBorder(), CouiCommentFieldShape)
                .padding(12.dp),
            decorationBox = { innerTextField ->
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (replyText.isEmpty()) {
                        CouiBodyText(
                            text = "写下你的回复（最多 $COMMENT_REPLY_MAX_LENGTH 字）",
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
            kind = CouiTextKind.GroupFooter,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Start,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** 折叠回复的入口：单独成行，缩进跟着父评论走；展开态只影响这一条父评论。 */
@Composable
private fun CouiRepliesToggle(
    row: CommentRow.RepliesToggle,
    onToggle: () -> Unit,
) {
    val scheme = CouiKitTheme.colorScheme
    val indentDepth = row.depth.coerceAtMost(COUI_COMMENT_MAX_INDENT_DEPTH)
    // 与父评论的正文起点对齐：外层缩进 + 头像宽度 + 头像与正文的间隔
    val contentStart = 16.dp + (indentDepth * 12).dp + (if (row.depth == 0) 40.dp else 30.dp) + 10.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = contentStart, end = 16.dp, bottom = 10.dp),
    ) {
        Text(
            text = if (row.expanded) "收起回复" else "展开 ${row.hiddenCount} 条回复",
            kind = CouiTextKind.ItemValue,
            color = scheme.accent,
            maxLines = 1,
            textAlign = TextAlign.Start,
            modifier = Modifier
                .clip(CouiCapsuleShape)
                .couiPressable(shape = CouiCapsuleShape, spotlight = SpotlightPreset.ChipTab, minTouchTarget = CouiMinTouchTarget, onClick = onToggle)
                .padding(horizontal = 6.dp, vertical = 4.dp),
        )
    }
}

/** 评论行：懒列表里的一行要么是一条评论，要么是它折起来的回复入口。 */
internal sealed interface CommentRow {
    /** 懒列表的稳定键：同一棵评论树内唯一。 */
    val key: String

    /** 懒列表的复用类型：评论与回复入口的行布局不同，分开复用避免互踩测量结果。 */
    val contentType: String

    /** 一条评论；[depth] 为回复层级，顶层评论为 0。 */
    data class Comment(val item: CommentItem, val depth: Int) : CommentRow {
        override val key: String get() = item.id
        override val contentType: String get() = "comment"
    }

    /** 「展开 N 条回复」行；[expanded] 表示这一支当前是否铺开。 */
    data class RepliesToggle(
        val parentId: String,
        val depth: Int,
        val hiddenCount: Int,
        val expanded: Boolean,
    ) : CommentRow {
        override val key: String get() = "reply-toggle-$parentId"
        override val contentType: String get() = "replies-toggle"
    }
}

/**
 * 把整棵评论树摊成懒列表的行序列：父评论先出，回复紧跟其后并逐层加深。
 *
 * 折叠态只由 [expandedIds] 决定：未展开的父评论只铺 [visibleReplies] 条回复；
 * 回复多于该阈值时始终补一行 [CommentRow.RepliesToggle]，展开后回复全出、该行换成「收起回复」。
 * 摊平结果只依赖入参，页面重组不会留下隐式的展开状态。
 *
 * 同一 id 只保留首次出现的那一棵子树：接口偶尔会在两页之间重复下发同一条评论，
 * 而懒列表的键必须唯一（重复键会直接抛 IllegalArgumentException）。
 */
internal fun flattenComments(
    roots: List<CommentItem>,
    expandedIds: Set<String> = emptySet(),
    visibleReplies: Int = COUI_COMMENT_VISIBLE_REPLIES,
): List<CommentRow> {
    val rows = ArrayList<CommentRow>()
    val seenIds = HashSet<String>()
    fun append(items: List<CommentItem>, depth: Int) {
        items.forEach { item ->
            if (!seenIds.add(item.id)) return@forEach
            rows += CommentRow.Comment(item, depth)
            val replies = item.replies
            if (replies.isEmpty()) return@forEach
            val expanded = item.id in expandedIds
            val shown = if (expanded) replies else replies.take(visibleReplies)
            append(shown, depth + 1)
            if (replies.size > visibleReplies) {
                rows += CommentRow.RepliesToggle(
                    parentId = item.id,
                    depth = depth,
                    hiddenCount = replies.size - visibleReplies,
                    expanded = expanded,
                )
            }
        }
    }
    append(roots, 0)
    return rows
}

/** 把预先拼好的懒列表内容装进当前列表作用域：窄屏单栏与宽屏两栏共用同一份构建器。 */
private fun LazyListScope.couiInclude(content: LazyListScope.() -> Unit) {
    content(this)
}
