// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.page

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.ui.ErrorScreen
import com.jm3.app.miuix.ui.HomeSideData
import com.jm3.app.miuix.ui.ListState
import com.jm3.app.miuix.ui.LoadMoreFooter
import com.jm3.app.miuix.ui.LoadMoreOnScroll
import com.jm3.app.miuix.ui.LoadingScreen
import com.jm3.app.miuix.ui.PagerBar
import com.jm3.app.miuix.ui.PaginationModeReloadEffect
import com.jm3.app.miuix.ui.SessionListKey
import com.jm3.app.miuix.ui.asListState
import com.jm3.app.miuix.ui.coui.component.CouiButton
import com.jm3.app.miuix.ui.coui.component.CouiCard
import com.jm3.app.miuix.ui.coui.component.CouiChip
import com.jm3.app.miuix.ui.coui.component.CouiCompactWorkCard
import com.jm3.app.miuix.ui.coui.component.CouiDialogOverlay
import com.jm3.app.miuix.ui.coui.component.CouiFab
import com.jm3.app.miuix.ui.coui.component.CouiSearchField
import com.jm3.app.miuix.ui.coui.component.CouiSectionTitle
import com.jm3.app.miuix.ui.coui.component.CouiSegmented
import com.jm3.app.miuix.ui.coui.component.CouiTagFlow
import com.jm3.app.miuix.ui.coui.component.CouiTextButton
import com.jm3.app.miuix.ui.coui.component.CouiTopBar
import com.jm3.app.miuix.ui.coui.component.couiBackdropSource
import com.jm3.app.miuix.ui.coui.component.couiClickable
import com.jm3.app.miuix.ui.coui.component.couiTopBarHeight
import com.jm3.app.miuix.ui.coui.component.rememberCouiBlurBackdrop
import com.jm3.app.miuix.ui.coui.theme.CouiType
import com.jm3.app.miuix.ui.coui.theme.LocalCouiColors
import com.jm3.app.miuix.ui.coui.theme.LocalCouiShapes
import com.jm3.app.miuix.ui.glass.GlassPullToRefresh
import com.jm3.app.miuix.ui.glass.LocalFloatingBarContentPadding
import com.jm3.app.miuix.ui.glass.LocalGlassTopBarExpandedHeight
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
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Help
import top.yukonga.miuix.kmp.icon.extended.Promotions
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Weeks
import top.yukonga.miuix.kmp.squircle.squircleClip

private const val SERIAL_CAROUSEL_LIMIT = 12
private const val HOT_TAG_COLLAPSED = 8
private val SectionHorizontalPadding = 16.dp

/**
 * COUI 首页：固定顶栏 + 固定搜索框 + 发现流 / 搜索结果。
 *
 * 数据编排与 HyperOS 首页同源：同一份 SessionCache 键、同一个 PagedFeed 状态机、
 * 同一套 AuthCall 结局翻译，切皮肤后列表与输入状态不会丢。
 *
 * 视觉上把 HyperOS 的玻璃顶栏 + 可折叠搜索栏换成「透明顶栏（滚动后磨砂）+ 固定搜索框」，
 * 卡片全部换成 20dp squircle + 投影/描边，列表项从 Miuix 网格卡换成 COUI 横排卡。
 */
@Composable
fun CouiHomeScreen(
    deps: ScreenDeps,
    onOpenAlbum: (String) -> Unit,
    onOpenWeek: () -> Unit,
) {
    val topBarBackdrop = rememberCouiBlurBackdrop()
    // 网关与鉴权统一从 ScreenDeps 取，不再额外收一份同源参数
    val gateway = deps.gateway
    val authCall = deps.authCall
    var query by rememberSaveable { mutableStateOf("") }
    var activeQuery by rememberSaveable { mutableStateOf("") }
    var searchByTag by rememberSaveable { mutableStateOf(false) }
    var orderValue by rememberSaveable { mutableStateOf(BrowseOrder.LATEST.apiValue) }
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
    val refreshState = rememberPullToRefreshState(refreshThreshold = 0.18f)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

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

    BackHandler(enabled = searching) { clearSearch() }

    fun dismissSearchInput() {
        focusManager.clearFocus()
        keyboard?.hide()
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

    fun refresh(byPull: Boolean = false) {
        if (byPull) refreshing = true
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
            if (mapped is ListState.Loaded) listState.scrollToItem(0)
        }
    }

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

    PaginationModeReloadEffect(mode) {
        if (searching) rememberResults(ListState.Loading) else rememberFeed(ListState.Loading)
        refresh()
    }

    val pagingFeed = activeFeed()
    if (!clickPaging && pagingFeed != null) {
        LoadMoreOnScroll(listState = listState, feed = pagingFeed, onLoadMore = ::loadMore)
    }

    val scrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 8
        }
    }
    val showActions by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 100 }
    }
    val barClearance = LocalFloatingBarContentPadding.current
    val density = LocalDensity.current
    // 覆盖层实测前先按设计高度估一帧，之后一律用实测值，避免字体放大时压住首项
    val estimatedHeaderHeight = couiTopBarHeight() + 56.dp + if (searching) 48.dp else 0.dp
    var measuredHeaderHeight by remember { mutableStateOf(0.dp) }
    val contentTop = if (measuredHeaderHeight > 0.dp) measuredHeaderHeight else estimatedHeaderHeight
    val contentBottom = if (barClearance == 0.dp) 16.dp else barClearance + 8.dp

    fun openRandom() {
        val candidates = randomPicks.ifEmpty { activeFeed()?.items.orEmpty() }
        candidates.randomOrNull()?.let { openAlbum(it.id) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CompositionLocalProvider(
            LocalGlassTopBarExpandedHeight provides contentTop,
        ) {
            GlassPullToRefresh(
                isRefreshing = refreshing,
                onRefresh = { refresh(byPull = true) },
                modifier = Modifier
                    .fillMaxSize()
                    .couiBackdropSource(topBarBackdrop),
                pullToRefreshState = refreshState,
                contentPadding = PaddingValues(top = contentTop, bottom = contentBottom),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = contentTop, bottom = contentBottom),
                ) {
                    if (searching) {
                        item(key = "search-header") {
                            CouiSearchHeader(
                                query = activeQuery,
                                byTag = searchByTag,
                                total = (results as? ListState.Loaded)?.feed?.total ?: 0,
                            )
                        }
                        when (val s = results) {
                            null, is ListState.Loading -> if (!refreshing) {
                                item(key = "search-loading") {
                                    LoadingScreen(modifier = Modifier.padding(vertical = 24.dp))
                                }
                            }
                            is ListState.Failed -> item(key = "search-error") {
                                ErrorScreen(
                                    message = s.message,
                                    onRetry = { runSearch(activeQuery, searchByTag, order) },
                                )
                            }
                            is ListState.Loaded -> couiResultItems(
                                feed = s.feed,
                                clickPaging = clickPaging,
                                onLoadMore = ::loadMore,
                                onPageChange = ::jumpToPage,
                                onOpenAlbum = { openAlbum(it) },
                            )
                        }
                    } else {
                        item(key = "discovery-header") {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                if (query.isBlank() && searchHistory.isNotEmpty()) {
                                    CouiSearchHistoryCard(
                                        history = searchHistory,
                                        onPick = { word ->
                                            query = word
                                            runSearch(word, byTag = false, sort = order)
                                        },
                                        onRemove = { word ->
                                            mutateHistory { deps.settings.removeSearchHistory(word) }
                                        },
                                        onClear = { mutateHistory { deps.settings.clearSearchHistory() } },
                                    )
                                }
                                if (serialUpdates.isNotEmpty()) {
                                    CouiSectionTitle(text = "连载更新")
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = SectionHorizontalPadding),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        items(serialUpdates, key = { it.id }) { update ->
                                            CouiCompactWorkCard(
                                                title = update.title,
                                                coverUrl = update.coverUrl,
                                                subtitle = update.authors.joinToString(" / ").ifBlank { "未知作者" },
                                                onClick = { openAlbum(update.id) },
                                            )
                                        }
                                    }
                                }
                                if (hotTags.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Box(modifier = Modifier.weight(1f)) {
                                            CouiSectionTitle(text = "热门标签")
                                        }
                                        if (hotTags.size > HOT_TAG_COLLAPSED) {
                                            CouiTextButton(
                                                text = if (showAllTags) "收起" else "更多",
                                                onClick = { showAllTags = !showAllTags },
                                                modifier = Modifier.padding(end = 8.dp),
                                            )
                                        }
                                    }
                                    CouiCard(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = SectionHorizontalPadding),
                                        contentPadding = PaddingValues(12.dp),
                                    ) {
                                        CouiTagFlow(
                                            tags = hotTags,
                                            collapsedLimit = HOT_TAG_COLLAPSED,
                                            expanded = showAllTags,
                                            onClick = { tag ->
                                                query = tag
                                                runSearch(tag, byTag = true, sort = order)
                                            },
                                        )
                                    }
                                }
                                if (randomPicks.isNotEmpty()) {
                                    CouiSectionTitle(text = "随便看看")
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = SectionHorizontalPadding),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        items(randomPicks, key = { it.id }) { pick ->
                                            CouiCompactWorkCard(
                                                title = pick.title,
                                                coverUrl = pick.coverUrl,
                                                subtitle = pick.authors.joinToString(" / ").ifBlank { "未知作者" },
                                                onClick = { openAlbum(pick.id) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        item(key = "latest-title") { CouiSectionTitle(text = "最新上架") }
                        when (val s = feed) {
                            is ListState.Loading -> if (!refreshing) {
                                item(key = "feed-loading") {
                                    LoadingScreen(modifier = Modifier.padding(vertical = 24.dp))
                                }
                            }
                            is ListState.Failed -> item(key = "feed-error") {
                                ErrorScreen(message = s.message, onRetry = { refresh() })
                            }
                            is ListState.Loaded -> couiResultItems(
                                feed = s.feed,
                                clickPaging = clickPaging,
                                onLoadMore = ::loadMore,
                                onPageChange = ::jumpToPage,
                                onOpenAlbum = { openAlbum(it) },
                            )
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .onSizeChanged { measuredHeaderHeight = with(density) { it.height.toDp() } },
        ) {
            CouiTopBar(
                title = "JM3",
                scrolled = scrolled,
                backdrop = topBarBackdrop,
                actions = {
                    IconButton(onClick = { showSearchHelp = true }) {
                        Icon(imageVector = MiuixIcons.Help, contentDescription = "搜索语法帮助")
                    }
                    IconButton(onClick = onOpenWeek) {
                        Icon(imageVector = MiuixIcons.Weeks, contentDescription = "每周必看")
                    }
                },
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CouiSearchField(
                    value = query,
                    onValueChange = {
                        query = it
                        if (it.isBlank() && searching) clearSearch()
                    },
                    placeholder = "搜索 / 车号",
                    modifier = Modifier.weight(1f),
                    backdrop = topBarBackdrop,
                    leadingIcon = MiuixIcons.Search,
                    onSearch = { submitSearch() },
                )
                if (searching) {
                    CouiTextButton(
                        text = "取消",
                        onClick = {
                            dismissSearchInput()
                            clearSearch()
                        },
                    )
                }
            }
            if (searching) {
                CouiSegmented(
                    options = BrowseOrder.entries.map { it.label },
                    selectedIndex = BrowseOrder.entries.indexOf(order),
                    onSelect = { runSearch(activeQuery, searchByTag, BrowseOrder.entries[it]) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }

        if (showActions) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = if (barClearance == 0.dp) 16.dp else barClearance + 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CouiFab(
                    icon = MiuixIcons.Promotions,
                    contentDescription = "随机看一部",
                    onClick = ::openRandom,
                )
                CouiFab(
                    icon = MiuixIcons.Back,
                    contentDescription = "回到顶部",
                    rotationDegrees = 90f,
                    onClick = { scope.launch { listState.animateScrollToItem(0) } },
                )
            }
        }

        CouiDialogOverlay(visible = showSearchHelp, onDismiss = { showSearchHelp = false }) {
            BasicText(
                text = "搜索语法",
                style = CouiType.title.copy(color = LocalCouiColors.current.textPrimary),
            )
            Spacer(Modifier.height(8.dp))
            BasicText(
                text = "用空格与 +/- 组合关键词，可缩小搜索范围。",
                style = CouiType.caption.copy(color = LocalCouiColors.current.textSecondary),
            )
            Spacer(Modifier.height(16.dp))
            CouiCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
            ) {
                CouiSyntaxRow(title = "全彩 人妻", summary = "同时包含这两个关键词（空格分隔）")
                CouiSyntaxRow(title = "全彩 +人妻", summary = "两个关键词都必须包含")
                CouiSyntaxRow(title = "全彩 -人妻", summary = "包含「全彩」，排除「人妻」")
            }
            Spacer(Modifier.height(16.dp))
            CouiButton(text = "知道了", onClick = { showSearchHelp = false }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun CouiSearchHeader(query: String, byTag: Boolean, total: Int) {
    val colors = LocalCouiColors.current
    BasicText(
        text = (if (byTag) "标签：$query" else "“$query”") + if (total > 0) " · 共 $total 结果" else "",
        style = CouiType.caption.copy(color = colors.textSecondary),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SectionHorizontalPadding, vertical = 12.dp),
    )
}

@Composable
private fun CouiSearchHistoryCard(
    history: List<String>,
    onPick: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
) {
    val colors = LocalCouiColors.current
    CouiCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SectionHorizontalPadding, vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = "搜索记录",
                style = CouiType.section.copy(color = colors.textPrimary),
                modifier = Modifier.weight(1f),
            )
            CouiTextButton(text = "清除", onClick = onClear)
        }
        history.forEach { word ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .couiClickable(onClick = { onPick(word) }, ensureMinTouchTarget = false)
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(
                    text = word,
                    style = CouiType.body.copy(color = colors.textPrimary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                BasicText(
                    text = "×",
                    style = CouiType.caption.copy(color = colors.textSecondary),
                    modifier = Modifier
                        .couiClickable(onClick = { onRemove(word) }, ensureMinTouchTarget = false)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun CouiResultRow(result: SearchResult, onClick: () -> Unit) {
    val colors = LocalCouiColors.current
    CouiCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SectionHorizontalPadding, vertical = 5.dp),
        contentPadding = PaddingValues(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(width = 68.dp, height = 90.dp)
                    .squircleClip(LocalCouiShapes.current.control)
                    .background(colors.accentContainer),
            ) {
                if (result.coverUrl != null) {
                    AsyncImage(
                        model = result.coverUrl,
                        contentDescription = result.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                BasicText(
                    text = result.title,
                    style = CouiType.bodyEmphasis.copy(color = colors.textPrimary),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                BasicText(
                    text = result.authors.joinToString(" / ").ifBlank { "未知作者" },
                    style = CouiType.caption.copy(color = colors.textSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (result.tags.isNotEmpty()) {
                    BasicText(
                        text = result.tags.take(3).joinToString(" · "),
                        style = CouiType.captionMeta.copy(color = colors.textTertiary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

private fun LazyListScope.couiResultItems(
    feed: PagedFeed<SearchResult>,
    clickPaging: Boolean,
    onLoadMore: () -> Unit,
    onPageChange: (Int) -> Unit,
    onOpenAlbum: (String) -> Unit,
) {
    if (feed.items.isEmpty()) {
        item(key = "result-empty") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = "没有找到作品",
                    style = CouiType.body.copy(color = LocalCouiColors.current.textSecondary),
                )
            }
        }
        return
    }
    items(feed.items, key = { it.id }) { result ->
        CouiResultRow(result = result, onClick = { onOpenAlbum(result.id) })
    }
    if (clickPaging && (feed.pageCount ?: 1) > 1) {
        item(key = "result-pager") {
            PagerBar(
                page = feed.page,
                totalPages = feed.pageCount,
                onPageChange = onPageChange,
            )
        }
    } else if (feed.loadingMore || feed.loadMoreFailed || feed.hasMore) {
        item(key = "result-footer") { LoadMoreFooter(feed = feed, onLoadMore = onLoadMore) }
    }
}

@Composable
private fun CouiSyntaxRow(title: String, summary: String) {
    val colors = LocalCouiColors.current
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        BasicText(text = title, style = CouiType.bodyEmphasis.copy(color = colors.textPrimary))
        BasicText(
            text = summary,
            style = CouiType.caption.copy(color = colors.textSecondary),
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

