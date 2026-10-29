package com.jm3.app.miuix.ui

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jm3.app.miuix.ui.glass.GlassScaffold
import com.jm3.app.miuix.ui.glass.GlassTopAppBar
import com.jm3.core.Jm3Gateway
import com.jm3.core.mergedWith
import com.jm3.core.model.BrowseCategory
import com.jm3.core.model.BrowseFilter
import com.jm3.core.model.BrowseOrder
import com.jm3.core.model.BrowseTime
import com.jm3.core.model.CategoryBlock
import com.jm3.core.model.CategoryTree
import com.jm3.core.model.SearchPage
import com.jm3.core.model.SearchScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Weeks
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 浏览模式：分类走 categories/filter，榜单为排序+时间参数近似（同一接口）。 */
enum class BrowseMode(val title: String) {
    CATEGORY("分类"),
    RANK("榜单"),
}

private sealed interface BrowseUiState {
    data object Loading : BrowseUiState
    data class Loaded(val page: SearchPage) : BrowseUiState
    data class Failed(val message: String) : BrowseUiState
}

@Composable
fun BrowseScreen(mode: BrowseMode, gateway: Jm3Gateway, onOpenAlbum: (String) -> Unit) {
    var categoryValue by rememberSaveable { mutableStateOf(BrowseCategory.ALL.apiValue) }
    var orderValue by rememberSaveable { mutableStateOf(BrowseOrder.LATEST.apiValue) }
    var timeValue by rememberSaveable { mutableStateOf(BrowseTime.ALL.apiValue) }
    var state by remember { mutableStateOf<BrowseUiState>(BrowseUiState.Loading) }
    var loadingMore by remember { mutableStateOf(false) }
    // 加载更多失败单独置位，供列表底部给出重试入口
    var loadMoreFailed by remember { mutableStateOf(false) }
    // 仅下拉手势触发的刷新使用顶部指示器；切换筛选等程序化加载走列表内进度条。
    var refreshing by remember { mutableStateOf(false) }
    val refreshState = rememberPullToRefreshState(refreshThreshold = 0.18f)
    // 快速切换筛选时旧响应不能覆盖新条件的结果
    var requestToken by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    // 榜单走两列瀑布流，滚动状态与分类页的逐行列表分开保存
    val gridState = rememberLazyStaggeredGridState()
    val selectionHaptic = rememberSelectionHaptic()

    // 原版分类页的三个增强区块：分类树（子分类/更多分类）、热门标签、每周连载更新。
    // 三者都是可选数据，任一加载失败即隐藏对应区域，不影响主列表。
    var categoryTree by remember { mutableStateOf<CategoryTree?>(null) }
    var hotTags by remember { mutableStateOf<List<String>>(emptyList()) }
    var weeklyPage by remember { mutableStateOf<SearchPage?>(null) }
    var weeklyLoadingMore by remember { mutableStateOf(false) }
    // 子分类/标签点击后的检索词；非空时页面切换为标签结果，返回即恢复分类浏览。
    var tagKeyword by remember { mutableStateOf<String?>(null) }
    var showMoreSheet by remember { mutableStateOf(false) }
    var showWeeklySheet by remember { mutableStateOf(false) }

    val category = BrowseCategory.entries.firstOrNull { it.apiValue == categoryValue } ?: BrowseCategory.ALL
    val order = BrowseOrder.entries.firstOrNull { it.apiValue == orderValue } ?: BrowseOrder.LATEST
    val time = BrowseTime.entries.firstOrNull { it.apiValue == timeValue } ?: BrowseTime.ALL

    // 主分类 slug 与分类树一级节点同值，据此取当前分类的子分类（在列表项内读取，避免闭包捕获过期值）。
    val hasTagBlocks = categoryTree?.blocks?.isNotEmpty() == true

    fun filter(): BrowseFilter = when (mode) {
        BrowseMode.CATEGORY -> BrowseFilter(category = category, order = order, time = BrowseTime.ALL)
        BrowseMode.RANK -> BrowseFilter(category = BrowseCategory.ALL, order = order, time = time)
    }

    fun refresh(byPull: Boolean = false) {
        if (byPull) refreshing = true
        requestToken++
        val token = requestToken
        // 下拉刷新时保留当前列表，避免刷新指示器下方整屏空白
        if (!byPull) state = BrowseUiState.Loading
        scope.launch {
            val next = withContext(Dispatchers.IO) {
                runCatching { BrowseUiState.Loaded(gateway.browse(filter(), 1)) }
                    .getOrElse { BrowseUiState.Failed(it.message ?: "加载失败") }
            }
            if (token != requestToken) return@launch
            state = next
            refreshing = false
        }
    }

    fun loadMore() {
        val loaded = state as? BrowseUiState.Loaded ?: return
        if (loadingMore || loaded.page.currentPage >= loaded.page.totalPages) return
        loadingMore = true
        val token = requestToken
        scope.launch {
            val next = withContext(Dispatchers.IO) {
                runCatching { gateway.browse(filter(), loaded.page.currentPage + 1) }.getOrNull()
            }
            loadingMore = false
            // 加载期间切换了筛选：这批结果属于旧条件，丢弃以免串页
            if (next != null && token == requestToken) {
                loadMoreFailed = false
                state = loaded.copy(page = loaded.page.mergedWith(next))
            } else if (token == requestToken) {
                loadMoreFailed = true
            }
        }
    }

    // 子分类/标签没有独立的筛选参数，统一按标签检索展示结果。
    fun openTag(keyword: String) {
        if (keyword.isNotBlank()) tagKeyword = keyword
    }

    fun loadMoreWeekly() {
        val loaded = weeklyPage ?: return
        if (weeklyLoadingMore || loaded.currentPage >= loaded.totalPages) return
        weeklyLoadingMore = true
        scope.launch {
            val next = withContext(Dispatchers.IO) {
                runCatching { gateway.serialization(loaded.currentPage + 1) }.getOrNull()
            }
            weeklyLoadingMore = false
            if (next != null) weeklyPage = loaded.mergedWith(next)
        }
    }

    // 三个增强区块并行加载；失败只隐藏自身。
    LaunchedEffect(gateway) {
        coroutineScope {
            val treeRequest = async(Dispatchers.IO) { runCatching { gateway.categoriesList() }.getOrNull() }
            val tagsRequest = async(Dispatchers.IO) { runCatching { gateway.hotTags() }.getOrDefault(emptyList()) }
            val weeklyRequest = async(Dispatchers.IO) { runCatching { gateway.serialization(1) }.getOrNull() }
            categoryTree = treeRequest.await()
            hotTags = tagsRequest.await()
            weeklyPage = weeklyRequest.await()
        }
    }

    LaunchedEffect(mode, categoryValue, orderValue, timeValue) {
        refresh()
    }

    val loadedNow = state as? BrowseUiState.Loaded
    LaunchedEffect(loadedNow?.page) {
        if (loadedNow == null) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { last ->
                val page = (state as? BrowseUiState.Loaded)?.page ?: return@collect
                if (last != null && last >= page.items.size - 2) loadMore()
            }
    }

    // 榜单瀑布流的触底判定：取两列可见项的最大索引，保留与列表相同的提前两行触发
    LaunchedEffect(loadedNow?.page, mode) {
        if (mode != BrowseMode.RANK) return@LaunchedEffect
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.maxOfOrNull { it.index } }
            .collect { last ->
                val page = (state as? BrowseUiState.Loaded)?.page ?: return@collect
                if (last != null && last >= page.items.size - 2) loadMore()
            }
    }

    GlassScaffold(
        topBar = {
            val activeTag = tagKeyword
            GlassTopAppBar(
                title = if (activeTag != null) "标签：$activeTag" else mode.title,
                navigationIcon = {
                    // 标签结果页返回分类浏览；分类浏览本身是底部 tab，无需返回键。
                    if (activeTag != null) BackButton(onBack = { tagKeyword = null })
                },
                actions = {
                    IconButton(onClick = { refresh(byPull = true) }, enabled = !refreshing) {
                        Icon(
                            imageVector = MiuixIcons.Refresh,
                            contentDescription = "刷新",
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    // 连载更新无数据（接口失败或为空）时不展示入口
                    if (weeklyPage?.items?.isNotEmpty() == true) {
                        IconButton(onClick = { showWeeklySheet = true }) {
                            Icon(
                                imageVector = MiuixIcons.Weeks,
                                contentDescription = "每周连载更新",
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                },
                // 榜单筛选条常驻玻璃顶栏：横滑胶囊替代分段控件，与内容同处玻璃采样区
                bottomContent = {
                    if (mode == BrowseMode.RANK && activeTag == null) {
                        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                            FilterChipRow(
                                labels = BrowseTime.entries.map { it.label },
                                selectedIndex = BrowseTime.entries.indexOf(time),
                                onSelected = { timeValue = BrowseTime.entries[it].apiValue },
                            )
                            FilterChipRow(
                                labels = BrowseOrder.entries.map { it.shortLabel },
                                selectedIndex = BrowseOrder.entries.indexOf(order),
                                onSelected = { orderValue = BrowseOrder.entries[it].apiValue },
                            )
                        }
                    }
                },
                // 透明底色：玻璃底色与模糊由 GlassScaffold 统一绘制
                color = Color.Transparent,
            )
        },
    ) { innerPadding ->
        // 主框架切换 tab 会重建页面，滚动容器恢复到原位置后顶栏要跟上，否则大标题压在已滚内容上
        val activeTag = tagKeyword
        if (activeTag != null) {
            TagResultSection(
                gateway = gateway,
                keyword = activeTag,
                order = order,
                onOpenAlbum = onOpenAlbum,
                modifier = Modifier.fillMaxSize(),
                contentPadding = innerPadding,
            )
        } else if (mode == BrowseMode.RANK) {
            // 榜单：筛选条常驻玻璃顶栏，内容区只保留两列瀑布流
            PullToRefresh(
                isRefreshing = refreshing,
                onRefresh = { refresh(byPull = true) },
                pullToRefreshState = refreshState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = innerPadding,
            ) {
                RankResultGrid(
                    state = state,
                    gridState = gridState,
                    contentPadding = innerPadding,
                    refreshing = refreshing,
                    loadingMore = loadingMore,
                    loadMoreFailed = loadMoreFailed,
                    onRetry = ::refresh,
                    onLoadMore = ::loadMore,
                    onOpenAlbum = onOpenAlbum,
                )
            }
        } else {
            // 列表内容穿到玻璃顶栏（顶栏 + 筛选条）下方，顶栏才能采样到真实背景
            PullToRefresh(
                isRefreshing = refreshing,
                onRefresh = { refresh(byPull = true) },
                pullToRefreshState = refreshState,
                modifier = Modifier.fillMaxSize(),
                // 刷新指示器下移到玻璃顶栏之下，避免被顶栏遮住
                contentPadding = innerPadding,
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = innerPadding,
                ) {
                    item(key = "filters") {
                        // 当前主分类的子分类（分类树尚未返回时为空，区域自动隐藏）
                        val subCategories = categoryTree?.categories
                            ?.firstOrNull { it.slug == category.apiValue }
                            ?.children
                            .orEmpty()
                        // 分组筛选统一用 Miuix TabRow（分段控件语义），替代手写 Chip 行。
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            if (mode == BrowseMode.CATEGORY) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    TabRow(
                                        tabs = BrowseCategory.entries.map { it.label },
                                        selectedTabIndex = BrowseCategory.entries.indexOf(category),
                                        onTabSelected = {
                                            selectionHaptic()
                                            categoryValue = BrowseCategory.entries[it].apiValue
                                        },
                                        modifier = Modifier.weight(1f),
                                    )
                                    // 「更多分类」入口固定在主分类条右侧（原版 Header 的 MoreHoriz 位置）
                                    if (hasTagBlocks) {
                                        Spacer(modifier = Modifier.size(width = 8.dp, height = 0.dp))
                                        TextButton(
                                            text = "更多",
                                            onClick = { showMoreSheet = true },
                                            minWidth = 0.dp,
                                            minHeight = 32.dp,
                                        )
                                    }
                                }
                                // 子分类横向胶囊条：取当前主分类的 children，点击按标签检索
                                if (subCategories.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    LazyRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        items(subCategories) { sub ->
                                            TextButton(
                                                text = sub.name,
                                                onClick = { openTag(sub.name) },
                                                minWidth = 0.dp,
                                                minHeight = 32.dp,
                                            )
                                        }
                                    }
                                }
                            }
                            FilterGroupLabel(text = "排序")
                            TabRow(
                                tabs = BrowseOrder.entries.map { it.shortLabel },
                                selectedTabIndex = BrowseOrder.entries.indexOf(order),
                                onTabSelected = {
                                    selectionHaptic()
                                    orderValue = BrowseOrder.entries[it].apiValue
                                },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            )
                            // 热门标签行：取前若干项，横向滚动，点击按标签检索；榜单页不提供标签导航
                            if (mode == BrowseMode.CATEGORY && hotTags.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                SmallTitle(text = "热门标签")
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    items(hotTags.take(12)) { tag ->
                                        TextButton(
                                            text = tag,
                                            onClick = { openTag(tag) },
                                            minWidth = 0.dp,
                                            minHeight = 32.dp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    when (val s = state) {
                        is BrowseUiState.Loading -> if (!refreshing) {
                            // 下拉刷新时顶部指示器已在转圈，列表内不再重复显示。
                            item(key = "loading") {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    InfiniteProgressIndicator()
                                }
                            }
                        }
                        is BrowseUiState.Failed -> item(key = "error") {
                            ErrorScreen(
                                message = "加载失败：${s.message}",
                                onRetry = ::refresh,
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                            )
                        }
                        is BrowseUiState.Loaded -> searchResultItems(
                            page = s.page,
                            loadingMore = loadingMore,
                            loadMoreFailed = loadMoreFailed,
                            onLoadMore = ::loadMore,
                            onOpenAlbum = onOpenAlbum,
                        )
                    }
                }
            }
        }

        // 「更多分类」弹窗：分区展示标签区块，点击标签进入标签结果
        OverlayBottomSheet(
            show = showMoreSheet,
            title = "更多分类",
            onDismissRequest = { showMoreSheet = false },
        ) {
            CategoryBlocksSheet(
                blocks = categoryTree?.blocks.orEmpty(),
                onTagClick = { tag ->
                    showMoreSheet = false
                    openTag(tag)
                },
            )
        }

        // 「每周连载更新」弹窗：与首页周更入口同源，支持继续加载下一页
        OverlayBottomSheet(
            show = showWeeklySheet,
            title = "每周连载更新",
            onDismissRequest = { showWeeklySheet = false },
        ) {
            val page = weeklyPage
            if (page == null || page.items.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    InfiniteProgressIndicator()
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp)) {
                    items(page.items, key = { it.id }) { result ->
                        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                            BasicComponent(
                                title = result.title,
                                summary = listOf(
                                    result.authors.joinToString(" "),
                                    result.tags.joinToString(" "),
                                ).filter { it.isNotBlank() }
                                    .joinToString(" · ")
                                    .ifBlank { null },
                                onClick = {
                                    showWeeklySheet = false
                                    onOpenAlbum(result.id)
                                },
                            )
                        }
                    }
                    if (page.currentPage < page.totalPages) {
                        item(key = "weekly-more") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (weeklyLoadingMore) {
                                    InfiniteProgressIndicator(modifier = Modifier.size(20.dp))
                                } else {
                                    TextButton(text = "加载更多", onClick = ::loadMoreWeekly)
                                }
                            }
                        }
                    }
                    item(key = "weekly-gap") { Spacer(modifier = Modifier.height(12.dp)) }
                }
            }
        }
    }
}

/** 「更多分类」分区：每个标签区块一张卡片，标签用可换行胶囊排布。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryBlocksSheet(blocks: List<CategoryBlock>, onTagClick: (String) -> Unit) {
    if (blocks.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(text = "暂无更多分类")
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp)) {
            items(blocks) { block ->
                SmallTitle(text = block.title)
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        block.tags.forEach { tag ->
                            TextButton(
                                text = tag,
                                onClick = { onTagClick(tag) },
                                minWidth = 0.dp,
                                minHeight = 32.dp,
                            )
                        }
                    }
                }
            }
            item(key = "blocks-gap") { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}

/** 标签/子分类检索结果：BrowseScreen 内部页面，返回即回到分类浏览，避免跳转其它页面。 */
@Composable
private fun TagResultSection(
    gateway: Jm3Gateway,
    keyword: String,
    order: BrowseOrder,
    onOpenAlbum: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    var state by remember(keyword, order) { mutableStateOf<BrowseUiState>(BrowseUiState.Loading) }
    var loadingMore by remember(keyword, order) { mutableStateOf(false) }
    // 加载更多失败单独置位，供列表底部给出重试入口
    var loadMoreFailed by remember(keyword, order) { mutableStateOf(false) }
    var requestToken by remember(keyword, order) { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    // 从其他 tab 切回时列表已恢复滚动位置，顶栏需要补同步

    fun load() {
        requestToken++
        val token = requestToken
        state = BrowseUiState.Loading
        scope.launch {
            val next = withContext(Dispatchers.IO) {
                runCatching { BrowseUiState.Loaded(gateway.search(keyword, 1, order, SearchScope.TAG)) }
                    .getOrElse { BrowseUiState.Failed(it.message ?: "加载失败") }
            }
            if (token != requestToken) return@launch
            state = next
        }
    }

    fun loadMore() {
        val loaded = state as? BrowseUiState.Loaded ?: return
        if (loadingMore || loaded.page.currentPage >= loaded.page.totalPages) return
        loadingMore = true
        val token = requestToken
        scope.launch {
            val next = withContext(Dispatchers.IO) {
                runCatching { gateway.search(keyword, loaded.page.currentPage + 1, order, SearchScope.TAG) }
                    .getOrNull()
            }
            loadingMore = false
            if (next != null && token == requestToken) {
                loadMoreFailed = false
                state = loaded.copy(page = loaded.page.mergedWith(next))
            } else if (token == requestToken) {
                loadMoreFailed = true
            }
        }
    }

    LaunchedEffect(keyword, order) { load() }

    LaunchedEffect((state as? BrowseUiState.Loaded)?.page) {
        if (state !is BrowseUiState.Loaded) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { last ->
                val page = (state as? BrowseUiState.Loaded)?.page ?: return@collect
                if (last != null && last >= page.items.size - 2) loadMore()
            }
    }

    Column(modifier = modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            when (val s = state) {
                is BrowseUiState.Loading -> item(key = "tag-loading") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        InfiniteProgressIndicator()
                    }
                }
                is BrowseUiState.Failed -> item(key = "tag-error") {
                    ErrorScreen(
                        message = "加载失败：${s.message}",
                        onRetry = ::load,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                }
                is BrowseUiState.Loaded -> searchResultItems(
                    page = s.page,
                    loadingMore = loadingMore,
                    loadMoreFailed = loadMoreFailed,
                    onLoadMore = ::loadMore,
                    onOpenAlbum = onOpenAlbum,
                )
            }
        }
    }
}

/** 筛选分组标题：分段控件只放得下一屏标签，分组语境靠这行小字承担。 */
@Composable
private fun FilterGroupLabel(text: String) {
    Text(
        text = text,
        fontSize = MiuixTheme.textStyles.footnote1.fontSize,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
    )
}

/**
 * 排序短标签：4 个分段在 369dp 宽度下约 78dp 一格，全称（如「最多观看」）会被省略号截断。
 * 排序语义由分组标题「排序」给定，这里只保留可区分的两字。
 */
private val BrowseOrder.shortLabel: String
    get() = when (this) {
        BrowseOrder.LATEST -> "最新"
        BrowseOrder.MOST_VIEWED -> "观看"
        BrowseOrder.MOST_IMAGES -> "图片"
        BrowseOrder.MOST_LIKED -> "喜欢"
    }

private const val RANK_GRID_COLUMNS = 2
private val RANK_GRID_VERTICAL_SPACING = 8.dp
private val RANK_GRID_HORIZONTAL_PADDING = 16.dp

/**
 * 榜单结果区：两列封面瀑布流，加载中/失败态占整行。
 *
 * 筛选条件已常驻玻璃顶栏，这里不再重复展示口径，只负责结果与加载更多。
 */
@Composable
private fun RankResultGrid(
    state: BrowseUiState,
    gridState: LazyStaggeredGridState,
    contentPadding: PaddingValues,
    refreshing: Boolean,
    loadingMore: Boolean,
    loadMoreFailed: Boolean,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenAlbum: (String) -> Unit,
) {
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(RANK_GRID_COLUMNS),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        // 顶部让出玻璃顶栏高度，再加 8dp 与筛选条脱开
        contentPadding = PaddingValues(
            start = RANK_GRID_HORIZONTAL_PADDING,
            end = RANK_GRID_HORIZONTAL_PADDING,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding(),
        ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalItemSpacing = RANK_GRID_VERTICAL_SPACING,
    ) {
        when (state) {
            is BrowseUiState.Loading -> if (!refreshing) {
                item(key = "rank-loading", span = StaggeredGridItemSpan.FullLine) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        InfiniteProgressIndicator()
                    }
                }
            }
            is BrowseUiState.Failed -> item(key = "rank-error", span = StaggeredGridItemSpan.FullLine) {
                ErrorScreen(
                    message = "加载失败：${state.message}",
                    onRetry = onRetry,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }
            is BrowseUiState.Loaded -> rankResultGrid(
                page = state.page,
                loadingMore = loadingMore,
                loadMoreFailed = loadMoreFailed,
                onLoadMore = onLoadMore,
                onOpenAlbum = onOpenAlbum,
            )
        }
    }
}
