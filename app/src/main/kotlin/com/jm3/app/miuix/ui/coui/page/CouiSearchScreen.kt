// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.page

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.itos.coui.effect.SpotlightPreset
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.layout.Footer
import com.itos.coui.scroll.rememberCouiOverscrollEffect
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme as CouiKitTheme
import com.itos.coui.topappbar.CouiTopAppBarDefaults
import com.itos.coui.topappbar.LargeTopAppBar
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.ui.ListState
import com.jm3.app.miuix.ui.LocalWideLayout
import com.jm3.app.miuix.ui.PaginationModeReloadEffect
import com.jm3.app.miuix.ui.asListState
import com.jm3.app.miuix.ui.couiPageExtraBottomDp
import com.jm3.app.miuix.ui.coui.component.CouiBodyText
import com.jm3.app.miuix.ui.coui.component.CouiAppScaffold
import com.jm3.app.miuix.ui.coui.component.CouiEmptyGroup
import com.jm3.app.miuix.ui.coui.component.CouiFab
import com.jm3.app.miuix.ui.coui.component.CouiLoadMoreFooter
import com.jm3.app.miuix.ui.coui.component.CouiLoadingGroup
import com.jm3.app.miuix.ui.coui.component.CouiMinTouchTarget
import com.jm3.app.miuix.ui.coui.component.CouiPagingGroup
import com.jm3.app.miuix.ui.coui.component.CouiScreenFailureGroup
import com.jm3.app.miuix.ui.coui.component.CouiTopBarBackButton
import com.jm3.app.miuix.ui.coui.component.CouiTopBarTitle
import com.jm3.app.miuix.ui.coui.component.CouiWorkRowCard
import com.jm3.app.miuix.ui.feedRowItems
import com.jm3.app.miuix.ui.coui.component.couiPressable
import com.jm3.app.miuix.ui.coui.component.couiScreenContentPadding
import com.jm3.app.miuix.ui.coui.component.couiTopAppBarColors
import com.jm3.app.miuix.ui.coui.icon.CouiIcons
import com.jm3.app.miuix.ui.coui.theme.CouiStrings
import com.jm3.app.miuix.ui.coui.theme.CouiTheme
import com.jm3.app.miuix.ui.readableWidth
import com.jm3.app.miuix.ui.route.AppRoutes
import com.jm3.app.miuix.ui.skin.LocalSkin
import com.jm3.core.appendedBy
import com.jm3.core.model.BrowseOrder
import com.jm3.core.model.BrowseTime
import com.jm3.core.model.SearchResult
import com.jm3.core.model.SearchScope
import com.jm3.core.paging.pagerTargetPage
import com.jm3.core.session.SessionOutcome
import com.jm3.core.settings.PaginationMode
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon

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
    val lazyState = rememberLazyListState()
    val scheme = CouiKitTheme.colorScheme
    val pageMode = deps.paging.paginationMode
    val clickPaging = pageMode == PaginationMode.CLICK

    var state by remember(query, scope) { mutableStateOf<ListState>(ListState.Loading) }
    var requestToken by remember { mutableIntStateOf(0) }
    var order by rememberSaveable(query, scope.name) { mutableStateOf(BrowseOrder.LATEST) }
    var time by rememberSaveable(query, scope.name) { mutableStateOf(BrowseTime.ALL) }

    fun loadFirstPage() {
        requestToken++
        val token = requestToken
        state = ListState.Loading
        coroutineScope.launch {
            val next = authCall.call { gateway.search(query, 1, order, scope, time) }
                .asListState(CouiStrings.LoadFailed)
            if (token != requestToken) return@launch
            state = next
            if (next is ListState.Loaded && next.feed.items.isNotEmpty()) lazyState.scrollToItem(0)
        }
    }

    fun jumpToPage(targetPage: Int) {
        val current = (state as? ListState.Loaded)?.feed ?: return
        val resolved = pagerTargetPage(current.page, targetPage, current.pageCount)
        if (resolved == current.page) return
        requestToken++
        val token = requestToken
        state = ListState.Loading
        coroutineScope.launch {
            val next = authCall.call { gateway.search(query, resolved, order, scope, time) }
                .asListState(CouiStrings.LoadFailed)
            if (token != requestToken) return@launch
            state = next
            if (next is ListState.Loaded && next.feed.items.isNotEmpty()) lazyState.scrollToItem(0)
        }
    }

    fun loadMore() {
        val current = (state as? ListState.Loaded)?.feed ?: return
        val requested = current.beginLoadMore()
        if (requested === current) return
        val token = requestToken
        state = ListState.Loaded(requested)
        coroutineScope.launch {
            val outcome = authCall.call { gateway.search(query, requested.page + 1, order, scope, time) }
            if (token != requestToken) return@launch
            val updated = when (outcome) {
                is SessionOutcome.Ok -> requested.appendedBy(outcome.value)
                else -> requested.failLoadMore()
            }
            state = ListState.Loaded(updated)
        }
    }

    LaunchedEffect(query, scope, order, time) { loadFirstPage() }

    PaginationModeReloadEffect(pageMode) { loadFirstPage() }

    CouiPrefetchEffect(
        listState = lazyState,
        enabled = !clickPaging,
        restartKey = Triple(query, order, time),
    ) { lastIndex, totalItemsCount, prefetchRows ->
        val feed = (state as? ListState.Loaded)?.feed ?: return@CouiPrefetchEffect
        if (!feed.canLoadMore || feed.loadMoreFailed) return@CouiPrefetchEffect
        if (!shouldPrefetch(lastIndex, totalItemsCount, prefetchRows)) return@CouiPrefetchEffect
        loadMore()
    }

    val scrollBehavior = CouiTopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        canScroll = { lazyState.canScrollForward || lazyState.canScrollBackward },
    )
    val title = if (scope == SearchScope.SITE) "搜索：$query" else "${scope.label}：$query"
    val feed = (state as? ListState.Loaded)?.feed

    Box(modifier = Modifier.fillMaxSize()) {
        CouiAppScaffold(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                LargeTopAppBar(
                    title = { CouiTopBarTitle(text = title) },
                    navigationIcon = { CouiTopBarBackButton(onClick = onBack) },
                    scrollBehavior = scrollBehavior,
                    colors = couiTopAppBarColors(),
                )
            },
            floatingActionButton = {
                CouiFab(
                    icon = CouiIcons.Refresh,
                    contentDescription = "重新搜索",
                    onClick = { loadFirstPage() },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                )
            },
        ) { innerPadding ->
            // 折叠位移留到测量期读取：组合期算成字面量会把首帧的顶栏高度冻结住
            val contentPadding = couiScreenContentPadding(
                scrollBehavior = scrollBehavior,
                innerPadding = innerPadding,
                horizontalPadding = null,
                extraBottom = couiPageExtraBottomDp(hasFab = true).dp,
            )
            // 行卡列数只读 policy 的派生值：手机断点为 1，内容宽够 840dp 才排两列
            val feedColumns = LocalWideLayout.current.feedColumns
            LazyColumn(
                state = lazyState,
                modifier = Modifier
                    .fillMaxSize()
                    .readableWidth()
                    .consumeWindowInsets(innerPadding),
                contentPadding = contentPadding,
                overscrollEffect = rememberCouiOverscrollEffect(),
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
                            onRetry = { loadFirstPage() },
                            onOpenLogin = onOpenLogin,
                        )
                    }

                    is ListState.Loaded -> {
                        if (current.feed.items.isEmpty()) {
                            item(key = "coui-search-empty") {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    CouiEmptyGroup(text = COUI_SEARCH_EMPTY)
                                    Footer(text = COUI_SEARCH_EMPTY_HINT)
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
                                            onRetry = { loadMore() },
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
            .padding(top = CouiPageDefaults.categoryTopSpacing, bottom = CouiPageDefaults.groupSpacing),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = CouiPageDefaults.horizontalMargin),
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
            contentPadding = PaddingValues(horizontal = CouiPageDefaults.horizontalMargin),
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
    val scheme = CouiKitTheme.colorScheme
    val shape = RoundedCornerShape(LocalSkin.current.shapes.controlCorner)
    Box(
        modifier = Modifier
            .couiPressable(
                shape = shape,
                backgroundColor = if (selected) scheme.accent.copy(alpha = 0.15f) else scheme.card,
                minTouchTarget = CouiMinTouchTarget,
                spotlight = SpotlightPreset.ChipTab,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        CouiBodyText(
            text = label,
            color = if (selected) scheme.accent else scheme.onSurface,
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
    val scheme = CouiKitTheme.colorScheme
    Column(modifier = Modifier.padding(horizontal = CouiPageDefaults.horizontalMargin)) {
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
                    kind = CouiTextKind.GroupFooter,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Start,
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}
