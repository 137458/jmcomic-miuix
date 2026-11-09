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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.Dialog
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.layout.Divider
import com.itos.coui.layout.Group
import com.itos.coui.layout.Item
import com.itos.coui.scroll.couiVerticalScroll
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
import com.jm3.app.miuix.ui.coui.component.CouiFab
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

/** 触底取下一页的提前量：离末尾还有这么远就开始请求，滚动不会顿在底部。 */
private val LoadMorePrefetchDistance = 320.dp

private val SortLabels: List<String> = BrowseOrder.entries.map { it.label }

/**
 * COUI 首页：折叠顶栏 + 搜索栏 + 分组式发现流 / 搜索结果。
 *
 * 数据编排与 HyperOS 首页同源：同一份 SessionCache 键、同一个 PagedFeed 状态机、
 * 同一套 AuthCall 结局翻译，切皮肤后列表与输入状态不会丢。
 *
 * 视觉全部交给 COUI 原厂组件：顶栏用 CollapsingTopAppBar，正文用 Group/Item 分组行，
 * 加载态用 Group 的 footer 文案表达，不再自绘卡片与按钮。COUI 没有下拉刷新组件，
 * 刷新因此变成分组标题行上的显式按钮。
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

    val scrollState = rememberScrollState()
    val header = rememberCouiCollapsingAppBarState(canScroll = { scrollState.maxValue > 0 })
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
            if (mapped is ListState.Loaded) scrollState.scrollTo(0)
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

    // COUI 的滚动容器是单一 Column，没有 LazyListState 可交给 LoadMoreOnScroll，因此用
    // snapshotFlow 直接观察滚动位置判触底；请求幂等由 beginLoadMore 保证。
    val pagingFeed by rememberUpdatedState(activeFeed())
    val latestLoadMore by rememberUpdatedState(::loadMore)
    LaunchedEffect(scrollState, clickPaging) {
        if (clickPaging) return@LaunchedEffect
        val prefetch = with(density) { LoadMorePrefetchDistance.roundToPx() }
        snapshotFlow { scrollState.value to scrollState.maxValue }.collect { (value, max) ->
            val current = pagingFeed ?: return@collect
            if (max > 0 && value >= max - prefetch && current.canLoadMore && !current.loadMoreFailed) {
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
        val contentPadding = remember(header, innerPadding, density) {
            header.contentPadding(innerPadding, density)
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .couiVerticalScroll(scrollState)
                .padding(contentPadding)
                .consumeWindowInsets(innerPadding)
                // 底部留出 FAB 的高度，最后一行不会被浮动按钮压住
                .padding(bottom = 88.dp),
        ) {
            if (searching) {
                CouiSearchSection(
                    query = activeQuery,
                    byTag = searchByTag,
                    results = results,
                    refreshing = refreshing,
                    clickPaging = clickPaging,
                    sortLabels = SortLabels,
                    selectedSort = order.ordinal.coerceIn(0, SortLabels.lastIndex),
                    onSelectSort = { index -> runSearch(activeQuery, searchByTag, BrowseOrder.entries[index]) },
                    onRefresh = { refresh() },
                    onRetry = { runSearch(activeQuery, searchByTag, order) },
                    onOpenAlbum = ::openAlbum,
                    onJumpPage = ::jumpToPage,
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
                Group(
                    title = "最近更新",
                    headerActions = { CouiRefreshAction(refreshing = refreshing, onRefresh = { refresh() }) },
                    footer = listFooter(feed),
                ) {
                    if (feed is ListState.Loaded) {
                        val state = feed as ListState.Loaded
                        if (state.feed.items.isEmpty()) {
                            CouiMessageRow(title = "还没有内容", summary = "点右上角刷新再试一次")
                        } else {
                            CouiAlbumRows(feed = state.feed, onOpenAlbum = ::openAlbum)
                            if (clickPaging) {
                                CouiPagingRows(feed = state.feed, onJumpPage = ::jumpToPage)
                            } else if (state.feed.loadMoreFailed) {
                                Divider()
                                CouiMessageRow(title = "更多内容没有加载成功", summary = "点按重试") { loadMore() }
                            }
                        }
                    } else if (feed is ListState.Failed) {
                        CouiMessageRow(
                            title = (feed as ListState.Failed).message.ifBlank { "加载失败" },
                            summary = "点按重试",
                        ) { refresh() }
                    } else {
                        CouiMessageRow(title = "正在加载最新上架…")
                    }
                }
                if (serialUpdates.isNotEmpty()) {
                    Group(title = "连更追踪") {
                        CouiAlbumRows(feed = serialUpdates.asFeed(), onOpenAlbum = ::openAlbum)
                    }
                }
                if (randomPicks.isNotEmpty()) {
                    Group(title = "随机推荐") {
                        CouiAlbumRows(feed = randomPicks.asFeed(), onOpenAlbum = ::openAlbum)
                    }
                }
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
                                summary = { Text(text = "点按重新搜索", kind = CouiTextKind.PreferenceSummary) },
                                trailing = {
                                    CouiRowIconAction(
                                        icon = CouiIcons.Close,
                                        description = "删除这条搜索记录",
                                        onClick = { mutateHistory { deps.settings.removeSearchHistory(word) } },
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

/** 搜索结果区：排序胶囊 + 结果摘要 + 结果分组，分页/加载态与首页列表同一套语义。 */
@Composable
private fun CouiSearchSection(
    query: String,
    byTag: Boolean,
    results: ListState?,
    refreshing: Boolean,
    clickPaging: Boolean,
    sortLabels: List<String>,
    selectedSort: Int,
    onSelectSort: (Int) -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    onJumpPage: (Int) -> Unit,
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
        text = searchSummary(query = query, byTag = byTag, total = (results as? ListState.Loaded)?.feed?.total),
        kind = CouiTextKind.Description,
        color = CouiTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 8.dp),
    )
    Group(
        title = "搜索结果",
        headerActions = { CouiRefreshAction(refreshing = refreshing, onRefresh = onRefresh) },
        footer = listFooter(results),
    ) {
        when (results) {
            is ListState.Loaded -> {
                if (results.feed.items.isEmpty()) {
                    CouiMessageRow(title = "没有找到作品", summary = "换个关键词或标签再试")
                } else {
                    CouiAlbumRows(feed = results.feed, onOpenAlbum = onOpenAlbum)
                    if (clickPaging) {
                        CouiPagingRows(feed = results.feed, onJumpPage = onJumpPage)
                    } else if (results.feed.loadMoreFailed) {
                        Divider()
                        CouiMessageRow(title = "更多结果没有加载成功", summary = "点按重试", onClick = onRetry)
                    }
                }
            }
            is ListState.Failed -> CouiMessageRow(
                title = results.message.ifBlank { "搜索失败" },
                summary = "点按重试",
                onClick = onRetry,
            )
            null, ListState.Loading -> CouiMessageRow(title = "正在搜索…", summary = query)
        }
    }
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

/** 分组内的作品行：封面当 icon，标题/作者走 Item 自带的两级字阶。 */
@Composable
private fun CouiAlbumRows(feed: PagedFeed<SearchResult>, onOpenAlbum: (String) -> Unit) {
    feed.items.forEachIndexed { index, album ->
        if (index > 0) Divider()
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
            onClick = { onOpenAlbum(album.id) },
        )
    }
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

/** 点击分页模式下的翻页行；无限滚动模式下不出现。 */
@Composable
private fun CouiPagingRows(feed: PagedFeed<SearchResult>, onJumpPage: (Int) -> Unit) {
    val pageCount = feed.pageCount ?: 1
    if (pageCount <= 1) return
    val page = feed.page.coerceIn(1, pageCount)
    val colors = CouiTheme.colorScheme
    Divider()
    Item(
        title = {
            Text(
                text = "上一页",
                kind = CouiTextKind.PreferenceTitle,
                color = if (page > 1) colors.onSurface else colors.onSurfaceVariant,
            )
        },
        summary = { Text(text = "第 $page / $pageCount 页", kind = CouiTextKind.PreferenceSummary) },
        enabled = page > 1,
        onClick = { onJumpPage(page - 1) },
    )
    Divider()
    Item(
        title = {
            Text(
                text = "下一页",
                kind = CouiTextKind.PreferenceTitle,
                color = if (page < pageCount) colors.onSurface else colors.onSurfaceVariant,
            )
        },
        enabled = page < pageCount,
        onClick = { onJumpPage(page + 1) },
    )
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

/**
 * Group 的 footer 只接受一行文案：加载中、加载失败与到底都用它表达，
 * 底部因此不再需要独立的 Footer/LoadMoreFooter 组件。
 */
private fun listFooter(state: ListState?): String? = when (state) {
    null, is ListState.Loading -> null
    is ListState.Failed -> null
    is ListState.Loaded -> {
        val feed = state.feed
        when {
            feed.items.isEmpty() -> null
            feed.loadingMore -> "正在加载更多…"
            feed.loadMoreFailed -> "加载失败，点下方重试"
            feed.hasMore -> "上滑加载更多"
            else -> "没有更多了"
        }
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
