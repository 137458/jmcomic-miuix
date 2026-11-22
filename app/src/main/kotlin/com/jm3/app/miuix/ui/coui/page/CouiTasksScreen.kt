// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.page

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.ui.GlassSnackbarHost
import com.jm3.app.miuix.ui.components.rememberSelectionHaptic
import com.jm3.app.miuix.ui.coui.component.CouiEmptyGroup
import com.jm3.app.miuix.ui.coui.component.CouiExpandTopAppBarEffect
import com.jm3.app.miuix.ui.coui.component.CouiFeedRefreshAction
import com.jm3.app.miuix.ui.coui.component.CouiGroupRow
import com.jm3.app.miuix.ui.coui.component.CouiGroupTitle
import com.jm3.app.miuix.ui.coui.component.CouiLayoutTokens
import com.jm3.app.miuix.ui.coui.component.CouiLoadingGroup
import com.jm3.app.miuix.ui.coui.component.CouiMinTouchTarget
import com.jm3.app.miuix.ui.coui.component.CouiPageScaffold
import com.jm3.app.miuix.ui.coui.component.CouiPageTopBarSegmentedTabs
import com.jm3.app.miuix.ui.coui.component.CouiPullToRefresh
import com.jm3.app.miuix.ui.coui.component.CouiScreenFailureGroup
import com.jm3.app.miuix.ui.coui.component.CouiSegmentedCapsules
import com.jm3.app.miuix.ui.coui.component.couiPageScroll
import com.jm3.app.miuix.ui.coui.component.couiPressable
import com.jm3.app.miuix.ui.coui.component.isCouiWideWindow
import com.jm3.app.miuix.ui.coui.component.rememberCouiPageScrollBehavior
import com.jm3.app.miuix.ui.coui.icon.CouiIcons
import com.jm3.app.miuix.ui.coui.theme.CouiStrings
import com.jm3.app.miuix.ui.coui.theme.CouiTheme
import com.jm3.app.miuix.ui.list.ListLoadState
import com.jm3.app.miuix.ui.readableWidth
import com.jm3.app.miuix.ui.rememberSyncedTabPagerState
import com.jm3.app.miuix.ui.route.AppRoutes
import com.jm3.app.miuix.ui.tasks.TASK_STATE_DONE
import com.jm3.app.miuix.ui.tasks.TASK_STATE_UNDONE
import com.jm3.app.miuix.ui.tasks.TaskHostResolution
import com.jm3.app.miuix.ui.tasks.isTaskEquipped
import com.jm3.app.miuix.ui.tasks.rememberTasksScaffoldState
import com.jm3.app.miuix.ui.tasks.tasksEmptyHint
import com.jm3.app.miuix.ui.tasks.tasksEmptyTitle
import com.jm3.core.CoverLocator
import com.jm3.core.model.TaskItem
import com.jm3.core.model.TaskType
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.preference.PagerFooterPreference
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.rememberCOUIFlingBehavior
import top.yukonga.miuix.kmp.basic.SnackbarHostState

/**
 * COUI 成就页：称号/勋章按行卡排布，类型与筛选用分段胶囊切换。
 *
 * 接口不返回总数，所以顶栏只写"成就"不写统计；勋章图走 imageHost 拼相对路径，
 * host 取不到时退化为纯文字行，不阻塞整页。状态编排在 [rememberTasksScaffoldState]。
 */
@Composable
internal fun CouiTasksScreen(
    deps: ScreenDeps,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    nav: AppRoutes,
) {
    val haptic = rememberSelectionHaptic()
    val listStates = remember { List(TaskType.entries.size) { LazyListState() } }
    val scaffold = rememberTasksScaffoldState(
        deps = deps,
        nav = nav,
        snackbarHostState = snackbarHostState,
        haptic = haptic,
        failureMessage = "成就加载失败",
        failureFallback = CouiStrings.RetryLater,
        hostResolution = TaskHostResolution.AfterSuccess,
        onTabStarted = { tab -> listStates.getOrNull(tab)?.requestScrollToItem(0) },
    )
    val snapshots = scaffold.loaders.map { it.snapshot.collectAsState().value }
    val pagerState = rememberSyncedTabPagerState(
        selectedTab = scaffold.clampedTypeIndex,
        pageCount = scaffold.types.size,
        onTabSelected = { next ->
            haptic()
            scaffold.selectType(next)
        },
    )

    CouiTheme {
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

        Box(modifier = Modifier.fillMaxSize()) {
            CouiPageScaffold(
                title = "成就",
                onBack = onBack,
                wide = isCouiWideWindow(),
                scrollBehavior = scrollBehavior,
                attachScrollNestedScroll = false,
                actions = {
                    CouiFeedRefreshAction(
                        refreshing = snapshots[scaffold.clampedTypeIndex].refreshing,
                        onRefresh = scaffold.loaders[scaffold.clampedTypeIndex]::refresh,
                    )
                },
                topBarBottom = {
                    CouiPageTopBarSegmentedTabs(
                        items = scaffold.types.map { it.label },
                        selectedIndex = scaffold.clampedTypeIndex,
                        onSelect = { index -> scaffold.selectType(index) },
                    )
                },
            ) { innerPadding ->
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { pageIdx ->
                    val pageType = scaffold.types[pageIdx]
                    val pageSnapshot = snapshots[pageIdx]
                    val pageItems = pageSnapshot.feed.items
                    val pageExtra = scaffold.extra(pageIdx)
                    val pageLoader = scaffold.loaders[pageIdx]

                    CouiPullToRefresh(
                        isRefreshing = pageSnapshot.refreshing,
                        onRefresh = pageLoader::refresh,
                        scrollBehavior = scrollBehavior,
                        contentPadding = innerPadding,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .readableWidth()
                                .couiPageScroll()
                                .consumeWindowInsets(innerPadding),
                            state = listStates[pageIdx],
                            contentPadding = innerPadding,
                            flingBehavior = rememberCOUIFlingBehavior(),
                        ) {
                            item(key = "tasks-filter") {
                                CouiSegmentedCapsules(
                                    items = scaffold.filters.map { it.label },
                                    selectedIndex = scaffold.clampedFilterIndex,
                                    onSelect = { index -> scaffold.selectFilter(index) },
                                    small = true,
                                    modifier = Modifier.padding(
                                        horizontal = CouiLayoutTokens.HorizontalMargin,
                                        vertical = 8.dp,
                                    ),
                                )
                            }

                            val pageMessage = pageExtra?.message.orEmpty()
                            if (pageMessage.isNotBlank()) {
                                item(key = "tasks-message") {
                                    PagerFooterPreference(
                                        summary = pageMessage,
                                        summaryColor = COUITheme.colorScheme.error,
                                        withExtraMarginBottom = false,
                                    )
                                }
                            }

                            when (val pageUiState = pageSnapshot.state) {
                                ListLoadState.Loading -> item(key = "tasks-loading") {
                                    CouiLoadingGroup(
                                        modifier = Modifier.padding(top = 8.dp),
                                        text = "正在加载成就…",
                                    )
                                }
                                is ListLoadState.Failed -> item(key = "tasks-failed") {
                                    CouiScreenFailureGroup(
                                        message = pageUiState.message,
                                        expired = pageUiState.sessionExpired,
                                        onRetry = pageLoader::loadFirstPage,
                                        onOpenLogin = scaffold.nav::login,
                                        modifier = Modifier.padding(top = 8.dp),
                                    )
                                }
                                ListLoadState.Loaded -> if (pageItems.isEmpty()) {
                                    item(key = "tasks-empty") {
                                        CouiEmptyGroup(
                                            text = tasksEmptyTitle(pageType),
                                            modifier = Modifier.padding(top = 8.dp),
                                        )
                                    }
                                    item(key = "tasks-empty-hint") {
                                        PagerFooterPreference(
                                            summary = tasksEmptyHint(pageType),
                                            withExtraMarginBottom = false,
                                        )
                                    }
                                } else {
                                    item(key = "tasks-group-title") {
                                        CouiGroupTitle(title = pageType.label)
                                    }
                                    itemsIndexed(pageItems, key = { _, item -> "task-" + item.id }) { index, item ->
                                        CouiGroupRow(
                                            isFirst = index == 0,
                                            isLast = index == pageItems.lastIndex,
                                        ) {
                                            TaskRow(
                                                item = item,
                                                type = pageType,
                                                imageHost = pageExtra?.imageHost.orEmpty(),
                                                equipped = isTaskEquipped(
                                                    type = pageType,
                                                    item = item,
                                                    equippedTitle = scaffold.equippedTitleOverride,
                                                    equippedBadgeId = scaffold.equippedBadgeId,
                                                ),
                                                claiming = scaffold.claimingId == item.id,
                                                claimEnabled = scaffold.claimingId == null,
                                                onClaim = { scaffold.claim(pageType, item) },
                                            )
                                        }
                                    }
                                    item(key = "tasks-tail") {
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
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/**
 * 一行成就：称号用星形图标占位，勋章用服务端勋章图（未达成降透明度）。
 *
 * 佩戴态与达成态分开展示：已佩戴优先，因为它是当前生效值。
 */
@Composable
private fun TaskRow(
    item: TaskItem,
    type: TaskType,
    imageHost: String,
    equipped: Boolean,
    claiming: Boolean,
    claimEnabled: Boolean,
    onClaim: () -> Unit,
) {
    val scheme = COUITheme.colorScheme
    val title = if (type == TaskType.TITLE) item.content.ifBlank { item.name } else item.name
    val subtitle = if (type == TaskType.TITLE) {
        item.name.takeIf { it.isNotBlank() && it != item.content }
    } else {
        item.coin.takeIf { it > 0 }?.let { it.toString() + " J coin" }
    }
    val claimable = item.done && !equipped && claimEnabled

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(scheme.container4)
                .alpha(if (item.done) 1f else 0.45f),
            contentAlignment = Alignment.Center,
        ) {
            if (type == TaskType.BADGE && imageHost.isNotBlank() && item.content.isNotBlank()) {
                AsyncImage(
                    model = CoverLocator.of(imageHost).image(item.content),
                    contentDescription = item.name,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = CouiIcons.Star,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = if (item.done) scheme.primaryText else scheme.labelSecondary,
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = COUITheme.textStyles.headlineXS,
                color = scheme.labelPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = buildString {
                append(if (item.done) TASK_STATE_DONE else TASK_STATE_UNDONE)
                subtitle?.takeIf { it.isNotBlank() }?.let { append(" · "); append(it) }
            }
            Text(
                text = meta,
                style = COUITheme.textStyles.bodyXS,
                color = scheme.labelSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        when {
            equipped -> Text(
                text = "佩戴中",
                style = COUITheme.textStyles.bodyXS,
                color = scheme.primaryText,
            )
            claimable -> TaskActionChip(
                text = if (claiming) "切换中…" else "佩戴",
                enabled = !claiming,
                onClick = onClaim,
            )
            else -> Text(
                text = if (item.done) TASK_STATE_DONE else TASK_STATE_UNDONE,
                style = COUITheme.textStyles.bodyXS,
                color = scheme.labelSecondary,
            )
        }
    }
}

/** 行尾小胶囊动作，仅用于"佩戴"这类轻量操作。 */
@Composable
private fun TaskActionChip(text: String, enabled: Boolean, onClick: () -> Unit) {
    val scheme = COUITheme.colorScheme
    Box(
        modifier = Modifier
            .couiPressable(
                shape = CircleShape,
                backgroundColor = scheme.primary.copy(alpha = 0.14f),
                enabled = enabled,
                minTouchTarget = CouiMinTouchTarget,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = COUITheme.textStyles.bodyXS,
            color = if (enabled) scheme.primaryText else scheme.labelSecondary,
        )
    }
}
