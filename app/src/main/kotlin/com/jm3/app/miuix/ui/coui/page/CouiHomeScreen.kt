// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.page

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
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
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme
import com.itos.coui.topappbar.CollapsingTopAppBar
import com.itos.coui.topappbar.rememberCouiCollapsingAppBarState
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.ui.coui.theme.CouiStrings
import com.jm3.app.miuix.ui.HomeSideData
import com.jm3.app.miuix.ui.ListState
import com.jm3.app.miuix.ui.list.ListLoadState
import com.jm3.app.miuix.ui.list.SessionListLoader
import com.jm3.app.miuix.ui.list.SessionListSnapshot
import com.jm3.app.miuix.ui.LocalWideLayout
import com.jm3.app.miuix.ui.PaginationModeReloadEffect
import com.jm3.app.miuix.ui.SessionListKey
import com.jm3.app.miuix.ui.couiHomeExtraBottomDp
import com.jm3.app.miuix.ui.coui.component.CouiBodyText
import com.jm3.app.miuix.ui.coui.component.CouiAppScaffold
import com.jm3.app.miuix.ui.coui.component.CouiEmptyGroup
import com.jm3.app.miuix.ui.coui.component.CouiFab
import com.jm3.app.miuix.ui.coui.component.CouiFeedRefreshAction
import com.jm3.app.miuix.ui.coui.component.CouiGroupTitle
import com.jm3.app.miuix.ui.coui.component.CouiHeaderCapsuleAction
import com.jm3.app.miuix.ui.coui.component.CouiLoadMoreFooter
import com.jm3.app.miuix.ui.coui.component.CouiLoadingGroup
import com.jm3.app.miuix.ui.coui.component.CouiMinTouchTarget
import com.jm3.app.miuix.ui.coui.component.CouiPagingGroup
import com.jm3.app.miuix.ui.coui.component.CouiScreenFailureGroup
import com.jm3.app.miuix.ui.coui.component.CouiSegmentedCapsules
import com.jm3.app.miuix.ui.coui.component.CouiTagCapsulePill
import com.jm3.app.miuix.ui.coui.component.CouiWorkCardTokens
import com.jm3.app.miuix.ui.coui.component.CouiWorkGridCard
import com.jm3.app.miuix.ui.coui.component.CouiWorkRankBadge
import com.jm3.app.miuix.ui.coui.component.CouiWorkRowCard
import com.jm3.app.miuix.ui.coui.component.LocalCouiFallbackContentColor
import com.jm3.app.miuix.ui.coui.component.couiDialogMaterial
import com.jm3.app.miuix.ui.feedRowItems
import com.jm3.app.miuix.ui.coui.component.couiPressable
import com.jm3.app.miuix.ui.coui.icon.CouiIcons
import com.jm3.app.miuix.ui.mutateSearchHistory
import com.jm3.app.miuix.ui.orDegrade
import com.jm3.app.miuix.ui.readSearchHistory
import com.jm3.app.miuix.ui.readableWidth
import com.jm3.core.albumCodeOf
import com.jm3.core.model.BrowseCategory
import com.jm3.core.model.BrowseFilter
import com.jm3.core.model.BrowseOrder
import com.jm3.core.model.BrowseTime
import com.jm3.core.model.SearchPage
import com.jm3.core.model.SearchResult
import com.jm3.core.model.SearchScope
import com.jm3.core.paging.PageSlice
import com.jm3.core.paging.PagedFeed
import com.jm3.core.session.SessionOutcome
import com.jm3.core.settings.PaginationMode
import com.jm3.core.toSlice
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon

private const val SERIAL_CAROUSEL_LIMIT = 12
private const val HOT_TAG_COLLAPSED = 8

/** 单条横滑轨道最多铺多少张卡：再多也只是多滑几下，收益低于首屏的组装成本。 */
private const val TRACK_CARD_LIMIT = 12

private const val HOME_LOAD_MORE_FAILED = "更多内容没有加载成功"

private const val SEARCH_LOAD_MORE_FAILED = "更多结果没有加载成功"

/** 排行榜入口轨道的标题：口径与榜单页默认筛选一致（全部书籍、本周、最多喜欢）。 */
private const val RANK_TRACK_TITLE = "排行榜 · 本周最多喜欢"

private val SortLabels: List<String> = BrowseOrder.entries.map { it.label }

/**
 * 首页横滑轨道：一条轨道 = 一个标题 + 一行竖卡。
 *
 * 轨道数据都来自真实接口（连载更新 / 随机推荐 / 榜单），空数据时整条轨道不出，
 * 不用占位卡凑数。
 */
private data class CouiHomeTrackData(
    val key: String,
    val title: String,
    val items: List<SearchResult>,
    val ranked: Boolean,
)

/**
 * COUI 首页：折叠顶栏 + 搜索栏 + 三条横滑轨道 + 分组式发现流 / 搜索结果。
 *
 * 数据编排与 HyperOS 首页同源：同一份 SessionCache 键、同一个 PagedFeed 状态机、
 * 同一套 AuthCall 结局翻译，切皮肤后列表与输入状态不会丢。
 *
 * 视觉全部交给 COUI 原厂组件：顶栏用 CollapsingTopAppBar，发现流按分组语义拆成 LazyColumn 的
 * item，轨道用 LazyRow + CouiWorkGridCard，结果行用 CouiWorkRowCard。COUI 没有下拉刷新组件，
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
    onOpenRanking: () -> Unit,
    onOpenLogin: () -> Unit = {},
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
    var hotTags by remember { mutableStateOf(sessionCache.homeSide.hotTags) }
    var serialUpdates by remember { mutableStateOf(sessionCache.homeSide.serialUpdates) }
    var randomPicks by remember { mutableStateOf(sessionCache.homeSide.randomPicks) }
    var rankPicks by remember { mutableStateOf(sessionCache.homeSide.rankPicks) }
    var searchHistory by remember { mutableStateOf<List<String>>(emptyList()) }
    var showAllTags by rememberSaveable { mutableStateOf(false) }
    var showSearchHelp by rememberSaveable { mutableStateOf(false) }

    val lazyState = rememberLazyListState()
    // 折叠顶栏只在列表还能滚的时候才收，短页保持展开
    val header = rememberCouiCollapsingAppBarState(
        canScroll = { lazyState.canScrollForward || lazyState.canScrollBackward },
    )
    val searchState = rememberCouiSearchBarState(expanded = searchExpanded)

    val order = BrowseOrder.entries.firstOrNull { it.apiValue == orderValue } ?: BrowseOrder.LATEST
    val mode = deps.paging.paginationMode
    val clickPaging = mode == PaginationMode.CLICK

    // 加载编排交给共享的 SessionListLoader：首屏 / 下拉刷新 / 翻页 / 跳页 / 失败分派只有一份实现，
    // 本页只留渲染用的 ListState 与「换页后归顶」这种纯视觉决策。
    val feedLoader = remember {
        SessionListLoader(
            scope = scope,
            idOf = { item: SearchResult -> item.id },
            failureMessage = CouiStrings.LoadFailed,
            fetchPage = { page ->
                authCall.call { gateway.latest(page) }.toCouiPageSlice()
            },
            notice = {},
            cache = sessionCache,
            cacheKey = SessionListKey.HomeFeed,
        )
    }
    // 搜索结果按「查询 + 是否标签 + 排序」重建 loader；HomeSearch 键没有查询维度，
    // 种缓存会让新查询先闪一眼旧结果，故这里只吃内存态，不接缓存。
    val searchLoader = remember(activeQuery, searchByTag, order) {
        SessionListLoader(
            scope = scope,
            idOf = { item: SearchResult -> item.id },
            failureMessage = "网络错误",
            fetchPage = { page ->
                authCall.call {
                    gateway.search(
                        activeQuery,
                        page,
                        order,
                        if (searchByTag) SearchScope.TAG else SearchScope.SITE,
                    )
                }.toCouiPageSlice()
            },
            notice = {},
        )
    }

    val feedSnapshot by feedLoader.snapshot.collectAsState()
    val searchSnapshotAll by searchLoader.snapshot.collectAsState()
    // 查询为空即首页态：clearSearch 之后结果立即让位，不需要额外的「有没有搜过」标记
    val searching = activeQuery.isNotEmpty()
    val searchSnapshot = searchSnapshotAll.takeIf { searching }
    val feed = feedSnapshot.toListState()
    val results = searchSnapshot?.toListState()
    val refreshing = if (searching) searchSnapshotAll.refreshing else feedSnapshot.refreshing

    // 换页后回到顶部：loader 不替页面滚列表，归顶时机仍由本页决定（整页替换落定那一刻）
    var pendingScrollTop by remember { mutableStateOf(false) }

    fun rememberSide(tags: List<String>, updates: List<SearchResult>, picks: List<SearchResult>) {
        hotTags = tags
        serialUpdates = updates
        randomPicks = picks
        sessionCache.homeSide = HomeSideData(tags, updates, picks, rankPicks)
    }

    fun mutateHistory(block: suspend () -> Unit) {
        scope.launch {
            searchHistory = mutateSearchHistory(deps.settings, { searchHistory }) { block() }
        }
    }

    fun runSearch(text: String, byTag: Boolean, sort: BrowseOrder) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        // 三个 remember 键一变，searchLoader 就重建并自动拉首屏；重试同一查询要走 loader 的首屏入口
        activeQuery = trimmed
        searchByTag = byTag
        orderValue = sort.apiValue
        mutateHistory { deps.settings.addSearchHistory(trimmed) }
    }

    fun clearSearch() {
        query = ""
        activeQuery = ""
        searchByTag = false
        // 首页流被搜索覆盖期间可能已经换代：回来时若手里没有可用结果，就补一次首屏
        if (feedLoader.state !is ListLoadState.Loaded) feedLoader.loadFirstPage()
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
        // 跳详情就退出搜索输入态：否则返回首页时顶栏仍是吸顶的搜索形态，
        // 库侧还会因为 expanded 仍是 true 重新抢焦点、把键盘拉起来
        searchExpanded = false
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
        if (searching) searchLoader.refresh() else feedLoader.refresh()
    }

    fun activeFeed(): PagedFeed<SearchResult>? =
        ((if (searching) results else feed) as? ListState.Loaded)?.feed

    fun loadMore() {
        if (searching) searchLoader.loadMore() else feedLoader.loadMore()
    }

    fun jumpToPage(target: Int) {
        val current = activeFeed() ?: return
        if (!couiCanJumpFeedPage(current, target)) return
        pendingScrollTop = true
        if (searching) searchLoader.goToPage(target) else feedLoader.goToPage(target)
    }

    fun openRandom() {
        val candidates = randomPicks.ifEmpty { activeFeed()?.items.orEmpty() }
        candidates.randomOrNull()?.let { openAlbum(it.id) }
    }

    BackHandler(enabled = searchExpanded || searching) { collapseSearch() }

    // 命中缓存时种子已是 Loaded：切 tab 回来首帧直接有列表，不退回 Loading 冲掉
    LaunchedEffect(feedLoader) {
        if (feedLoader.state !is ListLoadState.Loaded) feedLoader.loadFirstPage()
    }
    LaunchedEffect(searchLoader) {
        if (activeQuery.isNotEmpty()) searchLoader.loadFirstPage()
    }
    LaunchedEffect(searchSnapshot?.state, searchSnapshot?.feed?.page, feedSnapshot.state, feedSnapshot.feed.page) {
        if (!pendingScrollTop) return@LaunchedEffect
        val target = searchSnapshot ?: feedSnapshot
        if (target.state !is ListLoadState.Loaded) return@LaunchedEffect
        pendingScrollTop = false
        lazyState.scrollToItem(0)
    }
    LaunchedEffect(gateway, authCall) {
        if (sessionCache.homeSide.isEmpty) {
            // 四份侧栏数据互不依赖，并发取；任何一份失败只退化成空，不拖住首屏发现流
            coroutineScope {
                val tags = async { authCall.call { gateway.hotTags() }.orDegrade(emptyList()) }
                val picks = async { authCall.call { gateway.randomRecommend() }.orDegrade(emptyList()) }
                val updates = async {
                    authCall
                        .call { gateway.serialization(1) }
                        .orDegrade<SearchPage?>(null)
                        ?.items
                        ?.take(SERIAL_CAROUSEL_LIMIT)
                        .orEmpty()
                }
                val history = async { readSearchHistory(deps.settings) { emptyList() } }
                rememberSide(tags = tags.await(), updates = updates.await(), picks = picks.await())
                searchHistory = history.await()
            }
        } else {
            searchHistory = readSearchHistory(deps.settings) { searchHistory }
        }
    }

    // 榜单轨道单独一次请求：失败就整条轨道不出现（入口是增强项，不能拖住首屏发现流）
    LaunchedEffect(gateway, authCall) {
        // 命中缓存就不再请求：切 tab 回来首帧直接有轨道
        if (sessionCache.homeSide.rankPicks.isNotEmpty()) return@LaunchedEffect
        val rank = authCall
            .call {
                gateway.browse(
                    BrowseFilter(
                        category = BrowseCategory.ALL,
                        order = BrowseOrder.MOST_LIKED,
                        time = BrowseTime.WEEK,
                    ),
                    1,
                )
            }
            .orDegrade<SearchPage?>(null)
        rankPicks = rank?.items?.take(TRACK_CARD_LIMIT).orEmpty()
        if (rankPicks.isNotEmpty()) sessionCache.homeSide = sessionCache.homeSide.copy(rankPicks = rankPicks)
    }
    PaginationModeReloadEffect(mode) {
        if (searching) searchLoader.loadFirstPage() else feedLoader.loadFirstPage()
    }
    // 轨道条数随数据变化，结果行的起点不能写死：头部一块 + 若干条轨道 + 分组标题
    val tracks: List<CouiHomeTrackData> = if (searching) {
        emptyList()
    } else {
        buildList {
            if (serialUpdates.isNotEmpty()) {
                add(CouiHomeTrackData("serial", "连更追踪", serialUpdates, ranked = false))
            }
            if (randomPicks.isNotEmpty()) {
                add(CouiHomeTrackData("random", "随机推荐", randomPicks, ranked = false))
            }
            if (rankPicks.isNotEmpty()) {
                add(CouiHomeTrackData("rank", RANK_TRACK_TITLE, rankPicks, ranked = true))
            }
        }
    }
    val feedRowStart = 1 + tracks.size + 1
    // 行卡列数只读 policy 的派生值：手机断点为 1，内容宽够 840dp 才排两列
    val feedColumns = LocalWideLayout.current.feedColumns

    // 虚拟化后没有单一滚动位置可读，改看可见项：采集在 CouiFeedPrefetchEffect，
    // 本页只交 feed 与结果行起点；请求幂等仍由 loader 的 canLoadMore 保证。
    CouiFeedPrefetchEffect(
        feed = activeFeed(),
        rowStart = feedRowStart,
        listState = lazyState,
        columns = feedColumns,
        enabled = !clickPaging,
        onPrefetch = ::loadMore,
    )

    CouiAppScaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(header.nestedScrollConnection),
        topBar = {
            CollapsingTopAppBar(
                state = header,
                searchState = searchState,
                title = "JMComic",
                // 顶栏自绘底默认透明 + 叠 40% 黑渐变，用皮肤页底色替代，避免滚动时透出正文
                backgroundColor = CouiTheme.colorScheme.background,
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
            val fallback = LocalCouiFallbackContentColor.current
            val iconTint = if (fallback == Color.Unspecified) CouiTheme.colorScheme.onAccent else fallback
            val showBackToTop by remember { derivedStateOf { lazyState.firstVisibleItemIndex > 2 } }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.End,
            ) {
                if (showBackToTop) {
                    CouiFab(
                        icon = CouiIcons.Back,
                        contentDescription = "回到顶部",
                        rotationDegrees = 90f,
                        onClick = { scope.launch { lazyState.animateScrollToItem(0) } },
                    )
                }
                CouiFab(
                    onClick = { openRandom() },
                ) {
                    Icon(
                        imageVector = CouiIcons.Shuffle,
                        contentDescription = "随机看一部",
                        modifier = Modifier.size(24.dp),
                        tint = iconTint,
                    )
                }
            }
        },
    ) { innerPadding ->
        // 顶栏留白改由 LazyColumn 的 contentPadding 承担，正文才能滚到折叠后的顶栏下面。
        // 折叠位移留到测量期读取：组合期算成字面量会把首次组合那一帧的顶栏高度冻结住，
        // 之后顶栏展开/收缩正文都不再跟随（切 tab 回首页时标签行被大标题与搜索框压住）。
        // 底栏净空与 FAB 位置由 CouiAppScaffold 的 bottomBar 占位统一让出。
        val contentPadding = remember(header, innerPadding, density) {
            val base = header.contentPadding(innerPadding, density)
            object : PaddingValues {
                override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp =
                    base.calculateLeftPadding(layoutDirection)

                override fun calculateTopPadding(): Dp = base.calculateTopPadding()

                override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp =
                    base.calculateRightPadding(layoutDirection)

                // 竖排两枚 FAB 固定占位（16 + 56 + 12 + 56 + 16 呼吸净空），末项必须滚到它们上方；
                // 不按 showBackToTop 拆分：回到顶部按钮显隐会让 contentPadding 在滚动中跳动
                override fun calculateBottomPadding(): Dp =
                    base.calculateBottomPadding() + couiHomeExtraBottomDp().dp
            }
        }
        val feedState = if (searching) results else feed
        // 大屏按 LocalWideLayout 的断点收窄中栏：readableWidth() 读同一份 readableHorizontalPadding，
        // 页面不再自写宽度常量；窄屏该内衬为 0，行为与旧页一致
        LazyColumn(
            state = lazyState,
            modifier = Modifier.fillMaxSize().readableWidth().consumeWindowInsets(innerPadding),
            contentPadding = contentPadding,
            overscrollEffect = rememberCouiOverscrollEffect(),
        ) {
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
            // 三条横滑轨道排在发现流之前：先给「今天有什么可看」，再给完整清单
            tracks.forEach { track ->
                item(key = "coui-home-track-${track.key}") {
                    CouiHomeTrackSection(
                        track = track,
                        onOpenAlbum = ::openAlbum,
                        // 进榜入口只挂在排名轨道：其余轨道不摆一个没有去处的按钮
                        onOpenRanking = if (track.ranked) onOpenRanking else null,
                    )
                }
            }
            item(key = "coui-home-title") {
                CouiGroupTitle(
                    title = if (searching) "搜索结果" else "最近更新",
                    actions = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (!searching) {
                                CouiHeaderCapsuleAction(text = "每周必看", onClick = onOpenWeek)
                            }
                            CouiHeaderCapsuleAction(
                                text = "帮助",
                                tinted = false,
                                onClick = { showSearchHelp = true },
                            )
                            CouiFeedRefreshAction(refreshing = refreshing, onRefresh = { refresh() })
                        }
                    },
                )
            }
            when (val current = feedState) {
                null, is ListState.Loading -> item(key = "coui-home-loading") {
                    CouiLoadingGroup(
                        text = if (searching) "正在搜索…" else "正在加载最新上架…",
                    )
                }

                is ListState.Failed -> item(key = "coui-home-error") {
                    val failureMessage = current.message.ifBlank { if (searching) "搜索失败" else CouiStrings.LoadFailed }
                    CouiScreenFailureGroup(
                        message = failureMessage,
                        expired = current.sessionExpired,
                        onRetry = {
                            if (searching) searchLoader.loadFirstPage() else feedLoader.loadFirstPage()
                        },
                        onOpenLogin = onOpenLogin,
                    )
                }

                is ListState.Loaded -> {
                    val loadedFeed = current.feed
                    val items = loadedFeed.items
                    if (items.isEmpty()) {
                        item(key = "coui-home-empty") {
                            CouiEmptyGroup(
                                text = if (searching) CouiStrings.EmptySearch else CouiStrings.EmptyContent,
                            )
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
                        // key 用作品 id：首行贴齐标题下沿（top=0）与其它分段保持 8dp 标题间距，末行归零由下个分段自己留白
                        // 大屏把发现流行卡排成两列，手机断点仍是单列；key 与改动前一致，滚动位置不丢
                        feedRowItems(
                            count = items.size,
                            columns = feedColumns,
                            keyPrefix = "coui-home-row-",
                            keyOf = { index -> items[index].id },
                        ) { index ->
                            val album = items[index]
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        start = CouiPageDefaults.horizontalMargin,
                                        end = CouiPageDefaults.horizontalMargin,
                                        top = if (index == 0) 0.dp else CouiWorkCardTokens.RowSpacing,
                                    ),
                            ) {
                                CouiWorkFeedRow(album = album, onClick = { openAlbum(album.id) })
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
            // 搜索历史与更多入口只在首页态出现，搜索结果下保持与旧页一致的清爽
            if (!searching) {
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
                                            kind = CouiTextKind.ItemTitle,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    summary = {
                                        Text(text = "点按重新搜索", kind = CouiTextKind.ItemSummary)
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
                        title = { Text(text = "每周必看", kind = CouiTextKind.ItemTitle) },
                        summary = { Text(text = "按周汇总的连载更新", kind = CouiTextKind.ItemSummary) },
                        onClick = {
                            dismissSearchInput()
                            onOpenWeek()
                        },
                    )
                    Divider()
                    Item(
                        icon = { CouiAccentIcon(CouiIcons.Info) },
                        title = { Text(text = "搜索语法", kind = CouiTextKind.ItemTitle) },
                        summary = { Text(text = "空格 / + / - 组合关键词", kind = CouiTextKind.ItemSummary) },
                        onClick = { showSearchHelp = true },
                    )
                }
            }
        }
    }

    Dialog(
        onDismissRequest = { showSearchHelp = false },
        material = couiDialogMaterial(),
        // visible 非空时退场窗口由库持有；外层 if 会在关闭瞬间把弹窗抽走，动画来不及播
        visible = showSearchHelp,
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

/**
 * 一条横滑轨道：标题 + 一行竖卡。
 *
 * 卡片放在无界横滑容器里，宽度必须由轨道自己固定成 112dp（CouiWorkGridCard 已改为跟随栅格单元）；
 * 名次徽章只画在封面左上角（金银铜前三名由 CouiWorkRankBadge 判定），首页不重复报数。
 */
@Composable
private fun CouiHomeTrackSection(
    track: CouiHomeTrackData,
    onOpenAlbum: (String) -> Unit,
    onOpenRanking: (() -> Unit)? = null,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        CouiGroupTitle(title = track.title) {
            if (onOpenRanking != null) {
                CouiHeaderCapsuleAction(text = "查看全部", onClick = onOpenRanking)
            }
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            // 首尾留白取「分组标题左缘 - 卡内内衬」：卡片左缘落在页面边距上，卡内文字与 32dp 的标题同一条竖线
            contentPadding = PaddingValues(horizontal = CouiWorkCardTokens.TrackContentPadding),
            horizontalArrangement = Arrangement.spacedBy(CouiWorkCardTokens.GridSpacing),
        ) {
            itemsIndexed(
                items = track.items,
                key = { _, album -> "coui-home-track-${track.key}-${album.id}" },
            ) { index, album ->
                val badge: (@Composable BoxScope.() -> Unit)? = if (track.ranked) {
                    {
                        CouiWorkRankBadge(
                            rank = index + 1,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(CouiWorkCardTokens.BadgeInset),
                        )
                    }
                } else {
                    null
                }
                CouiWorkGridCard(
                    title = album.title,
                    coverUrl = album.coverUrl,
                    onClick = { onOpenAlbum(album.id) },
                    albumId = album.id,
                    subtitle = album.authors.firstOrNull(),
                    category = album.category,
                    badge = badge,
                    modifier = Modifier.width(CouiWorkCardTokens.GridWidth),
                )
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
        modifier = Modifier
            .padding(horizontal = CouiPageDefaults.horizontalMargin)
            .padding(top = CouiPageDefaults.categoryTopSpacing),
    )
    Text(
        text = summary,
        kind = CouiTextKind.GroupFooter,
        color = CouiTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
            .padding(top = 8.dp),
    )
}

/** 热门搜索推荐词卡片：标题栏右侧放置统一胶囊操作，热词胶囊带细微轮廓与深浅色对比层。 */
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
    Group(
        title = "热门搜索",
        headerActions = {
            if (tags.size > HOT_TAG_COLLAPSED) {
                CouiHeaderCapsuleAction(
                    text = if (expanded) "收起" else "展开",
                    trailingIcon = if (expanded) CouiIcons.ChevronUp else CouiIcons.ChevronDown,
                    onClick = onToggleExpanded,
                )
            }
        },
    ) {
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CouiPageDefaults.rowPadding, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            shown.forEach { tag ->
                CouiTagCapsulePill(text = tag, onClick = { onPick(tag) })
            }
        }
    }
}

/**
 * 作品行：发现流与搜索结果共用同一张 CouiWorkRowCard。
 *
 * 作者进次级行、分类用强调色、标签另起一行，三级信息都在卡内，不再自绘 60×80 缩略图；
 * 行卡自带按压弹簧、触点点亮与 hero 共享元素，调用方只交数据与点击。
 */
@Composable
private fun CouiWorkFeedRow(album: SearchResult, onClick: () -> Unit) {
    val tags = album.tags.take(3).joinToString(" · ")
    CouiWorkRowCard(
        coverUrl = album.coverUrl,
        title = album.title,
        onClick = onClick,
        albumId = album.id,
        subtitle = album.authors.joinToString(" / ").ifBlank { null },
        category = album.category,
        content = {
            if (tags.isNotEmpty()) {
                Text(
                    text = tags,
                    kind = CouiTextKind.GroupFooter,
                    color = CouiTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
    )
}

@Composable
private fun CouiHeaderTextAction(text: String, onClick: () -> Unit) {
    CouiHeaderCapsuleAction(text = text, onClick = onClick)
}

/** 行尾的图标动作：32dp 视觉里放 16dp 图标，触区由 couiPressable 补到 48dp，避免误触整行。 */
@Composable
private fun CouiRowIconAction(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .couiPressable(shape = CircleShape, minTouchTarget = CouiMinTouchTarget, onClick = onClick)
            .size(32.dp)
            .clip(CircleShape),
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
        { Text(text = summaryText, kind = CouiTextKind.ItemSummary) }
    }
    Item(
        title = { Text(text = title, kind = CouiTextKind.ItemTitle) },
        summary = summarySlot,
        onClick = onClick,
    )
}

@Composable
private fun CouiSyntaxRow(expression: String, meaning: String) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = expression, kind = CouiTextKind.ItemTitle)
        Text(
            text = meaning,
            kind = CouiTextKind.ItemSummary,
            color = CouiTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 搜索摘要：标签搜索要标明是标签，结果总数只在服务端给出时显示。 */
private fun searchSummary(query: String, byTag: Boolean, total: Int?): String {
    val head = if (byTag) "标签：$query" else "“$query”"
    return if (total != null && total > 0) "$head · 共 $total 个结果" else head
}

/** 渲染层仍按 [ListState] 分三态：把 loader 的快照映射回去，渲染代码不用改。 */
private fun SessionListSnapshot<SearchResult>.toListState(): ListState = when (val current = state) {
    is ListLoadState.Loading -> ListState.Loading
    is ListLoadState.Loaded -> ListState.Loaded(feed)
    is ListLoadState.Failed -> ListState.Failed(current.message, current.sessionExpired)
}

/** 网关分页结果 → 共享分页切片：页码必须透传，否则续页会取错页。 */
private fun SessionOutcome<SearchPage>.toCouiPageSlice(): SessionOutcome<PageSlice<SearchResult>> = when (this) {
    is SessionOutcome.Ok -> SessionOutcome.Ok(value.toSlice())
    is SessionOutcome.Expired -> SessionOutcome.Expired
    is SessionOutcome.Failed -> this
}
