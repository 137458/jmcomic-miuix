// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.page

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.ui.GlassSnackbarHost
import com.jm3.app.miuix.ui.ListState
import com.jm3.app.miuix.ui.LocalWideLayout
import com.jm3.app.miuix.ui.PaginationModeReloadEffect
import com.jm3.app.miuix.ui.SessionListKey
import com.jm3.app.miuix.ui.coui.component.CouiBodyText
import com.jm3.app.miuix.ui.coui.component.CouiEmptyGroup
import com.jm3.app.miuix.ui.coui.component.CouiFab
import com.jm3.app.miuix.ui.coui.component.CouiLayoutTokens
import com.jm3.app.miuix.ui.coui.component.CouiLoadMoreFooter
import com.jm3.app.miuix.ui.coui.component.CouiLoadingGroup
import com.jm3.app.miuix.ui.coui.component.CouiMinTouchTarget
import com.jm3.app.miuix.ui.coui.component.CouiPageScaffold
import com.jm3.app.miuix.ui.coui.component.CouiPagingGroup
import com.jm3.app.miuix.ui.coui.component.CouiPullToRefresh
import com.jm3.app.miuix.ui.coui.component.CouiScreenFailureGroup
import com.jm3.app.miuix.ui.coui.component.CouiWorkRowCard
import com.jm3.app.miuix.ui.coui.component.couiPageScroll
import com.jm3.app.miuix.ui.coui.component.couiPressable
import com.jm3.app.miuix.ui.coui.component.isCouiWideWindow
import com.jm3.app.miuix.ui.coui.component.rememberCouiPageScrollBehavior
import com.jm3.app.miuix.ui.coui.icon.CouiIcons
import com.jm3.app.miuix.ui.coui.theme.CouiStrings
import com.jm3.app.miuix.ui.coui.theme.CouiTheme
import com.jm3.app.miuix.ui.feedRowItems
import com.jm3.app.miuix.ui.list.ListLoadState
import com.jm3.app.miuix.ui.list.toListState
import com.jm3.app.miuix.ui.readableWidth
import com.jm3.app.miuix.ui.route.AppRoutes
import com.jm3.app.miuix.ui.search.SearchCriteria
import com.jm3.app.miuix.ui.search.clampedPageJumpTarget
import com.jm3.app.miuix.ui.search.searchListLoader
import com.jm3.app.miuix.ui.search.searchPageTitle
import com.jm3.app.miuix.ui.skin.LocalSkin
import com.jm3.core.model.BrowseOrder
import com.jm3.core.model.BrowseTime
import com.jm3.core.model.SearchResult
import com.jm3.core.model.SearchScope
import com.jm3.core.settings.PaginationMode
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.preference.PagerFooterPreference
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.rememberCOUIFlingBehavior
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.SnackbarHostState

/** 结果行在懒列表里的起点：前面只有一条筛选胶囊项。 */
private const val COUI_SEARCH_ROW_START = 1

private const val COUI_SEARCH_LOAD_MORE_FAILED = CouiStrings.LoadMoreFailedRetry
private const val COUI_SEARCH_EMPTY = "无结果"
private const val COUI_SEARCH_EMPTY_HINT = "换个关键词，或者少加几个筛选试试"

/**
 * COUI 搜索结果页。
 *
 * 排序与时间范围都送到服务端重查（[SearchScope] 决定搜索范围），因此筛选变化等同换一次首屏，
 * 不能只重排本地结果；翻页模式沿用设置里的点击翻页/滚动续页开关。
 */
@Composable
internal fun CouiSearchScreen(
    deps: ScreenDeps,
    query: String,
    scope: SearchScope,
    onBack: () -> Unit,
    nav: AppRoutes,
) {
    CouiTheme {
        CouiSearchBody(
            deps = deps,
            query = query,
            scope = scope,
            onBack = onBack,
            onOpenAlbum = nav::album,
            onOpenLogin = nav::login,
        )
    }
}

@Composable
private fun CouiSearchBody(
    deps: ScreenDeps,
    query: String,
    scope: SearchScope,
    onBack: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenLogin: () -> Unit,
) {
    val gateway = deps.gateway
    val authCall = deps.authCall
    val coroutineScope = rememberCoroutineScope()
    val sessionCache = deps.sessionCache
    val snackbarHostState = remember { SnackbarHostState() }
    val lazyState = rememberLazyListState()
    val pageMode = deps.paginationMode
    val clickPaging = pageMode == PaginationMode.CLICK
    // 行卡列数只读 policy 的派生值：手机断点为 1，内容宽够 840dp 才排两列
    val feedColumns = LocalWideLayout.current.feedColumns

    var order by rememberSaveable(query, scope.name) { mutableStateOf(BrowseOrder.LATEST) }
    var time by rememberSaveable(query, scope.name) { mutableStateOf(BrowseTime.ALL) }

    // 代次、在途标记、翻页游标与失败落点都收进 loader：本页只交「取哪一页」与重试入口。
    // 取数口径（结局翻译、失败文案、缓存键）与其余三处搜索入口共用同一份构造器。
    val loader = remember(query, scope, order, time) {
        searchListLoader(
            scope = coroutineScope,
            authCall = authCall,
            gateway = gateway,
            criteria = { SearchCriteria(query = query, order = order) },
            failureMessage = CouiStrings.LoadFailed,
            notice = { message -> coroutineScope.launch { snackbarHostState.showSnackbar(message) } },
            searchScope = { scope },
            time = time,
            cache = sessionCache,
            cacheKey = SessionListKey.Search(query = query, scope = scope, order = order, time = time),
        )
    }
    val snapshot by loader.snapshot.collectAsState()
    // 渲染仍按 ListState 三态走：快照到三态的翻译在 list/ListStateAdapters.kt，这里只取结果
    val state = snapshot.toListState()
    // 换筛选或跳页后回到列表顶部：滚动位置是页面的事，不进 loader
    var pendingScrollTop by remember { mutableStateOf(false) }

    LaunchedEffect(loader) {
        // 缓存命中时 loader 构造即带内容：不再补拉首屏，回来时不闪加载态
        if (loader.state is ListLoadState.Loaded) return@LaunchedEffect
        pendingScrollTop = true
        loader.loadFirstPage()
    }
    LaunchedEffect(snapshot.state, snapshot.feed.page) {
        if (!pendingScrollTop || snapshot.state !is ListLoadState.Loaded) return@LaunchedEffect
        pendingScrollTop = false
        lazyState.scrollToItem(0)
    }

    fun jumpToPage(targetPage: Int) {
        if (snapshot.state !is ListLoadState.Loaded) return
        // 本页没有首页那道「总页数未知且没有下一页」的守卫：夹取后同页即不跳
        val resolved = clampedPageJumpTarget(snapshot.feed, targetPage, requireMoreWhenUnknown = false) ?: return
        pendingScrollTop = true
        loader.goToPage(resolved)
    }

    PaginationModeReloadEffect(pageMode) { loader.loadFirstPage() }

    CouiFeedPrefetchEffect(
        feed = snapshot.feed.takeIf { snapshot.state is ListLoadState.Loaded },
        rowStart = COUI_SEARCH_ROW_START,
        listState = lazyState,
        columns = feedColumns,
        enabled = !clickPaging,
        restartKey = Triple(query, order, time),
        onPrefetch = { loader.loadMore() },
    )

    val scrollBehavior = rememberCouiPageScrollBehavior {
        lazyState.canScrollForward || lazyState.canScrollBackward
    }
    val title = searchPageTitle(query, scope)

    Box(modifier = Modifier.fillMaxSize()) {
        CouiPageScaffold(
            title = title,
            onBack = onBack,
            wide = isCouiWideWindow(),
            scrollBehavior = scrollBehavior,
            attachScrollNestedScroll = false,
            horizontalPadding = null,
            floatingActionButton = {
                CouiFab(
                    icon = CouiIcons.Refresh,
                    contentDescription = "重新搜索",
                    onClick = { loader.loadFirstPage() },
                )
            },
        ) { contentPadding ->
            CouiPullToRefresh(
                isRefreshing = snapshot.refreshing,
                onRefresh = loader::refresh,
                scrollBehavior = scrollBehavior,
                contentPadding = contentPadding,
            ) {
                LazyColumn(
                    state = lazyState,
                    modifier = Modifier
                        .fillMaxSize()
                        .readableWidth()
                        .couiPageScroll()
                        .consumeWindowInsets(contentPadding),
                    contentPadding = contentPadding,
                    flingBehavior = rememberCOUIFlingBehavior(),
                ) {
                    item(key = "coui-search-filters") {
                        CouiSearchFilterRow(
                            order = order,
                            time = time,
                            onOrder = { order = it },
                            onTime = { time = it },
                        )
                    }
                    when (val current = state) {
                        ListState.Loading -> item(key = "coui-search-loading") {
                            CouiLoadingGroup()
                        }

                        is ListState.Failed -> item(key = "coui-search-error") {
                            CouiScreenFailureGroup(
                                message = current.message,
                                expired = current.sessionExpired,
                                onRetry = { loader.loadFirstPage() },
                                onOpenLogin = onOpenLogin,
                            )
                        }

                        is ListState.Loaded -> {
                            if (current.feed.items.isEmpty()) {
                                item(key = "coui-search-empty") {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        CouiEmptyGroup(text = COUI_SEARCH_EMPTY)
                                        PagerFooterPreference(
                                            summary = COUI_SEARCH_EMPTY_HINT,
                                            withExtraMarginBottom = false,
                                        )
                                    }
                                }
                            } else {
                                feedRowItems(
                                    count = current.feed.items.size,
                                    columns = feedColumns,
                                    keyPrefix = "coui-search-result-",
                                    keyOf = { index -> current.feed.items[index].id },
                                ) { index ->
                                    val result = current.feed.items[index]
                                    CouiSearchResultRow(
                                        result = result,
                                        onClick = { onOpenAlbum(result.id) },
                                    )
                                }
                                item(key = "coui-search-footer") {
                                    Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                        if (clickPaging) {
                                            CouiPagingGroup(
                                                page = current.feed.page,
                                                pageCount = current.feed.pageCount,
                                                hasMore = current.feed.hasMore,
                                                onJumpPage = { jumpToPage(it) },
                                            )
                                        } else {
                                            CouiLoadMoreFooter(
                                                loading = current.feed.loadingMore,
                                                error = if (current.feed.loadMoreFailed) COUI_SEARCH_LOAD_MORE_FAILED else null,
                                                endReached = !current.feed.hasMore,
                                                onRetry = { loader.loadMore() },
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
        GlassSnackbarHost(
            state = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** 排序与时间范围两排胶囊：都送服务端，所以选中即整页重查。 */
@Composable
private fun CouiSearchFilterRow(
    order: BrowseOrder,
    time: BrowseTime,
    onOrder: (BrowseOrder) -> Unit,
    onTime: (BrowseTime) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = CouiLayoutTokens.CategoryTopSpacing, bottom = CouiLayoutTokens.GroupSpacing),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = CouiLayoutTokens.HorizontalMargin),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items = BrowseOrder.entries.toList(), key = { it.name }) { option ->
                CouiSearchFilterChip(
                    label = option.label,
                    selected = option == order,
                    onClick = { onOrder(option) },
                )
            }
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = CouiLayoutTokens.HorizontalMargin),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items = BrowseTime.entries.toList(), key = { it.name }) { option ->
                CouiSearchFilterChip(
                    label = option.label,
                    selected = option == time,
                    onClick = { onTime(option) },
                )
            }
        }
    }
}

@Composable
private fun CouiSearchFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = COUITheme.colorScheme
    val shape = RoundedCornerShape(LocalSkin.current.shapes.controlCorner)
    Box(
        modifier = Modifier
            .couiPressable(
                shape = shape,
                backgroundColor = if (selected) scheme.primary.copy(alpha = 0.15f) else scheme.card,
                minTouchTarget = CouiMinTouchTarget,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        CouiBodyText(
            text = label,
            color = if (selected) scheme.primaryText else scheme.labelPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 结果行：封面 + 标题 + 作者 + 标签，整行可点进相册详情。 */
@Composable
private fun CouiSearchResultRow(
    result: SearchResult,
    onClick: () -> Unit,
) {
    val scheme = COUITheme.colorScheme
    Column(modifier = Modifier.padding(horizontal = CouiLayoutTokens.HorizontalMargin)) {
        CouiWorkRowCard(
            coverUrl = result.coverUrl,
            title = result.title,
            onClick = onClick,
            albumId = result.id,
            subtitle = result.authors.joinToString(" / ").ifBlank { null },
            category = result.category,
            coverContentDescription = result.title,
        ) {
            if (result.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = result.tags.take(3).joinToString(" · "),
                    style = COUITheme.textStyles.bodyS,
                    color = scheme.labelSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Start,
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}
