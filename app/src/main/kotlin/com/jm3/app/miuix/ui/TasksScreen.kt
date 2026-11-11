package com.jm3.app.miuix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.failureCopy
import com.jm3.app.miuix.ui.glass.GlassPullToRefresh
import com.jm3.app.miuix.ui.glass.GlassScaffold
import com.jm3.app.miuix.ui.glass.GlassTopAppBar
import com.jm3.app.miuix.ui.skin.LocalSkin
import com.jm3.core.CoverLocator
import com.jm3.core.model.TaskFilter
import com.jm3.core.model.TaskItem
import com.jm3.core.model.TaskPage
import com.jm3.core.model.TaskType
import com.jm3.core.session.SessionOutcome
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 成就格最小宽度：360dp 屏扣掉两栏 16dp 内边距后可用 328dp，格间距 8dp，
 * Adaptive 的列数 = floor((可宽 + 间距) / (最小格宽 + 间距))。
 * 取 72dp → floor(336 / 80) = 4 列，与原版成就页的四列一致；
 * 320dp 窄屏可宽 288dp → floor(296 / 80) = 3 列，单格仍有约 90dp，放得下 50dp 勋章图。
 */
private val TaskGridMinCellWidth = 72.dp

/**
 * 成就格最小高度（只是下限，不封顶）：按 fontScale 1.3 的大字号推算，
 * 标题 footnote1 两行约 44dp、副标题 footnote2 两行约 40dp、间距与「佩戴中」标签约 20dp、
 * 上下内边距 20dp，合计约 124dp；带勋章图的格子再多占 56dp，由内容撑开，不再被固定高度裁切。
 */
private val TaskCellMinHeight = 124.dp

/** 成就状态的无障碍描述：达成与否原本只体现在配色和透明度上，TalkBack 读不到。 */
private const val TASK_STATE_DONE = "已达成"
private const val TASK_STATE_UNDONE = "未达成"

private sealed interface TasksUiState {
    data object Loading : TasksUiState

    /** imageHost 为图片域名，用于拼接勋章图的相对路径。 */
    data class Loaded(val page: TaskPage, val imageHost: String) : TasksUiState

    /** sessionExpired 区分「重试可能好」和「会话没了」：后者只能去登录，给重试按钮是骗人的。 */
    data class Failed(val message: String, val sessionExpired: Boolean = false) : TasksUiState
}

/** 成就/任务：称号与勋章两个维度，支持「我的/全部」筛选。 */
@Composable
fun TasksScreen(
    deps: ScreenDeps,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    onOpenLogin: () -> Unit = {},
) {
    val types = TaskType.entries
    val filters = TaskFilter.entries
    var typeIndex by rememberSaveable { mutableIntStateOf(0) }
    var filterIndex by rememberSaveable { mutableIntStateOf(0) }
    val clampedTypeIndex = typeIndex.coerceIn(0, types.lastIndex)
    val clampedFilterIndex = filterIndex.coerceIn(0, filters.lastIndex)
    val states = remember { mutableStateMapOf<Int, TasksUiState>() }
    val gridStates = remember { List(types.size) { LazyGridState() } }
    var lastFilterIndex by remember { mutableIntStateOf(clampedFilterIndex) }
    var refreshingTab by remember { mutableStateOf<Int?>(null) }
    // 佩戴状态以本地覆盖优先：切换成功后服务端已更新，但账号缓存不会立刻刷新
    var equippedTitleOverride by remember { mutableStateOf<String?>(null) }
    var equippedBadgeId by remember { mutableStateOf<String?>(null) }
    var claimingId by remember { mutableStateOf<String?>(null) }
    val selectionHaptic = rememberSelectionHaptic()
    val scope = rememberCoroutineScope()
    val user by deps.account.user.collectAsState()
    val pagerState = rememberSyncedTabPagerState(
        selectedTab = clampedTypeIndex,
        pageCount = types.size,
        onTabSelected = { next ->
            selectionHaptic()
            typeIndex = next
        },
        useMiuixSpring = true,
    )

    // 换账号后旧账号的佩戴覆盖不能留在界面上
    LaunchedEffect(user?.uid) {
        equippedTitleOverride = null
        equippedBadgeId = null
    }

    /** 点选已获得的称号/勋章并切换佩戴；失败保留服务端提示，便于区分“没权限”和“网络错”。 */
    fun claim(type: TaskType, item: TaskItem) {
        val uid = user?.uid
        if (uid.isNullOrBlank()) {
            onOpenLogin()
            return
        }
        if (claimingId != null) return
        claimingId = item.id
        scope.launch {
            val outcome = deps.authCall.call { deps.gateway.claimTask(type, uid, item.id) }
            claimingId = null
            val failure = when (outcome) {
                is SessionOutcome.Ok -> outcome.value
                SessionOutcome.Expired -> "登录已失效，请重新登录"
                is SessionOutcome.Failed -> failureCopy(outcome.cause, "请稍后重试")
            }
            if (outcome is SessionOutcome.Ok && failure == null) {
                if (type == TaskType.TITLE) {
                    equippedTitleOverride = item.content
                } else {
                    equippedBadgeId = item.id
                }
                selectionHaptic()
                snackbarHostState.showSnackbar("已佩戴「${item.name}」")
            } else {
                snackbarHostState.showSnackbar("切换失败：$failure")
            }
        }
    }

    // 快速切换筛选时旧响应不能覆盖新条件的结果，慢网下否则会把旧条件的结果画在当前格子位置
    val requestTokens = remember { mutableStateMapOf<Int, Int>() }

    /** 失效与失败共用一个落点：已有成就格就保留，只弹提示；否则整屏化，失效换成「去登录」。 */
    fun applyFailure(targetTypeIdx: Int, outcome: SessionOutcome<*>) {
        val plan = planLoad(
            outcome = outcome,
            hasContent = states[targetTypeIdx] is TasksUiState.Loaded,
            failureMessage = "成就列表加载失败",
        )
        if (plan.keepContent) {
            scope.launch { snackbarHostState.showSnackbar(plan.notice.orEmpty()) }
        } else {
            states[targetTypeIdx] = TasksUiState.Failed(
                message = plan.message.orEmpty(),
                sessionExpired = plan.phase == ScreenLoadPhase.Expired,
            )
        }
    }

    fun load(targetTypeIdx: Int = clampedTypeIndex, showLoading: Boolean = true) {
        if (showLoading) states[targetTypeIdx] = TasksUiState.Loading
        val token = (requestTokens[targetTypeIdx] ?: 0) + 1
        requestTokens[targetTypeIdx] = token
        // 请求发出时定住筛选条件：响应回来时筛选可能已经切走
        val requestType = types[targetTypeIdx]
        val requestFilterIdx = clampedFilterIndex
        val requestFilter = filters[requestFilterIdx]
        scope.launch {
            val outcome = deps.authCall.call {
                // 勋章图与 API 同域；封面域名未就绪时退回当前 API 域名，仍为空才不展示图片
                val host = runCatching { deps.gateway.imageHost() }
                    .getOrDefault("")
                    .ifBlank { runCatching { deps.gateway.usedDomain() }.getOrNull().orEmpty() }
                TasksUiState.Loaded(deps.gateway.tasks(requestType, requestFilter), host)
            }
            if (refreshingTab == targetTypeIdx) refreshingTab = null
            // 令牌或筛选已被更新的请求替换：这次响应属于旧条件，整体丢弃
            if (requestTokens[targetTypeIdx] != token || clampedFilterIndex != requestFilterIdx) return@launch
            when (outcome) {
                is SessionOutcome.Ok -> states[targetTypeIdx] = outcome.value

                SessionOutcome.Expired -> applyFailure(targetTypeIdx, outcome)

                is SessionOutcome.Failed -> applyFailure(targetTypeIdx, outcome)
            }
        }
    }

    LaunchedEffect(clampedTypeIndex, clampedFilterIndex) {
        val filterChanged = lastFilterIndex != clampedFilterIndex
        if (filterChanged) {
            lastFilterIndex = clampedFilterIndex
            states.clear()
        }
        if (filterChanged || states[clampedTypeIndex] == null) {
            gridStates[clampedTypeIndex].requestScrollToItem(0)
            load(targetTypeIdx = clampedTypeIndex, showLoading = true)
        }
    }

    GlassScaffold(
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                GlassTopAppBar(
                    title = "成就",
                    // 接口只返回当前筛选的条目，没有总数；顶栏不承担统计，避免把本次条数说成「共 N 项」
                    color = Color.Transparent,
                    navigationIcon = { BackButton(onBack = onBack) },
                )
                // 两组筛选页签固定于列表之外：与顶栏同处玻璃层，列表滚动后仍可切换
                GlassChipRow(
                    tabs = types.map { it.label },
                    selectedTabIndex = clampedTypeIndex,
                    onTabSelected = {
                        selectionHaptic()
                        typeIndex = it
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                )
                GlassChipRow(
                    tabs = filters.map { it.label },
                    selectedTabIndex = clampedFilterIndex,
                    onTabSelected = {
                        selectionHaptic()
                        filterIndex = it
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                )
            }
        },
        snackbarHost = { GlassSnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { pageIdx ->
            val pageType = types[pageIdx]
            val pageState = states[pageIdx] ?: TasksUiState.Loading
            Column(modifier = Modifier.fillMaxSize().readableWidth()) {
                when (pageState) {
                    TasksUiState.Loading -> LoadingScreen(modifier = Modifier.padding(innerPadding))
                    is TasksUiState.Failed -> if (pageState.sessionExpired) {
                        LoginExpiredScreen(
                            onOpenLogin = onOpenLogin,
                            modifier = Modifier.padding(innerPadding),
                        )
                    } else {
                        ErrorScreen(
                            message = pageState.message,
                            onRetry = { load(targetTypeIdx = pageIdx) },
                            modifier = Modifier.padding(innerPadding),
                        )
                    }
                    is TasksUiState.Loaded -> TaskGrid(
                        type = pageType,
                        page = pageState.page,
                        imageHost = pageState.imageHost,
                        gridState = gridStates[pageIdx],
                        equippedTitle = equippedTitleOverride ?: user?.levelName,
                        equippedBadgeId = equippedBadgeId,
                        claimingId = claimingId,
                        contentPadding = innerPadding,
                        refreshing = refreshingTab == pageIdx,
                        onRefresh = {
                            refreshingTab = pageIdx
                            load(targetTypeIdx = pageIdx, showLoading = false)
                        },
                        onClaim = { item -> claim(pageType, item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskGrid(
    type: TaskType,
    page: TaskPage,
    imageHost: String,
    gridState: LazyGridState,
    equippedTitle: String?,
    equippedBadgeId: String?,
    claimingId: String?,
    contentPadding: PaddingValues,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onClaim: (TaskItem) -> Unit,
) {
    val refreshState = rememberPullToRefreshState(refreshThreshold = 0.18f)
    GlassPullToRefresh(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        pullToRefreshState = refreshState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        LazyVerticalGrid(
            // 自适应列宽：窄屏自动降到 3 列，避免单格被压到放不下勋章图
            columns = GridCells.Adaptive(minSize = TaskGridMinCellWidth),
            state = gridState,
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            // 列表内容滚到玻璃顶栏下方，顶栏才能采样到真实背景
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 服务端以 status 非 ok 返回时带 message，即便列表为空也要把原因呈现出来
            page.message?.let { message ->
                item(key = "message", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = message,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    )
                }
            }
            if (page.items.isEmpty()) {
                item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        title = "暂无${type.label}",
                        hint = if (type == TaskType.TITLE) {
                            "继续阅读与收藏即可解锁称号"
                        } else {
                            "完成成就后可在此获得勋章"
                        },
                    )
                }
            }
            items(page.items, key = { it.id }) { item ->
                val equipped = when (type) {
                    TaskType.TITLE -> equippedTitle != null && item.content == equippedTitle
                    TaskType.BADGE -> equippedBadgeId == item.id
                }
                TaskCell(
                    type = type,
                    item = item,
                    imageHost = imageHost,
                    equipped = equipped,
                    claimable = item.done && !equipped && claimingId == null,
                    onClick = { onClaim(item) },
                )
            }
        }
    }
}

/** 单个成就格：称号显示称号名与获取条件，勋章显示图标与所需金币；未达成整格降为次级色。 */
@Composable
private fun TaskCell(
    type: TaskType,
    item: TaskItem,
    imageHost: String,
    equipped: Boolean,
    claimable: Boolean,
    onClick: () -> Unit,
) {
    val title = when (type) {
        TaskType.TITLE -> item.content.ifBlank { item.name }
        TaskType.BADGE -> item.name
    }
    val skin = LocalSkin.current
    val subtitle = when (type) {
        TaskType.TITLE -> item.name.takeIf { it.isNotBlank() && it != item.content }
        // 勋章的金币是购买价：原版确认弹窗文案为「花费 N J coin 购买」
        TaskType.BADGE -> item.coin.takeIf { it > 0 }?.let { "$it J coin" }
    }
    val titleColor = when {
        equipped -> MiuixTheme.colorScheme.primary
        item.done -> MiuixTheme.colorScheme.onSurface
        else -> MiuixTheme.colorScheme.onSurfaceVariantSummary
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // 已获得且未佩戴的格子可点选切换；未达成或佩戴中的格子不响应，避免误触
            .then(
                if (claimable) {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )
            // 只留最小高度：大字号下由内容自己撑开，同排高度取该排最高格，不会被截断
            .heightIn(min = TaskCellMinHeight)
            .border(1.dp, MiuixTheme.colorScheme.dividerLine, RoundedCornerShape(skin.shapes.controlCorner))
            .semantics { stateDescription = if (item.done) TASK_STATE_DONE else TASK_STATE_UNDONE }
            .padding(horizontal = 6.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (type == TaskType.BADGE) {
            // 图片地址由内容与域名拼成：两者不变就不该跟着重组反复重算
            val url = remember(item.content, imageHost) { CoverLocator.of(imageHost).image(item.content) }
            if (url != null) {
                AsyncImage(
                    model = url,
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .alpha(if (item.done) 1f else 0.45f),
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
        Text(
            text = title,
            style = MiuixTheme.textStyles.footnote1,
            color = titleColor,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
        subtitle?.let { sub ->
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = sub,
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
        if (equipped) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "佩戴中",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.primary,
                modifier = Modifier
                    .background(MiuixTheme.colorScheme.surfaceContainer, RoundedCornerShape(HERO_BADGE_CORNER_DP.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
    }
}
