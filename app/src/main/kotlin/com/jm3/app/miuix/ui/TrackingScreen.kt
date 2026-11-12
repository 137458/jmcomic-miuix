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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.failureCopy
import com.jm3.app.miuix.ui.glass.GlassPullToRefresh
import com.jm3.app.miuix.ui.glass.GlassScaffold
import com.jm3.app.miuix.ui.glass.GlassTopAppBar
import com.jm3.app.miuix.ui.list.ListLoadState
import com.jm3.app.miuix.ui.route.AppRoutes
import com.jm3.app.miuix.ui.list.SessionListLoader
import com.jm3.core.model.TrackingItem
import com.jm3.core.model.TrackingPage
import com.jm3.core.paging.PageSlice
import com.jm3.core.paging.hasMoreByLoaded
import com.jm3.core.paging.loadedThrough
import com.jm3.core.paging.pagerTargetPage
import com.jm3.core.session.SessionOutcome
import com.jm3.core.settings.PaginationMode
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.SnackbarResult
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import java.time.format.DateTimeFormatter

/**
 * 连载追踪：已关注作品的更新列表，支持一键取消关注。
 *
 * 加载编排（请求代次、在途标记、翻页游标、失败落点）都在 [SessionListLoader]；本屏只给
 * 「取哪一页」与「怎么提示」两个回调。另有三处 loader 表达不了、只能留在页内：
 *
 * 1. 每页条数提示：接口只给总数，翻页要用首屏条数估「已加载到第几条」；
 * 2. 取消追踪：成功后按条件抹掉一行，撤销时 loader 没有回插入口，只能回源刷新；
 * 3. 翻页成功后回到列表顶部：loader 不暴露成功回调，用目标页与当前页对齐触发一次。
 */
@Composable
internal fun TrackingScreen(
    deps: ScreenDeps,
    onBack: () -> Unit,
    nav: AppRoutes,
    snackbarHostState: SnackbarHostState,
) {
    val mode = deps.paging.paginationMode
    val clickPaging = mode == PaginationMode.CLICK
    val user by deps.account.user.collectAsState()
    val refreshState = rememberPullToRefreshState(refreshThreshold = 0.18f)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var removingId by remember { mutableStateOf<String?>(null) }
    // 首屏条数：接口按页返回且无总页数，翻页时用它配合 loadedThrough 估算已加载条数
    var pageSizeHint by remember { mutableIntStateOf(0) }
    // 待回顶的目标页：翻页请求回来、页码真的变了才滚到顶部
    var pendingScrollPage by remember { mutableStateOf<Int?>(null) }

    // 账号或分页模式一变就换一个 loader：新实例从空列表起，旧账号的条目不会残留在屏上
    val loader = remember(mode, user?.uid, snackbarHostState) {
        SessionListLoader(
            scope = scope,
            idOf = { item: TrackingItem -> item.id },
            failureMessage = "追踪列表加载失败",
            fetchPage = { page ->
                // 启动闸门、IO 切线程与失效通知都在 AuthCall 里，这里只把一页转成 PageSlice
                val outcome = deps.authCall.call { deps.gateway.trackingList(page) }
                if (page == 1 && outcome is SessionOutcome.Ok) pageSizeHint = outcome.value.items.size
                outcome.toTrackingSlice(page, pageSizeHint)
            },
            notice = { message -> scope.launch { snackbarHostState.showSnackbar(message) } },
        )
    }
    val snapshot by loader.snapshot.collectAsState()
    val feed = snapshot.feed

    fun unfollow(item: TrackingItem) {
        if (removingId != null) return
        removingId = item.id
        scope.launch {
            val outcome = deps.authCall.call { deps.gateway.setTracking(item.id) }
            removingId = null
            when (outcome) {
                is SessionOutcome.Ok -> {
                    loader.removeWhere { it.id == item.id }
                    val result = snackbarHostState.showSnackbar(
                        message = "已取消追踪",
                        actionLabel = "撤销",
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        // 撤销是把条目放回原位：loader 只有整页替换与按条件抹除，没有回插，
                        // 所以这里回源刷新一次，让服务端顺序决定它落在哪
                        when (val undoOutcome = deps.authCall.call { deps.gateway.setTracking(item.id) }) {
                            is SessionOutcome.Ok -> loader.refresh()
                            SessionOutcome.Expired -> snackbarHostState.showSnackbar(SessionOutcome.Expired.message)
                            is SessionOutcome.Failed -> snackbarHostState.showSnackbar(
                                "撤销失败：" + failureCopy(undoOutcome, "请稍后重试"),
                            )
                        }
                    }
                }

                // 账号态操作失效：列表留在原处，只提示一次，重试同一个请求只会再吃一次 401
                SessionOutcome.Expired -> snackbarHostState.showSnackbar(SessionOutcome.Expired.message)

                is SessionOutcome.Failed -> snackbarHostState.showSnackbar(
                    "操作失败：" + failureCopy(outcome, "请稍后重试"),
                )
            }
        }
    }

    LaunchedEffect(loader) {
        loader.loadFirstPage()
    }

    LaunchedEffect(feed.page) {
        val target = pendingScrollPage
        if (target != null && feed.page == target) {
            pendingScrollPage = null
            listState.scrollToItem(0)
        }
    }

    // 触底加载只在无限滚动模式生效；点击翻页由列表底部翻页条驱动
    if (!clickPaging && snapshot.state == ListLoadState.Loaded) {
        LoadMoreOnScroll(listState = listState, feed = feed, onLoadMore = loader::loadMore)
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
                    onRetry = loader::loadFirstPage,
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
                    onRefresh = loader::refresh,
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
                                        onClick = { unfollow(item) },
                                        enabled = removingId != item.id,
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
                                        val resolved = pagerTargetPage(feed.page, target, feed.pageCount)
                                        if (resolved != feed.page) {
                                            pendingScrollPage = resolved
                                            loader.goToPage(resolved)
                                        }
                                    },
                                )
                            } else {
                                LoadMoreFooter(feed = feed, onLoadMore = loader::loadMore)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 追踪列表一页转 [PageSlice]：接口只给总数、不给总页数，按累计条数对比总数收敛末页。
 *
 * [sizeHint] 是首屏条数，把「已加载条数」估算到当前页；空页由 [hasMoreByLoaded] 直接判到底。
 * 与旧页内实现的唯一差别：跨页出现重复条目时，旧实现按去重后的条数算已加载，这里按页内条数算。
 */
private fun SessionOutcome<TrackingPage>.toTrackingSlice(
    page: Int,
    sizeHint: Int,
): SessionOutcome<PageSlice<TrackingItem>> = when (this) {
    is SessionOutcome.Ok -> {
        val loaded = loadedThrough(page, sizeHint, value.items.size)
        SessionOutcome.Ok(
            PageSlice(
                items = value.items,
                hasMore = hasMoreByLoaded(value.items.size, loaded, value.total),
                total = value.total,
                page = page,
            ),
        )
    }

    SessionOutcome.Expired -> SessionOutcome.Expired

    is SessionOutcome.Failed -> this
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
