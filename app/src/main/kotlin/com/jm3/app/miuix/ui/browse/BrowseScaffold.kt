package com.jm3.app.miuix.ui.browse

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.ui.BrowseMode
import com.jm3.app.miuix.ui.BrowsePageKey
import com.jm3.app.miuix.ui.ListState
import com.jm3.app.miuix.ui.PaginationModeReloadEffect
import com.jm3.app.miuix.ui.SessionListKey
import com.jm3.app.miuix.ui.list.ListLoadState
import com.jm3.app.miuix.ui.list.SessionListLoader
import com.jm3.app.miuix.ui.list.SessionListSnapshot
import com.jm3.app.miuix.ui.list.toCouiPageSlice
import com.jm3.app.miuix.ui.list.toListState
import com.jm3.app.miuix.ui.normalizeBrowseTagKeyword
import com.jm3.app.miuix.ui.orDegrade
import com.jm3.core.model.BrowseCategory
import com.jm3.core.model.BrowseFilter
import com.jm3.core.model.BrowseOrder
import com.jm3.core.model.BrowseTime
import com.jm3.core.model.CategoryTree
import com.jm3.core.model.SearchResult
import com.jm3.core.paging.pagerTargetPage
import com.jm3.core.session.SessionOutcome
import com.jm3.core.settings.PaginationMode
import kotlinx.coroutines.CoroutineScope

/*
 * 浏览页状态编排骨架（C4 双皮肤去重）。
 *
 * 抽出来的是行为：筛选口径与还原、缓存键与请求口径的换算、主列表 loader 的构造与首屏补拉、
 * 下拉刷新/触底加载/点击换页、换页落地后的归顶时机、标签检索词的进出，
 * 以及分类树与热门标签这两个可选区块的降级口径。
 * 皮肤侧只留视觉结构：顶栏与筛选条、结果行/网格、周更区块、标签结果视图、弹窗与手势；
 * 本文件不引用任何 Miuix/COUI 控件。
 *
 * 两皮肤的真实差异用参数与字段表达，不强行统一：
 * - 失败文案（HyperOS「加载失败」/ ColorOS 取 CouiStrings）由 [rememberBrowseScaffoldState] 的 failureMessage 传入；
 * - 归顶动作不同（列表/瀑布流瞬时归顶 vs 列表动画归顶）由 onScrollToTop 传入；
 * - 标签视图等皮肤自有的重载，各自挂在皮肤自己的效果里；
 * - 周更区块（两边机制不同）、标签结果的呈现方式（ColorOS 同页视图 / HyperOS 独立子屏）、
 *   榜单续号与其余弹窗手势，都留在各自皮肤。
 */

/** 三个筛选口径的当前值；字符串形态与 Saveable 存的是同一份 apiValue。 */
internal data class BrowseFilters(
    val categoryValue: String = BrowseCategory.ALL.apiValue,
    val orderValue: String = BrowseOrder.LATEST.apiValue,
    val timeValue: String = BrowseTime.ALL.apiValue,
) {
    /** 还原值可能来自旧版本或被裁掉的枚举项：认不出来就退回默认口径。 */
    val category: BrowseCategory
        get() = BrowseCategory.entries.firstOrNull { it.apiValue == categoryValue } ?: BrowseCategory.ALL
    val order: BrowseOrder
        get() = BrowseOrder.entries.firstOrNull { it.apiValue == orderValue } ?: BrowseOrder.LATEST
    val time: BrowseTime
        get() = BrowseTime.entries.firstOrNull { it.apiValue == timeValue } ?: BrowseTime.ALL

    /** 缓存键带上筛选口径：不同口径各存一份，切回页签才不会先闪一份别人的列表。 */
    fun pageKey(mode: BrowseMode): BrowsePageKey = BrowsePageKey(mode, categoryValue, orderValue, timeValue)

    /** 请求口径：分类只认分类 + 排序，榜单只认时间 + 排序（与两皮肤原本的内联判据同义）。 */
    fun filter(mode: BrowseMode): BrowseFilter = when (mode) {
        BrowseMode.CATEGORY -> BrowseFilter(category = category, order = order, time = BrowseTime.ALL)
        BrowseMode.RANK -> BrowseFilter(category = BrowseCategory.ALL, order = order, time = time)
    }
}

/** 需要跨重建保留的页面状态：三个筛选值与标签检索词。 */
internal class BrowseScaffoldFields internal constructor(
    val categoryValue: MutableState<String>,
    val orderValue: MutableState<String>,
    val timeValue: MutableState<String>,
    val tagKeyword: MutableState<String?>,
) {
    val filters: BrowseFilters
        get() = BrowseFilters(categoryValue.value, orderValue.value, timeValue.value)
}

/** 三个筛选值与标签检索词统一走 rememberSaveable：进程重建后仍停在原口径与标签视图。 */
@Composable
internal fun rememberBrowseScaffoldFields(): BrowseScaffoldFields {
    val categoryValue = rememberSaveable { mutableStateOf(BrowseCategory.ALL.apiValue) }
    val orderValue = rememberSaveable { mutableStateOf(BrowseOrder.LATEST.apiValue) }
    val timeValue = rememberSaveable { mutableStateOf(BrowseTime.ALL.apiValue) }
    val tagKeyword = rememberSaveable { mutableStateOf<String?>(null) }
    return remember(categoryValue, orderValue, timeValue, tagKeyword) {
        BrowseScaffoldFields(categoryValue, orderValue, timeValue, tagKeyword)
    }
}

/**
 * 浏览页状态：筛选口径、主列表 loader 快照与分页动作、标签检索词、分类树与热门标签。
 *
 * 依赖以窄口注入（scope / loader / 分页模式读数 / 两个可选区块的结局），
 * 所以状态机能在纯 kotlin.test 里跑起来，不必构造 [ScreenDeps] 与两个皮肤。
 */
@Stable
internal class BrowseScaffoldState internal constructor(
    val mode: BrowseMode,
    private val scope: CoroutineScope,
    private val fields: BrowseScaffoldFields,
    private val loader: SessionListLoader<SearchResult>,
    private val readPaginationMode: () -> PaginationMode,
    private val categoriesOutcome: suspend () -> SessionOutcome<CategoryTree>,
    private val hotTagsOutcome: suspend () -> SessionOutcome<List<String>>,
) {
    /** loader 快照；由 [observe] 持续写入，皮肤读它即参与重组。 */
    var snapshot: SessionListSnapshot<SearchResult> by mutableStateOf(loader.snapshot.value)
        private set

    /** 渲染层吃的三态：列表复用件只认 feed，加载态走这里。 */
    val feedState: ListState get() = snapshot.toListState()

    val refreshing: Boolean get() = snapshot.refreshing

    val filters: BrowseFilters get() = fields.filters

    val pageKey: BrowsePageKey get() = fields.filters.pageKey(mode)

    /** 同一份口径的缓存键，供皮肤做「换口径即重建」的 remember 键。 */
    val cacheKey: SessionListKey get() = SessionListKey.Browse(pageKey)

    val category: BrowseCategory get() = fields.filters.category

    val order: BrowseOrder get() = fields.filters.order

    val time: BrowseTime get() = fields.filters.time

    val clickPaging: Boolean get() = readPaginationMode() == PaginationMode.CLICK

    /** 分类树只用来渲染标签区块；拿不到时整块隐藏，不影响主列表。 */
    var categoryTree: CategoryTree? by mutableStateOf(null)
        private set

    /** 热门标签：与子分类一起构成结果列表之前的标签导航。 */
    var hotTags: List<String> by mutableStateOf(emptyList())
        private set

    /** 主分类 slug 与分类树一级节点同值，据此决定是否给「更多分类」入口。 */
    val hasTagBlocks: Boolean get() = categoryTree?.blocks?.isNotEmpty() == true

    /** 标签检索词：非空时页面切到标签结果视图。 */
    val tagKeyword: String? get() = fields.tagKeyword.value

    fun selectCategory(apiValue: String) {
        fields.categoryValue.value = apiValue
    }

    fun selectOrder(apiValue: String) {
        fields.orderValue.value = apiValue
    }

    fun selectTime(apiValue: String) {
        fields.timeValue.value = apiValue
    }

    /** 进入标签视图：空白检索词直接忽略，不留一个空标签页。 */
    fun openTag(keyword: String) {
        normalizeBrowseTagKeyword(keyword)?.let { fields.tagKeyword.value = it }
    }

    /** 退出标签视图，回到分类/榜单浏览。 */
    fun closeTag() {
        fields.tagKeyword.value = null
    }

    /** 订阅 loader 快照直到页面销毁（在 remember 入口挂成 LaunchedEffect）。 */
    suspend fun observe() {
        loader.snapshot.collect { snapshot = it }
    }

    /** 下拉手势走 refresh（保留当前列表），程序化重载走 reload（整屏进度）。 */
    fun refresh(byPull: Boolean = false) {
        if (byPull) loader.refresh() else loader.loadFirstPage()
    }

    fun reload() {
        loader.loadFirstPage()
    }

    fun loadMore() {
        loader.loadMore()
    }

    /** 点击分页：按目标页整页替换，沿用首屏缓存与筛选口径。 */
    fun jumpToPage(target: Int) {
        val current = loader.feed
        val resolved = pagerTargetPage(current.page, target, current.pageCount)
        if (resolved == current.page) return
        pendingScrollPage = resolved
        loader.goToPage(resolved)
    }

    private var pendingScrollPage = 0

    /** 换页落地那一刻消费归顶请求：只有真的停在目标页的 Loaded 快照才算数。 */
    fun consumeScrollToTop(): Boolean {
        val page = pendingScrollPage
        if (page == 0 || snapshot.state !is ListLoadState.Loaded || snapshot.feed.page != page) return false
        pendingScrollPage = 0
        return true
    }

    /** 分类树加载：失败与会话失效都退化为空（区域隐藏），不打断主列表。 */
    suspend fun loadCategoryTree() {
        categoryTree = categoriesOutcome().orDegrade<CategoryTree?>(null)
    }

    /** 热门标签加载：同样只让标签导航退化，不影响结果列表。 */
    suspend fun loadHotTags() {
        hotTags = hotTagsOutcome().orDegrade(emptyList())
    }
}

/**
 * 浏览页唯一入口：接 [ScreenDeps] 与皮肤给的三个差异点，把 loader 与四条编排挂成效果。
 *
 * @param failureMessage 主列表失败的兜底文案（两皮肤话术不同，各自给）
 * @param onNotice 加载器要提示的一句话（皮肤自己决定进哪儿，通常是 snackbar）
 * @param onScrollToTop 换页落地后的归顶动作（滚动容器在皮肤手里）
 */
@Composable
internal fun rememberBrowseScaffoldState(
    deps: ScreenDeps,
    mode: BrowseMode,
    failureMessage: String,
    onNotice: (String) -> Unit,
    onScrollToTop: suspend () -> Unit,
): BrowseScaffoldState {
    val scope = rememberCoroutineScope()
    val fields = rememberBrowseScaffoldFields()
    val filters = fields.filters
    val pageKey = filters.pageKey(mode)
    // 列表加载编排（请求代次/在途标记/翻页游标/失败落点）收在 loader 内；本页只提供取哪一页与缓存键
    val loader = remember(pageKey) {
        SessionListLoader<SearchResult>(
            scope = scope,
            idOf = { item: SearchResult -> item.id },
            failureMessage = failureMessage,
            fetchPage = { page ->
                deps.authCall.call { deps.gateway.browse(filters.filter(mode), page) }.toCouiPageSlice()
            },
            notice = onNotice,
            cache = deps.sessionCache,
            cacheKey = SessionListKey.Browse(pageKey),
        )
    }
    val state = remember(loader, fields) {
        BrowseScaffoldState(
            mode = mode,
            scope = scope,
            fields = fields,
            loader = loader,
            readPaginationMode = { deps.paginationMode },
            categoriesOutcome = { deps.authCall.call { deps.gateway.categoriesList() } },
            hotTagsOutcome = { deps.authCall.call { deps.gateway.hotTags() } },
        )
    }

    // 首帧命中 SessionCache 时 loader 构造即带内容，这里不再补拉，避免先闪一次加载态
    LaunchedEffect(loader) {
        if (loader.state !is ListLoadState.Loaded) loader.loadFirstPage()
    }
    // 快照订阅：皮肤读 state.snapshot / feedState 才跟得上 loader
    LaunchedEffect(state) { state.observe() }

    // 换页落地后回顶，避免停在旧页的滚动位置；具体滚哪个容器由皮肤决定
    val scrollToTop by rememberUpdatedState(onScrollToTop)
    LaunchedEffect(state.snapshot.feed.page, state.snapshot.state) {
        if (state.consumeScrollToTop()) scrollToTop()
    }

    // 分页模式切换后按新形态重载第一页；标签视图等皮肤自有的重载各自挂在自己的效果里
    PaginationModeReloadEffect(deps.paginationMode) { state.reload() }
    return state
}
