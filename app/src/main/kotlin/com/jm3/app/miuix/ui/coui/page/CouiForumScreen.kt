// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.page

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.ui.AlbumCoverImage
import com.jm3.app.miuix.ui.GlassSnackbarHost
import com.jm3.app.miuix.ui.components.COMMENT_REPLY_MAX_LENGTH
import com.jm3.app.miuix.ui.components.rememberSelectionHaptic
import com.jm3.app.miuix.ui.coui.component.CouiBodyText
import com.jm3.app.miuix.ui.coui.component.CouiCommentFieldShape
import com.jm3.app.miuix.ui.coui.component.CouiCommentRow
import com.jm3.app.miuix.ui.coui.component.CouiEmptyGroup
import com.jm3.app.miuix.ui.coui.component.CouiFeedRefreshAction
import com.jm3.app.miuix.ui.coui.component.CouiGroupRow
import com.jm3.app.miuix.ui.coui.component.CouiLayoutTokens
import com.jm3.app.miuix.ui.coui.component.CouiLoadMoreFooter
import com.jm3.app.miuix.ui.coui.component.CouiLoadingGroup
import com.jm3.app.miuix.ui.coui.component.CouiMinTouchTarget
import com.jm3.app.miuix.ui.coui.component.CouiPageScaffold
import com.jm3.app.miuix.ui.coui.component.CouiPageTopBarAction
import com.jm3.app.miuix.ui.coui.component.CouiPageTopBarSegmentedTabs
import com.jm3.app.miuix.ui.coui.component.CouiPagingGroup
import com.jm3.app.miuix.ui.coui.component.CouiPullToRefresh
import com.jm3.app.miuix.ui.coui.component.CouiScreenFailureGroup
import com.jm3.app.miuix.ui.coui.component.couiDialogSurfaceColor
import com.jm3.app.miuix.ui.coui.component.couiPageScroll
import com.jm3.app.miuix.ui.coui.component.couiPressable
import com.jm3.app.miuix.ui.coui.component.couiWorkCardShape
import com.jm3.app.miuix.ui.coui.component.isCouiWideWindow
import com.jm3.app.miuix.ui.coui.component.panelBlur
import com.jm3.app.miuix.ui.coui.component.rememberCouiBodyTextStyle
import com.jm3.app.miuix.ui.coui.component.rememberCouiPageScrollBehavior
import com.jm3.app.miuix.ui.coui.component.rememberRetainedTarget
import com.jm3.app.miuix.ui.coui.icon.CouiIcons
import com.jm3.app.miuix.ui.coui.theme.CouiStrings
import com.jm3.app.miuix.ui.coui.theme.CouiTheme
import com.jm3.app.miuix.ui.forum.FORUM_LOAD_FAILED
import com.jm3.app.miuix.ui.forum.FORUM_PAGE_SIZE
import com.jm3.app.miuix.ui.forum.FORUM_RULES_HINT
import com.jm3.app.miuix.ui.forum.FORUM_RULES_TEXT
import com.jm3.app.miuix.ui.forum.FORUM_STALE_PREFIX
import com.jm3.app.miuix.ui.forum.forumEmptyHint
import com.jm3.app.miuix.ui.forum.forumEmptyTitle
import com.jm3.app.miuix.ui.forum.forumSlice
import com.jm3.app.miuix.ui.forum.rememberForumScaffoldState
import com.jm3.app.miuix.ui.list.ListLoadState
import com.jm3.app.miuix.ui.list.SessionListLoader
import com.jm3.app.miuix.ui.readableWidth
import com.jm3.app.miuix.ui.rememberSavedDialogDraft
import com.jm3.app.miuix.ui.rememberSyncedTabPagerState
import com.jm3.app.miuix.ui.route.AppRoutes
import com.jm3.core.appendedBy
import com.jm3.core.model.CommentItem
import com.jm3.core.model.CommentPage
import com.jm3.core.model.ForumScope
import com.jm3.core.paging.PageSlice
import com.jm3.core.paging.PagedFeed
import com.jm3.core.paging.pageCountOf
import com.jm3.core.replacedBy
import com.jm3.core.session.SessionOutcome
import com.jm3.core.settings.PaginationMode
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.blur.MaterialBlurDefaults
import io.github.suqi8.coui.kmp.layout.DialogButtonBar
import io.github.suqi8.coui.kmp.layout.DialogButtonBarAction
import io.github.suqi8.coui.kmp.layout.DialogDefaults
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.preference.PagerFooterPreference
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.rememberCOUIFlingBehavior
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.SnackbarHostState

/** 论坛正文限行：超长楼层会铺满整屏并顶掉后续话题。 */
private const val FORUM_BODY_MAX_LINES = 8

/** 回复缩进层级上限：再深不再吃正文宽度。 */
private const val FORUM_MAX_INDENT_DEPTH = 2

/**
 * COUI 社区论坛：全部/漫画评论/闲聊三档，支持发帖、回复与两种分页模式。
 *
 * 话题按行卡分组，楼层沿用账号头像 + 等级 + 徽章 + 分段正文的信息结构；
 * 总页数由接口 total 折算（接口本身不带页码），点击分页与滚动分页都保。
 */
@Composable
internal fun CouiForumScreen(
    deps: ScreenDeps,
    onBack: () -> Unit,
    nav: AppRoutes,
    snackbarHostState: SnackbarHostState,
) {
    val user by deps.account.user.collectAsState()
    val pageMode = deps.paginationMode
    val selectionHaptic = rememberSelectionHaptic()
    // 档位、回复/发帖面板与规则卡的状态编排在共享骨架里：两套皮肤原先各写一份
    val forum = rememberForumScaffoldState(
        deps = deps,
        nav = nav,
        loggedIn = user != null,
        notify = { message -> snackbarHostState.showSnackbar(message) },
        failureFallback = CouiStrings.RetryLater,
    )
    val scope = forum.scope
    val listStates = remember { List(ForumScope.entries.size) { LazyListState() } }
    val clampedScopeIndex = forum.clampedScopeIndex
    // 重试页码：整屏失败时列表停在上一次成功的页，重试要回到真正失败的那一页
    val retryPages = remember(pageMode) { mutableMapOf<Int, Int>() }
    // 回复后要保留到当前页：先记下目标页，随后的 refresh 由 fetchPage 一次取回 1..N 页合并
    val pendingMerge = remember(pageMode) { mutableMapOf<Int, Int>() }
    // 每档一个列表装配器：页码、在途、代次与失败计划都收在 loader 里，页面只提供「取哪一页」
    val loaders = remember(pageMode, snackbarHostState) {
        ForumScope.entries.mapIndexed { tabIndex, forumScope ->
            SessionListLoader(
                scope = scope,
                idOf = { item: CommentItem -> item.id },
                failureMessage = FORUM_LOAD_FAILED,
                fetchPage = { page ->
                    retryPages[tabIndex] = page
                    val mergeThrough = pendingMerge.remove(tabIndex) ?: 0
                    if (mergeThrough > 1) {
                        // 回复后重载：并发取 1..N 页合成一页返回，对外仍是 loader 的单页替换
                        val outcomes = coroutineScope {
                            (1..mergeThrough)
                                .map { targetPage ->
                                    async { deps.authCall.call { deps.gateway.forum(forumScope, targetPage) } }
                                }
                                .awaitAll()
                        }
                        val failed = outcomes.firstOrNull { it !is SessionOutcome.Ok }
                        when (failed) {
                            null -> {
                                val pages = outcomes.map { (it as SessionOutcome.Ok).value }
                                val merged = mergeCouiForumPages(pages, activePage = mergeThrough)
                                SessionOutcome.Ok(
                                    PageSlice(
                                        items = merged.items,
                                        hasMore = merged.hasMore,
                                        total = merged.total,
                                        page = merged.page,
                                        pageCount = merged.pageCount,
                                    ),
                                )
                            }
                            is SessionOutcome.Failed -> failed
                            else -> SessionOutcome.Expired
                        }
                    } else {
                        when (val outcome = deps.authCall.call { deps.gateway.forum(forumScope, page) }) {
                            is SessionOutcome.Ok -> SessionOutcome.Ok(forumSlice(outcome.value, page))
                            SessionOutcome.Expired -> SessionOutcome.Expired
                            is SessionOutcome.Failed -> outcome
                        }
                    }
                },
                notice = { message -> scope.launch { snackbarHostState.showSnackbar(message) } },
                stalePrefix = FORUM_STALE_PREFIX,
            )
        }
    }
    val activeSnapshot by loaders[clampedScopeIndex].snapshot.collectAsState()
    // 每档只首屏加载一次；模式切换会重建 loader，等价于把已加载标记清空
    val startedTabs = remember(loaders) { mutableSetOf<Int>() }

    /**
     * 回复后重载：CLICK 模式或停在第 1 页时只换当前页；
     * 滚动模式停在更深的页时，先记下合并目标页再走 refresh，由 fetchPage 一次取回 1..N 页。
     */
    fun reloadAfterReply(tabIndex: Int, activePage: Int) {
        val safeActivePage = activePage.coerceAtLeast(1)
        val tabLoader = loaders[tabIndex]
        if (pageMode == PaginationMode.CLICK || safeActivePage <= 1) {
            tabLoader.goToPage(safeActivePage)
            return
        }
        pendingMerge[tabIndex] = safeActivePage
        tabLoader.refresh()
    }

    // 切档或换分页模式（loader 重建）时：没加载过的档位走首屏加载，已加载过的静默刷新
    LaunchedEffect(clampedScopeIndex, loaders) {
        val tabIndex = clampedScopeIndex
        val tabLoader = loaders[tabIndex]
        if (startedTabs.add(tabIndex)) {
            listStates[tabIndex].requestScrollToItem(0)
            tabLoader.loadFirstPage()
        } else {
            tabLoader.refresh()
        }
    }

    CouiTheme {
        val scrollBehavior = rememberCouiPageScrollBehavior {
            val active = listStates[clampedScopeIndex]
            active.canScrollForward || active.canScrollBackward
        }
        val pagerState = rememberSyncedTabPagerState(
            selectedTab = clampedScopeIndex,
            pageCount = ForumScope.entries.size,
            onTabSelected = {
                if (it != forum.scopeIndex) {
                    selectionHaptic()
                    forum.selectScope(it)
                }
            },
        )

        Box(modifier = Modifier.fillMaxSize()) {
            CouiPageScaffold(
                title = "社区论坛",
                onBack = onBack,
                wide = isCouiWideWindow(),
                scrollBehavior = scrollBehavior,
                actions = {
                    CouiPageTopBarAction(
                        icon = CouiIcons.Comment,
                        contentDescription = "发帖",
                        onClick = forum::openCompose,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                    CouiFeedRefreshAction(
                        refreshing = activeSnapshot.refreshing,
                        onRefresh = loaders[clampedScopeIndex]::refresh,
                    )
                },
                topBarBottom = {
                    CouiPageTopBarSegmentedTabs(
                        items = ForumScope.entries.map { it.label },
                        selectedIndex = clampedScopeIndex,
                        onSelect = {
                            if (it != forum.scopeIndex) {
                                selectionHaptic()
                                forum.selectScope(it)
                            }
                        },
                    )
                },
            ) { contentPadding ->
                Box(modifier = Modifier.fillMaxSize()) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                    ) { pageIdx ->
                        val pageScope = ForumScope.entries[pageIdx]
                        val pageLoader = loaders[pageIdx]
                        val pageSnapshot by pageLoader.snapshot.collectAsState()
                        val pageFeed = pageSnapshot.feed
                        val pageState = pageSnapshot.state
                        // 重试回真正失败的那一页：整屏失败时列表还停在上一次成功的页
                        val pageFailedAt = retryPages[pageIdx] ?: 1
                        val pageListState = listStates[pageIdx]
                        val pageItems = pageFeed.items
                        val pageTopicTotal = pageFeed.total ?: 0
                        val pageTotalPages = pageFeed.pageCount ?: pageFeed.page

                        CouiFeedPrefetchEffect(
                            feed = pageFeed,
                            rowStart = 0,
                            listState = pageListState,
                            enabled = pageMode != PaginationMode.CLICK &&
                                pageState == ListLoadState.Loaded &&
                                pageIdx == clampedScopeIndex,
                            onPrefetch = pageLoader::loadMore,
                        )

                        CouiPullToRefresh(
                            isRefreshing = pageSnapshot.refreshing,
                            onRefresh = pageLoader::refresh,
                            scrollBehavior = scrollBehavior,
                        ) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .readableWidth()
                                    .couiPageScroll()
                                    .consumeWindowInsets(contentPadding),
                                state = pageListState,
                                contentPadding = contentPadding,
                                flingBehavior = rememberCOUIFlingBehavior(),
                            ) {
                                when (pageState) {
                                    ListLoadState.Loading -> item(key = "forum-loading") {
                                        CouiLoadingGroup(
                                            modifier = Modifier.padding(top = 8.dp),
                                            text = "正在加载话题…",
                                        )
                                    }
                                    is ListLoadState.Failed -> item(key = "forum-error") {
                                        CouiScreenFailureGroup(
                                            message = pageState.message,
                                            expired = pageState.sessionExpired,
                                            onRetry = {
                                                // 首屏失败回第 1 页并给整屏加载态；翻页失败要回到失败的那一页
                                                if (pageFailedAt <= 1) {
                                                    pageLoader.loadFirstPage()
                                                } else {
                                                    pageLoader.goToPage(pageFailedAt)
                                                }
                                            },
                                            onOpenLogin = nav::login,
                                            modifier = Modifier.padding(top = 8.dp),
                                        )
                                    }
                                    ListLoadState.Loaded -> {
                                        // 闲聊档的规则卡：折叠态给提要，展开态给全文
                                        if (pageScope == ForumScope.CHAT) {
                                            item(key = "forum-rules") {
                                                CouiForumRulesRow(
                                                    expanded = forum.rulesExpanded,
                                                    onToggle = forum::toggleRules,
                                                )
                                            }
                                        }
                                        if (pageItems.isEmpty()) {
                                            item(key = "forum-empty") {
                                                CouiEmptyGroup(
                                                    text = forumEmptyTitle(pageScope),
                                                    modifier = Modifier.padding(top = 8.dp),
                                                )
                                            }
                                            item(key = "forum-empty-hint") {
                                                PagerFooterPreference(
                                                    summary = forumEmptyHint(pageScope),
                                                    withExtraMarginBottom = false,
                                                )
                                            }
                                            item(key = "forum-empty-action") {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(
                                                            horizontal = CouiLayoutTokens.HorizontalMargin,
                                                            vertical = 8.dp,
                                                        ),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    ForumCapsuleButton(text = "发布新帖", onClick = forum::openCompose)
                                                }
                                            }
                                        } else {
                                            if (pageTopicTotal > 0) {
                                                item(key = "forum-count") {
                                                    PagerFooterPreference(
                                                        summary = "共 " + pageTopicTotal + " 条话题",
                                                        withExtraMarginBottom = false,
                                                    )
                                                }
                                            }
                                            itemsIndexed(pageItems, key = { _, item -> "forum-" + item.id }) { index, item ->
                                                CouiGroupRow(
                                                    isFirst = index == 0,
                                                    isLast = index == pageItems.lastIndex,
                                                ) {
                                                    CouiForumTopic(
                                                        item = item,
                                                        onReply = forum::startReply,
                                                        onOpenAlbum = nav::album,
                                                    )
                                                }
                                            }
                                            item(key = "forum-footer") {
                                                if (pageMode == PaginationMode.CLICK) {
                                                    // 只有一页时翻页条没有信息量，不占位
                                                    if (pageTotalPages > 1) {
                                                        CouiPagingGroup(
                                                            page = pageFeed.page,
                                                            pageCount = pageTotalPages,
                                                            hasMore = pageFeed.hasMore,
                                                            onJumpPage = { target ->
                                                                if (target != pageFeed.page) {
                                                                    pageListState.requestScrollToItem(0)
                                                                    pageLoader.goToPage(target)
                                                                }
                                                            },
                                                            modifier = Modifier.padding(top = 8.dp),
                                                        )
                                                    }
                                                } else {
                                                    CouiLoadMoreFooter(
                                                        feed = pageFeed,
                                                        errorMessage = CouiStrings.LoadFailed,
                                                        onRetry = pageLoader::loadMore,
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

                    CouiForumInputDialog(
                        visible = forum.composing,
                        title = "发布新帖",
                        busy = forum.composeBusy,
                        confirmText = "发布",
                        busyConfirmText = "发布中…",
                        placeholder = "说点什么（最多 " + COMMENT_REPLY_MAX_LENGTH + " 字）",
                        onDismiss = forum::dismissCompose,
                        onSend = { text ->
                            forum.postTopic(text) {
                                // 新帖落在第一页首位，回到顶部才能看到刚发的内容
                                val activeTab = forum.clampedScopeIndex
                                listStates[activeTab].requestScrollToItem(0)
                                loaders[activeTab].goToPage(1)
                            }
                        },
                    )
                    val retainedReplyAuthor = rememberRetainedTarget(forum.replyTarget?.author?.takeIf { it.isNotBlank() })
                    CouiForumInputDialog(
                        visible = forum.replyTarget != null,
                        title = if (retainedReplyAuthor != null) "回复 $retainedReplyAuthor" else "回复",
                        targetKey = forum.replyTarget?.id,
                        busy = forum.replyBusy,
                        confirmText = "发送",
                        busyConfirmText = "发送中…",
                        placeholder = "写下你的回复（最多 " + COMMENT_REPLY_MAX_LENGTH + " 字）",
                        onDismiss = forum::dismissReply,
                        onSend = { text ->
                            forum.sendReply(text) {
                                reloadAfterReply(forum.clampedScopeIndex, loaders[forum.clampedScopeIndex].feed.page)
                            }
                        },
                    )
                }
            }

            GlassSnackbarHost(
                state = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
            )
        }
    }
}

/**
 * 闲聊档规则行：折叠只给提要，展开给全文；整行可点，行尾给出动作提示。
 */
@Composable
private fun CouiForumRulesRow(expanded: Boolean, onToggle: () -> Unit) {
    val scheme = COUITheme.colorScheme
    CouiGroupRow(isFirst = true, isLast = true) {
        val shape = couiWorkCardShape()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .couiPressable(shape = shape, minTouchTarget = CouiMinTouchTarget, onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "社区规则",
                    style = COUITheme.textStyles.headlineXS,
                    color = scheme.labelPrimary,
                    maxLines = 1,
                    textAlign = TextAlign.Start,
                )
                Text(
                    text = if (expanded) FORUM_RULES_TEXT else FORUM_RULES_HINT,
                    style = COUITheme.textStyles.bodyS,
                    color = scheme.labelSecondary,
                    textAlign = TextAlign.Start,
                )
            }
            Text(
                text = if (expanded) "收起" else "展开",
                style = COUITheme.textStyles.bodyM,
                color = scheme.primaryText,
                maxLines = 1,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

/**
 * 单条话题：楼层内容 + 关联作品入口。
 *
 * 楼层按扁平化后的行渲染，回复带缩进竖线但不递归，避免深层回复把正文挤成一条。
 */
@Composable
private fun CouiForumTopic(
    item: CommentItem,
    onReply: (CommentItem) -> Unit,
    onOpenAlbum: (String) -> Unit,
) {
    val scheme = COUITheme.colorScheme
    val albumId = item.albumId
        ?: item.albumName?.filter { it.isDigit() }?.takeIf { it.isNotBlank() }
    val albumName = item.albumName?.takeIf { albumId != null }
    Column(modifier = Modifier.fillMaxWidth()) {
        CouiCommentRow(item = item, depth = 0, bodyMaxLines = FORUM_BODY_MAX_LINES, onReply = onReply)
        val replies = remember(item) { flattenForumReplies(item) }
        replies.forEach { (reply, depth) ->
            CouiCommentRow(item = reply, depth = depth, bodyMaxLines = FORUM_BODY_MAX_LINES, onReply = onReply)
        }
        if (albumName != null && albumId != null) {
            val rowShape = remember { SquircleShape(10.dp, 3f) }
            val coverShape = remember { SquircleShape(8.dp, 3f) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
                    .clip(rowShape)
                    .background(scheme.container4)
                    .couiPressable(shape = rowShape, onClick = { onOpenAlbum(albumId) })
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .width(48.dp)
                        .height(64.dp)
                        .clip(coverShape)
                        .background(scheme.card),
                ) {
                    AlbumCoverImage(
                        url = item.albumCoverUrl,
                        contentDescription = albumName,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "相关作品",
                        style = COUITheme.textStyles.bodyS,
                        color = scheme.labelSecondary,
                        maxLines = 1,
                        textAlign = TextAlign.Start,
                    )
                    Text(
                        text = albumName,
                        style = COUITheme.textStyles.bodyM,
                        color = scheme.labelPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Start,
                    )
                }
                Icon(
                    imageVector = CouiIcons.Forward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = scheme.labelSecondary,
                )
            }
        }
    }
}

/** 楼层扁平化：只保留两层缩进，更深的回复按最深层级展示，避免正文被挤成一条。 */
internal fun flattenForumReplies(root: CommentItem): List<Pair<CommentItem, Int>> {
    val rows = mutableListOf<Pair<CommentItem, Int>>()
    fun walk(item: CommentItem, depth: Int) {
        for (reply in item.replies) {
            val next = depth.coerceAtMost(FORUM_MAX_INDENT_DEPTH)
            rows.add(reply to next)
            walk(reply, depth + 1)
        }
    }
    walk(root, 1)
    return rows
}

/** 评论页没有页码字段：页码与总页数由请求页和 total 补上，与 Miuix 皮肤共用同一份折算。 */
internal fun couiForumSlice(page: CommentPage, requestPage: Int): PageSlice<CommentItem> =
    forumSlice(page, requestPage)

/** 无限滚动回复后重拉已加载的 1..activePage 页，保留前序页面条目与游标。 */
internal fun mergeCouiForumPages(
    pages: List<CommentPage>,
    activePage: Int,
    pageSize: Int = FORUM_PAGE_SIZE,
): PagedFeed<CommentItem> {
    if (pages.isEmpty()) return PagedFeed()
    var merged = PagedFeed<CommentItem>().replacedBy(pages.first())
    for (index in 1 until pages.size) {
        merged = merged.appendedBy(pages[index])
    }
    val last = pages.last()
    val pageCount = pageCountOf(last.total, pageSize)
    return merged.copy(page = activePage.coerceAtLeast(1), pageCount = pageCount)
}

/** 轻量胶囊按钮：空态里的"发布新帖"入口。 */
@Composable
private fun ForumCapsuleButton(text: String, onClick: () -> Unit) {
    val scheme = COUITheme.colorScheme
    Box(
        modifier = Modifier
            .couiPressable(
                shape = CircleShape,
                backgroundColor = scheme.primary,
                minTouchTarget = CouiMinTouchTarget,
                onClick = onClick,
            )
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = COUITheme.textStyles.buttonM,
            color = scheme.onPrimary,
        )
    }
}

/**
 * 发帖 / 回复共用的输入弹窗：单行标题 + 多行输入 + 字数计数。
 *
 * 草稿按 visible 换代重置：visible 传 false 后库不会卸载组合，remember 必须跟着换代。
 */
@Composable
private fun CouiForumInputDialog(
    visible: Boolean,
    title: String,
    busy: Boolean,
    confirmText: String,
    busyConfirmText: String,
    placeholder: String,
    onDismiss: () -> Unit,
    onSend: (String) -> Unit,
    targetKey: String? = null,
) {
    val scheme = COUITheme.colorScheme
    val blur = panelBlur(MaterialBlurDefaults::dialog)
    var draft by rememberSavedDialogDraft(isOpen = visible, targetKey = targetKey)
    OverlayDialog(
        show = visible,
        title = title,
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
                    value = draft,
                    onValueChange = { draft = it.take(COMMENT_REPLY_MAX_LENGTH) },
                    textStyle = rememberCouiBodyTextStyle().copy(color = scheme.labelPrimary),
                    cursorBrush = SolidColor(scheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 96.dp)
                        .clip(CouiCommentFieldShape)
                        .background(couiDialogSurfaceColor())
                        .padding(12.dp),
                    decorationBox = { innerTextField ->
                        Box(modifier = Modifier.fillMaxWidth()) {
                            if (draft.isEmpty()) {
                                CouiBodyText(
                                    text = placeholder,
                                    color = scheme.labelSecondary,
                                    textAlign = TextAlign.Start,
                                )
                            }
                            innerTextField()
                        }
                    },
                )
                Text(
                    text = draft.length.toString() + "/" + COMMENT_REPLY_MAX_LENGTH,
                    style = COUITheme.textStyles.bodyS,
                    color = scheme.labelSecondary,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        },
    ) {
        DialogButtonBar(
            negative = DialogButtonBarAction(text = "取消", onClick = onDismiss),
            positive = DialogButtonBarAction(
                text = if (busy) busyConfirmText else confirmText,
                enabled = !busy && draft.isNotBlank(),
                recommended = true,
                onClick = { onSend(draft.trim()) },
            ),
        )
    }
}
