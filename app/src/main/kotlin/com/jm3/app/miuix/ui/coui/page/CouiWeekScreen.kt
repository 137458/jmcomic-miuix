// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.page

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.layout.Footer
import com.itos.coui.scroll.rememberCouiOverscrollEffect
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.shape.CouiShape
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme as CouiKitTheme
import com.itos.coui.topappbar.CouiTopAppBarDefaults
import com.itos.coui.topappbar.LargeTopAppBar
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.ui.ScreenLoadPhase
import com.jm3.app.miuix.ui.coui.component.CouiAppScaffold
import com.jm3.app.miuix.ui.coui.component.CouiEmptyGroup
import com.jm3.app.miuix.ui.coui.component.CouiErrorGroup
import com.jm3.app.miuix.ui.coui.component.CouiLoadingGroup
import com.jm3.app.miuix.ui.coui.component.CouiTopBarBackButton
import com.jm3.app.miuix.ui.coui.component.CouiTopBarTitle
import com.jm3.app.miuix.ui.coui.component.CouiWorkRowCard
import com.jm3.app.miuix.ui.coui.component.couiWorkCardShape
import com.jm3.app.miuix.ui.coui.component.couiPressable
import com.jm3.app.miuix.ui.coui.component.couiScreenContentPadding
import com.jm3.app.miuix.ui.coui.component.couiTopAppBarColors
import com.jm3.app.miuix.ui.coui.icon.CouiIcons
import com.jm3.app.miuix.ui.coui.theme.CouiTheme
import com.jm3.app.miuix.ui.planLoad
import com.jm3.app.miuix.ui.readableWidth
import com.jm3.core.model.SearchResult
import com.jm3.core.model.WeeklyPeriod
import com.jm3.core.model.WeeklyPicksDetail
import com.jm3.core.session.SessionOutcome
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon

/** 时间轴左侧轨道宽度：圆点与竖线都落在这条带子里。 */
private val WeekRailWidth = 28.dp

/** 圆点在行内的垂直位置：略高于封面中心，与标题基线对齐。 */
private val WeekRailDotCenter = 24.dp

private val WeekRailDotRadius = 4.dp

/**
 * COUI 每周必看页：期数胶囊条 + 时间轴列表，签名与原页面一致。
 *
 * 数据链路不变：先取期数（[WeeklyPicks]），再按期取详情（[WeeklyPicksDetail]）。
 * 快速切期用代际号丢弃旧响应，否则慢响应会把当前期覆盖成上一期内容。
 */
@Composable
internal fun CouiWeekScreen(
    deps: ScreenDeps,
    onBack: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenLogin: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var periods by remember { mutableStateOf<List<WeeklyPeriod>>(emptyList()) }
    var periodsLoading by remember { mutableStateOf(true) }
    var periodsFailure by remember { mutableStateOf<String?>(null) }
    var periodsExpired by remember { mutableStateOf(false) }
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var detail by remember { mutableStateOf<WeeklyPicksDetail?>(null) }
    var detailLoading by remember { mutableStateOf(false) }
    var detailFailure by remember { mutableStateOf<String?>(null) }
    var detailExpired by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    // 切期代际号：只认最后一次选择的响应
    var detailToken by remember { mutableIntStateOf(0) }

    fun loadDetail(index: Int, showLoading: Boolean = true) {
        val period = periods.getOrNull(index) ?: return
        val token = ++detailToken
        if (showLoading) {
            detailLoading = true
            detail = null
        }
        detailFailure = null
        detailExpired = false
        scope.launch {
            val outcome = deps.authCall.call { deps.gateway.weeklyPicksDetail(period.id) }
            if (token != detailToken) return@launch
            detailLoading = false
            when (outcome) {
                is SessionOutcome.Ok -> detail = outcome.value
                else -> {
                    val plan = planLoad(outcome, hasContent = false, failureMessage = "加载失败")
                    detailExpired = plan.phase == ScreenLoadPhase.Expired
                    detailFailure = plan.message
                }
            }
        }
    }

    fun loadPeriods() {
        periodsFailure = null
        periodsExpired = false
        scope.launch {
            val outcome = deps.authCall.call { deps.gateway.weeklyPicks() }
            refreshing = false
            periodsLoading = false
            when (outcome) {
                is SessionOutcome.Ok -> {
                    periods = outcome.value.periods
                    val index = selected.coerceIn(0, (periods.size - 1).coerceAtLeast(0))
                    selected = index
                    if (periods.isEmpty()) {
                        detail = null
                        detailLoading = false
                    } else {
                        loadDetail(index)
                    }
                }
                else -> {
                    val plan = planLoad(outcome, hasContent = periods.isNotEmpty(), failureMessage = "加载失败")
                    if (plan.keepContent) {
                        // 有旧期数时刷新失败只降级提示，不把已有内容清空
                        periodsFailure = null
                    } else {
                        periodsExpired = plan.phase == ScreenLoadPhase.Expired
                        periodsFailure = plan.message
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) { loadPeriods() }

    CouiTheme {
        val listState = rememberLazyListState()
        val overscroll = rememberCouiOverscrollEffect()
        val scrollBehavior = CouiTopAppBarDefaults.exitUntilCollapsedScrollBehavior()
        val scheme = CouiKitTheme.colorScheme
        val currentPeriod = periods.getOrNull(selected)

        CouiAppScaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                LargeTopAppBar(
                    title = { CouiTopBarTitle(text = "每周必看", textAlign = TextAlign.Start) },
                    navigationIcon = { CouiTopBarBackButton(onClick = onBack) },
                    actions = {
                        CouiFeedRefreshAction(
                            refreshing = refreshing,
                            onRefresh = {
                                if (!refreshing) {
                                    refreshing = true
                                    loadPeriods()
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
                if (periods.isNotEmpty()) {
                    item(key = "week-periods") {
                        WeekPeriodRow(
                            periods = periods,
                            selected = selected,
                            onSelect = { index ->
                                if (index != selected) {
                                    selected = index
                                    loadDetail(index)
                                }
                            },
                        )
                    }
                }
                item(key = "week-subtitle") {
                    Footer(
                        text = buildString {
                            append("每周五 18:00 更新")
                            val time = currentPeriod?.time.orEmpty()
                            if (time.isNotBlank()) {
                                append(" · ")
                                append(time)
                            }
                        },
                    )
                }

                when {
                    periodsLoading -> item(key = "week-loading") {
                        CouiLoadingGroup(
                            modifier = Modifier.padding(top = 8.dp),
                            text = "正在加载每周必看…",
                        )
                    }
                    periodsExpired -> item(key = "week-expired") {
                        CouiFeedLoginGroup(message = periodsFailure.orEmpty(), onOpenLogin = onOpenLogin)
                    }
                    periodsFailure != null -> item(key = "week-failed") {
                        CouiErrorGroup(
                            message = periodsFailure.orEmpty(),
                            onRetry = { periodsLoading = true; loadPeriods() },
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    periods.isEmpty() -> {
                        item(key = "week-empty") {
                            CouiEmptyGroup(
                                text = "暂无每周必看",
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        item(key = "week-empty-hint") { Footer(text = "稍后再来看看") }
                    }
                    detailLoading -> item(key = "week-detail-loading") {
                        CouiLoadingGroup(
                            modifier = Modifier.padding(top = 8.dp),
                            text = "正在加载本期内容…",
                        )
                    }
                    detailExpired -> item(key = "week-detail-expired") {
                        CouiFeedLoginGroup(message = detailFailure.orEmpty(), onOpenLogin = onOpenLogin)
                    }
                    detailFailure != null -> item(key = "week-detail-failed") {
                        CouiErrorGroup(
                            message = detailFailure.orEmpty(),
                            onRetry = { loadDetail(selected) },
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    else -> {
                        val items = detail?.items.orEmpty()
                        if (items.isEmpty()) {
                            item(key = "week-detail-empty") {
                                CouiEmptyGroup(
                                    text = "本期暂无内容",
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                        } else {
                            itemsIndexed(items, key = { _, item -> "week-" + item.id }) { index, item ->
                                WeekTimelineRow(
                                    item = item,
                                    isFirst = index == 0,
                                    isLast = index == items.lastIndex,
                                    railColor = scheme.outlineVariant,
                                    dotColor = scheme.accent,
                                    onClick = { onOpenAlbum(item.id) },
                                )
                            }
                            item(key = "week-tail") { Spacer(modifier = Modifier.size(CouiPageDefaults.groupSpacing)) }
                        }
                    }
                }
            }
        }
    }
}

/** 时间轴单行：左轨道用 drawBehind 画，圆点按行高居中偏上，避免额外布局层。 */
@Composable
private fun WeekTimelineRow(
    item: SearchResult,
    isFirst: Boolean,
    isLast: Boolean,
    railColor: androidx.compose.ui.graphics.Color,
    dotColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    val scheme = CouiKitTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .drawBehind {
                val centerX = WeekRailWidth.toPx() / 2f
                val centerY = WeekRailDotCenter.toPx()
                val radius = WeekRailDotRadius.toPx()
                val stroke = 1.dp.toPx()
                if (!isFirst) {
                    drawLine(
                        color = railColor,
                        start = Offset(centerX, 0f),
                        end = Offset(centerX, centerY - radius),
                        strokeWidth = stroke,
                    )
                }
                if (!isLast) {
                    drawLine(
                        color = railColor,
                        start = Offset(centerX, centerY + radius),
                        end = Offset(centerX, size.height),
                        strokeWidth = stroke,
                    )
                }
                drawCircle(color = dotColor, radius = radius, center = Offset(centerX, centerY))
            },
        verticalAlignment = Alignment.Top,
    ) {
        Spacer(modifier = Modifier.width(WeekRailWidth))
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(couiWorkCardShape())
                .background(scheme.card),
        ) {
            CouiWorkRowCard(
                coverUrl = item.coverUrl,
                title = item.title,
                onClick = onClick,
                albumId = item.id,
                subtitle = item.authors.joinToString(" / "),
                category = item.category,
            )
        }
    }
}

/** 期数胶囊条：期数可能很多，横向滚动比等分分段更稳。 */
@Composable
private fun WeekPeriodRow(
    periods: List<WeeklyPeriod>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    val scheme = CouiKitTheme.colorScheme
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        itemsIndexed(periods, key = { _, period -> "period-" + period.id }) { index, period ->
            val active = index == selected
            Box(
                modifier = Modifier
                    .clip(CouiCapsuleShape)
                    .background(if (active) scheme.accent.copy(alpha = 0.14f) else scheme.container)
                    .couiPressable(shape = CouiCapsuleShape, onClick = { onSelect(index) })
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = period.title,
                    kind = CouiTextKind.PreferenceValue,
                    color = if (active) scheme.accent else scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 顶栏刷新胶囊：信息流各页共用同一形态，避免每页各写一套。 */
@Composable
internal fun CouiFeedRefreshAction(refreshing: Boolean, onRefresh: () -> Unit) {
    val scheme = CouiKitTheme.colorScheme
    val tint = if (refreshing) scheme.onSurfaceVariant else scheme.accent
    Row(
        modifier = Modifier
            .clip(CouiCapsuleShape)
            .couiPressable(shape = CouiCapsuleShape, enabled = !refreshing, onClick = onRefresh)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(imageVector = CouiIcons.Refresh, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Text(text = if (refreshing) "刷新中" else "刷新", kind = CouiTextKind.Description, color = tint)
    }
}

/** 登录过期时的落点：解释原因并把重新登录入口放在手边，信息流各页共用。 */
@Composable
internal fun CouiFeedLoginGroup(message: String, onOpenLogin: () -> Unit) {
    CouiErrorGroup(
        message = message.ifBlank { "登录已过期，请重新登录" },
        onRetry = onOpenLogin,
        modifier = Modifier.padding(top = 8.dp),
    )
}
