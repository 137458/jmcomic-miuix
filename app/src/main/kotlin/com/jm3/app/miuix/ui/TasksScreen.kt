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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.jm3.app.miuix.ui.components.BackButton
import com.jm3.app.miuix.ui.components.EmptyState
import com.jm3.app.miuix.ui.components.ErrorScreen
import com.jm3.app.miuix.ui.components.LoadingScreen
import com.jm3.app.miuix.ui.components.rememberSelectionHaptic
import com.jm3.app.miuix.ui.glass.GlassPullToRefresh
import com.jm3.app.miuix.ui.glass.GlassScaffold
import com.jm3.app.miuix.ui.glass.GlassTopAppBar
import com.jm3.app.miuix.ui.list.ListLoadState
import com.jm3.app.miuix.ui.route.AppRoutes
import com.jm3.app.miuix.ui.skin.LocalSkin
import com.jm3.app.miuix.ui.tasks.TASK_STATE_DONE
import com.jm3.app.miuix.ui.tasks.TASK_STATE_UNDONE
import com.jm3.app.miuix.ui.tasks.TaskHostResolution
import com.jm3.app.miuix.ui.tasks.isTaskEquipped
import com.jm3.app.miuix.ui.tasks.rememberTasksScaffoldState
import com.jm3.app.miuix.ui.tasks.tasksEmptyHint
import com.jm3.app.miuix.ui.tasks.tasksEmptyTitle
import com.jm3.core.CoverLocator
import com.jm3.core.model.TaskItem
import com.jm3.core.model.TaskType
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

/** 成就/任务：称号与勋章两个维度，支持「我的/全部」筛选。状态编排在 [rememberTasksScaffoldState]。 */
@Composable
internal fun TasksScreen(
    deps: ScreenDeps,
    onBack: () -> Unit,
    nav: AppRoutes,
    snackbarHostState: SnackbarHostState,
) {
    val selectionHaptic = rememberSelectionHaptic()
    // 每档列表一个滚动状态：换筛选时整组复位，避免旧筛选的滚动位置串到新列表
    val gridStates = remember { List(TaskType.entries.size) { LazyGridState() } }
    val scaffold = rememberTasksScaffoldState(
        deps = deps,
        nav = nav,
        snackbarHostState = snackbarHostState,
        haptic = selectionHaptic,
        failureMessage = "成就列表加载失败",
        failureFallback = "请稍后重试",
        hostResolution = TaskHostResolution.BeforeRequest,
        pageCount = 1,
        reloadOnAccount = true,
        onFilterReset = { gridStates.forEach { it.requestScrollToItem(0) } },
    )
    val pagerState = rememberSyncedTabPagerState(
        selectedTab = scaffold.clampedTypeIndex,
        pageCount = scaffold.types.size,
        onTabSelected = { next ->
            selectionHaptic()
            scaffold.selectType(next)
        },
        useMiuixSpring = true,
    )

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
                    tabs = scaffold.types.map { it.label },
                    selectedTabIndex = scaffold.clampedTypeIndex,
                    onTabSelected = {
                        if (scaffold.types.getOrNull(it) != null) {
                            selectionHaptic()
                            scaffold.selectType(it)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                )
                GlassChipRow(
                    tabs = scaffold.filters.map { it.label },
                    selectedTabIndex = scaffold.clampedFilterIndex,
                    onTabSelected = {
                        if (scaffold.filters.getOrNull(it) != null) {
                            selectionHaptic()
                            scaffold.selectFilter(it)
                        }
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
            val pageType = scaffold.types.getOrNull(pageIdx) ?: return@HorizontalPager
            val loader = scaffold.loaders.getOrNull(pageIdx) ?: return@HorizontalPager
            val snapshot by loader.snapshot.collectAsState()
            Column(modifier = Modifier.fillMaxSize().readableWidth()) {
                when (val state = snapshot.state) {
                    ListLoadState.Loading -> LoadingScreen(modifier = Modifier.padding(innerPadding))
                    is ListLoadState.Failed -> if (state.sessionExpired) {
                        LoginExpiredScreen(
                            onOpenLogin = scaffold.nav::login,
                            modifier = Modifier.padding(innerPadding),
                        )
                    } else {
                        ErrorScreen(
                            message = state.message,
                            onRetry = loader::loadFirstPage,
                            modifier = Modifier.padding(innerPadding),
                        )
                    }
                    ListLoadState.Loaded -> TaskGrid(
                        type = pageType,
                        items = snapshot.feed.items,
                        message = scaffold.extra(pageIdx)?.message?.takeIf { it.isNotBlank() },
                        imageHost = scaffold.imageHost,
                        gridState = gridStates[pageIdx],
                        equippedTitle = scaffold.equippedTitleOverride ?: scaffold.user?.levelName,
                        equippedBadgeId = scaffold.equippedBadgeId,
                        claimingId = scaffold.claimingId,
                        contentPadding = innerPadding,
                        refreshing = snapshot.refreshing,
                        onRefresh = loader::refresh,
                        onClaim = { item -> scaffold.claim(pageType, item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskGrid(
    type: TaskType,
    items: List<TaskItem>,
    message: String?,
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
            message?.let { message ->
                item(key = "message", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = message,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    )
                }
            }
            if (items.isEmpty()) {
                item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        title = tasksEmptyTitle(type),
                        hint = tasksEmptyHint(type),
                    )
                }
            }
            items(items, key = { it.id }) { item ->
                val equipped = isTaskEquipped(type, item, equippedTitle, equippedBadgeId)
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
