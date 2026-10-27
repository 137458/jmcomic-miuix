package com.jm3.app.miuix.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jm3.app.miuix.Jm3Graph
import com.jm3.core.model.NotificationItem
import com.jm3.core.model.NotificationPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Badge
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 信箱：漫画更新与站内通知；点击未读条目即标记已读并同步底栏角标。 */
@Composable
fun NotificationsScreen(onBack: () -> Unit, snackbarHostState: SnackbarHostState) {
    var page by remember { mutableStateOf<NotificationPage?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var marking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun syncUnread(items: List<NotificationItem>) {
        Jm3Graph.unreadNotifications.value = items.count { !it.read }
    }

    fun load(showLoading: Boolean = true) {
        if (showLoading) loading = true
        error = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { Jm3Graph.gateway.notifications() }
            }
            loading = false
            refreshing = false
            result
                .onSuccess {
                    page = it
                    syncUnread(it.items)
                }
                .onFailure { error = it.message ?: "信箱加载失败" }
        }
    }

    fun markRead(item: NotificationItem) {
        if (item.read || marking) return
        marking = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { Jm3Graph.gateway.markNotification(item.id, true) }
            }
            marking = false
            result
                .onSuccess {
                    val updated = page?.items?.map { if (it.id == item.id) it.copy(read = true) else it }.orEmpty()
                    page = page?.copy(items = updated)
                    syncUnread(updated)
                }
                .onFailure { snackbarHostState.showSnackbar("标记失败：" + (it.message ?: "请稍后重试")) }
        }
    }

    LaunchedEffect(Unit) { load() }

    val unread = page?.items?.count { !it.read } ?: 0
    Scaffold(
        topBar = {
            TopAppBar(
                title = "信箱",
                subtitle = if (page == null) "" else if (unread > 0) "$unread 条未读" else "全部已读",
                navigationIcon = { BackButton(onBack = onBack) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when {
            error != null -> ErrorScreen(
                message = error.orEmpty(),
                onRetry = { load() },
                modifier = Modifier.padding(innerPadding),
            )
            loading -> LoadingScreen(modifier = Modifier.padding(innerPadding))
            else -> PullToRefresh(
                isRefreshing = refreshing,
                onRefresh = {
                    refreshing = true
                    load(showLoading = false)
                },
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = innerPadding,
            ) {
                val items = page?.items.orEmpty()
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    if (items.isEmpty()) {
                        item(key = "empty") {
                            EmptyState(title = "暂无消息", hint = "订阅作品更新后会收到通知")
                        }
                    }
                    items(items, key = { it.id }) { item ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp),
                            onClick = { markRead(item) },
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = item.title,
                                        fontSize = MiuixTheme.textStyles.body2.fontSize,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    if (!item.read) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Badge()
                                    }
                                }
                                if (item.content.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(text = item.content, fontSize = MiuixTheme.textStyles.body1.fontSize)
                                }
                                item.time?.takeIf { it.isNotBlank() }?.let {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = it,
                                        fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
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
