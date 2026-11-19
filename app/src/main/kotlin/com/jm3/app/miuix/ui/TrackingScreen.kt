package com.jm3.app.miuix.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.ui.components.BackButton
import com.jm3.app.miuix.ui.components.CenteredEmptyState
import com.jm3.app.miuix.ui.components.ErrorScreen
import com.jm3.app.miuix.ui.components.LoadMoreFooter
import com.jm3.app.miuix.ui.components.LoadMoreOnScroll
import com.jm3.app.miuix.ui.components.LoadingScreen
import com.jm3.app.miuix.ui.components.PagerBar
import com.jm3.app.miuix.ui.components.TrackedLazyColumn
import com.jm3.app.miuix.ui.glass.GlassPullToRefresh
import com.jm3.app.miuix.ui.glass.GlassScaffold
import com.jm3.app.miuix.ui.glass.GlassTopAppBar
import com.jm3.app.miuix.ui.list.ListLoadState
import com.jm3.app.miuix.ui.route.AppRoutes
import com.jm3.app.miuix.ui.tracking.TrackingUndoReload
import com.jm3.app.miuix.ui.tracking.rememberTrackingScaffoldState
import com.jm3.core.session.SessionOutcome
import com.jm3.core.settings.PaginationMode
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import java.time.format.DateTimeFormatter

/**
 * 连载追踪：已关注作品的更新列表，支持一键取消关注。
 *
 * 加载编排、取消追踪与撤销都在 TrackingScaffoldState；本屏只留 HyperOS 的视觉与两处皮肤差异：
 * 每页条数提示只认首屏（HyperOS 侧不随后续页变大），撤销成功后回源首屏（服务端顺序决定条目落点）。
 * 翻页成功后回顶是滚动容器的事，也只在页内：loader 不暴露成功回调，用目标页与当前页对齐触发一次。
 */
@Composable
internal fun TrackingScreen(
    deps: ScreenDeps,
    onBack: () -> Unit,
    nav: AppRoutes,
    snackbarHostState: SnackbarHostState,
) {
    val mode = deps.paginationMode
    val clickPaging = mode == PaginationMode.CLICK
    val refreshState = rememberPullToRefreshState(refreshThreshold = 0.18f)
    val listState = rememberLazyListState()
    val state = rememberTrackingScaffoldState(
        deps = deps,
        snackbarHostState = snackbarHostState,
        hintGrowsWithLaterPages = false,
        undoReload = TrackingUndoReload.FirstPage,
    )
    val snapshot by state.snapshot.collectAsState()
    val feed = snapshot.feed
    // 待回顶的目标页：翻页请求回来、页码真的变了才滚到顶部
    var pendingScrollPage by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(feed.page) {
        val target = pendingScrollPage
        if (target != null && feed.page == target) {
            pendingScrollPage = null
            listState.scrollToItem(0)
        }
    }

    // 触底加载只在无限滚动模式生效；点击翻页由列表底部翻页条驱动
    if (!clickPaging && snapshot.state == ListLoadState.Loaded) {
        LoadMoreOnScroll(listState = listState, feed = feed, onLoadMore = state::loadMore)
    }

    GlassScaffold(
        topBar = {
            GlassTopAppBar(
                title = "连载追踪",
                subtitle = if (feed.items.isEmpty()) "" else "共 ${feed.total ?: feed.items.size} 部",
                // 透明底色：玻璃底色与模糊由 GlassScaffold 统一绘制
                color = Color.Transparent,
                navigationIcon = { BackButton(onBack = onBack) },
            )
        },
        snackbarHost = { GlassSnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when (val s = snapshot.state) {
            ListLoadState.Loading -> LoadingScreen(modifier = Modifier.padding(innerPadding))
            is ListLoadState.Failed -> if (s.sessionExpired) {
                LoginExpiredScreen(
                    onOpenLogin = nav::login,
                    modifier = Modifier.padding(innerPadding),
                )
            } else {
                ErrorScreen(
                    message = s.message,
                    onRetry = state::loadFirstPage,
                    modifier = Modifier.padding(innerPadding),
                )
            }
            ListLoadState.Loaded -> if (feed.items.isEmpty()) {
                CenteredEmptyState(
                    title = "暂无追踪",
                    hint = "在详情页开启追更后会出现在这里",
                    modifier = Modifier.padding(innerPadding),
                )
            } else {
                GlassPullToRefresh(
                    isRefreshing = snapshot.refreshing,
                    onRefresh = state::refresh,
                    pullToRefreshState = refreshState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = innerPadding,
                ) {
                    // 格式化器整页一份：放进行项内会让滚动进出反复构造 DateTimeFormatter
                    val dateFormat = rememberDateFormatter()
                    // 大屏行卡两列：列数由 WideLayout.feedColumns 判定，手机断点仍是单列
                    val feedColumns = LocalWideLayout.current.feedColumns
                    TrackedLazyColumn(
                        state = listState,
                        // 列表内容滚到玻璃顶栏下方，顶栏才能采样到真实背景
                        contentPadding = innerPadding,
                        modifier = Modifier.fillMaxSize().readableWidth(),
                    ) {
                        // key 仍是作品 id：与单列时逐字一致，升级后滚动位置不丢
                        feedRowItems(
                            count = feed.items.size,
                            columns = feedColumns,
                            keyPrefix = "",
                            keyOf = { index -> feed.items[index].id },
                        ) { index ->
                            val item = feed.items[index]
                            WorkRowCard(
                                coverUrl = item.coverUrl,
                                title = item.name,
                                onClick = { nav.album(item.id) },
                                albumId = item.id,
                                subtitle = item.updateAt?.toReadableTime(dateFormat),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                                textEndPadding = 4.dp,
                                trailing = {
                                    IconButton(
                                        onClick = { state.unfollow(item) },
                                        enabled = state.removingId != item.id,
                                    ) {
                                        Icon(MiuixIcons.Delete, contentDescription = "取消追踪")
                                    }
                                },
                            ) {
                                WorkCardMetaRow(tags = emptyList(), albumCode = item.id)
                            }
                        }
                        item(key = "pagination-footer") {
                            if (clickPaging) {
                                PagerBar(
                                    page = feed.page,
                                    hasMore = feed.hasMore,
                                    onPageChange = { target ->
                                        val resolved = state.pageToLoad(target)
                                        if (resolved != null) {
                                            pendingScrollPage = resolved
                                            state.goToPage(resolved)
                                        }
                                    },
                                )
                            } else {
                                LoadMoreFooter(feed = feed, onLoadMore = state::loadMore)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Unix 秒字符串转本地时间；非数字原样返回，避免吞掉服务端文案。 */
private fun String.toReadableTime(dateFormat: DateTimeFormatter): String =
    toLongOrNull()?.let { formatEpochSeconds(it, dateFormat).ifBlank { this } } ?: this

/**
 * 会话失效全屏态：文案位置与排版同 [ErrorScreen]，但动作是「去登录」而不是「重试」——
 * 会话在服务端已经作废，重试同一个请求只会再吃一次 401。
 *
 * 跨屏幕复用（连载追踪、收藏页），所以做成同包的 internal 顶层函数而不是各页私有实现。
 * 暂不并回 Common.kt：[ErrorScreen] 的按钮语义固定为「重试」，没有承载自定义动作的位置；
 * 等 Common.kt 提供带动作的全屏态时，两者应合并。
 */
@Composable
internal fun LoginExpiredScreen(onOpenLogin: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            // 失效文案只有一份，存在结局类型上：屏幕不再各自 import 文案常量
            text = SessionOutcome.Expired.message,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(12.dp))
        TextButton(text = "去登录", onClick = onOpenLogin)
    }
}
