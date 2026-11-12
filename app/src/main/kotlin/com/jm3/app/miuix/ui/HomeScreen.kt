package com.jm3.app.miuix.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.ui.glass.CollapsibleSearchAction
import com.jm3.app.miuix.ui.glass.CollapsibleSearchBar
import com.jm3.app.miuix.ui.glass.GlassFabItem
import com.jm3.app.miuix.ui.glass.GlassFloatingActionBar
import com.jm3.app.miuix.ui.glass.GlassPullToRefresh
import com.jm3.app.miuix.ui.glass.GlassScaffold
import com.jm3.app.miuix.ui.glass.GlassSearchInputField
import com.jm3.app.miuix.ui.glass.GlassTopAppBar
import com.jm3.app.miuix.ui.glass.LocalFloatingBarContentPadding
import com.jm3.app.miuix.ui.glass.LocalGlassBottomBarTokens
import com.jm3.app.miuix.ui.glass.collapsibleSearchScroll
import com.jm3.app.miuix.ui.glass.rememberCollapsibleSearchState
import com.jm3.app.miuix.ui.glass.withSpacing
import com.jm3.app.miuix.ui.skin.LocalSkin
import com.jm3.core.albumCodeOf
import com.jm3.core.appendedBy
import com.jm3.core.model.BrowseOrder
import com.jm3.core.model.SearchPage
import com.jm3.core.model.SearchResult
import com.jm3.core.model.SearchScope
import com.jm3.core.paging.PagedFeed
import com.jm3.core.paging.pagerTargetPage
import com.jm3.core.session.SessionOutcome
import com.jm3.core.settings.PaginationMode
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TooltipBox
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Close
import top.yukonga.miuix.kmp.icon.extended.Back
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

// 列表三态与会话缓存统一在 SessionCache.kt 定义；结局到列表状态的翻译在 ListOutcome.kt

/** 首页：固定搜索栏 + 发现流（连载更新/热门标签/随便看看/最新上架）或搜索结果。 */
@Composable
fun HomeScreen(
    deps: ScreenDeps,
    onOpenAlbum: (String) -> Unit,
    onOpenWeek: () -> Unit,
    onOpenLogin: () -> Unit = {},
) {
    val skin = LocalSkin.current
    var query by rememberSaveable { mutableStateOf("") }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    // 已执行的关键词/标签；空串且未执行时展示发现流，rememberSaveable 保证从详情页返回时保留
    var activeQuery by rememberSaveable { mutableStateOf("") }
    var searchByTag by rememberSaveable { mutableStateOf(false) }
    var orderValue by rememberSaveable { mutableStateOf(BrowseOrder.LATEST.apiValue) }
    // 页面当前的列表状态（含失败态）由 Composable 持有；会话缓存只留加载成功的结果，供重建后恢复。
    val sessionCache = deps.sessionCache
    var feed by remember { mutableStateOf(sessionCache.stateOf(SessionListKey.HomeFeed)) }
    var results by remember { mutableStateOf(sessionCache.stateOrNull(SessionListKey.HomeSearch)) }
    var hotTags by remember { mutableStateOf(sessionCache.homeSide.hotTags) }
    var serialUpdates by remember { mutableStateOf(sessionCache.homeSide.serialUpdates) }
    var randomPicks by remember { mutableStateOf(sessionCache.homeSide.randomPicks) }
    var searchHistory by remember { mutableStateOf<List<String>>(emptyList()) }
    var showAllTags by rememberSaveable { mutableStateOf(false) }
    var showSearchHelp by rememberSaveable { mutableStateOf(false) }
    // 仅下拉手势触发的刷新使用顶部指示器；普通加载由列表内进度条表达。
    var refreshing by remember { mutableStateOf(false) }
    // 列表代次：切换搜索/刷新时递增，旧请求的响应只在代次未变时落地。
    var listToken by remember { mutableIntStateOf(0) }
    // 下拉阻尼阈值调低：首页内容多，轻拉即可触发刷新
    val refreshState = rememberPullToRefreshState(refreshThreshold = 0.18f)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    // 搜索栏折叠与顶栏折叠各自独立：这里只跟随列表已消费的滚动位移，不改动顶栏行为。
    val searchCollapse = rememberCollapsibleSearchState(listState = listState)
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    val order = BrowseOrder.entries.firstOrNull { it.apiValue == orderValue } ?: BrowseOrder.LATEST
    // 分页模式来自设置域的可观察状态：设置页切换后立即变更控件形态，并触发第一页重载。
    val mode = deps.paging.paginationMode
    val clickPaging = mode == PaginationMode.CLICK
    val searching = isShowingSearchResults(activeQuery, results)

    /** 列表状态与会话缓存一起改：只改其一，切走再回来就会先闪上一份结果。 */
    fun rememberFeed(next: ListState) {
        feed = next
        sessionCache.remember(SessionListKey.HomeFeed, next)
    }

    fun rememberResults(next: ListState) {
        results = next
        sessionCache.remember(SessionListKey.HomeSearch, next)
    }

    /** 侧栏三份数据一起到达，一起换。 */
    fun rememberSide(tags: List<String>, updates: List<SearchResult>, picks: List<SearchResult>) {
        hotTags = tags
        serialUpdates = updates
        randomPicks = picks
        sessionCache.homeSide = HomeSideData(tags, updates, picks)
    }

    fun requestSearch(page: Int, byTag: Boolean, text: String, sort: BrowseOrder): SearchPage =
        deps.gateway.search(text, page, sort, if (byTag) SearchScope.TAG else SearchScope.SITE)

    /** 搜索历史读写都在 IO 线程，写入后重新读取，UI 只拿最新快照。 */
    fun mutateHistory(block: suspend () -> Unit) {
        scope.launch {
            searchHistory = mutateSearchHistory(deps.settings, { searchHistory }) { block() }
        }
    }

    fun loadFeed(page: Int, token: Int) {
        scope.launch {
            // 等待会话恢复、切 IO、判失效都在 AuthCall 里完成，这里只关心结局。
            val next = deps.authCall.call { deps.gateway.latest(page) }
            if (token != listToken) return@launch
            rememberFeed(next.asListState("加载失败"))
        }
    }

    fun runSearch(text: String, byTag: Boolean, sort: BrowseOrder) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        refreshing = false
        activeQuery = trimmed
        searchByTag = byTag
        orderValue = sort.apiValue
        listToken++
        val token = listToken
        rememberResults(ListState.Loading)
        // 提交即记录：标签检索与排序切换也会把当前关键词前移。
        mutateHistory { deps.settings.addSearchHistory(trimmed) }
        scope.launch {
            val next = deps.authCall.call { requestSearch(1, byTag, trimmed, sort) }
            if (token != listToken) return@launch
            rememberResults(next.asListState("网络错误"))
        }
    }

    fun clearSearch() {
        refreshing = false
        query = ""
        activeQuery = ""
        results = null
        sessionCache.forget(SessionListKey.HomeSearch)
        searchByTag = false
        listToken++
        // 首屏发现流请求可能被搜索代次丢弃；退出搜索时补一次，避免发现流一直停在加载态
        if (feed !is ListState.Loaded) loadFeed(1, listToken)
    }

    // 处于搜索态时拦截系统返回键，退回主页发现流而非直接退出应用
    BackHandler(enabled = searching) {
        clearSearch()
    }

    /**
     * 收起搜索输入：清焦点、落键盘、退出展开态。
     * 只藏键盘不清焦点的话，任何一次状态刷新都会让输入框把键盘再拉回来。
     */
    fun dismissSearchInput() {
        focusManager.clearFocus()
        keyboard?.hide()
        searchExpanded = false
    }

    /** 跳转作品前先收掉搜索入口：焦点不能跟着新页面进栈，否则回来/刷新时键盘会再次弹出。 */
    fun openAlbum(albumId: String) {
        dismissSearchInput()
        onOpenAlbum(albumId)
    }

    fun submitSearch() {
        val q = query.trim()
        val code = albumCodeOf(q)
        when {
            q.isEmpty() -> Unit
            // 纯数字默认就是车号，和搜索栏既有的车号直达走同一套判定
            code != null -> openAlbum(code)
            else -> runSearch(q, byTag = false, sort = order)
        }
    }

    fun refresh(byPull: Boolean = false) {
        refreshing = byPull
        listToken++
        val token = listToken
        scope.launch {
            if (searching) {
                val text = activeQuery
                val byTag = searchByTag
                val next = deps.authCall.call { requestSearch(1, byTag, text, order) }
                if (token == listToken) {
                    rememberResults(next.asListState("网络错误"))
                    refreshing = false
                }
            } else {
                val next = deps.authCall.call { deps.gateway.latest(1) }
                if (token == listToken) {
                    rememberFeed(next.asListState("加载失败"))
                    refreshing = false
                }
            }
        }
    }

    /** 当前展示列表的分页状态：搜索态看搜索结果，否则看发现流。 */
    fun activeFeed(): PagedFeed<SearchResult>? =
        ((if (searching) results else feed) as? ListState.Loaded)?.feed

    /** 只改页面状态、不写会话缓存：在途标记属于这一次请求，不该留在缓存里。 */
    fun publishTransient(next: ListState) {
        if (searching) results = next else feed = next
    }

    fun loadMore() {
        val current = activeFeed() ?: return
        // 还能不能拉、在不在拉、上次是否失败，全由 PagedFeed 判定
        val requested = current.beginLoadMore()
        if (requested === current) return
        val token = listToken
        val nextOrder = order
        val text = activeQuery
        val byTag = searchByTag
        publishTransient(ListState.Loaded(requested))
        scope.launch {
            val next = deps.authCall.call {
                if (searching) requestSearch(requested.page + 1, byTag, text, nextOrder)
                else deps.gateway.latest(requested.page + 1)
            }
            if (token != listToken) return@launch
            val updated = when (next) {
                is SessionOutcome.Ok -> requested.appendedBy(next.value)
                else -> requested.failLoadMore()
            }
            if (searching) rememberResults(ListState.Loaded(updated)) else rememberFeed(ListState.Loaded(updated))
        }
    }

    /** 点击分页：用目标页整页替换当前列表，成功后回到列表顶部。 */
    fun jumpToPage(target: Int) {
        val current = activeFeed() ?: return
        val resolved = pagerTargetPage(current.page, target, current.pageCount)
        if (resolved == current.page) return
        if (current.pageCount == null && resolved > current.page && !current.hasMore) return
        refreshing = false
        val text = activeQuery
        val byTag = searchByTag
        val nextOrder = order
        listToken++
        val token = listToken
        if (searching) {
            rememberResults(ListState.Loading)
        } else {
            rememberFeed(ListState.Loading)
        }
        scope.launch {
            val next = deps.authCall.call {
                if (searching) requestSearch(resolved, byTag, text, nextOrder)
                else deps.gateway.latest(resolved)
            }
            if (token != listToken) return@launch
            val mapped = next.asListState("网络错误")
            if (searching) rememberResults(mapped) else rememberFeed(mapped)
            if (mapped is ListState.Loaded) listState.scrollToItem(0)
        }
    }

    // 首屏：最新上架 + 连载更新 + 热门标签 + 随机推荐 + 搜索历史并行加载；若处于搜索态且无缓存结果则自动拉取搜索。
    LaunchedEffect(deps.gateway, deps.authCall) {
        if (!searching && feed !is ListState.Loaded) {
            listToken++
            loadFeed(1, listToken)
        }
        if (searching && results == null) {
            runSearch(activeQuery, byTag = searchByTag, sort = order)
        }
        coroutineScope {
            if (sessionCache.homeSide.isEmpty) {
                launch {
                    // 三份侧栏数据都是可降级内容：接口失败或会话失效一律留空，主列表与整屏错误态不受影响。
                    val tags = deps.authCall.call { deps.gateway.hotTags() }.orDegrade(emptyList())
                    val picks = deps.authCall.call { deps.gateway.randomRecommend() }.orDegrade(emptyList())
                    val updates = deps.authCall
                        .call { deps.gateway.serialization(1) }
                        .orDegrade<SearchPage?>(null)
                        ?.items
                        ?.take(SERIAL_CAROUSEL_LIMIT)
                        .orEmpty()
                    // 搜索历史是本地 DataStore，不走网关，因此不进 AuthCall，直接留在 IO 线程读。
                    val storedHistory = readSearchHistory(deps.settings) { emptyList() }
                    rememberSide(tags = tags, updates = updates, picks = picks)
                    searchHistory = storedHistory
                }
            } else {
                launch {
                    // 同上：本地历史读取与网关无关，仍在 IO 线程读取并保留取消传播。
                    searchHistory = readSearchHistory(deps.settings) { searchHistory }
                }
            }
        }
    }

    // 分页模式切换后清掉已追加内容并回到第 1 页重取，避免两种翻页语义混用；非激活列表同步复位。
    PaginationModeReloadEffect(mode) {
        if (searching) {
            rememberResults(ListState.Loading)
            rememberFeed(ListState.Loading)
        } else {
            rememberFeed(ListState.Loading)
            results = null
            sessionCache.forget(SessionListKey.HomeSearch)
        }
        refresh()
    }

    // 接近列表尾部时自动加载下一页；点击分页模式下改由页码控件驱动。
    val pagingFeed = activeFeed()
    if (!clickPaging && pagingFeed != null) {
        LoadMoreOnScroll(listState = listState, feed = pagingFeed, onLoadMore = ::loadMore)
    }

    // 与原版一致：列表滚动后出现悬浮操作（随机一部/回到顶部）
    val showActions by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 100 }
    }

    // 悬浮操作栏要让开底栏胶囊。底边距为「8dp + 导航条 inset」，而页面内读到的 inset
    // 已被主框架消费为 0，算式无法直接换算；按手势导航真机实测标定：底栏高度 + 21dp
    // 即得 16dp 视觉间距。底栏档位切换后高度变化会自动跟随。
    val bottomBarTokens = LocalGlassBottomBarTokens.current
    val floatingBarContentPadding = LocalFloatingBarContentPadding.current
    val floatingActionsBottom = if (floatingBarContentPadding == 0.dp) {
        // 宽屏改用侧栏、没有悬浮底栏，直接贴右下角
        16.dp
    } else {
        bottomBarTokens.height + 21.dp
    }

    fun openRandom() {
        val candidates = randomPicks.ifEmpty { activeFeed()?.items.orEmpty() }
        candidates.randomOrNull()?.let { openAlbum(it.id) }
    }

    val selectionHaptic = rememberSelectionHaptic()

    GlassScaffold(
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                GlassTopAppBar(
                    title = "JM3 Miuix",
                    // 透明底色：玻璃底色与模糊由 GlassScaffold 统一绘制
                    color = Color.Transparent,
                    actions = {
                        CollapsibleSearchAction(state = searchCollapse)
                        TooltipBox(text = "每周必看") {
                            IconButton(onClick = onOpenWeek) {
                                Icon(imageVector = MiuixIcons.Weeks, contentDescription = "每周必看")
                            }
                        }
                    },
                )
                // 搜索栏固定于列表之外：与顶栏同处玻璃层；向下滚动内容时收起，向上滚动回展。
                CollapsibleSearchBar(state = searchCollapse) {
                    SearchBar(
                        insideMargin = DpSize(16.dp, 0.dp),
                        inputField = {
                            GlassSearchInputField(
                                query = query,
                                onQueryChange = {
                                    query = it
                                    // 清空输入即退出搜索态，回到发现流
                                    if (it.isBlank() && searching) clearSearch()
                                },
                                onSearch = { submitSearch() },
                                expanded = searchExpanded,
                                onExpandedChange = { searchExpanded = it },
                                interactionSource = searchCollapse.interactionSource,
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
                                                    if (searching) clearSearch()
                                                },
                                                minWidth = 44.dp,
                                                minHeight = 44.dp,
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
                                            minWidth = 44.dp,
                                            minHeight = 44.dp,
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
                }
                // 搜索结果排序常驻玻璃顶栏：与内容同处玻璃采样区，列表滚动时背景随之实时更新
                if (searching) {
                    GlassChipRow(
                        tabs = BrowseOrder.entries.map { it.label },
                        selectedTabIndex = BrowseOrder.entries.indexOf(order),
                        onTabSelected = {
                            BrowseOrder.entries.getOrNull(it)?.let { selected ->
                                selectionHaptic()
                                runSearch(activeQuery, searchByTag, selected)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    )
                }
            }
        },
        floatingActionButton = {
            if (showActions) {
                GlassFloatingActionBar(
                    items = listOf(
                        GlassFabItem(
                            icon = MiuixIcons.Promotions,
                            contentDescription = "随机看一部",
                            onClick = ::openRandom,
                        ),
                        GlassFabItem(
                            // 库里没有现成的向上箭头，用标准返回箭头顺时针转 90° 得到有箭杆的「↑」
                            icon = MiuixIcons.Back,
                            contentDescription = "回到顶部",
                            onClick = { scope.launch { listState.animateScrollToItem(0) } },
                            rotationDegrees = 90f,
                        ),
                    ),
                    // 右下角摆放，整体上移让开悬浮底栏；右边距与底栏胶囊的 24dp 对齐
                    modifier = Modifier.padding(end = 12.dp, bottom = floatingActionsBottom),
                )
            }
        },
        collapsibleSearch = searchCollapse,
    ) { innerPadding ->
        val listContentPadding = remember(innerPadding, floatingBarContentPadding, floatingActionsBottom) {
            val extraBottom = glassHomeExtraBottomDp(
                hasActions = true,
                floatingBarContentPaddingDp = floatingBarContentPadding.value,
                floatingActionsBottomDp = floatingActionsBottom.value,
            ).dp
            innerPadding.withSpacing(extraBottom = extraBottom)
        }
        // 列表内容滚到玻璃顶栏（顶栏 + 搜索栏）下方，顶栏才能采样到真实背景
        GlassPullToRefresh(
            isRefreshing = refreshing,
            onRefresh = { refresh(byPull = true) },
            modifier = Modifier.fillMaxSize(),
            pullToRefreshState = refreshState,
            // 刷新指示器下移到玻璃顶栏之下，避免被顶栏遮住
            contentPadding = innerPadding,
        ) {
            // 大屏行卡两列：列数由 WideLayout.feedColumns 判定，手机断点仍是单列
            val feedColumns = LocalWideLayout.current.feedColumns
            TrackedLazyColumn(
                state = listState,
                // 首项下移让出顶栏高度，底部额外让出悬浮操作胶囊高度
                contentPadding = listContentPadding,
                modifier = Modifier.fillMaxSize().readableWidth().collapsibleSearchScroll(searchCollapse),
            ) {
                if (searching) {
                    item(key = "search-header") {
                        SearchResultHeader(
                            query = activeQuery,
                            byTag = searchByTag,
                            total = (results as? ListState.Loaded)?.feed?.total ?: 0,
                        )
                    }
                    when (val s = results) {
                        null, is ListState.Loading -> if (!refreshing) {
                            item(key = "search-loading") { InlineLoading() }
                        }
                        is ListState.Failed -> item(key = "search-error") {
                            ScreenFailureOrLogin(
                                message = s.message,
                                expired = s.sessionExpired,
                                onRetry = { runSearch(activeQuery, searchByTag, order) },
                                onOpenLogin = onOpenLogin,
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                            )
                        }
                        is ListState.Loaded -> resultItems(
                            feed = s.feed,
                            clickPaging = clickPaging,
                            onLoadMore = ::loadMore,
                            onPageChange = ::jumpToPage,
                            onOpenAlbum = { openAlbum(it) },
                            columns = feedColumns,
                        )
                    }
                } else {
                    // 附属区块（搜索记录/连载更新/热门标签/随便看看）的数据是异步到达的，
                    // 这里合并成单个 item：若各自作为独立 item 插到列表头部，LazyColumn 会按 key
                    // 锚定原首个可见项「最新上架」，首页打开后就不是真正的顶部了。
                    item(key = "discovery-header") {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // 仅当搜索框为空且未进入搜索态时展示搜索记录
                            if (query.isBlank() && searchHistory.isNotEmpty()) {
                                SearchHistoryCard(
                                    history = searchHistory,
                                    onPick = { word ->
                                        query = word
                                        runSearch(word, byTag = false, sort = order)
                                    },
                                    onRemove = { word ->
                                        mutateHistory { deps.settings.removeSearchHistory(word) }
                                    },
                                    onClear = {
                                        mutateHistory { deps.settings.clearSearchHistory() }
                                    },
                                )
                            }
                            if (serialUpdates.isNotEmpty()) {
                                SmallTitle(text = "连载更新")
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = SectionHorizontalPadding),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    items(serialUpdates, key = { it.id }) { update ->
                                        SerialUpdateCard(update = update, onClick = { openAlbum(update.id) })
                                    }
                                }
                            }
                            if (hotTags.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    SmallTitle(text = "热门标签", modifier = Modifier.weight(1f))
                                    if (hotTags.size > HOT_TAG_COLLAPSED) {
                                        MinTouchTarget(
                                            onClick = { showAllTags = !showAllTags },
                                            modifier = Modifier.padding(end = 16.dp),
                                        ) {
                                            TextButton(
                                                text = if (showAllTags) "收起" else "更多",
                                                onClick = { showAllTags = !showAllTags },
                                                minWidth = 0.dp,
                                                minHeight = 36.dp,
                                                insideMargin = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                            )
                                        }
                                    }
                                }
                                // 卡内 FlowRow 自带 12dp：外缘保持 16dp，标签胶囊与标题同在 28dp
                                Card(cornerRadius = skin.shapes.cardCorner, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
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
                            if (randomPicks.isNotEmpty()) {
                                SmallTitle(text = "随便看看")
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = SectionHorizontalPadding),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    items(randomPicks, key = { it.id }) { pick ->
                                        RandomPickCard(pick = pick, onClick = { openAlbum(pick.id) })
                                    }
                                }
                            }
                        }
                    }
                    item(key = "latest-title") { SmallTitle(text = "最新上架") }
                    when (val s = feed) {
                        is ListState.Loading -> if (!refreshing) {
                            item(key = "feed-loading") { InlineLoading() }
                        }
                        is ListState.Failed -> item(key = "feed-error") {
                            ScreenFailureOrLogin(
                                message = s.message,
                                expired = s.sessionExpired,
                                onRetry = { refresh() },
                                onOpenLogin = onOpenLogin,
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                            )
                        }
                        is ListState.Loaded -> resultItems(
                            feed = s.feed,
                            clickPaging = clickPaging,
                            onLoadMore = ::loadMore,
                            onPageChange = ::jumpToPage,
                            onOpenAlbum = { openAlbum(it) },
                            columns = feedColumns,
                        )
                    }
                }
            }
        }

        // 原版搜索语法帮助：空格=同时包含，+ 必须包含，- 排除；按钮沿用 HyperOS 纵向全宽惯例。
        OverlayDialog(
            show = showSearchHelp,
            maxWidth = overlayDialogMaxWidth(),
            title = "搜索语法",
            cornerRadius = skin.shapes.dialogCorner,
            summary = "用空格与 +/- 组合关键词，可缩小搜索范围。",
            onDismissRequest = { showSearchHelp = false },
        ) {
            Card(cornerRadius = skin.shapes.cardCorner, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                BasicComponent(title = "全彩 人妻", summary = "同时包含这两个关键词（空格分隔）")
                BasicComponent(title = "全彩 +人妻", summary = "两个关键词都必须包含")
                BasicComponent(title = "全彩 -人妻", summary = "包含「全彩」，排除「人妻」")
            }
            // 水平内边距由弹窗自带，这里只补垂直间距，避免左右变成 48dp
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
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
    val skin = LocalSkin.current
    Card(cornerRadius = skin.shapes.cardCorner, modifier = Modifier.fillMaxWidth().padding(horizontal = SectionHorizontalPadding, vertical = 4.dp)) {
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
            MinTouchTarget(onClick = onClear) {
                TextButton(
                    text = "清除",
                    onClick = onClear,
                    minWidth = 0.dp,
                    minHeight = 36.dp,
                    insideMargin = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                )
            }
        }
        history.forEach { word ->
            BasicComponent(
                title = word,
                onClick = { onPick(word) },
                endActions = {
                    IconButton(
                        onClick = { onRemove(word) },
                        minWidth = 48.dp,
                        minHeight = 48.dp,
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
            MinTouchTarget(onClick = { onClick(tag) }) {
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
}

/** 连载更新卡：封面 + 标题 + 作者，横向滑动浏览；外观走 [WorkGridCard] 的统一契约。 */
@Composable
private fun SerialUpdateCard(update: SearchResult, onClick: () -> Unit) {
    WorkGridCard(
        title = update.title,
        coverUrl = update.coverUrl,
        onClick = onClick,
        albumId = update.id,
        coverContentDescription = update.title,
        subtitle = update.authors.joinToString(" / ").ifBlank { "未知作者" },
        category = update.category,
        modifier = Modifier.width(WorkCardTokens.CompactWidth),
        badge = { WorkAlbumCodeBadge(update.id) },
        footer = { WorkTagLine(update.tags) },
    )
}

/** 搜索结果摘要：关键词/结果数；排序已常驻玻璃顶栏。 */
@Composable
private fun SearchResultHeader(
    query: String,
    byTag: Boolean,
    total: Int,
) {
    Text(
        text = (if (byTag) "标签：$query" else "“$query”") + if (total > 0) " · 共 $total 结果" else "",
        style = MiuixTheme.textStyles.footnote1,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SectionTextStartPadding, vertical = 12.dp),
    )
}

@Composable
private fun InlineLoading() {
    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        InfiniteProgressIndicator()
    }
}

/** 随机推荐卡：封面 + 两行标题，横向滑动浏览；外观走 [WorkGridCard] 的统一契约。 */
@Composable
private fun RandomPickCard(pick: SearchResult, onClick: () -> Unit) {
    // 行高缺省时会按字形最大度量排版：日文假名、emoji 能让个别标题每行高出一点，
    // 同排卡片便出现毫厘之差。三行文字都按字号折算固定行高，整卡高度只由行数决定。
    val titleLineHeight = largeScreenTitleStyle(MiuixTheme.textStyles.body2).pinnedLineHeight()
    val subtitleLineHeight = MiuixTheme.textStyles.footnote1.pinnedLineHeight()
    val tagLineHeight = MiuixTheme.textStyles.footnote2.pinnedLineHeight()
    WorkGridCard(
        title = pick.title,
        coverUrl = pick.coverUrl,
        onClick = onClick,
        albumId = pick.id,
        coverContentDescription = pick.title,
        // 缺作者/缺标签也占住对应行：随机推荐是等高卡片，少一行就会比同排卡片矮
        subtitle = pick.authors.joinToString(" / ").ifBlank { "未知作者" },
        category = pick.category,
        titleLineHeight = titleLineHeight,
        subtitleLineHeight = subtitleLineHeight,
        modifier = Modifier.width(WorkCardTokens.CompactWidth),
        badge = { WorkAlbumCodeBadge(pick.id) },
        footer = { WorkTagLine(pick.tags, reserveLine = true, lineHeight = tagLineHeight) },
    )
}
