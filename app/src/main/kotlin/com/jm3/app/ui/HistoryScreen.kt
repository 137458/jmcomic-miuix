package com.jm3.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jm3.app.Jm3Graph
import com.jm3.core.history.HistoryEntry
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val historyDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

private sealed interface HistoryListState {
    data object Loading : HistoryListState
    data object Loaded : HistoryListState
    data class Failed(val message: String) : HistoryListState
}

@Composable
fun HistoryScreen(onBack: () -> Unit, onOpenAlbum: (String) -> Unit) {
    val repository = Jm3Graph.history
    val pageSize = 20
    var entries by remember { mutableStateOf<List<HistoryEntry>>(emptyList()) }
    var state by remember { mutableStateOf<HistoryListState>(HistoryListState.Loading) }
    var nextPage by remember { mutableStateOf(2) }
    var endReached by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun loadFirstPage() {
        scope.launch {
            state = HistoryListState.Loading
            nextPage = 2
            endReached = false
            runCatching { repository.page(1, pageSize) }
                .onSuccess {
                    entries = it
                    state = HistoryListState.Loaded
                    if (it.size < pageSize) endReached = true
                }
                .onFailure { state = HistoryListState.Failed(it.message ?: "历史加载失败") }
        }
    }

    LaunchedEffect(Unit) {
        loadFirstPage()
    }

    fun loadMore() {
        if (loadingMore || endReached || state != HistoryListState.Loaded) return
        loadingMore = true
        scope.launch {
            val next = runCatching { repository.page(nextPage, pageSize) }.getOrDefault(emptyList())
            loadingMore = false
            if (next.isEmpty()) {
                endReached = true
            } else {
                entries = entries + next
                nextPage += 1
                if (next.size < pageSize) endReached = true
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = "阅读历史", navigationIcon = { BackButton(onBack = onBack) }) },
    ) { innerPadding ->
        when (val s = state) {
            HistoryListState.Loading -> LoadingScreen(modifier = Modifier.padding(innerPadding))
            is HistoryListState.Failed -> ErrorScreen(
                message = s.message,
                onRetry = ::loadFirstPage,
                modifier = Modifier.padding(innerPadding),
            )
            HistoryListState.Loaded -> {
                if (entries.isEmpty()) {
                    CenteredEmptyState(
                        title = "暂无阅读历史",
                        hint = "读过的章节会出现在这里",
                        modifier = Modifier.padding(innerPadding),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = innerPadding,
                    ) {
                        items(entries, key = { it.photoId }) { entry ->
                            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp)) {
                                BasicComponent(
                                    title = entry.title,
                                    summary = historyDateFormat.format(Date(entry.updatedAt)),
                                    onClick = { onOpenAlbum(entry.albumId) },
                                )
                            }
                        }
                        if (!endReached) {
                            item(key = "load-more") {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (loadingMore) {
                                        CircularProgressIndicator(size = 20.dp)
                                    } else {
                                        TextButton(text = "加载更多", onClick = ::loadMore)
                                    }
                                }
                            }
                        }
                        item(key = "bottom-space") { Spacer(modifier = Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }
}
