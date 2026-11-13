// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.page

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.itos.coui.backdrop.rememberCouiBackdrop
import com.itos.coui.chip.ChipTabButton
import com.itos.coui.chip.ChipTabRow
import com.itos.coui.chip.CouiChipTabItem
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.CouiDialogActionStyle
import com.itos.coui.dialog.CouiDialogPlacement
import com.itos.coui.dialog.Dialog
import com.itos.coui.effect.SpotlightPreset
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.layout.Divider
import com.itos.coui.layout.Footer
import com.itos.coui.layout.Group
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
import com.jm3.app.miuix.ui.LocalWideLayout
import com.jm3.app.miuix.ui.PaginationModeReloadEffect
import com.jm3.app.miuix.ui.SessionListKey
import com.jm3.app.miuix.ui.browseTagCacheKey
import com.jm3.app.miuix.ui.couiPageExtraBottomDp
import com.jm3.app.miuix.ui.normalizeBrowseTagKeyword
import com.jm3.app.miuix.ui.coui.component.CouiAppScaffold
import com.jm3.app.miuix.ui.coui.component.CouiBodyText
import com.jm3.app.miuix.ui.coui.component.CouiCommentFieldShape
import com.jm3.app.miuix.ui.coui.component.CouiEmptyGroup
import com.jm3.app.miuix.ui.coui.component.CouiFab
import com.jm3.app.miuix.ui.coui.component.CouiGroupTitle
import com.jm3.app.miuix.ui.coui.component.CouiLoadMoreFooter
import com.jm3.app.miuix.ui.coui.component.CouiLoadingGroup
import com.jm3.app.miuix.ui.coui.component.CouiMinTouchTarget
import com.jm3.app.miuix.ui.coui.component.CouiPagingGroup
import com.jm3.app.miuix.ui.coui.component.CouiScreenFailureGroup
import com.jm3.app.miuix.ui.coui.component.CouiSegmentedCapsules
import com.jm3.app.miuix.ui.coui.component.CouiSelectedContainerAlpha
import com.jm3.app.miuix.ui.coui.component.CouiTagCapsulePill
import com.jm3.app.miuix.ui.coui.component.CouiTopBarAction
import com.jm3.app.miuix.ui.coui.component.CouiTopBarBackButton
import com.jm3.app.miuix.ui.coui.component.CouiTopBarTitle
import com.jm3.app.miuix.ui.coui.component.CouiWorkCardTokens
import com.jm3.app.miuix.ui.coui.component.CouiWorkRankBadge
import com.jm3.app.miuix.ui.coui.component.CouiWorkRowCard
import com.jm3.app.miuix.ui.list.ListLoadState
import com.jm3.app.miuix.ui.list.SessionListLoader
import com.jm3.app.miuix.ui.list.SessionListSnapshot
import com.jm3.app.miuix.ui.coui.component.LocalCouiAppBackdrop
import com.jm3.app.miuix.ui.coui.component.couiDialogListMaxHeight
import com.jm3.app.miuix.ui.coui.component.couiDialogMaterial
import com.jm3.app.miuix.ui.coui.component.couiDialogSurfaceBorder
import com.jm3.app.miuix.ui.coui.component.couiDialogSurfaceColor
import com.jm3.app.miuix.ui.feedRowItems
import com.jm3.app.miuix.ui.coui.component.couiPressable
import com.jm3.app.miuix.ui.coui.component.couiScreenContentPadding
import com.jm3.app.miuix.ui.coui.component.couiTopAppBarColors
import com.jm3.app.miuix.ui.coui.component.rememberCouiBodyTextStyle
import com.jm3.app.miuix.ui.coui.icon.CouiIcons
import com.jm3.app.miuix.ui.coui.theme.CouiStrings
import com.jm3.app.miuix.ui.orDegrade
import com.jm3.app.miuix.ui.readableWidth
import com.jm3.core.model.BrowseCategory
import com.jm3.core.model.BrowseFilter
import com.jm3.core.model.BrowseOrder
import com.jm3.core.model.BrowseTime
import com.jm3.core.model.CategoryBlock
import com.jm3.core.model.CategoryTree
import com.jm3.core.model.SearchPage
import com.jm3.core.model.SearchResult
import com.jm3.core.model.SearchScope
import com.jm3.core.paging.PageSlice
import com.jm3.core.paging.PagedFeed
import com.jm3.core.paging.pagerTargetPage
import com.jm3.core.session.SessionOutcome
import com.jm3.core.settings.PaginationMode
import com.jm3.core.toSlice
import top.yukonga.miuix.kmp.basic.Icon

/** 标签检索视图里结果行的起始下标：0 是「标签：xxx」标题，其后才是行。 */
private const val TAG_FEED_ROW_START = 1

/** 主列表与标签列表共用的续页失败提示，文案与 HyperOS 版浏览页一致。 */
private const val BROWSE_LOAD_MORE_FAILED = CouiStrings.LoadMoreFailedRetry

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
 * 点标签进入本页的标签检索视图，结果同步写入 SessionCache 的 BrowseTag 键以便退栈直接复用。
 */
@Composable
internal fun CouiBrowseScreen(
    deps: ScreenDeps,
    mode: BrowseMode,
    onOpenAlbum: (String) -> Unit,
    onOpenLogin: () -> Unit = {},
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

    // 加载编排交给共享的 SessionListLoader：首屏 / 下拉刷新 / 翻页 / 跳页 / 失败分派都只有一份实现，
    // 本页只留渲染用的 ListState，以及「换页后归顶」这种纯视觉决策。
    // 分类只认分类 + 排序，榜单只认时间 + 排序：两套口径与旧页一致
    val currentFilter = when (mode) {
        BrowseMode.CATEGORY -> BrowseFilter(category = category, order = order, time = BrowseTime.ALL)
        BrowseMode.RANK -> BrowseFilter(category = BrowseCategory.ALL, order = order, time = time)
    }

    val feedLoader = remember(pageKey) {
        SessionListLoader(
            scope = scope,
            idOf = { item: SearchResult -> item.id },
            failureMessage = CouiStrings.LoadFailed,
            fetchPage = { page ->
                authCall.call { gateway.browse(currentFilter, page) }.toCouiPageSlice()
            },
            notice = {},
            cache = sessionCache,
            cacheKey = listKey,
        )
    }

    // 单页条数：只有首页能确定，用于榜单名次跨页续号
    var pageStride by remember(pageKey) { mutableIntStateOf(0) }

    val pageMode = deps.paging.paginationMode
    val clickPaging = pageMode == PaginationMode.CLICK

    // 每周连载更新：首页一页 + 用户点「加载更多」追加；分类模式收进顶栏入口弹层
    val weeklyLoader = remember {
        SessionListLoader(
            scope = scope,
            idOf = { item: SearchResult -> item.id },
            failureMessage = "每周更新加载失败",
            fetchPage = { page ->
                authCall.call { gateway.serialization(page) }.toCouiPageSlice()
            },
            notice = {},
        )
    }

    // 分类树只用来渲染标签区块；拿不到时不显示顶栏「更多」入口，避免空弹窗
    var categoryTree by remember { mutableStateOf<CategoryTree?>(null) }
    var weeklyDialogOpen by rememberSaveable { mutableStateOf(false) }
    var moreCategoriesDialogOpen by rememberSaveable { mutableStateOf(false) }
    var tagNavExpanded by rememberSaveable { mutableStateOf(false) }
    // 热门标签：与子分类一起构成结果列表之前的标签导航；拿不到就整块隐藏
    var hotTags by remember { mutableStateOf(emptyList<String>()) }

    // 激活标签保留在 Saveable 状态中；loader 用 BrowseTag 键自取缓存种子，退栈重建后首帧即可恢复
    var tagKeyword by rememberSaveable { mutableStateOf<String?>(null) }
    val tagLoader = remember(tagKeyword, order) {
        tagKeyword?.let { keyword ->
            SessionListLoader(
                scope = scope,
                idOf = { item: SearchResult -> item.id },
                failureMessage = "标签加载失败",
                fetchPage = { page ->
                    authCall.call {
                        gateway.search(
                            text = keyword,
                            page = page,
                            order = order,
                            scope = SearchScope.TAG,
                        )
                    }.toCouiPageSlice()
                },
                notice = {},
                cache = sessionCache,
                cacheKey = browseTagCacheKey(keyword, order),
            )
        }
    }

    val feedSnapshot by feedLoader.snapshot.collectAsState()
    val tagSnapshot = tagLoader?.snapshot?.collectAsState()?.value
    val weeklySnapshot by weeklyLoader.snapshot.collectAsState()

    // 换页后回到顶部：loader 不替页面滚列表，归顶时机仍由本页决定（整页替换落定那一刻）
    var pendingFeedScrollTop by remember { mutableStateOf(false) }
    var pendingTagScrollTop by remember { mutableStateOf(false) }

    fun refresh() {
        feedLoader.refresh()
    }

    fun loadMore() {
        feedLoader.loadMore()
    }

    /** 点击分页：整页替换，沿用同一筛选口径与首屏缓存。 */
    fun jumpToPage(target: Int) {
        val current = feedSnapshot.feed
        val resolved = pagerTargetPage(current.page, target, current.pageCount)
        if (resolved == current.page) return
        pendingFeedScrollTop = true
        feedLoader.goToPage(resolved)
    }

    fun loadWeekly() {
        weeklyLoader.loadFirstPage()
    }

    fun loadMoreWeekly() {
        weeklyLoader.loadMore()
    }

    fun loadTag(keyword: String) {
        val trimmed = normalizeBrowseTagKeyword(keyword) ?: return
        if (trimmed == tagKeyword) {
            // 同一个标签：loader 已被 remember 复用，重试要走它的首屏入口
            tagLoader?.loadFirstPage()
        } else {
            // 换标签：tagKeyword 一变就重建 loader，首屏由 LaunchedEffect(tagLoader) 触发
            tagKeyword = trimmed
        }
    }

    fun loadMoreTag() {
        tagLoader?.loadMore()
    }

    fun jumpToTagPage(target: Int) {
        val loader = tagLoader ?: return
        val current = tagSnapshot?.feed ?: return
        val resolved = pagerTargetPage(current.page, target, current.pageCount)
        if (resolved == current.page) return
        pendingTagScrollTop = true
        loader.goToPage(resolved)
    }

    fun openTag(keyword: String) {
        tagKeyword = normalizeBrowseTagKeyword(keyword) ?: return
    }

    // 命中缓存时种子已是 Loaded：同一口径切回来直接渲染，不退回 Loading 冲掉
    LaunchedEffect(feedLoader) {
        if (feedLoader.state !is ListLoadState.Loaded) feedLoader.loadFirstPage()
    }
    // 首页条数落定即可用于榜单名次续号；续页与跳页不改写这个基准
    LaunchedEffect(feedSnapshot.feed.page, feedSnapshot.feed.items.size) {
        val feed = feedSnapshot.feed
        if (feed.page == 1 && feed.items.isNotEmpty()) pageStride = feed.items.size
    }
    LaunchedEffect(feedSnapshot.state, feedSnapshot.feed.page) {
        if (!pendingFeedScrollTop || feedSnapshot.state !is ListLoadState.Loaded) return@LaunchedEffect
        pendingFeedScrollTop = false
        lazyState.animateScrollToItem(0)
    }
    LaunchedEffect(tagLoader) {
        val loader = tagLoader ?: return@LaunchedEffect
        if (loader.state !is ListLoadState.Loaded) loader.loadFirstPage()
    }
    LaunchedEffect(tagSnapshot?.state, tagSnapshot?.feed?.page) {
        if (!pendingTagScrollTop || tagSnapshot?.state !is ListLoadState.Loaded) return@LaunchedEffect
        pendingTagScrollTop = false
        lazyState.animateScrollToItem(0)
    }
    PaginationModeReloadEffect(pageMode) {
        feedLoader.loadFirstPage()
        normalizeBrowseTagKeyword(tagKeyword)?.let(::loadTag)
    }
    LaunchedEffect(deps.gateway, deps.authCall) {
        weeklyLoader.loadFirstPage()
        // 分类树失败只让「更多分类」退化成一行提示，不打断主列表
        categoryTree = authCall.call { gateway.categoriesList() }.orDegrade<CategoryTree?>(null)
        hotTags = authCall.call { gateway.hotTags() }.orDegrade(emptyList())
    }

    // 标签导航的数据：当前主分类的子分类 + 热门标签（热门标签只在分类模式给）
    val categoryBlocks = categoryTree?.blocks.orEmpty()
    val subCategories = categoryTree
        ?.categories
        ?.firstOrNull { it.slug == category.apiValue }
        ?.children
        .orEmpty()
    val tagNavTags = if (mode == BrowseMode.CATEGORY) hotTags.take(HOT_TAG_LIMIT) else emptyList()
    val hasTagNav = subCategories.isNotEmpty() || tagNavTags.isNotEmpty()
    // 行卡列数只读 policy 的派生值：手机断点为 1，内容宽够 840dp 才排两列
    val feedColumns = LocalWideLayout.current.feedColumns
    val weekly = weeklySnapshot.feed
    val weeklyIsLoading = weeklySnapshot.state is ListLoadState.Loading
    val weeklyFail = weeklySnapshot.state as? ListLoadState.Failed
    val tagFeed = tagSnapshot?.feed ?: PagedFeed()
    val feedState = feedSnapshot.toListState()
    val browseHeadCount = couiBrowseHeadCount(
        isCategoryMode = mode == BrowseMode.CATEGORY,
        hasTagNav = hasTagNav,
        hasWeeklyPaging = weekly.items.isNotEmpty(),
        weeklyItemCount = weekly.items.size,
        hasWeeklyFooter = weekly.items.isNotEmpty() && weeklyFail != null,
        columns = feedColumns,
    )

    /*
     * 触底预取：把懒列表下标换算成结果行下标再判提前量。
     * 周更与更多分类在分类模式下已移入弹层，行起点只由筛选、标签导航与标题决定，追加一页后不会漂移；
     * 采集与行高换算在 CouiFeedPrefetchEffect，本页只交当前 feed、行起点与取下一页的动作。
     */
    val browseTagView = tagKeyword != null
    CouiFeedPrefetchEffect(
        feed = if (browseTagView) tagFeed else (feedState as? ListState.Loaded)?.feed,
        rowStart = if (browseTagView) TAG_FEED_ROW_START else browseHeadCount,
        listState = lazyState,
        columns = feedColumns,
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
    val keyword = tagKeyword
    val tagFailureMessage = tagSnapshot?.state as? ListLoadState.Failed
    val tagIsLoading = tagSnapshot?.state is ListLoadState.Loading
    val tagItems = tagFeed.items
    val tagTotal = tagFeed.total
    val tagPage = tagFeed.page
    val tagPageCount = tagFeed.pageCount
    val tagHasMore = tagFeed.hasMore
    val tagLoadingMore = tagFeed.loadingMore
    val tagLoadMoreFailed = tagFeed.loadMoreFailed
    val categoryBackdrop = if (Build.VERSION.SDK_INT >= 34) rememberCouiBackdrop() else null
    val activeBackdrop = if (mode == BrowseMode.CATEGORY && keyword == null) categoryBackdrop else null

    CouiAppScaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentBackdrop = activeBackdrop,
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                LargeTopAppBar(
                    title = { CouiTopBarTitle(text = if (keyword == null) mode.title else "标签：$keyword") },
                    navigationIcon = {
                        if (keyword != null) {
                            CouiTopBarBackButton(onClick = { tagKeyword = null })
                        }
                    },
                    actions = {
                        if (mode == BrowseMode.CATEGORY && keyword == null) {
                            CouiTopBarAction(
                                icon = CouiIcons.Clock,
                                contentDescription = "每周连载更新",
                                onClick = { weeklyDialogOpen = true },
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    colors = couiTopAppBarColors(),
                )
                if (mode == BrowseMode.CATEGORY && keyword == null) {
                    CouiBrowseCategoryBar(
                        options = BrowseCategory.entries,
                        selected = category,
                        onSelect = { categoryValue = it.apiValue },
                        showMoreAction = categoryBlocks.isNotEmpty(),
                        onOpenMore = { moreCategoriesDialogOpen = true },
                    )
                }
            }
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
            extraBottom = couiPageExtraBottomDp(hasFab = true).dp,
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
                                    kind = CouiTextKind.GroupFooter,
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
                        CouiScreenFailureGroup(
                            message = tagFailureMessage.message,
                            expired = tagFailureMessage.sessionExpired,
                            onRetry = { loadTag(keyword) },
                            onOpenLogin = onOpenLogin,
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
                        feedRowItems(
                            count = tagItems.size,
                            columns = feedColumns,
                            keyPrefix = "coui-tag-row-",
                            keyOf = { index -> tagItems[index].id },
                        ) { index ->
                            val result = tagItems[index]
                            CouiBrowseRowSlot(
                                result = result,
                                rank = null,
                                onClick = { onOpenAlbum(result.id) },
                            )
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
                                small = true,
                                modifier = Modifier.padding(
                                    horizontal = CouiPageDefaults.horizontalMargin,
                                    vertical = 8.dp,
                                ),
                            )
                        } else {
                            CouiSegmentedCapsules(
                                items = BrowseTime.entries.map { it.label },
                                selectedIndex = BrowseTime.entries.indexOf(time),
                                onSelect = { timeValue = BrowseTime.entries[it].apiValue },
                                small = true,
                                modifier = Modifier.padding(
                                    horizontal = CouiPageDefaults.horizontalMargin,
                                    vertical = 8.dp,
                                ),
                            )
                            CouiSegmentedCapsules(
                                items = BrowseOrder.entries.map { it.label },
                                selectedIndex = BrowseOrder.entries.indexOf(order),
                                onSelect = { orderValue = BrowseOrder.entries[it].apiValue },
                                small = true,
                                modifier = Modifier.padding(
                                    horizontal = CouiPageDefaults.horizontalMargin,
                                    vertical = 4.dp,
                                ),
                            )
                        }
                    }
                }
                if (hasTagNav) {
                    item(key = "coui-browse-tag-nav") {
                        CouiCollapsibleTagNavGroup(
                            subCategoryNames = subCategories.map { it.name },
                            hotTags = tagNavTags,
                            expanded = tagNavExpanded,
                            onToggle = { tagNavExpanded = !tagNavExpanded },
                            onTagClick = { openTag(it) },
                        )
                    }
                }
                if (mode == BrowseMode.RANK) {
                    // 榜单模式仍就地保留每周连载更新（与其余列表统一为独立浮岛行卡）与续页脚
                    couiWeeklySection(
                        feed = weekly,
                        columns = feedColumns,
                        loading = weeklyIsLoading,
                        failure = weeklyFail,
                        onRetry = { loadWeekly() },
                        onLoadMore = { loadMoreWeekly() },
                        onOpenLogin = onOpenLogin,
                        onOpenAlbum = onOpenAlbum,
                    )
                }

                when (feedState) {
                    ListState.Loading -> item(key = "coui-browse-loading") {
                        CouiLoadingGroup()
                    }

                    is ListState.Failed -> item(key = "coui-browse-error") {
                        CouiScreenFailureGroup(
                            message = feedState.message,
                            expired = feedState.sessionExpired,
                            onRetry = { refresh() },
                            onOpenLogin = onOpenLogin,
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
                                CouiEmptyGroup(text = CouiStrings.EmptyWorks)
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
                                                kind = CouiTextKind.GroupFooter,
                                                color = colors.onSurfaceVariant,
                                            )
                                        }
                                    },
                                )
                            }
                            // 首页拿不到时退回本页条数：宁可名次在本页重新起算，也不要空着
                            val stride = if (pageStride > 0) pageStride else feed.items.size.coerceAtLeast(1)
                            // 大屏两列，key 与单列时完全一致（含序号），滚动位置不丢
                            feedRowItems(
                                count = feed.items.size,
                                columns = feedColumns,
                                keyPrefix = "coui-browse-row-",
                                keyOf = { index -> feed.items[index].id + "-" + index },
                            ) { index ->
                                val result = feed.items[index]
                                CouiBrowseRowSlot(
                                    result = result,
                                    rank = if (mode == BrowseMode.RANK) {
                                        displayRank(clickPaging, feed.page, stride, index)
                                    } else {
                                        null
                                    },
                                    onClick = { onOpenAlbum(result.id) },
                                )
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

    if (mode == BrowseMode.CATEGORY) {
        CouiMoreCategoriesDialog(
            visible = moreCategoriesDialogOpen && categoryBlocks.isNotEmpty(),
            blocks = categoryBlocks,
            onDismiss = { moreCategoriesDialogOpen = false },
            onTagClick = { tag ->
                moreCategoriesDialogOpen = false
                openTag(tag)
            },
        )
        CouiWeeklyUpdatesDialog(
            visible = weeklyDialogOpen,
            feed = weekly,
            loading = weeklyIsLoading,
            failure = weeklyFail,
            onDismiss = { weeklyDialogOpen = false },
            onRetry = { loadWeekly() },
            onLoadMore = { loadMoreWeekly() },
            onOpenLogin = {
                weeklyDialogOpen = false
                onOpenLogin()
            },
            onOpenAlbum = { albumId ->
                weeklyDialogOpen = false
                onOpenAlbum(albumId)
            },
        )
    }
}

/** 顶栏下方主分类条：API 34+ 走原厂 ChipTabRow（含“更多”前置按钮与毛玻璃背景），低版本退化为轻量滚动胶囊栏。 */
@Composable
private fun CouiBrowseCategoryBar(
    options: List<BrowseCategory>,
    selected: BrowseCategory,
    onSelect: (BrowseCategory) -> Unit,
    showMoreAction: Boolean,
    onOpenMore: () -> Unit,
) {
    val backdrop = LocalCouiAppBackdrop.current
    val display = LocalView.current.display
    val colors = CouiTheme.colorScheme
    if (Build.VERSION.SDK_INT >= 34 && backdrop != null && display != null) {
        val items = remember(options) {
            options.map { option ->
                CouiChipTabItem(key = option.apiValue, label = option.label)
            }
        }
        ChipTabRow(
            modifier = Modifier.fillMaxWidth().readableWidth(),
            items = items,
            selectedKey = selected.apiValue,
            onSelectedKeyChange = { key ->
                val target = options.firstOrNull { it.apiValue == key } ?: return@ChipTabRow
                onSelect(target)
            },
            backdrop = backdrop,
            leadingAction = {
                ChipTabButton(
                    onClick = onOpenMore,
                    contentDescription = "更多分类",
                    backdrop = backdrop,
                    contentColor = colors.onSurface,
                ) {
                    Icon(
                        imageVector = CouiIcons.Category,
                        contentDescription = null,
                        tint = colors.onSurface,
                    )
                }
            },
            leadingActionVisible = showMoreAction,
        )
    } else {
        CouiFallbackCategoryBar(
            options = options,
            selected = selected,
            onSelect = onSelect,
            showMoreAction = showMoreAction,
            onOpenMore = onOpenMore,
        )
    }
}

/** API 31-33 降级主分类轨道：32dp 轻量胶囊，保持与 ChipTabRow 一致的低高度占比。 */
@Composable
private fun CouiFallbackCategoryBar(
    options: List<BrowseCategory>,
    selected: BrowseCategory,
    onSelect: (BrowseCategory) -> Unit,
    showMoreAction: Boolean,
    onOpenMore: () -> Unit,
) {
    val colors = CouiTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .readableWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showMoreAction) {
            Box(
                modifier = Modifier
                    .padding(start = CouiPageDefaults.horizontalMargin, end = 8.dp)
                    .size(32.dp)
                    .clip(CouiCapsuleShape)
                    .couiPressable(
                        shape = CouiCapsuleShape,
                        backgroundColor = colors.container,
                        spotlight = SpotlightPreset.ChipTab,
                        minTouchTarget = CouiMinTouchTarget,
                        onClick = onOpenMore,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = CouiIcons.Category,
                    contentDescription = "更多分类",
                    modifier = Modifier.size(16.dp),
                    tint = colors.onSurface,
                )
            }
        }
        LazyRow(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                start = if (showMoreAction) 0.dp else CouiPageDefaults.horizontalMargin,
                end = CouiPageDefaults.horizontalMargin,
            ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(options, key = { it.apiValue }) { option ->
                val active = option == selected
                Box(
                    modifier = Modifier
                        .height(32.dp)
                        .clip(CouiCapsuleShape)
                        .background(
                            if (active) colors.accent.copy(alpha = CouiSelectedContainerAlpha)
                            else colors.container,
                        )
                        .couiPressable(
                            shape = CouiCapsuleShape,
                            spotlight = SpotlightPreset.ChipTab,
                            minTouchTarget = CouiMinTouchTarget,
                            onClick = { onSelect(option) },
                        )
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = option.label,
                        kind = CouiTextKind.GroupFooter,
                        color = if (active) colors.accent else colors.onSurface,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** 更多分类弹层：将冗长的分类标签树收进底部弹窗，主列表直接落回作品本身。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CouiMoreCategoriesDialog(
    visible: Boolean,
    blocks: List<CategoryBlock>,
    onDismiss: () -> Unit,
    onTagClick: (String) -> Unit,
) {
    val colors = CouiTheme.colorScheme
    var filterQuery by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(visible) {
        if (visible) filterQuery = ""
    }
    val filteredBlocks = remember(blocks, filterQuery) {
        couiFilterCategoryBlocks(blocks, filterQuery)
    }
    val surfaceColor = couiDialogSurfaceColor()
    val elevatedSurfaceColor = couiDialogSurfaceColor(elevated = true)
    val surfaceBorder = couiDialogSurfaceBorder()
    Dialog(
        onDismissRequest = onDismiss,
        visible = visible,
        material = couiDialogMaterial(),
        title = "更多分类",
        placement = CouiDialogPlacement.Bottom,
        confirmAction = CouiDialogAction(
            text = "关闭",
            onClick = onDismiss,
            style = CouiDialogActionStyle.Secondary,
        ),
    ) {
        // 直接承接 Dialog 玻璃材质，不再嵌套实底 Group 与实底输入栏割裂质感
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CouiPageDefaults.horizontalMargin)
                    .heightIn(min = 42.dp)
                    .clip(CouiCommentFieldShape)
                    .background(surfaceColor)
                    .border(surfaceBorder, CouiCommentFieldShape)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = CouiIcons.Search,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = colors.onSurfaceVariant,
                )
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (filterQuery.isEmpty()) {
                        CouiBodyText(
                            text = "筛选分类或标签",
                            color = colors.onSurfaceVariant,
                            textAlign = TextAlign.Start,
                        )
                    }
                    BasicTextField(
                        value = filterQuery,
                        onValueChange = { filterQuery = it },
                        singleLine = true,
                        textStyle = rememberCouiBodyTextStyle().copy(color = colors.onSurface),
                        cursorBrush = SolidColor(colors.accent),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (filterQuery.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .couiPressable(
                                shape = CouiCapsuleShape,
                                backgroundColor = elevatedSurfaceColor,
                                spotlight = SpotlightPreset.ChipTab,
                                minTouchTarget = CouiMinTouchTarget,
                                onClick = { filterQuery = "" },
                            )
                            .padding(4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = CouiIcons.Close,
                            contentDescription = "清除筛选",
                            modifier = Modifier.size(14.dp),
                            tint = colors.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (filteredBlocks.isEmpty()) {
                CouiBodyText(
                    text = "没有匹配的分类或标签",
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Start,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = couiDialogListMaxHeight(420.dp)),
                    contentPadding = PaddingValues(bottom = 8.dp),
                ) {
                    itemsIndexed(filteredBlocks, key = { _, block -> block.title }) { index, block ->
                        if (index > 0) {
                            Divider(startIndent = 16.dp, endIndent = 16.dp)
                        }
                        Text(
                            text = block.title,
                            kind = CouiTextKind.GroupTitle,
                            color = colors.onSurfaceVariant,
                            textAlign = TextAlign.Start,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = 20.dp,
                                    end = 20.dp,
                                    top = if (index == 0) 6.dp else 12.dp,
                                    bottom = 8.dp,
                                ),
                        )
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = CouiPageDefaults.horizontalMargin)
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            block.tags.forEach { tag ->
                                CouiTagCapsulePill(
                                    text = tag,
                                    glassSurface = true,
                                    onClick = { onTagClick(tag) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 每周连载更新弹层：普通列布局承载，避免在 Dialog 内嵌套无约束 LazyColumn。 */
@Composable
private fun CouiWeeklyUpdatesDialog(
    visible: Boolean,
    feed: PagedFeed<SearchResult>,
    loading: Boolean,
    failure: ListLoadState.Failed?,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenLogin: () -> Unit,
    onOpenAlbum: (String) -> Unit,
) {
    val colors = CouiTheme.colorScheme
    Dialog(
        onDismissRequest = onDismiss,
        visible = visible,
        material = couiDialogMaterial(),
        title = "每周连载更新",
        placement = CouiDialogPlacement.Bottom,
        confirmAction = CouiDialogAction(
            text = "关闭",
            onClick = onDismiss,
            style = CouiDialogActionStyle.Secondary,
        ),
    ) {
        when {
            loading && feed.items.isEmpty() -> CouiBodyText(
                text = "正在加载每周更新…",
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            )

            failure != null && feed.items.isEmpty() -> {
                CouiBodyText(
                    text = failure.message.ifBlank { "每周更新加载失败" },
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
                Spacer(modifier = Modifier.height(8.dp))
                CouiTagChip(
                    text = if (failure.sessionExpired) "去登录" else "重试",
                    onClick = {
                        if (failure.sessionExpired) onOpenLogin() else onRetry()
                    },
                )
            }

            feed.items.isEmpty() -> CouiBodyText(
                text = "暂无每周更新",
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            )

            else -> {
                val layoutSpec = couiWeeklyCardLayoutSpec(inDialog = true)
                feed.items.forEachIndexed { index, result ->
                    if (index > 0) Spacer(modifier = Modifier.height(layoutSpec.cardSpacingDp.dp))
                    CouiBrowseResultRow(
                        result = result,
                        rank = null,
                        onClick = { onOpenAlbum(result.id) },
                    )
                }
                when {
                    feed.loadingMore -> Text(
                        text = "正在加载更多…",
                        kind = CouiTextKind.GroupFooter,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                    feed.loadMoreFailed -> Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CouiTagChip(text = WEEKLY_LOAD_MORE_FAILED, onClick = onLoadMore)
                    }
                    feed.hasMore -> Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CouiTagChip(text = "加载更多", onClick = onLoadMore)
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
 * 失败时卡片下方仍给原因、卡内给可点的重试/去登录行（由 [CouiScreenFailureGroup] 提供）。
 * 续页脚在卡外单独成项：[CouiLoadMoreFooter] 自带卡片，套进本 Group 会变成卡中卡。
 */
@Composable
private fun CouiBrowseRowSlot(
    result: SearchResult,
    rank: Int?,
    onClick: () -> Unit,
    horizontalMargin: Dp = CouiPageDefaults.horizontalMargin,
    verticalPadding: Dp = CouiWorkCardTokens.RowSpacing / 2,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalMargin, vertical = verticalPadding),
    ) {
        CouiBrowseResultRow(
            result = result,
            rank = rank,
            onClick = onClick,
        )
    }
}

private fun LazyListScope.couiWeeklySection(
    feed: PagedFeed<SearchResult>,
    columns: Int,
    loading: Boolean,
    failure: ListLoadState.Failed?,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenLogin: () -> Unit,
    onOpenAlbum: (String) -> Unit,
) {
    val layoutSpec = couiWeeklyCardLayoutSpec(inDialog = false)
    if (feed.items.isEmpty()) {
        item(key = "coui-browse-weekly") {
            Column(modifier = Modifier.fillMaxWidth()) {
                CouiGroupTitle(title = "每周连载更新")
                when {
                    loading -> CouiLoadingGroup(text = "正在加载每周更新…")

                    failure != null -> CouiScreenFailureGroup(
                        message = failure.message,
                        expired = failure.sessionExpired,
                        onRetry = onRetry,
                        onOpenLogin = onOpenLogin,
                    )

                    else -> CouiEmptyGroup(text = "暂无每周更新")
                }
            }
        }
        return
    }
    item(key = "coui-browse-weekly-title") {
        CouiGroupTitle(title = "每周连载更新")
    }
    // 与其余行卡段同一套大屏分列：单列时 key 逐字不变，手机断点行为不动
    feedRowItems(
        count = feed.items.size,
        columns = columns,
        keyPrefix = "coui-weekly-row-",
        keyOf = { index -> feed.items[index].id + "-" + index },
    ) { index ->
        val result = feed.items[index]
        CouiBrowseRowSlot(
            result = result,
            rank = null,
            onClick = { onOpenAlbum(result.id) },
            horizontalMargin = layoutSpec.horizontalMarginDp.dp,
            verticalPadding = layoutSpec.cardSpacingDp.dp / 2,
        )
    }
    failure?.message?.let { message ->
        item(key = "coui-browse-weekly-footer") {
            Footer(text = message)
        }
    }
    item(key = "coui-browse-weekly-paging") {
        CouiLoadMoreFooter(
            feed = feed,
            errorMessage = WEEKLY_LOAD_MORE_FAILED,
            onRetry = onLoadMore,
        )
    }
}

/** 可折叠子分类与热门标签导航卡片：默认收起以保持首屏清爽，点击标题栏按需展开。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CouiCollapsibleTagNavGroup(
    subCategoryNames: List<String>,
    hotTags: List<String>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onTagClick: (String) -> Unit,
) {
    val colors = CouiTheme.colorScheme
    val totalCount = subCategoryNames.size + hotTags.size
    val title = when {
        subCategoryNames.isNotEmpty() && hotTags.isNotEmpty() -> "子分类与热门标签"
        subCategoryNames.isNotEmpty() -> "子分类"
        else -> "热门标签"
    }
    Group {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .couiPressable(
                    spotlight = SpotlightPreset.ChipTab,
                    minTouchTarget = CouiMinTouchTarget,
                    onClick = onToggle,
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CouiBodyText(
                text = title,
                color = colors.onSurface,
                textAlign = TextAlign.Start,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${totalCount} 项",
                kind = CouiTextKind.GroupFooter,
                color = colors.onSurfaceVariant,
                maxLines = 1,
            )
            Icon(
                imageVector = CouiIcons.Forward,
                contentDescription = if (expanded) "收起" else "展开",
                modifier = Modifier
                    .size(16.dp)
                    .rotate(if (expanded) 90f else 0f),
                tint = colors.onSurfaceVariant,
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Divider()
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
                        kind = CouiTextKind.GroupFooter,
                        color = colors.onSurfaceVariant,
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
    }
}

/** 标签胶囊：容器色底 + 胶囊裁剪，按压反馈走 couiPressable。 */
@Composable
private fun CouiTagChip(
    text: String,
    onClick: () -> Unit,
) {
    CouiTagCapsulePill(
        text = text,
        glassSurface = true,
        onClick = onClick,
    )
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

/** 按关键词过滤更多分类分区；空查询保留全部非空分区，仅命中分区标题时保留该分块全部标签。 */
internal fun couiFilterCategoryBlocks(
    blocks: List<CategoryBlock>,
    query: String,
): List<CategoryBlock> {
    val keyword = query.trim()
    if (keyword.isEmpty()) return blocks.filter { it.tags.isNotEmpty() }
    return blocks.mapNotNull { block ->
        if (block.tags.isEmpty()) return@mapNotNull null
        if (block.title.contains(keyword, ignoreCase = true)) {
            block
        } else {
            val matched = block.tags.filter { it.contains(keyword, ignoreCase = true) }
            if (matched.isEmpty()) null else block.copy(tags = matched)
        }
    }
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
