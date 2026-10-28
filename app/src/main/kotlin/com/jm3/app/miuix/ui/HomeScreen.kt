package com.jm3.app.miuix.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.jm3.app.miuix.Jm3Graph
import com.jm3.core.Jm3Gateway
import com.jm3.core.mergedWith
import com.jm3.core.model.BrowseOrder
import com.jm3.core.model.SearchPage
import com.jm3.core.model.SearchResult
import com.jm3.core.model.SearchScope
import com.jm3.core.settings.PaginationMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Close
import top.yukonga.miuix.kmp.icon.extended.ExpandLess
import top.yukonga.miuix.kmp.icon.extended.Help
import top.yukonga.miuix.kmp.icon.extended.Promotions
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Weeks
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 连载更新横滑区最多展示的条目数，避免首屏请求体过大。 */
private const val SERIAL_CAROUSEL_LIMIT = 12

/** 热门标签折叠时展示的标签数。 */
private const val HOT_TAG_COLLAPSED = 8

private sealed interface HomeListState {
    data object Loading : HomeListState
    data class Loaded(val page: SearchPage) : HomeListState
    data class Failed(val message: String) : HomeListState
}

/** 首页：固定搜索栏 + 发现流（连载更新/热门标签/随便看看/最新上架）或搜索结果。 */
@Composable
fun HomeScreen(gateway: Jm3Gateway, onOpenAlbum: (String) -> Unit, onOpenWeek: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }
    // 已执行的关键词/标签；空串且未执行时展示发现流
    var activeQuery by remember { mutableStateOf("") }
    var searchByTag by remember { mutableStateOf(false) }
    var orderValue by rememberSaveable { mutableStateOf(BrowseOrder.LATEST.apiValue) }
    var feed by remember { mutableStateOf<HomeListState>(HomeListState.Loading) }
    var results by remember { mutableStateOf<HomeListState?>(null) }
    var hotTags by remember { mutableStateOf<List<String>>(emptyList()) }
    var serialUpdates by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var randomPicks by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var searchHistory by remember { mutableStateOf<List<String>>(emptyList()) }
    var showAllTags by remember { mutableStateOf(false) }
    var showSearchHelp by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    // 加载更多失败单独置位，供列表底部给出重试入口
    var loadMoreFailed by remember { mutableStateOf(false) }
    // 仅下拉手势触发的刷新使用顶部指示器；普通加载由列表内进度条表达。
    var refreshing by remember { mutableStateOf(false) }
    // 列表代次：切换搜索/刷新时递增，旧请求的响应只在代次未变时落地。
    var listToken by remember { mutableIntStateOf(0) }
    // 下拉阻尼阈值调低：首页内容多，轻拉即可触发刷新
    val refreshState = rememberPullToRefreshState(refreshThreshold = 0.18f)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val order = BrowseOrder.entries.firstOrNull { it.apiValue == orderValue } ?: BrowseOrder.LATEST
    // 分页模式是 Jm3Graph 的全局可观察状态，设置页切换后本页立即变更控件形态。
    val clickPaging = Jm3Graph.paginationMode == PaginationMode.CLICK

    fun requestSearch(page: Int, byTag: Boolean, text: String, sort: BrowseOrder): SearchPage =
        gateway.search(text, page, sort, if (byTag) SearchScope.TAG else SearchScope.SITE)

    /** 搜索历史读写都在 IO 线程，写入后重新读取，UI 只拿最新快照。 */
    fun mutateHistory(block: suspend () -> Unit) {
        scope.launch {
            withContext(Dispatchers.IO) { runCatching { block() } }
            searchHistory = withContext(Dispatchers.IO) {
                runCatching { Jm3Graph.settings.searchHistory() }.getOrDefault(searchHistory)
            }
        }
    }

    fun loadFeed(page: Int, token: Int) {
        scope.launch {
            val next = withContext(Dispatchers.IO) {
                runCatching { gateway.latest(page) }
            }
            if (token != listToken) return@launch
            feed = next.fold(
                onSuccess = { HomeListState.Loaded(it) },
                onFailure = { HomeListState.Failed(it.message ?: "加载失败") },
            )
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
        results = HomeListState.Loading
        // 提交即记录：标签检索与排序切换也会把当前关键词前移。
        mutateHistory { Jm3Graph.settings.addSearchHistory(trimmed) }
        scope.launch {
            val next = withContext(Dispatchers.IO) {
                runCatching { requestSearch(1, byTag, trimmed, sort) }
            }
            if (token != listToken) return@launch
            results = next.fold(
                onSuccess = { HomeListState.Loaded(it) },
                onFailure = { HomeListState.Failed(it.message ?: "网络错误") },
            )
        }
    }

    fun clearSearch() {
        activeQuery = ""
        results = null
        searchByTag = false
        listToken++
        // 首屏发现流请求可能被搜索代次丢弃；退出搜索时补一次，避免发现流一直停在加载态
        if (feed !is HomeListState.Loaded) loadFeed(1, listToken)
    }

    fun submitSearch() {
        val q = query.trim()
        when {
            q.isEmpty() -> Unit
            q.all { it.isDigit() } -> onOpenAlbum(q) // 车号直达
            else -> runSearch(q, byTag = false, sort = order)
        }
    }

    fun refresh(byPull: Boolean = false) {
        if (byPull) refreshing = true
        listToken++
        val token = listToken
        scope.launch {
            if (results != null) {
                results = HomeListState.Loading
                val text = activeQuery
                val byTag = searchByTag
                val next = withContext(Dispatchers.IO) {
                    runCatching { requestSearch(1, byTag, text, order) }
                }
                if (token == listToken) {
                    results = next.fold(
                        onSuccess = { HomeListState.Loaded(it) },
                        onFailure = { HomeListState.Failed(it.message ?: "网络错误") },
                    )
                }
            } else {
                val next = withContext(Dispatchers.IO) {
                    runCatching { gateway.latest(1) }
                }
                if (token == listToken) {
                    feed = next.fold(
                        onSuccess = { HomeListState.Loaded(it) },
                        onFailure = { HomeListState.Failed(it.message ?: "加载失败") },
                    )
                }
            }
            // 刷新协程结束即复位指示器；即使期间切换了搜索，也不能让顶部一直转圈
            refreshing = false
        }
    }

    fun activePage(): SearchPage? =
        ((if (results != null) results else feed) as? HomeListState.Loaded)?.page

    fun loadMore() {
        if (loadingMore) return
        val current = activePage() ?: return
        if (current.currentPage >= current.totalPages) return
        val searching = results != null
        val token = listToken
        val nextOrder = order
        val text = activeQuery
        val byTag = searchByTag
        loadingMore = true
        scope.launch {
            val next = withContext(Dispatchers.IO) {
                runCatching {
                    if (searching) requestSearch(current.currentPage + 1, byTag, text, nextOrder)
                    else gateway.latest(current.currentPage + 1)
                }.getOrNull()
            }
            loadingMore = false
            if (token != listToken) return@launch
            // 同一代次内失败可重试；成功即清除失败标记
            if (next == null) {
                loadMoreFailed = true
                return@launch
            }
            loadMoreFailed = false
            val merged = current.mergedWith(next)
            if (searching) {
                if (results != null) results = HomeListState.Loaded(merged)
            } else {
                feed = HomeListState.Loaded(merged)
            }
        }
    }

    /** 点击分页：用目标页整页替换当前列表，成功后回到列表顶部。 */
    fun jumpToPage(target: Int) {
        val current = activePage() ?: return
        if (target < 1 || target > current.totalPages) return
        val searching = results != null
        val text = activeQuery
        val byTag = searchByTag
        val nextOrder = order
        listToken++
        val token = listToken
        if (searching) results = HomeListState.Loading else feed = HomeListState.Loading
        scope.launch {
            val next = withContext(Dispatchers.IO) {
                runCatching {
                    if (searching) requestSearch(target, byTag, text, nextOrder)
                    else gateway.latest(target)
                }
            }
            if (token != listToken) return@launch
            next.fold(
                onSuccess = { page ->
                    if (searching) results = HomeListState.Loaded(page) else feed = HomeListState.Loaded(page)
                    listState.scrollToItem(0)
                },
                onFailure = { error ->
                    val failed = HomeListState.Failed(error.message ?: "网络错误")
                    if (searching) results = failed else feed = failed
                },
            )
        }
    }

    // 首屏：最新上架 + 连载更新 + 热门标签 + 随机推荐 + 搜索历史并行加载；附属区块失败静默隐藏。
    LaunchedEffect(gateway) {
        listToken++
        loadFeed(1, listToken)
        scope.launch {
            val tags = withContext(Dispatchers.IO) { runCatching { gateway.hotTags() }.getOrDefault(emptyList()) }
            val picks = withContext(Dispatchers.IO) { runCatching { gateway.randomRecommend() }.getOrDefault(emptyList()) }
            val updates = withContext(Dispatchers.IO) {
                runCatching { gateway.serialization(1).items.take(SERIAL_CAROUSEL_LIMIT) }.getOrDefault(emptyList())
            }
            val storedHistory = withContext(Dispatchers.IO) {
                runCatching { Jm3Graph.settings.searchHistory() }.getOrDefault(emptyList())
            }
            hotTags = tags
            randomPicks = picks
            serialUpdates = updates
            searchHistory = storedHistory
        }
    }

    // 接近列表尾部时自动加载下一页；点击分页模式下禁用自动加载。页面切换后重新订阅。
    LaunchedEffect(activePage(), results != null, clickPaging) {
        if (clickPaging || activePage() == null) return@LaunchedEffect
        snapshotFlow {
            val info = listState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: -1) to info.totalItemsCount
        }.collect { (last, total) ->
            if (last >= 0 && total > 0 && last >= total - 3) loadMore()
        }
    }

    // 与原版一致：列表滚动后出现悬浮操作（随机一部/回到顶部）
    val showActions by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 100 }
    }

    fun openRandom() {
        val candidates = randomPicks.ifEmpty { activePage()?.items.orEmpty() }
        candidates.randomOrNull()?.let { onOpenAlbum(it.id) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "JM3 Miuix",
                actions = {
                    IconButton(onClick = onOpenWeek) {
                        Icon(imageVector = MiuixIcons.Weeks, contentDescription = "每周必看")
                    }
                },
            )
        },
        floatingActionButton = {
            if (showActions) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FloatingActionButton(
                        onClick = ::openRandom,
                        minWidth = 44.dp,
                        minHeight = 44.dp,
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Promotions,
                            contentDescription = "随机看一部",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    FloatingActionButton(
                        onClick = { scope.launch { listState.animateScrollToItem(0) } },
                        minWidth = 44.dp,
                        minHeight = 44.dp,
                    ) {
                        Icon(
                            imageVector = MiuixIcons.ExpandLess,
                            contentDescription = "回到顶部",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            // 搜索栏固定于列表之外：结果滚动后仍可直接改关键词。
            SearchBar(
                inputField = {
                    InputField(
                        query = query,
                        onQueryChange = {
                            query = it
                            // 清空输入即退出搜索态，回到发现流
                            if (it.isBlank() && results != null) clearSearch()
                        },
                        onSearch = { submitSearch() },
                        expanded = searchExpanded,
                        onExpandedChange = { searchExpanded = it },
                        label = "搜索 / 车号",
                        // 自绘尾部动作：保留清空入口，并常驻「?」语法帮助。
                        trailingIcon = {
                            Row(
                                modifier = Modifier.padding(end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (query.isNotEmpty()) {
                                    IconButton(
                                        onClick = {
                                            query = ""
                                            if (results != null) clearSearch()
                                        },
                                        minWidth = 32.dp,
                                        minHeight = 32.dp,
                                    ) {
                                        Icon(
                                            imageVector = MiuixIcons.Basic.Close,
                                            contentDescription = "清空输入",
                                            tint = MiuixTheme.colorScheme.onSurfaceContainerHigh,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { showSearchHelp = true },
                                    minWidth = 32.dp,
                                    minHeight = 32.dp,
                                ) {
                                    Icon(
                                        imageVector = MiuixIcons.Help,
                                        contentDescription = "搜索语法帮助",
                                        tint = MiuixTheme.colorScheme.onSurfaceContainerHigh,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                onExpandedChange = { searchExpanded = it },
                modifier = Modifier.fillMaxWidth(),
                expanded = searchExpanded,
                outsideEndAction = {
                    IconButton(onClick = ::submitSearch) {
                        Icon(
                            imageVector = MiuixIcons.Search,
                            contentDescription = "搜索",
                        )
                    }
                },
            ) {}
            PullToRefresh(
                isRefreshing = refreshing,
                onRefresh = { refresh(byPull = true) },
                modifier = Modifier.fillMaxSize(),
                pullToRefreshState = refreshState,
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    val searching = results != null
                    if (searching) {
                        item(key = "search-header") {
                            SearchResultHeader(
                                query = activeQuery,
                                byTag = searchByTag,
                                order = order,
                                onOrderChange = { runSearch(activeQuery, searchByTag, it) },
                                total = (results as? HomeListState.Loaded)?.page?.totalItems ?: 0,
                            )
                        }
                        when (val s = results) {
                            is HomeListState.Loading -> if (!refreshing) {
                                item(key = "search-loading") { InlineLoading() }
                            }
                            is HomeListState.Failed -> item(key = "search-error") {
                                ErrorScreen(
                                    message = "加载失败：${s.message}",
                                    onRetry = { runSearch(activeQuery, searchByTag, order) },
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                )
                            }
                            is HomeListState.Loaded -> homePageItems(
                                page = s.page,
                                clickPaging = clickPaging,
                                loadingMore = loadingMore,
                                loadMoreFailed = loadMoreFailed,
                                onLoadMore = ::loadMore,
                                onPageChange = ::jumpToPage,
                                onOpenAlbum = onOpenAlbum,
                            )
                            null -> Unit
                        }
                    } else {
                        // 仅当搜索框为空且未进入搜索态时展示搜索记录
                        if (query.isBlank() && searchHistory.isNotEmpty()) {
                            item(key = "search-history") {
                                SearchHistoryCard(
                                    history = searchHistory,
                                    onPick = { word ->
                                        query = word
                                        runSearch(word, byTag = false, sort = order)
                                    },
                                    onRemove = { word ->
                                        mutateHistory { Jm3Graph.settings.removeSearchHistory(word) }
                                    },
                                    onClear = {
                                        mutateHistory { Jm3Graph.settings.clearSearchHistory() }
                                    },
                                )
                            }
                        }
                        if (serialUpdates.isNotEmpty()) {
                            item(key = "serial-title") { SmallTitle(text = "连载更新") }
                            item(key = "serial-row") {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    items(serialUpdates, key = { it.id }) { update ->
                                        SerialUpdateCard(update = update, onClick = { onOpenAlbum(update.id) })
                                    }
                                }
                            }
                        }
                        if (hotTags.isNotEmpty()) {
                            item(key = "hot-tags-title") {
                                SectionHeader(
                                    text = "热门标签",
                                    actionText = if (hotTags.size > HOT_TAG_COLLAPSED) {
                                        if (showAllTags) "收起" else "更多"
                                    } else {
                                        null
                                    },
                                    onAction = { showAllTags = !showAllTags },
                                )
                            }
                            item(key = "hot-tags") {
                                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                                    HotTagFlow(
                                        tags = hotTags,
                                        expanded = showAllTags,
                                        onClick = { tag ->
                                            query = tag
                                            runSearch(tag, byTag = true, sort = order)
                                        },
                                    )
                                }
                            }
                        }
                        if (randomPicks.isNotEmpty()) {
                            item(key = "random-title") { SmallTitle(text = "随便看看") }
                            item(key = "random-picks") {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    items(randomPicks, key = { it.id }) { pick ->
                                        RandomPickCard(pick = pick, onClick = { onOpenAlbum(pick.id) })
                                    }
                                }
                            }
                        }
                        item(key = "latest-title") { SmallTitle(text = "最新上架") }
                        when (val s = feed) {
                            is HomeListState.Loading -> if (!refreshing) {
                                item(key = "feed-loading") { InlineLoading() }
                            }
                            is HomeListState.Failed -> item(key = "feed-error") {
                                ErrorScreen(
                                    message = "加载失败：${s.message}",
                                    onRetry = { refresh() },
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                )
                            }
                            is HomeListState.Loaded -> homePageItems(
                                page = s.page,
                                clickPaging = clickPaging,
                                loadingMore = loadingMore,
                                loadMoreFailed = loadMoreFailed,
                                onLoadMore = ::loadMore,
                                onPageChange = ::jumpToPage,
                                onOpenAlbum = onOpenAlbum,
                            )
                        }
                    }
                    item(key = "bottom-space") { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        }

        // 原版搜索语法帮助：空格=同时包含，+ 必须包含，- 排除；按钮沿用 HyperOS 纵向全宽惯例。
        OverlayDialog(
            show = showSearchHelp,
            title = "搜索语法",
            summary = "用空格与 +/- 组合关键词，可缩小搜索范围。",
            onDismissRequest = { showSearchHelp = false },
        ) {
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                BasicComponent(title = "全彩 人妻", summary = "同时包含这两个关键词（空格分隔）")
                BasicComponent(title = "全彩 +人妻", summary = "两个关键词都必须包含")
                BasicComponent(title = "全彩 -人妻", summary = "包含「全彩」，排除「人妻」")
            }
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
                TextButton(
                    text = "知道了",
                    onClick = { showSearchHelp = false },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** 搜索记录卡：标题右侧清除全部，点击词条复搜，行尾删除单条。 */
@Composable
private fun SearchHistoryCard(
    history: List<String>,
    onPick: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "搜索记录",
                style = MiuixTheme.textStyles.subtitle,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                text = "清除",
                onClick = onClear,
                minWidth = 0.dp,
                minHeight = 28.dp,
                insideMargin = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
            )
        }
        history.forEach { word ->
            BasicComponent(
                title = word,
                onClick = { onPick(word) },
                endActions = {
                    IconButton(
                        onClick = { onRemove(word) },
                        minWidth = 36.dp,
                        minHeight = 36.dp,
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Basic.Close,
                            contentDescription = "删除该记录",
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                },
            )
        }
    }
}

/** 区块标题：文本 + 右侧可选文字入口（原版「查看更多」）。 */
@Composable
private fun SectionHeader(
    text: String,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 28.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.subtitle,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            modifier = Modifier.weight(1f),
        )
        if (actionText != null && onAction != null) {
            TextButton(
                text = actionText,
                onClick = onAction,
                minWidth = 0.dp,
                minHeight = 28.dp,
                insideMargin = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
            )
        }
    }
}

/** 热门标签胶囊流：默认折叠，更多/收起由区块标题控制。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HotTagFlow(tags: List<String>, expanded: Boolean, onClick: (String) -> Unit) {
    val visible = if (expanded) tags else tags.take(HOT_TAG_COLLAPSED)
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            // 展开/收起时高度弹性过渡，避免列表瞬间跳变
            .animateContentSize(
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 700f),
            ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        visible.forEach { tag ->
            TextButton(
                text = tag,
                onClick = { onClick(tag) },
                minWidth = 0.dp,
                minHeight = 32.dp,
                insideMargin = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

/** 连载更新卡：封面 + 标题 + 作者，横向滑动浏览。 */
@Composable
private fun SerialUpdateCard(update: SearchResult, onClick: () -> Unit) {
    Card(modifier = Modifier.width(112.dp), onClick = onClick) {
        if (update.coverUrl != null) {
            AsyncImage(
                model = update.coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.width(112.dp).height(150.dp),
            )
        } else {
            Spacer(modifier = Modifier.width(112.dp).height(150.dp))
        }
        Text(
            text = update.title,
            fontSize = MiuixTheme.textStyles.footnote2.fontSize,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
        )
        Text(
            text = update.authors.joinToString(" / ").ifBlank { "未知作者" },
            fontSize = MiuixTheme.textStyles.footnote2.fontSize,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, bottom = 6.dp),
        )
    }
}

/** 结果列表分派：无限滚动沿用公共实现；点击分页复用公共单页列表与翻页条。 */
private fun LazyListScope.homePageItems(
    page: SearchPage,
    clickPaging: Boolean,
    loadingMore: Boolean,
    loadMoreFailed: Boolean,
    onLoadMore: () -> Unit,
    onPageChange: (Int) -> Unit,
    onOpenAlbum: (String) -> Unit,
) {
    if (!clickPaging) {
        searchResultItems(
            page = page,
            loadingMore = loadingMore,
            loadMoreFailed = loadMoreFailed,
            onLoadMore = onLoadMore,
            onOpenAlbum = onOpenAlbum,
        )
        return
    }
    pagedSearchResultItems(page = page, onPageChange = onPageChange, onOpenAlbum = onOpenAlbum)
}

/** 搜索结果头：关键词/结果数 + 排序分段控件。 */
@Composable
private fun SearchResultHeader(
    query: String,
    byTag: Boolean,
    order: BrowseOrder,
    onOrderChange: (BrowseOrder) -> Unit,
    total: Int,
) {
    val selectionHaptic = rememberSelectionHaptic()
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text = (if (byTag) "标签：$query" else "“$query”") + if (total > 0) " · 共 $total 结果" else "",
            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )
        TabRow(
            tabs = BrowseOrder.entries.map { it.label },
            selectedTabIndex = BrowseOrder.entries.indexOf(order),
            onTabSelected = {
                selectionHaptic()
                onOrderChange(BrowseOrder.entries[it])
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun InlineLoading() {
    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        InfiniteProgressIndicator()
    }
}

/** 随机推荐卡：封面 + 两行标题，横向滑动浏览。 */
@Composable
private fun RandomPickCard(pick: SearchResult, onClick: () -> Unit) {
    Card(modifier = Modifier.width(96.dp), onClick = onClick) {
        if (pick.coverUrl != null) {
            AsyncImage(
                model = pick.coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.width(96.dp).height(128.dp),
            )
        } else {
            Spacer(modifier = Modifier.width(96.dp).height(128.dp))
        }
        Text(
            text = pick.title,
            fontSize = MiuixTheme.textStyles.footnote2.fontSize,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(6.dp),
        )
    }
}
