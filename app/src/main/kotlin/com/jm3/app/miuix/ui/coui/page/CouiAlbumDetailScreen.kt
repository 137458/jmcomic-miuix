// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.page

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.jm3.app.miuix.data.download.DownloadManager
import com.jm3.app.miuix.ui.AlbumCoverImage
import com.jm3.app.miuix.ui.AlbumDetailHeroFlightEffect
import com.jm3.app.miuix.ui.AlbumDetailStateHolder
import com.jm3.app.miuix.ui.AlbumUiState
import com.jm3.app.miuix.ui.CommentsUiState
import com.jm3.app.miuix.ui.GlassSnackbarHost
import com.jm3.app.miuix.ui.HERO_COVER_HEIGHT_DP
import com.jm3.app.miuix.ui.HERO_COVER_WIDTH_DP
import com.jm3.app.miuix.ui.LocalAlbumDetailRootCoordinates
import com.jm3.app.miuix.ui.LocalAlbumHeroHost
import com.jm3.app.miuix.ui.LocalWideLayout
import com.jm3.app.miuix.ui.SessionCache
import com.jm3.app.miuix.ui.StringSetSaver
import com.jm3.app.miuix.ui.album.rememberAlbumDetailScaffoldState
import com.jm3.app.miuix.ui.albumHeroHeader
import com.jm3.app.miuix.ui.components.COMMENT_REPLY_MAX_LENGTH
import com.jm3.app.miuix.ui.coui.component.COUI_COMMENT_MAX_INDENT_DEPTH
import com.jm3.app.miuix.ui.coui.component.CouiBodyText
import com.jm3.app.miuix.ui.coui.component.CouiCommentFieldShape
import com.jm3.app.miuix.ui.coui.component.CouiCommentRow
import com.jm3.app.miuix.ui.coui.component.CouiDetailActionTile
import com.jm3.app.miuix.ui.coui.component.CouiDetailButtonShape
import com.jm3.app.miuix.ui.coui.component.CouiDetailSplitHandle
import com.jm3.app.miuix.ui.coui.component.CouiEmptyGroup
import com.jm3.app.miuix.ui.coui.component.CouiGroupRow
import com.jm3.app.miuix.ui.coui.component.CouiGroupTitle
import com.jm3.app.miuix.ui.coui.component.CouiHeaderCapsuleAction
import com.jm3.app.miuix.ui.coui.component.CouiLayoutTokens
import com.jm3.app.miuix.ui.coui.component.CouiLoadMoreFooter
import com.jm3.app.miuix.ui.coui.component.CouiLoadingGroup
import com.jm3.app.miuix.ui.coui.component.CouiMinTouchTarget
import com.jm3.app.miuix.ui.coui.component.CouiPageScaffold
import com.jm3.app.miuix.ui.coui.component.CouiPageTopBarAction
import com.jm3.app.miuix.ui.coui.component.CouiPrimaryActionButton
import com.jm3.app.miuix.ui.coui.component.CouiScreenFailureGroup
import com.jm3.app.miuix.ui.coui.component.CouiSectionTitleText
import com.jm3.app.miuix.ui.coui.component.CouiSelectedContainerAlpha
import com.jm3.app.miuix.ui.coui.component.CouiTagCapsulePill
import com.jm3.app.miuix.ui.coui.component.couiDialogSurfaceBorder
import com.jm3.app.miuix.ui.coui.component.couiDialogSurfaceColor
import com.jm3.app.miuix.ui.coui.component.couiPageScroll
import com.jm3.app.miuix.ui.coui.component.couiPressable
import com.jm3.app.miuix.ui.coui.component.isCouiWideWindow
import com.jm3.app.miuix.ui.coui.component.panelBlur
import com.jm3.app.miuix.ui.coui.component.rememberCouiBodyTextStyle
import com.jm3.app.miuix.ui.coui.component.rememberCouiPageScrollBehavior
import com.jm3.app.miuix.ui.coui.component.rememberRetainedTarget
import com.jm3.app.miuix.ui.coui.icon.CouiIcons
import com.jm3.app.miuix.ui.coui.theme.CouiStrings
import com.jm3.app.miuix.ui.coui.theme.CouiTheme
import com.jm3.app.miuix.ui.readableWidth
import com.jm3.app.miuix.ui.rememberReaderImageLoader
import com.jm3.app.miuix.ui.rememberSavedDialogDraft
import com.jm3.app.miuix.ui.route.AppRoutes
import com.jm3.app.miuix.ui.skin.LocalSkin
import com.jm3.core.AlbumPreviewPlan
import com.jm3.core.Jm3Gateway
import com.jm3.core.download.DownloadStore
import com.jm3.core.history.HistoryRepository
import com.jm3.core.model.AlbumDetail
import com.jm3.core.model.CommentItem
import com.jm3.core.model.SearchResult
import com.jm3.core.model.SearchScope
import com.jm3.core.session.AccountManager
import com.jm3.core.session.AuthCall
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.Button
import io.github.suqi8.coui.kmp.basic.ButtonDefaults
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardListGroup
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.blur.MaterialBlurDefaults
import io.github.suqi8.coui.kmp.layout.DialogButtonBar
import io.github.suqi8.coui.kmp.layout.DialogButtonBarAction
import io.github.suqi8.coui.kmp.layout.DialogDefaults
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.rememberCOUIFlingBehavior
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
    nav: AppRoutes,
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
            onOpenReader = nav::readerAt,
            onOpenAlbum = nav::album,
            onOpenSearch = nav::search,
            onOpenLogin = nav::login,
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
    // 预览图与阅读页共用同一套图片加载器（含同一份缓存），避免再建第二个 loader
    val previewImageLoader = rememberReaderImageLoader(gateway, downloadFiles, authCall)

    // 会话、快照、收藏夹恢复、预览取图与下载/分享动作的编排与 Miuix 皮肤共用同一份
    val scaffold = rememberAlbumDetailScaffoldState(
        albumId = albumId,
        initialTitle = initialTitle,
        initialCoverUrl = initialCoverUrl,
        snackbarHostState = snackbarHostState,
        gateway = gateway,
        authCall = authCall,
        history = history,
        account = account,
        sessionCache = sessionCache,
        downloadManager = downloadManager,
        onOpenLogin = onOpenLogin,
        shareHost = { gateway.usedDomain() },
    )
    val stateHolder = scaffold.stateHolder
    val uiState = scaffold.uiState
    val user = scaffold.user
    val loadedDetail = scaffold.loadedDetail
    val previewPlan = scaffold.previewPlan
    val currentTitle = scaffold.currentTitle
    val resumeId = scaffold.resumeId
    val effectiveTitle = scaffold.effectiveTitle
    val effectiveCoverUrl = scaffold.effectiveCoverUrl

    // 预测性返回：封面逆向飞行的编排（退出进度 → 起飞/推进/收场），详情页渐隐淡出，位移由共享封面主导
    AlbumDetailHeroFlightEffect(albumId)
    // 评论折叠态由页面持有：摊平只认集合里的 id，展开/收起只重算行表
    var expandedReplyIds by rememberSaveable(albumId, stateSaver = StringSetSaver) {
        mutableStateOf(emptySet<String>())
    }
    val commentFeed = (uiState.commentsState as? CommentsUiState.Loaded)?.feed
    val commentRows = remember(commentFeed?.items, expandedReplyIds) {
        flattenComments(commentFeed?.items.orEmpty(), expandedReplyIds)
    }

    // 宽屏两栏：左栏作品信息与章节，右栏评论；两栏各自持有滚动状态，互不牵连
    val wideLayout = LocalWideLayout.current
    val wideDetail = wideLayout.twoPaneDetail
    val singleListState = rememberLazyListState()
    val mainListState = rememberLazyListState()
    val commentListState = rememberLazyListState()
    val scrollBehavior = rememberCouiPageScrollBehavior()
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
            CouiPageScaffold(
                title = currentTitle,
                onBack = onBack,
                wide = isCouiWideWindow(),
                actions = {
                    if (loadedDetail != null) {
                        CouiPageTopBarAction(
                            icon = CouiIcons.Share,
                            contentDescription = "分享",
                            onClick = { scaffold.share(loadedDetail) },
                        )
                        if (loadedDetail.chapters.size > 1) {
                            CouiPageTopBarAction(
                                icon = CouiIcons.Download,
                                contentDescription = "下载全部",
                                onClick = { scaffold.downloadAll(loadedDetail.id, loadedDetail.chapters) },
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                horizontalPadding = null,
            ) { contentPadding ->
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
                            onCopyId = { copiedId -> scaffold.copyText("禁漫车号", copiedId) },
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
                                fun() { scaffold.downloadAll(detail.id, detail.chapters) }
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
                                        if (!uiState.favoriteBusy) scaffold.favorite()
                                    },
                                    onLike = {
                                        if (!uiState.likeBusy) scaffold.like()
                                    },
                                    onTrack = {
                                        if (!uiState.trackingBusy) scaffold.track()
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
                                        onCopy = { scaffold.copyText("简介", detail.description) },
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
                            onDownload = { chapter, title -> scaffold.downloadChapter(chapter, detail.id, title) },
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
                                onCopy = { label, value -> scaffold.copyText(label, value) },
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
                                    if (row.depth == 0) HorizontalDivider()
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
                Box(modifier = Modifier.fillMaxSize()) {
                    if (wideDetail) {
                        // 宽屏两栏：左栏是「这部作品是什么」（头部 + 操作 + 简介 + 信息），
                        // 右栏是「读什么、大家怎么说」（预览 + 章节 + 相关 + 评论）；两栏各自滚动、互不牵扯
                        // 比例随本页保存：拖过分隔条后重进本页仍记得，双击分隔条回到默认比例
                        var splitFraction by rememberSaveable {
                            mutableStateOf(wideLayout.defaultDetailSplitFraction)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .readableWidth(),
                        ) {
                            LazyColumn(
                                state = mainListState,
                                modifier = Modifier
                                    .weight(splitFraction)
                                    .fillMaxHeight()
                                    .couiPageScroll(),
                                contentPadding = contentPadding,
                                flingBehavior = rememberCOUIFlingBehavior(),
                            ) {
                                couiInclude(headItems)
                                couiInclude(infoItems)
                            }
                            CouiDetailSplitHandle(
                                fraction = splitFraction,
                                defaultFraction = wideLayout.defaultDetailSplitFraction,
                                availableWidthDp = wideLayout.contentWidthDp,
                                onFractionChange = { splitFraction = it },
                            )
                            LazyColumn(
                                state = commentListState,
                                modifier = Modifier
                                    .weight(1f - splitFraction)
                                    .fillMaxHeight()
                                    .couiPageScroll(),
                                contentPadding = contentPadding,
                                flingBehavior = rememberCOUIFlingBehavior(),
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
                                .couiPageScroll(),
                            contentPadding = contentPadding,
                            flingBehavior = rememberCOUIFlingBehavior(),
                        ) {
                            couiInclude(headItems)
                            couiInclude(previewItems)
                            couiInclude(chapterItems)
                            couiInclude(infoItems)
                            couiInclude(relatedItems)
                            couiInclude(commentItems)
                        }
                    }

                    // 收藏夹选择面板：置于 CouiPageScaffold 内侧、LazyColumn 外侧，继承页面模糊源与弹窗宿主
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

            // 页面内的 Snackbar 需要真实底栏上方留白，GlassSnackbarHost 内部会补这段差额
            GlassSnackbarHost(
                state = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
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
    val colors = COUITheme.colorScheme
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 34.dp),
        enabled = enabled,
        minWidth = 0.dp,
        minHeight = 34.dp,
        colors = if (accent) {
            ButtonDefaults.buttonColorsPrimary(
                color = colors.primary,
                disabledColor = colors.labelPrimary.copy(alpha = 0.06f),
                contentColor = colors.onPrimary,
                disabledContentColor = colors.disabledNeutral,
            )
        } else {
            ButtonDefaults.buttonColors(
                color = colors.labelPrimary.copy(alpha = 0.08f),
                disabledColor = colors.labelPrimary.copy(alpha = 0.06f),
                contentColor = colors.labelPrimary,
                disabledContentColor = colors.disabledNeutral,
            )
        },
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 7.dp),
        materialBackdrop = false,
    ) {
        Text(
            text = text,
            style = COUITheme.textStyles.buttonM,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 头部信息卡：3:4 封面 + 标题 + 作者胶囊 + 可复制车号 + 结构化热度指标 + 标签胶囊。
 *
 * 封面参与转场飞行，保留 [albumHeroHeader] 与皮肤圆角，外层使用 COUI 17 [Card]。
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
    val dark = COUITheme.isDark
    val colors = COUITheme.colorScheme
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
    val metaChipBg = colors.labelPrimary.copy(alpha = if (dark) 0.08f else 0.05f)
    val metaChipBorder = colors.labelPrimary.copy(alpha = if (dark) 0.12f else 0.07f)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CouiLayoutTokens.HorizontalMargin)
            .padding(top = CouiLayoutTokens.CategoryTopSpacing),
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
                        .background(colors.container4)
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
                    color = colors.labelPrimary,
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
                                shape = CircleShape,
                                backgroundColor = colors.primary.copy(alpha = if (dark) 0.18f else CouiSelectedContainerAlpha),
                                scaleOnPress = true,
                                onClick = { onOpenSearch(authors.first(), SearchScope.AUTHOR) },
                            )
                            .border(0.5.dp, colors.primary.copy(alpha = if (dark) 0.34f else 0.22f), CircleShape)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = authors.joinToString("、"),
                            style = COUITheme.textStyles.description,
                            color = colors.primaryText,
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
                            shape = CircleShape,
                            backgroundColor = metaChipBg,
                            scaleOnPress = true,
                            onClick = { onCopyId(displayId) },
                        )
                        .border(0.5.dp, metaChipBorder, CircleShape)
                        .padding(horizontal = 9.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        text = "JM #$displayId",
                        style = COUITheme.textStyles.description,
                        color = colors.labelSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Start,
                    )
                    Icon(
                        imageVector = CouiIcons.Copy,
                        contentDescription = "复制禁漫车号",
                        modifier = Modifier.size(12.dp),
                        tint = colors.labelSecondary,
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
                                    .clip(CircleShape)
                                    .background(metaChipBg)
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                            ) {
                                Text(
                                    text = stat,
                                    style = COUITheme.textStyles.bodyXS,
                                    color = colors.labelSecondary,
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
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
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
    val startPhotoId = resumeId ?: detail.chapters.firstOrNull()?.id
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CouiLayoutTokens.HorizontalMargin)
            .padding(top = CouiLayoutTokens.CategoryTopSpacing),
        insideMargin = PaddingValues(12.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
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
}

/** 简介：默认折叠 4 行，超长才给展开入口；复制按钮放在分组标题行，避免正文被按钮切碎。 */
@Composable
private fun CouiDescriptionGroup(
    text: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    onCopy: () -> Unit,
) {
    val colors = COUITheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth()) {
        CouiGroupTitle(
            title = "简介",
            actions = {
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
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CouiLayoutTokens.HorizontalMargin),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                CouiBodyText(
                    text = text,
                    color = colors.labelPrimary,
                    maxLines = if (expanded) Int.MAX_VALUE else 4,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Start,
                )
            }
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
    val colors = COUITheme.colorScheme
    val authors = detail.authors.map { it.trim() }.filter { it.isNotBlank() }
    val works = detail.works.map { it.trim() }.filter { it.isNotBlank() }
    val actors = detail.actors.map { it.trim() }.filter { it.isNotBlank() }
    val likes = detail.likes?.takeIf { it.isNotBlank() && it != "0" }
    val views = detail.views?.takeIf { it.isNotBlank() && it != "0" }
    Column(modifier = Modifier.fillMaxWidth()) {
        CouiGroupTitle(title = "作品信息")
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CouiLayoutTokens.HorizontalMargin),
        ) {
            CardListGroup {
                if (authors.isNotEmpty()) {
                    item(key = "authors") {
                        CouiInfoRow(
                            title = "作者",
                            value = authors.joinToString("、"),
                            onClick = { onOpenSearch(authors.first(), SearchScope.AUTHOR) },
                        )
                    }
                }
                if (works.isNotEmpty()) {
                    item(key = "works") {
                        CouiInfoRow(
                            title = "作品",
                            value = works.joinToString("、"),
                            onClick = { onOpenSearch(works.first(), SearchScope.WORK) },
                        )
                    }
                }
                if (actors.isNotEmpty()) {
                    item(key = "actors") {
                        CouiInfoRow(
                            title = "登场人物",
                            value = actors.joinToString("、"),
                            onClick = { onOpenSearch(actors.first(), SearchScope.ACTOR) },
                        )
                    }
                }
                item(key = "id") {
                    BasicComponent(
                        title = "禁漫车号",
                        endActions = {
                            Text(
                                text = "#$displayId",
                                style = COUITheme.textStyles.bodyM,
                                color = colors.labelTertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.End,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = CouiIcons.Copy,
                                contentDescription = "复制车号",
                                modifier = Modifier.size(18.dp),
                                tint = colors.primaryText,
                            )
                        },
                        onClick = { onCopy("禁漫车号", displayId) },
                    )
                }
                if (detail.pageCount > 0) {
                    item(key = "pages") {
                        CouiInfoRow(title = "页数", value = "${detail.pageCount} 页")
                    }
                }
                if (likes != null) {
                    item(key = "likes") {
                        CouiInfoRow(title = "喜欢", value = likes)
                    }
                }
                if (views != null) {
                    item(key = "views") {
                        CouiInfoRow(title = "浏览", value = views)
                    }
                }
                if (detail.commentCount > 0) {
                    item(key = "comments") {
                        CouiInfoRow(title = "评论数", value = "${detail.commentCount} 条")
                    }
                }
            }
        }
    }
}

/** 只读信息行；onClick 非空时使用 [ArrowPreference] 提示可跳转。 */
@Composable
private fun CouiInfoRow(
    title: String,
    value: String,
    onClick: (() -> Unit)? = null,
) {
    if (onClick != null) {
        ArrowPreference(
            title = title,
            endActions = {
                Text(
                    text = value,
                    style = COUITheme.textStyles.bodyM,
                    color = COUITheme.colorScheme.labelTertiary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                )
            },
            onClick = onClick,
        )
    } else {
        BasicComponent(
            title = title,
            endActions = {
                Text(
                    text = value,
                    style = COUITheme.textStyles.bodyM,
                    color = COUITheme.colorScheme.labelTertiary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                )
            },
        )
    }
}

/** 预览：首章前几页缩略图，点第 i 张直达阅读器第 i 页。 */
@Composable
private fun CouiPreviewGroup(
    plan: AlbumPreviewPlan,
    imageLoader: ImageLoader,
    onOpenPage: (page: Int) -> Unit,
) {
    val colors = COUITheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth()) {
        CouiGroupTitle(title = "预览")
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CouiLayoutTokens.HorizontalMargin),
        ) {
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
                            .background(colors.container4)
                            .couiPressable(shape = previewShape) {
                                onOpenPage(page.index + 1)
                            },
                    )
                }
            }
        }
    }
}

/** 相关作品：横向作品卡，容器为 [Card]，卡内只保留封面与两行文字。 */
@Composable
private fun CouiRelatedGroup(
    related: List<SearchResult>,
    onOpenAlbum: (albumId: String) -> Unit,
) {
    val colors = COUITheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth()) {
        CouiGroupTitle(title = "更多相关")
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CouiLayoutTokens.HorizontalMargin),
        ) {
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
                            .couiPressable(shape = itemShape) {
                                onOpenAlbum(item.id)
                            },
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 104.dp, height = 138.dp)
                                // 缩略图不参与飞行，圆角取皮肤的控制件圆角（ENGINEERING.md:93）
                                .clip(RoundedCornerShape(LocalSkin.current.shapes.controlCorner))
                                .background(colors.container4),
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
                            style = COUITheme.textStyles.bodyM,
                            color = colors.labelPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Start,
                        )
                        if (subtitle.isNotBlank()) {
                            Text(
                                text = subtitle,
                                style = COUITheme.textStyles.bodyXS,
                                color = colors.labelSecondary,
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
    val colors = COUITheme.colorScheme
    if (!loggedIn) {
        ArrowPreference(
            title = "登录后发表评论",
            summary = "登录账号即可参与讨论",
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
            textStyle = rememberCouiBodyTextStyle().copy(color = colors.labelPrimary),
            cursorBrush = SolidColor(colors.primary),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 84.dp)
                .clip(CouiCommentFieldShape)
                .background(colors.container4)
                .border(0.5.dp, colors.divider, CouiCommentFieldShape)
                .padding(12.dp),
            decorationBox = { innerTextField ->
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (text.isEmpty()) {
                        CouiBodyText(
                            text = "写下你的评论（最多 $COMMENT_REPLY_MAX_LENGTH 字）",
                            color = colors.labelSecondary,
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
                style = COUITheme.textStyles.bodyXS,
                color = colors.labelSecondary,
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
    val blur = panelBlur(MaterialBlurDefaults::dialog)
    OverlayDialog(
        show = visible,
        title = if (favorited) "移动收藏夹" else "选择收藏夹",
        backgroundModifier = blur?.backgroundModifier ?: Modifier,
        backgroundColor = blur?.backgroundColor ?: DialogDefaults.backgroundColor(),
        materialStroke = blur != null,
        onDismissRequest = onDismiss,
        customContent = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
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
        },
    ) {
        DialogButtonBar(
            negative = null,
            positive = DialogButtonBarAction(text = "关闭", recommended = true, onClick = onDismiss),
        )
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
    val colors = COUITheme.colorScheme
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
                style = COUITheme.textStyles.headlineXS,
                color = if (danger) colors.error else colors.labelPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start,
            )
            if (summary != null) {
                Text(
                    text = summary,
                    style = COUITheme.textStyles.bodyS,
                    color = colors.labelSecondary,
                    maxLines = 1,
                    textAlign = TextAlign.Start,
                )
            }
        }
        Icon(
            imageVector = CouiIcons.Forward,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = colors.labelTertiary,
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
    val colors = COUITheme.colorScheme
    val blur = panelBlur(MaterialBlurDefaults::dialog)
    val retainedAuthor = rememberRetainedTarget(targetAuthor?.takeIf { it.isNotBlank() })
    var replyText by rememberSavedDialogDraft(isOpen = visible, targetKey = targetKey ?: retainedAuthor)
    OverlayDialog(
        show = visible,
        title = if (retainedAuthor != null) "回复 $retainedAuthor" else "回复",
        backgroundModifier = blur?.backgroundModifier ?: Modifier,
        backgroundColor = blur?.backgroundColor ?: DialogDefaults.backgroundColor(),
        materialStroke = blur != null,
        onDismissRequest = onDismiss,
        customContent = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            ) {
                BasicTextField(
                    value = replyText,
                    onValueChange = { replyText = couiClampCommentInput(it) },
                    textStyle = rememberCouiBodyTextStyle().copy(color = colors.labelPrimary),
                    cursorBrush = SolidColor(colors.primary),
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
                                    color = colors.labelSecondary,
                                    textAlign = TextAlign.Start,
                                )
                            }
                            innerTextField()
                        }
                    },
                )
                Text(
                    text = "${replyText.length}/$COMMENT_REPLY_MAX_LENGTH",
                    style = COUITheme.textStyles.bodyXS,
                    color = colors.labelSecondary,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        },
    ) {
        DialogButtonBar(
            negative = DialogButtonBarAction(text = "取消", onClick = onDismiss),
            positive = DialogButtonBarAction(
                text = if (busy) "发送中…" else "发送",
                enabled = !busy && replyText.isNotBlank(),
                recommended = true,
                onClick = { onSend(replyText) },
            ),
        )
    }
}

/** 折叠回复的入口：单独成行，缩进跟着父评论走；展开态只影响这一条父评论。 */
@Composable
private fun CouiRepliesToggle(
    row: CommentRow.RepliesToggle,
    onToggle: () -> Unit,
) {
    val colors = COUITheme.colorScheme
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
            style = COUITheme.textStyles.buttonM,
            color = colors.primaryText,
            maxLines = 1,
            textAlign = TextAlign.Start,
            modifier = Modifier
                .clip(CircleShape)
                .couiPressable(shape = CircleShape, minTouchTarget = CouiMinTouchTarget, onClick = onToggle)
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
