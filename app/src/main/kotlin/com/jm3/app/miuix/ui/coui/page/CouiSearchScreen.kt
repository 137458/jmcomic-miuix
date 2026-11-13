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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.jm3.app.miuix.ui.list.ListLoadState
import com.jm3.app.miuix.ui.list.SessionListLoader
import com.jm3.app.miuix.ui.list.SessionListSnapshot
import com.jm3.app.miuix.ui.readableWidth
import com.jm3.app.miuix.ui.route.AppRoutes
import com.jm3.app.miuix.ui.skin.LocalSkin
import com.jm3.core.model.BrowseOrder
import com.jm3.core.model.BrowseTime
import com.jm3.core.model.SearchPage
import com.jm3.core.model.SearchResult
import com.jm3.core.model.SearchScope
import com.jm3.core.paging.PageSlice
import com.jm3.core.paging.pagerTargetPage
import com.jm3.core.session.SessionOutcome
import com.jm3.core.settings.PaginationMode
import com.jm3.core.toSlice
import top.yukonga.miuix.kmp.basic.Icon

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
    val lazyState = rememberLazyListState()
    val scheme = CouiKitTheme.colorScheme
    val pageMode = deps.paging.paginationMode
    val clickPaging = pageMode == PaginationMode.CLICK
    // 行卡列数只读 policy 的派生值：手机断点为 1，内容宽够 840dp 才排两列
    val feedColumns = LocalWideLayout.current.feedColumns

    var order by rememberSaveable(query, scope.name) { mutableStateOf(BrowseOrder.LATEST) }
    var time by rememberSaveable(query, scope.name) { mutableStateOf(BrowseTime.ALL) }

    // 代次、在途标记、翻页游标与失败落点都收进 loader：本页只交「取哪一页」与重试入口
    val loader = remember(query, scope, order, time) {
        SessionListLoader(
            scope = coroutineScope,
            idOf = { item: SearchResult -> item.id },
            failureMessage = CouiStrings.LoadFailed,
            fetchPage = { page ->
                authCall.call { gateway.search(query, page, order, scope, time) }.toCouiPageSlice()
            },
            notice = {},
        )
    }
    val snapshot by loader.snapshot.collectAsState()
    // 渲染仍按 ListState 三态走：翻译留在页内，公共三态件不必为加载编排改签名
    val state = snapshot.toListState()
    // 换筛选或跳页后回到列表顶部：滚动位置是页面的事，不进 loader
    var pendingScrollTop by remember { mutableStateOf(false) }

    LaunchedEffect(loader) {
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
        val current = snapshot.feed
        val resolved = pagerTargetPage(current.page, targetPage, current.pageCount)
        if (resolved == current.page) return
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

    val scrollBehavior = CouiTopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        canScroll = { lazyState.canScrollForward || lazyState.canScrollBackward },
    )
    val title = if (scope == SearchScope.SITE) "搜索：$query" else "${scope.label}：$query"

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
                    onClick = { loader.loadFirstPage() },
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
                            onRetry = { loader.loadFirstPage() },
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

/** 加载态 → 渲染三态：页面画的是 [ListState]，编排只认 [ListLoadState]。 */
private fun SessionListSnapshot<SearchResult>.toListState(): ListState =
    when (val current = state) {
        ListLoadState.Loading -> ListState.Loading
        ListLoadState.Loaded -> ListState.Loaded(feed)
        is ListLoadState.Failed -> ListState.Failed(current.message, current.sessionExpired)
    }

/** 网关结局的外层映射：一页结果的解码在 core 的 toSlice，这里只补结局分支。 */
private fun SessionOutcome<SearchPage>.toCouiPageSlice(): SessionOutcome<PageSlice<SearchResult>> =
    when (this) {
        is SessionOutcome.Ok -> SessionOutcome.Ok(value.toSlice())
        SessionOutcome.Expired -> SessionOutcome.Expired
        is SessionOutcome.Failed -> this
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
