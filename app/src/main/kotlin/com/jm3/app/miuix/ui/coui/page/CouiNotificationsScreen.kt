// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.page

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.itos.coui.effect.SpotlightPreset
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.layout.Footer
import com.itos.coui.scroll.rememberCouiOverscrollEffect
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.swipereveal.CouiSwipeRevealDefaults
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme as CouiKitTheme
import com.itos.coui.topappbar.CouiTopAppBarDefaults
import com.itos.coui.topappbar.LargeTopAppBar
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.failureCopy
import com.jm3.app.miuix.ui.GlassSnackbarHost
import com.jm3.app.miuix.ui.ScreenLoadPhase
import com.jm3.app.miuix.ui.coui.component.CouiAppScaffold
import com.jm3.app.miuix.ui.coui.component.CouiEmptyGroup
import com.jm3.app.miuix.ui.coui.component.CouiErrorGroup
import com.jm3.app.miuix.ui.coui.component.CouiLoadingGroup
import com.jm3.app.miuix.ui.coui.component.CouiSegmentedCapsules
import com.jm3.app.miuix.ui.coui.component.CouiTopBarBackButton
import com.jm3.app.miuix.ui.coui.component.CouiTopBarTitle
import com.jm3.app.miuix.ui.coui.component.couiPressable
import com.jm3.app.miuix.ui.coui.component.couiScreenContentPadding
import com.jm3.app.miuix.ui.coui.component.couiTopAppBarColors
import com.jm3.app.miuix.ui.coui.component.couiWorkCardShape
import com.jm3.app.miuix.ui.coui.theme.CouiTheme
import com.jm3.app.miuix.ui.planLoad
import com.jm3.app.miuix.ui.readableWidth
import com.jm3.app.miuix.ui.rememberSelectionHaptic
import com.jm3.core.NOTIFICATION_TYPE_FOLLOW
import com.jm3.core.model.NotificationBody
import com.jm3.core.model.NotificationItem
import com.jm3.core.model.NotificationPage
import com.jm3.core.notificationBody
import com.jm3.core.notificationDisplayTitle
import com.jm3.core.session.SessionOutcome
import com.jm3.core.session.UnreadBadge
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.SnackbarHostState

/** 展开后最多直接铺开的作品更新条数；再多就让用户手动「展开全部」。 */
private const val NOTIFICATION_PREVIEW_UPDATES = 2

/** 侧滑露出动作的行程：38dp 的融合动作尺寸再留出标签宽度。 */
private val NotificationsSwipeReach = 96.dp

/** 信箱的分类档位；[type] 为 null 表示不筛。 */
private enum class NotificationTab(val label: String, val type: String?) {
    ALL("全部", null),
    FOLLOW("追更", NOTIFICATION_TYPE_FOLLOW),
    NOTICE("站内", "site_notice"),
}

/**
 * COUI 信箱页：行卡 + 融合滑动，签名与原页面一致。
 *
 * 交互对旧页做了升级而不是替换：点击整卡仍是展开/收起，向右滑切换已读、向左滑展开；
 * 旧页底部的「标记已读」仍保留一行文字入口，滑动手势不是唯一路径。
 */
@Composable
internal fun CouiNotificationsScreen(
    deps: ScreenDeps,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    unreadBadge: UnreadBadge,
    onOpenAlbum: (albumId: String) -> Unit = {},
    onOpenLogin: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf<NotificationPage?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var sessionExpired by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var markingId by remember { mutableStateOf<String?>(null) }
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    // 展开态要跨配置变更保留，否则换肤一次就全部收起
    var expandedIds by rememberSaveable(
        stateSaver = listSaver<Set<String>, String>(
            save = { it.toList() },
            restore = { it.toSet() },
        ),
    ) { mutableStateOf(emptySet<String>()) }

    fun syncUnread(items: List<NotificationItem>) {
        unreadBadge.sync(items.count { !it.read })
    }

    fun applyPlan(outcome: SessionOutcome<*>) {
        val plan = planLoad(outcome, hasContent = page != null, failureMessage = "信箱加载失败")
        if (plan.phase == ScreenLoadPhase.Content) {
            error = null
            sessionExpired = false
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
                else -> applyPlan(outcome)
            }
        }
    }

    fun toggleRead(item: NotificationItem) {
        // 单条串行：同一行连点两次会打架，用 markingId 挡住
        if (markingId != null) return
        val target = !item.read
        markingId = item.id
        scope.launch {
            val outcome = deps.authCall.call { deps.gateway.markNotification(item.id, target) }
            markingId = null
            when (outcome) {
                is SessionOutcome.Ok -> {
                    page = page?.let { current ->
                        current.copy(
                            items = current.items.map { existing ->
                                if (existing.id == item.id) existing.copy(read = target) else existing
                            },
                        )
                    }
                    page?.let { syncUnread(it.items) }
                }
                is SessionOutcome.Expired -> snackbarHostState.showSnackbar(
                    planLoad(outcome, hasContent = false, failureMessage = "标记失败").message.orEmpty(),
                )
                is SessionOutcome.Failed -> snackbarHostState.showSnackbar(
                    "标记失败：" + failureCopy(outcome.cause, "请稍后重试"),
                )
            }
        }
    }

    LaunchedEffect(Unit) { load() }

    CouiTheme {
        val listState = rememberLazyListState()
        val overscroll = rememberCouiOverscrollEffect()
        val scrollBehavior = CouiTopAppBarDefaults.exitUntilCollapsedScrollBehavior()
        val items = page?.items.orEmpty()
        val tab = NotificationTab.entries[tabIndex.coerceIn(0, NotificationTab.entries.lastIndex)]
        val visible = items.filter { tab.type == null || it.type == tab.type }
        val unread = items.count { !it.read }
        val tabLabels = NotificationTab.entries.map { entry ->
            val count = if (entry.type == null) {
                unread
            } else {
                items.count { !it.read && it.type == entry.type }
            }
            if (count > 0) entry.label + " " + count else entry.label
        }
        val subtitle = when {
            page == null -> ""
            unread > 0 -> unread.toString() + " 条未读"
            else -> "全部已读"
        }

        Box(modifier = Modifier.fillMaxSize()) {
            CouiAppScaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = {
                    LargeTopAppBar(
                        title = { CouiTopBarTitle(text = "信箱", textAlign = TextAlign.Start) },
                        navigationIcon = { CouiTopBarBackButton(onClick = onBack) },
                        actions = {
                            CouiFeedRefreshAction(
                                refreshing = refreshing,
                                onRefresh = {
                                    if (!refreshing) {
                                        refreshing = true
                                        load(showLoading = false)
                                    }
                                },
                            )
                        },
                        scrollBehavior = scrollBehavior,
                        colors = couiTopAppBarColors(),
                    )
                },
            ) { innerPadding ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .readableWidth()
                        .nestedScroll(scrollBehavior.nestedScrollConnection)
                        .consumeWindowInsets(innerPadding),
                    state = listState,
                    contentPadding = couiScreenContentPadding(scrollBehavior, innerPadding),
                    overscrollEffect = overscroll,
                ) {
                    item(key = "notif-tabs") {
                        CouiSegmentedCapsules(
                            items = tabLabels,
                            selectedIndex = tabIndex,
                            onSelect = { tabIndex = it },
                        )
                    }
                    if (subtitle.isNotBlank()) {
                        item(key = "notif-subtitle") { Footer(text = subtitle) }
                    }

                    when {
                        loading && page == null -> item(key = "notif-loading") {
                            CouiLoadingGroup(
                                modifier = Modifier.padding(top = 8.dp),
                                text = "正在加载信箱…",
                            )
                        }
                        sessionExpired -> item(key = "notif-expired") {
                            CouiFeedLoginGroup(message = error.orEmpty(), onOpenLogin = onOpenLogin)
                        }
                        error != null -> item(key = "notif-error") {
                            CouiErrorGroup(
                                message = error.orEmpty(),
                                onRetry = { loading = true; load() },
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        items.isEmpty() -> {
                            item(key = "notif-empty") {
                                CouiEmptyGroup(
                                    text = "暂无消息",
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                            item(key = "notif-empty-hint") {
                                Footer(text = emptyHint(tab))
                            }
                        }
                        visible.isEmpty() -> {
                            item(key = "notif-tab-empty") {
                                CouiEmptyGroup(
                                    text = "暂无此类消息",
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                            item(key = "notif-tab-empty-hint") { Footer(text = emptyHint(tab)) }
                        }
                        else -> {
                            itemsIndexed(visible, key = { _, item -> "notif-" + item.id }) { _, item ->
                                val expanded = item.id in expandedIds
                                CouiSwipeRow(
                                    startAction = if (item.read) "标记未读" else "标记已读",
                                    endAction = if (expanded) "收起" else "展开",
                                    onStartAction = { toggleRead(item) },
                                    onEndAction = {
                                        expandedIds = if (expanded) {
                                            expandedIds - item.id
                                        } else {
                                            expandedIds + item.id
                                        }
                                    },
                                    modifier = Modifier.padding(bottom = 8.dp),
                                ) {
                                    NotificationCard(
                                        item = item,
                                        expanded = expanded,
                                        marking = markingId == item.id,
                                        onToggleExpanded = {
                                            expandedIds = if (expanded) {
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
                            item(key = "notif-tail") {
                                Spacer(modifier = Modifier.size(CouiPageDefaults.groupSpacing))
                            }
                        }
                    }
                }
            }

            GlassSnackbarHost(
                state = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
            )
        }
    }
}

/** 空态说明按档位给具体去处，比统一的「暂无」更能指路。 */
private fun emptyHint(tab: NotificationTab): String = when (tab) {
    NotificationTab.NOTICE -> "暂无站内消息"
    NotificationTab.FOLLOW -> "订阅作品更新后会在这里收到通知"
    NotificationTab.ALL -> "订阅更新与站内消息都会出现在这里"
}

/** 单条通知卡：类型/时间一行、标题、正文（作品更新列表或纯文本）、已读动作一行。 */
@Composable
private fun NotificationCard(
    item: NotificationItem,
    expanded: Boolean,
    marking: Boolean,
    onToggleExpanded: () -> Unit,
    onToggleRead: () -> Unit,
    onOpenAlbum: (albumId: String) -> Unit,
) {
    val scheme = CouiKitTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(couiWorkCardShape())
            .background(scheme.card)
            .couiPressable(onClick = onToggleExpanded)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = if (item.type == NOTIFICATION_TYPE_FOLLOW) "漫画追踪通知" else "站内通知",
                kind = CouiTextKind.Description,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
            )
            val time = item.time
            if (!time.isNullOrBlank()) {
                Text(
                    text = "· " + time,
                    kind = CouiTextKind.Description,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            if (!item.read) {
                // 未读用圆点而不是文字：行首已经有类型标签，再多一个字会抢标题
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(scheme.accent),
                )
            }
        }
        Text(
            text = notificationDisplayTitle(item),
            kind = CouiTextKind.PreferenceTitle,
            color = scheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        when (val body = notificationBody(item.content)) {
            is NotificationBody.Updates -> {
                if (body.items.isEmpty()) {
                    Text(
                        text = "暂无作品更新",
                        kind = CouiTextKind.PreferenceSummary,
                        color = scheme.onSurfaceVariant,
                    )
                } else {
                    val shown = if (expanded) body.items else body.items.take(NOTIFICATION_PREVIEW_UPDATES)
                    shown.forEach { update ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            val date = update.updateDate
                            if (!date.isNullOrBlank()) {
                                Text(
                                    text = "· " + date,
                                    kind = CouiTextKind.Description,
                                    color = scheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                            val comicId = update.comicId
                            if (comicId != null) {
                                Box(
                                    modifier = Modifier
                                        .clip(CouiCapsuleShape)
                                        .couiPressable(shape = CouiCapsuleShape, spotlight = SpotlightPreset.ChipTab, onClick = { onOpenAlbum(comicId) }),
                                ) {
                                    Text(
                                        text = update.comicTitle,
                                        kind = CouiTextKind.Body,
                                        color = scheme.accent,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            } else {
                                Text(
                                    text = update.comicTitle,
                                    kind = CouiTextKind.Body,
                                    color = scheme.accent,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    if (body.items.size > NOTIFICATION_PREVIEW_UPDATES) {
                        Text(
                            text = if (expanded) "收起" else "展开全部 " + body.items.size + " 条",
                            kind = CouiTextKind.PreferenceValue,
                            color = scheme.accent,
                        )
                    }
                }
            }
            is NotificationBody.Text -> if (body.text.isNotBlank()) {
                Text(
                    text = body.text,
                    kind = CouiTextKind.PreferenceSummary,
                    color = scheme.onSurfaceVariant,
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .clip(CouiCapsuleShape)
                    .couiPressable(shape = CouiCapsuleShape, spotlight = SpotlightPreset.ChipTab, onClick = onToggleRead)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Text(
                    text = when {
                        marking -> "处理中…"
                        item.read -> "标记未读"
                        else -> "标记已读"
                    },
                    kind = CouiTextKind.Description,
                    color = if (marking) scheme.onSurfaceVariant else scheme.accent,
                )
            }
        }
    }
}

/**
 * 融合滑动行：横向拖拽把卡片推开，露出两侧动作，越过半程松手即提交。
 *
 * 融合感来自推离程度的模糊渐变（[CouiSwipeRevealDefaults.fusedRevealMaxBlurPx]）与回弹
 * 弹簧（[CouiSwipeRevealDefaults.itemSettleSpec]），所以拖到一半松手会自然弹回原位。
 */
@Composable
private fun CouiSwipeRow(
    startAction: String,
    endAction: String,
    onStartAction: () -> Unit,
    onEndAction: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val haptic = rememberSelectionHaptic()
    val reachPx = with(density) { NotificationsSwipeReach.toPx() }
    val offset = remember { Animatable(0f) }
    val scheme = CouiKitTheme.colorScheme
    val scrim = (abs(offset.value) / reachPx).coerceIn(0f, 1f)
    val blurRadius = with(density) {
        (CouiSwipeRevealDefaults.fusedRevealMaxBlurPx * scrim.coerceAtLeast(0f)).toDp()
    }
    val scope = rememberCoroutineScope()

    Box(modifier = modifier.fillMaxWidth()) {
        // 动作层先铺满：卡片被推开时露出的就是这一层，文字始终落在可读位置
        Row(
            modifier = Modifier.matchParentSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = startAction,
                    kind = CouiTextKind.Description,
                    color = scheme.accent.copy(alpha = scrim.coerceAtLeast(0.25f)),
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(end = 16.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(
                    text = endAction,
                    kind = CouiTextKind.Description,
                    color = scheme.accent.copy(alpha = scrim.coerceAtLeast(0.25f)),
                )
            }
        }
        Box(
            modifier = Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .blur(blurRadius)
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        scope.launch {
                            offset.snapTo((offset.value + delta).coerceIn(-reachPx, reachPx))
                        }
                    },
                    onDragStopped = {
                        val current = offset.value
                        if (current >= reachPx * 0.5f) {
                            haptic()
                            onStartAction()
                        } else if (current <= -reachPx * 0.5f) {
                            haptic()
                            onEndAction()
                        }
                        offset.animateTo(0f, CouiSwipeRevealDefaults.itemSettleSpec)
                    },
                ),
        ) {
            content()
        }
    }
}
