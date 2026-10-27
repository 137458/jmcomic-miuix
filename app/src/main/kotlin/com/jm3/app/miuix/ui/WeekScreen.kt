package com.jm3.app.miuix.ui

import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.dp
import com.jm3.app.miuix.Jm3Graph
import com.jm3.core.model.SearchPage
import com.jm3.core.model.WeeklyPeriod
import com.jm3.core.model.WeeklyPicksDetail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.TopAppBar

/** 每周必看：期数切换 + 当期作品列表；接口一次返回整期，无分页。 */
@Composable
fun WeekScreen(onBack: () -> Unit, onOpenAlbum: (String) -> Unit) {
    var periods by remember { mutableStateOf<List<WeeklyPeriod>>(emptyList()) }
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var detail by remember { mutableStateOf<WeeklyPicksDetail?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    // 快速切期时旧响应不能覆盖当前期
    var token by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    fun load(showLoading: Boolean = true) {
        token++
        val current = token
        if (showLoading) loading = true
        error = null
        val target = selected
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val list = Jm3Graph.gateway.weeklyPicks().periods
                    val period = list.getOrNull(target.coerceIn(0, (list.size - 1).coerceAtLeast(0)))
                    list to period?.let { Jm3Graph.gateway.weeklyPicksDetail(it.id) }
                }
            }
            if (current != token) return@launch
            loading = false
            refreshing = false
            result.fold(
                onSuccess = { (list, picked) ->
                    periods = list
                    selected = selected.coerceIn(0, (list.size - 1).coerceAtLeast(0))
                    detail = picked
                },
                onFailure = { error = it.message ?: "加载失败" },
            )
        }
    }

    LaunchedEffect(Unit) { load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "每周必看",
                subtitle = "每周五 18:00 更新",
                navigationIcon = { BackButton(onBack = onBack) },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (periods.size > 1) {
                TabRow(
                    tabs = periods.map { it.title },
                    selectedTabIndex = selected,
                    onTabSelected = { index ->
                        if (index != selected) {
                            selected = index
                            load()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            when {
                error != null -> ErrorScreen(
                    message = "加载失败：$error",
                    onRetry = { load() },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
                loading -> LoadingScreen()
                periods.isEmpty() -> CenteredEmptyState(
                    title = "暂无每周必看",
                    hint = "稍后再来看看",
                )
                else -> PullToRefresh(
                    isRefreshing = refreshing,
                    onRefresh = {
                        refreshing = true
                        load(showLoading = false)
                    },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    val loaded = detail
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        if (loaded == null || loaded.items.isEmpty()) {
                            item(key = "empty") { EmptyState(title = "本期暂无内容") }
                        } else {
                            searchResultItems(
                                page = SearchPage(
                                    currentPage = 1,
                                    totalPages = 1,
                                    items = loaded.items,
                                    totalItems = loaded.total,
                                ),
                                loadingMore = false,
                                onLoadMore = {},
                                onOpenAlbum = onOpenAlbum,
                            )
                        }
                        item(key = "bottom-space") { Spacer(modifier = Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }
}
