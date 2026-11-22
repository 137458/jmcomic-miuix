// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.page

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.ui.BrowseMode
import com.jm3.app.miuix.ui.GlassSnackbarHost
import com.jm3.app.miuix.ui.ListState
import com.jm3.app.miuix.ui.LocalWideLayout
import com.jm3.app.miuix.ui.PaginationModeReloadEffect
import com.jm3.app.miuix.ui.browse.rememberBrowseScaffoldState
import com.jm3.app.miuix.ui.browseTagCacheKey
import com.jm3.app.miuix.ui.coui.component.CouiBodyText
import com.jm3.app.miuix.ui.coui.component.CouiCommentFieldShape
import com.jm3.app.miuix.ui.coui.component.CouiEmptyGroup
import com.jm3.app.miuix.ui.coui.component.CouiFab
import com.jm3.app.miuix.ui.coui.component.CouiGroupTitle
import com.jm3.app.miuix.ui.coui.component.CouiLayoutTokens
import com.jm3.app.miuix.ui.coui.component.CouiLoadMoreFooter
import com.jm3.app.miuix.ui.coui.component.CouiLoadingGroup
import com.jm3.app.miuix.ui.coui.component.CouiMinTouchTarget
import com.jm3.app.miuix.ui.coui.component.CouiPageScaffold
import com.jm3.app.miuix.ui.coui.component.CouiPageTopBarAction
import com.jm3.app.miuix.ui.coui.component.CouiPagingGroup
import com.jm3.app.miuix.ui.coui.component.CouiPullToRefresh
import com.jm3.app.miuix.ui.coui.component.CouiScreenFailureGroup
import com.jm3.app.miuix.ui.coui.component.CouiSegmentedCapsules
import com.jm3.app.miuix.ui.coui.component.CouiTagCapsulePill
import com.jm3.app.miuix.ui.coui.component.CouiWorkCardTokens
import com.jm3.app.miuix.ui.coui.component.CouiWorkRankBadge
import com.jm3.app.miuix.ui.coui.component.CouiWorkRowCard
import com.jm3.app.miuix.ui.coui.component.couiDialogListMaxHeight
import com.jm3.app.miuix.ui.coui.component.couiDialogSurfaceBorder
import com.jm3.app.miuix.ui.coui.component.couiDialogSurfaceColor
import com.jm3.app.miuix.ui.coui.component.couiPageScroll
import com.jm3.app.miuix.ui.coui.component.couiPressable
import com.jm3.app.miuix.ui.coui.component.isCouiWideWindow
import com.jm3.app.miuix.ui.coui.component.panelBlur
import com.jm3.app.miuix.ui.coui.component.rememberCouiBodyTextStyle
import com.jm3.app.miuix.ui.coui.component.rememberCouiPageScrollBehavior
import com.jm3.app.miuix.ui.coui.icon.CouiIcons
import com.jm3.app.miuix.ui.coui.theme.CouiStrings
import com.jm3.app.miuix.ui.feedRowItems
import com.jm3.app.miuix.ui.list.ListLoadState
import com.jm3.app.miuix.ui.list.SessionListLoader
import com.jm3.app.miuix.ui.list.toCouiPageSlice
import com.jm3.app.miuix.ui.normalizeBrowseTagKeyword
import com.jm3.app.miuix.ui.readableWidth
import com.jm3.core.model.BrowseCategory
import com.jm3.core.model.BrowseOrder
import com.jm3.core.model.BrowseTime
import com.jm3.core.model.CategoryBlock
import com.jm3.core.model.SearchResult
import com.jm3.core.model.SearchScope
import com.jm3.core.paging.PagedFeed
import com.jm3.core.paging.pagerTargetPage
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.ChipTabRow
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.blur.MaterialBlurDefaults
import io.github.suqi8.coui.kmp.layout.BottomSheetDefaults
import io.github.suqi8.coui.kmp.overlay.OverlayBottomSheet
import io.github.suqi8.coui.kmp.preference.PagerFooterPreference
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.rememberCOUIFlingBehavior
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.SnackbarHostState

/** 标签检索视图里结果行的起始下标：0 是「标签：xxx」标题，其后才是行。 */
private const val TAG_FEED_ROW_START = 1

/** 主列表与标签列表共用的续页失败提示，文案与 HyperOS 版浏览页一致。 */
private const val BROWSE_LOAD_MORE_FAILED = CouiStrings.LoadMoreFailedRetry

/** 标签视图的续页失败提示。 */
private const val TAG_LOAD_MORE_FAILED = "标签结果没拿到下一页，可点击重试"

/** 每周更新区块的续页失败提示。 */
private const val WEEKLY_LOAD_MORE_FAILED = "每周更新没拿到下一页，可点击重试"

/** 标签导航里热门标签的最大条数：与 HyperOS 版浏览页一致，避免首屏被标签墙占满。 */
private const val HOT_TAG_LIMIT = 12

/**
 * COUI 分类 / 榜单页（suqi8/coui 2.1.0）。
 */
@Composable
internal fun CouiBrowseScreen(
    deps: ScreenDeps,
    mode: BrowseMode,
    onOpenAlbum: (String) -> Unit,
    onOpenLogin: () -> Unit = {},
) {
    val sessionCache = deps.sessionCache
    val gateway = deps.gateway
    val authCall = deps.authCall
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val lazyState = rememberLazyListState()
    val colors = COUITheme.colorScheme

    val browse = rememberBrowseScaffoldState(
        deps = deps,
        mode = mode,
        failureMessage = CouiStrings.LoadFailed,
        onNotice = { message -> scope.launch { snackbarHostState.showSnackbar(message) } },
        onScrollToTop = { lazyState.animateScrollToItem(0) },
    )
    val feedSnapshot = browse.snapshot
    val feedState = browse.feedState
    val clickPaging = browse.clickPaging
    val category = browse.category
    val order = browse.order
    val time = browse.time
    val categoryTree = browse.categoryTree
    val hotTags = browse.hotTags
    val listKey = browse.cacheKey

    var pageStride by remember(browse.pageKey) { mutableIntStateOf(0) }

    val weeklyLoader = remember {
        SessionListLoader(
            scope = scope,
            idOf = { item: SearchResult -> item.id },
            failureMessage = "每周更新加载失败",
            fetchPage = { page ->
                authCall.call { gateway.serialization(page) }.toCouiPageSlice()
            },
            notice = { message -> scope.launch { snackbarHostState.showSnackbar(message) } },
        )
    }

    var weeklyDialogOpen by rememberSaveable { mutableStateOf(false) }
    var moreCategoriesDialogOpen by rememberSaveable { mutableStateOf(false) }
    var tagNavExpanded by rememberSaveable { mutableStateOf(false) }

    val tagKeyword = browse.tagKeyword
    val tagLoader = remember(tagKeyword, order) {
        tagKeyword?.let { keyword ->
            SessionListLoader(
                scope = scope,
                idOf = { item: SearchResult -> item.id },
                failureMessage = "标签加载失败",
                fetchPage = { page ->
                    authCall.call {
                        gateway.search(
                            text = keyword,
                            page = page,
                            order = order,
                            scope = SearchScope.TAG,
                        )
                    }.toCouiPageSlice()
                },
                notice = { message -> scope.launch { snackbarHostState.showSnackbar(message) } },
                cache = sessionCache,
                cacheKey = browseTagCacheKey(keyword, order),
            )
        }
    }

    val tagSnapshot = tagLoader?.snapshot?.collectAsState()?.value
    val weeklySnapshot by weeklyLoader.snapshot.collectAsState()

    var pendingTagScrollTop by remember { mutableStateOf(false) }

    fun loadWeekly() {
        weeklyLoader.loadFirstPage()
    }

    fun loadMoreWeekly() {
        weeklyLoader.loadMore()
    }

    fun loadTag(keyword: String) {
        val trimmed = normalizeBrowseTagKeyword(keyword) ?: return
        if (trimmed == tagKeyword) {
            tagLoader?.loadFirstPage()
        } else {
            browse.openTag(trimmed)
        }
    }

    fun loadMoreTag() {
        tagLoader?.loadMore()
    }

    fun jumpToTagPage(target: Int) {
        val loader = tagLoader ?: return
        val current = tagSnapshot?.feed ?: return
        val resolved = pagerTargetPage(current.page, target, current.pageCount)
        if (resolved == current.page) return
        pendingTagScrollTop = true
        loader.goToPage(resolved)
    }

    LaunchedEffect(feedSnapshot.feed.page, feedSnapshot.feed.items.size) {
        val feed = feedSnapshot.feed
        if (feed.page == 1 && feed.items.isNotEmpty()) pageStride = feed.items.size
    }
    LaunchedEffect(tagLoader) {
        val loader = tagLoader ?: return@LaunchedEffect
        if (loader.state !is ListLoadState.Loaded) loader.loadFirstPage()
    }
    LaunchedEffect(tagSnapshot?.state, tagSnapshot?.feed?.page) {
        if (!pendingTagScrollTop || tagSnapshot?.state !is ListLoadState.Loaded) return@LaunchedEffect
        pendingTagScrollTop = false
        lazyState.animateScrollToItem(0)
    }
    PaginationModeReloadEffect(deps.paginationMode) {
        normalizeBrowseTagKeyword(tagKeyword)?.let(::loadTag)
    }
    LaunchedEffect(deps.gateway, deps.authCall) {
        weeklyLoader.loadFirstPage()
        browse.loadCategoryTree()
        browse.loadHotTags()
    }

    val categoryBlocks = categoryTree?.blocks.orEmpty()
    val subCategories = categoryTree
        ?.categories
        ?.firstOrNull { it.slug == category.apiValue }
        ?.children
        .orEmpty()
    val tagNavTags = if (mode == BrowseMode.CATEGORY) hotTags.take(HOT_TAG_LIMIT) else emptyList()
    val hasTagNav = subCategories.isNotEmpty() || tagNavTags.isNotEmpty()
    val feedColumns = LocalWideLayout.current.feedColumns
    val weekly = weeklySnapshot.feed
    val weeklyIsLoading = weeklySnapshot.state is ListLoadState.Loading
    val weeklyFail = weeklySnapshot.state as? ListLoadState.Failed
    val tagFeed = tagSnapshot?.feed ?: PagedFeed()
    val browseHeadCount = couiBrowseHeadCount(
        isCategoryMode = mode == BrowseMode.CATEGORY,
        hasTagNav = hasTagNav,
        hasWeeklyPaging = weekly.items.isNotEmpty(),
        weeklyItemCount = weekly.items.size,
        hasWeeklyFooter = weekly.items.isNotEmpty() && weeklyFail != null,
        columns = feedColumns,
    )

    val browseTagView = tagKeyword != null
    CouiFeedPrefetchEffect(
        feed = if (browseTagView) tagFeed else (feedState as? ListState.Loaded)?.feed,
        rowStart = if (browseTagView) TAG_FEED_ROW_START else browseHeadCount,
        listState = lazyState,
        columns = feedColumns,
        enabled = !clickPaging,
        restartKey = listKey to tagKeyword,
        onPrefetch = { if (browseTagView) loadMoreTag() else browse.loadMore() },
    )

    val scrollBehavior = rememberCouiPageScrollBehavior {
        lazyState.canScrollForward || lazyState.canScrollBackward
    }

    BackHandler(enabled = tagKeyword != null) { browse.closeTag() }

    val keyword = tagKeyword
    val tagFailureMessage = tagSnapshot?.state as? ListLoadState.Failed
    val tagIsLoading = tagSnapshot?.state is ListLoadState.Loading
    val tagItems = tagFeed.items
    val tagTotal = tagFeed.total
    val tagPage = tagFeed.page
    val tagPageCount = tagFeed.pageCount
    val tagHasMore = tagFeed.hasMore
    val tagLoadingMore = tagFeed.loadingMore
    val tagLoadMoreFailed = tagFeed.loadMoreFailed
    val activeRefreshing = if (keyword == null) browse.refreshing else (tagSnapshot?.refreshing == true)

    fun refreshActive(byPull: Boolean) {
        if (keyword == null) {
            browse.refresh(byPull = byPull)
        } else if (byPull && tagLoader != null) {
            tagLoader.refresh()
        } else {
            loadTag(keyword)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CouiPageScaffold(
            title = if (keyword == null) mode.title else "标签：$keyword",
            wide = isCouiWideWindow(),
            onBack = if (keyword != null) browse::closeTag else null,
            scrollBehavior = scrollBehavior,
            attachScrollNestedScroll = false,
            actions = {
                if (mode == BrowseMode.CATEGORY && keyword == null) {
                    CouiPageTopBarAction(
                        icon = CouiIcons.Clock,
                        contentDescription = "每周连载更新",
                        onClick = { weeklyDialogOpen = true },
                    )
                }
            },
            topBarBottom = {
                if (mode == BrowseMode.CATEGORY && keyword == null) {
                    CouiBrowseCategoryBar(
                        options = BrowseCategory.entries,
                        selected = category,
                        onSelect = { browse.selectCategory(it.apiValue) },
                        showMoreAction = categoryBlocks.isNotEmpty(),
                        onOpenMore = { moreCategoriesDialogOpen = true },
                    )
                }
            },
            floatingActionButton = {
                CouiFab(
                    icon = CouiIcons.Refresh,
                    contentDescription = "刷新",
                    onClick = { refreshActive(byPull = true) },
                )
            },
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize()) {
                CouiPullToRefresh(
                    isRefreshing = activeRefreshing,
                    onRefresh = { refreshActive(byPull = true) },
                    scrollBehavior = scrollBehavior,
                    contentPadding = innerPadding,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(
                        state = lazyState,
                        modifier = Modifier
                            .fillMaxSize()
                            .readableWidth()
                            .couiPageScroll()
                            .consumeWindowInsets(innerPadding),
                        contentPadding = innerPadding,
                        flingBehavior = rememberCOUIFlingBehavior(),
                    ) {
                        if (keyword != null) {
                            item(key = "coui-tag-title") {
                                CouiGroupTitle(
                                    title = "标签：$keyword",
                                    actions = {
                                        val total = tagTotal
                                        if (total != null) {
                                            Text(
                                                text = "共 $total 部",
                                                style = COUITheme.textStyles.bodyXS,
                                                color = colors.labelSecondary,
                                            )
                                        }
                                    },
                                )
                            }
                            when {
                                tagIsLoading && tagItems.isEmpty() -> item(key = "coui-tag-loading") {
                                    CouiLoadingGroup()
                                }

                                tagFailureMessage != null && tagItems.isEmpty() -> item(key = "coui-tag-error") {
                                    CouiScreenFailureGroup(
                                        message = tagFailureMessage.message,
                                        expired = tagFailureMessage.sessionExpired,
                                        onRetry = { loadTag(keyword) },
                                        onOpenLogin = onOpenLogin,
                                    )
                                }

                                tagItems.isEmpty() -> {
                                    item(key = "coui-tag-empty") {
                                        CouiEmptyGroup(text = "这个标签还没有作品")
                                    }
                                    item(key = "coui-tag-empty-hint") {
                                        PagerFooterPreference(
                                            summary = "换个标签，或返回分类浏览看看",
                                            withExtraMarginBottom = false,
                                        )
                                    }
                                }

                                else -> {
                                    feedRowItems(
                                        count = tagItems.size,
                                        columns = feedColumns,
                                        keyPrefix = "coui-tag-row-",
                                        keyOf = { index -> tagItems[index].id },
                                    ) { index ->
                                        val result = tagItems[index]
                                        CouiBrowseRowSlot(
                                            result = result,
                                            rank = null,
                                            onClick = { onOpenAlbum(result.id) },
                                        )
                                    }
                                    item(key = "coui-tag-paging") {
                                        if (clickPaging) {
                                            CouiPagingGroup(
                                                page = tagPage,
                                                pageCount = tagPageCount,
                                                hasMore = tagHasMore,
                                                onJumpPage = { jumpToTagPage(it) },
                                            )
                                        } else {
                                            CouiLoadMoreFooter(
                                                loading = tagLoadingMore,
                                                error = if (tagLoadMoreFailed) TAG_LOAD_MORE_FAILED else null,
                                                endReached = !tagHasMore,
                                                onRetry = { loadMoreTag() },
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            item(key = "coui-browse-filters") {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    if (mode == BrowseMode.CATEGORY) {
                                        CouiSegmentedCapsules(
                                            items = BrowseOrder.entries.map { it.label },
                                            selectedIndex = BrowseOrder.entries.indexOf(order),
                                            onSelect = { browse.selectOrder(BrowseOrder.entries[it].apiValue) },
                                            small = true,
                                            modifier = Modifier.padding(
                                                horizontal = CouiLayoutTokens.HorizontalMargin,
                                                vertical = 8.dp,
                                            ),
                                        )
                                    } else {
                                        CouiSegmentedCapsules(
                                            items = BrowseTime.entries.map { it.label },
                                            selectedIndex = BrowseTime.entries.indexOf(time),
                                            onSelect = { browse.selectTime(BrowseTime.entries[it].apiValue) },
                                            small = true,
                                            modifier = Modifier.padding(
                                                horizontal = CouiLayoutTokens.HorizontalMargin,
                                                vertical = 8.dp,
                                            ),
                                        )
                                        CouiSegmentedCapsules(
                                            items = BrowseOrder.entries.map { it.label },
                                            selectedIndex = BrowseOrder.entries.indexOf(order),
                                            onSelect = { browse.selectOrder(BrowseOrder.entries[it].apiValue) },
                                            small = true,
                                            modifier = Modifier.padding(
                                                horizontal = CouiLayoutTokens.HorizontalMargin,
                                                vertical = 4.dp,
                                            ),
                                        )
                                    }
                                }
                            }
                            if (hasTagNav) {
                                item(key = "coui-browse-tag-nav") {
                                    CouiCollapsibleTagNavGroup(
                                        subCategoryNames = subCategories.map { it.name },
                                        hotTags = tagNavTags,
                                        expanded = tagNavExpanded,
                                        onToggle = { tagNavExpanded = !tagNavExpanded },
                                        onTagClick = { browse.openTag(it) },
                                    )
                                }
                            }
                            if (mode == BrowseMode.RANK) {
                                couiWeeklySection(
                                    feed = weekly,
                                    columns = feedColumns,
                                    loading = weeklyIsLoading,
                                    failure = weeklyFail,
                                    onRetry = { loadWeekly() },
                                    onLoadMore = { loadMoreWeekly() },
                                    onOpenLogin = onOpenLogin,
                                    onOpenAlbum = onOpenAlbum,
                                )
                            }

                            when (feedState) {
                                ListState.Loading -> item(key = "coui-browse-loading") {
                                    CouiLoadingGroup()
                                }

                                is ListState.Failed -> item(key = "coui-browse-error") {
                                    CouiScreenFailureGroup(
                                        message = feedState.message,
                                        expired = feedState.sessionExpired,
                                        onRetry = { browse.refresh(byPull = true) },
                                        onOpenLogin = onOpenLogin,
                                    )
                                }

                                is ListState.Loaded -> {
                                    val feed = feedState.feed
                                    val resultTitle = when (mode) {
                                        BrowseMode.CATEGORY ->
                                            if (category == BrowseCategory.ALL) {
                                                "全部作品"
                                            } else {
                                                "${category.label}作品"
                                            }

                                        BrowseMode.RANK -> "${time.label} · ${order.label}榜"
                                    }
                                    if (feed.items.isEmpty()) {
                                        item(key = "coui-browse-empty") {
                                            CouiEmptyGroup(text = CouiStrings.EmptyWorks)
                                        }
                                        item(key = "coui-browse-empty-hint") {
                                            PagerFooterPreference(
                                                summary = "换一个分类或排序试试",
                                                withExtraMarginBottom = false,
                                            )
                                        }
                                    } else {
                                        item(key = "coui-browse-feed-title") {
                                            CouiGroupTitle(
                                                title = resultTitle,
                                                actions = {
                                                    val total = feed.total
                                                    if (total != null) {
                                                        Text(
                                                            text = "共 $total 部",
                                                            style = COUITheme.textStyles.bodyXS,
                                                            color = colors.labelSecondary,
                                                        )
                                                    }
                                                },
                                            )
                                        }
                                        val stride = if (pageStride > 0) pageStride else feed.items.size.coerceAtLeast(1)
                                        feedRowItems(
                                            count = feed.items.size,
                                            columns = feedColumns,
                                            keyPrefix = "coui-browse-row-",
                                            keyOf = { index -> feed.items[index].id + "-" + index },
                                        ) { index ->
                                            val result = feed.items[index]
                                            CouiBrowseRowSlot(
                                                result = result,
                                                rank = if (mode == BrowseMode.RANK) {
                                                    displayRank(clickPaging, feed.page, stride, index)
                                                } else {
                                                    null
                                                },
                                                onClick = { onOpenAlbum(result.id) },
                                            )
                                        }
                                        item(key = "coui-browse-paging") {
                                            if (clickPaging) {
                                                CouiPagingGroup(
                                                    page = feed.page,
                                                    pageCount = feed.pageCount,
                                                    hasMore = feed.hasMore,
                                                    onJumpPage = { browse.jumpToPage(it) },
                                                )
                                            } else {
                                                CouiLoadMoreFooter(
                                                    loading = feed.loadingMore,
                                                    error = if (feed.loadMoreFailed) BROWSE_LOAD_MORE_FAILED else null,
                                                    endReached = !feed.hasMore,
                                                    onRetry = { browse.loadMore() },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 弹层放在 LazyColumn 之外、CouiPageScaffold 之内，承接 LocalPageBackdrop 实时材质模糊
                if (mode == BrowseMode.CATEGORY) {
                    CouiMoreCategoriesSheet(
                        visible = moreCategoriesDialogOpen && categoryBlocks.isNotEmpty(),
                        blocks = categoryBlocks,
                        onDismiss = { moreCategoriesDialogOpen = false },
                        onTagClick = { tag ->
                            moreCategoriesDialogOpen = false
                            browse.openTag(tag)
                        },
                    )
                    CouiWeeklyUpdatesSheet(
                        visible = weeklyDialogOpen,
                        feed = weekly,
                        loading = weeklyIsLoading,
                        failure = weeklyFail,
                        onDismiss = { weeklyDialogOpen = false },
                        onRetry = { loadWeekly() },
                        onLoadMore = { loadMoreWeekly() },
                        onOpenLogin = {
                            weeklyDialogOpen = false
                            onOpenLogin()
                        },
                        onOpenAlbum = { albumId ->
                            weeklyDialogOpen = false
                            onOpenAlbum(albumId)
                        },
                    )
                }
            }
        }

        GlassSnackbarHost(
            state = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** 顶栏下方主分类条：使用 suqi8/coui 官方 [ChipTabRow]（内置滑动渐隐遮罩与前置「更多」胶囊）。 */
@Composable
private fun CouiBrowseCategoryBar(
    options: List<BrowseCategory>,
    selected: BrowseCategory,
    onSelect: (BrowseCategory) -> Unit,
    showMoreAction: Boolean,
    onOpenMore: () -> Unit,
) {
    val tabs = remember(options) { options.map { it.label } }
    val selectedIndex = options.indexOf(selected).coerceAtLeast(0)
    ChipTabRow(
        tabs = tabs,
        selectedTabIndex = selectedIndex,
        onTabSelected = { index ->
            options.getOrNull(index)?.let(onSelect)
        },
        modifier = Modifier
            .fillMaxWidth()
            .readableWidth(),
        onLeadingIconClick = onOpenMore,
        leadingIcon = if (showMoreAction) {
            {
                Icon(
                    imageVector = CouiIcons.Category,
                    contentDescription = "更多分类",
                    modifier = Modifier.size(20.dp),
                )
            }
        } else {
            null
        },
    )
}

/** 更多分类底部抽屉：24dp 横向留白 + [panelBlur] 材质模糊。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CouiMoreCategoriesSheet(
    visible: Boolean,
    blocks: List<CategoryBlock>,
    onDismiss: () -> Unit,
    onTagClick: (String) -> Unit,
) {
    val colors = COUITheme.colorScheme
    var filterQuery by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(visible) {
        if (visible) filterQuery = ""
    }
    val filteredBlocks = remember(blocks, filterQuery) {
        couiFilterCategoryBlocks(blocks, filterQuery)
    }
    val surfaceColor = couiDialogSurfaceColor()
    val elevatedSurfaceColor = couiDialogSurfaceColor(elevated = true)
    val surfaceBorder = couiDialogSurfaceBorder()
    val blur = panelBlur(MaterialBlurDefaults::bottomSheet)

    OverlayBottomSheet(
        show = visible,
        title = "更多分类",
        backgroundModifier = blur?.backgroundModifier ?: Modifier,
        backgroundColor = blur?.backgroundColor ?: BottomSheetDefaults.backgroundColor(),
        materialStroke = blur != null,
        endAction = {
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = CouiIcons.Close,
                    contentDescription = "关闭",
                    tint = colors.labelPrimary,
                )
            }
        },
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 42.dp)
                    .clip(CouiCommentFieldShape)
                    .background(surfaceColor)
                    .border(surfaceBorder, CouiCommentFieldShape)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = CouiIcons.Search,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = colors.labelSecondary,
                )
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (filterQuery.isEmpty()) {
                        CouiBodyText(
                            text = "筛选分类或标签",
                            color = colors.labelSecondary,
                            textAlign = TextAlign.Start,
                        )
                    }
                    BasicTextField(
                        value = filterQuery,
                        onValueChange = { filterQuery = it },
                        singleLine = true,
                        textStyle = rememberCouiBodyTextStyle().copy(color = colors.labelPrimary),
                        cursorBrush = SolidColor(colors.primary),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (filterQuery.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .couiPressable(
                                shape = CircleShape,
                                backgroundColor = elevatedSurfaceColor,
                                minTouchTarget = CouiMinTouchTarget,
                                onClick = { filterQuery = "" },
                            )
                            .padding(4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = CouiIcons.Close,
                            contentDescription = "清除筛选",
                            modifier = Modifier.size(14.dp),
                            tint = colors.labelSecondary,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (filteredBlocks.isEmpty()) {
                CouiBodyText(
                    text = "没有匹配的分类或标签",
                    color = colors.labelSecondary,
                    textAlign = TextAlign.Start,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = couiDialogListMaxHeight(420.dp)),
                    contentPadding = PaddingValues(bottom = 8.dp),
                ) {
                    itemsIndexed(filteredBlocks, key = { _, block -> block.title }) { index, block ->
                        if (index > 0) {
                            HorizontalDivider(
                                thickness = CouiLayoutTokens.DividerThickness,
                                color = colors.divider,
                            )
                        }
                        Text(
                            text = block.title,
                            style = COUITheme.textStyles.description,
                            color = colors.labelSecondary,
                            textAlign = TextAlign.Start,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    top = if (index == 0) 6.dp else 12.dp,
                                    bottom = 8.dp,
                                ),
                        )
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            block.tags.forEach { tag ->
                                CouiTagCapsulePill(
                                    text = tag,
                                    glassSurface = true,
                                    onClick = { onTagClick(tag) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 每周连载更新底部抽屉：24dp 横向留白 + [panelBlur] 材质模糊。 */
@Composable
private fun CouiWeeklyUpdatesSheet(
    visible: Boolean,
    feed: PagedFeed<SearchResult>,
    loading: Boolean,
    failure: ListLoadState.Failed?,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenLogin: () -> Unit,
    onOpenAlbum: (String) -> Unit,
) {
    val colors = COUITheme.colorScheme
    val blur = panelBlur(MaterialBlurDefaults::bottomSheet)
    OverlayBottomSheet(
        show = visible,
        title = "每周连载更新",
        backgroundModifier = blur?.backgroundModifier ?: Modifier,
        backgroundColor = blur?.backgroundColor ?: BottomSheetDefaults.backgroundColor(),
        materialStroke = blur != null,
        endAction = {
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = CouiIcons.Close,
                    contentDescription = "关闭",
                    tint = colors.labelPrimary,
                )
            }
        },
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = couiDialogListMaxHeight(420.dp))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp),
        ) {
            when {
                loading && feed.items.isEmpty() -> CouiBodyText(
                    text = "正在加载每周更新…",
                    color = colors.labelSecondary,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                )

                failure != null && feed.items.isEmpty() -> {
                    CouiBodyText(
                        text = failure.message.ifBlank { "每周更新加载失败" },
                        color = colors.labelSecondary,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    CouiTagChip(
                        text = if (failure.sessionExpired) "去登录" else "重试",
                        onClick = {
                            if (failure.sessionExpired) onOpenLogin() else onRetry()
                        },
                    )
                }

                feed.items.isEmpty() -> CouiBodyText(
                    text = "暂无每周更新",
                    color = colors.labelSecondary,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                )

                else -> {
                    val layoutSpec = couiWeeklyCardLayoutSpec(inDialog = true)
                    feed.items.forEachIndexed { index, result ->
                        if (index > 0) Spacer(modifier = Modifier.height(layoutSpec.cardSpacingDp.dp))
                        CouiBrowseResultRow(
                            result = result,
                            rank = null,
                            onClick = { onOpenAlbum(result.id) },
                        )
                    }
                    when {
                        feed.loadingMore -> Text(
                            text = "正在加载更多…",
                            style = COUITheme.textStyles.bodyXS,
                            color = colors.labelSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        )

                        feed.loadMoreFailed -> Box(
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CouiTagChip(text = WEEKLY_LOAD_MORE_FAILED, onClick = onLoadMore)
                        }

                        feed.hasMore -> Box(
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CouiTagChip(text = "加载更多", onClick = onLoadMore)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CouiBrowseRowSlot(
    result: SearchResult,
    rank: Int?,
    onClick: () -> Unit,
    horizontalMargin: Dp = CouiLayoutTokens.HorizontalMargin,
    verticalPadding: Dp = CouiWorkCardTokens.RowSpacing / 2,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalMargin, vertical = verticalPadding),
    ) {
        CouiBrowseResultRow(
            result = result,
            rank = rank,
            onClick = onClick,
        )
    }
}

private fun LazyListScope.couiWeeklySection(
    feed: PagedFeed<SearchResult>,
    columns: Int,
    loading: Boolean,
    failure: ListLoadState.Failed?,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenLogin: () -> Unit,
    onOpenAlbum: (String) -> Unit,
) {
    val layoutSpec = couiWeeklyCardLayoutSpec(inDialog = false)
    if (feed.items.isEmpty()) {
        item(key = "coui-browse-weekly") {
            Column(modifier = Modifier.fillMaxWidth()) {
                CouiGroupTitle(title = "每周连载更新")
                when {
                    loading -> CouiLoadingGroup(text = "正在加载每周更新…")

                    failure != null -> CouiScreenFailureGroup(
                        message = failure.message,
                        expired = failure.sessionExpired,
                        onRetry = onRetry,
                        onOpenLogin = onOpenLogin,
                    )

                    else -> CouiEmptyGroup(text = "暂无每周更新")
                }
            }
        }
        return
    }
    item(key = "coui-browse-weekly-title") {
        CouiGroupTitle(title = "每周连载更新")
    }
    feedRowItems(
        count = feed.items.size,
        columns = columns,
        keyPrefix = "coui-weekly-row-",
        keyOf = { index -> feed.items[index].id + "-" + index },
    ) { index ->
        val result = feed.items[index]
        CouiBrowseRowSlot(
            result = result,
            rank = null,
            onClick = { onOpenAlbum(result.id) },
            horizontalMargin = layoutSpec.horizontalMarginDp.dp,
            verticalPadding = layoutSpec.cardSpacingDp.dp / 2,
        )
    }
    failure?.message?.let { message ->
        item(key = "coui-browse-weekly-footer") {
            PagerFooterPreference(
                summary = message,
                withExtraMarginBottom = false,
            )
        }
    }
    item(key = "coui-browse-weekly-paging") {
        CouiLoadMoreFooter(
            feed = feed,
            errorMessage = WEEKLY_LOAD_MORE_FAILED,
            onRetry = onLoadMore,
        )
    }
}

/** 可折叠子分类与热门标签导航卡片：默认收起以保持首屏清爽，点击标题栏按需展开。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CouiCollapsibleTagNavGroup(
    subCategoryNames: List<String>,
    hotTags: List<String>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onTagClick: (String) -> Unit,
) {
    val colors = COUITheme.colorScheme
    val totalCount = subCategoryNames.size + hotTags.size
    val title = when {
        subCategoryNames.isNotEmpty() && hotTags.isNotEmpty() -> "子分类与热门标签"
        subCategoryNames.isNotEmpty() -> "子分类"
        else -> "热门标签"
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CouiLayoutTokens.HorizontalMargin)
            .padding(bottom = CouiLayoutTokens.GroupSpacing),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .couiPressable(
                    minTouchTarget = CouiMinTouchTarget,
                    onClick = onToggle,
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CouiBodyText(
                text = title,
                color = colors.labelPrimary,
                textAlign = TextAlign.Start,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "$totalCount 项",
                style = COUITheme.textStyles.bodyXS,
                color = colors.labelSecondary,
                maxLines = 1,
            )
            Icon(
                imageVector = CouiIcons.Forward,
                contentDescription = if (expanded) "收起" else "展开",
                modifier = Modifier
                    .size(16.dp)
                    .rotate(if (expanded) 90f else 0f),
                tint = colors.labelSecondary,
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.fillMaxWidth()) {
                HorizontalDivider(
                    thickness = CouiLayoutTokens.DividerThickness,
                    color = colors.divider,
                )
                if (subCategoryNames.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        subCategoryNames.forEach { name ->
                            CouiTagChip(text = name, onClick = { onTagClick(name) })
                        }
                    }
                }
                if (hotTags.isNotEmpty()) {
                    if (subCategoryNames.isNotEmpty()) {
                        HorizontalDivider(
                            thickness = CouiLayoutTokens.DividerThickness,
                            color = colors.divider,
                        )
                    }
                    Text(
                        text = "热门标签",
                        style = COUITheme.textStyles.bodyXS,
                        color = colors.labelSecondary,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.padding(start = 16.dp, top = 12.dp),
                    )
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        hotTags.forEach { tag ->
                            CouiTagChip(text = tag, onClick = { onTagClick(tag) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CouiTagChip(
    text: String,
    onClick: () -> Unit,
) {
    CouiTagCapsulePill(
        text = text,
        glassSurface = true,
        onClick = onClick,
    )
}

@Composable
private fun CouiBrowseResultRow(
    result: SearchResult,
    rank: Int?,
    onClick: () -> Unit,
) {
    val rankSlot: (@Composable RowScope.() -> Unit)? = if (rank == null) {
        null
    } else {
        {
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .padding(start = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                CouiWorkRankBadge(rank = rank)
            }
        }
    }
    CouiWorkRowCard(
        coverUrl = result.coverUrl,
        title = result.title,
        onClick = onClick,
        albumId = result.id,
        subtitle = result.authors.joinToString(" / ").ifBlank { null },
        category = result.category,
        leading = rankSlot,
    )
}

/** 按关键词过滤更多分类分区；空查询保留全部非空分区，仅命中分区标题时保留该分块全部标签。 */
internal fun couiFilterCategoryBlocks(
    blocks: List<CategoryBlock>,
    query: String,
): List<CategoryBlock> {
    val keyword = query.trim()
    if (keyword.isEmpty()) return blocks.filter { it.tags.isNotEmpty() }
    return blocks.mapNotNull { block ->
        if (block.tags.isEmpty()) return@mapNotNull null
        if (block.title.contains(keyword, ignoreCase = true)) {
            block
        } else {
            val matched = block.tags.filter { it.contains(keyword, ignoreCase = true) }
            if (matched.isEmpty()) null else block.copy(tags = matched)
        }
    }
}
