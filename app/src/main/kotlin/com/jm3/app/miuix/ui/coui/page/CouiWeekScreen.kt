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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.itos.coui.effect.SpotlightPreset
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
import com.jm3.app.miuix.ui.GlassSnackbarHost
import com.jm3.app.miuix.ui.ScreenLoadPhase
import com.jm3.app.miuix.ui.coui.component.CouiAppScaffold
import com.jm3.app.miuix.ui.coui.component.CouiEmptyGroup
import com.jm3.app.miuix.ui.coui.component.CouiErrorGroup
import com.jm3.app.miuix.ui.coui.component.CouiFeedLoginGroup
import com.jm3.app.miuix.ui.coui.component.CouiFeedRefreshAction
import com.jm3.app.miuix.ui.coui.component.CouiLoadingGroup
import com.jm3.app.miuix.ui.coui.component.CouiMinTouchTarget
import com.jm3.app.miuix.ui.coui.component.CouiTopBarBackButton
import com.jm3.app.miuix.ui.coui.component.CouiTopBarTitle
import com.jm3.app.miuix.ui.coui.component.CouiWorkRowCard
import com.jm3.app.miuix.ui.coui.component.couiWorkCardShape
import com.jm3.app.miuix.ui.coui.component.couiPressable
import com.jm3.app.miuix.ui.coui.component.couiScreenContentPadding
import com.jm3.app.miuix.ui.coui.component.couiTopAppBarColors
import com.jm3.app.miuix.ui.coui.icon.CouiIcons
import com.jm3.app.miuix.ui.coui.theme.CouiTheme
import com.jm3.app.miuix.ui.readableWidth
import com.jm3.app.miuix.ui.route.AppRoutes
import com.jm3.app.miuix.ui.week.WeekDetailLoadingStyle
import com.jm3.app.miuix.ui.week.WeekPeriodsFailureStyle
import com.jm3.app.miuix.ui.week.WeekRefreshReset
import com.jm3.app.miuix.ui.week.rememberWeekScaffoldState
import com.jm3.core.model.SearchResult
import com.jm3.core.model.WeeklyPeriod
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SnackbarHostState

/** 时间轴左侧轨道宽度：圆点与竖线都落在这条带子里。 */
private val WeekRailWidth = 28.dp

/** 圆点在行内的垂直位置：略高于封面中心，与标题基线对齐。 */
private val WeekRailDotCenter = 24.dp

private val WeekRailDotRadius = 4.dp

/**
 * COUI 每周必看页：期数胶囊条 + 时间轴列表，签名与原页面一致。
 *
 * 数据链路不变：先取期数，再按期取详情；请求编排与代际号守卫在共享状态里，本页只负责画。
 */
@Composable
internal fun CouiWeekScreen(
    deps: ScreenDeps,
    onBack: () -> Unit,
    nav: AppRoutes,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val user by deps.account.user.collectAsState()
    // 取期数、按期取详情、切期、刷新、失败落点都在共享状态里，本页只负责画
    val week = rememberWeekScaffoldState(
        loadPicks = { deps.authCall.call { deps.gateway.weeklyPicks() } },
        loadPicksDetail = { periodId -> deps.authCall.call { deps.gateway.weeklyPicksDetail(periodId) } },
        periodsFailureStyle = WeekPeriodsFailureStyle.KeepContentNotice,
        detailLoadingStyle = WeekDetailLoadingStyle.ClearContent,
        refreshReset = WeekRefreshReset.WithPeriods,
        showMessage = { message -> snackbarHostState.showSnackbar(message) },
        // 账号变了期数可能跟着变：登录态是重取期数的时机
        reloadKey = user?.uid,
        showLoadingOnEnter = false,
    )

    CouiTheme {
        val listState = rememberLazyListState()
        val overscroll = rememberCouiOverscrollEffect()
        val scrollBehavior = CouiTopAppBarDefaults.exitUntilCollapsedScrollBehavior()
        val scheme = CouiKitTheme.colorScheme
        val currentPeriod = week.currentPeriod
        val periodsFailure = week.periodsFailure
        val detailFailure = week.detailFailure

        Box(modifier = Modifier.fillMaxSize()) {
            CouiAppScaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                LargeTopAppBar(
                    title = { CouiTopBarTitle(text = "每周必看", textAlign = TextAlign.Start) },
                    navigationIcon = { CouiTopBarBackButton(onClick = onBack) },
                    actions = {
                        CouiFeedRefreshAction(
                            refreshing = week.refreshing,
                            onRefresh = {
                                // 刷新中不重复触发：指示器已经在转
                                if (!week.refreshing) week.refresh()
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
                if (week.periods.isNotEmpty()) {
                    item(key = "week-periods") {
                        WeekPeriodRow(
                            periods = week.periods,
                            selected = week.selected,
                            onSelect = { index -> week.selectPeriod(index) },
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
                    week.periodsLoading -> item(key = "week-loading") {
                        CouiLoadingGroup(
                            modifier = Modifier.padding(top = 8.dp),
                            text = "正在加载每周必看…",
                        )
                    }
                    periodsFailure?.phase == ScreenLoadPhase.Expired -> item(key = "week-expired") {
                        CouiFeedLoginGroup(message = periodsFailure?.message.orEmpty(), onOpenLogin = nav::login)
                    }
                    periodsFailure != null -> item(key = "week-failed") {
                        CouiErrorGroup(
                            message = periodsFailure?.message.orEmpty(),
                            onRetry = { week.loadPeriods() },
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    week.periods.isEmpty() -> {
                        item(key = "week-empty") {
                            CouiEmptyGroup(
                                text = "暂无每周必看",
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        item(key = "week-empty-hint") { Footer(text = "稍后再来看看") }
                    }
                    week.detailLoading -> item(key = "week-detail-loading") {
                        CouiLoadingGroup(
                            modifier = Modifier.padding(top = 8.dp),
                            text = "正在加载本期内容…",
                        )
                    }
                    detailFailure?.phase == ScreenLoadPhase.Expired -> item(key = "week-detail-expired") {
                        CouiFeedLoginGroup(message = detailFailure?.message.orEmpty(), onOpenLogin = nav::login)
                    }
                    detailFailure != null -> item(key = "week-detail-failed") {
                        CouiErrorGroup(
                            message = detailFailure?.message.orEmpty(),
                            onRetry = { week.retryDetail() },
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    else -> {
                        val items = week.detail?.items.orEmpty()
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
                                    onClick = { nav.album(item.id) },
                                )
                            }
                            item(key = "week-tail") { Spacer(modifier = Modifier.size(CouiPageDefaults.groupSpacing)) }
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
            .padding(horizontal = CouiPageDefaults.horizontalMargin)
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
        CouiWorkRowCard(
            coverUrl = item.coverUrl,
            title = item.title,
            onClick = onClick,
            albumId = item.id,
            subtitle = item.authors.joinToString(" / "),
            category = item.category,
            modifier = Modifier.weight(1f),
        )
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
        contentPadding = PaddingValues(horizontal = CouiPageDefaults.horizontalMargin, vertical = 4.dp),
    ) {
        itemsIndexed(periods, key = { _, period -> "period-" + period.id }) { index, period ->
            val active = index == selected
            Box(
                modifier = Modifier
                    .couiPressable(
                        shape = CouiCapsuleShape,
                        backgroundColor = if (active) scheme.accent.copy(alpha = 0.14f) else scheme.container,
                        spotlight = SpotlightPreset.ChipTab,
                        minTouchTarget = CouiMinTouchTarget,
                        onClick = { onSelect(index) },
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = period.title,
                    kind = CouiTextKind.ItemValue,
                    color = if (active) scheme.accent else scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
