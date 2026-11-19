package com.jm3.app.miuix.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.ui.components.BackButton
import com.jm3.app.miuix.ui.components.EmptyState
import com.jm3.app.miuix.ui.components.ErrorScreen
import com.jm3.app.miuix.ui.components.LoadingScreen
import com.jm3.app.miuix.ui.components.TrackedLazyColumn
import com.jm3.app.miuix.ui.components.pressScale
import com.jm3.app.miuix.ui.components.rememberSelectionHaptic
import com.jm3.app.miuix.ui.glass.GlassPullToRefresh
import com.jm3.app.miuix.ui.glass.GlassScaffold
import com.jm3.app.miuix.ui.glass.GlassTopAppBar
import com.jm3.app.miuix.ui.notifications.NOTIFICATION_PREVIEW_UPDATES
import com.jm3.app.miuix.ui.notifications.NotificationTab
import com.jm3.app.miuix.ui.notifications.notificationEmptyHint
import com.jm3.app.miuix.ui.notifications.notificationMarkActionText
import com.jm3.app.miuix.ui.notifications.notificationTypeLabel
import com.jm3.app.miuix.ui.notifications.notificationUpdatesToggleText
import com.jm3.app.miuix.ui.notifications.rememberNotificationsScaffoldState
import com.jm3.app.miuix.ui.route.AppRoutes
import com.jm3.app.miuix.ui.skin.LocalSkin
import com.jm3.core.model.NotificationBody
import com.jm3.core.model.NotificationItem
import com.jm3.core.notificationBody
import com.jm3.core.notificationDisplayTitle
import com.jm3.core.session.UnreadBadge
import top.yukonga.miuix.kmp.basic.Badge
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 信箱：通知列表、按类型筛选、标记已读/未读；底栏未读数随列表状态同步。 */
@Composable
internal fun NotificationsScreen(
    deps: ScreenDeps,
    onBack: () -> Unit,
    nav: AppRoutes,
    snackbarHostState: SnackbarHostState,
    // 未读角标的唯一真源：与 MainScreen 注入同一个实例，避免这里写一份、底栏读另一份
    unreadBadge: UnreadBadge,
) {
    var tabValue by rememberSaveable { mutableStateOf(NotificationTab.ALL.name) }
    val selectionHaptic = rememberSelectionHaptic()
    val tab = NotificationTab.entries.firstOrNull { it.name == tabValue } ?: NotificationTab.ALL
    val tabIndex = NotificationTab.entries.indexOf(tab).coerceAtLeast(0)
    val refreshState = rememberPullToRefreshState(refreshThreshold = 0.18f)
    val listStates = remember { List(NotificationTab.entries.size) { LazyListState() } }
    val pagerState = rememberSyncedTabPagerState(
        selectedTab = tabIndex,
        pageCount = NotificationTab.entries.size,
        onTabSelected = { next ->
            NotificationTab.entries.getOrNull(next)?.let {
                selectionHaptic()
                tabValue = it.name
            }
        },
        useMiuixSpring = true,
    )
    // 装载、标记已读、未读角标同步与账号切换清空都在共享骨架里，本皮肤只留玻璃控件与页签
    val scaffoldState = rememberNotificationsScaffoldState(
        deps = deps,
        snackbarHostState = snackbarHostState,
        unreadBadge = unreadBadge,
        guardStaleResponses = true,
        clearFailureOnKeptContent = false,
    )
    val loading = scaffoldState.loading
    val error = scaffoldState.error
    val sessionExpired = scaffoldState.sessionExpired
    val refreshing = scaffoldState.refreshing
    val markingId = scaffoldState.markingId
    val items = scaffoldState.items
    val tabLabels = scaffoldState.tabLabels

    GlassScaffold(
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                GlassTopAppBar(
                    title = "信箱",
                    subtitle = scaffoldState.subtitle,
                    // 透明底色：玻璃底色与模糊由 GlassScaffold 统一绘制
                    color = Color.Transparent,
                    navigationIcon = { BackButton(onBack = onBack) },
                )
                // 分类页签固定于列表之外：与顶栏同处玻璃层，列表滚动后仍可切换
                GlassChipRow(
                    tabs = tabLabels,
                    selectedTabIndex = tabIndex,
                    onTabSelected = {
                        NotificationTab.entries.getOrNull(it)?.let { selected ->
                            selectionHaptic()
                            tabValue = selected.name
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
            }
        },
        snackbarHost = { GlassSnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when {
            sessionExpired -> LoginExpiredScreen(onOpenLogin = nav::login, modifier = Modifier.padding(innerPadding))
            error != null -> ErrorScreen(
                message = error.orEmpty(),
                onRetry = { scaffoldState.load() },
                modifier = Modifier.padding(innerPadding),
            )
            loading -> LoadingScreen(modifier = Modifier.padding(innerPadding))
            else -> HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { pageIndex ->
                val pageTab = NotificationTab.entries[pageIndex]
                val pageVisible = items.filter { pageTab.type == null || it.type == pageTab.type }
                GlassPullToRefresh(
                    isRefreshing = refreshing && tabIndex == pageIndex,
                    onRefresh = scaffoldState::refresh,
                    pullToRefreshState = refreshState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = innerPadding,
                ) {
                    TrackedLazyColumn(
                        state = listStates[pageIndex],
                        // 列表内容滚到玻璃顶栏下方，顶栏才能采样到真实背景
                        contentPadding = innerPadding,
                        modifier = Modifier.fillMaxSize().readableWidth(),
                    ) {
                        if (pageVisible.isEmpty()) {
                            item(key = "empty") {
                                EmptyState(
                                    title = if (items.isEmpty()) "暂无消息" else "暂无此类消息",
                                    // 站内页签下不存在「订阅作品更新」，提示语要按当前页签区分
                                    hint = notificationEmptyHint(pageTab),
                                )
                            }
                        }
                        items(pageVisible, key = { it.id }) { item ->
                            NotificationCard(
                                item = item,
                                expanded = scaffoldState.isExpanded(item.id),
                                marking = markingId == item.id,
                                onToggleExpanded = { scaffoldState.toggleExpanded(item.id) },
                                onToggleRead = { scaffoldState.toggleRead(item) },
                                onOpenAlbum = nav::album,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 单条通知：头部类型/时间、标题、正文（追更列表可折叠）、底部标记动作。 */
@Composable
private fun NotificationCard(
    item: NotificationItem,
    expanded: Boolean,
    marking: Boolean,
    onToggleExpanded: () -> Unit,
    onToggleRead: () -> Unit,
    onOpenAlbum: (albumId: String) -> Unit,
) {
    val skin = LocalSkin.current
    // 正文形态固定：content 不会在列表内变化，key 用 id 避免每次重组重复解析
    val body = remember(item.id, item.content) { notificationBody(item.content) }
    val title = remember(item.id, item.title, item.type, item.content) { notificationDisplayTitle(item) }
    val updates = (body as? NotificationBody.Updates)?.items.orEmpty()
    Card(
        cornerRadius = skin.shapes.cardCorner,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        onClick = onToggleExpanded,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = notificationTypeLabel(item.type),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                item.time?.takeIf { it.isNotBlank() }?.let { time ->
                    Text(
                        text = " · $time",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                if (!item.read) Badge()
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.Medium,
            )
            when (body) {
                is NotificationBody.Updates -> {
                    Spacer(modifier = Modifier.height(6.dp))
                    if (updates.isEmpty()) {
                        Text(
                            text = "暂无作品更新",
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    } else {
                        val shown = if (expanded) updates else updates.take(NOTIFICATION_PREVIEW_UPDATES)
                        shown.forEach { update ->
                            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                                update.updateDate?.let { date ->
                                    Text(
                                        text = "· $date",
                                        style = MiuixTheme.textStyles.footnote2,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                }
                                val albumId = update.comicId
                                Text(
                                    text = update.comicTitle,
                                    style = MiuixTheme.textStyles.footnote2,
                                    color = MiuixTheme.colorScheme.primary,
                                    modifier = if (albumId == null) {
                                        Modifier
                                    } else {
                                        Modifier.pressScale().clickable { onOpenAlbum(albumId) }
                                    },
                                )
                            }
                        }
                        if (updates.size > NOTIFICATION_PREVIEW_UPDATES) {
                            Text(
                                text = notificationUpdatesToggleText(expanded, updates.size),
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.primary,
                                modifier = Modifier.pressScale().clickable(onClick = onToggleExpanded),
                            )
                        }
                    }
                }
                is NotificationBody.Text -> if (body.text.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = body.text,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 点按区至少撑到 48dp 触区：视觉只有文字本身，不改变外观的同时保证可点范围
                Box(
                    modifier = Modifier
                        .pressScale()
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .clickable(enabled = !marking, onClick = onToggleRead),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = notificationMarkActionText(marking, item.read),
                        style = MiuixTheme.textStyles.footnote1,
                        color = if (marking) {
                            MiuixTheme.colorScheme.onSurfaceVariantSummary
                        } else {
                            MiuixTheme.colorScheme.primary
                        },
                    )
                }
            }
        }
    }
}
