package com.jm3.app.miuix.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.ui.components.BackButton
import com.jm3.app.miuix.ui.components.CenteredEmptyState
import com.jm3.app.miuix.ui.components.EmptyState
import com.jm3.app.miuix.ui.components.ErrorScreen
import com.jm3.app.miuix.ui.components.LoadingScreen
import com.jm3.app.miuix.ui.components.SectionTextStartPadding
import com.jm3.app.miuix.ui.components.TrackedLazyColumn
import com.jm3.app.miuix.ui.components.searchResultItems
import com.jm3.app.miuix.ui.glass.GlassPullToRefresh
import com.jm3.app.miuix.ui.glass.GlassScaffold
import com.jm3.app.miuix.ui.glass.GlassTopAppBar
import com.jm3.app.miuix.ui.route.AppRoutes
import com.jm3.app.miuix.ui.skin.LocalSkin
import com.jm3.app.miuix.ui.week.WeekDetailLoadingStyle
import com.jm3.app.miuix.ui.week.WeekPeriodsFailureStyle
import com.jm3.app.miuix.ui.week.WeekRefreshReset
import com.jm3.app.miuix.ui.week.rememberWeekScaffoldState
import com.jm3.core.paging.PagedFeed
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 每周必看：期数下拉 + 当期作品列表。
 * 原版页面为「期数下拉 + 类型分段 + 分页」；协议库的期数详情只接受期数 id，
 * 不支持类型与页码参数，因此这里只做期数切换，不伪造类型筛选。
 */
@Composable
internal fun WeekScreen(
    deps: ScreenDeps,
    onBack: () -> Unit,
    nav: AppRoutes,
) {
    val skin = LocalSkin.current
    val refreshState = rememberPullToRefreshState(refreshThreshold = 0.18f)
    // 取期数、按期取详情、切期、失败落点都在共享状态里，本页只负责画
    val week = rememberWeekScaffoldState(
        loadPicks = { deps.authCall.call { deps.gateway.weeklyPicks() } },
        loadPicksDetail = { periodId -> deps.authCall.call { deps.gateway.weeklyPicksDetail(periodId) } },
        periodsFailureStyle = WeekPeriodsFailureStyle.SplitAcrossSlots,
        detailLoadingStyle = WeekDetailLoadingStyle.KeepContent,
        refreshReset = WeekRefreshReset.WithDetail,
    )

    GlassScaffold(
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                GlassTopAppBar(
                    title = "每周必看",
                    subtitle = "每周五 18:00 更新",
                    // 透明底色：玻璃底色与模糊由 GlassScaffold 统一绘制
                    color = Color.Transparent,
                    navigationIcon = { BackButton(onBack = onBack) },
                )
                if (week.periods.isNotEmpty()) {
                    val topCurrent = week.periods[week.selected.coerceIn(0, week.periods.lastIndex)]
                    // 期数选择器固定于列表之外：与顶栏同处玻璃层，列表滚动后仍可切期
                    Card(cornerRadius = skin.shapes.cardCorner, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        OverlaySpinnerPreference(
                            items = week.periods.map { DropdownItem(text = it.title, selected = it.id == topCurrent.id) },
                            selectedIndex = week.selected.coerceIn(0, week.periods.lastIndex),
                            title = "期数",
                            summary = topCurrent.time.ifBlank { topCurrent.title },
                            showValue = false,
                            onSelectedIndexChange = { index -> week.selectPeriod(index) },
                        )
                    }
                    // 期数时间范围紧跟选择器，和原版菜单里的时间文案对应
                    if (topCurrent.time.isNotBlank()) {
                        Text(
                            text = topCurrent.time,
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.fillMaxWidth()
                                .padding(horizontal = SectionTextStartPadding, vertical = 4.dp),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        when {
            week.periodsLoading -> LoadingScreen(modifier = Modifier.padding(innerPadding))
            // 期数列表都没有时：失效只能去登录，普通失败才给重试
            week.periodsFailure != null -> {
                val failure = week.periodsFailure
                if (failure?.phase == ScreenLoadPhase.Expired) {
                    LoginExpiredScreen(
                        onOpenLogin = nav::login,
                        modifier = Modifier.padding(innerPadding),
                    )
                } else {
                    ErrorScreen(
                        message = failure?.message.orEmpty(),
                        onRetry = { week.loadPeriods() },
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
            week.periods.isEmpty() -> CenteredEmptyState(
                title = "暂无每周必看",
                hint = "稍后再来看看",
                modifier = Modifier.padding(innerPadding),
            )
            else -> Column(modifier = Modifier.fillMaxSize()) {
                GlassPullToRefresh(
                    isRefreshing = week.refreshing,
                    onRefresh = { week.refresh() },
                    pullToRefreshState = refreshState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = innerPadding,
                ) {
                    // 大屏行卡两列：列数由 WideLayout.feedColumns 判定，手机断点仍是单列
                    val feedColumns = LocalWideLayout.current.feedColumns
                    val listState = rememberLazyListState()
                    TrackedLazyColumn(
                        state = listState,
                        // 列表内容滚到玻璃顶栏下方，顶栏才能采样到真实背景
                        contentPadding = innerPadding,
                        modifier = Modifier.fillMaxSize().readableWidth(),
                    ) {
                        val loaded = week.detail
                        when {
                            week.detailLoading -> item(key = "loading") {
                                LoadingScreen(modifier = Modifier.fillMaxWidth().padding(24.dp))
                            }
                            week.detailFailure != null -> item(key = "error") {
                                val failure = week.detailFailure
                                if (failure?.phase == ScreenLoadPhase.Expired) {
                                    LoginExpiredScreen(
                                        onOpenLogin = nav::login,
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    )
                                } else {
                                    // 期数列表已成功，只有当期失败：保留下拉选择器重试
                                    ErrorScreen(
                                        message = failure?.message.orEmpty(),
                                        onRetry = { week.retryDetail() },
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    )
                                }
                            }
                            loaded == null || loaded.items.isEmpty() -> item(key = "empty") {
                                EmptyState(title = "本期暂无内容")
                            }
                            else -> searchResultItems(
                                // 期数详情只有一页：不伪造页码，交给 PagedFeed 的空页，页脚自然不显示
                                feed = PagedFeed(items = loaded.items),
                                onLoadMore = {},
                                onOpenAlbum = nav::album,
                                columns = feedColumns,
                            )
                        }
                    }
                }
            }
        }
    }
}
