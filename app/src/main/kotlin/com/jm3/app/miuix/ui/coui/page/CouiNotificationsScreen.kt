// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.page

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.ui.GlassSnackbarHost
import com.jm3.app.miuix.ui.components.rememberSelectionHaptic
import com.jm3.app.miuix.ui.coui.component.CouiBodyText
import com.jm3.app.miuix.ui.coui.component.CouiEmptyGroup
import com.jm3.app.miuix.ui.coui.component.CouiErrorGroup
import com.jm3.app.miuix.ui.coui.component.CouiExpandTopAppBarEffect
import com.jm3.app.miuix.ui.coui.component.CouiFeedLoginGroup
import com.jm3.app.miuix.ui.coui.component.CouiFeedRefreshAction
import com.jm3.app.miuix.ui.coui.component.CouiLayoutTokens
import com.jm3.app.miuix.ui.coui.component.CouiLoadingGroup
import com.jm3.app.miuix.ui.coui.component.CouiMinTouchTarget
import com.jm3.app.miuix.ui.coui.component.CouiPageScaffold
import com.jm3.app.miuix.ui.coui.component.CouiPageTopBarSegmentedTabs
import com.jm3.app.miuix.ui.coui.component.CouiPullToRefresh
import com.jm3.app.miuix.ui.coui.component.couiPageScroll
import com.jm3.app.miuix.ui.coui.component.couiPressable
import com.jm3.app.miuix.ui.coui.component.couiWorkCardShape
import com.jm3.app.miuix.ui.coui.component.isCouiWideWindow
import com.jm3.app.miuix.ui.coui.component.rememberCouiPageScrollBehavior
import com.jm3.app.miuix.ui.coui.theme.CouiTheme
import com.jm3.app.miuix.ui.notifications.NOTIFICATION_PREVIEW_UPDATES
import com.jm3.app.miuix.ui.notifications.NotificationTab
import com.jm3.app.miuix.ui.notifications.notificationEmptyHint
import com.jm3.app.miuix.ui.notifications.notificationMarkActionText
import com.jm3.app.miuix.ui.notifications.notificationTypeLabel
import com.jm3.app.miuix.ui.notifications.notificationUpdatesToggleText
import com.jm3.app.miuix.ui.notifications.rememberNotificationsScaffoldState
import com.jm3.app.miuix.ui.readableWidth
import com.jm3.app.miuix.ui.rememberSyncedTabPagerState
import com.jm3.app.miuix.ui.route.AppRoutes
import com.jm3.core.model.NotificationBody
import com.jm3.core.model.NotificationItem
import com.jm3.core.notificationBody
import com.jm3.core.notificationDisplayTitle
import com.jm3.core.session.UnreadBadge
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.preference.PagerFooterPreference
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.rememberCOUIFlingBehavior
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.SnackbarHostState

/** 侧滑露出动作的行程：38dp 的融合动作尺寸再留出标签宽度。 */
private val NotificationsSwipeReach = 96.dp

/** 融合滑动推离时的最大模糊半径（像素）与松手归位弹簧（对齐 COUI `COUISlideView` 阻尼 1 / 刚度 200）。 */
private const val NotificationsSwipeMaxBlurPx = 12f
private val NotificationsSwipeSettleSpec = spring<Float>(dampingRatio = 1f, stiffness = 200f)

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
    nav: AppRoutes,
) {
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    val scaffoldState = rememberNotificationsScaffoldState(
        deps = deps,
        snackbarHostState = snackbarHostState,
        unreadBadge = unreadBadge,
        guardStaleResponses = false,
        clearFailureOnKeptContent = true,
    )
    val page = scaffoldState.page
    val loading = scaffoldState.loading
    val error = scaffoldState.error
    val sessionExpired = scaffoldState.sessionExpired
    val refreshing = scaffoldState.refreshing
    val markingId = scaffoldState.markingId
    val items = scaffoldState.items

    CouiTheme {
        val listStates = remember { List(NotificationTab.entries.size) { LazyListState() } }
        val clampedTabIndex = tabIndex.coerceIn(0, NotificationTab.entries.lastIndex)
        val pagerState = rememberSyncedTabPagerState(
            selectedTab = clampedTabIndex,
            pageCount = NotificationTab.entries.size,
            onTabSelected = { if (it != tabIndex) tabIndex = it },
        )
        val activeCanScroll by remember(pagerState, listStates) {
            derivedStateOf {
                val active = listStates.getOrNull(pagerState.currentPage)
                active != null && (active.canScrollForward || active.canScrollBackward)
            }
        }
        val scrollBehavior = rememberCouiPageScrollBehavior { activeCanScroll }
        CouiExpandTopAppBarEffect(
            scrollBehavior = scrollBehavior,
            activeTab = pagerState.currentPage,
            canScroll = activeCanScroll,
        )
        val tabLabels = scaffoldState.tabLabels
        val subtitle = scaffoldState.subtitle

        Box(modifier = Modifier.fillMaxSize()) {
            CouiPageScaffold(
                title = "信箱",
                onBack = onBack,
                wide = isCouiWideWindow(),
                scrollBehavior = scrollBehavior,
                attachScrollNestedScroll = false,
                actions = {
                    CouiFeedRefreshAction(
                        refreshing = refreshing,
                        onRefresh = scaffoldState::refreshIfIdle,
                    )
                },
                topBarBottom = {
                    CouiPageTopBarSegmentedTabs(
                        items = tabLabels,
                        selectedIndex = clampedTabIndex,
                        onSelect = { tabIndex = it },
                    )
                },
            ) { contentPadding ->
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { pageIdx ->
                    val pageTab = NotificationTab.entries[pageIdx]
                    val pageVisible = scaffoldState.itemsFor(pageTab)
                    CouiPullToRefresh(
                        isRefreshing = refreshing,
                        onRefresh = scaffoldState::refreshIfIdle,
                        scrollBehavior = scrollBehavior,
                        contentPadding = contentPadding,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .readableWidth()
                                .couiPageScroll()
                                .consumeWindowInsets(contentPadding),
                            state = listStates[pageIdx],
                            contentPadding = contentPadding,
                            flingBehavior = rememberCOUIFlingBehavior(),
                        ) {
                            if (subtitle.isNotBlank()) {
                                item(key = "notif-subtitle") {
                                    PagerFooterPreference(summary = subtitle, withExtraMarginBottom = false)
                                }
                            }

                            when {
                                loading && page == null -> item(key = "notif-loading") {
                                    CouiLoadingGroup(
                                        modifier = Modifier.padding(top = 8.dp),
                                        text = "正在加载信箱…",
                                    )
                                }
                                sessionExpired -> item(key = "notif-expired") {
                                    CouiFeedLoginGroup(message = error.orEmpty(), onOpenLogin = nav::login)
                                }
                                error != null -> item(key = "notif-error") {
                                    CouiErrorGroup(
                                        message = error.orEmpty(),
                                        onRetry = { scaffoldState.load() },
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
                                        PagerFooterPreference(
                                            summary = notificationEmptyHint(pageTab),
                                            withExtraMarginBottom = false,
                                        )
                                    }
                                }
                                pageVisible.isEmpty() -> {
                                    item(key = "notif-tab-empty") {
                                        CouiEmptyGroup(
                                            text = "暂无此类消息",
                                            modifier = Modifier.padding(top = 8.dp),
                                        )
                                    }
                                    item(key = "notif-tab-empty-hint") {
                                        PagerFooterPreference(
                                            summary = notificationEmptyHint(pageTab),
                                            withExtraMarginBottom = false,
                                        )
                                    }
                                }
                                else -> {
                                    itemsIndexed(pageVisible, key = { _, item -> "notif-" + item.id }) { _, item ->
                                        val expanded = scaffoldState.isExpanded(item.id)
                                        CouiSwipeRow(
                                            startAction = notificationMarkActionText(marking = false, read = item.read),
                                            endAction = if (expanded) "收起" else "展开",
                                            onStartAction = { scaffoldState.toggleRead(item) },
                                            onEndAction = { scaffoldState.toggleExpanded(item.id) },
                                            modifier = Modifier
                                                .padding(horizontal = CouiLayoutTokens.HorizontalMargin)
                                                .padding(bottom = 8.dp),
                                        ) {
                                            NotificationCard(
                                                item = item,
                                                expanded = expanded,
                                                marking = markingId == item.id,
                                                onToggleExpanded = { scaffoldState.toggleExpanded(item.id) },
                                                onToggleRead = { scaffoldState.toggleRead(item) },
                                                onOpenAlbum = nav::album,
                                            )
                                        }
                                    }
                                    item(key = "notif-tail") {
                                        Spacer(modifier = Modifier.size(CouiLayoutTokens.GroupSpacing))
                                    }
                                }
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
    val scheme = COUITheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(couiWorkCardShape())
            .background(scheme.cardBackground)
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
                text = notificationTypeLabel(item.type),
                style = COUITheme.textStyles.bodyXS,
                color = scheme.labelSecondary,
                maxLines = 1,
            )
            val time = item.time
            if (!time.isNullOrBlank()) {
                Text(
                    text = "· " + time,
                    style = COUITheme.textStyles.bodyXS,
                    color = scheme.labelSecondary,
                    maxLines = 1,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            if (!item.read) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(scheme.primary),
                )
            }
        }
        Text(
            text = notificationDisplayTitle(item),
            style = COUITheme.textStyles.headlineXS,
            color = scheme.labelPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        when (val body = notificationBody(item.content)) {
            is NotificationBody.Updates -> {
                if (body.items.isEmpty()) {
                    Text(
                        text = "暂无作品更新",
                        style = COUITheme.textStyles.bodyS,
                        color = scheme.labelSecondary,
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
                                    style = COUITheme.textStyles.bodyXS,
                                    color = scheme.labelSecondary,
                                    maxLines = 1,
                                )
                            }
                            val comicId = update.comicId
                            if (comicId != null) {
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .couiPressable(
                                            shape = CircleShape,
                                            minTouchTarget = CouiMinTouchTarget,
                                            onClick = { onOpenAlbum(comicId) },
                                        ),
                                ) {
                                    CouiBodyText(
                                        text = update.comicTitle,
                                        color = scheme.primaryText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            } else {
                                CouiBodyText(
                                    text = update.comicTitle,
                                    color = scheme.primaryText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    if (body.items.size > NOTIFICATION_PREVIEW_UPDATES) {
                        Text(
                            text = notificationUpdatesToggleText(expanded, body.items.size),
                            style = COUITheme.textStyles.bodyM,
                            color = scheme.primaryText,
                        )
                    }
                }
            }
            is NotificationBody.Text -> if (body.text.isNotBlank()) {
                Text(
                    text = body.text,
                    style = COUITheme.textStyles.bodyS,
                    color = scheme.labelSecondary,
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
                    .clip(CircleShape)
                    .couiPressable(
                        shape = CircleShape,
                        minTouchTarget = CouiMinTouchTarget,
                        onClick = onToggleRead,
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Text(
                    text = notificationMarkActionText(marking, item.read),
                    style = COUITheme.textStyles.bodyXS,
                    color = if (marking) scheme.labelSecondary else scheme.primaryText,
                )
            }
        }
    }
}

internal enum class CouiSwipeActionTrigger {
    START,
    END,
    NONE,
}

/** 融合滑动松手判定：半程以上触发对应侧动作，未过半程回弹。 */
internal fun resolveCouiSwipeAction(offsetPx: Float, reachPx: Float): CouiSwipeActionTrigger {
    if (reachPx <= 0f) return CouiSwipeActionTrigger.NONE
    val threshold = reachPx * 0.5f
    return when {
        offsetPx >= threshold -> CouiSwipeActionTrigger.START
        offsetPx <= -threshold -> CouiSwipeActionTrigger.END
        else -> CouiSwipeActionTrigger.NONE
    }
}

/**
 * 融合滑动行：长按后横向拖拽把卡片推开，露出两侧动作，越过半程松手即提交。
 *
 * 长按启动手势将普通水平滑屏让给外层 [HorizontalPager]，避免从消息卡片上起滑无法切换页签；
 * 融合感来自推离程度的模糊渐变（[NotificationsSwipeMaxBlurPx]）与回弹弹簧（[NotificationsSwipeSettleSpec]），
 * 拖到一半松手会自然弹回原位。
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
    val scheme = COUITheme.colorScheme
    val scrim = (abs(offset.value) / reachPx).coerceIn(0f, 1f)
    val blurRadius = with(density) {
        (NotificationsSwipeMaxBlurPx * scrim.coerceAtLeast(0f)).toDp()
    }
    val scope = rememberCoroutineScope()
    val currentStartAction by rememberUpdatedState(onStartAction)
    val currentEndAction by rememberUpdatedState(onEndAction)

    Box(modifier = modifier.fillMaxWidth()) {
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
                    style = COUITheme.textStyles.bodyXS,
                    color = scheme.primaryText.copy(alpha = scrim.coerceAtLeast(0.25f)),
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
                    style = COUITheme.textStyles.bodyXS,
                    color = scheme.primaryText.copy(alpha = scrim.coerceAtLeast(0.25f)),
                )
            }
        }
        Box(
            modifier = Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .blur(blurRadius)
                .pointerInput(reachPx) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { haptic() },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch {
                                offset.snapTo((offset.value + dragAmount.x).coerceIn(-reachPx, reachPx))
                            }
                        },
                        onDragEnd = {
                            scope.launch {
                                when (resolveCouiSwipeAction(offset.value, reachPx)) {
                                    CouiSwipeActionTrigger.START -> {
                                        haptic()
                                        currentStartAction()
                                    }
                                    CouiSwipeActionTrigger.END -> {
                                        haptic()
                                        currentEndAction()
                                    }
                                    CouiSwipeActionTrigger.NONE -> Unit
                                }
                                offset.animateTo(0f, NotificationsSwipeSettleSpec)
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                offset.animateTo(0f, NotificationsSwipeSettleSpec)
                            }
                        },
                    )
                },
        ) {
            content()
        }
    }
}
