package com.jm3.app.miuix.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.net.toUri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.ui.glass.CollapsibleSearchAction
import com.jm3.app.miuix.ui.glass.CollapsibleSearchBar
import com.jm3.app.miuix.ui.glass.GlassPullToRefresh
import com.jm3.app.miuix.ui.glass.GlassScaffold
import com.jm3.app.miuix.ui.glass.GlassSearchInputField
import com.jm3.app.miuix.ui.glass.GlassTopAppBar
import com.jm3.app.miuix.ui.glass.collapsibleSearchScroll
import com.jm3.app.miuix.ui.glass.rememberCollapsibleSearchState
import com.jm3.app.miuix.ui.glass.withSpacing
import com.jm3.app.miuix.ui.skin.LocalSkin
import com.jm3.core.model.CreatorAuthorWorks
import com.jm3.core.model.CreatorFilters
import com.jm3.core.model.CreatorMeta
import com.jm3.core.model.CreatorPage
import com.jm3.core.model.CreatorRelatedWork
import com.jm3.core.model.CreatorWork
import com.jm3.core.model.CreatorWorkMeta
import com.jm3.core.model.CreatorWorkPage
import com.jm3.core.paging.PageSlice
import com.jm3.core.paging.PagedFeed
import com.jm3.core.session.SessionOutcome
import com.jm3.core.settings.PaginationMode
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.BreadcrumbBar
import top.yukonga.miuix.kmp.basic.BreadcrumbItem
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.FloatingToolbar
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 原版列表每页 30 条；接口只返回总数，总页数由它推算。 */
private const val LIBRARY_PAGE_SIZE = 30

private val LIBRARY_TABS = listOf("创作者", "作品")

/** 书库首页：创作者/作品双 Tab + 关键词搜索 + 语言/来源筛选 + 两种分页模式。 */
@Composable
fun LibraryScreen(
    deps: ScreenDeps,
    onBack: () -> Unit,
    onOpenCreator: (String) -> Unit,
    onOpenWork: (String) -> Unit,
    snackbarHostState: SnackbarHostState,
    onOpenLogin: () -> Unit = {},
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val selectionHaptic = rememberSelectionHaptic()
    // 作品网格列数：手机断点以下仍是两列，大屏按窗口宽度补列，卡片宽度由列宽反算
    val gridColumns = LocalWideLayout.current.gridColumns
    var query by rememberSaveable { mutableStateOf("") }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var appliedQuery by rememberSaveable { mutableStateOf("") }
    var lang by rememberSaveable { mutableStateOf("") }
    var source by rememberSaveable { mutableStateOf("") }
    // 两个 Tab 各有一份分页与状态：左右滑动过渡期互不串屏
    var creatorFeed by remember { mutableStateOf(PagedFeed<CreatorMeta>()) }
    var workFeed by remember { mutableStateOf(PagedFeed<CreatorWorkMeta>()) }
    var filters by rememberSaveable { mutableStateOf(CreatorFilters()) }
    var creatorLoading by remember { mutableStateOf(false) }
    var workLoading by remember { mutableStateOf(false) }
    var creatorLoadedOnce by remember { mutableStateOf(false) }
    var workLoadedOnce by remember { mutableStateOf(false) }
    var refreshingTab by remember { mutableStateOf<Int?>(null) }
    // 失效与失败分 Tab 持有：切走再切回仍保留对应页的错误/重登提示
    var creatorFailure by remember { mutableStateOf<ScreenLoadPlan?>(null) }
    var workFailure by remember { mutableStateOf<ScreenLoadPlan?>(null) }
    // 两个 Tab 各持独立代次，切换 Tab 时互不作废对方在途请求
    var creatorLoadToken by remember { mutableIntStateOf(0) }
    var workLoadToken by remember { mutableIntStateOf(0) }
    var creatorPageBusy by remember { mutableStateOf(false) }
    var workPageBusy by remember { mutableStateOf(false) }
    var attemptedCreatorQuery by remember { mutableStateOf<String?>(null) }
    var attemptedWorkParams by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    val paginationMode = deps.preferences.paginationMode
    // 阻尼阈值略低：下拉更轻快即触发刷新，回弹仍由库的弹簧曲线负责
    val refreshState = rememberPullToRefreshState(refreshThreshold = 0.18f)
    val scope = rememberCoroutineScope()
    val creatorListState = rememberLazyListState()
    val workListState = rememberLazyListState()
    val searchCollapse = rememberCollapsibleSearchState(
        listState = if (tab == 0) creatorListState else workListState,
    )
    val pagerState = rememberSyncedTabPagerState(
        selectedTab = tab,
        pageCount = LIBRARY_TABS.size,
        onTabSelected = { next ->
            selectionHaptic()
            tab = next
        },
        useMiuixSpring = true,
    )

    /** 失效与失败共用一个落点：[planLoad] 决定整屏去登录/给重试，还是有内容时只弹提示。 */
    fun applyFailure(tabIndex: Int, outcome: SessionOutcome<*>, hasContent: Boolean) {
        val plan = planLoad(outcome, hasContent = hasContent, failureMessage = "加载失败")
        if (plan.keepContent) {
            scope.launch { snackbarHostState.showSnackbar(plan.notice.orEmpty()) }
        } else if (tabIndex == 0) {
            creatorFailure = plan
        } else {
            workFailure = plan
        }
    }

    fun load(
        targetPage: Int,
        append: Boolean,
        targetTab: Int = tab,
        showLoading: Boolean = !append && refreshingTab != targetTab,
        pageJump: Boolean = false,
    ) {
        if (pageJump && ((targetTab == 0 && creatorPageBusy) || (targetTab != 0 && workPageBusy))) return
        val keyword = appliedQuery
        // 在不在拉、还能不能拉都交给 feed：重复触底不会再并发打同一页
        val creatorRequest = if (append && targetTab == 0) creatorFeed.beginLoadMore() else null
        val workRequest = if (append && targetTab != 0) workFeed.beginLoadMore() else null
        // 拦截必须在占用 token 之前：被拦下的重复触底若先自增 token，在途请求的结果会被丢弃，
        // feed 就永远停在 loadingMore，页脚一直转圈且再也拉不动。
        if (creatorRequest === creatorFeed || workRequest === workFeed) return
        val token = if (append) {
            if (targetTab == 0) creatorLoadToken else workLoadToken
        } else {
            if (targetTab == 0) ++creatorLoadToken else ++workLoadToken
        }
        if (creatorRequest != null) creatorFeed = creatorRequest
        if (workRequest != null) workFeed = workRequest
        if (pageJump) {
            if (targetTab == 0) creatorPageBusy = true else workPageBusy = true
        } else if (!append) {
            if (targetTab == 0) creatorPageBusy = false else workPageBusy = false
        }
        if (showLoading) {
            if (targetTab == 0) creatorLoading = true else workLoading = true
        }
        if (targetTab == 0) creatorFailure = null else workFailure = null
        // 附加页说明列表已有内容；首屏与下拉刷新则按当前 Tab 是否已有条目决定要不要保住列表
        val hasContent = append || if (targetTab == 0) {
            creatorFeed.items.isNotEmpty()
        } else {
            workFeed.items.isNotEmpty()
        }
        scope.launch {
            try {
                // 启动闸门、IO 切线程与失效通知都在 AuthCall 里
                val outcome = deps.authCall.call {
                    if (targetTab == 0) {
                        deps.gateway.creatorAuthors(targetPage, keyword)
                    } else {
                        deps.gateway.creatorWorks(targetPage, keyword, lang, source)
                    }
                }
                val currentToken = if (targetTab == 0) creatorLoadToken else workLoadToken
                if (token != currentToken) return@launch
                if (targetTab == 0) creatorLoading = false else workLoading = false
                if (refreshingTab == targetTab) refreshingTab = null
                when (outcome) {
                    is SessionOutcome.Ok -> when (val value = outcome.value) {
                        is CreatorPage -> {
                            val slice = librarySlice(value.items, value.total, targetPage)
                            creatorFeed = creatorRequest?.append(slice) { it.id }
                                ?: PagedFeed<CreatorMeta>().replace(slice) { it.id }
                            creatorLoadedOnce = true
                            if (pageJump) creatorListState.scrollToItem(0)
                        }

                        is CreatorWorkPage -> {
                            filters = value.filters
                            val slice = librarySlice(value.items, value.total, targetPage)
                            workFeed = workRequest?.append(slice) { it.id }
                                ?: PagedFeed<CreatorWorkMeta>().replace(slice) { it.id }
                            workLoadedOnce = true
                            if (pageJump) workListState.scrollToItem(0)
                        }

                        else -> Unit
                    }

                    SessionOutcome.Expired -> applyFailure(targetTab, outcome, hasContent)

                    is SessionOutcome.Failed -> {
                        applyFailure(targetTab, outcome, hasContent)
                        // 附加页失败只影响页脚，重试入口交给页脚上的「加载失败，点击重试」
                        creatorRequest?.let { creatorFeed = it.failLoadMore() }
                        workRequest?.let { workFeed = it.failLoadMore() }
                    }
                }
            } finally {
                val currentToken = if (targetTab == 0) creatorLoadToken else workLoadToken
                if (pageJump && token == currentToken) {
                    if (targetTab == 0) creatorPageBusy = false else workPageBusy = false
                }
            }
        }
    }

    LaunchedEffect(tab, appliedQuery, lang, source) {
        if (tab == 0) {
            if (attemptedCreatorQuery != appliedQuery) {
                attemptedCreatorQuery = appliedQuery
                // 变更检索词先清旧列表并回顶，防止加载态或失败态混显旧结果
                creatorFeed = PagedFeed()
                creatorLoadedOnce = false
                creatorListState.requestScrollToItem(0)
                load(1, append = false, targetTab = 0)
            }
        } else {
            val params = Triple(appliedQuery, lang, source)
            if (attemptedWorkParams != params) {
                attemptedWorkParams = params
                // 变更检索词或筛选条件先清旧列表并回顶，防止混显或误按旧内容保底
                workFeed = PagedFeed()
                workLoadedOnce = false
                workListState.requestScrollToItem(0)
                load(1, append = false, targetTab = 1)
            }
        }
    }
    PaginationModeReloadEffect(paginationMode) {
        attemptedCreatorQuery = if (tab == 0) appliedQuery else null
        attemptedWorkParams = if (tab == 1) Triple(appliedQuery, lang, source) else null
        creatorFeed = PagedFeed()
        workFeed = PagedFeed()
        creatorLoadedOnce = false
        workLoadedOnce = false
        load(1, append = false, targetTab = tab)
    }

    val activeFeed: PagedFeed<*> = if (tab == 0) creatorFeed else workFeed
    val total = activeFeed.total ?: 0

    GlassScaffold(
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                GlassTopAppBar(
                    title = "书库",
                    subtitle = if (total > 0) "共 $total 项" else "",
                    navigationIcon = { BackButton(onBack = onBack) },
                    // 透明底色：玻璃底色与模糊由 GlassScaffold 统一绘制
                    color = Color.Transparent,
                    actions = {
                        CollapsibleSearchAction(state = searchCollapse)
                    },
                )
                // 胶囊搜索框：展开态输入，提交即按当前 Tab 检索；滚动时收起，页签与筛选保持吸顶。
                CollapsibleSearchBar(state = searchCollapse) {
                    SearchBar(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        expanded = searchExpanded,
                        onExpandedChange = { searchExpanded = it },
                        insideMargin = DpSize(0.dp, 0.dp),
                        inputField = {
                            GlassSearchInputField(
                                query = query,
                                onQueryChange = { query = it },
                                onSearch = {
                                    appliedQuery = query.trim()
                                    searchExpanded = false
                                },
                                expanded = searchExpanded,
                                onExpandedChange = { searchExpanded = it },
                                interactionSource = searchCollapse.interactionSource,
                                label = if (tab == 0) "搜索创作者" else "搜索作品",
                            )
                        },
                    ) {}
                }
                GlassChipRow(
                    tabs = LIBRARY_TABS,
                    selectedTabIndex = tab,
                    onTabSelected = {
                        selectionHaptic()
                        tab = it
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                )
                // 语言/来源筛选固定在列表之外：与顶栏同处玻璃层，列表从其下方穿过
                if (tab == 1 && !filters.isEmpty) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (filters.languages.isNotEmpty()) {
                            FilterSpinner(
                                title = "语言",
                                options = filters.languages,
                                selected = lang,
                                onSelect = { lang = it },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (filters.sources.isNotEmpty()) {
                            FilterSpinner(
                                title = "来源",
                                options = filters.sources,
                                selected = source,
                                onSelect = { source = it },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        },
        snackbarHost = { GlassSnackbarHost(snackbarHostState) },
        collapsibleSearch = searchCollapse,
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val pageFeed: PagedFeed<*> = if (page == 0) creatorFeed else workFeed
            val pageItemCount = pageFeed.items.size
            val pageTotal = pageFeed.total ?: 0
            val pageTotalPages = libraryPageCount(pageTotal)
            val pageFailure = if (page == 0) creatorFailure else workFailure
            val pageLoadedOnce = if (page == 0) creatorLoadedOnce else workLoadedOnce
            val pageLoading = (if (page == 0) creatorLoading else workLoading) || (!pageLoadedOnce && pageFailure == null)
            val pageListState = if (page == 0) creatorListState else workListState

            if (paginationMode != PaginationMode.CLICK && !pageLoading && pageFailure == null && pageItemCount > 0) {
                LoadMoreOnScroll(
                    listState = pageListState,
                    feed = pageFeed,
                    onLoadMore = { load(pageFeed.page + 1, append = true, targetTab = page) },
                )
            }

            when {
                pageLoading -> LoadingScreen(modifier = Modifier.padding(innerPadding))
                // 失效只能去登录，普通失败才给重试
                pageFailure != null -> {
                    if (pageFailure.phase == ScreenLoadPhase.Expired) {
                        LoginExpiredScreen(
                            onOpenLogin = onOpenLogin,
                            modifier = Modifier.padding(innerPadding),
                        )
                    } else {
                        ErrorScreen(
                            message = pageFailure.message.orEmpty(),
                            onRetry = { load(pageFeed.page.coerceAtLeast(1), append = false, targetTab = page) },
                            modifier = Modifier.padding(innerPadding),
                        )
                    }
                }

                pageItemCount == 0 -> EmptyState(
                    title = if (appliedQuery.isBlank()) {
                        if (page == 0) "暂无创作者" else "暂无作品"
                    } else {
                        "没有找到相关内容"
                    },
                    hint = if (appliedQuery.isBlank()) "换个筛选条件再试试" else "换个关键词再试试",
                    modifier = Modifier.padding(innerPadding),
                )

                else -> GlassPullToRefresh(
                    isRefreshing = refreshingTab == page,
                    onRefresh = {
                        refreshingTab = page
                        load(1, append = false, targetTab = page)
                    },
                    modifier = Modifier.fillMaxSize(),
                    pullToRefreshState = refreshState,
                    // 刷新指示器下移到玻璃顶栏之下，避免被顶栏遮住
                    contentPadding = innerPadding,
                ) {
                    // 首项下移让出顶栏高度，滚动时内容从玻璃下方穿过
                    TrackedLazyColumn(
                        state = pageListState,
                        contentPadding = innerPadding,
                        modifier = Modifier.fillMaxSize().readableWidth().collapsibleSearchScroll(searchCollapse),
                    ) {
                        if (page == 0) {
                            items(creatorFeed.items, key = { it.id }) { creator ->
                                CreatorRow(creator = creator, onClick = { onOpenCreator(creator.id) })
                            }
                        } else {
                            items(
                                items = workFeed.items.chunked(gridColumns),
                                key = { row -> row.joinToString(",") { it.id } },
                            ) { row ->
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(WorkCardTokens.GridSpacing),
                                ) {
                                    row.forEach { work ->
                                        WorkCard(
                                            title = work.title,
                                            coverUrl = work.coverUrl,
                                            platform = work.platform,
                                            authorName = work.authorName,
                                            workDate = work.workDate,
                                            modifier = Modifier.weight(1f),
                                            onClick = { onOpenWork(work.id) },
                                            onOpenAuthor = work.authorId?.let { id ->
                                                { onOpenCreator(id) }
                                            },
                                        )
                                    }
                                    // 末行不足一列时补空位，避免剩余卡片被均分拉宽
                                    repeat(gridColumns - row.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                        item(key = "footer") {
                            LibraryFooter(
                                feed = pageFeed,
                                mode = paginationMode,
                                totalPages = pageTotalPages,
                                pageBusy = if (page == 0) creatorPageBusy else workPageBusy,
                                onLoadMore = { load(pageFeed.page + 1, append = true, targetTab = page) },
                                onPageChange = { target ->
                                    if (target != pageFeed.page) {
                                        load(target, append = false, targetTab = page, showLoading = false, pageJump = true)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 作者主页：背景/头像/赞助入口 + 语言来源筛选 + 作品网格。 */
@Composable
fun CreatorWorksScreen(
    deps: ScreenDeps,
    creatorId: String,
    onBack: () -> Unit,
    onOpenCreator: (String) -> Unit,
    onOpenWork: (String) -> Unit,
    snackbarHostState: SnackbarHostState,
    onOpenLogin: () -> Unit = {},
) {
    var data by remember { mutableStateOf<CreatorAuthorWorks?>(null) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    // 失效与失败共用一个落点：计划自己决定「整屏去登录」还是「给重试按钮」
    var failure by remember { mutableStateOf<ScreenLoadPlan?>(null) }
    var lang by rememberSaveable { mutableStateOf("") }
    var source by rememberSaveable { mutableStateOf("") }
    var loadToken by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val refreshState = rememberPullToRefreshState(refreshThreshold = 0.18f)
    // 作者主页的作品网格与大屏列数一致：手机两列，大屏随窗口补列
    val gridColumns = LocalWideLayout.current.gridColumns

    fun load(showLoading: Boolean) {
        loadToken++
        val token = loadToken
        if (showLoading) loading = true
        failure = null
        val hasContent = data != null
        scope.launch {
            val outcome = deps.authCall.call { deps.gateway.creatorAuthorWorks(creatorId, lang, source, 1) }
            if (token != loadToken) return@launch
            loading = false
            refreshing = false
            when (outcome) {
                is SessionOutcome.Ok -> data = outcome.value
                SessionOutcome.Expired, is SessionOutcome.Failed -> {
                    val plan = planLoad(outcome, hasContent = hasContent, failureMessage = "加载失败")
                    if (plan.keepContent) {
                        snackbarHostState.showSnackbar(plan.notice.orEmpty())
                    } else {
                        failure = plan
                    }
                }
            }
        }
    }

    LaunchedEffect(creatorId, lang, source) {
        load(showLoading = data == null)
    }

    /** 赞助平台：绝对地址直接打开，缺失时提示；失败不崩溃。 */
    fun openSponsor(url: String?) {
        if (url.isNullOrBlank()) {
            scope.launch { snackbarHostState.showSnackbar("该平台暂无链接") }
            return
        }
        openExternal(context, url) {
            scope.launch { snackbarHostState.showSnackbar("无法打开链接") }
        }
    }

    GlassScaffold(
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                GlassTopAppBar(
                    title = data?.authorName?.takeIf { it.isNotBlank() } ?: "创作者",
                    subtitle = data?.works?.size?.takeIf { it > 0 }?.let { "$it 部作品" } ?: "",
                    navigationIcon = { BackButton(onBack = onBack) },
                    // 透明底色：玻璃底色与模糊由 GlassScaffold 统一绘制
                    color = Color.Transparent,
                )
                // 多层级目录：书库 → 创作者，点击「书库」回到上一层
                BreadcrumbBar(
                    items = listOf(
                        BreadcrumbItem(path = "library", text = "书库"),
                        BreadcrumbItem(
                            path = creatorId,
                            text = data?.authorName?.takeIf { it.isNotBlank() } ?: "创作者",
                        ),
                    ),
                    onItemClick = { index -> if (index == 0) onBack() },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                )
            }
        },
        snackbarHost = { GlassSnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when {
            loading -> LoadingScreen(modifier = Modifier.padding(innerPadding))
            // 已有作者页内容时刷新失败不清屏；没有内容才整屏处置
            failure != null && data == null -> {
                val planned = failure
                if (planned?.phase == ScreenLoadPhase.Expired) {
                    LoginExpiredScreen(
                        onOpenLogin = onOpenLogin,
                        modifier = Modifier.padding(innerPadding),
                    )
                } else {
                    ErrorScreen(
                        message = planned?.message.orEmpty(),
                        onRetry = { load(showLoading = true) },
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
            else -> GlassPullToRefresh(
                isRefreshing = refreshing,
                onRefresh = {
                    refreshing = true
                    load(showLoading = false)
                },
                modifier = Modifier.fillMaxSize(),
                pullToRefreshState = refreshState,
                // 刷新指示器下移到玻璃顶栏之下，避免被顶栏遮住
                contentPadding = innerPadding,
            ) {
                val listState = rememberLazyListState()
                // 首项下移让出顶栏高度，滚动时内容从玻璃下方穿过
                TrackedLazyColumn(
                    state = listState,
                    contentPadding = innerPadding,
                    modifier = Modifier.fillMaxSize().readableWidth(),
                ) {
                    item(key = "hero") {
                        val current = data
                        if (current != null) {
                            CreatorHero(author = current)
                        }
                    }
                    items(data?.sponsors.orEmpty(), key = { "sponsor-${it.name}-${it.url}" }) { sponsor ->
                        Button(
                            onClick = { openSponsor(sponsor.url) },
                            colors = ButtonDefaults.buttonColorsPrimary(),
                            modifier = Modifier.fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                        ) {
                            Text(text = "支持作者 → ${sponsor.name}")
                        }
                    }
                    val filterSource = data?.filters
                    if (filterSource != null && !filterSource.isEmpty) {
                        item(key = "filters") {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if (filterSource.languages.isNotEmpty()) {
                                    FilterSpinner(
                                        title = "语言",
                                        options = filterSource.languages,
                                        selected = lang,
                                        onSelect = { lang = it },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                if (filterSource.sources.isNotEmpty()) {
                                    FilterSpinner(
                                        title = "来源",
                                        options = filterSource.sources,
                                        selected = source,
                                        onSelect = { source = it },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                    val works = data?.works.orEmpty()
                    if (works.isEmpty()) {
                        item(key = "empty") {
                            EmptyState(title = "暂无作品", hint = "换个语言或来源再试试")
                        }
                    } else {
                        items(
                            items = works.chunked(gridColumns),
                            key = { row -> row.joinToString(",") { it.id } },
                        ) { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(WorkCardTokens.GridSpacing),
                            ) {
                                row.forEach { work ->
                                    WorkCard(
                                        title = work.title,
                                        coverUrl = work.coverUrl,
                                        platform = work.platform,
                                        authorName = null,
                                        workDate = work.workDate,
                                        modifier = Modifier.weight(1f),
                                        onClick = { onOpenWork(work.id) },
                                        onOpenAuthor = null,
                                    )
                                }
                                repeat(gridColumns - row.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 作品详情：描述 + 整页图片 + 底部「相关作品」悬浮入口。 */
@Composable
fun CreatorWorkDetailScreen(
    deps: ScreenDeps,
    workId: String,
    onBack: () -> Unit,
    onOpenWork: (String) -> Unit,
    snackbarHostState: SnackbarHostState,
    onOpenLogin: () -> Unit = {},
) {
    val skin = LocalSkin.current
    var work by remember { mutableStateOf<CreatorWork?>(null) }
    var loading by remember { mutableStateOf(true) }
    // 失效与失败共用一个落点：计划自己决定「整屏去登录」还是「给重试按钮」
    var failure by remember { mutableStateOf<ScreenLoadPlan?>(null) }
    var relatedOpen by remember { mutableStateOf(false) }
    var loadToken by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    fun load() {
        loadToken++
        val token = loadToken
        loading = true
        failure = null
        scope.launch {
            val outcome = deps.authCall.call { deps.gateway.creatorWork(workId) }
            if (token != loadToken) return@launch
            loading = false
            when (outcome) {
                is SessionOutcome.Ok -> work = outcome.value
                SessionOutcome.Expired -> failure = planLoad(outcome, hasContent = false, failureMessage = "")
                is SessionOutcome.Failed -> failure = planLoad(outcome, hasContent = false, failureMessage = "加载失败")
            }
        }
    }

    LaunchedEffect(workId) { load() }

    val related = work?.related.orEmpty()

    GlassScaffold(
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                GlassTopAppBar(
                    title = work?.title?.takeIf { it.isNotBlank() } ?: "作品",
                    subtitle = work?.authorName.orEmpty(),
                    navigationIcon = { BackButton(onBack = onBack) },
                    // 透明底色：玻璃底色与模糊由 GlassScaffold 统一绘制
                    color = Color.Transparent,
                )
                // 多层级目录：书库 → 作品，点击「书库」回到上一层
                BreadcrumbBar(
                    items = listOf(
                        BreadcrumbItem(path = "library", text = "书库"),
                        BreadcrumbItem(
                            path = workId,
                            text = work?.title?.takeIf { it.isNotBlank() } ?: "作品",
                        ),
                    ),
                    onItemClick = { index -> if (index == 0) onBack() },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                )
            }
        },
        snackbarHost = { GlassSnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when {
            loading -> LoadingScreen(modifier = Modifier.padding(innerPadding))
            // 失效只能去登录，普通失败才给重试
            failure != null -> {
                val planned = failure
                if (planned?.phase == ScreenLoadPhase.Expired) {
                    LoginExpiredScreen(
                        onOpenLogin = onOpenLogin,
                        modifier = Modifier.padding(innerPadding),
                    )
                } else {
                    ErrorScreen(
                        message = planned?.message.orEmpty(),
                        onRetry = ::load,
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
            work == null -> CenteredEmptyState(
                title = "作品不存在",
                hint = "可能已下架",
                modifier = Modifier.padding(innerPadding),
            )
            else -> {
                val body = work!!
                val listContentPadding = remember(innerPadding, related.isNotEmpty()) {
                    val extraBottom = creatorWorkExtraBottomDp(hasRelated = related.isNotEmpty()).dp
                    innerPadding.withSpacing(extraBottom = extraBottom)
                }
                val listState = rememberLazyListState()
                // 首项下移让出顶栏高度，底部按是否存在相关作品悬浮栏预留净空
                TrackedLazyColumn(
                    state = listState,
                    contentPadding = listContentPadding,
                    modifier = Modifier.fillMaxSize().readableWidth(),
                ) {
                    if (body.contentText.isNotBlank()) {
                        item(key = "content") {
                            Text(
                                text = body.contentText,
                                modifier = Modifier.fillMaxWidth()
                                    .padding(horizontal = SectionTextStartPadding, vertical = 12.dp),
                                style = MiuixTheme.textStyles.body2,
                            )
                        }
                    }
                    items(body.images.withIndex().toList(), key = { it.index }) { entry ->
                        LibraryCover(
                            url = entry.value,
                            modifier = Modifier.fillMaxWidth()
                                .padding(horizontal = SectionHorizontalPadding, vertical = 4.dp)
                                .squircleClip(10.dp)
                                .aspectRatio(0.72f),
                            contentScale = ContentScale.Fit,
                        )
                    }
                    if (body.images.isEmpty() && body.contentText.isBlank()) {
                        item(key = "empty") {
                            EmptyState(title = "暂无内容", hint = "该作品还在补充中")
                        }
                    }
                }
            }
        }

        // 底栏原由 Scaffold 的 floatingToolbar 槽承载；GlassScaffold 无该槽，
        // 改为内容层底部居中的覆盖层，位置与原先一致
        if (related.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = innerPadding.calculateBottomPadding() + 4.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                FloatingToolbar {
                    MinTouchTarget(onClick = { relatedOpen = true }) {
                        TextButton(
                            text = "相关作品 ${related.size}",
                            onClick = { relatedOpen = true },
                            minWidth = 0.dp,
                            minHeight = 44.dp,
                        )
                    }
                }
            }
        }

        OverlayBottomSheet(
            show = relatedOpen,
            title = "相关作品",
            cornerRadius = skin.shapes.sheetCorner,
            onDismissRequest = { relatedOpen = false },
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(related, key = { it.id }) { item ->
                        RelatedWorkCard(
                            work = item,
                            onClick = {
                                relatedOpen = false
                                onOpenWork(item.id)
                            },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

/**
 * 无模糊回退的整块暗度：列表入口与详情头图共用，保证不支持模糊的设备进入详情不跳变。
 */
private const val CreatorCoverScrimAlpha = 0.45f

/**
 * 支持模糊时的顶部暗度：底部模糊最强处仍用 [CreatorCoverScrimAlpha]，
 * 往上随模糊强度递减到本值，避免整块暗色把模糊的通透感压掉。
 */
private const val CreatorCoverBlurTopScrimAlpha = 0.18f

/**
 * 封面内头像与文字的水平内衬：列表卡内部与详情头图内部统一为 16dp。
 * 列表卡另有 16dp 外边距，屏幕级起点因此比详情头图多 16dp。
 */
private val CreatorCoverInset = 16.dp

/** 创作者行：背景图 + 暗色蒙版 + 头像 + 名称/更新时间。 */
@Composable
private fun CreatorRow(creator: CreatorMeta, onClick: () -> Unit) {
    val skin = LocalSkin.current
    Card(
        cornerRadius = skin.shapes.cardCorner,
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .pressScale(),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(104.dp).clickable(onClick = onClick),
        ) {
            LibraryCover(
                url = creator.backgroundUrl,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = CreatorCoverScrimAlpha)))
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = CreatorCoverInset),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LibraryCover(
                    url = creator.avatarUrl,
                    modifier = Modifier.size(64.dp).squircleClip(14.dp),
                    contentScale = ContentScale.Crop,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = creator.name.ifBlank { creator.id },
                        style = largeScreenTitleStyle(MiuixTheme.textStyles.title4),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    creator.updateText?.let { update ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "更新于 $update",
                            style = MiuixTheme.textStyles.footnote1,
                            color = Color.White.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 作品卡：封面 + 平台角标 + 标题 + 作者（可跳作者主页）/发布日期。
 *
 * 外观（圆角、封面比例、内边距、字号层级、角标）全部由 [WorkGridCard] 决定，
 * 这里只负责把作品字段映射进去。
 */
@Composable
private fun WorkCard(
    title: String,
    coverUrl: String?,
    platform: String?,
    authorName: String?,
    workDate: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onOpenAuthor: (() -> Unit)?,
) {
    val badgeLabel = platform?.takeIf { it.isNotBlank() }
    WorkGridCard(
        title = title.ifBlank { "无题" },
        coverUrl = coverUrl,
        onClick = onClick,
        modifier = modifier,
        badge = {
            if (badgeLabel != null) {
                WorkCoverBadge(
                    text = badgeLabel,
                    highlighted = true,
                    modifier = Modifier.align(Alignment.TopEnd).padding(WorkCardTokens.BadgeInset),
                )
            }
        },
        footer = {
            if (!authorName.isNullOrBlank() && onOpenAuthor != null) {
                Text(
                    text = authorName,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(top = WorkCardTokens.TextGap)
                        .clickable(onClick = onOpenAuthor),
                )
            }
            workDate?.takeIf { it.isNotBlank() }?.let { date ->
                Text(
                    text = date,
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = WorkCardTokens.TextGap),
                )
            }
        },
    )
}

/** 作者主页头部：背景（底部渐进模糊）+ 头像 + 名称。 */
@Composable
private fun CreatorHero(author: CreatorAuthorWorks) {
    // 背景层作为 backdrop 供底部渐进模糊采样；API 33 以下退回纯色蒙版
    val backdrop = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberLayerBackdrop()
    } else {
        null
    }
    Box(modifier = Modifier.fillMaxWidth().height(150.dp)) {
        LibraryCover(
            url = author.backgroundUrl,
            modifier = Modifier.fillMaxSize().then(
                if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier,
            ),
            contentScale = ContentScale.Crop,
        )
        if (backdrop != null) {
            // 从下往上由实到虚：底部模糊最强处用整块暗度，向上随模糊减弱而变浅，保留模糊的通透感
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .progressiveTextureBlur(
                        backdrop = backdrop,
                        shape = RectangleShape,
                        blurRadius = 26f,
                        gradient = ProgressiveBlur.Bottom,
                    )
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = CreatorCoverBlurTopScrimAlpha),
                            1f to Color.Black.copy(alpha = CreatorCoverScrimAlpha),
                        ),
                    ),
            )
        } else {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = CreatorCoverScrimAlpha)))
        }
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = CreatorCoverInset),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LibraryCover(
                url = author.avatarUrl,
                modifier = Modifier.size(76.dp).squircleClip(18.dp),
                contentScale = ContentScale.Crop,
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = author.authorName.ifBlank { "未知创作者" },
                    style = largeScreenTitleStyle(MiuixTheme.textStyles.title3),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (author.works.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${author.works.size} 部作品",
                        style = MiuixTheme.textStyles.footnote1,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                }
            }
        }
    }
}

/** 相关作品卡：窄版封面 + 标题 + 平台角标，外观走 [WorkGridCard] 的统一契约。 */
@Composable
private fun RelatedWorkCard(work: CreatorRelatedWork, onClick: () -> Unit) {
    val badgeLabel = work.platform?.takeIf { it.isNotBlank() }
    WorkGridCard(
        title = work.title.ifBlank { "无题" },
        coverUrl = work.coverUrl,
        onClick = onClick,
        modifier = Modifier.width(WorkCardTokens.CompactWidth),
        badge = {
            if (badgeLabel != null) {
                WorkCoverBadge(
                    text = badgeLabel,
                    highlighted = true,
                    modifier = Modifier.align(Alignment.TopEnd).padding(WorkCardTokens.BadgeInset),
                )
            }
        },
    )
}

/** 语言/来源筛选：接口下发的选项 + 首项「全部」。 */
@Composable
private fun FilterSpinner(
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val skin = LocalSkin.current
    val all = remember(options) { listOf("全部") + options.distinct() }
    val selectedIndex = all.indexOf(selected).coerceAtLeast(0)
    Card(cornerRadius = skin.shapes.cardCorner, modifier = modifier) {
        OverlaySpinnerPreference(
            items = all.mapIndexed { index, text ->
                DropdownItem(text = text, selected = index == selectedIndex)
            },
            selectedIndex = selectedIndex,
            title = title,
            summary = all.getOrNull(selectedIndex) ?: "全部",
            showValue = false,
            onSelectedIndexChange = { index ->
                onSelect(if (index == 0) "" else all.getOrNull(index).orEmpty())
            },
        )
    }
}

/** 列表底部：点击模式交给 PagerBar，滚动模式交给 LoadMoreFooter，本文件不再自己拼一套。 */
@Composable
private fun LibraryFooter(
    feed: PagedFeed<*>,
    mode: PaginationMode,
    totalPages: Int,
    pageBusy: Boolean,
    onLoadMore: () -> Unit,
    onPageChange: (Int) -> Unit,
) {
    if (feed.items.isEmpty()) return
    if (mode == PaginationMode.CLICK) {
        PagerBar(
            page = feed.page,
            totalPages = totalPages,
            enabled = !pageBusy,
            onPageChange = onPageChange,
        )
    } else {
        LoadMoreFooter(feed = feed, onLoadMore = onLoadMore)
    }
}

/** 总数 → 总页数：书库所有位置的页码换算都走这一份，避免向上取整写错一边。 */
internal fun libraryPageCount(total: Int): Int =
    ((total + LIBRARY_PAGE_SIZE - 1) / LIBRARY_PAGE_SIZE).coerceAtLeast(1)

/** 把书库接口的一页折算成 PagedFeed 切片：末页判据用页码，条目数不参与。 */
private fun <T> librarySlice(items: List<T>, total: Int, page: Int): PageSlice<T> {
    val pageCount = libraryPageCount(total)
    return PageSlice(items = items, hasMore = page < pageCount, total = total, page = page)
}

/** 统一封面占位：缺失时保留同尺寸色块，避免网格跳动。 */
@Composable
private fun LibraryCover(
    url: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    Box(
        modifier = modifier.background(MiuixTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** 外链打开：无匹配 Activity 时回调提示，不抛异常。 */
private fun openExternal(context: Context, url: String, onFailure: () -> Unit) {
    val intent = Intent(Intent.ACTION_VIEW, url.toUri())
    runCatching { context.startActivity(intent) }.onFailure { onFailure() }
}
