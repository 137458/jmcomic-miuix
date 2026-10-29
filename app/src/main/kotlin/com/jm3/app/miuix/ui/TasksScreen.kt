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

/** 成就网格列数：与原版成就页的 grid-cols-4 一致。 */
private const val TASK_GRID_COLUMNS = 4

/** 成就格子高度：容纳 50dp 勋章图或称号名 + 两行说明。 */
private val TaskCellHeight = 132.dp

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

    fun load(showLoading: Boolean = true) {
        if (showLoading) state = TasksUiState.Loading
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    // 勋章图与 API 同域，域名解析失败时退化为不展示图片
                    val host = runCatching { Jm3Graph.gateway.imageHost() }.getOrDefault("")
                    TasksUiState.Loaded(Jm3Graph.gateway.tasks(type, filter), host)
                }
            }
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
            columns = GridCells.Fixed(TASK_GRID_COLUMNS),
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
            .height(TaskCellHeight)
            .border(1.dp, MiuixTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp))
            .padding(horizontal = 6.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (type == TaskType.BADGE) {
            val url = imageUrl(item.content, imageHost)
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
