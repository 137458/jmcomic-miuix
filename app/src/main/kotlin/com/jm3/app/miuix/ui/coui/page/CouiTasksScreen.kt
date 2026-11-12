// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.page

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.itos.coui.effect.SpotlightPreset
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.layout.Footer
import com.itos.coui.scroll.rememberCouiOverscrollEffect
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.text.CouiTextKind
import com.itos.coui.dialog.CouiDialogDefaults
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme as CouiKitTheme
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.failureCopy
import com.jm3.app.miuix.ui.GlassSnackbarHost
import com.jm3.app.miuix.ui.ScreenLoadPhase
import com.jm3.app.miuix.ui.applyLoadPlan
import com.jm3.app.miuix.ui.coui.component.CouiEmptyGroup
import com.jm3.app.miuix.ui.coui.component.CouiErrorGroup
import com.jm3.app.miuix.ui.coui.component.CouiFeedLoginGroup
import com.jm3.app.miuix.ui.coui.component.CouiFeedRefreshAction
import com.jm3.app.miuix.ui.coui.component.CouiGroupRow
import com.jm3.app.miuix.ui.coui.component.CouiGroupTitle
import com.jm3.app.miuix.ui.coui.component.CouiLoadingGroup
import com.jm3.app.miuix.ui.coui.component.CouiMinTouchTarget
import com.jm3.app.miuix.ui.coui.component.CouiScreenScaffold
import com.jm3.app.miuix.ui.coui.component.CouiSegmentedCapsules
import com.jm3.app.miuix.ui.coui.component.CouiTopBarSegmentedTabs
import com.jm3.app.miuix.ui.coui.component.couiPressable
import com.jm3.app.miuix.ui.coui.icon.CouiIcons
import com.jm3.app.miuix.ui.coui.theme.CouiStrings
import com.jm3.app.miuix.ui.coui.theme.CouiTheme
import com.jm3.app.miuix.ui.readableWidth
import com.jm3.app.miuix.ui.rememberSelectionHaptic
import com.jm3.app.miuix.ui.rememberSyncedTabPagerState
import com.jm3.core.CoverLocator
import com.jm3.core.model.TaskFilter
import com.jm3.core.model.TaskPage
import com.jm3.core.model.TaskType
import com.jm3.core.model.TaskItem
import com.jm3.core.session.SessionOutcome
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SnackbarHostState

private const val TASK_STATE_DONE = "已达成"
private const val TASK_STATE_UNDONE = "未达成"

/** 成就页三态：称号/勋章条目不是 SearchResult，自带状态。 */
private sealed interface TasksUiState {
    data object Loading : TasksUiState
    data class Loaded(val page: TaskPage, val imageHost: String) : TasksUiState
    data class Failed(val message: String, val sessionExpired: Boolean = false) : TasksUiState
}

/**
 * COUI 成就页：称号/勋章按行卡排布，类型与筛选用分段胶囊切换。
 *
 * 接口不返回总数，所以顶栏只写"成就"不写统计；勋章图走 imageHost 拼相对路径，
 * host 取不到时退化为纯文字行，不阻塞整页。
 */
@Composable
internal fun CouiTasksScreen(
    deps: ScreenDeps,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    onOpenLogin: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val haptic = rememberSelectionHaptic()
    val user by deps.account.user.collectAsState()

    val types = TaskType.entries
    val filters = TaskFilter.entries
    var typeIndex by rememberSaveable { mutableIntStateOf(0) }
    var filterIndex by rememberSaveable { mutableIntStateOf(0) }
    val clampedTypeIndex = typeIndex.coerceIn(0, types.lastIndex)
    val clampedFilterIndex = filterIndex.coerceIn(0, filters.lastIndex)
    val states = remember { mutableStateMapOf<Int, TasksUiState>() }
    val listStates = remember { List(types.size) { LazyListState() } }
    var lastFilterIndex by remember { mutableIntStateOf(clampedFilterIndex) }
    var refreshingTab by remember { mutableStateOf<Int?>(null) }
    // 佩戴结果由本地覆盖，避免为了一个字段重新拉整页
    var equippedTitleOverride by remember { mutableStateOf<String?>(null) }
    var equippedBadgeId by remember { mutableStateOf<String?>(null) }
    var claimingId by remember { mutableStateOf<String?>(null) }
    // 请求代际号：快速切筛选时丢弃旧条件的迟到响应
    val requestTokens = remember { mutableStateMapOf<Int, Int>() }
    val pagerState = rememberSyncedTabPagerState(
        selectedTab = clampedTypeIndex,
        pageCount = types.size,
        onTabSelected = { next ->
            haptic()
            typeIndex = next
        },
    )

    // 换账号后旧的佩戴态不再成立
    LaunchedEffect(user?.uid) {
        equippedTitleOverride = null
        equippedBadgeId = null
    }

    fun load(targetTypeIdx: Int = clampedTypeIndex, showLoading: Boolean = true) {
        val token = (requestTokens[targetTypeIdx] ?: 0) + 1
        requestTokens[targetTypeIdx] = token
        val requestType = types[targetTypeIdx]
        val requestFilterIdx = clampedFilterIndex
        val requestFilter = filters[requestFilterIdx]
        if (showLoading) states[targetTypeIdx] = TasksUiState.Loading
        scope.launch {
            val outcome = deps.authCall.call { deps.gateway.tasks(requestType, requestFilter) }
            if (requestTokens[targetTypeIdx] != token || clampedFilterIndex != requestFilterIdx) return@launch
            if (refreshingTab == targetTypeIdx) refreshingTab = null
            when (outcome) {
                is SessionOutcome.Ok -> {
                    val host = (deps.authCall.call { deps.images.imageHost() } as? SessionOutcome.Ok)
                        ?.value
                        .orEmpty()
                        .ifBlank {
                            (deps.authCall.call { deps.routes.usedDomain() } as? SessionOutcome.Ok)
                                ?.value
                                .orEmpty()
                        }
                    if (requestTokens[targetTypeIdx] != token || clampedFilterIndex != requestFilterIdx) return@launch
                    states[targetTypeIdx] = TasksUiState.Loaded(outcome.value, host)
                }
                else -> scope.applyLoadPlan(
                    outcome, hasContent = states[targetTypeIdx] is TasksUiState.Loaded,
                    failureMessage = "成就加载失败", snackbarHostState = snackbarHostState,
                ) { plan ->
                    states[targetTypeIdx] = TasksUiState.Failed(
                        message = plan.message.orEmpty(),
                        sessionExpired = plan.phase == ScreenLoadPhase.Expired,
                    )
                }
            }
        }
    }

    fun claim(targetType: TaskType, item: TaskItem) {
        val uid = user?.uid
        if (uid.isNullOrBlank()) {
            onOpenLogin()
            return
        }
        if (claimingId != null) return
        claimingId = item.id
        scope.launch {
            val outcome = deps.authCall.call { deps.gateway.claimTask(targetType, uid, item.id) }
            claimingId = null
            val failure = when (outcome) {
                is SessionOutcome.Ok -> outcome.value
                is SessionOutcome.Expired -> "登录已失效，请重新登录"
                is SessionOutcome.Failed -> failureCopy(outcome, CouiStrings.RetryLater)
            }
            if (failure == null) {
                // 佩戴成功：称号记内容文本，勋章记 id，两者互斥
                if (targetType == TaskType.TITLE) {
                    equippedTitleOverride = item.content
                } else {
                    equippedBadgeId = item.id
                }
                haptic()
                snackbarHostState.showSnackbar("已佩戴「" + item.name + "」")
            } else {
                snackbarHostState.showSnackbar("切换失败：" + failure)
            }
        }
    }

    LaunchedEffect(clampedTypeIndex, clampedFilterIndex) {
        val filterChanged = lastFilterIndex != clampedFilterIndex
        if (filterChanged) {
            lastFilterIndex = clampedFilterIndex
            states.clear()
        }
        if (filterChanged || states[clampedTypeIndex] == null) {
            listStates[clampedTypeIndex].requestScrollToItem(0)
            load(targetTypeIdx = clampedTypeIndex, showLoading = true)
        }
    }

    CouiTheme {
        val overscroll = rememberCouiOverscrollEffect()

        Box(modifier = Modifier.fillMaxSize()) {
            CouiScreenScaffold(
                title = "成就",
                onBack = onBack,
                actions = {
                    CouiFeedRefreshAction(
                        refreshing = refreshingTab == clampedTypeIndex,
                        onRefresh = {
                            if (refreshingTab != clampedTypeIndex) {
                                refreshingTab = clampedTypeIndex
                                load(targetTypeIdx = clampedTypeIndex, showLoading = false)
                            }
                        },
                    )
                },
                topBarBottom = {
                    CouiTopBarSegmentedTabs(
                        items = types.map { it.label },
                        selectedIndex = clampedTypeIndex,
                        onSelect = { typeIndex = it },
                    )
                },
            ) { innerPadding ->
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { pageIdx ->
                    val pageType = types[pageIdx]
                    val pageState = states[pageIdx] ?: TasksUiState.Loading
                    val pageLoaded = pageState as? TasksUiState.Loaded
                    val items = pageLoaded?.page?.items.orEmpty()

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .readableWidth()
                            .consumeWindowInsets(innerPadding),
                        state = listStates[pageIdx],
                        contentPadding = innerPadding,
                        overscrollEffect = overscroll,
                    ) {
                        item(key = "tasks-filter") {
                            CouiSegmentedCapsules(
                                items = filters.map { it.label },
                                selectedIndex = clampedFilterIndex,
                                onSelect = { filterIndex = it },
                                small = true,
                                modifier = Modifier.padding(
                                    horizontal = CouiPageDefaults.horizontalMargin,
                                    vertical = 8.dp,
                                ),
                            )
                        }

                        // 服务端 message（如"暂未登录"）先播出来，再按空态处理
                        val pageMessage = pageLoaded?.page?.message.orEmpty()
                        if (pageMessage.isNotBlank()) {
                            item(key = "tasks-message") {
                                Footer(
                                    text = pageMessage,
                                    color = CouiDialogDefaults.themeColors(CouiKitTheme.isDark).destructive,
                                )
                            }
                        }

                        when (pageState) {
                            TasksUiState.Loading -> item(key = "tasks-loading") {
                                CouiLoadingGroup(
                                    modifier = Modifier.padding(top = 8.dp),
                                    text = "正在加载成就…",
                                )
                            }
                            is TasksUiState.Failed -> if (pageState.sessionExpired) {
                                item(key = "tasks-expired") {
                                    CouiFeedLoginGroup(message = pageState.message, onOpenLogin = onOpenLogin)
                                }
                            } else {
                                item(key = "tasks-error") {
                                    CouiErrorGroup(
                                        message = pageState.message,
                                        onRetry = { load(targetTypeIdx = pageIdx) },
                                        modifier = Modifier.padding(top = 8.dp),
                                    )
                                }
                            }
                            is TasksUiState.Loaded -> if (items.isEmpty()) {
                                item(key = "tasks-empty") {
                                    CouiEmptyGroup(
                                        text = "暂无" + pageType.label,
                                        modifier = Modifier.padding(top = 8.dp),
                                    )
                                }
                                item(key = "tasks-empty-hint") {
                                    Footer(
                                        text = if (pageType == TaskType.TITLE) {
                                            "继续阅读与收藏即可解锁称号"
                                        } else {
                                            "完成成就后可在此获得勋章"
                                        },
                                    )
                                }
                            } else {
                                item(key = "tasks-group-title") {
                                    CouiGroupTitle(title = pageType.label)
                                }
                                itemsIndexed(items, key = { _, item -> "task-" + item.id }) { index, item ->
                                    CouiGroupRow(
                                        isFirst = index == 0,
                                        isLast = index == items.lastIndex,
                                    ) {
                                        TaskRow(
                                            item = item,
                                            type = pageType,
                                            imageHost = pageState.imageHost,
                                            equipped = if (pageType == TaskType.TITLE) {
                                                equippedTitleOverride == item.content
                                            } else {
                                                equippedBadgeId == item.id
                                            },
                                            claiming = claimingId == item.id,
                                            claimEnabled = claimingId == null,
                                            onClaim = { claim(pageType, item) },
                                        )
                                    }
                                }
                                item(key = "tasks-tail") {
                                    Spacer(modifier = Modifier.size(CouiPageDefaults.groupSpacing))
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
    val scheme = CouiKitTheme.colorScheme
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
                .background(scheme.container)
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
                    tint = if (item.done) scheme.accent else scheme.onSurfaceVariant,
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                kind = CouiTextKind.ItemTitle,
                color = scheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // 达成态在前，副标题（称号原名 / 金币数）在后：两者都是判断要不要佩戴的依据
            val meta = buildString {
                append(if (item.done) TASK_STATE_DONE else TASK_STATE_UNDONE)
                subtitle?.takeIf { it.isNotBlank() }?.let { append(" · "); append(it) }
            }
            Text(
                text = meta,
                kind = CouiTextKind.GroupFooter,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        when {
            equipped -> Text(
                text = "佩戴中",
                kind = CouiTextKind.GroupFooter,
                color = scheme.accent,
            )
            claimable -> TaskActionChip(
                text = if (claiming) "切换中…" else "佩戴",
                enabled = !claiming,
                onClick = onClaim,
            )
            else -> Text(
                text = if (item.done) TASK_STATE_DONE else TASK_STATE_UNDONE,
                kind = CouiTextKind.GroupFooter,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

/** 行尾小胶囊动作，仅用于"佩戴"这类轻量操作。 */
@Composable
private fun TaskActionChip(text: String, enabled: Boolean, onClick: () -> Unit) {
    val scheme = CouiKitTheme.colorScheme
    Box(
        modifier = Modifier
            .couiPressable(
                shape = CouiCapsuleShape,
                backgroundColor = scheme.accent.copy(alpha = 0.14f),
                spotlight = SpotlightPreset.ChipTab,
                enabled = enabled,
                minTouchTarget = CouiMinTouchTarget,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            kind = CouiTextKind.GroupFooter,
            color = if (enabled) scheme.accent else scheme.onSurfaceVariant,
        )
    }
}
