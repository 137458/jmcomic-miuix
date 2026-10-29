package com.jm3.app.miuix.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import com.jm3.app.miuix.ui.glass.GlassTopAppBar
import com.jm3.core.model.SearchPage
import com.jm3.core.model.WeeklyPeriod
import com.jm3.core.model.WeeklyPicksDetail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 每周必看：期数下拉 + 当期作品列表。
 * 原版页面为「期数下拉 + 类型分段 + 分页」；协议库的期数详情只接受期数 id，
 * 不支持类型与页码参数，因此这里只做期数切换，不伪造类型筛选。
 */
@Composable
fun WeekScreen(onBack: () -> Unit, onOpenAlbum: (String) -> Unit) {
    var periods by remember { mutableStateOf<List<WeeklyPeriod>>(emptyList()) }
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var detail by remember { mutableStateOf<WeeklyPicksDetail?>(null) }
    var periodsLoading by remember { mutableStateOf(true) }
    var periodsError by remember { mutableStateOf<String?>(null) }
    var detailLoading by remember { mutableStateOf(false) }
    var detailError by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    val refreshState = rememberPullToRefreshState(refreshThreshold = 0.18f)
    // 快速切期时旧响应不能覆盖当前期的结果
    var detailToken by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    fun loadDetail(period: WeeklyPeriod, showLoading: Boolean = true) {
        detailToken++
        val current = detailToken
        if (showLoading) detailLoading = true
        detailError = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { Jm3Graph.gateway.weeklyPicksDetail(period.id) }
            }
            if (current != detailToken) return@launch
            detailLoading = false
            refreshing = false
            result
                .onSuccess { detail = it }
                .onFailure { detailError = it.message ?: "加载失败" }
        }
    }

    // 期数列表只在进入页面/下拉刷新时取一次；切期只重取当期，避免每次切期都请求列表
    fun loadPeriods() {
        periodsLoading = true
        periodsError = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { Jm3Graph.gateway.weeklyPicks() }
            }
            periodsLoading = false
            refreshing = false
            result
                .onSuccess { picks ->
                    periods = picks.periods
                    val index = selected.coerceIn(0, (periods.size - 1).coerceAtLeast(0))
                    selected = index
                    val period = periods.getOrNull(index)
                    if (period == null) {
                        detail = null
                    } else {
                        loadDetail(period, showLoading = detail == null)
                    }
                }
                .onFailure { periodsError = it.message ?: "加载失败" }
        }
    }

    LaunchedEffect(Unit) { loadPeriods() }

    GlassScaffold(
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                GlassTopAppBar(
                    title = "每周必看",
                    subtitle = "每周五 18:00 更新",
                    // 透明底色：玻璃底色与模糊由 GlassScaffold 统一绘制
                    color = Color.Transparent,
                    navigationIcon = { BackButton(onBack = onBack) },
                )
                if (periods.isNotEmpty()) {
                    val topCurrent = periods[selected.coerceIn(0, periods.lastIndex)]
                    // 期数选择器固定于列表之外：与顶栏同处玻璃层，列表滚动后仍可切期
                    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        OverlaySpinnerPreference(
                            items = periods.map { DropdownItem(text = it.title, selected = it.id == topCurrent.id) },
                            selectedIndex = selected.coerceIn(0, periods.lastIndex),
                            title = "期数",
                            summary = topCurrent.time.ifBlank { topCurrent.title },
                            showValue = false,
                            onSelectedIndexChange = { index ->
                                val period = periods.getOrNull(index) ?: return@OverlaySpinnerPreference
                                if (index == selected) return@OverlaySpinnerPreference
                                selected = index
                                detail = null
                                loadDetail(period)
                            },
                        )
                    }
                    // 期数时间范围紧跟选择器，和原版菜单里的时间文案对应
                    if (topCurrent.time.isNotBlank()) {
                        Text(
                            text = topCurrent.time,
                            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        when {
            periodsLoading -> LoadingScreen(modifier = Modifier.padding(innerPadding))
            periodsError != null -> ErrorScreen(
                message = "加载失败：$periodsError",
                onRetry = ::loadPeriods,
                modifier = Modifier.padding(innerPadding),
            )
            periods.isEmpty() -> CenteredEmptyState(
                title = "暂无每周必看",
                hint = "稍后再来看看",
                modifier = Modifier.padding(innerPadding),
            )
            else -> Column(modifier = Modifier.fillMaxSize()) {
                PullToRefresh(
                    isRefreshing = refreshing,
                    onRefresh = {
                        refreshing = true
                        loadDetail(periods[selected.coerceIn(0, periods.lastIndex)], showLoading = false)
                    },
                    pullToRefreshState = refreshState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = innerPadding,
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        // 列表内容滚到玻璃顶栏下方，顶栏才能采样到真实背景
                        contentPadding = PaddingValues(
                            top = innerPadding.calculateTopPadding(),
                            bottom = innerPadding.calculateBottomPadding(),
                        ),
                    ) {
                        val loaded = detail
                        when {
                            detailLoading -> item(key = "loading") {
                                LoadingScreen(modifier = Modifier.fillMaxWidth().padding(24.dp))
                            }
                            detailError != null -> item(key = "error") {
                                // 期数列表已成功，只有当期失败：保留下拉选择器重试
                                ErrorScreen(
                                    message = "加载失败：$detailError",
                                    onRetry = {
                                        loadDetail(periods[selected.coerceIn(0, periods.lastIndex)])
                                    },
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                )
                            }
                            loaded == null || loaded.items.isEmpty() -> item(key = "empty") {
                                EmptyState(title = "本期暂无内容")
                            }
                            else -> searchResultItems(
                                page = SearchPage(
                                    currentPage = 1,
                                    totalPages = 1,
                                    items = loaded.items,
                                    totalItems = loaded.total,
                                ),
                                loadingMore = false,
                                loadMoreFailed = false,
                                onLoadMore = {},
                                onOpenAlbum = onOpenAlbum,
                            )
                        }
                    }
                }
            }
        }
    }
}
