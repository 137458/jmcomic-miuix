package com.jm3.app.miuix.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import com.jm3.core.Jm3Gateway
import com.jm3.core.mergedWith
import com.jm3.core.model.BrowseOrder
import com.jm3.core.model.SearchPage
import com.jm3.core.model.SearchResult
import com.jm3.core.model.SearchScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ExpandLess
import top.yukonga.miuix.kmp.icon.extended.Promotions
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Weeks
import top.yukonga.miuix.kmp.theme.MiuixTheme

private sealed interface HomeListState {
    data object Loading : HomeListState
    data class Loaded(val page: SearchPage) : HomeListState
    data class Failed(val message: String) : HomeListState
}

/** 首页：固定搜索栏 + 发现流（热门标签/随便看看/最新上架）或搜索结果。 */
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
    var randomPicks by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var loadingMore by remember { mutableStateOf(false) }
    // 仅下拉手势触发的刷新使用顶部指示器；普通加载由列表内进度条表达。
    var refreshing by remember { mutableStateOf(false) }
    // 列表代次：切换搜索/刷新时递增，旧请求的响应只在代次未变时落地。
    var listToken by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val order = BrowseOrder.entries.firstOrNull { it.apiValue == orderValue } ?: BrowseOrder.LATEST

    fun requestSearch(page: Int, byTag: Boolean, text: String, sort: BrowseOrder): SearchPage =
        gateway.search(text, page, sort, if (byTag) SearchScope.TAG else SearchScope.SITE)

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
            if (next == null || token != listToken) return@launch
            val merged = current.mergedWith(next)
            if (searching) {
                if (results != null) results = HomeListState.Loaded(merged)
            } else {
                feed = HomeListState.Loaded(merged)
            }
        }
    }

    // 首屏：最新上架 + 热门标签 + 随机推荐并行加载；标签/随机失败不阻塞主列表。
    LaunchedEffect(gateway) {
        listToken++
        loadFeed(1, listToken)
        scope.launch {
            val tags = withContext(Dispatchers.IO) { runCatching { gateway.hotTags() }.getOrDefault(emptyList()) }
            val picks = withContext(Dispatchers.IO) { runCatching { gateway.randomRecommend() }.getOrDefault(emptyList()) }
            hotTags = tags
            randomPicks = picks
        }
    }

    // 接近列表尾部时自动加载下一页；页面切换后重新订阅。
    LaunchedEffect(activePage(), results != null) {
        if (activePage() == null) return@LaunchedEffect
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
                            is HomeListState.Loaded -> searchResultItems(
                                page = s.page,
                                loadingMore = loadingMore,
                                onLoadMore = ::loadMore,
                                onOpenAlbum = onOpenAlbum,
                            )
                            null -> Unit
                        }
                    } else {
                        if (hotTags.isNotEmpty()) {
                            item(key = "hot-tags-title") { SmallTitle(text = "热门标签") }
                            item(key = "hot-tags") {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    items(hotTags) { tag ->
                                        TextButton(
                                            text = tag,
                                            onClick = { query = tag; runSearch(tag, byTag = true, sort = order) },
                                            minWidth = 0.dp,
                                            minHeight = 32.dp,
                                        )
                                    }
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
                            is HomeListState.Loaded -> searchResultItems(
                                page = s.page,
                                loadingMore = loadingMore,
                                onLoadMore = ::loadMore,
                                onOpenAlbum = onOpenAlbum,
                            )
                        }
                    }
                    item(key = "bottom-space") { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        }
    }
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
            onTabSelected = { onOrderChange(BrowseOrder.entries[it]) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun InlineLoading() {
    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
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
