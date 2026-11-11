// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.page

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.itos.coui.effect.SpotlightPreset
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.layout.Divider
import com.itos.coui.layout.Footer
import com.itos.coui.layout.Group
import com.itos.coui.layout.Item
import com.itos.coui.scroll.rememberCouiOverscrollEffect
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme
import com.itos.coui.topappbar.CouiTopAppBarDefaults
import com.itos.coui.topappbar.LargeTopAppBar
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.ui.BrowseMode
import com.jm3.app.miuix.ui.BrowsePageKey
import com.jm3.app.miuix.ui.ListState
import com.jm3.app.miuix.ui.PaginationModeReloadEffect
import com.jm3.app.miuix.ui.SessionListKey
import com.jm3.app.miuix.ui.asListState
import com.jm3.app.miuix.ui.coui.component.CouiAppScaffold
import com.jm3.app.miuix.ui.coui.component.CouiEmptyGroup
import com.jm3.app.miuix.ui.coui.component.CouiErrorGroup
import com.jm3.app.miuix.ui.coui.component.CouiFab
import com.jm3.app.miuix.ui.coui.component.CouiGroupTitle
import com.jm3.app.miuix.ui.coui.component.CouiWorkCardTokens
import com.jm3.app.miuix.ui.coui.component.CouiLoadMoreFooter
import com.jm3.app.miuix.ui.coui.component.CouiLoadingGroup
import com.jm3.app.miuix.ui.coui.component.CouiPagingGroup
import com.jm3.app.miuix.ui.coui.component.CouiSelectedContainerAlpha
import com.jm3.app.miuix.ui.coui.component.CouiSegmentedCapsules
import com.jm3.app.miuix.ui.coui.component.CouiTopBarBackButton
import com.jm3.app.miuix.ui.coui.component.CouiTopBarTitle
import com.jm3.app.miuix.ui.coui.component.CouiWorkRankBadge
import com.jm3.app.miuix.ui.coui.component.CouiWorkRowCard
import com.jm3.app.miuix.ui.coui.component.couiScreenContentPadding
import com.jm3.app.miuix.ui.coui.component.couiTopAppBarColors
import com.jm3.app.miuix.ui.coui.component.couiPressable
import com.jm3.app.miuix.ui.coui.icon.CouiIcons
import com.jm3.app.miuix.ui.orDegrade
import com.jm3.app.miuix.ui.planLoad
import com.jm3.app.miuix.ui.readableWidth
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
import kotlinx.coroutines.launch

/** 标签检索视图里结果行的起始下标：0 是「标签：xxx」标题，其后才是行。 */
private const val TAG_FEED_ROW_START = 1

/** 主列表与标签列表共用的续页失败提示，文案与 HyperOS 版浏览页一致。 */
private const val BROWSE_LOAD_MORE_FAILED = "没拿到下一页，可点击重试"

/** 标签视图的续页失败提示。 */
private const val TAG_LOAD_MORE_FAILED = "标签结果没拿到下一页，可点击重试"

/** 每周更新区块的续页失败提示。 */
private const val WEEKLY_LOAD_MORE_FAILED = "每周更新没拿到下一页，可点击重试"

/** 标签导航里热门标签的最大条数：与 HyperOS 版浏览页一致，避免首屏被标签墙占满。 */
private const val HOT_TAG_LIMIT = 12

/**
 * COUI 分类 / 榜单页。
 *
 * 视觉只由 COUI-Kit 原厂组件构成：Scaffold 栏位、可折叠顶栏、Group/Item 分组行、
 * 胶囊分段与字阶，不再包任何 Miuix 组件；数据接线与旧 BrowseScreen 逐条对齐
 * （同一份 SessionCache 键、同一套筛选口径、同一套点击分页 / 触底分页语义），
 * 这样切页签回来仍然取回上次那份结果。
 *
 * 正文是 LazyColumn：结果行、每周连载更新与更多分类都可能很长，整列一次性组合会拖慢首帧。
 * 代价是内容不足一屏时没有原厂的强回弹（回弹要求容器自身还能滚动），
 * 列表够长时行为与整页滚动一致，滚动位置仍由 LazyListState 记住。
 *
 * 「每周连载更新」取 gateway.serialization(1)（与首页同一接口），
 * 「更多分类」取 gateway.categoriesList() 的标签区块；两者与筛选无关，进页各加载一次。
 * 点标签进入本页的标签检索视图，不写 SessionCache：标签组合无限多，缓存只会污染缓存表。
 */
@Composable
internal fun CouiBrowseScreen(
    deps: ScreenDeps,
    mode: BrowseMode,
    onOpenAlbum: (String) -> Unit,
) {
    val sessionCache = deps.sessionCache
    val gateway = deps.gateway
    val authCall = deps.authCall
    val scope = rememberCoroutineScope()
    val lazyState = rememberLazyListState()
    val colors = CouiTheme.colorScheme

    var categoryValue by rememberSaveable { mutableStateOf(BrowseCategory.ALL.apiValue) }
    var orderValue by rememberSaveable { mutableStateOf(BrowseOrder.LATEST.apiValue) }
    var timeValue by rememberSaveable { mutableStateOf(BrowseTime.ALL.apiValue) }

    val category = BrowseCategory.entries.firstOrNull { it.apiValue == categoryValue } ?: BrowseCategory.ALL
    val order = BrowseOrder.entries.firstOrNull { it.apiValue == orderValue } ?: BrowseOrder.LATEST
    val time = BrowseTime.entries.firstOrNull { it.apiValue == timeValue } ?: BrowseTime.ALL

    // 缓存键带上筛选口径：不同口径各自缓存，切回来才不会先闪一份别人的列表
    val pageKey = BrowsePageKey(mode, categoryValue, orderValue, timeValue)
    val listKey = SessionListKey.Browse(pageKey)

    // SessionCache 是普通 map，不是快照状态，页面自己持有一份并双写
    var state by remember(pageKey) { mutableStateOf(sessionCache.stateOf(listKey)) }
    // 快速切筛选时，旧响应不能覆盖新条件的结果
    var requestToken by remember { mutableIntStateOf(0) }
    // 单页条数：只有首页能确定，用于榜单名次跨页续号
    var pageStride by remember(pageKey) { mutableIntStateOf(0) }

    val pageMode = deps.preferences.paginationMode
    val clickPaging = pageMode == PaginationMode.CLICK

    // 每周连载更新：首页一页 + 用户点「加载更多」追加；失败只影响这一块，不动主列表
    var weeklyFeed by remember { mutableStateOf(PagedFeed<SearchResult>()) }
    var weeklyLoading by remember { mutableStateOf(true) }
    var weeklyFailure by remember { mutableStateOf<String?>(null) }
    var weeklyToken by remember { mutableIntStateOf(0) }
    // 分类树只用来渲染标签区块；拿不到就整块降级成一行提示
    var categoryTree by remember { mutableStateOf<CategoryTree?>(null) }
    var categoriesExpanded by rememberSaveable { mutableStateOf(false) }
    // 热门标签：与子分类一起构成结果列表之前的标签导航；拿不到就整块隐藏
    var hotTags by remember { mutableStateOf(emptyList<String>()) }

    // 标签检索是本页的临时视图：返回分类浏览即丢弃，所以不参与状态恢复
    var tagKeyword by remember { mutableStateOf<String?>(null) }
    var tagFeed by remember { mutableStateOf(PagedFeed<SearchResult>()) }
    var tagLoading by remember { mutableStateOf(false) }
    var tagFailure by remember { mutableStateOf<String?>(null) }
    var tagToken by remember { mutableIntStateOf(0) }

    // 分类只认分类 + 排序，榜单只认时间 + 排序：两套口径与旧页一致
    fun filter(): BrowseFilter = when (mode) {
        BrowseMode.CATEGORY -> BrowseFilter(category = category, order = order, time = BrowseTime.ALL)
        BrowseMode.RANK -> BrowseFilter(category = BrowseCategory.ALL, order = order, time = time)
    }

    /** 列表状态与缓存一起改：只改 state 的话，切走再回来缓存还是上一份。 */
    fun publish(key: BrowsePageKey, next: ListState) {
        state = next
        if (next is ListState.Loaded && next.feed.page == 1 && next.feed.items.isNotEmpty()) {
            pageStride = next.feed.items.size
        }
        sessionCache.remember(SessionListKey.Browse(key), next)
    }

    fun refresh() {
        requestToken++
        val token = requestToken
        val key = pageKey
        publish(key, ListState.Loading)
        scope.launch {
            val next = authCall.call { gateway.browse(filter(), 1) }.asListState("加载失败")
            if (token != requestToken) return@launch
            publish(key, next)
        }
    }

    /** 点击分页：整页替换，沿用同一筛选口径与首屏缓存。 */
    fun jumpToPage(target: Int) {
        val current = (state as? ListState.Loaded)?.feed ?: return
        val resolved = pagerTargetPage(current.page, target, current.pageCount)
        if (resolved == current.page) return
        requestToken++
        val token = requestToken
        val key = pageKey
        publish(key, ListState.Loading)
        scope.launch {
            val next = authCall.call { gateway.browse(filter(), resolved) }.asListState("加载失败")
            if (token != requestToken) return@launch
            publish(key, next)
            // 换页后回到顶部，否则新一页会停在上一页的滚动位置
            lazyState.animateScrollToItem(0)
        }
    }

    fun loadMore() {
        val current = (state as? ListState.Loaded)?.feed ?: return
        val requested = current.beginLoadMore()
        if (requested === current) return
        val token = requestToken
        val key = pageKey
        // 加载中标记不进缓存：它只是瞬态，缓存只留可复用的整页结果
        state = ListState.Loaded(requested)
        scope.launch {
            val next = authCall.call { gateway.browse(filter(), requested.page + 1) }
            // 加载期间切了筛选：这批结果属于旧条件，直接丢弃
            if (token != requestToken) return@launch
            val updated = when (next) {
                is SessionOutcome.Ok -> requested.appendedBy(next.value)
                else -> requested.failLoadMore()
            }
            publish(key, ListState.Loaded(updated))
        }
    }

    fun loadWeekly() {
        weeklyToken++
        val token = weeklyToken
        weeklyLoading = true
        weeklyFailure = null
        scope.launch {
            val outcome = authCall.call { gateway.serialization(1) }
            if (token != weeklyToken) return@launch
            weeklyLoading = false
            when (outcome) {
                is SessionOutcome.Ok -> weeklyFeed = outcome.value.toFeed()
                else -> weeklyFailure =
                    planLoad(outcome, hasContent = false, failureMessage = "每周更新加载失败").message
            }
        }
    }

    fun loadMoreWeekly() {
        val current = weeklyFeed
        val requested = current.beginLoadMore()
        if (requested === current) return
        val token = weeklyToken
        weeklyFeed = requested
        scope.launch {
            val outcome = authCall.call { gateway.serialization(requested.page + 1) }
            if (token != weeklyToken) return@launch
            weeklyFeed = when (outcome) {
                is SessionOutcome.Ok -> requested.appendedBy(outcome.value)
                else -> requested.failLoadMore()
            }
        }
    }

    fun loadTag(keyword: String) {
        tagToken++
        val token = tagToken
        tagLoading = true
        tagFailure = null
        tagFeed = PagedFeed()
        scope.launch {
            val outcome = authCall.call {
                gateway.search(text = keyword, page = 1, order = order, scope = SearchScope.TAG)
            }
            if (token != tagToken) return@launch
            tagLoading = false
            when (outcome) {
                is SessionOutcome.Ok -> tagFeed = outcome.value.toFeed()
                else -> tagFailure =
                    planLoad(outcome, hasContent = false, failureMessage = "标签加载失败").message
            }
        }
    }

    fun loadMoreTag() {
        val keyword = tagKeyword ?: return
        val current = tagFeed
        val requested = current.beginLoadMore()
        if (requested === current) return
        // 切换标签会推进 token：在途的旧标签下一页到达后不能再写进新列表
        val token = tagToken
        tagFeed = requested
        scope.launch {
            val outcome = authCall.call {
                gateway.search(
                    text = keyword,
                    page = requested.page + 1,
                    order = order,
                    scope = SearchScope.TAG,
                )
            }
            if (token != tagToken) return@launch
            tagFeed = when (outcome) {
                is SessionOutcome.Ok -> requested.appendedBy(outcome.value)
                else -> requested.failLoadMore()
            }
        }
    }

    fun jumpToTagPage(target: Int) {
        val keyword = tagKeyword ?: return
        val current = tagFeed
        val resolved = pagerTargetPage(current.page, target, current.pageCount)
        if (resolved == current.page) return
        tagToken++
        val token = tagToken
        tagLoading = true
        scope.launch {
            val outcome = authCall.call {
                gateway.search(text = keyword, page = resolved, order = order, scope = SearchScope.TAG)
            }
            if (token != tagToken) return@launch
            tagLoading = false
            when (outcome) {
                is SessionOutcome.Ok -> {
                    tagFeed = outcome.value.toFeed()
                    lazyState.animateScrollToItem(0)
                }

                else -> tagFailure =
                    planLoad(outcome, hasContent = false, failureMessage = "标签加载失败").message
            }
        }
    }

    fun openTag(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return
        tagKeyword = trimmed
        loadTag(trimmed)
    }

    LaunchedEffect(pageKey) {
        // 同一口径的结果还在缓存里（切 pageKey 回来就是这种情况）：直接用，别退回 Loading 冲掉
        if (sessionCache.hasPage(SessionListKey.Browse(pageKey))) return@LaunchedEffect
        refresh()
    }
    PaginationModeReloadEffect(pageMode) { refresh() }
    LaunchedEffect(deps.gateway, deps.authCall) {
        loadWeekly()
        // 分类树失败只让「更多分类」退化成一行提示，不打断主列表
        categoryTree = authCall.call { gateway.categoriesList() }.orDegrade<CategoryTree?>(null)
        hotTags = authCall.call { gateway.hotTags() }.orDegrade(emptyList())
    }

    // 标签导航的数据：当前主分类的子分类 + 热门标签（热门标签只在分类模式给）
    val subCategories = categoryTree
        ?.categories
        ?.firstOrNull { it.slug == category.apiValue }
        ?.children
        .orEmpty()
    val tagNavTags = if (mode == BrowseMode.CATEGORY) hotTags.take(HOT_TAG_LIMIT) else emptyList()
    val hasTagNav = subCategories.isNotEmpty() || tagNavTags.isNotEmpty()
    // 结果行之前的条目：筛选 1 + 主分类与更多分类（分类模式 2）+ 标签导航（有才计）+ 周更与续页脚 2
    val browseHeadCount = 1 +
        (if (mode == BrowseMode.CATEGORY) 2 else 0) +
        (if (hasTagNav) 1 else 0) +
        2

    /*
     * 触底预取：把懒列表下标换算成结果行下标再判提前量。
     * 周更与更多分类已经排在结果列表之前，行起点只由这串固定条目决定，追加一页后不会漂移；
     * 采集与行高换算在 CouiFeedPrefetchEffect，本页只交当前 feed、行起点与取下一页的动作。
     */
    val browseTagView = tagKeyword != null
    CouiFeedPrefetchEffect(
        feed = if (browseTagView) tagFeed else (state as? ListState.Loaded)?.feed,
        rowStart = if (browseTagView) TAG_FEED_ROW_START else browseHeadCount,
        listState = lazyState,
        enabled = !clickPaging,
        restartKey = listKey to tagKeyword,
        onPrefetch = { if (browseTagView) loadMoreTag() else loadMore() },
    )

    val scrollBehavior = CouiTopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        canScroll = { lazyState.canScrollForward || lazyState.canScrollBackward },
    )

    BackHandler(enabled = tagKeyword != null) { tagKeyword = null }

    /*
     * 分支判据与行数据都在组合体里读一次：LazyColumn 的 item 结构由这些局部值决定，
     * 状态一变本页重组、结构随之重建。把快照读留到 item 内容里只会让单个 item 重组，
     * 结构（有几行、有没有空态）不会重建。
     */
    val feedState = state
    val keyword = tagKeyword
    val tagFailureMessage = tagFailure
    val tagIsLoading = tagLoading
    val tagItems = tagFeed.items
    val tagTotal = tagFeed.total
    val tagPage = tagFeed.page
    val tagPageCount = tagFeed.pageCount
    val tagHasMore = tagFeed.hasMore
    val tagLoadingMore = tagFeed.loadingMore
    val tagLoadMoreFailed = tagFeed.loadMoreFailed
    val weekly = weeklyFeed
    val weeklyIsLoading = weeklyLoading
    val weeklyFail = weeklyFailure

    CouiAppScaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { CouiTopBarTitle(text = if (keyword == null) mode.title else "标签：$keyword") },
                navigationIcon = {
                    if (keyword != null) {
                        CouiTopBarBackButton(onClick = { tagKeyword = null })
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = couiTopAppBarColors(),
            )
        },
        floatingActionButton = {
            CouiFab(
                icon = CouiIcons.Refresh,
                contentDescription = "刷新",
                onClick = {
                    // 标签视图里的刷新重发标签检索，不把用户弹回分类浏览
                    if (keyword == null) refresh() else loadTag(keyword)
                },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        },
    ) { innerPadding ->
        // 顶栏折叠位移留到测量期读取（含底栏净空），组合期算成字面量会把它冻结在首帧；
        // 左右内衬保留 Scaffold 自己的净空
        val contentPadding = couiScreenContentPadding(
            scrollBehavior = scrollBehavior,
            innerPadding = innerPadding,
            horizontalPadding = null,
        )
        LazyColumn(
            state = lazyState,
            // 大屏按 LocalWideLayout 断点收窄中栏：readableWidth() 读同一份 readableHorizontalPadding
            modifier = Modifier
                .fillMaxSize()
                .readableWidth()
                .consumeWindowInsets(innerPadding),
            contentPadding = contentPadding,
            overscrollEffect = rememberCouiOverscrollEffect(),
        ) {
            if (keyword != null) {
                item(key = "coui-tag-title") {
                    CouiGroupTitle(
                        title = "标签：$keyword",
                        actions = {
                            val total = tagTotal
                            if (total != null) {
                                Text(
                                    text = "共 $total 部",
                                    kind = CouiTextKind.Description,
                                    color = colors.onSurfaceVariant,
                                )
                            }
                        },
                    )
                }
                when {
                    tagIsLoading && tagItems.isEmpty() -> item(key = "coui-tag-loading") {
                        CouiLoadingGroup()
                    }

                    tagFailureMessage != null && tagItems.isEmpty() -> item(key = "coui-tag-error") {
                        CouiErrorGroup(
                            message = tagFailureMessage,
                            onRetry = { loadTag(keyword) },
                        )
                    }

                    tagItems.isEmpty() -> {
                        item(key = "coui-tag-empty") {
                            CouiEmptyGroup(text = "这个标签还没有作品")
                        }
                        // 空态的补充说明留在卡下：公共件只负责卡片正文
                        item(key = "coui-tag-empty-hint") {
                            Footer(text = "换个标签，或返回分类浏览看看")
                        }
                    }

                    else -> {
                        items(
                            items = tagItems,
                            key = { result -> "coui-tag-row-${result.id}" },
                        ) { result ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = CouiPageDefaults.horizontalMargin,
                                        vertical = CouiWorkCardTokens.RowSpacing / 2,
                                    ),
                            ) {
                                CouiBrowseResultRow(
                                    result = result,
                                    rank = null,
                                    onClick = { onOpenAlbum(result.id) },
                                )
                            }
                        }
                        item(key = "coui-tag-paging") {
                            if (clickPaging) {
                                CouiPagingGroup(
                                    page = tagPage,
                                    pageCount = tagPageCount,
                                    hasMore = tagHasMore,
                                    onJumpPage = { jumpToTagPage(it) },
                                )
                            } else {
                                CouiLoadMoreFooter(
                                    loading = tagLoadingMore,
                                    error = if (tagLoadMoreFailed) TAG_LOAD_MORE_FAILED else null,
                                    endReached = !tagHasMore,
                                    onRetry = { loadMoreTag() },
                                )
                            }
                        }
                    }
                }
            } else {
                item(key = "coui-browse-filters") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (mode == BrowseMode.CATEGORY) {
                            CouiSegmentedCapsules(
                                items = BrowseOrder.entries.map { it.label },
                                selectedIndex = BrowseOrder.entries.indexOf(order),
                                onSelect = { orderValue = BrowseOrder.entries[it].apiValue },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        } else {
                            CouiSegmentedCapsules(
                                items = BrowseTime.entries.map { it.label },
                                selectedIndex = BrowseTime.entries.indexOf(time),
                                onSelect = { timeValue = BrowseTime.entries[it].apiValue },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                            CouiSegmentedCapsules(
                                items = BrowseOrder.entries.map { it.label },
                                selectedIndex = BrowseOrder.entries.indexOf(order),
                                onSelect = { orderValue = BrowseOrder.entries[it].apiValue },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
                if (mode == BrowseMode.CATEGORY) {
                    item(key = "coui-browse-categories") {
                        // 9 个主分类塞不进 CouiSegmentedCapsules（它按 weight 等分且不横向滚动），
                        // 这里保留可滑动的分类轨道，但选中态与分段胶囊同源：同一枚 accent 14% 容器、
                        // 40dp 段高与 PreferenceValue 字阶，视觉上仍是同一套分段控件。
                        CouiCategoryRail(
                            options = BrowseCategory.entries,
                            selected = category,
                            onSelect = { categoryValue = it.apiValue },
                        )
                    }
                    // 分类树给的标签区块原来排在无限列表尾部，滚不到；上移到结果之前，默认只展开一块
                    item(key = "coui-browse-more-categories") {
                        CouiMoreCategoriesGroup(
                            tree = categoryTree,
                            expanded = categoriesExpanded,
                            onToggle = { categoriesExpanded = !categoriesExpanded },
                            onTagClick = { openTag(it) },
                        )
                    }
                }
                if (hasTagNav) {
                    item(key = "coui-browse-tag-nav") {
                        CouiTagNavGroup(
                            subCategoryNames = subCategories.map { it.name },
                            hotTags = tagNavTags,
                            onTagClick = { openTag(it) },
                        )
                    }
                }
                // 周更与它的续页脚同样排在结果之前：无限滚动不会再把它们推到滚不到的位置
                item(key = "coui-browse-weekly") {
                    CouiWeeklyGroup(
                        feed = weekly,
                        loading = weeklyIsLoading,
                        failure = weeklyFail,
                        onRetry = { loadWeekly() },
                        onOpenAlbum = onOpenAlbum,
                    )
                }
                item(key = "coui-browse-weekly-paging") {
                    if (weekly.items.isNotEmpty()) {
                        CouiLoadMoreFooter(
                            feed = weekly,
                            errorMessage = WEEKLY_LOAD_MORE_FAILED,
                            onRetry = { loadMoreWeekly() },
                        )
                    }
                }

                when (feedState) {
                    ListState.Loading -> item(key = "coui-browse-loading") {
                        CouiLoadingGroup()
                    }

                    is ListState.Failed -> item(key = "coui-browse-error") {
                        CouiErrorGroup(
                            message = feedState.message,
                            onRetry = { refresh() },
                        )
                    }

                    is ListState.Loaded -> {
                        val feed = feedState.feed
                        val resultTitle = when (mode) {
                            BrowseMode.CATEGORY ->
                                if (category == BrowseCategory.ALL) {
                                    "全部作品"
                                } else {
                                    "${category.label}作品"
                                }

                            BrowseMode.RANK -> "${time.label} · ${order.label}榜"
                        }
                        if (feed.items.isEmpty()) {
                            item(key = "coui-browse-empty") {
                                CouiEmptyGroup(text = "这里还没有作品")
                            }
                            // 空态的补充说明留在卡下：公共件只负责卡片正文
                            item(key = "coui-browse-empty-hint") {
                                Footer(text = "换一个分类或排序试试")
                            }
                        } else {
                            item(key = "coui-browse-feed-title") {
                                CouiGroupTitle(
                                    title = resultTitle,
                                    actions = {
                                        val total = feed.total
                                        if (total != null) {
                                            Text(
                                                text = "共 $total 部",
                                                kind = CouiTextKind.Description,
                                                color = colors.onSurfaceVariant,
                                            )
                                        }
                                    },
                                )
                            }
                            // 首页拿不到时退回本页条数：宁可名次在本页重新起算，也不要空着
                            val stride = if (pageStride > 0) pageStride else feed.items.size.coerceAtLeast(1)
                            itemsIndexed(
                                items = feed.items,
                                key = { index, result -> "coui-browse-row-${result.id}-$index" },
                            ) { index, result ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            horizontal = CouiPageDefaults.horizontalMargin,
                                            vertical = CouiWorkCardTokens.RowSpacing / 2,
                                        ),
                                ) {
                                    CouiBrowseResultRow(
                                        result = result,
                                        rank = if (mode == BrowseMode.RANK) {
                                            displayRank(clickPaging, feed.page, stride, index)
                                        } else {
                                            null
                                        },
                                        onClick = { onOpenAlbum(result.id) },
                                    )
                                }
                            }
                            item(key = "coui-browse-paging") {
                                if (clickPaging) {
                                    CouiPagingGroup(
                                        page = feed.page,
                                        pageCount = feed.pageCount,
                                        hasMore = feed.hasMore,
                                        onJumpPage = { jumpToPage(it) },
                                    )
                                } else {
                                    CouiLoadMoreFooter(
                                        loading = feed.loadingMore,
                                        error = if (feed.loadMoreFailed) BROWSE_LOAD_MORE_FAILED else null,
                                        endReached = !feed.hasMore,
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

/**
 * 每周连载更新：与首页同一个接口（serialization），一页一页追加。
 *
 * 标题与卡片拆开，空态/失败态直接用公共三态件，不再各写一套「标题 + 单行 Item」；
 * 失败时卡片下方仍给原因、卡内给可点的重试行（由 [CouiErrorGroup] 提供）。
 * 续页脚在卡外单独成项：[CouiLoadMoreFooter] 自带卡片，套进本 Group 会变成卡中卡。
 */
@Composable
private fun CouiWeeklyGroup(
    feed: PagedFeed<SearchResult>,
    loading: Boolean,
    failure: String?,
    onRetry: () -> Unit,
    onOpenAlbum: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        CouiGroupTitle(title = "每周连载更新")
        when {
            loading && feed.items.isEmpty() -> CouiLoadingGroup(text = "正在加载每周更新…")

            failure != null && feed.items.isEmpty() -> CouiErrorGroup(
                message = failure,
                onRetry = onRetry,
            )

            feed.items.isEmpty() -> CouiEmptyGroup(text = "暂无每周更新")

            else -> Group(footer = failure) {
                feed.items.forEachIndexed { index, result ->
                    if (index > 0) Divider()
                    CouiBrowseResultRow(
                        result = result,
                        rank = null,
                        onClick = { onOpenAlbum(result.id) },
                    )
                }
            }
        }
    }
}

/**
 * 更多分类：按分类树给的标签区块逐块列标签，点标签进入标签检索。
 *
 * 区块可能很长，默认只展开第一块，标题右侧给「全部分类 / 收起」；
 * 分类树拉不到时（离线或接口降级）退化成一行提示，不占整屏。
 */
@Composable
private fun CouiMoreCategoriesGroup(
    tree: CategoryTree?,
    expanded: Boolean,
    onToggle: () -> Unit,
    onTagClick: (String) -> Unit,
) {
    val blocks = tree?.blocks.orEmpty()
    Column(modifier = Modifier.fillMaxWidth()) {
        CouiGroupTitle(
            title = "更多分类",
            actions = {
                if (blocks.size > 1) {
                    Text(
                        text = if (expanded) "收起" else "全部分类",
                        kind = CouiTextKind.Description,
                        color = CouiTheme.colorScheme.accent,
                        modifier = Modifier
                            .clip(CouiCapsuleShape)
                            .couiPressable(shape = CouiCapsuleShape, spotlight = SpotlightPreset.ChipTab, onClick = onToggle)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            },
        )
        if (blocks.isEmpty()) {
            Group {
                Item(
                    title = {
                        Text(
                            text = "暂无更多分类",
                            kind = CouiTextKind.Body,
                            color = CouiTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Start,
                        )
                    },
                )
            }
        } else {
            val visible = if (expanded) blocks else blocks.take(1)
            visible.forEach { block -> CouiCategoryBlock(block = block, onTagClick = onTagClick) }
        }
    }
}

/** 单个标签区块：卡片内用 FlowRow 排胶囊，标签可换行。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CouiCategoryBlock(
    block: CategoryBlock,
    onTagClick: (String) -> Unit,
) {
    Group(title = block.title) {
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            block.tags.forEach { tag ->
                CouiTagChip(text = tag, onClick = { onTagClick(tag) })
            }
        }
    }
}

/** 分类轨道：横向滚动的一等分胶囊，选中态复用 CouiSegmentedCapsules 的容器色与段高。 */
@Composable
private fun CouiCategoryRail(
    options: List<BrowseCategory>,
    selected: BrowseCategory,
    onSelect: (BrowseCategory) -> Unit,
) {
    val colors = CouiTheme.colorScheme
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(options) { option ->
            val active = option == selected
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (active) colors.accent.copy(alpha = CouiSelectedContainerAlpha)
                        else Color.Transparent,
                    )
                    .couiPressable(
                        shape = RoundedCornerShape(20.dp),
                        spotlight = SpotlightPreset.ChipTab,
                        onClick = { onSelect(option) },
                    )
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = option.label,
                    kind = CouiTextKind.PreferenceValue,
                    color = if (active) colors.accent else colors.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/** 标签胶囊：容器色底 + 胶囊裁剪，按压反馈走 couiPressable。 */
@Composable
private fun CouiTagChip(
    text: String,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        kind = CouiTextKind.Body,
        color = CouiTheme.colorScheme.onSurface,
        textAlign = TextAlign.Start,
        modifier = Modifier
            .clip(CouiCapsuleShape)
            .background(CouiTheme.colorScheme.container)
            .couiPressable(shape = CouiCapsuleShape, spotlight = SpotlightPreset.ChipTab, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** 标签导航：当前主分类的子分类 + 热门标签，点一下直接按标签检索。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CouiTagNavGroup(
    subCategoryNames: List<String>,
    hotTags: List<String>,
    onTagClick: (String) -> Unit,
) {
    Group(title = "按标签浏览") {
        if (subCategoryNames.isNotEmpty()) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                subCategoryNames.forEach { name ->
                    CouiTagChip(text = name, onClick = { onTagClick(name) })
                }
            }
        }
        if (hotTags.isNotEmpty()) {
            if (subCategoryNames.isNotEmpty()) Divider()
            Text(
                text = "热门标签",
                kind = CouiTextKind.Description,
                color = CouiTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp),
            )
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                hotTags.forEach { tag ->
                    CouiTagChip(text = tag, onClick = { onTagClick(tag) })
                }
            }
        }
    }
}

/**
 * 作品行：统一走 [CouiWorkRowCard]，榜单名次落在行首槽位。
 *
 * 名次由调用点按全局偏移算出（[displayRank]），前三名交给 [CouiWorkRankBadge] 上金银铜，
 * 其余名次沿用它自带的次要文字档；页面不再自绘 60×80 缩略图与名次列。
 */
@Composable
private fun CouiBrowseResultRow(
    result: SearchResult,
    rank: Int?,
    onClick: () -> Unit,
) {
    val rankSlot: (@Composable RowScope.() -> Unit)? = if (rank == null) {
        null
    } else {
        {
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .padding(start = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                CouiWorkRankBadge(rank = rank)
            }
        }
    }
    CouiWorkRowCard(
        coverUrl = result.coverUrl,
        title = result.title,
        onClick = onClick,
        albumId = result.id,
        subtitle = result.authors.joinToString(" / ").ifBlank { null },
        category = result.category,
        leading = rankSlot,
    )
}
