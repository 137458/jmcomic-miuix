package com.jm3.app.miuix.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jm3.app.miuix.Jm3Graph
import com.jm3.app.miuix.ui.glass.GlassScaffold
import com.jm3.core.model.TaskFilter
import com.jm3.core.model.TaskItem
import com.jm3.core.model.TaskPage
import com.jm3.core.model.TaskType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.FavoritesFill
import top.yukonga.miuix.kmp.theme.MiuixTheme

private sealed interface TasksUiState {
    data object Loading : TasksUiState
    data class Loaded(val page: TaskPage) : TasksUiState
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
    val scope = rememberCoroutineScope()

    val type = types[typeIndex]
    val filter = filters[filterIndex]

    fun load(showLoading: Boolean = true) {
        if (showLoading) state = TasksUiState.Loading
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { Jm3Graph.gateway.tasks(type, filter) }
            }
            refreshing = false
            result
                .onSuccess { state = TasksUiState.Loaded(it) }
                .onFailure { state = TasksUiState.Failed(it.message ?: "成就列表加载失败") }
        }
    }

    LaunchedEffect(typeIndex, filterIndex) { load() }

    GlassScaffold(
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TopAppBar(
                    title = "成就",
                    subtitle = (state as? TasksUiState.Loaded)
                        ?.page
                        ?.items
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { "共 ${it.size} 项" }
                        .orEmpty(),
                    // 透明底色：玻璃底色与模糊由 GlassScaffold 统一绘制
                    color = Color.Transparent,
                    navigationIcon = { BackButton(onBack = onBack) },
                )
                // 两组筛选页签固定于列表之外：与顶栏同处玻璃层，列表滚动后仍可切换
                TabRow(
                    tabs = types.map { it.label },
                    selectedTabIndex = typeIndex,
                    onTabSelected = { typeIndex = it },
                )
                TabRow(
                    tabs = filters.map { it.label },
                    selectedTabIndex = filterIndex,
                    onTabSelected = { filterIndex = it },
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
                is TasksUiState.Loaded -> TaskList(
                    type = type,
                    page = s.page,
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
private fun TaskList(
    type: TaskType,
    page: TaskPage,
    contentPadding: PaddingValues,
    refreshing: Boolean,
    onRefresh: () -> Unit,
) {
    val done = page.items.count { it.done }
    val refreshState = rememberPullToRefreshState(refreshThreshold = 0.18f)
    PullToRefresh(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        pullToRefreshState = refreshState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // 列表内容滚到玻璃顶栏下方，顶栏才能采样到真实背景
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding(),
            ),
        ) {
            item(key = "progress") {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    BasicComponent(
                        title = "${type.label}进度",
                        summary = "已达成 $done / ${page.items.size} 项",
                        endActions = {
                            LinearProgressIndicator(
                                progress = if (page.items.isEmpty()) 0f else done.toFloat() / page.items.size,
                                modifier = Modifier.width(88.dp),
                            )
                        },
                    )
                }
            }
            // 服务端以 status 非 ok 返回时带 message，即便列表为空也要把原因呈现出来
            page.message?.let { message ->
                item(key = "message") {
                    Text(
                        text = message,
                        fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                        color = MiuixTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
                    )
                }
            }
            if (page.items.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        title = "暂无${type.label}",
                        hint = if (type == TaskType.TITLE) "继续阅读与收藏即可解锁称号" else "完成成就后可在此获得勋章",
                    )
                }
            }
            items(page.items, key = { it.id }) { item -> TaskRow(type = type, item = item) }
            item(key = "bottom-space") { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun TaskRow(type: TaskType, item: TaskItem) {
    // 称号以 content 为称号名、name 为获取条件；勋章以 name 为主标题，content 是图标路径不展示
    val title = if (type == TaskType.TITLE) item.content.ifBlank { item.name } else item.name
    val summary = buildString {
        if (type == TaskType.TITLE && item.name.isNotBlank()) append(item.name)
        if (item.coin > 0) {
            if (isNotEmpty()) append(" · ")
            append("奖励 ${item.coin} 金币")
        }
    }.ifBlank { null }
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp)) {
        BasicComponent(
            title = title,
            summary = summary,
            startAction = {
                Icon(
                    imageVector = if (item.done) MiuixIcons.FavoritesFill else MiuixIcons.Favorites,
                    contentDescription = null,
                    tint = if (item.done) {
                        MiuixTheme.colorScheme.primary
                    } else {
                        MiuixTheme.colorScheme.onSurfaceVariantSummary
                    },
                )
            },
            endActions = {
                Text(
                    text = if (item.done) "已达成" else "未达成",
                    fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                    color = if (item.done) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            },
        )
    }
}
