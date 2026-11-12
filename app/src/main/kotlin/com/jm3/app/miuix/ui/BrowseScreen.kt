package com.jm3.app.miuix.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.ui.glass.GlassPullToRefresh
import com.jm3.app.miuix.ui.glass.GlassScaffold
import com.jm3.app.miuix.ui.glass.GlassTopAppBar
import com.jm3.app.miuix.ui.glass.withSpacing
import com.jm3.app.miuix.ui.route.AppRoutes
import com.jm3.app.miuix.ui.skin.LocalSkin
import com.jm3.core.appendedBy
import com.jm3.core.model.BrowseCategory
import com.jm3.core.model.BrowseFilter
import com.jm3.core.model.BrowseOrder
import com.jm3.core.model.BrowseTime
import com.jm3.core.model.CategoryBlock
import com.jm3.core.model.CategoryTree
import com.jm3.core.model.SearchResult
import com.jm3.core.model.SearchScope
import com.jm3.core.paging.PagedFeed
import com.jm3.core.paging.pagerTargetPage
import com.jm3.core.session.SessionOutcome
import com.jm3.core.settings.PaginationMode
import com.jm3.core.toFeed
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Weeks
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet

/** 浏览模式：分类走 categories/filter，榜单为排序+时间参数近似（同一接口）。 */
enum class BrowseMode(val title: String) {
    CATEGORY("分类"),
    RANK("榜单"),
}

@Composable
internal fun BrowseScreen(
    deps: ScreenDeps,
    mode: BrowseMode,
    nav: AppRoutes,
) {
    val skin = LocalSkin.current
    var categoryValue by rememberSaveable { mutableStateOf(BrowseCategory.ALL.apiValue) }
    var orderValue by rememberSaveable { mutableStateOf(BrowseOrder.LATEST.apiValue) }
    var timeValue by rememberSaveable { mutableStateOf(BrowseTime.ALL.apiValue) }
    // 筛选口径与列表结果一起进缓存：切 tab 回来时按同一口径取回上次的结果，滚动位置才有内容可落
    val pageKey = BrowsePageKey(mode, categoryValue, orderValue, timeValue)
    val sessionCache = deps.sessionCache
    val pageMode = deps.paging.paginationMode
    val clickPaging = pageMode == PaginationMode.CLICK
    var state by remember(pageKey) { mutableStateOf(sessionCache.stateOf(SessionListKey.Browse(pageKey))) }
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
    var weeklyFeed by remember { mutableStateOf(PagedFeed<SearchResult>()) }
    // 周更区块的三态：空列表既可能是「还没回来」也可能是「回来 0 条」，必须分开
    var weeklyLoading by remember { mutableStateOf(true) }
    var weeklyFailure by remember { mutableStateOf<ScreenLoadPlan?>(null) }
    var weeklyLoadToken by remember { mutableIntStateOf(0) }
    // 子分类/标签点击后的检索词；非空时页面切换为标签结果，返回即恢复分类浏览。
    var tagKeyword by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = tagKeyword != null) { tagKeyword = null }
    var showMoreSheet by rememberSaveable { mutableStateOf(false) }
    var showWeeklySheet by rememberSaveable { mutableStateOf(false) }

    val category = BrowseCategory.entries.firstOrNull { it.apiValue == categoryValue } ?: BrowseCategory.ALL
    val order = BrowseOrder.entries.firstOrNull { it.apiValue == orderValue } ?: BrowseOrder.LATEST
    val time = BrowseTime.entries.firstOrNull { it.apiValue == timeValue } ?: BrowseTime.ALL

    // 主分类 slug 与分类树一级节点同值，据此取当前分类的子分类（在列表项内读取，避免闭包捕获过期值）。
    val hasTagBlocks = categoryTree?.blocks?.isNotEmpty() == true

    fun filter(): BrowseFilter = when (mode) {
        BrowseMode.CATEGORY -> BrowseFilter(category = category, order = order, time = BrowseTime.ALL)
        BrowseMode.RANK -> BrowseFilter(category = BrowseCategory.ALL, order = order, time = time)
    }

    /**
     * 列表状态与缓存一起改。
     *
     * 只改 state 的话，切走再回来缓存里还是上一份，页面会先闪一次旧列表再跳成新的。
     */
    fun publish(key: BrowsePageKey, next: ListState) {
        sessionCache.remember(SessionListKey.Browse(key), next)
        if (key == pageKey) {
            state = next
        }
    }

    fun refresh(byPull: Boolean = false) {
        if (byPull) refreshing = true
        requestToken++
        val token = requestToken
        val key = pageKey
        // 下拉刷新时保留当前列表，避免刷新指示器下方整屏空白
        if (!byPull) publish(key, ListState.Loading)
        scope.launch {
            val next = deps.authCall.call { deps.gateway.browse(filter(), 1) }.asListState("加载失败")
            if (token != requestToken) return@launch
            publish(key, next)
            refreshing = false
        }
    }

    /** 点击分页：按目标页整页替换，沿用首屏缓存与筛选口径。 */
    fun jumpToPage(target: Int) {
        val current = (state as? ListState.Loaded)?.feed ?: return
        val resolved = pagerTargetPage(current.page, target, current.pageCount)
        if (resolved == current.page) return
        refreshing = false
        requestToken++
        val token = requestToken
        val key = pageKey
        publish(key, ListState.Loading)
        scope.launch {
            val next = deps.authCall.call { deps.gateway.browse(filter(), resolved) }.asListState("加载失败")
            if (token != requestToken) return@launch
            publish(key, next)
            if (mode == BrowseMode.RANK) gridState.scrollToItem(0) else listState.scrollToItem(0)
        }
    }

    fun loadMore() {
        val loaded = state as? ListState.Loaded ?: return
        val requested = loaded.feed.beginLoadMore()
        if (requested === loaded.feed) return
        val token = requestToken
        val key = pageKey
        state = ListState.Loaded(requested)
        scope.launch {
            val next = deps.authCall.call { deps.gateway.browse(filter(), requested.page + 1) }
            // 加载期间切换了筛选：这批结果属于旧条件，丢弃以免串页
            if (token != requestToken) return@launch
            val updated = when (next) {
                is SessionOutcome.Ok -> requested.appendedBy(next.value)
                else -> requested.failLoadMore()
            }
            publish(key, ListState.Loaded(updated))
        }
    }

    // 子分类/标签没有独立的筛选参数，统一按标签检索展示结果。
    fun openTag(keyword: String) {
        normalizeBrowseTagKeyword(keyword)?.let { tagKeyword = it }
    }

    fun loadMoreWeekly() {
        val requested = weeklyFeed.beginLoadMore()
        if (requested === weeklyFeed) return
        val token = ++weeklyLoadToken
        weeklyFeed = requested
        weeklyFailure = null
        scope.launch {
            val outcome = deps.authCall.call { deps.gateway.serialization(requested.page + 1) }
            if (token != weeklyLoadToken) return@launch
            val next = resolveWeeklyLoadMore(requested = requested, outcome = outcome)
            weeklyFeed = next.feed
            weeklyFailure = next.failure
        }
    }

    /** 周更首屏/重试：成功给列表、失效与失败给可重试的失败态，空结果不等于加载中。 */
    fun loadWeekly() {
        val token = ++weeklyLoadToken
        weeklyLoading = true
        weeklyFailure = null
        scope.launch {
            val outcome = deps.authCall.call { deps.gateway.serialization(1) }
            if (token != weeklyLoadToken) return@launch
            val next = resolveWeeklyLoad(currentFeed = weeklyFeed, outcome = outcome)
            weeklyLoading = false
            weeklyFailure = next.failure
            weeklyFeed = next.feed
        }
    }

    // 三个增强区块并行加载；失败或会话失效只影响各自区域，不影响主列表与整屏错误态。
    LaunchedEffect(deps.gateway, deps.authCall) {
        loadWeekly()
        coroutineScope {
            val treeRequest = async { deps.authCall.call { deps.gateway.categoriesList() } }
            val tagsRequest = async { deps.authCall.call { deps.gateway.hotTags() } }
            categoryTree = treeRequest.await().orDegrade<CategoryTree?>(null)
            hotTags = tagsRequest.await().orDegrade(emptyList())
        }
    }

    LaunchedEffect(pageKey) {
        val cacheKey = SessionListKey.Browse(pageKey)
        // 同一口径的结果还在缓存里（切 tab 或切回已缓存筛选）：直接同步缓存并作废旧在途请求
        if (sessionCache.hasPage(cacheKey)) {
            requestToken++
            refreshing = false
            state = sessionCache.stateOf(cacheKey)
            return@LaunchedEffect
        }
        refresh()
    }

    PaginationModeReloadEffect(pageMode) { refresh() }

    // 触底加载：分类页跟逐行列表，榜单页跟两列瀑布流，判据由 PagedFeed 统一给出
    val browsing = state as? ListState.Loaded
    if (!clickPaging && browsing != null) {
        if (mode == BrowseMode.RANK) {
            LoadMoreOnScroll(gridState = gridState, feed = browsing.feed, onLoadMore = ::loadMore)
        } else {
            LoadMoreOnScroll(listState = listState, feed = browsing.feed, onLoadMore = ::loadMore)
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
                    // 入口常驻：加载中/失败/空都在弹窗内有独立形态，不能拿「有没有数据」决定能否打开
                    IconButton(onClick = { showWeeklySheet = true }) {
                        Icon(
                            imageVector = MiuixIcons.Weeks,
                            contentDescription = "每周连载更新",
                            modifier = Modifier.size(24.dp),
                        )
                    }
                },
                // 筛选条常驻玻璃顶栏：与内容同处玻璃采样区，列表滚动时玻璃背景随之实时更新
                bottomContent = {
                    if (activeTag == null) {
                        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                            if (mode == BrowseMode.RANK) {
                                GlassChipRow(
                                    tabs = BrowseTime.entries.map { it.label },
                                    selectedTabIndex = BrowseTime.entries.indexOf(time),
                                    onTabSelected = { index ->
                                        val picked = BrowseTime.entries.getOrNull(index) ?: return@GlassChipRow
                                        selectionHaptic()
                                        timeValue = picked.apiValue
                                    },
                                    contentPadding = FilterChipsPadding,
                                )
                                GlassChipRow(
                                    tabs = BrowseOrder.entries.map { it.shortLabel },
                                    selectedTabIndex = BrowseOrder.entries.indexOf(order),
                                    onTabSelected = { index ->
                                        val picked = BrowseOrder.entries.getOrNull(index) ?: return@GlassChipRow
                                        selectionHaptic()
                                        orderValue = picked.apiValue
                                    },
                                    contentPadding = FilterChipsPadding,
                                )
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    GlassChipRow(
                                        tabs = BrowseCategory.entries.map { it.label },
                                        selectedTabIndex = BrowseCategory.entries.indexOf(category),
                                        onTabSelected = { index ->
                                            val picked = BrowseCategory.entries.getOrNull(index) ?: return@GlassChipRow
                                            selectionHaptic()
                                            categoryValue = picked.apiValue
                                        },
                                        modifier = Modifier.weight(1f).chipRowEndFade(),
                                        contentPadding = PaddingValues(vertical = 4.dp),
                                    )
                                    // 「更多分类」入口固定在主分类条右侧（原版 Header 的 MoreHoriz 位置）
                                    if (hasTagBlocks) {
                                        Spacer(modifier = Modifier.size(width = 8.dp, height = 0.dp))
                                        GlassActionChip(
                                            text = "更多",
                                            onClick = { showMoreSheet = true },
                                        )
                                    }
                                }
                                GlassChipRow(
                                    tabs = BrowseOrder.entries.map { it.shortLabel },
                                    selectedTabIndex = BrowseOrder.entries.indexOf(order),
                                    onTabSelected = { index ->
                                        val picked = BrowseOrder.entries.getOrNull(index) ?: return@GlassChipRow
                                        selectionHaptic()
                                        orderValue = picked.apiValue
                                    },
                                    contentPadding = FilterChipsPadding,
                                )
                            }
                        }
                    }
                },
                // 透明底色：玻璃底色与模糊由 GlassScaffold 统一绘制
                color = Color.Transparent,
            )
        },
    ) { innerPadding ->
        // 主框架切换 tab 会重建页面，滚动容器恢复到原位置后顶栏要跟上，否则大标题压在已滚内容上
        // 用 AnimatedContent 只在标签结果与分类/榜单之间切换时播放横向转场，从详情/阅读器返回同一标签页时不重播入场
        AnimatedContent(
            targetState = tagKeyword,
            contentKey = { it != null },
            transitionSpec = {
                val enteringTag = targetState != null
                val enter = slideInHorizontally { width -> if (enteringTag) width / 3 else -width / 4 } + fadeIn()
                val exit = slideOutHorizontally { width -> if (enteringTag) -width / 4 else width / 3 } + fadeOut()
                (enter togetherWith exit).using(SizeTransform(clip = false))
            },
            label = "browse-tag-transition",
        ) { currentTag ->
            if (currentTag != null) {
                TagResultSection(
                    deps = deps,
                    keyword = currentTag,
                    order = order,
                    onOpenAlbum = nav::album,
                    onOpenLogin = nav::login,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = innerPadding,
                )
            } else if (mode == BrowseMode.RANK) {
                // 榜单：筛选条常驻玻璃顶栏，内容区只保留两列瀑布流
                GlassPullToRefresh(
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
                        onRetry = ::refresh,
                        onOpenLogin = nav::login,
                        onLoadMore = ::loadMore,
                        onOpenAlbum = nav::album,
                        clickPaging = clickPaging,
                        onPageChange = ::jumpToPage,
                    )
                }
            } else {
                // 列表内容穿到玻璃顶栏（顶栏 + 筛选条）下方，顶栏才能采样到真实背景
                GlassPullToRefresh(
                    isRefreshing = refreshing,
                    onRefresh = { refresh(byPull = true) },
                    pullToRefreshState = refreshState,
                    modifier = Modifier.fillMaxSize(),
                    // 刷新指示器下移到玻璃顶栏之下，避免被顶栏遮住
                    contentPadding = innerPadding,
                ) {
                    // 大屏行卡两列：列数由 WideLayout.feedColumns 判定，手机断点仍是单列
                    val feedColumns = LocalWideLayout.current.feedColumns
                    TrackedLazyColumn(
                        state = listState,
                        contentPadding = innerPadding,
                        modifier = Modifier.fillMaxSize().readableWidth(),
                    ) {
                        item(key = "filters") {
                            // 当前主分类的子分类（分类树尚未返回时为空，区域自动隐藏）
                            val subCategories = categoryTree?.categories
                                ?.firstOrNull { it.slug == category.apiValue }
                                ?.children
                                .orEmpty()
                            val hasHotTags = mode == BrowseMode.CATEGORY && hotTags.isNotEmpty()
                            // 主分类与排序已常驻玻璃顶栏：这里只保留随内容滚动的子分类与热门标签导航
                            if (subCategories.isNotEmpty() || hasHotTags) {
                                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                    // 子分类横向胶囊条：取当前主分类的 children，点击按标签检索
                                    if (subCategories.isNotEmpty()) {
                                        LazyRow(
                                            modifier = Modifier.fillMaxWidth(),
                                            contentPadding = PaddingValues(horizontal = SectionHorizontalPadding),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            items(subCategories, key = { it.name }) { sub ->
                                                MinTouchTarget(onClick = { openTag(sub.name) }) {
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
                                    // 热门标签行：取前若干项，横向滚动，点击按标签检索；榜单页不提供标签导航
                                    if (hasHotTags) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        SmallTitle(text = "热门标签")
                                        LazyRow(
                                            contentPadding = PaddingValues(horizontal = SectionHorizontalPadding),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            items(hotTags.take(12), key = { it }) { tag ->
                                                MinTouchTarget(onClick = { openTag(tag) }) {
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
                            }
                        }
                        when (val s = state) {
                            is ListState.Loading -> if (!refreshing) {
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
                            is ListState.Failed -> item(key = "error") {
                                ScreenFailureOrLogin(
                                    message = s.message,
                                    expired = s.sessionExpired,
                                    onRetry = ::refresh,
                                    onOpenLogin = nav::login,
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                )
                            }
                            is ListState.Loaded -> searchResultItems(
                                feed = s.feed,
                                onLoadMore = ::loadMore,
                                onOpenAlbum = nav::album,
                                clickPaging = clickPaging,
                                onPageChange = ::jumpToPage,
                                columns = feedColumns,
                            )
                        }
                    }
                }
            }
        }

        // 「更多分类」弹窗：分区展示标签区块，点击标签进入标签结果
        OverlayBottomSheet(
            show = showMoreSheet,
            title = "更多分类",
            cornerRadius = skin.shapes.sheetCorner,
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
            cornerRadius = skin.shapes.sheetCorner,
            onDismissRequest = { showWeeklySheet = false },
        ) {
            val feed = weeklyFeed
            val failure = weeklyFailure
            when {
                feed.items.isNotEmpty() -> LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = overlayMaxHeight(440.dp))) {
                    items(feed.items, key = { it.id }) { result ->
                        WorkRowCard(
                            coverUrl = result.coverUrl,
                            title = result.title,
                            onClick = {
                                showWeeklySheet = false
                                nav.album(result.id)
                            },
                            albumId = result.id,
                            subtitle = result.authors.authorNames(),
                            category = result.category,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        ) {
                            WorkCardMetaRow(tags = result.tags, albumCode = result.id)
                        }
                    }
                    if (failure?.phase == ScreenLoadPhase.Expired) {
                        item(key = "weekly-expired") {
                            ScreenFailureOrLogin(
                                message = failure.message.orEmpty(),
                                expired = true,
                                onRetry = ::loadWeekly,
                                onOpenLogin = {
                                    showWeeklySheet = false
                                    weeklyFailure = null
                                    nav.login()
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    } else if (feed.hasMore) {
                        item(key = "weekly-more") {
                            LoadMoreFooter(feed = feed, onLoadMore = ::loadMoreWeekly)
                        }
                    }
                    item(key = "weekly-gap") { Spacer(modifier = Modifier.height(12.dp)) }
                }

                weeklyLoading -> Box(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    InfiniteProgressIndicator()
                }

                failure != null -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = overlayMaxHeight(240.dp))
                        .verticalScroll(rememberScrollState()),
                ) {
                    ScreenFailureOrLogin(
                        message = failure.message.orEmpty(),
                        expired = failure.phase == ScreenLoadPhase.Expired,
                        onRetry = ::loadWeekly,
                        onOpenLogin = {
                            showWeeklySheet = false
                            nav.login()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                else -> Box(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "暂无每周更新")
                }
            }
        }
    }
}

/** 「更多分类」分区：每个标签区块一张卡片，标签用可换行胶囊排布。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryBlocksSheet(blocks: List<CategoryBlock>, onTagClick: (String) -> Unit) {
    val skin = LocalSkin.current
    if (blocks.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(text = "暂无更多分类")
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = overlayMaxHeight(440.dp))) {
            items(blocks, key = { it.title }) { block ->
                SmallTitle(text = block.title)
                Card(cornerRadius = skin.shapes.cardCorner, modifier = Modifier.fillMaxWidth().padding(horizontal = SectionHorizontalPadding, vertical = 4.dp)) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        block.tags.forEach { tag ->
                            MinTouchTarget(onClick = { onTagClick(tag) }) {
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
            }
            item(key = "blocks-gap") { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}

/** 标签/子分类检索结果：BrowseScreen 内部页面，返回即回到分类浏览，避免跳转其它页面。 */
@Composable
private fun TagResultSection(
    deps: ScreenDeps,
    keyword: String,
    order: BrowseOrder,
    onOpenAlbum: (String) -> Unit,
    onOpenLogin: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val normalizedKeyword = normalizeBrowseTagKeyword(keyword) ?: keyword.trim()
    val tagKey = browseTagCacheKey(normalizedKeyword, order) ?: SessionListKey.BrowseTag(normalizedKeyword, order)
    var state by remember(tagKey) { mutableStateOf(deps.sessionCache.stateOf(tagKey)) }
    var requestToken by remember { mutableIntStateOf(0) }
    val pageMode = deps.paging.paginationMode
    val clickPaging = pageMode == PaginationMode.CLICK
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    // 从详情/阅读器退栈重建后首帧即可命中 SessionCache，与 listState 恢复的滚动下标直接对齐

    fun publish(key: SessionListKey.BrowseTag, next: ListState) {
        state = next
        deps.sessionCache.remember(key, next)
    }

    fun load() {
        requestToken++
        val token = requestToken
        val key = tagKey
        publish(key, ListState.Loading)
        scope.launch {
            val next = deps.authCall.call { deps.gateway.search(key.keyword, 1, order, SearchScope.TAG) }
                .asListState("加载失败")
            if (token != requestToken) return@launch
            publish(key, next)
        }
    }

    /** 点击分页：按目标页整页替换，检索口径不变。 */
    fun jumpToPage(target: Int) {
        val current = (state as? ListState.Loaded)?.feed ?: return
        val resolved = pagerTargetPage(current.page, target, current.pageCount)
        if (resolved == current.page) return
        requestToken++
        val token = requestToken
        val key = tagKey
        publish(key, ListState.Loading)
        scope.launch {
            val next = deps.authCall.call { deps.gateway.search(key.keyword, resolved, order, SearchScope.TAG) }
                .asListState("加载失败")
            if (token != requestToken) return@launch
            publish(key, next)
            listState.scrollToItem(0)
        }
    }

    fun loadMore() {
        val loaded = state as? ListState.Loaded ?: return
        val requested = loaded.feed.beginLoadMore()
        if (requested === loaded.feed) return
        val token = requestToken
        val key = tagKey
        state = ListState.Loaded(requested)
        scope.launch {
            val next = deps.authCall.call { deps.gateway.search(key.keyword, requested.page + 1, order, SearchScope.TAG) }
            if (token != requestToken) return@launch
            val updated = when (next) {
                is SessionOutcome.Ok -> requested.appendedBy(next.value)
                else -> requested.failLoadMore()
            }
            publish(key, ListState.Loaded(updated))
        }
    }

    LaunchedEffect(tagKey) {
        if (deps.sessionCache.hasPage(tagKey)) return@LaunchedEffect
        load()
    }
    PaginationModeReloadEffect(pageMode) { load() }

    val loaded = state as? ListState.Loaded
    if (!clickPaging && loaded != null) {
        LoadMoreOnScroll(listState = listState, feed = loaded.feed, onLoadMore = ::loadMore)
    }

    Column(modifier = modifier) {
        // 大屏行卡两列：列数由 WideLayout.feedColumns 判定，手机断点仍是单列
        val feedColumns = LocalWideLayout.current.feedColumns
        TrackedLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize().readableWidth(),
        ) {
            when (val s = state) {
                is ListState.Loading -> item(key = "tag-loading") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        InfiniteProgressIndicator()
                    }
                }
                is ListState.Failed -> item(key = "tag-error") {
                    ScreenFailureOrLogin(
                        message = s.message,
                        expired = s.sessionExpired,
                        onRetry = ::load,
                        onOpenLogin = onOpenLogin,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                }
                is ListState.Loaded -> searchResultItems(
                    feed = s.feed,
                    onLoadMore = ::loadMore,
                    onOpenAlbum = onOpenAlbum,
                    clickPaging = clickPaging,
                    onPageChange = ::jumpToPage,
                    columns = feedColumns,
                )
            }
        }
    }
}

/**
 * 排序短标签：4 个分段在 369dp 宽度下约 78dp 一格，全称（如「最多观看」）会被省略号截断。
 * 页签常驻顶栏、不再有分组标题，短标签需自身可区分。
 */
private val BrowseOrder.shortLabel: String
    get() = when (this) {
        BrowseOrder.LATEST -> "最新"
        BrowseOrder.MOST_VIEWED -> "观看"
        BrowseOrder.MOST_IMAGES -> "图片"
        BrowseOrder.MOST_LIKED -> "喜欢"
    }

// 列宽自适应阈值：StaggeredGridCells.Adaptive 按 (可用宽度+间距)/(阈值+间距) 取整定列数，
// 369dp 宽的手机扣除左右各 16dp 后只有 337dp，180dp 阈值会被压成 1 列；
// 160dp 保证 360dp 及以上宽度的手机仍排 2 列，平板则自动排 3 列以上。
private val RANK_GRID_MIN_CARD_WIDTH = 160.dp
private val RANK_GRID_VERTICAL_SPACING = 8.dp
private val RANK_GRID_HORIZONTAL_PADDING = 16.dp

/** 分类页筛选胶囊条的留白：与玻璃顶栏左右 16dp 对齐，上下各 4dp 使两行胶囊间距与旧版一致。 */
private val FilterChipsPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)

/**
 * 榜单结果区：两列封面瀑布流，加载中/失败态占整行。
 *
 * 筛选条件已常驻玻璃顶栏，这里不再重复展示口径，只负责结果与加载更多。
 */
@Composable
private fun RankResultGrid(
    state: ListState,
    gridState: LazyStaggeredGridState,
    contentPadding: PaddingValues,
    refreshing: Boolean,
    onRetry: () -> Unit,
    onOpenLogin: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    clickPaging: Boolean = false,
    onPageChange: ((Int) -> Unit)? = null,
) {
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(RANK_GRID_MIN_CARD_WIDTH),
        state = gridState,
        modifier = Modifier.fillMaxSize().readableWidth(),
        // 顶部让出玻璃顶栏高度（随折叠实时收缩），再加 8dp 与筛选条脱开
        contentPadding = contentPadding.withSpacing(
            horizontal = RANK_GRID_HORIZONTAL_PADDING,
            extraTop = 8.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalItemSpacing = RANK_GRID_VERTICAL_SPACING,
    ) {
        when (state) {
            is ListState.Loading -> if (!refreshing) {
                item(key = "rank-loading", span = StaggeredGridItemSpan.FullLine) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        InfiniteProgressIndicator()
                    }
                }
            }
            is ListState.Failed -> item(key = "rank-error", span = StaggeredGridItemSpan.FullLine) {
                ScreenFailureOrLogin(
                    message = state.message,
                    expired = state.sessionExpired,
                    onRetry = onRetry,
                    onOpenLogin = onOpenLogin,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }
            is ListState.Loaded -> rankResultGrid(
                feed = state.feed,
                onLoadMore = onLoadMore,
                onOpenAlbum = onOpenAlbum,
                clickPaging = clickPaging,
                onPageChange = onPageChange,
            )
        }
    }
}

/** 规范化分类页标签检索词：空白串视作无激活标签。 */
internal fun normalizeBrowseTagKeyword(keyword: String?): String? =
    keyword?.trim()?.takeIf { it.isNotEmpty() }

/** 从栈销毁重建恢复标签页时，仅在标签有效且尚无内容/未在加载/未失败时触发补拉。 */
internal fun shouldReloadRestoredTag(
    tagKeyword: String?,
    itemCount: Int,
    loading: Boolean,
    hasFailure: Boolean,
): Boolean = normalizeBrowseTagKeyword(tagKeyword) != null && itemCount == 0 && !loading && !hasFailure

internal data class WeeklyLoadOutcome(
    val feed: PagedFeed<SearchResult>,
    val failure: ScreenLoadPlan?,
)

internal fun resolveWeeklyLoad(
    currentFeed: PagedFeed<SearchResult>,
    outcome: SessionOutcome<com.jm3.core.model.SearchPage>,
): WeeklyLoadOutcome = when (outcome) {
    is SessionOutcome.Ok -> WeeklyLoadOutcome(
        feed = outcome.value.toFeed(),
        failure = null,
    )
    else -> WeeklyLoadOutcome(
        feed = currentFeed,
        failure = planLoad(
            outcome = outcome,
            hasContent = false,
            failureMessage = "每周更新加载失败",
        ),
    )
}

internal fun resolveWeeklyLoadMore(
    requested: PagedFeed<SearchResult>,
    outcome: SessionOutcome<com.jm3.core.model.SearchPage>,
): WeeklyLoadOutcome = when (outcome) {
    is SessionOutcome.Ok -> WeeklyLoadOutcome(
        feed = requested.appendedBy(outcome.value),
        failure = null,
    )
    SessionOutcome.Expired -> WeeklyLoadOutcome(
        feed = requested.failLoadMore(),
        failure = planLoad(
            outcome = outcome,
            hasContent = false,
            failureMessage = "每周更新加载失败",
        ),
    )
    is SessionOutcome.Failed -> WeeklyLoadOutcome(
        feed = requested.failLoadMore(),
        failure = null,
    )
}
