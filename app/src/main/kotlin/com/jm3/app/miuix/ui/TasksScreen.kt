package com.jm3.app.miuix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.jm3.app.miuix.Jm3Graph
import com.jm3.app.miuix.ui.glass.GlassPullToRefresh
import com.jm3.app.miuix.ui.glass.GlassScaffold
import com.jm3.app.miuix.ui.glass.GlassTopAppBar
import com.jm3.core.imageUrl
import com.jm3.core.model.TaskFilter
import com.jm3.core.model.TaskItem
import com.jm3.core.model.TaskPage
import com.jm3.core.model.TaskType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.TabRow
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

    data class Failed(val message: String) : TasksUiState
}

/** 成就/任务：称号与勋章两个维度，支持「我的/全部」筛选。 */
@Composable
fun TasksScreen(onBack: () -> Unit, snackbarHostState: SnackbarHostState) {
    val types = TaskType.entries
    val filters = TaskFilter.entries
    var typeIndex by rememberSaveable { mutableIntStateOf(0) }
    var filterIndex by rememberSaveable { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<TasksUiState>(TasksUiState.Loading) }
    var refreshing by remember { mutableStateOf(false) }
    val selectionHaptic = rememberSelectionHaptic()
    val scope = rememberCoroutineScope()
    val user by Jm3Graph.account.user.collectAsState()

    val type = types[typeIndex]
    val filter = filters[filterIndex]

    // 快速切换页签时旧响应不能覆盖新条件的结果，慢网下否则会把 A 类型的结果画在 B 类型的格子位置
    var requestToken by remember { mutableIntStateOf(0) }

    fun load(showLoading: Boolean = true) {
        if (showLoading) state = TasksUiState.Loading
        requestToken++
        val token = requestToken
        // 请求发出时定住筛选条件：响应回来时页签可能已经切走
        val requestType = type
        val requestFilter = filter
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    // 勋章图与 API 同域，域名解析失败时退化为不展示图片
                    val host = runCatching { Jm3Graph.gateway.imageHost() }.getOrDefault("")
                    TasksUiState.Loaded(Jm3Graph.gateway.tasks(requestType, requestFilter), host)
                }
            }
            // 令牌已被更新的请求替换：这次响应属于旧条件，整体丢弃（含刷新态复位）
            if (token != requestToken) return@launch
            refreshing = false
            result
                .onSuccess { state = it }
                .onFailure { state = TasksUiState.Failed(it.message ?: "成就列表加载失败") }
        }
    }

    LaunchedEffect(typeIndex, filterIndex) { load() }

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
                TabRow(
                    tabs = types.map { it.label },
                    selectedTabIndex = typeIndex,
                    onTabSelected = {
                        selectionHaptic()
                        typeIndex = it
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                )
                TabRow(
                    tabs = filters.map { it.label },
                    selectedTabIndex = filterIndex,
                    onTabSelected = {
                        selectionHaptic()
                        filterIndex = it
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize()) {
            when (val s = state) {
                TasksUiState.Loading -> LoadingScreen(modifier = Modifier.padding(innerPadding))
                is TasksUiState.Failed -> ErrorScreen(
                    message = s.message,
                    onRetry = { load() },
                    modifier = Modifier.padding(innerPadding),
                )
                is TasksUiState.Loaded -> TaskGrid(
                    type = type,
                    page = s.page,
                    imageHost = s.imageHost,
                    equippedTitle = user?.levelName,
                    contentPadding = innerPadding,
                    refreshing = refreshing,
                    onRefresh = {
                        refreshing = true
                        load(showLoading = false)
                    },
                )
            }
        }
    }
}

@Composable
private fun TaskGrid(
    type: TaskType,
    page: TaskPage,
    imageHost: String,
    equippedTitle: String?,
    contentPadding: PaddingValues,
    refreshing: Boolean,
    onRefresh: () -> Unit,
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
                TaskCell(
                    type = type,
                    item = item,
                    imageHost = imageHost,
                    equipped = type == TaskType.TITLE && equippedTitle != null && item.content == equippedTitle,
                )
            }
        }
    }
}

/** 单个成就格：称号显示称号名与获取条件，勋章显示图标与所需金币；未达成整格降为次级色。 */
@Composable
private fun TaskCell(type: TaskType, item: TaskItem, imageHost: String, equipped: Boolean) {
    val title = when (type) {
        TaskType.TITLE -> item.content.ifBlank { item.name }
        TaskType.BADGE -> item.name
    }
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
            // 只留最小高度：大字号下由内容自己撑开，同排高度取该排最高格，不会被截断
            .heightIn(min = TaskCellMinHeight)
            .border(1.dp, MiuixTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp))
            .semantics { stateDescription = if (item.done) TASK_STATE_DONE else TASK_STATE_UNDONE }
            .padding(horizontal = 6.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (type == TaskType.BADGE) {
            // 图片地址由内容与域名拼成：两者不变就不该跟着重组反复重算
            val url = remember(item.content, imageHost) { imageUrl(item.content, imageHost) }
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
                    .background(MiuixTheme.colorScheme.surfaceContainer, RoundedCornerShape(6.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
    }
}
