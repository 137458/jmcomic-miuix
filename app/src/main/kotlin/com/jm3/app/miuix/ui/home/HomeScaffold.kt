package com.jm3.app.miuix.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.ui.HomeSideData
import com.jm3.app.miuix.ui.ListState
import com.jm3.app.miuix.ui.PaginationModeReloadEffect
import com.jm3.app.miuix.ui.SessionListKey
import com.jm3.app.miuix.ui.list.ListLoadState
import com.jm3.app.miuix.ui.list.SessionListLoader
import com.jm3.app.miuix.ui.list.SessionListSnapshot
import com.jm3.app.miuix.ui.list.toListState
import com.jm3.app.miuix.ui.mutateSearchHistory
import com.jm3.app.miuix.ui.orDegrade
import com.jm3.app.miuix.ui.readSearchHistory
import com.jm3.app.miuix.ui.search.SearchCriteria
import com.jm3.app.miuix.ui.search.SearchInputState
import com.jm3.app.miuix.ui.search.SearchSubmission
import com.jm3.app.miuix.ui.search.clampedPageJumpTarget
import com.jm3.app.miuix.ui.search.couiPageJumpTarget
import com.jm3.app.miuix.ui.search.resolveSearchSubmission
import com.jm3.app.miuix.ui.search.searchListLoader
import com.jm3.app.miuix.ui.search.toSearchPageSlice
import com.jm3.core.model.BrowseCategory
import com.jm3.core.model.BrowseFilter
import com.jm3.core.model.BrowseOrder
import com.jm3.core.model.BrowseTime
import com.jm3.core.model.SearchPage
import com.jm3.core.model.SearchResult
import com.jm3.core.paging.PagedFeed
import com.jm3.core.settings.PaginationMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.SnackbarHostState

/**
 * 首页状态编排骨架的单一真源：Miuix 首页与 COUI 首页共用这一份。
 *
 * 布局、动画、顶栏与滚动容器都不在这里：皮肤只留视觉结构与 slot 传参，
 * 这里只留「输入面状态 + 两个 loader + 侧栏与历史 + 动作口径」。
 *
 * 两块皮肤确有差异的四处都用策略参数表达，每条都对应实测行为，不做强行统一：
 * 搜索 loader 是否常驻并接缓存、跳页校验口径、分页模式切换时重载谁、侧栏并发与榜单轨。
 */

/** 连载更新横滑区最多展示的条目数，避免首屏请求体过大。 */
internal const val SERIAL_CAROUSEL_LIMIT = 12

/** 热门标签折叠时展示的标签数。 */
internal const val HOT_TAG_COLLAPSED = 8

/** 单条横滑轨道最多铺多少张卡。 */
private const val TRACK_CARD_LIMIT = 12

/** 搜索 loader 的记忆方式。 */
internal enum class HomeSearchLoad {
    /** loader 只建一次、条件由持有者给，且接 HomeSearch 缓存：切页回来结果还在。 */
    Sticky,

    /** 条件一变就重建 loader，只吃内存态不接缓存。 */
    Rebuilt,
}

/** 跳页校验口径。 */
internal enum class HomePageJump {
    /** 先夹进总页数，再挡掉同页与「总页数未知又没有下一页」。 */
    Clamped,

    /** 只做合法性校验，通过就按原目标页跳。 */
    CouiGuarded,
}

/** 分页模式切换后重载谁。 */
internal enum class HomePaginationReload {
    /** 搜索态两个 loader 都重取；非搜索态连搜索标记与缓存一起复位。 */
    BothLoaders,

    /** 只重载当前激活的那一个 loader。 */
    ActiveOnly,
}

/** 侧栏数据的取数编排。 */
internal enum class HomeSideLoad {
    /** 三份网关请求按顺序发，再读本地历史。 */
    Sequential,

    /** 四份并发取，互不拖住。 */
    Parallel,
}

/** 换页落地后的归顶判定口径。 */
internal enum class HomeScrollPolicy {
    /** 只有「落地页 == 待滚动页」才归顶。 */
    ExactPage,

    /** 任意一次 Loaded 落地都归顶。 */
    AnyLoaded,
}

/** 两块皮肤的全部差异参数：构造一次，整页生命周期内不变。 */
internal data class HomeScaffoldParams(
    val searchLoad: HomeSearchLoad,
    val jump: HomePageJump,
    val paginationReload: HomePaginationReload,
    val sideLoad: HomeSideLoad,
    val loadRankTrack: Boolean,
    val scrollPolicy: HomeScrollPolicy,
    val searchFailureMessage: String,
    val feedFailureMessage: String,
)

/** 分页模式切换后的重载计划：纯判定，便于单测。 */
internal data class HomeReloadPlan(
    val searchFirstPage: Boolean,
    val feedFirstPage: Boolean,
    val dropSearchCache: Boolean,
)

/** 分页模式切换后要做什么。 */
internal fun homeReloadPlan(searching: Boolean, policy: HomePaginationReload): HomeReloadPlan =
    when (policy) {
        HomePaginationReload.BothLoaders -> if (searching) {
            HomeReloadPlan(searchFirstPage = true, feedFirstPage = true, dropSearchCache = false)
        } else {
            HomeReloadPlan(searchFirstPage = false, feedFirstPage = true, dropSearchCache = true)
        }

        HomePaginationReload.ActiveOnly -> HomeReloadPlan(
            searchFirstPage = searching,
            feedFirstPage = !searching,
            dropSearchCache = false,
        )
    }

/** 跳页目标：null 表示不跳。 */
internal fun homeJumpTarget(feed: PagedFeed<*>, target: Int, policy: HomePageJump): Int? = when (policy) {
    HomePageJump.Clamped -> clampedPageJumpTarget(feed, target, requireMoreWhenUnknown = true)
    HomePageJump.CouiGuarded -> couiPageJumpTarget(feed, target)
}

/** 「随机看一部」的候选池：优先随机推荐，空则退到当前列表。 */
internal fun homeRandomPool(
    randomPicks: List<SearchResult>,
    activeItems: List<SearchResult>,
): List<SearchResult> = randomPicks.ifEmpty { activeItems }

/** 输入面的存档：只存用户看得见、且必须跨进程存活的四项。 */
private val SearchInputSaver: Saver<SearchInputState, Any> = listSaver<SearchInputState, Any>(
    save = { listOf(it.query, it.activeQuery, it.byTag, it.orderValue, it.expanded) },
    restore = { saved ->
        SearchInputState(
            query = saved[0] as String,
            activeQuery = saved[1] as String,
            byTag = saved[2] as Boolean,
            orderValue = saved[3] as String,
            expanded = saved[4] as Boolean,
        )
    },
)

/** 状态槽的集合：与 [HomeScaffoldState] 分开，remember 的分工才好读。 */
internal class HomeScaffoldFields internal constructor(
    val input: MutableState<SearchInputState>,
    val showAllTags: MutableState<Boolean>,
    val showSearchHelp: MutableState<Boolean>,
    /** 常驻 loader 的取数条件持有者：loader 只认这一份条件。 */
    val searchCriteria: MutableState<SearchCriteria>,
    val hotTags: MutableState<List<String>>,
    val serialUpdates: MutableState<List<SearchResult>>,
    val randomPicks: MutableState<List<SearchResult>>,
    val rankPicks: MutableState<List<SearchResult>>,
    val searchHistory: MutableState<List<String>>,
    val pendingScrollPage: MutableState<Int>,
    val pendingScrollTop: MutableState<Boolean>,
)

/**
 * 首页状态编排：可变状态 + 动作口径。
 *
 * 皮肤只读属性、只调动作，不直接改 loaders 与内部标记；所有 Compose 副作用
 * （焦点、键盘、滚动、返回键）都由皮肤或 [rememberHomeScaffoldState] 承担。
 */
@Stable
internal class HomeScaffoldState internal constructor(
    private val deps: ScreenDeps,
    private val scope: CoroutineScope,
    val snackbarHostState: SnackbarHostState,
    private val onDismissInputRef: State<() -> Unit>,
    private val onOpenAlbumRef: State<(String) -> Unit>,
    private val params: HomeScaffoldParams,
    private val fields: HomeScaffoldFields,
    val feedLoader: SessionListLoader<SearchResult>,
    val searchLoader: SessionListLoader<SearchResult>,
    private val feedSnapshotState: State<SessionListSnapshot<SearchResult>>,
    private val searchSnapshotState: State<SessionListSnapshot<SearchResult>>,
) {
    /** 输入框内容。 */
    var query: String
        get() = fields.input.value.query
        set(value) {
            fields.input.value = fields.input.value.typed(value)
        }

    /** 搜索栏展开态。 */
    var searchExpanded: Boolean
        get() = fields.input.value.expanded
        set(value) {
            fields.input.value = fields.input.value.expandedTo(value)
        }

    var showAllTags: Boolean
        get() = fields.showAllTags.value
        set(value) {
            fields.showAllTags.value = value
        }

    var showSearchHelp: Boolean
        get() = fields.showSearchHelp.value
        set(value) {
            fields.showSearchHelp.value = value
        }

    /** 已执行的关键词/标签；空串且无复原结果时展示发现流。 */
    val activeQuery: String get() = fields.input.value.activeQuery

    val searchByTag: Boolean get() = fields.input.value.byTag

    val searching: Boolean get() = fields.input.value.searching

    val order: BrowseOrder get() = fields.input.value.order

    /** 分页模式是否点击翻页：控件形态与自动加载都由它决定。 */
    val clickPaging: Boolean get() = deps.paginationMode == PaginationMode.CLICK

    val hotTags: List<String> get() = fields.hotTags.value

    val serialUpdates: List<SearchResult> get() = fields.serialUpdates.value

    val randomPicks: List<SearchResult> get() = fields.randomPicks.value

    val rankPicks: List<SearchResult> get() = fields.rankPicks.value

    val searchHistory: List<String> get() = fields.searchHistory.value

    /** 发现流的当前快照。 */
    val feedSnapshot: SessionListSnapshot<SearchResult> get() = feedSnapshotState.value

    /** 搜索快照：非搜索态为 null，插件不必自己判两次。 */
    val searchSnapshot: SessionListSnapshot<SearchResult>?
        get() = searchSnapshotState.value.takeIf { searching }

    /** 当前该渲染哪一个列表的快照。 */
    val activeSnapshot: SessionListSnapshot<SearchResult> get() = searchSnapshot ?: feedSnapshot

    /** 渲染层沿用页面既有的 ListState 三态：列表复用件只吃 feed。 */
    val feed: ListState get() = feedSnapshot.toListState()

    val results: ListState? get() = searchSnapshot?.toListState()

    val refreshing: Boolean get() = activeSnapshot.refreshing

    /** 当前列表里可用的 feed：只有 Loaded 落地才有。 */
    val activeFeed: PagedFeed<SearchResult>?
        get() = if (activeSnapshot.state is ListLoadState.Loaded) activeSnapshot.feed else null

    /** 输入框内容变化：清空输入即退出搜索态，回到发现流。 */
    fun editedInput(value: String) {
        fields.input.value = fields.input.value.typed(value)
        if (value.isBlank() && searching) clearSearch()
    }

    /** 执行一次搜索：提交即记录历史，标签检索与排序切换也会把当前关键词前移。 */
    fun runSearch(text: String, byTag: Boolean, sort: BrowseOrder) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val criteria = SearchCriteria(query = trimmed, byTag = byTag, order = sort)
        fields.input.value = fields.input.value.started(
            criteria,
            keepCachedResults = params.searchLoad == HomeSearchLoad.Sticky,
        )
        fields.searchCriteria.value = criteria
        mutateHistory { deps.settings.addSearchHistory(trimmed) }
        // 常驻 loader 的取数条件变了要显式重取；重建型 loader 由皮肤侧的 LaunchedEffect 负责
        if (params.searchLoad == HomeSearchLoad.Sticky) searchLoader.loadFirstPage()
    }

    /** 退出搜索态回到发现流。 */
    fun clearSearch() {
        fields.input.value = fields.input.value.cleared()
        if (params.searchLoad == HomeSearchLoad.Sticky) {
            deps.sessionCache.forget(SessionListKey.HomeSearch)
        }
        // 首屏发现流请求可能被搜索态丢弃；退出搜索时补一次，避免发现流一直停在加载态
        if (feedLoader.state !is ListLoadState.Loaded) feedLoader.loadFirstPage()
    }

    /** 收起搜索输入：只收焦点、落键盘，不动已执行条件。 */
    fun dismissSearchInput() {
        onDismissInputRef.value()
    }

    /** 收起搜索栏并退出搜索态（COUI 口径：返回键与搜索栏收起都走这里）。 */
    fun collapseSearch() {
        fields.input.value = fields.input.value.expandedTo(false)
        onDismissInputRef.value()
        if (searching) clearSearch()
    }

    /** 跳转作品前先收掉搜索入口：焦点不能跟着新页面进栈，否则回来/刷新时键盘会再次弹出。 */
    fun openAlbum(albumId: String) {
        fields.input.value = fields.input.value.expandedTo(false)
        onDismissInputRef.value()
        onOpenAlbumRef.value(albumId)
    }

    /** 提交输入：空输入忽略、纯数字车号直达、其余按当前排序检索。 */
    fun submitSearch() {
        when (val submission = resolveSearchSubmission(query, order)) {
            SearchSubmission.Ignored -> Unit
            is SearchSubmission.OpenAlbum -> openAlbum(submission.albumId)
            is SearchSubmission.Search -> runSearch(
                submission.criteria.query,
                submission.criteria.byTag,
                submission.criteria.order,
            )
        }
    }

    /** 下拉手势保留当前列表：搜索态与发现流各走自己的 loader。 */
    fun refresh() {
        activeLoader().refresh()
    }

    /** 失败页重试：整屏换加载态重取第一页。 */
    fun reload() {
        activeLoader().loadFirstPage()
    }

    fun loadMore() {
        activeLoader().loadMore()
    }

    /** 点击分页：用目标页整页替换当前列表，落地后由皮肤归顶。 */
    fun jumpToPage(target: Int) {
        val current: PagedFeed<SearchResult> = when (params.jump) {
            // COUI 口径只在已有落地结果时允许跳页
            HomePageJump.CouiGuarded -> activeFeed ?: return
            HomePageJump.Clamped -> activeLoader().feed
        }
        val resolved = homeJumpTarget(current, target, params.jump) ?: return
        when (params.scrollPolicy) {
            HomeScrollPolicy.ExactPage -> fields.pendingScrollPage.value = resolved
            HomeScrollPolicy.AnyLoaded -> fields.pendingScrollTop.value = true
        }
        activeLoader().goToPage(resolved)
    }

    /**
     * 列表落地后是否需要归顶：按皮肤口径判定一次并消费掉等待标记。
     *
     * 滚动库不外泄到骨架：皮肤在列表快照变化时调用，返回 true 自己滚 LazyListState。
     */
    fun scrollRequestedAfterLanding(): Boolean {
        val snapshot = activeSnapshot
        val loaded = snapshot.state is ListLoadState.Loaded
        return when (params.scrollPolicy) {
            HomeScrollPolicy.ExactPage -> {
                val page = fields.pendingScrollPage.value
                if (page == 0 || !loaded || snapshot.feed.page != page) {
                    false
                } else {
                    fields.pendingScrollPage.value = 0
                    true
                }
            }

            HomeScrollPolicy.AnyLoaded -> {
                if (!fields.pendingScrollTop.value || !loaded) {
                    false
                } else {
                    fields.pendingScrollTop.value = false
                    true
                }
            }
        }
    }

    /** 随机看一部：优先随机推荐，空则退到当前列表。 */
    fun openRandom() {
        homeRandomPool(randomPicks, activeFeed?.items.orEmpty()).randomOrNull()?.let { openAlbum(it.id) }
    }

    /** 搜索历史读写都在 IO 线程，写入后重新读取，UI 只拿最新快照。 */
    fun mutateHistory(block: suspend () -> Unit) {
        scope.launch {
            fields.searchHistory.value = mutateSearchHistory(deps.settings, { searchHistory }) { block() }
        }
    }

    fun removeHistory(word: String) {
        mutateHistory { deps.settings.removeSearchHistory(word) }
    }

    fun clearHistory() {
        mutateHistory { deps.settings.clearSearchHistory() }
    }

    /** 首屏：发现流与搜索各拉一次；缓存命中时 loader 构造即带内容，这里不再补拉。 */
    fun loadHomeFirstPage() {
        if (feedLoader.state !is ListLoadState.Loaded) feedLoader.loadFirstPage()
        if (searching && searchLoader.state is ListLoadState.Loading) searchLoader.loadFirstPage()
    }

    /** 重建型 loader 的首屏入口：条件一变就换 loader，换完由这里补首屏。 */
    fun loadSearchFirstPage() {
        if (activeQuery.isNotEmpty()) searchLoader.loadFirstPage()
    }

    /** COUI 首屏：发现流单独拉，搜索结果由 [loadSearchFirstPage] 负责。 */
    fun loadFeedFirstPage() {
        if (feedLoader.state !is ListLoadState.Loaded) feedLoader.loadFirstPage()
    }

    /** 分页模式切换后按皮肤口径重载，避免两种翻页语义混用。 */
    fun reloadForPaginationMode() {
        val plan = homeReloadPlan(searching, params.paginationReload)
        if (plan.searchFirstPage) searchLoader.loadFirstPage()
        if (plan.feedFirstPage) feedLoader.loadFirstPage()
        if (plan.dropSearchCache) {
            fields.input.value = fields.input.value.searchDropped()
            deps.sessionCache.forget(SessionListKey.HomeSearch)
        }
    }

    /** 侧栏与搜索历史：四份都是可降级内容，接口失败或会话失效一律留空。 */
    suspend fun loadSide() {
        val sessionCache = deps.sessionCache
        if (!sessionCache.homeSide.isEmpty) {
            // 本地历史读取与网关无关，仍在 IO 线程读取并保留取消传播
            fields.searchHistory.value = readSearchHistory(deps.settings) { searchHistory }
            return
        }
        when (params.sideLoad) {
            HomeSideLoad.Parallel -> coroutineScope {
                val tags = async { deps.authCall.call { deps.gateway.hotTags() }.orDegrade(emptyList()) }
                val picks = async { deps.authCall.call { deps.gateway.randomRecommend() }.orDegrade(emptyList()) }
                val updates = async { fetchSerialUpdates() }
                val history = async { readSearchHistory(deps.settings) { emptyList() } }
                applySide(tags = tags.await(), updates = updates.await(), picks = picks.await())
                fields.searchHistory.value = history.await()
            }

            HomeSideLoad.Sequential -> {
                val tags = deps.authCall.call { deps.gateway.hotTags() }.orDegrade(emptyList())
                val picks = deps.authCall.call { deps.gateway.randomRecommend() }.orDegrade(emptyList())
                val updates = fetchSerialUpdates()
                // 搜索历史是本地 DataStore，不走网关，因此不进 AuthCall
                val history = readSearchHistory(deps.settings) { emptyList() }
                applySide(tags = tags, updates = updates, picks = picks)
                fields.searchHistory.value = history
            }
        }
    }

    /** 榜单轨道：入口是增强项，失败就整条轨道不出现；命中缓存不再请求。 */
    suspend fun loadRankTrack() {
        if (deps.sessionCache.homeSide.rankPicks.isNotEmpty()) return
        val rank = deps.authCall.call {
            deps.gateway.browse(
                BrowseFilter(
                    category = BrowseCategory.ALL,
                    order = BrowseOrder.MOST_LIKED,
                    time = BrowseTime.WEEK,
                ),
                1,
            )
        }.orDegrade<SearchPage?>(null)
        val picks = rank?.items?.take(TRACK_CARD_LIMIT).orEmpty()
        fields.rankPicks.value = picks
        if (picks.isNotEmpty()) deps.sessionCache.homeSide = deps.sessionCache.homeSide.copy(rankPicks = picks)
    }

    private suspend fun fetchSerialUpdates(): List<SearchResult> = deps.authCall
        .call { deps.gateway.serialization(1) }
        .orDegrade<SearchPage?>(null)
        ?.items
        ?.take(SERIAL_CAROUSEL_LIMIT)
        .orEmpty()

    /** 侧栏三份一起到达、一起换：rankPicks 不动，避免与榜单轨道互相覆盖。 */
    private fun applySide(tags: List<String>, updates: List<SearchResult>, picks: List<SearchResult>) {
        fields.hotTags.value = tags
        fields.serialUpdates.value = updates
        fields.randomPicks.value = picks
        deps.sessionCache.homeSide = HomeSideData(tags, updates, picks, rankPicks)
    }

    private fun activeLoader(): SessionListLoader<SearchResult> = if (searching) searchLoader else feedLoader
}

/**
 * 首页骨架入口：负责 remember/rememberSaveable 与 LaunchedEffect 编排。
 *
 * [onDismissInput] 与 [onOpenAlbum] 由皮肤给（焦点清除、键盘收起、路由跳转都不属于状态机），
 * 内部用 rememberUpdatedState 兜住每次重组的新 lambda，状态对象本身只建一次。
 */
@Composable
internal fun rememberHomeScaffoldState(
    deps: ScreenDeps,
    snackbarHostState: SnackbarHostState,
    onDismissInput: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    params: HomeScaffoldParams,
): HomeScaffoldState {
    val scope = rememberCoroutineScope()
    val sessionCache = deps.sessionCache
    val dismissInputRef = rememberUpdatedState(onDismissInput)
    val openAlbumRef = rememberUpdatedState(onOpenAlbum)

    // 缓存里还留着上次搜索结果时，首帧就是搜索态：从详情页返回时结果先出现
    val restoredResults = remember {
        params.searchLoad == HomeSearchLoad.Sticky && sessionCache.hasPage(SessionListKey.HomeSearch)
    }
    // 复原搜索态时，已执行条件是存档里带回来的那一份：常驻 loader 的首份条件要和它一致
    val input = rememberSaveable(stateSaver = SearchInputSaver) {
        mutableStateOf(SearchInputState(restoredResults = restoredResults))
    }
    val showAllTags = rememberSaveable { mutableStateOf(false) }
    val showSearchHelp = rememberSaveable { mutableStateOf(false) }
    val searchCriteria = remember { mutableStateOf(input.value.criteria) }
    val hotTags = remember { mutableStateOf(sessionCache.homeSide.hotTags) }
    val serialUpdates = remember { mutableStateOf(sessionCache.homeSide.serialUpdates) }
    val randomPicks = remember { mutableStateOf(sessionCache.homeSide.randomPicks) }
    val rankPicks = remember { mutableStateOf(sessionCache.homeSide.rankPicks) }
    val searchHistory = remember { mutableStateOf<List<String>>(emptyList()) }
    val pendingScrollPage = remember { mutableIntStateOf(0) }
    val pendingScrollTop = remember { mutableStateOf(false) }
    // 槽位集合本身也要 remember：否则每帧都是新实例，下面以它为键的状态与效应会整轮重启
    val fields = remember(
        input,
        showAllTags,
        showSearchHelp,
        searchCriteria,
        hotTags,
        serialUpdates,
        randomPicks,
        rankPicks,
        searchHistory,
        pendingScrollPage,
        pendingScrollTop,
    ) {
        HomeScaffoldFields(
            input = input,
            showAllTags = showAllTags,
            showSearchHelp = showSearchHelp,
            searchCriteria = searchCriteria,
            hotTags = hotTags,
            serialUpdates = serialUpdates,
            randomPicks = randomPicks,
            rankPicks = rankPicks,
            searchHistory = searchHistory,
            pendingScrollPage = pendingScrollPage,
            pendingScrollTop = pendingScrollTop,
        )
    }

    fun notice(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    val feedLoader = remember {
        SessionListLoader<SearchResult>(
            scope = scope,
            idOf = { it.id },
            failureMessage = params.feedFailureMessage,
            fetchPage = { page -> deps.authCall.call { deps.gateway.latest(page) }.toSearchPageSlice() },
            notice = { message -> notice(message) },
            cache = sessionCache,
            cacheKey = SessionListKey.HomeFeed,
        )
    }
    // 搜索 loader 的两种记忆方式：常驻靠条件持有者逐次刷新，重建靠三个键
    val searchLoader = when (params.searchLoad) {
        HomeSearchLoad.Sticky -> remember {
            searchListLoader(
                scope = scope,
                authCall = deps.authCall,
                gateway = deps.gateway,
                criteria = { fields.searchCriteria.value },
                failureMessage = params.searchFailureMessage,
                notice = { message -> notice(message) },
                cache = sessionCache,
                cacheKey = SessionListKey.HomeSearch,
            )
        }

        HomeSearchLoad.Rebuilt -> {
            val criteria = fields.input.value.criteria
            remember(criteria) {
                searchListLoader(
                    scope = scope,
                    authCall = deps.authCall,
                    gateway = deps.gateway,
                    criteria = { criteria },
                    failureMessage = params.searchFailureMessage,
                    notice = { message -> notice(message) },
                )
            }
        }
    }

    val feedSnapshotState = feedLoader.snapshot.collectAsState()
    val searchSnapshotState = searchLoader.snapshot.collectAsState()

    val state = remember(
        deps,
        scope,
        snackbarHostState,
        params,
        fields,
        feedLoader,
        searchLoader,
        feedSnapshotState,
        searchSnapshotState,
    ) {
        HomeScaffoldState(
            deps = deps,
            scope = scope,
            snackbarHostState = snackbarHostState,
            onDismissInputRef = dismissInputRef,
            onOpenAlbumRef = openAlbumRef,
            params = params,
            fields = fields,
            feedLoader = feedLoader,
            searchLoader = searchLoader,
            feedSnapshotState = feedSnapshotState,
            searchSnapshotState = searchSnapshotState,
        )
    }

    // 首屏编排：常驻口径一次性拉齐，重建口径由 loader 换代驱动
    when (params.searchLoad) {
        HomeSearchLoad.Sticky -> LaunchedEffect(state) {
            state.loadHomeFirstPage()
            state.loadSide()
        }

        HomeSearchLoad.Rebuilt -> {
            LaunchedEffect(state, feedLoader) { state.loadFeedFirstPage() }
            LaunchedEffect(state, searchLoader) { state.loadSearchFirstPage() }
            LaunchedEffect(state) { state.loadSide() }
        }
    }
    if (params.loadRankTrack) {
        LaunchedEffect(state) { state.loadRankTrack() }
    }

    // 分页模式切换后清掉已追加内容并回到第 1 页重取
    PaginationModeReloadEffect(deps.paginationMode) {
        state.reloadForPaginationMode()
    }

    return state
}
