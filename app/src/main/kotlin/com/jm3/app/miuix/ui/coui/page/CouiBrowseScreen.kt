// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.page

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.itos.coui.layout.Divider
import com.itos.coui.layout.Group
import com.itos.coui.layout.Item
import com.itos.coui.scroll.couiVerticalScroll
import com.itos.coui.shape.CouiShape
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
import com.jm3.app.miuix.ui.coui.component.CouiFab
import com.jm3.app.miuix.ui.coui.component.CouiSegmentedCapsules
import com.jm3.app.miuix.ui.coui.icon.CouiIcons
import com.jm3.core.appendedBy
import com.jm3.core.model.BrowseCategory
import com.jm3.core.model.BrowseFilter
import com.jm3.core.model.BrowseOrder
import com.jm3.core.model.BrowseTime
import com.jm3.core.model.SearchResult
import com.jm3.core.paging.PagedFeed
import com.jm3.core.paging.pagerTargetPage
import com.jm3.core.session.SessionOutcome
import com.jm3.core.settings.PaginationMode
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon

/**
 * COUI 分类 / 榜单页。
 *
 * 视觉只由 COUI-Kit 原厂组件构成：Scaffold 栏位、可折叠顶栏、Group/Item 分组行、
 * 胶囊分段与字阶，不再包任何 Miuix 组件；数据接线与旧 BrowseScreen 逐条对齐
 * （同一份 SessionCache 键、同一套筛选口径、同一套点击分页 / 触底分页语义），
 * 这样切页签回来仍然取回上次那份结果。
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
    val scrollState = rememberScrollState()
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
            scrollState.animateScrollTo(0)
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

    LaunchedEffect(pageKey) {
        // 同一口径的结果还在缓存里（切 pageKey 回来就是这种情况）：直接用，别退回 Loading 冲掉
        if (sessionCache.hasPage(SessionListKey.Browse(pageKey))) return@LaunchedEffect
        refresh()
    }
    PaginationModeReloadEffect(pageMode) { refresh() }

    /*
     * 正文是 Column + couiVerticalScroll（LazyColumn 与 COUI 的滚动节点不兼容），
     * 没有可见项下标可用，因此触底判定改成「距底剩余像素」；同一条数只自动请求一次，
     * 避免服务端返回空页时反复重试。
     */
    val prefetchPx = with(LocalDensity.current) { 240.dp.roundToPx() }
    LaunchedEffect(listKey, clickPaging) {
        if (clickPaging) return@LaunchedEffect
        var lastAutoCount = -1
        snapshotFlow {
            val feed = (state as? ListState.Loaded)?.feed
            (scrollState.maxValue - scrollState.value) to feed
        }.collect { (remaining, feed) ->
            if (feed == null || !feed.canLoadMore || feed.loadMoreFailed) return@collect
            if (feed.items.size == lastAutoCount) return@collect
            if (remaining > prefetchPx) return@collect
            lastAutoCount = feed.items.size
            loadMore()
        }
    }

    val scrollBehavior = CouiTopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        canScroll = { scrollState.maxValue > 0 },
    )

    CouiAppScaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(text = mode.title, kind = CouiTextKind.PageTitle) },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            CouiFab(
                icon = CouiIcons.Refresh,
                contentDescription = "刷新",
                onClick = { refresh() },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .couiVerticalScroll(scrollState)
                // padding 放在滚动节点内侧：正文才能从顶栏下方滚过并穿过栏位背景采样
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .padding(bottom = 24.dp),
        ) {
            if (mode == BrowseMode.CATEGORY) {
                CouiSegmentedCapsules(
                    items = BrowseOrder.entries.map { it.label },
                    selectedIndex = BrowseOrder.entries.indexOf(order),
                    onSelect = { orderValue = BrowseOrder.entries[it].apiValue },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                Group(title = "分类", footer = "当前顺序：${order.label}") {
                    BrowseCategory.entries.forEachIndexed { index, option ->
                        if (index > 0) Divider()
                        Item(
                            title = {
                                Text(
                                    text = option.label,
                                    kind = CouiTextKind.Body,
                                    textAlign = TextAlign.Start,
                                )
                            },
                            trailing = {
                                if (option == category) {
                                    Icon(
                                        imageVector = CouiIcons.Check,
                                        contentDescription = "已选中",
                                        modifier = Modifier.size(18.dp),
                                        tint = colors.accent,
                                    )
                                }
                            },
                            onClick = { categoryValue = option.apiValue },
                        )
                    }
                }
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

            val resultTitle = when (mode) {
                BrowseMode.CATEGORY ->
                    if (category == BrowseCategory.ALL) "全部作品" else "${category.label}作品"
                BrowseMode.RANK -> "${time.label} · ${order.label}榜"
            }

            when (val current = state) {
                ListState.Loading -> {
                    Group {
                        Item(
                            title = {
                                Text(
                                    text = "正在加载…",
                                    kind = CouiTextKind.Body,
                                    textAlign = TextAlign.Start,
                                )
                            },
                        )
                    }
                }

                is ListState.Failed -> {
                    Group(title = "加载失败", footer = current.message) {
                        Item(
                            title = {
                                Text(
                                    text = "点击重试",
                                    kind = CouiTextKind.Body,
                                    textAlign = TextAlign.Start,
                                )
                            },
                            trailing = {
                                Icon(
                                    imageVector = CouiIcons.Refresh,
                                    contentDescription = "重试",
                                    modifier = Modifier.size(20.dp),
                                    tint = colors.accent,
                                )
                            },
                            onClick = { refresh() },
                        )
                    }
                }

                is ListState.Loaded -> CouiBrowseResults(
                    feed = current.feed,
                    mode = mode,
                    title = resultTitle,
                    pageStride = pageStride,
                    clickPaging = clickPaging,
                    onOpenAlbum = onOpenAlbum,
                    onLoadMore = { loadMore() },
                    onJumpPage = { jumpToPage(it) },
                )
            }
        }
    }
}

/** 结果区：分组行 + 两种分页语义；空态、加载中、失败态都落在 COUI 的 Group/Item/Footer 上。 */
@Composable
private fun CouiBrowseResults(
    feed: PagedFeed<SearchResult>,
    mode: BrowseMode,
    title: String,
    pageStride: Int,
    clickPaging: Boolean,
    onOpenAlbum: (String) -> Unit,
    onLoadMore: () -> Unit,
    onJumpPage: (Int) -> Unit,
) {
    if (feed.items.isEmpty()) {
        Group(footer = "换一个分类或排序试试") {
            Item(
                title = {
                    Text(
                        text = "这里还没有作品",
                        kind = CouiTextKind.Body,
                        textAlign = TextAlign.Start,
                    )
                },
            )
        }
        return
    }

    // 首页拿不到时退回本页条数：宁可名次在本页重新起算，也不要空着
    val stride = if (pageStride > 0) pageStride else feed.items.size.coerceAtLeast(1)
    Group(title = title, footer = feed.total?.let { "共 $it 部" }) {
        feed.items.forEachIndexed { index, result ->
            if (index > 0) Divider()
            CouiBrowseResultRow(
                result = result,
                rank = if (mode == BrowseMode.RANK) (feed.page - 1) * stride + index + 1 else null,
                onClick = { onOpenAlbum(result.id) },
            )
        }
    }

    if (clickPaging) {
        val totalPages = feed.pageCount?.toString() ?: "?"
        Group(footer = "第 ${feed.page} / $totalPages 页") {
            Item(
                title = {
                    Text(text = "上一页", kind = CouiTextKind.Body, textAlign = TextAlign.Start)
                },
                enabled = feed.page > 1,
                onClick = { onJumpPage(feed.page - 1) },
            )
            Divider()
            Item(
                title = {
                    Text(text = "下一页", kind = CouiTextKind.Body, textAlign = TextAlign.Start)
                },
                enabled = feed.pageCount?.let { feed.page < it } ?: feed.hasMore,
                onClick = { onJumpPage(feed.page + 1) },
            )
        }
    } else {
        val footer = when {
            feed.loadMoreFailed -> "没拿到下一页，可点击重试"
            feed.loadingMore -> "正在获取下一页…"
            feed.hasMore -> "继续上滑自动加载下一页"
            else -> "没有更多了"
        }
        Group(footer = footer) {
            when {
                feed.loadMoreFailed -> Item(
                    title = {
                        Text(text = "重新加载", kind = CouiTextKind.Body, textAlign = TextAlign.Start)
                    },
                    trailing = {
                        Icon(
                            imageVector = CouiIcons.Refresh,
                            contentDescription = "重试",
                            modifier = Modifier.size(20.dp),
                            tint = CouiTheme.colorScheme.accent,
                        )
                    },
                    onClick = onLoadMore,
                )

                feed.hasMore && !feed.loadingMore -> Item(
                    title = {
                        Text(text = "加载更多", kind = CouiTextKind.Body, textAlign = TextAlign.Start)
                    },
                    onClick = onLoadMore,
                )

                else -> Item(
                    title = {
                        Text(
                            text = if (feed.loadingMore) "正在加载更多…" else "已经到底了",
                            kind = CouiTextKind.Body,
                            textAlign = TextAlign.Start,
                        )
                    },
                )
            }
        }
    }
}

/** 作品行：封面当 icon，名次只在榜单模式出现。 */
@Composable
private fun CouiBrowseResultRow(
    result: SearchResult,
    rank: Int?,
    onClick: () -> Unit,
) {
    Item(
        icon = { CouiBrowseCover(coverUrl = result.coverUrl, title = result.title) },
        // 默认 24dp 的图标位放不下封面，这里按行高给到 44dp
        iconSize = 44.dp,
        title = {
            Text(
                text = result.title,
                kind = CouiTextKind.Body,
                textAlign = TextAlign.Start,
                maxLines = 2,
            )
        },
        summary = {
            // 作者行可能为空（接口不给作者），空 Composable 不占行高
            if (result.authors.isNotEmpty()) {
                Text(
                    text = result.authors.joinToString(" / "),
                    kind = CouiTextKind.Description,
                    textAlign = TextAlign.Start,
                    maxLines = 1,
                )
            }
        },
        trailing = {
            if (rank != null) {
                Text(
                    text = rank.toString(),
                    kind = CouiTextKind.Stepper,
                    color = if (rank <= 3) {
                        CouiTheme.colorScheme.accent
                    } else {
                        CouiTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        },
        onClick = onClick,
    )
}

/** 封面缩略图：沿用旧页的 Coil 加载，形状取 COUI 的平滑圆角。 */
@Composable
private fun CouiBrowseCover(coverUrl: String?, title: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(CouiShape(12.dp))
            .background(CouiTheme.colorScheme.container),
    ) {
        if (coverUrl != null) {
            AsyncImage(
                model = coverUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
