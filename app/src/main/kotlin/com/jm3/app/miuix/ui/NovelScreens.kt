package com.jm3.app.miuix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.ui.glass.CollapsibleSearchAction
import com.jm3.app.miuix.ui.glass.CollapsibleSearchBar
import com.jm3.app.miuix.ui.glass.GlassPullToRefresh
import com.jm3.app.miuix.ui.glass.GlassScaffold
import com.jm3.app.miuix.ui.glass.GlassSearchInputField
import com.jm3.app.miuix.ui.glass.GlassSmallTopAppBar
import com.jm3.app.miuix.ui.glass.GlassTopAppBar
import com.jm3.app.miuix.ui.glass.collapsibleSearchScroll
import com.jm3.app.miuix.ui.glass.rememberCollapsibleSearchState
import com.jm3.app.miuix.ui.glass.withSpacing
import com.jm3.app.miuix.ui.skin.LocalSkin
import com.jm3.core.appendedBy
import com.jm3.core.model.CommentItem
import com.jm3.core.model.NovelChapter
import com.jm3.core.model.NovelChapterMeta
import com.jm3.core.model.NovelDetail
import com.jm3.core.model.NovelMeta
import com.jm3.core.model.NovelOrder
import com.jm3.core.model.SearchScope
import com.jm3.core.paging.PagedFeed
import com.jm3.core.reader.ChapterGroups
import com.jm3.core.replacedBy
import com.jm3.core.session.SessionOutcome
import com.jm3.core.settings.SettingsRepository
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.FloatingToolbar
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.FavoritesFill
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.FolderFill
import top.yukonga.miuix.kmp.icon.extended.Messages
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 动作类调用（点赞/收藏/发评）的结局提示：会话失效要给登录引导，其余沿用各动作原有文案。 */
internal data class NovelActionNotice(val message: String, val openLogin: Boolean)

/**
 * 结局 → 动作提示的唯一翻译点：失效给登录引导（文案与其它屏同一份），普通失败沿用动作自己的前缀。
 *
 * 为什么单列一个纯函数：失效与失败的差别只是「要不要引导重新登录」，六个动作各写一遍
 * 迟早会有一处漏掉登录入口，也就没地方能验证这个差别。
 */
internal fun novelActionNotice(
    outcome: SessionOutcome<*>,
    failurePrefix: String,
    fallback: String = "请稍后重试",
): NovelActionNotice? = when (outcome) {
    is SessionOutcome.Ok -> null
    SessionOutcome.Expired -> NovelActionNotice(SessionOutcome.Expired.message, openLogin = true)
    is SessionOutcome.Failed -> NovelActionNotice(failurePrefix + outcome.messageOr(fallback), openLogin = false)
}

private data class NovelPageState(
    val items: List<NovelMeta> = emptyList(),
    val total: Int = 0,
    val loading: Boolean = true,
    val failure: ScreenLoadPlan? = null,
    val loadedOnce: Boolean = false,
)

/** 小说列表页：排序 + 关键词搜索 + 点赞/收藏。 */
@Composable
fun NovelListScreen(
    deps: ScreenDeps,
    onBack: () -> Unit,
    onOpenNovel: (String) -> Unit,
    onOpenLogin: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    // 会话闸门只经 deps 取：路由层不再重复传第二份。
    val authCall = deps.authCall
    val user by deps.account.user.collectAsState()
    val orders = NovelOrder.entries
    var orderName by rememberSaveable { mutableStateOf(NovelOrder.DEFAULT.name) }
    val order = orders.firstOrNull { it.name == orderName } ?: NovelOrder.DEFAULT
    val clampedOrderIndex = orders.indexOf(order).coerceAtLeast(0)
    var query by rememberSaveable { mutableStateOf("") }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var appliedQuery by rememberSaveable { mutableStateOf("") }
    var lastAppliedQuery by remember { mutableStateOf(appliedQuery) }
    val pageStates = remember { mutableStateMapOf<Int, NovelPageState>() }
    val listStates = remember { List(orders.size) { LazyListState() } }
    var refreshingTab by remember { mutableStateOf<Int?>(null) }
    // 快速切换排序/搜索时旧响应不能覆盖新结果
    val loadTokens = remember { mutableStateMapOf<Int, Int>() }
    var busyId by remember { mutableStateOf<String?>(null) }
    val selectionHaptic = rememberSelectionHaptic()
    val pagerState = rememberSyncedTabPagerState(
        selectedTab = clampedOrderIndex,
        pageCount = orders.size,
        onTabSelected = { index ->
            val next = orders.getOrNull(index) ?: return@rememberSyncedTabPagerState
            if (next != order) {
                selectionHaptic()
                orderName = next.name
            }
        },
        useMiuixSpring = true,
    )
    val scope = rememberCoroutineScope()
    val searchCollapse = rememberCollapsibleSearchState(listState = listStates[clampedOrderIndex])
    // 列表层只建一份格式化器，行级 Composable 不再各自持有
    val dateFormat = rememberDateFormatter("yyyy-MM-dd")

    fun load(targetOrderIdx: Int = clampedOrderIndex, keyword: String = appliedQuery, showLoading: Boolean) {
        val targetOrder = orders[targetOrderIdx]
        val token = (loadTokens[targetOrderIdx] ?: 0) + 1
        loadTokens[targetOrderIdx] = token
        val current = pageStates[targetOrderIdx] ?: NovelPageState()
        pageStates[targetOrderIdx] = current.copy(loading = showLoading, failure = null)
        scope.launch {
            val outcome = authCall.call {
                if (keyword.isBlank()) {
                    deps.gateway.novelList(targetOrder.apiValue, 1)
                } else {
                    deps.gateway.searchNovels(keyword)
                }
            }
            if (refreshingTab == targetOrderIdx) refreshingTab = null
            if (loadTokens[targetOrderIdx] != token || appliedQuery != keyword) return@launch
            if (outcome is SessionOutcome.Ok) {
                pageStates[targetOrderIdx] = NovelPageState(
                    items = outcome.value.items,
                    total = outcome.value.total,
                    loading = false,
                    failure = null,
                    loadedOnce = true,
                )
            } else {
                val latest = pageStates[targetOrderIdx] ?: NovelPageState()
                // 已有列表就只提示并保留内容；没有内容才换整屏（失效换成「去登录」）
                val plan = planLoad(
                    outcome = outcome,
                    hasContent = latest.items.isNotEmpty(),
                    failureMessage = "加载失败",
                )
                if (plan.keepContent) {
                    pageStates[targetOrderIdx] = latest.copy(loading = false)
                    snackbarHostState.showSnackbar(plan.notice.orEmpty())
                } else {
                    pageStates[targetOrderIdx] = latest.copy(loading = false, failure = plan)
                }
            }
        }
    }

    LaunchedEffect(clampedOrderIndex, appliedQuery) {
        val queryChanged = lastAppliedQuery != appliedQuery
        if (queryChanged) {
            lastAppliedQuery = appliedQuery
            pageStates.clear()
        }
        val current = pageStates[clampedOrderIndex]
        if (queryChanged || current == null || (!current.loadedOnce && current.failure == null && !current.loading)) {
            listStates[clampedOrderIndex].requestScrollToItem(0)
            load(
                targetOrderIdx = clampedOrderIndex,
                keyword = appliedQuery,
                showLoading = current == null || current.items.isEmpty(),
            )
        }
    }

    fun updateCachedNovel(novelId: String, transform: (NovelMeta) -> NovelMeta) {
        pageStates.keys.toList().forEach { tabIdx ->
            val current = pageStates[tabIdx] ?: return@forEach
            if (current.items.any { it.id == novelId }) {
                pageStates[tabIdx] = current.copy(items = current.items.map { if (it.id == novelId) transform(it) else it })
            }
        }
    }

    /** 点赞：平台不支持取消，已赞时只提示。 */
    fun like(item: NovelMeta) {
        if (item.liked) {
            scope.launch { snackbarHostState.showSnackbar("已经点过赞了") }
            return
        }
        if (busyId != null) return
        busyId = item.id
        scope.launch {
            val outcome = authCall.call { deps.gateway.toggleNovelLike(item.id) }
            busyId = null
            val notice = novelActionNotice(outcome, failurePrefix = "点赞失败：")
            if (notice == null) {
                updateCachedNovel(item.id) { it.copy(liked = true) }
                snackbarHostState.showSnackbar("点赞成功")
            } else {
                snackbarHostState.showSnackbar(notice.message)
                if (notice.openLogin) onOpenLogin()
            }
        }
    }

    /** 收藏：未登录先引导登录，成功后本地回显收藏态。 */
    fun favorite(item: NovelMeta) {
        if (user == null) {
            onOpenLogin()
            return
        }
        if (busyId != null) return
        busyId = item.id
        scope.launch {
            val outcome = authCall.call { deps.gateway.toggleNovelFavorite(item.id) }
            busyId = null
            val notice = novelActionNotice(outcome, failurePrefix = "收藏失败：")
            if (notice == null) {
                updateCachedNovel(item.id) { it.copy(favorite = !it.favorite) }
                snackbarHostState.showSnackbar(if (item.favorite) "已取消收藏" else "已加入收藏")
            } else {
                snackbarHostState.showSnackbar(notice.message)
                if (notice.openLogin) onOpenLogin()
            }
        }
    }

    val activeTotal = pageStates[clampedOrderIndex]?.total ?: 0

    GlassScaffold(
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                GlassTopAppBar(
                    title = if (appliedQuery.isBlank()) "小说" else "小说搜索",
                    subtitle = if (activeTotal > 0) "共 $activeTotal 本" else "",
                    navigationIcon = { BackButton(onBack = onBack) },
                    // 透明底色：玻璃底色与模糊由 GlassScaffold 统一绘制
                    color = Color.Transparent,
                    actions = {
                        CollapsibleSearchAction(state = searchCollapse)
                    },
                )
                // 胶囊搜索框：展开态输入，提交即检索；滚动时收起，排序页签保持吸顶
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
                                label = "搜索小说",
                            )
                        },
                    ) {}
                }
                // 排序收进胶囊条：与全站筛选同构，不再用设置卡片占掉半屏
                GlassChipRow(
                    tabs = orders.map { it.label },
                    selectedTabIndex = clampedOrderIndex,
                    onTabSelected = { index ->
                        val next = orders.getOrNull(index) ?: return@GlassChipRow
                        if (next == order) return@GlassChipRow
                        selectionHaptic()
                        orderName = next.name
                    },
                    // 排序有 5 档，窄屏会切出半枚胶囊：右缘渐隐替代硬边
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).chipRowEndFade(),
                )
            }
        },
        snackbarHost = { GlassSnackbarHost(snackbarHostState) },
        collapsibleSearch = searchCollapse,
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { pageIdx ->
            val pageState = pageStates[pageIdx] ?: NovelPageState()
            val pageFailure = pageState.failure
            val pageRefreshState = rememberPullToRefreshState(refreshThreshold = 0.18f)
            when {
                pageState.loading -> LoadingScreen(modifier = Modifier.padding(innerPadding))
                pageFailure?.phase == ScreenLoadPhase.Expired -> LoginExpiredScreen(
                    onOpenLogin = onOpenLogin,
                    modifier = Modifier.padding(innerPadding),
                )
                pageFailure != null -> ErrorScreen(
                    message = pageFailure.message.orEmpty(),
                    onRetry = { load(pageIdx, appliedQuery, showLoading = true) },
                    modifier = Modifier.padding(innerPadding),
                )
                pageState.items.isEmpty() -> EmptyState(
                    title = if (appliedQuery.isBlank()) "暂无小说" else "没有找到相关小说",
                    hint = if (appliedQuery.isBlank()) "稍后再来看看" else "换个关键词再试试",
                    modifier = Modifier.padding(innerPadding),
                )
                else -> GlassPullToRefresh(
                    isRefreshing = refreshingTab == pageIdx,
                    onRefresh = {
                        refreshingTab = pageIdx
                        load(pageIdx, appliedQuery, showLoading = false)
                    },
                    modifier = Modifier.fillMaxSize(),
                    pullToRefreshState = pageRefreshState,
                    // 刷新指示器下移到玻璃顶栏之下，避免被顶栏遮住
                    contentPadding = innerPadding,
                ) {
                    // 首项下移让出顶栏高度，滚动时内容从玻璃下方穿过
                    LazyColumn(
                        state = listStates[pageIdx],
                        modifier = Modifier.fillMaxSize().readableWidth().collapsibleSearchScroll(searchCollapse),
                        contentPadding = innerPadding,
                    ) {
                        items(pageState.items, key = { it.id }) { item ->
                            NovelListRow(
                                item = item,
                                dateFormat = dateFormat,
                                busy = busyId == item.id,
                                onClick = { onOpenNovel(item.id) },
                                onLike = { like(item) },
                                onFavorite = { favorite(item) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 小说列表行：封面 + 标题/作者/更新 + 行内点赞与收藏动作。 */
@Composable
private fun NovelListRow(
    item: NovelMeta,
    dateFormat: DateTimeFormatter,
    busy: Boolean,
    onClick: () -> Unit,
    onLike: () -> Unit,
    onFavorite: () -> Unit,
) {
    WorkRowCard(
        coverUrl = item.coverUrl,
        title = item.name,
        onClick = onClick,
        coverContentDescription = item.name,
        subtitle = item.author.ifBlank { "佚名" },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        item.lastChapterTitle?.let { chapter ->
            Text(
                text = "更新至 $chapter",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        // 元信息与动作同一行：动作按内容宽度占位，元信息吃剩下的宽度并在溢出时省略，
        // 这样窄屏只压日期不压按钮，也不会把动作挤成一字一行。
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                item.likes?.let { likes ->
                    Text(
                        text = "♥ $likes",
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                    )
                }
                Text(
                    text = formatEpochSeconds(item.updateAt, dateFormat),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (busy) {
                CircularProgressIndicator(size = 18.dp)
            } else {
                // 点赞不可取消：做成紧凑文字动作而不是大胶囊，避免误触
                NovelRowAction(
                    text = if (item.liked) "已赞" else "点赞",
                    active = !item.liked,
                    onClick = onLike,
                )
                NovelRowAction(
                    text = if (item.favorite) "已收藏" else "收藏",
                    active = true,
                    onClick = onFavorite,
                )
            }
        }
    }
}

/** 小说列表行的文字动作：紧凑但保留 32dp 触摸高度，主色表示可执行。 */
@Composable
private fun NovelRowAction(
    text: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.footnote1,
        fontWeight = FontWeight.Medium,
        color = if (active) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary,
        maxLines = 1,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

/** 小说详情：头部信息 + 目录/相关/评论三档 + 阅读入口。 */
@Composable
fun NovelDetailScreen(
    deps: ScreenDeps,
    novelId: String,
    onBack: () -> Unit,
    onOpenNovel: (String) -> Unit,
    onOpenReader: (String, String) -> Unit,
    onOpenSearch: (String, SearchScope) -> Unit,
    onOpenLogin: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val authCall = deps.authCall
    val user by deps.account.user.collectAsState()
    var detail by remember { mutableStateOf<NovelDetail?>(null) }
    var savedChapter by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var failure by remember { mutableStateOf<ScreenLoadPlan?>(null) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val selectionHaptic = rememberSelectionHaptic()
    var ascending by rememberSaveable { mutableStateOf(true) }
    // 目录按十章一组折叠：组身份用章节 id，切正序/倒序后展开的仍是同一批章节
    var selectedGroupId by rememberSaveable { mutableStateOf<String?>(null) }
    // 未选择时默认展开首组；用户收起后记下「全部收起」，否则会被默认值重新展开
    var allGroupsCollapsed by rememberSaveable { mutableStateOf(false) }
    var descriptionExpanded by rememberSaveable { mutableStateOf(false) }
    var actionBusy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // 详情页只建一份格式化器，头部与相关作品行共用
    val dateFormat = rememberDateFormatter("yyyy-MM-dd")

    fun load() {
        loading = true
        failure = null
        scope.launch {
            val outcome = authCall.call { deps.gateway.novelDetail(novelId) }
            loading = false
            if (outcome is SessionOutcome.Ok) {
                detail = outcome.value
            } else {
                // 整屏加载：没有可保留的内容，失效与失败都由计划决定整屏形态
                failure = planLoad(outcome = outcome, hasContent = false, failureMessage = "加载失败")
            }
        }
    }

    LaunchedEffect(novelId) {
        savedChapter = deps.settings.novelLastChapter(novelId)
        load()
    }

    fun openChapter(chapter: NovelChapterMeta) {
        if (chapter.needBuy) {
            scope.launch {
                snackbarHostState.showSnackbar("该章节需付费购买，当前版本暂不支持购买")
            }
            return
        }
        onOpenReader(novelId, chapter.ncid)
    }

    /** 继续阅读：优先上次章节，其次第一章。 */
    fun startReading(current: NovelDetail) {
        val target = current.chapters.firstOrNull { it.ncid == savedChapter }
            ?: current.chapters.firstOrNull()
        if (target == null) {
            scope.launch { snackbarHostState.showSnackbar("暂无章节") }
            return
        }
        openChapter(target)
    }

    fun toggleLike(current: NovelDetail) {
        if (current.liked) {
            scope.launch { snackbarHostState.showSnackbar("已经点过赞了") }
            return
        }
        if (actionBusy) return
        actionBusy = true
        scope.launch {
            val outcome = authCall.call { deps.gateway.toggleNovelLike(novelId) }
            actionBusy = false
            val notice = novelActionNotice(outcome, failurePrefix = "点赞失败：")
            if (notice == null) {
                detail = current.copy(liked = true)
                snackbarHostState.showSnackbar("点赞成功")
            } else {
                snackbarHostState.showSnackbar(notice.message)
                if (notice.openLogin) onOpenLogin()
            }
        }
    }

    fun toggleFavorite(current: NovelDetail) {
        if (user == null) {
            onOpenLogin()
            return
        }
        if (actionBusy) return
        actionBusy = true
        scope.launch {
            val outcome = authCall.call { deps.gateway.toggleNovelFavorite(novelId) }
            actionBusy = false
            val notice = novelActionNotice(outcome, failurePrefix = "收藏失败：")
            if (notice == null) {
                detail = current.copy(favorite = !current.favorite)
                snackbarHostState.showSnackbar(if (current.favorite) "已取消收藏" else "已加入收藏")
            } else {
                snackbarHostState.showSnackbar(notice.message)
                if (notice.openLogin) onOpenLogin()
            }
        }
    }

    GlassScaffold(
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 书名常常长到撑三行，这里固定单行省略，完整书名交给页首详情头
                GlassSmallTopAppBar(
                    title = detail?.name ?: "小说详情",
                    navigationIcon = { BackButton(onBack = onBack) },
                )
                // 胶囊必须留在顶栏：放进可滚动正文会把自身录进采样源产生重影
                GlassChipRow(
                    tabs = listOf("目录", "相关", "评论"),
                    selectedTabIndex = tab,
                    onTabSelected = {
                        selectionHaptic()
                        tab = it
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                )
            }
        },
        snackbarHost = { GlassSnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when {
            loading -> LoadingScreen(modifier = Modifier.padding(innerPadding))
            failure?.phase == ScreenLoadPhase.Expired -> LoginExpiredScreen(
                onOpenLogin = onOpenLogin,
                modifier = Modifier.padding(innerPadding),
            )
            failure != null -> ErrorScreen(
                message = failure?.message.orEmpty(),
                onRetry = ::load,
                modifier = Modifier.padding(innerPadding),
            )
            else -> {
                val current = detail
                if (current == null) {
                    CenteredEmptyState(
                        title = "小说不存在",
                        hint = "可能已下架",
                        modifier = Modifier.padding(innerPadding),
                    )
                } else {
                    // 首项下移让出顶栏高度，滚动时内容从玻璃下方穿过
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().readableWidth(),
                        contentPadding = innerPadding,
                    ) {
                        item(key = "header") {
                            NovelDetailHeader(
                                detail = current,
                                dateFormat = dateFormat,
                                savedChapter = savedChapter,
                                descriptionExpanded = descriptionExpanded,
                                onToggleDescription = { descriptionExpanded = !descriptionExpanded },
                                onTagClick = { tag -> onOpenSearch(tag, SearchScope.TAG) },
                                onStartReading = { startReading(current) },
                                onToggleLike = { toggleLike(current) },
                                onToggleFavorite = { toggleFavorite(current) },
                                onComments = { tab = 2 },
                            )
                        }

                        when (tab) {
                            0 -> {
                                item(key = "sort") {
                                    Row(
                                        modifier = Modifier.fillMaxWidth()
                                            .padding(horizontal = SectionTextStartPadding, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            text = "共 ${current.chapters.size} 章",
                                            style = MiuixTheme.textStyles.footnote1,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        )
                                        MinTouchTarget(onClick = { ascending = !ascending }) {
                                            TextButton(
                                                text = if (ascending) "正序" else "倒序",
                                                onClick = { ascending = !ascending },
                                                minWidth = 0.dp,
                                                minHeight = 32.dp,
                                            )
                                        }
                                    }
                                }
                                val groups = ChapterGroups.of(
                                    items = current.chapters,
                                    keyOf = NovelChapterMeta::ncid,
                                    descending = !ascending,
                                )
                                val expandedGroupId = if (allGroupsCollapsed) {
                                    null
                                } else {
                                    selectedGroupId ?: groups.firstOrNull()?.id
                                }
                                items(count = groups.size, key = { groups[it].id }) { index ->
                                    val group = groups[index]
                                    NovelChapterGroupCard(
                                        group = group.items,
                                        expanded = expandedGroupId == group.id,
                                        savedChapter = savedChapter,
                                        onToggle = {
                                            val collapsing = expandedGroupId == group.id
                                            allGroupsCollapsed = collapsing
                                            selectedGroupId = if (collapsing) null else group.id
                                        },
                                        onOpen = { chapter -> openChapter(chapter) },
                                    )
                                }
                            }
                            1 -> {
                                if (current.related.isEmpty()) {
                                    item(key = "related-empty") { EmptyState(title = "暂无相关作品") }
                                } else {
                                    items(current.related, key = { it.id }) { related ->
                                        NovelListRow(
                                            item = related,
                                            dateFormat = dateFormat,
                                            busy = false,
                                            onClick = { onOpenNovel(related.id) },
                                            onLike = {},
                                            onFavorite = {},
                                        )
                                    }
                                }
                            }
                            else -> item(key = "comments") {
                                NovelCommentsPanel(
                                    deps = deps,
                                    novelId = novelId,
                                    onOpenLogin = onOpenLogin,
                                    snackbarHostState = snackbarHostState,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 详情头部：封面 + 元信息 + 标签 + 三宫格操作 + 阅读按钮 + 简介。 */
@Composable
private fun NovelDetailHeader(
    detail: NovelDetail,
    dateFormat: DateTimeFormatter,
    savedChapter: String?,
    descriptionExpanded: Boolean,
    onToggleDescription: () -> Unit,
    onTagClick: (String) -> Unit,
    onStartReading: () -> Unit,
    onToggleLike: () -> Unit,
    onToggleFavorite: () -> Unit,
    onComments: () -> Unit,
) {
    val skin = LocalSkin.current
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            NovelCover(url = detail.coverUrl, width = 100.dp, height = 136.dp)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = detail.name,
                    style = largeScreenTitleStyle(MiuixTheme.textStyles.title4),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "NVL${detail.id} · ${detail.author.ifBlank { "佚名" }}",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = buildString {
                        append(if (detail.isEnd) "已完结" else "连载中")
                        if (detail.serialStatus.isNotBlank()) append(" · ${detail.serialStatus}")
                        append(" · ${detail.chapters.size} 章")
                    },
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = buildString {
                        val added = formatEpochSeconds(detail.addtime, dateFormat)
                        if (added.isNotBlank()) append("上架 $added")
                        detail.totalViews?.let { append(" · 阅 $it") }
                        detail.likes?.let { append(" · ♥ $it") }
                    },
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        if (detail.tags.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                detail.tags.forEach { tag ->
                    MinTouchTarget(onClick = { onTagClick(tag) }) {
                        TextButton(
                            text = tag,
                            onClick = { onTagClick(tag) },
                            minWidth = 0.dp,
                            minHeight = 30.dp,
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Button(
            onClick = onStartReading,
            colors = ButtonDefaults.buttonColorsPrimary(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = if (savedChapter == null) "开始阅读" else "继续阅读")
        }
        Card(cornerRadius = skin.shapes.cardCorner, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                ActionCell(
                    icon = if (detail.favorite) MiuixIcons.FolderFill else MiuixIcons.Folder,
                    label = "收藏",
                    active = detail.favorite,
                    modifier = Modifier.weight(1f),
                    onClick = onToggleFavorite,
                )
                ActionCell(
                    icon = if (detail.liked) MiuixIcons.FavoritesFill else MiuixIcons.Favorites,
                    label = "喜欢",
                    active = detail.liked,
                    modifier = Modifier.weight(1f),
                    onClick = onToggleLike,
                )
                ActionCell(
                    icon = MiuixIcons.Messages,
                    label = "评论",
                    active = false,
                    modifier = Modifier.weight(1f),
                    onClick = onComments,
                )
            }
        }
        if (detail.description.isNotBlank()) {
            Card(cornerRadius = skin.shapes.cardCorner, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onToggleDescription).padding(14.dp)) {
                    Text(
                        text = detail.description,
                        style = MiuixTheme.textStyles.body2,
                        maxLines = if (descriptionExpanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (descriptionExpanded) "收起" else "展开简介",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.primary,
                    )
                }
            }
        }
        // ascending 在章节栏使用；此处读取一次保证参数被使用且切换排序时整块重组
        if (!descriptionExpanded) Spacer(modifier = Modifier.height(2.dp))
    }
}


/** 章节分组卡片：十章一卡，默认只展开首组，单章分组不渲染摘要行。 */
@Composable
private fun NovelChapterGroupCard(
    group: List<NovelChapterMeta>,
    expanded: Boolean,
    savedChapter: String?,
    onToggle: () -> Unit,
    onOpen: (NovelChapterMeta) -> Unit,
) {
    val skin = LocalSkin.current
    val showToggle = shouldShowChapterGroupToggle(group.size)
    Card(cornerRadius = skin.shapes.cardCorner, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        if (showToggle) {
            BasicComponent(
                title = novelGroupTitle(group),
                summary = "${group.size} 章 · 点击${if (expanded) "收起" else "展开"}",
                onClick = onToggle,
            )
        }
        if (expanded || !showToggle) {
            group.forEach { chapter ->
                BasicComponent(
                    title = chapter.title.ifBlank { "未命名章节" },
                    summary = when {
                        chapter.ncid == savedChapter -> "上次读到"
                        chapter.needBuy -> "需购买"
                        chapter.isNew -> "新章节"
                        chapter.purchased -> "已购买"
                        else -> null
                    },
                    onClick = { onOpen(chapter) },
                )
            }
        }
    }
}

/** 小说阅读页：正文 + 字号 + 繁简 + 章节切换 + 评论/收藏。 */
@Composable
fun NovelReaderScreen(
    deps: ScreenDeps,
    novelId: String,
    initialChapterId: String,
    onBack: () -> Unit,
    onOpenLogin: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val authCall = deps.authCall
    val skin = LocalSkin.current
    val user by deps.account.user.collectAsState()
    var detail by remember { mutableStateOf<NovelDetail?>(null) }
    var chapter by remember { mutableStateOf<NovelChapter?>(null) }
    var currentId by rememberSaveable { mutableStateOf(initialChapterId) }
    var fontSize by remember { mutableIntStateOf(SettingsRepository.DEFAULT_NOVEL_FONT_SIZE) }
    var lang by rememberSaveable { mutableStateOf(SettingsRepository.DEFAULT_NOVEL_LANG) }
    var loading by remember { mutableStateOf(true) }
    var failure by remember { mutableStateOf<ScreenLoadPlan?>(null) }
    var favorited by remember { mutableStateOf(false) }
    var commentsOpen by remember { mutableStateOf(false) }
    var fontSheetOpen by rememberSaveable { mutableStateOf(false) }
    var loadToken by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    fun loadChapter(chapterId: String, language: String, showLoading: Boolean) {
        loadToken++
        val token = loadToken
        if (showLoading) loading = true
        failure = null
        scope.launch {
            val outcome = authCall.call { deps.gateway.novelChapter(chapterId, language) }
            if (token != loadToken) return@launch
            loading = false
            if (outcome is SessionOutcome.Ok) {
                chapter = outcome.value
                currentId = chapterId
                favorited = outcome.value.favorite
                scope.launch { deps.settings.setNovelLastChapter(novelId, chapterId) }
            } else {
                // 翻到下一章失败时旧正文还在：保留正文只提示，不把正在读的章节顶掉
                val plan = planLoad(
                    outcome = outcome,
                    hasContent = chapter != null && !showLoading,
                    failureMessage = "章节加载失败",
                )
                if (plan.keepContent) {
                    snackbarHostState.showSnackbar(plan.notice.orEmpty())
                } else {
                    failure = plan
                }
            }
        }
    }

    LaunchedEffect(novelId) {
        fontSize = deps.settings.novelFontSize()
        lang = deps.settings.novelLang()
        // 目录只是辅助数据：普通失败保持静默（正文该能读就读），只有会话失效才提示去登录
        when (val detailOutcome = authCall.call { deps.gateway.novelDetail(novelId) }) {
            is SessionOutcome.Ok -> {
                detail = detailOutcome.value
                favorited = detailOutcome.value.favorite
            }

            SessionOutcome.Expired -> snackbarHostState.showSnackbar(SessionOutcome.Expired.message)

            is SessionOutcome.Failed -> Unit
        }
        loadChapter(currentId, lang, showLoading = true)
    }

    val chapters = detail?.chapters.orEmpty()
    val currentIndex = chapters.indexOfFirst { it.ncid == currentId }
    val prevChapter = chapters.getOrNull(currentIndex - 1)
    val nextChapter = chapters.getOrNull(currentIndex + 1)

    fun goTo(target: NovelChapterMeta?) {
        if (target == null) {
            scope.launch { snackbarHostState.showSnackbar("已到尽头") }
            return
        }
        if (target.needBuy) {
            scope.launch { snackbarHostState.showSnackbar("该章节需付费购买，当前版本暂不支持购买") }
            return
        }
        loadChapter(target.ncid, lang, showLoading = true)
    }

    fun toggleFavorite() {
        if (user == null) {
            onOpenLogin()
            return
        }
        scope.launch {
            val outcome = authCall.call { deps.gateway.toggleNovelFavorite(novelId) }
            val notice = novelActionNotice(outcome, failurePrefix = "收藏失败：")
            if (notice == null) {
                favorited = !favorited
                snackbarHostState.showSnackbar(if (favorited) "已加入收藏" else "已取消收藏")
            } else {
                snackbarHostState.showSnackbar(notice.message)
                if (notice.openLogin) onOpenLogin()
            }
        }
    }

    fun changeLang(next: String) {
        lang = next
        scope.launch { deps.settings.setNovelLang(next) }
        loadChapter(currentId, next, showLoading = false)
    }

    fun changeFontSize(delta: Int) {
        val next = (fontSize + delta).coerceIn(
            SettingsRepository.NOVEL_FONT_SIZE_MIN,
            SettingsRepository.NOVEL_FONT_SIZE_MAX,
        )
        if (next == fontSize) return
        fontSize = next
        scope.launch { deps.settings.setNovelFontSize(next) }
    }

    GlassScaffold(
        topBar = {
            // 沉浸阅读页用不折叠的固定单行顶栏，把纵向空间让给正文
            GlassSmallTopAppBar(
                title = chapter?.title ?: "阅读",
                subtitle = detail?.name.orEmpty(),
                navigationIcon = { BackButton(onBack = onBack) },
                actions = {
                    MinTouchTarget(onClick = { changeLang(if (lang == "tw") "cn" else "tw") }) {
                        TextButton(
                            text = if (lang == "tw") "繁" else "简",
                            onClick = { changeLang(if (lang == "tw") "cn" else "tw") },
                            minWidth = 0.dp,
                            minHeight = 36.dp,
                        )
                    }
                },
            )
        },
        snackbarHost = { GlassSnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when {
            loading -> LoadingScreen(modifier = Modifier.padding(innerPadding))
            failure?.phase == ScreenLoadPhase.Expired -> LoginExpiredScreen(
                onOpenLogin = onOpenLogin,
                modifier = Modifier.padding(innerPadding),
            )
            failure != null -> ErrorScreen(
                message = failure?.message.orEmpty(),
                onRetry = { loadChapter(currentId, lang, showLoading = true) },
                modifier = Modifier.padding(innerPadding),
            )
            chapter == null -> CenteredEmptyState(
                title = "章节不存在",
                hint = "可能已下架",
                modifier = Modifier.padding(innerPadding),
            )
            else -> chapter?.let { body ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .readableWidth()
                        .verticalScroll(rememberScrollState())
                        // 内边距放在滚动容器内部，正文才能从玻璃顶栏下方穿过
                        .padding(
                            innerPadding.withSpacing(horizontal = 20.dp, extraTop = 12.dp, bottom = 12.dp),
                        ),
                ) {
                    Text(
                        text = body.title,
                        style = MiuixTheme.textStyles.title4,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = body.contentText,
                        fontSize = fontSize.sp,
                        lineHeight = (fontSize * 1.75).sp,
                    )
                    Spacer(modifier = Modifier.height(96.dp))
                }
            }
        }

        // 底栏原由 Scaffold 的 floatingToolbar 槽承载；GlassScaffold 无该槽，
        // 改为内容层底部居中的覆盖层，位置与原先一致
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding() + 4.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            NovelReaderToolbar(
                favorited = favorited,
                canPrev = prevChapter != null,
                canNext = nextChapter != null,
                onFont = { fontSheetOpen = true },
                onPrev = { goTo(prevChapter) },
                onNext = { goTo(nextChapter) },
                onComments = { commentsOpen = true },
                onFavorite = ::toggleFavorite,
            )
        }

        OverlayBottomSheet(
            show = fontSheetOpen,
            title = "字号",
            cornerRadius = skin.shapes.sheetCorner,
            onDismissRequest = { fontSheetOpen = false },
        ) {
            Card(cornerRadius = skin.shapes.cardCorner, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        text = "当前字号 ${fontSize}sp",
                        style = MiuixTheme.textStyles.body2,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        TextButton(
                            text = "A-",
                            onClick = { changeFontSize(-1) },
                            enabled = fontSize > SettingsRepository.NOVEL_FONT_SIZE_MIN,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            text = "A+",
                            onClick = { changeFontSize(1) },
                            enabled = fontSize < SettingsRepository.NOVEL_FONT_SIZE_MAX,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        OverlayBottomSheet(
            show = commentsOpen,
            title = "评论",
            cornerRadius = skin.shapes.sheetCorner,
            onDismissRequest = { commentsOpen = false },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = overlayMaxHeight(480.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                NovelCommentsPanel(
                    deps = deps,
                    novelId = novelId,
                    onOpenLogin = onOpenLogin,
                    snackbarHostState = snackbarHostState,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

/** 阅读器底部工具栏：字号/上一章/下一章/评论/收藏。 */
@Composable
private fun NovelReaderToolbar(
    favorited: Boolean,
    canPrev: Boolean,
    canNext: Boolean,
    onFont: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onComments: () -> Unit,
    onFavorite: () -> Unit,
) {
    FloatingToolbar {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ActionCell(glyph = "A", label = "字号", onClick = onFont, modifier = Modifier.width(66.dp))
            ActionCell(
                icon = MiuixIcons.ChevronBackward,
                label = "上一章",
                enabled = canPrev,
                onClick = onPrev,
                modifier = Modifier.width(66.dp),
            )
            ActionCell(
                icon = MiuixIcons.ChevronForward,
                label = "下一章",
                enabled = canNext,
                onClick = onNext,
                modifier = Modifier.width(66.dp),
            )
            ActionCell(icon = MiuixIcons.Messages, label = "评论", onClick = onComments, modifier = Modifier.width(66.dp))
            ActionCell(
                icon = if (favorited) MiuixIcons.FolderFill else MiuixIcons.Folder,
                label = "收藏",
                active = favorited,
                onClick = onFavorite,
                modifier = Modifier.width(66.dp),
            )
        }
    }
}


/** 小说评论区：列表 + 加载更多 + 发评论 + 回复。详情页与阅读页共用。 */
@Composable
private fun NovelCommentsPanel(
    deps: ScreenDeps,
    novelId: String,
    onOpenLogin: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val authCall = deps.authCall
    val skin = LocalSkin.current
    val user by deps.account.user.collectAsState()
    var feed by remember { mutableStateOf(PagedFeed<CommentItem>()) }
    var loading by remember { mutableStateOf(true) }
    var failure by remember { mutableStateOf<ScreenLoadPlan?>(null) }
    var draft by rememberSaveable { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var replyTarget by rememberSaveable(novelId, stateSaver = CommentItemNullableSaver) {
        mutableStateOf<CommentItem?>(null)
    }
    var replyBusy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun load(targetPage: Int, replace: Boolean, showLoading: Boolean) {
        // 还能不能拉、在不在拉全由 feed 判定：重复点「加载更多」不会再并发打同一页
        val requested = if (replace) feed else feed.beginLoadMore()
        if (!replace && requested === feed) return
        if (showLoading) loading = true
        failure = null
        feed = requested
        scope.launch {
            val outcome = authCall.call { deps.gateway.novelComments(novelId, targetPage) }
            loading = false
            if (outcome is SessionOutcome.Ok) {
                feed = if (replace) {
                    PagedFeed<CommentItem>().replacedBy(outcome.value)
                } else {
                    requested.appendedBy(outcome.value)
                }
            } else {
                // 翻页失败保留已加载评论并让页脚可重试；首屏失败才整块换掉
                val plan = planLoad(
                    outcome = outcome,
                    hasContent = !replace && requested.items.isNotEmpty(),
                    failureMessage = "评论加载失败",
                )
                if (plan.keepContent) {
                    feed = requested.failLoadMore()
                    snackbarHostState.showSnackbar(plan.notice.orEmpty())
                } else {
                    feed = requested.copy(loadingMore = false)
                    failure = plan
                }
            }
        }
    }

    LaunchedEffect(novelId) { load(1, replace = true, showLoading = true) }

    fun send() {
        val text = draft.trim()
        if (text.isEmpty() || sending) return
        if (user == null) {
            onOpenLogin()
            return
        }
        sending = true
        scope.launch {
            val outcome = authCall.call { deps.gateway.postNovelComment(novelId, text, "") }
            sending = false
            val notice = novelActionNotice(outcome, failurePrefix = "发布失败：")
            if (notice == null) {
                draft = ""
                snackbarHostState.showSnackbar("评论已发布")
                load(1, replace = true, showLoading = false)
            } else {
                snackbarHostState.showSnackbar(notice.message)
                if (notice.openLogin) onOpenLogin()
            }
        }
    }

    fun sendReply(text: String) {
        val target = replyTarget ?: return
        if (text.isBlank() || replyBusy) return
        replyBusy = true
        scope.launch {
            val outcome = authCall.call { deps.gateway.replyNovelComment(novelId, text, target.id, "") }
            replyBusy = false
            val notice = novelActionNotice(outcome, failurePrefix = "回复失败：")
            if (notice == null) {
                replyTarget = null
                snackbarHostState.showSnackbar("回复已发送")
                load(1, replace = true, showLoading = false)
            } else {
                snackbarHostState.showSnackbar(notice.message)
                if (notice.openLogin) onOpenLogin()
            }
        }
    }

    Column(modifier = modifier) {
        when {
            loading -> LoadingScreen(modifier = Modifier.fillMaxWidth().padding(24.dp))
            failure?.phase == ScreenLoadPhase.Expired -> Column(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 面板不是整屏：用内联文案 + 去登录入口，不把整屏态塞进列表项
                Text(
                    text = failure?.message.orEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MiuixTheme.textStyles.footnote1,
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(text = "去登录", onClick = onOpenLogin)
            }
            failure != null -> ErrorScreen(
                message = failure?.message.orEmpty(),
                onRetry = { load(1, replace = true, showLoading = true) },
                modifier = Modifier.fillMaxWidth().padding(8.dp),
            )
            feed.items.isEmpty() -> EmptyState(title = "暂无评论", hint = "来说点什么吧")
            else -> {
                feed.items.forEach { item ->
                    CommentRow(item = item, onReply = { replyTarget = it })
                }
                // 三态页脚与其他列表共用：加载中 / 失败重试 / 没有更多
                LoadMoreFooter(
                    feed = feed,
                    onLoadMore = { load(feed.page + 1, replace = false, showLoading = false) },
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Card(cornerRadius = skin.shapes.cardCorner, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                TextField(
                    value = draft,
                    onValueChange = { draft = it.take(COMMENT_REPLY_MAX_LENGTH) },
                    label = if (user == null) "登录后参与评论" else "写下你的评论",
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "${draft.length}/$COMMENT_REPLY_MAX_LENGTH",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Button(
                        onClick = ::send,
                        enabled = !sending && draft.isNotBlank(),
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) {
                        Text(text = if (sending) "发布中…" else "发布")
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        // 未登录时也给出明确入口，避免输入框静默失效
        if (user == null) {
            BasicComponent(
                title = "未登录",
                summary = "点击登录后参与评论",
                onClick = onOpenLogin,
            )
        }
    }

    CommentReplySheet(
        show = replyTarget != null,
        targetAuthor = replyTarget?.author,
        busy = replyBusy,
        onDismiss = { replyTarget = null },
        onSend = ::sendReply,
    )
}

/** 统一封面：超椭圆裁切 + 缺图占位，保持行高稳定。 */
@Composable
private fun NovelCover(url: String?, width: Dp, height: Dp) {
    Box(
        modifier = Modifier
            .size(width = width, height = height)
            .squircleClip(8.dp)
            .background(MiuixTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = "无封面",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}
