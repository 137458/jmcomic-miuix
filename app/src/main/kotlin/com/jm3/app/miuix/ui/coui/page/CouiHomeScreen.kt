// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.page

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.Dialog
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.layout.Divider
import com.itos.coui.layout.Footer
import com.itos.coui.layout.Group
import com.itos.coui.layout.Item
import com.itos.coui.scroll.rememberCouiOverscrollEffect
import com.itos.coui.search.SearchBar
import com.itos.coui.search.rememberCouiSearchBarState
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme
import com.itos.coui.topappbar.CollapsingTopAppBar
import com.itos.coui.topappbar.rememberCouiCollapsingAppBarState
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.ui.HomeSideData
import com.jm3.app.miuix.ui.ListState
import com.jm3.app.miuix.ui.PaginationModeReloadEffect
import com.jm3.app.miuix.ui.SessionListKey
import com.jm3.app.miuix.ui.asListState
import com.jm3.app.miuix.ui.coui.component.CouiAppScaffold
import com.jm3.app.miuix.ui.coui.component.CouiEmptyGroup
import com.jm3.app.miuix.ui.coui.component.CouiErrorGroup
import com.jm3.app.miuix.ui.coui.component.CouiFab
import com.jm3.app.miuix.ui.coui.component.CouiGroupRow
import com.jm3.app.miuix.ui.coui.component.CouiGroupTitle
import com.jm3.app.miuix.ui.coui.component.CouiLoadMoreFooter
import com.jm3.app.miuix.ui.coui.component.CouiLoadingGroup
import com.jm3.app.miuix.ui.coui.component.CouiPagingGroup
import com.jm3.app.miuix.ui.coui.component.CouiSegmentedCapsules
import com.jm3.app.miuix.ui.coui.component.LocalCouiFallbackContentColor
import com.jm3.app.miuix.ui.coui.icon.CouiIcons
import com.jm3.app.miuix.ui.isShowingSearchResults
import com.jm3.app.miuix.ui.mutateSearchHistory
import com.jm3.app.miuix.ui.orDegrade
import com.jm3.app.miuix.ui.readSearchHistory
import com.jm3.core.albumCodeOf
import com.jm3.core.appendedBy
import com.jm3.core.model.BrowseOrder
import com.jm3.core.model.SearchPage
import com.jm3.core.model.SearchResult
import com.jm3.core.model.SearchScope
import com.jm3.core.paging.PagedFeed
import com.jm3.core.session.SessionOutcome
import com.jm3.core.settings.PaginationMode
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon

private const val SERIAL_CAROUSEL_LIMIT = 12
private const val HOT_TAG_COLLAPSED = 8

/** 列表头部固定占两个 item：热词行 / 搜索头，以及分组标题行；feed 行从下标 2 起。 */
private const val HOME_FEED_ROW_START = 2

private const val HOME_LOAD_MORE_FAILED = "更多内容没有加载成功"

private const val SEARCH_LOAD_MORE_FAILED = "更多结果没有加载成功"

private val SortLabels: List<String> = BrowseOrder.entries.map { it.label }

/**
 * COUI 首页：折叠顶栏 + 搜索栏 + 分组式发现流 / 搜索结果。
 *
 * 数据编排与 HyperOS 首页同源：同一份 SessionCache 键、同一个 PagedFeed 状态机、
 * 同一套 AuthCall 结局翻译，切皮肤后列表与输入状态不会丢。
 *
 * 视觉全部交给 COUI 原厂组件：顶栏用 CollapsingTopAppBar，正文按分组语义拆成 LazyColumn 的
 * item，分组标题与逐行卡片由 CouiGroupTitle / CouiGroupRow 复刻。COUI 没有下拉刷新组件，
 * 刷新因此变成分组标题行上的显式按钮。
 *
 * 列表走 LazyColumn 而不是 Group + couiVerticalScroll：分组内容一多，Column 会把所有行都组合
 * 出来。代价是短页（内容不足一屏）失去 COUI 的整页强回弹——LazyColumn 的回弹只作用在内容
 * 边界上，这是虚拟化换来的取舍，长列表的滚动与预取行为不变。
 */
@Composable
fun CouiHomeScreen(
    deps: ScreenDeps,
    onOpenAlbum: (String) -> Unit,
    onOpenWeek: () -> Unit,
) {
    // 网关与鉴权统一从 ScreenDeps 取，不额外收同源参数
    val gateway = deps.gateway
    val authCall = deps.authCall
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    var query by rememberSaveable { mutableStateOf("") }
    var activeQuery by rememberSaveable { mutableStateOf("") }
    var searchByTag by rememberSaveable { mutableStateOf(false) }
    var orderValue by rememberSaveable { mutableStateOf(BrowseOrder.LATEST.apiValue) }
    // 展开态必须由页面持有：CouiSearchBarState.expanded 只读，库只按宿主给的目标做动画
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    val sessionCache = deps.sessionCache
    var feed by remember { mutableStateOf(sessionCache.stateOf(SessionListKey.HomeFeed)) }
    var results by remember { mutableStateOf(sessionCache.stateOrNull(SessionListKey.HomeSearch)) }
    var hotTags by remember { mutableStateOf(sessionCache.homeSide.hotTags) }
    var serialUpdates by remember { mutableStateOf(sessionCache.homeSide.serialUpdates) }
    var randomPicks by remember { mutableStateOf(sessionCache.homeSide.randomPicks) }
    var searchHistory by remember { mutableStateOf<List<String>>(emptyList()) }
    var showAllTags by remember { mutableStateOf(false) }
    var showSearchHelp by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var listToken by remember { mutableIntStateOf(0) }

    val lazyState = rememberLazyListState()
    val layoutDirection = LocalLayoutDirection.current
    // 折叠顶栏只在列表还能滚的时候才收，短页保持展开
    val header = rememberCouiCollapsingAppBarState(
        canScroll = { lazyState.canScrollForward || lazyState.canScrollBackward },
    )
    val searchState = rememberCouiSearchBarState(expanded = searchExpanded)

    val order = BrowseOrder.entries.firstOrNull { it.apiValue == orderValue } ?: BrowseOrder.LATEST
    val mode = deps.preferences.paginationMode
    val clickPaging = mode == PaginationMode.CLICK
    val searching = isShowingSearchResults(activeQuery, results)

    fun rememberFeed(next: ListState) {
        feed = next
        sessionCache.remember(SessionListKey.HomeFeed, next)
    }

    fun rememberResults(next: ListState) {
        results = next
        sessionCache.remember(SessionListKey.HomeSearch, next)
    }

    fun rememberSide(tags: List<String>, updates: List<SearchResult>, picks: List<SearchResult>) {
        hotTags = tags
        serialUpdates = updates
        randomPicks = picks
        sessionCache.homeSide = HomeSideData(tags, updates, picks)
    }

    fun requestSearch(page: Int, byTag: Boolean, text: String, sort: BrowseOrder): SearchPage =
        gateway.search(text, page, sort, if (byTag) SearchScope.TAG else SearchScope.SITE)

    fun mutateHistory(block: suspend () -> Unit) {
        scope.launch {
            searchHistory = mutateSearchHistory(deps.settings, { searchHistory }) { block() }
        }
    }

    fun loadFeed(page: Int, token: Int) {
        scope.launch {
            val next = authCall.call { gateway.latest(page) }
            if (token != listToken) return@launch
            rememberFeed(next.asListState("加载失败"))
        }
    }

    fun runSearch(text: String, byTag: Boolean, sort: BrowseOrder) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        activeQuery = trimmed
        searchByTag = byTag
        orderValue = sort.apiValue
        listToken++
        val token = listToken
        rememberResults(ListState.Loading)
        mutateHistory { deps.settings.addSearchHistory(trimmed) }
        scope.launch {
            val next = authCall.call { requestSearch(1, byTag, trimmed, sort) }
            if (token != listToken) return@launch
            rememberResults(next.asListState("网络错误"))
        }
    }

    fun clearSearch() {
        query = ""
        activeQuery = ""
        results = null
        sessionCache.forget(SessionListKey.HomeSearch)
        searchByTag = false
        listToken++
        if (feed !is ListState.Loaded) loadFeed(1, listToken)
    }

    fun dismissSearchInput() {
        focusManager.clearFocus()
        keyboard?.hide()
    }

    fun collapseSearch() {
        searchExpanded = false
        dismissSearchInput()
        if (searching) clearSearch()
    }

    fun openAlbum(albumId: String) {
        dismissSearchInput()
        onOpenAlbum(albumId)
    }

    fun submitSearch() {
        val q = query.trim()
        val code = albumCodeOf(q)
        when {
            q.isEmpty() -> Unit
            code != null -> openAlbum(code)
            else -> runSearch(q, byTag = false, sort = order)
        }
    }

    fun refresh() {
        refreshing = true
        listToken++
        val token = listToken
        scope.launch {
            if (searching) {
                rememberResults(ListState.Loading)
                val text = activeQuery
                val byTag = searchByTag
                val next = authCall.call { requestSearch(1, byTag, text, order) }
                if (token == listToken) rememberResults(next.asListState("网络错误"))
            } else {
                val next = authCall.call { gateway.latest(1) }
                if (token == listToken) rememberFeed(next.asListState("加载失败"))
            }
            refreshing = false
        }
    }

    fun activeFeed(): PagedFeed<SearchResult>? =
        ((if (searching) results else feed) as? ListState.Loaded)?.feed

    fun publishTransient(next: ListState) {
        if (searching) results = next else feed = next
    }

    fun loadMore() {
        val current = activeFeed() ?: return
        val requested = current.beginLoadMore()
        if (requested === current) return
        val token = listToken
        val nextOrder = order
        val text = activeQuery
        val byTag = searchByTag
        publishTransient(ListState.Loaded(requested))
        scope.launch {
            val next = authCall.call {
                if (searching) requestSearch(requested.page + 1, byTag, text, nextOrder)
                else gateway.latest(requested.page + 1)
            }
            if (token != listToken) return@launch
            val updated = when (next) {
                is SessionOutcome.Ok -> requested.appendedBy(next.value)
                else -> requested.failLoadMore()
            }
            if (searching) rememberResults(ListState.Loaded(updated)) else rememberFeed(ListState.Loaded(updated))
        }
    }

    fun jumpToPage(target: Int) {
        val current = activeFeed() ?: return
        if (target < 1 || target > (current.pageCount ?: 1)) return
        val text = activeQuery
        val byTag = searchByTag
        val nextOrder = order
        listToken++
        val token = listToken
        if (searching) rememberResults(ListState.Loading) else rememberFeed(ListState.Loading)
        scope.launch {
            val next = authCall.call {
                if (searching) requestSearch(target, byTag, text, nextOrder) else gateway.latest(target)
            }
            if (token != listToken) return@launch
            val mapped = next.asListState("网络错误")
            if (searching) rememberResults(mapped) else rememberFeed(mapped)
            // 换页后回到列表顶部，否则用户会停在新一页的中段
            if (mapped is ListState.Loaded) lazyState.scrollToItem(0)
        }
    }

    fun openRandom() {
        val candidates = randomPicks.ifEmpty { activeFeed()?.items.orEmpty() }
        candidates.randomOrNull()?.let { openAlbum(it.id) }
    }

    BackHandler(enabled = searchExpanded || searching) { collapseSearch() }

    LaunchedEffect(gateway, authCall) {
        if (!searching && feed !is ListState.Loaded) {
            listToken++
            loadFeed(1, listToken)
        }
        if (searching && results == null) {
            runSearch(activeQuery, byTag = searchByTag, sort = order)
        }
        if (sessionCache.homeSide.isEmpty) {
            scope.launch {
                val tags = authCall.call { gateway.hotTags() }.orDegrade(emptyList())
                val picks = authCall.call { gateway.randomRecommend() }.orDegrade(emptyList())
                val updates = authCall
                    .call { gateway.serialization(1) }
                    .orDegrade<SearchPage?>(null)
                    ?.items
                    ?.take(SERIAL_CAROUSEL_LIMIT)
                    .orEmpty()
                val storedHistory = readSearchHistory(deps.settings) { emptyList() }
                rememberSide(tags = tags, updates = updates, picks = picks)
                searchHistory = storedHistory
            }
        } else {
            scope.launch {
                searchHistory = readSearchHistory(deps.settings) { searchHistory }
            }
        }
    }

    PaginationModeReloadEffect(mode) { _ ->
        if (searching) rememberResults(ListState.Loading) else rememberFeed(ListState.Loading)
        refresh()
    }

    // 虚拟化后没有单一滚动位置可读，改看可见项：把「最后可见项在 feed 里的下标」交给
    // CouiFeedPolicy 判定，提前量按实测行高换算成条数；请求幂等由 beginLoadMore 保证。
    val pagingFeed by rememberUpdatedState(activeFeed())
    val latestLoadMore by rememberUpdatedState(::loadMore)
    LaunchedEffect(lazyState, clickPaging) {
        if (clickPaging) return@LaunchedEffect
        snapshotFlow {
            val visible = lazyState.layoutInfo.visibleItemsInfo
            val rowHeightDp = if (visible.isEmpty()) {
                0f
            } else {
                visible.sumOf { it.size }.toFloat() / visible.size / density.density
            }
            visible.lastOrNull()?.index to rowHeightDp
        }.collect { (lastIndex, rowHeightDp) ->
            val current = pagingFeed ?: return@collect
            if (!current.canLoadMore || current.loadMoreFailed) return@collect
            val distance = prefetchItemCount(COUI_LOAD_MORE_PREFETCH_DP.toFloat(), rowHeightDp)
            val feedIndex = (lastIndex ?: return@collect) - HOME_FEED_ROW_START
            if (shouldPrefetch(feedIndex, current.items.size, distance)) {
                latestLoadMore()
            }
        }
    }

    CouiAppScaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(header.nestedScrollConnection),
        topBar = {
            CollapsingTopAppBar(
                state = header,
                searchState = searchState,
                title = "JMComic",
                searchBar = {
                    SearchBar(
                        state = searchState,
                        query = query,
                        onQueryChange = { value ->
                            query = value
                            // 清空输入框时退回首页，避免空查询与旧结果同时存在
                            if (value.isEmpty() && searching) clearSearch()
                        },
                        onExpandedChange = { expanded ->
                            if (expanded) searchExpanded = true else collapseSearch()
                        },
                        placeholder = "搜索作品 / 作者 / 标签",
                        cancelText = "取消",
                        clearDescription = "清除搜索内容",
                        onSearch = { submitSearch() },
                    )
                },
            )
        },
        floatingActionButton = {
            // API34 的原厂 Fab 只在降级分支下发这个 local，Unspecified 会让图标不上色
            val fallback = LocalCouiFallbackContentColor.current
            val iconTint = if (fallback == Color.Unspecified) CouiTheme.colorScheme.onAccent else fallback
            CouiFab(onClick = { openRandom() }) {
                Icon(
                    imageVector = CouiIcons.Shuffle,
                    contentDescription = "随机看一部",
                    modifier = Modifier.size(24.dp),
                    tint = iconTint,
                )
            }
        },
    ) { innerPadding ->
        val appBarPadding = remember(header, innerPadding, density) {
            header.contentPadding(innerPadding, density)
        }
        // 顶栏留白改由 LazyColumn 的 contentPadding 承担，正文才能滚到折叠后的顶栏下面
        val contentPadding = remember(appBarPadding, layoutDirection) {
            PaddingValues(
                start = appBarPadding.calculateLeftPadding(layoutDirection),
                top = appBarPadding.calculateTopPadding(),
                end = appBarPadding.calculateRightPadding(layoutDirection),
                // 底部再留出 FAB 的高度，最后一行不会被浮动按钮压住
                bottom = appBarPadding.calculateBottomPadding() + 88.dp,
            )
        }
        val feedState = if (searching) results else feed
        LazyColumn(
            state = lazyState,
            modifier = Modifier.fillMaxSize().consumeWindowInsets(innerPadding),
            contentPadding = contentPadding,
            overscrollEffect = rememberCouiOverscrollEffect(),
        ) {
            // 头部两块固定占位（热词 / 搜索头 + 标题），feed 行下标因此恒从 HOME_FEED_ROW_START 起
            item(key = "coui-home-head") {
                if (searching) {
                    CouiSearchHeader(
                        sortLabels = SortLabels,
                        selectedSort = order.ordinal.coerceIn(0, SortLabels.lastIndex),
                        onSelectSort = { index ->
                            runSearch(activeQuery, searchByTag, BrowseOrder.entries[index])
                        },
                        summary = searchSummary(
                            query = activeQuery,
                            byTag = searchByTag,
                            total = (results as? ListState.Loaded)?.feed?.total,
                        ),
                    )
                } else {
                    CouiTagRow(
                        tags = hotTags,
                        expanded = showAllTags,
                        onToggleExpanded = { showAllTags = !showAllTags },
                        onPick = { tag ->
                            query = tag
                            runSearch(tag, byTag = true, sort = order)
                        },
                    )
                }
            }
            item(key = "coui-home-title") {
                CouiGroupTitle(
                    title = if (searching) "搜索结果" else "最近更新",
                    actions = {
                        CouiRefreshAction(refreshing = refreshing, onRefresh = { refresh() })
                    },
                )
            }
            when (val current = feedState) {
                null, is ListState.Loading -> item(key = "coui-home-loading") {
                    CouiLoadingGroup(text = if (searching) "正在搜索…" else "正在加载最新上架…")
                }

                is ListState.Failed -> item(key = "coui-home-error") {
                    CouiErrorGroup(
                        message = current.message.ifBlank { if (searching) "搜索失败" else "加载失败" },
                        onRetry = {
                            if (searching) {
                                runSearch(activeQuery, searchByTag, order)
                            } else {
                                refresh()
                            }
                        },
                    )
                }

                is ListState.Loaded -> {
                    val loadedFeed = current.feed
                    val items = loadedFeed.items
                    if (items.isEmpty()) {
                        item(key = "coui-home-empty") {
                            CouiEmptyGroup(text = if (searching) "没有找到作品" else "还没有内容")
                        }
                        item(key = "coui-home-empty-hint") {
                            Footer(
                                text = if (searching) {
                                    "换个关键词或标签再试"
                                } else {
                                    "点右上角刷新再试一次"
                                },
                            )
                        }
                    } else {
                        // key 用作品 id：刷新后行身份不变，滚动位置才能跟着内容走
                        itemsIndexed(
                            items = items,
                            key = { _, album -> "coui-home-row-${album.id}" },
                        ) { index, album ->
                            CouiGroupRow(isFirst = index == 0, isLast = index == items.lastIndex) {
                                CouiAlbumRow(album = album, onClick = { openAlbum(album.id) })
                            }
                        }
                        item(key = "coui-home-paging") {
                            if (clickPaging) {
                                CouiPagingGroup(
                                    page = loadedFeed.page,
                                    pageCount = loadedFeed.pageCount,
                                    hasMore = loadedFeed.hasMore,
                                    onJumpPage = ::jumpToPage,
                                )
                            } else {
                                CouiLoadMoreFooter(
                                    loading = loadedFeed.loadingMore,
                                    error = if (loadedFeed.loadMoreFailed) {
                                        if (searching) SEARCH_LOAD_MORE_FAILED else HOME_LOAD_MORE_FAILED
                                    } else {
                                        null
                                    },
                                    endReached = !loadedFeed.hasMore,
                                    onRetry = { loadMore() },
                                )
                            }
                        }
                    }
                }
            }
            // 侧栏三块只在首页态出现，搜索结果下保持与旧页一致的清爽
            if (!searching) {
                if (serialUpdates.isNotEmpty()) {
                    item(key = "coui-home-serial") {
                        Group(title = "连更追踪") {
                            CouiAlbumRows(feed = serialUpdates.asFeed(), onOpenAlbum = ::openAlbum)
                        }
                    }
                }
                if (randomPicks.isNotEmpty()) {
                    item(key = "coui-home-random") {
                        Group(title = "随机推荐") {
                            CouiAlbumRows(feed = randomPicks.asFeed(), onOpenAlbum = ::openAlbum)
                        }
                    }
                }
                item(key = "coui-home-history") {
                    Group(
                        title = "首页发现",
                        headerActions = {
                            if (searchHistory.isNotEmpty()) {
                                CouiHeaderTextAction(text = "清除") {
                                    mutateHistory { deps.settings.clearSearchHistory() }
                                }
                            }
                        },
                    ) {
                        if (searchHistory.isEmpty()) {
                            CouiMessageRow(title = "还没有搜索记录", summary = "搜索或点热词后，关键词会留在这里")
                        } else {
                            searchHistory.forEachIndexed { index, word ->
                                if (index > 0) Divider()
                                Item(
                                    title = {
                                        Text(
                                            text = word,
                                            kind = CouiTextKind.PreferenceTitle,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    summary = {
                                        Text(text = "点按重新搜索", kind = CouiTextKind.PreferenceSummary)
                                    },
                                    trailing = {
                                        CouiRowIconAction(
                                            icon = CouiIcons.Close,
                                            description = "删除这条搜索记录",
                                            onClick = {
                                                mutateHistory { deps.settings.removeSearchHistory(word) }
                                            },
                                        )
                                    },
                                    onClick = {
                                        query = word
                                        runSearch(word, byTag = false, sort = order)
                                    },
                                )
                            }
                        }
                    }
                }
            }
            item(key = "coui-home-more") {
                Group(title = "更多") {
                    Item(
                        icon = { CouiAccentIcon(CouiIcons.Star) },
                        title = { Text(text = "每周必看", kind = CouiTextKind.PreferenceTitle) },
                        summary = { Text(text = "按周汇总的连载更新", kind = CouiTextKind.PreferenceSummary) },
                        onClick = {
                            dismissSearchInput()
                            onOpenWeek()
                        },
                    )
                    Divider()
                    Item(
                        icon = { CouiAccentIcon(CouiIcons.Info) },
                        title = { Text(text = "搜索语法", kind = CouiTextKind.PreferenceTitle) },
                        summary = { Text(text = "空格 / + / - 组合关键词", kind = CouiTextKind.PreferenceSummary) },
                        onClick = { showSearchHelp = true },
                    )
                }
            }
        }
    }

    if (showSearchHelp) {
        Dialog(
            onDismissRequest = { showSearchHelp = false },
            confirmAction = CouiDialogAction(text = "知道了", onClick = { showSearchHelp = false }),
            title = "搜索语法",
            message = "空格、加号与减号决定关键词之间的关系。",
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CouiSyntaxRow(expression = "全彩 人妻", meaning = "同时包含这两个关键词（空格分隔）")
                CouiSyntaxRow(expression = "全彩 +人妻", meaning = "两个关键词都必须包含")
                CouiSyntaxRow(expression = "全彩 -人妻", meaning = "包含「全彩」，排除「人妻」")
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
        modifier = Modifier.padding(
            horizontal = CouiPageDefaults.horizontalMargin,
            vertical = CouiPageDefaults.categoryTopSpacing,
        ),
    )
    Text(
        text = summary,
        kind = CouiTextKind.Description,
        color = CouiTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 8.dp),
    )
}

/** 热词胶囊行：COUI 没有 chip 组件，这里只借用原厂胶囊形状与容器色，不做卡片化。 */
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
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = CouiPageDefaults.horizontalMargin,
                vertical = CouiPageDefaults.categoryTopSpacing,
            ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        shown.forEach { tag ->
            CouiTagCapsule(text = tag, onClick = { onPick(tag) })
        }
        if (tags.size > HOT_TAG_COLLAPSED) {
            CouiTagCapsule(
                text = if (expanded) "收起" else "更多",
                onClick = onToggleExpanded,
            )
        }
    }
}

@Composable
private fun CouiTagCapsule(text: String, onClick: () -> Unit) {
    val colors = CouiTheme.colorScheme
    Box(
        modifier = Modifier
            .clip(CouiCapsuleShape)
            .background(colors.container)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, kind = CouiTextKind.Body, color = colors.onSurface)
    }
}

/** 分组内的整段作品行：侧栏这几组条数有限，仍由 Group 的整卡包住。 */
@Composable
private fun CouiAlbumRows(feed: PagedFeed<SearchResult>, onOpenAlbum: (String) -> Unit) {
    feed.items.forEachIndexed { index, album ->
        if (index > 0) Divider()
        CouiAlbumRow(album = album, onClick = { onOpenAlbum(album.id) })
    }
}

/** 单个作品行：封面当 icon，标题/作者走 Item 自带的两级字阶。虚拟化列表逐行下发时用它。 */
@Composable
private fun CouiAlbumRow(album: SearchResult, onClick: () -> Unit) {
    Item(
        icon = { CouiAlbumCover(url = album.coverUrl, title = album.title) },
        iconSize = 52.dp,
        title = {
            Text(
                text = album.title,
                kind = CouiTextKind.PreferenceTitle,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        summary = {
            Text(
                text = metaOf(album),
                kind = CouiTextKind.PreferenceSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        onClick = onClick,
    )
}

@Composable
private fun CouiAlbumCover(url: String?, title: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(CouiTheme.colorScheme.container),
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** 分组标题行里的刷新入口：COUI 没有下拉刷新，加载反馈只靠文案与禁用态。 */
@Composable
private fun CouiRefreshAction(refreshing: Boolean, onRefresh: () -> Unit) {
    val colors = CouiTheme.colorScheme
    val tint = if (refreshing) colors.onSurfaceVariant else colors.accent
    Row(
        modifier = Modifier
            .clip(CouiCapsuleShape)
            .clickable(enabled = !refreshing, onClick = onRefresh)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = CouiIcons.Refresh,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = tint,
        )
        Text(text = if (refreshing) "刷新中" else "刷新", kind = CouiTextKind.Description, color = tint)
    }
}

@Composable
private fun CouiHeaderTextAction(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        kind = CouiTextKind.Description,
        color = CouiTheme.colorScheme.accent,
        modifier = Modifier
            .clip(CouiCapsuleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

/** 行尾的图标动作：40dp 命中区里放 16dp 图标，避免误触整行。 */
@Composable
private fun CouiRowIconAction(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CouiCapsuleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            modifier = Modifier.size(16.dp),
            tint = CouiTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CouiAccentIcon(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(CouiPageDefaults.iconSize),
        tint = CouiTheme.colorScheme.accent,
    )
}

@Composable
private fun CouiMessageRow(title: String, summary: String? = null, onClick: (() -> Unit)? = null) {
    val summaryText = summary
    val summarySlot: (@Composable () -> Unit)? = if (summaryText == null) {
        null
    } else {
        { Text(text = summaryText, kind = CouiTextKind.PreferenceSummary) }
    }
    Item(
        title = { Text(text = title, kind = CouiTextKind.PreferenceTitle) },
        summary = summarySlot,
        onClick = onClick,
    )
}

@Composable
private fun CouiSyntaxRow(expression: String, meaning: String) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = expression, kind = CouiTextKind.PreferenceTitle)
        Text(
            text = meaning,
            kind = CouiTextKind.PreferenceSummary,
            color = CouiTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 搜索摘要：标签搜索要标明是标签，结果总数只在服务端给出时显示。 */
private fun searchSummary(query: String, byTag: Boolean, total: Int?): String {
    val head = if (byTag) "标签：$query" else "“$query”"
    return if (total != null && total > 0) "$head · 共 $total 个结果" else head
}

/** 行摘要：作者加前两个标签，标签比作者更能说明题材与更新方向。 */
private fun metaOf(album: SearchResult): String {
    val authors = album.authors.joinToString(" / ").ifBlank { "未知作者" }
    val tags = album.tags.take(2).joinToString(" / ")
    return if (tags.isEmpty()) authors else "$authors · $tags"
}

/** 侧栏列表不是分页数据，包一层空的 PagedFeed 让它们与主列表共用同一套行渲染。 */
private fun List<SearchResult>.asFeed(): PagedFeed<SearchResult> =
    PagedFeed<SearchResult>(items = this, page = 1, hasMore = false)
