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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.failureCopy
import com.jm3.app.miuix.ui.glass.GlassPullToRefresh
import com.jm3.app.miuix.ui.glass.GlassScaffold
import com.jm3.app.miuix.ui.glass.GlassTopAppBar
import com.jm3.app.miuix.ui.skin.LocalSkin
import com.jm3.core.NOTIFICATION_TYPE_FOLLOW
import com.jm3.core.model.NotificationBody
import com.jm3.core.model.NotificationItem
import com.jm3.core.model.NotificationPage
import com.jm3.core.notificationBody
import com.jm3.core.notificationDisplayTitle
import com.jm3.core.session.SessionOutcome
import com.jm3.core.session.UnreadBadge
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Badge
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 信箱分类：全部 / 漫画追更 / 站内通知，与原版页签一致。 */
private enum class NotificationTab(val label: String, val type: String?) {
    ALL("全部", null),
    FOLLOW("追更", NOTIFICATION_TYPE_FOLLOW),
    NOTICE("站内", "site_notice"),
}

/** 追更通知折叠时最多展示的更新条目数。 */
private const val NOTIFICATION_PREVIEW_UPDATES = 2

/** 信箱：通知列表、按类型筛选、标记已读/未读；底栏未读数随列表状态同步。 */
@Composable
fun NotificationsScreen(
    deps: ScreenDeps,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    // 未读角标的唯一真源：与 MainScreen 注入同一个实例，避免这里写一份、底栏读另一份
    unreadBadge: UnreadBadge,
    onOpenAlbum: (albumId: String) -> Unit = {},
    onOpenLogin: () -> Unit = {},
) {
    var page by remember { mutableStateOf<NotificationPage?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    // 会话失效与「刷新失败」要分开：前者给重试按钮是骗人的，只能去登录
    var sessionExpired by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    val refreshState = rememberPullToRefreshState(refreshThreshold = 0.18f)
    var markingId by remember { mutableStateOf<String?>(null) }
    // 展开态要跟相邻页签一样熬过旋屏重建：Set 不在 Bundle 可存类型里，拆成 List 存取
    var expandedIds by rememberSaveable(
        stateSaver = listSaver(
            save = { it.toList() },
            restore = { it.toSet() },
        ),
    ) { mutableStateOf(emptySet<String>()) }
    var tabValue by rememberSaveable { mutableStateOf(NotificationTab.ALL.name) }
    val selectionHaptic = rememberSelectionHaptic()
    val tab = NotificationTab.entries.firstOrNull { it.name == tabValue } ?: NotificationTab.ALL
    val tabIndex = NotificationTab.entries.indexOf(tab).coerceAtLeast(0)
    val scope = rememberCoroutineScope()
    val listStates = remember { List(NotificationTab.entries.size) { LazyListState() } }
    val pagerState = rememberSyncedTabPagerState(
        selectedTab = tabIndex,
        pageCount = NotificationTab.entries.size,
        onTabSelected = { next ->
            selectionHaptic()
            tabValue = NotificationTab.entries[next].name
        },
        useMiuixSpring = true,
    )

    fun syncUnread(items: List<NotificationItem>) {
        unreadBadge.sync(items.count { !it.read })
    }

    /** 失效与失败共用一条落点：有数据就保留列表、只弹提示，没有数据才整屏。 */
    fun applyPlan(outcome: SessionOutcome<*>) {
        val plan = planLoad(outcome, hasContent = page != null, failureMessage = "信箱加载失败")
        if (plan.phase == ScreenLoadPhase.Content) {
            plan.notice?.let { notice -> scope.launch { snackbarHostState.showSnackbar(notice) } }
        } else {
            sessionExpired = plan.phase == ScreenLoadPhase.Expired
            error = plan.message
        }
    }

    fun load(showLoading: Boolean = true) {
        if (showLoading) loading = true
        scope.launch {
            val outcome = deps.authCall.call { deps.gateway.notifications() }
            loading = false
            refreshing = false
            when (outcome) {
                is SessionOutcome.Ok -> {
                    error = null
                    sessionExpired = false
                    page = outcome.value
                    syncUnread(outcome.value.items)
                }

                SessionOutcome.Expired -> applyPlan(outcome)

                // 已有数据时保留列表，只用 Snackbar 提示刷新失败，避免整屏被错误态替换
                is SessionOutcome.Failed -> applyPlan(outcome)
            }
        }
    }

    /** 已读/未读可双向切换：原版格子上的读信图标同样按当前状态取反。 */
    fun toggleRead(item: NotificationItem) {
        if (markingId != null) return
        markingId = item.id
        scope.launch {
            val target = !item.read
            val outcome = deps.authCall.call { deps.gateway.markNotification(item.id, target) }
            markingId = null
            when (outcome) {
                is SessionOutcome.Ok -> {
                    val updated = page?.items
                        ?.map { if (it.id == item.id) it.copy(read = target) else it }
                        .orEmpty()
                    page = page?.copy(items = updated)
                    syncUnread(updated)
                }

                SessionOutcome.Expired -> snackbarHostState.showSnackbar(
                    planLoad(outcome, hasContent = false, failureMessage = "标记失败").message.orEmpty(),
                )

                is SessionOutcome.Failed -> snackbarHostState.showSnackbar("标记失败：" + failureCopy(outcome.cause, "请稍后重试"))
            }
        }
    }

    LaunchedEffect(Unit) { load() }

    val items = page?.items.orEmpty()
    val unread = items.count { !it.read }
    val tabLabels = NotificationTab.entries.map { entry ->
        val count = items.count { !it.read && (entry.type == null || it.type == entry.type) }
        if (count > 0) "${entry.label} $count" else entry.label
    }

    GlassScaffold(
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                GlassTopAppBar(
                    title = "信箱",
                    subtitle = if (page == null) "" else if (unread > 0) "$unread 条未读" else "全部已读",
                    // 透明底色：玻璃底色与模糊由 GlassScaffold 统一绘制
                    color = Color.Transparent,
                    navigationIcon = { BackButton(onBack = onBack) },
                )
                // 分类页签固定于列表之外：与顶栏同处玻璃层，列表滚动后仍可切换
                GlassChipRow(
                    tabs = tabLabels,
                    selectedTabIndex = tabIndex,
                    onTabSelected = {
                        selectionHaptic()
                        tabValue = NotificationTab.entries[it].name
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
            }
        },
        snackbarHost = { GlassSnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when {
            sessionExpired -> LoginExpiredScreen(onOpenLogin = onOpenLogin, modifier = Modifier.padding(innerPadding))
            error != null -> ErrorScreen(
                message = error.orEmpty(),
                onRetry = { load() },
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
                    onRefresh = {
                        refreshing = true
                        load(showLoading = false)
                    },
                    pullToRefreshState = refreshState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = innerPadding,
                ) {
                    LazyColumn(
                        state = listStates[pageIndex],
                        modifier = Modifier.fillMaxSize().readableWidth(),
                        // 列表内容滚到玻璃顶栏下方，顶栏才能采样到真实背景
                        contentPadding = innerPadding,
                    ) {
                        if (pageVisible.isEmpty()) {
                            item(key = "empty") {
                                EmptyState(
                                    title = if (items.isEmpty()) "暂无消息" else "暂无此类消息",
                                    // 站内页签下不存在「订阅作品更新」，提示语要按当前页签区分
                                    hint = when (pageTab) {
                                        NotificationTab.NOTICE -> "暂无站内消息"
                                        NotificationTab.FOLLOW -> "订阅作品更新后会在这里收到通知"
                                        NotificationTab.ALL -> "订阅更新与站内消息都会出现在这里"
                                    },
                                )
                            }
                        }
                        items(pageVisible, key = { it.id }) { item ->
                            NotificationCard(
                                item = item,
                                expanded = item.id in expandedIds,
                                marking = markingId == item.id,
                                onToggleExpanded = {
                                    expandedIds = if (item.id in expandedIds) {
                                        expandedIds - item.id
                                    } else {
                                        expandedIds + item.id
                                    }
                                },
                                onToggleRead = { toggleRead(item) },
                                onOpenAlbum = onOpenAlbum,
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
    val typeLabel = if (item.type == NOTIFICATION_TYPE_FOLLOW) "漫画追踪通知" else "站内通知"
    Card(
        cornerRadius = skin.shapes.cardCorner,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        onClick = onToggleExpanded,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = typeLabel,
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
                                text = if (expanded) "收起" else "展开全部 ${updates.size} 条",
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
                        text = if (marking) "处理中…" else if (item.read) "标记未读" else "标记已读",
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
