// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.page

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.ui.GlassSnackbarHost
import com.jm3.app.miuix.ui.ListState
import com.jm3.app.miuix.ui.LocalWideLayout
import com.jm3.app.miuix.ui.coui.component.CouiEmptyGroup
import com.jm3.app.miuix.ui.coui.component.CouiFab
import com.jm3.app.miuix.ui.coui.component.CouiFeedRefreshAction
import com.jm3.app.miuix.ui.coui.component.CouiGroupTitle
import com.jm3.app.miuix.ui.coui.component.CouiHeaderCapsuleAction
import com.jm3.app.miuix.ui.coui.component.CouiLayoutTokens
import com.jm3.app.miuix.ui.coui.component.CouiLoadMoreFooter
import com.jm3.app.miuix.ui.coui.component.CouiLoadingGroup
import com.jm3.app.miuix.ui.coui.component.CouiMinTouchTarget
import com.jm3.app.miuix.ui.coui.component.CouiPageScaffold
import com.jm3.app.miuix.ui.coui.component.CouiPagingGroup
import com.jm3.app.miuix.ui.coui.component.CouiPullToRefresh
import com.jm3.app.miuix.ui.coui.component.CouiScreenFailureGroup
import com.jm3.app.miuix.ui.coui.component.CouiSegmentedCapsules
import com.jm3.app.miuix.ui.coui.component.CouiTagCapsulePill
import com.jm3.app.miuix.ui.coui.component.CouiTopBarStyle
import com.jm3.app.miuix.ui.coui.component.CouiWorkCardTokens
import com.jm3.app.miuix.ui.coui.component.CouiWorkGridCard
import com.jm3.app.miuix.ui.coui.component.CouiWorkRankBadge
import com.jm3.app.miuix.ui.coui.component.CouiWorkRowCard
import com.jm3.app.miuix.ui.coui.component.couiPageScroll
import com.jm3.app.miuix.ui.coui.component.couiPressable
import com.jm3.app.miuix.ui.coui.component.isCouiWideWindow
import com.jm3.app.miuix.ui.coui.component.panelBlur
import com.jm3.app.miuix.ui.coui.component.rememberCouiPageScrollBehavior
import com.jm3.app.miuix.ui.coui.icon.CouiIcons
import com.jm3.app.miuix.ui.coui.theme.CouiStrings
import com.jm3.app.miuix.ui.couiHomeExtraBottomDp
import com.jm3.app.miuix.ui.feedRowItems
import com.jm3.app.miuix.ui.home.HOT_TAG_COLLAPSED
import com.jm3.app.miuix.ui.home.HomePageJump
import com.jm3.app.miuix.ui.home.HomePaginationReload
import com.jm3.app.miuix.ui.home.HomeScaffoldParams
import com.jm3.app.miuix.ui.home.HomeScrollPolicy
import com.jm3.app.miuix.ui.home.HomeSearchLoad
import com.jm3.app.miuix.ui.home.HomeSideLoad
import com.jm3.app.miuix.ui.home.rememberHomeScaffoldState
import com.jm3.app.miuix.ui.readableWidth
import com.jm3.app.miuix.ui.search.searchHeadline
import com.jm3.core.model.BrowseOrder
import com.jm3.core.model.SearchResult
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardListGroup
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TopAppBarSearchBar
import io.github.suqi8.coui.kmp.basic.rememberTopAppBarSearchState
import io.github.suqi8.coui.kmp.basic.topAppBarSearchContent
import io.github.suqi8.coui.kmp.basic.topAppBarSearchResults
import io.github.suqi8.coui.kmp.blur.MaterialBlurDefaults
import io.github.suqi8.coui.kmp.layout.DialogButtonBar
import io.github.suqi8.coui.kmp.layout.DialogButtonBarAction
import io.github.suqi8.coui.kmp.layout.DialogDefaults
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.preference.PagerFooterPreference
import io.github.suqi8.coui.kmp.preference.PreferenceCategory
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.rememberCOUIFlingBehavior
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.SnackbarHostState

private const val HOME_LOAD_MORE_FAILED = "更多内容没有加载成功"

private const val SEARCH_LOAD_MORE_FAILED = "更多结果没有加载成功"

/** 排行榜入口轨道的标题：口径与榜单页默认筛选一致（全部书籍、本周、最多喜欢）。 */
private const val RANK_TRACK_TITLE = "排行榜 · 本周最多喜欢"

private val SortLabels: List<String> = BrowseOrder.entries.map { it.label }

/**
 * 首页横滑轨道：一条轨道 = 一个标题 + 一行竖卡。
 *
 * 轨道数据都来自真实接口（连载更新 / 随机推荐 / 榜单），空数据时整条轨道不出，
 * 不用占位卡凑数。
 */
private data class CouiHomeTrackData(
    val key: String,
    val title: String,
    val items: List<SearchResult>,
    val ranked: Boolean,
)

/**
 * COUI 首页：ColorOS 17 设置首页式大顶栏 + 联动搜索栏 + 下拉刷新 + 三条横滑轨道 + 分组式发现流 / 搜索结果。
 *
 * 数据编排与 HyperOS 首页同源：同一份 SessionCache 键、同一个 PagedFeed 状态机、
 * 同一套 AuthCall 结局翻译，切皮肤后列表与输入状态不会丢。
 */
@Composable
fun CouiHomeScreen(
    deps: ScreenDeps,
    onOpenAlbum: (String) -> Unit,
    onOpenWeek: () -> Unit,
    onOpenRanking: () -> Unit,
    onOpenLogin: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    val state = rememberHomeScaffoldState(
        deps = deps,
        snackbarHostState = snackbarHostState,
        onDismissInput = {
            focusManager.clearFocus()
            keyboard?.hide()
        },
        onOpenAlbum = onOpenAlbum,
        params = HomeScaffoldParams(
            searchLoad = HomeSearchLoad.Rebuilt,
            jump = HomePageJump.CouiGuarded,
            paginationReload = HomePaginationReload.ActiveOnly,
            sideLoad = HomeSideLoad.Parallel,
            loadRankTrack = true,
            scrollPolicy = HomeScrollPolicy.AnyLoaded,
            searchFailureMessage = "网络错误",
            feedFailureMessage = CouiStrings.LoadFailed,
        ),
    )

    val homeListState = rememberLazyListState()
    val searchListState = rememberLazyListState()
    val searchActive = state.searchExpanded || state.searching
    val topBarSearchState = rememberTopAppBarSearchState(active = searchActive)
    val scrollBehavior = rememberCouiPageScrollBehavior {
        val activeList = if (topBarSearchState.isResultsVisible) searchListState else homeListState
        activeList.canScrollForward || activeList.canScrollBackward
    }

    // 滚动搜索结果列表时收起键盘，避免第一次点按作品被清焦点吞掉
    LaunchedEffect(searchListState.isScrollInProgress) {
        if (searchListState.isScrollInProgress) {
            focusManager.clearFocus()
            keyboard?.hide()
        }
    }

    // 换页落地后归顶：发现流与搜索结果各持一份 LazyListState，互不冲掉滚动位置
    LaunchedEffect(
        state.searchSnapshot?.state,
        state.searchSnapshot?.feed?.page,
        state.feedSnapshot.state,
        state.feedSnapshot.feed.page,
    ) {
        if (state.scrollRequestedAfterLanding()) {
            val targetList = if (state.searching) searchListState else homeListState
            targetList.scrollToItem(0)
        }
    }

    val tracks: List<CouiHomeTrackData> = buildList {
        if (state.serialUpdates.isNotEmpty()) {
            add(CouiHomeTrackData("serial", "连更追踪", state.serialUpdates, ranked = false))
        }
        if (state.randomPicks.isNotEmpty()) {
            add(CouiHomeTrackData("random", "随机推荐", state.randomPicks, ranked = false))
        }
        if (state.rankPicks.isNotEmpty()) {
            add(CouiHomeTrackData("rank", RANK_TRACK_TITLE, state.rankPicks, ranked = true))
        }
    }
    val homeFeedRowStart = 1 + tracks.size + 1
    val searchFeedRowStart = 2
    val feedColumns = LocalWideLayout.current.feedColumns

    CouiFeedPrefetchEffect(
        feed = state.activeFeed,
        rowStart = if (state.searching) searchFeedRowStart else homeFeedRowStart,
        listState = if (state.searching) searchListState else homeListState,
        columns = feedColumns,
        enabled = !state.clickPaging,
        onPrefetch = state::loadMore,
    )

    Box(modifier = Modifier.fillMaxSize()) {
        CouiPageScaffold(
            title = "JMComic",
            topBarStyle = CouiTopBarStyle.SettingsHome,
            wide = isCouiWideWindow(),
            scrollBehavior = scrollBehavior,
            topBarSearchState = topBarSearchState,
            topBarBottom = {
                TopAppBarSearchBar(
                    query = state.query,
                    onQueryChange = { value ->
                        state.query = value
                        if (value.isEmpty() && state.searching) state.clearSearch()
                    },
                    expanded = searchActive,
                    onExpandedChange = { expanded ->
                        if (expanded) state.searchExpanded = true else state.collapseSearch()
                    },
                    cancelText = "取消",
                    onSearch = {
                        state.submitSearch()
                        state.dismissSearchInput()
                    },
                    label = "搜索作品 / 作者 / 标签",
                )
            },
            extraBottom = couiHomeExtraBottomDp().dp,
            floatingActionButton = {
                val activeListState = if (state.searching) searchListState else homeListState
                val showBackToTop by remember(activeListState) {
                    derivedStateOf { activeListState.firstVisibleItemIndex > 2 }
                }
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    if (showBackToTop) {
                        CouiFab(
                            icon = CouiIcons.Back,
                            contentDescription = "回到顶部",
                            rotationDegrees = 90f,
                            onClick = { scope.launch { activeListState.animateScrollToItem(0) } },
                        )
                    }
                    CouiFab(
                        icon = CouiIcons.Shuffle,
                        contentDescription = "随机看一部",
                        onClick = state::openRandom,
                    )
                }
            },
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize()) {
                CouiPullToRefresh(
                    isRefreshing = state.refreshing,
                    onRefresh = state::refresh,
                    scrollBehavior = scrollBehavior,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (topBarSearchState.isContentVisible) {
                            LazyColumn(
                                state = homeListState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .readableWidth()
                                    .couiPageScroll()
                                    .topAppBarSearchContent(topBarSearchState)
                                    .consumeWindowInsets(innerPadding),
                                contentPadding = innerPadding,
                                flingBehavior = rememberCOUIFlingBehavior(),
                            ) {
                                item(key = "coui-home-head") {
                                    CouiTagRow(
                                        tags = state.hotTags,
                                        expanded = state.showAllTags,
                                        onToggleExpanded = { state.showAllTags = !state.showAllTags },
                                        onPick = { tag ->
                                            state.query = tag
                                            state.runSearch(tag, byTag = true, sort = state.order)
                                        },
                                    )
                                }
                                tracks.forEach { track ->
                                    item(key = "coui-home-track-${track.key}") {
                                        CouiHomeTrackSection(
                                            track = track,
                                            onOpenAlbum = state::openAlbum,
                                            onOpenRanking = if (track.ranked) onOpenRanking else null,
                                        )
                                    }
                                }
                                item(key = "coui-home-title") {
                                    CouiGroupTitle(
                                        title = "最近更新",
                                        actions = {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                CouiHeaderCapsuleAction(text = "每周必看", onClick = onOpenWeek)
                                                CouiHeaderCapsuleAction(
                                                    text = "帮助",
                                                    tinted = false,
                                                    onClick = { state.showSearchHelp = true },
                                                )
                                                CouiFeedRefreshAction(
                                                    refreshing = state.refreshing,
                                                    onRefresh = state::refresh,
                                                )
                                            }
                                        },
                                    )
                                }
                                when (val current = state.feed) {
                                    is ListState.Loading -> item(key = "coui-home-loading") {
                                        CouiLoadingGroup(text = "正在加载最新上架…")
                                    }

                                    is ListState.Failed -> item(key = "coui-home-error") {
                                        val failureMessage = current.message.ifBlank { CouiStrings.LoadFailed }
                                        CouiScreenFailureGroup(
                                            message = failureMessage,
                                            expired = current.sessionExpired,
                                            onRetry = state::reload,
                                            onOpenLogin = onOpenLogin,
                                        )
                                    }

                                    is ListState.Loaded -> {
                                        val loadedFeed = current.feed
                                        val items = loadedFeed.items
                                        if (items.isEmpty()) {
                                            item(key = "coui-home-empty") {
                                                CouiEmptyGroup(text = CouiStrings.EmptyContent)
                                            }
                                            item(key = "coui-home-empty-hint") {
                                                PagerFooterPreference(
                                                    summary = "点右上角刷新再试一次",
                                                    withExtraMarginBottom = false,
                                                )
                                            }
                                        } else {
                                            feedRowItems(
                                                count = items.size,
                                                columns = feedColumns,
                                                keyPrefix = "coui-home-row-",
                                                keyOf = { index -> items[index].id },
                                            ) { index ->
                                                val album = items[index]
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(
                                                            start = CouiLayoutTokens.HorizontalMargin,
                                                            end = CouiLayoutTokens.HorizontalMargin,
                                                            top = if (index == 0) 0.dp else CouiWorkCardTokens.RowSpacing,
                                                        ),
                                                ) {
                                                    CouiWorkFeedRow(
                                                        album = album,
                                                        onClick = { state.openAlbum(album.id) },
                                                    )
                                                }
                                            }
                                            item(key = "coui-home-paging") {
                                                if (state.clickPaging) {
                                                    CouiPagingGroup(
                                                        page = loadedFeed.page,
                                                        pageCount = loadedFeed.pageCount,
                                                        hasMore = loadedFeed.hasMore,
                                                        onJumpPage = state::jumpToPage,
                                                    )
                                                } else {
                                                    CouiLoadMoreFooter(
                                                        loading = loadedFeed.loadingMore,
                                                        error = if (loadedFeed.loadMoreFailed) {
                                                            HOME_LOAD_MORE_FAILED
                                                        } else {
                                                            null
                                                        },
                                                        endReached = !loadedFeed.hasMore,
                                                        onRetry = state::loadMore,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                item(key = "coui-home-history") {
                                    CouiSearchHistoryGroup(
                                        history = state.searchHistory,
                                        onClear = state::clearHistory,
                                        onRemove = state::removeHistory,
                                        onPick = { word ->
                                            state.query = word
                                            state.runSearch(word, byTag = false, sort = state.order)
                                        },
                                    )
                                }
                                item(key = "coui-home-more") {
                                    CouiHomeMoreGroup(
                                        onOpenWeek = {
                                            state.dismissSearchInput()
                                            onOpenWeek()
                                        },
                                        onShowSearchHelp = { state.showSearchHelp = true },
                                    )
                                }
                            }
                        }

                        if (topBarSearchState.isResultsVisible) {
                            LazyColumn(
                                state = searchListState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .readableWidth()
                                    .couiPageScroll()
                                    .topAppBarSearchResults(topBarSearchState)
                                    .consumeWindowInsets(innerPadding),
                                contentPadding = innerPadding,
                                flingBehavior = rememberCOUIFlingBehavior(),
                            ) {
                                if (state.searching) {
                                    item(key = "coui-search-head") {
                                        CouiSearchHeader(
                                            sortLabels = SortLabels,
                                            selectedSort = state.order.ordinal.coerceIn(0, SortLabels.lastIndex),
                                            onSelectSort = { index ->
                                                state.runSearch(
                                                    state.activeQuery,
                                                    state.searchByTag,
                                                    BrowseOrder.entries[index],
                                                )
                                            },
                                            summary = searchSummary(
                                                query = state.activeQuery,
                                                byTag = state.searchByTag,
                                                total = (state.results as? ListState.Loaded)?.feed?.total,
                                            ),
                                        )
                                    }
                                    item(key = "coui-search-title") {
                                        CouiGroupTitle(
                                            title = "搜索结果",
                                            actions = {
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    CouiHeaderCapsuleAction(
                                                        text = "帮助",
                                                        tinted = false,
                                                        onClick = { state.showSearchHelp = true },
                                                    )
                                                    CouiFeedRefreshAction(
                                                        refreshing = state.refreshing,
                                                        onRefresh = state::refresh,
                                                    )
                                                }
                                            },
                                        )
                                    }
                                    when (val current = state.results) {
                                        null, is ListState.Loading -> item(key = "coui-search-loading") {
                                            CouiLoadingGroup(text = "正在搜索…")
                                        }

                                        is ListState.Failed -> item(key = "coui-search-error") {
                                            val failureMessage = current.message.ifBlank { "搜索失败" }
                                            CouiScreenFailureGroup(
                                                message = failureMessage,
                                                expired = current.sessionExpired,
                                                onRetry = state::reload,
                                                onOpenLogin = onOpenLogin,
                                            )
                                        }

                                        is ListState.Loaded -> {
                                            val loadedFeed = current.feed
                                            val items = loadedFeed.items
                                            if (items.isEmpty()) {
                                                item(key = "coui-search-empty") {
                                                    CouiEmptyGroup(text = CouiStrings.EmptySearch)
                                                }
                                                item(key = "coui-search-empty-hint") {
                                                    PagerFooterPreference(
                                                        summary = "换个关键词或标签再试",
                                                        withExtraMarginBottom = false,
                                                    )
                                                }
                                            } else {
                                                feedRowItems(
                                                    count = items.size,
                                                    columns = feedColumns,
                                                    keyPrefix = "coui-search-row-",
                                                    keyOf = { index -> items[index].id },
                                                ) { index ->
                                                    val album = items[index]
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(
                                                                start = CouiLayoutTokens.HorizontalMargin,
                                                                end = CouiLayoutTokens.HorizontalMargin,
                                                                top = if (index == 0) 0.dp else CouiWorkCardTokens.RowSpacing,
                                                            ),
                                                    ) {
                                                        CouiWorkFeedRow(
                                                            album = album,
                                                            onClick = { state.openAlbum(album.id) },
                                                        )
                                                    }
                                                }
                                                item(key = "coui-search-paging") {
                                                    if (state.clickPaging) {
                                                        CouiPagingGroup(
                                                            page = loadedFeed.page,
                                                            pageCount = loadedFeed.pageCount,
                                                            hasMore = loadedFeed.hasMore,
                                                            onJumpPage = state::jumpToPage,
                                                        )
                                                    } else {
                                                        CouiLoadMoreFooter(
                                                            loading = loadedFeed.loadingMore,
                                                            error = if (loadedFeed.loadMoreFailed) {
                                                                SEARCH_LOAD_MORE_FAILED
                                                            } else {
                                                                null
                                                            },
                                                            endReached = !loadedFeed.hasMore,
                                                            onRetry = state::loadMore,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    item(key = "coui-search-hot-tags") {
                                        CouiTagRow(
                                            tags = state.hotTags,
                                            expanded = state.showAllTags,
                                            onToggleExpanded = { state.showAllTags = !state.showAllTags },
                                            onPick = { tag ->
                                                state.query = tag
                                                state.runSearch(tag, byTag = true, sort = state.order)
                                                state.dismissSearchInput()
                                            },
                                        )
                                    }
                                    item(key = "coui-search-history") {
                                        CouiSearchHistoryGroup(
                                            history = state.searchHistory,
                                            onClear = state::clearHistory,
                                            onRemove = state::removeHistory,
                                            onPick = { word ->
                                                state.query = word
                                                state.runSearch(word, byTag = false, sort = state.order)
                                                state.dismissSearchInput()
                                            },
                                        )
                                    }
                                    item(key = "coui-search-more") {
                                        CouiHomeMoreGroup(
                                            onOpenWeek = {
                                                state.dismissSearchInput()
                                                onOpenWeek()
                                            },
                                            onShowSearchHelp = { state.showSearchHelp = true },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 弹窗放在 LazyColumn 外侧，随页面采样源获得 COUI 17 材质模糊，且不因列表滚动被卸载
                CouiSearchHelpDialog(
                    show = state.showSearchHelp,
                    onDismiss = { state.showSearchHelp = false },
                )
            }
        }

        GlassSnackbarHost(
            state = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun CouiSearchHelpDialog(
    show: Boolean,
    onDismiss: () -> Unit,
) {
    val blur = panelBlur(MaterialBlurDefaults::dialog)
    OverlayDialog(
        show = show,
        title = "搜索语法",
        summary = "空格、加号与减号决定关键词之间的关系。",
        backgroundModifier = blur?.backgroundModifier ?: Modifier,
        backgroundColor = blur?.backgroundColor ?: DialogDefaults.backgroundColor(),
        materialStroke = blur != null,
        onDismissRequest = onDismiss,
        customContent = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CouiSyntaxRow(expression = "全彩 人妻", meaning = "同时包含这两个关键词（空格分隔）")
                CouiSyntaxRow(expression = "全彩 +人妻", meaning = "两个关键词都必须包含")
                CouiSyntaxRow(expression = "全彩 -人妻", meaning = "包含「全彩」，排除「人妻」")
            }
        },
    ) {
        DialogButtonBar(
            negative = null,
            positive = DialogButtonBarAction(
                text = "知道了",
                recommended = true,
                onClick = onDismiss,
            ),
        )
    }
}

@Composable
private fun CouiSearchHistoryGroup(
    history: List<String>,
    onClear: () -> Unit,
    onRemove: (String) -> Unit,
    onPick: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        CouiGroupTitle(
            title = "首页发现",
            actions = {
                if (history.isNotEmpty()) {
                    CouiHeaderTextAction(text = "清除", onClick = onClear)
                }
            },
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CouiLayoutTokens.HorizontalMargin)
                .padding(bottom = CouiLayoutTokens.GroupSpacing),
        ) {
            CardListGroup {
                if (history.isEmpty()) {
                    item(key = "empty-history") {
                        BasicComponent(
                            title = "还没有搜索记录",
                            summary = "搜索或点热词后，关键词会留在这里",
                        )
                    }
                } else {
                    history.forEach { word ->
                        item(key = "history-$word") {
                            BasicComponent(
                                title = word,
                                summary = "点按重新搜索",
                                endActions = {
                                    CouiRowIconAction(
                                        icon = CouiIcons.Close,
                                        description = "删除这条搜索记录",
                                        onClick = { onRemove(word) },
                                    )
                                },
                                onClick = { onPick(word) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CouiHomeMoreGroup(
    onOpenWeek: () -> Unit,
    onShowSearchHelp: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        PreferenceCategory(title = "更多")
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CouiLayoutTokens.HorizontalMargin)
                .padding(bottom = CouiLayoutTokens.GroupSpacing),
        ) {
            CardListGroup {
                item(key = "more-week") {
                    BasicComponent(
                        startAction = { CouiAccentIcon(CouiIcons.Star) },
                        title = "每周必看",
                        summary = "按周汇总的连载更新",
                        onClick = onOpenWeek,
                    )
                }
                item(key = "more-syntax") {
                    BasicComponent(
                        startAction = { CouiAccentIcon(CouiIcons.Info) },
                        title = "搜索语法",
                        summary = "空格 / + / - 组合关键词",
                        onClick = onShowSearchHelp,
                    )
                }
            }
        }
    }
}

/**
 * 一条横滑轨道：标题 + 一行竖卡。
 *
 * 卡片放在无界横滑容器里，宽度必须由轨道自己固定成 112dp（CouiWorkGridCard 已改为跟随栅格单元）；
 * 名次徽章只画在封面左上角（金银铜前三名由 CouiWorkRankBadge 判定），首页不重复报数。
 */
@Composable
private fun CouiHomeTrackSection(
    track: CouiHomeTrackData,
    onOpenAlbum: (String) -> Unit,
    onOpenRanking: (() -> Unit)? = null,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        CouiGroupTitle(title = track.title) {
            if (onOpenRanking != null) {
                CouiHeaderCapsuleAction(text = "查看全部", onClick = onOpenRanking)
            }
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = CouiWorkCardTokens.TrackContentPadding),
            horizontalArrangement = Arrangement.spacedBy(CouiWorkCardTokens.GridSpacing),
        ) {
            itemsIndexed(
                items = track.items,
                key = { _, album -> "coui-home-track-${track.key}-${album.id}" },
            ) { index, album ->
                val badge: (@Composable BoxScope.() -> Unit)? = if (track.ranked) {
                    {
                        CouiWorkRankBadge(
                            rank = index + 1,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(CouiWorkCardTokens.BadgeInset),
                        )
                    }
                } else {
                    null
                }
                CouiWorkGridCard(
                    title = album.title,
                    coverUrl = album.coverUrl,
                    onClick = { onOpenAlbum(album.id) },
                    albumId = album.id,
                    subtitle = album.authors.firstOrNull(),
                    category = album.category,
                    badge = badge,
                    modifier = Modifier.width(CouiWorkCardTokens.GridWidth),
                )
            }
        }
    }
}

/** 搜索头：排序胶囊 + 结果摘要；标题行与分页脚由 LazyColumn 单独下发。 */
@Composable
private fun CouiSearchHeader(
    sortLabels: List<String>,
    selectedSort: Int,
    onSelectSort: (Int) -> Unit,
    summary: String,
) {
    CouiSegmentedCapsules(
        items = sortLabels,
        selectedIndex = selectedSort,
        onSelect = onSelectSort,
        modifier = Modifier
            .padding(horizontal = CouiLayoutTokens.HorizontalMargin)
            .padding(top = CouiLayoutTokens.CategoryTopSpacing),
    )
    Text(
        text = summary,
        style = COUITheme.textStyles.bodyXS,
        color = COUITheme.colorScheme.labelSecondary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
            .padding(top = 8.dp),
    )
}

/** 热门搜索推荐词卡片：标题栏右侧放置统一胶囊操作，热词胶囊带细微轮廓与深浅色对比层。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CouiTagRow(
    tags: List<String>,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onPick: (String) -> Unit,
) {
    if (tags.isEmpty()) return
    val shown = if (expanded) tags else tags.take(HOT_TAG_COLLAPSED)
    Column(modifier = Modifier.fillMaxWidth()) {
        CouiGroupTitle(
            title = "热门搜索",
            actions = {
                if (tags.size > HOT_TAG_COLLAPSED) {
                    CouiHeaderCapsuleAction(
                        text = if (expanded) "收起" else "展开",
                        trailingIcon = if (expanded) CouiIcons.ChevronUp else CouiIcons.ChevronDown,
                        onClick = onToggleExpanded,
                    )
                }
            },
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CouiLayoutTokens.HorizontalMargin),
        ) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CouiLayoutTokens.RowPadding, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                shown.forEach { tag ->
                    CouiTagCapsulePill(text = tag, onClick = { onPick(tag) })
                }
            }
        }
    }
}

/**
 * 作品行：发现流与搜索结果共用同一张 CouiWorkRowCard。
 */
@Composable
private fun CouiWorkFeedRow(album: SearchResult, onClick: () -> Unit) {
    val tags = album.tags.take(3).joinToString(" · ")
    CouiWorkRowCard(
        coverUrl = album.coverUrl,
        title = album.title,
        onClick = onClick,
        albumId = album.id,
        subtitle = album.authors.joinToString(" / ").ifBlank { null },
        category = album.category,
        content = {
            if (tags.isNotEmpty()) {
                Text(
                    text = tags,
                    style = COUITheme.textStyles.bodyXS,
                    color = COUITheme.colorScheme.labelSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
    )
}

@Composable
private fun CouiHeaderTextAction(text: String, onClick: () -> Unit) {
    CouiHeaderCapsuleAction(text = text, onClick = onClick)
}

/** 行尾的图标动作：32dp 视觉里放 16dp 图标，触区由 couiPressable 补到 48dp，避免误触整行。 */
@Composable
private fun CouiRowIconAction(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .couiPressable(shape = CircleShape, minTouchTarget = CouiMinTouchTarget, onClick = onClick)
            .size(32.dp)
            .clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            modifier = Modifier.size(16.dp),
            tint = COUITheme.colorScheme.labelSecondary,
        )
    }
}

@Composable
private fun CouiAccentIcon(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(CouiLayoutTokens.IconSize),
        tint = COUITheme.colorScheme.primary,
    )
}

@Composable
private fun CouiSyntaxRow(expression: String, meaning: String) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = expression,
            style = COUITheme.textStyles.bodyXL,
            color = COUITheme.colorScheme.labelPrimary,
        )
        Text(
            text = meaning,
            style = COUITheme.textStyles.bodyM,
            color = COUITheme.colorScheme.labelSecondary,
        )
    }
}

/** 搜索摘要：标签搜索要标明是标签，结果总数只在服务端给出时显示。 */
internal fun searchSummary(query: String, byTag: Boolean, total: Int?): String {
    val head = searchHeadline(query, byTag)
    return if (total != null && total > 0) "$head · 共 $total 个结果" else head
}
