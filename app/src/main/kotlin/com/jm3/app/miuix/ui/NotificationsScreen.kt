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
import androidx.compose.runtime.saveable.rememberSaveable
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
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 信箱分类：全部 / 漫画追更 / 站内通知，与原版页签一致。 */
private enum class NotificationTab(val label: String, val type: String?) {
    ALL("全部", null),
    FOLLOW("追更", "comic_follow"),
    NOTICE("站内", "site_notice"),
}

/** 信箱：通知列表、按类型筛选、点击标记已读；底栏未读数随列表状态同步。 */
@Composable
fun NotificationsScreen(onBack: () -> Unit, snackbarHostState: SnackbarHostState) {
    var page by remember { mutableStateOf<NotificationPage?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    val refreshState = rememberPullToRefreshState(refreshThreshold = 0.18f)
    var marking by remember { mutableStateOf(false) }
    var tabValue by rememberSaveable { mutableStateOf(NotificationTab.ALL.name) }
    val selectionHaptic = rememberSelectionHaptic()
    val tab = NotificationTab.entries.firstOrNull { it.name == tabValue } ?: NotificationTab.ALL
    val scope = rememberCoroutineScope()

    fun syncUnread(items: List<NotificationItem>) {
        Jm3Graph.unreadNotifications.value = items.count { !it.read }
    }

    fun load(showLoading: Boolean = true) {
        if (showLoading) loading = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { Jm3Graph.gateway.notifications() }
            }
            loading = false
            refreshing = false
            result
                .onSuccess {
                    error = null
                    page = it
                    syncUnread(it.items)
                }
                .onFailure {
                    // 已有数据时保留列表，只用 Snackbar 提示刷新失败，避免整屏被错误态替换
                    if (page == null) {
                        error = it.message ?: "信箱加载失败"
                    } else {
                        snackbarHostState.showSnackbar("刷新失败：" + (it.message ?: "请稍后重试"))
                    }
                }
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

    val items = page?.items.orEmpty()
    val visible = items.filter { tab.type == null || it.type == tab.type }
    val unread = items.count { !it.read }
    val tabLabels = NotificationTab.entries.map { entry ->
        val count = items.count { !it.read && (entry.type == null || it.type == entry.type) }
        if (count > 0) "${entry.label} $count" else entry.label
    }

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
            else -> Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                if (items.isNotEmpty()) {
                    TabRow(
                        tabs = tabLabels,
                        selectedTabIndex = NotificationTab.entries.indexOf(tab),
                        onTabSelected = {
                            selectionHaptic()
                            tabValue = NotificationTab.entries[it].name
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                PullToRefresh(
                    isRefreshing = refreshing,
                    onRefresh = {
                        refreshing = true
                        load(showLoading = false)
                    },
                    pullToRefreshState = refreshState,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        if (visible.isEmpty()) {
                            item(key = "empty") {
                                EmptyState(
                                    title = if (items.isEmpty()) "暂无消息" else "暂无此类消息",
                                    hint = "订阅作品更新后会收到通知",
                                )
                            }
                        }
                        items(visible, key = { it.id }) { item ->
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
}
