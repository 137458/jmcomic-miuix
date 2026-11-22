// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jm3.app.miuix.ui.coui.icon.CouiIcons
import com.jm3.app.miuix.ui.coui.theme.CouiStrings
import com.jm3.core.paging.PagedFeed
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.Button
import io.github.suqi8.coui.kmp.basic.ButtonDefaults
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.InfiniteProgressIndicator
import io.github.suqi8.coui.kmp.basic.ProgressIndicatorDefaults
import io.github.suqi8.coui.kmp.basic.PullToRefresh
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.preference.PagerFooterPreference
import io.github.suqi8.coui.kmp.preference.PreferenceCategory
import io.github.suqi8.coui.kmp.theme.COUITheme

/** 下拉刷新容器：封装 suqi8/coui 原生 [PullToRefresh]，与顶栏 [ScrollBehavior] 联动。 */
@Composable
fun CouiPullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    scrollBehavior: ScrollBehavior? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    PullToRefresh(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        contentPadding = contentPadding,
        topAppBarScrollBehavior = scrollBehavior,
        content = content,
    )
}

/** 加载态：单行说明卡片；首屏与分页都能直接放。 */
@Composable
fun CouiLoadingGroup(
    modifier: Modifier = Modifier,
    text: String = "正在加载…",
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = CouiLayoutTokens.HorizontalMargin),
    ) {
        BasicComponent(
            startAction = {
                InfiniteProgressIndicator(size = ProgressIndicatorDefaults.DefaultInfiniteProgressIndicatorSize)
            },
            title = text,
        )
    }
}

/** 空态：单行说明卡片；文案由页面给出（例如「这里还没有作品」）。 */
@Composable
fun CouiEmptyGroup(
    text: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = CouiLayoutTokens.HorizontalMargin),
    ) {
        BasicComponent(title = text)
    }
}

/** 失败态：标题给结论、脚注给原因，「重试」行始终可见且可点。 */
@Composable
fun CouiErrorGroup(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = COUITheme.colorScheme
    Column(modifier = modifier.fillMaxWidth()) {
        PreferenceCategory(title = CouiStrings.LoadFailed)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CouiLayoutTokens.HorizontalMargin),
        ) {
            BasicComponent(
                title = CouiStrings.Retry,
                endActions = {
                    Icon(
                        imageVector = CouiIcons.Refresh,
                        // 行标题已念出「重试」，图标不再重复播报
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = colors.primary,
                    )
                },
                onClick = onRetry,
            )
        }
        if (message.isNotBlank()) {
            PagerFooterPreference(summary = message, withExtraMarginBottom = false)
        }
    }
}

/**
 * 分页脚：滚动到底时报告续页状态。
 *
 * error 非空时给可点的「重新加载」行；loading 与 endReached 只报进度与到底；
 * 还有下一页且尚未触发加载时，若传入 [onLoadMore] 则给出可点的「加载更多」行，否则仅留提示文案。
 */
@Composable
fun CouiLoadMoreFooter(
    loading: Boolean,
    error: String?,
    endReached: Boolean,
    modifier: Modifier = Modifier,
    onLoadMore: (() -> Unit)? = null,
    onRetry: () -> Unit,
) {
    val colors = COUITheme.colorScheme
    when {
        error != null -> Column(modifier = modifier.fillMaxWidth()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CouiLayoutTokens.HorizontalMargin),
            ) {
                BasicComponent(
                    title = CouiStrings.Reload,
                    endActions = {
                        Icon(
                            imageVector = CouiIcons.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = colors.primary,
                        )
                    },
                    onClick = onRetry,
                )
            }
            if (error.isNotBlank()) {
                PagerFooterPreference(summary = error, withExtraMarginBottom = false)
            }
        }

        loading -> Column(modifier = modifier.fillMaxWidth()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CouiLayoutTokens.HorizontalMargin),
            ) {
                BasicComponent(
                    startAction = {
                        InfiniteProgressIndicator(size = ProgressIndicatorDefaults.DefaultInfiniteProgressIndicatorSize)
                    },
                    title = "正在加载更多…",
                )
            }
            PagerFooterPreference(summary = "正在获取下一页…", withExtraMarginBottom = false)
        }

        endReached -> Column(modifier = modifier.fillMaxWidth()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CouiLayoutTokens.HorizontalMargin),
            ) {
                BasicComponent(title = "已经到底了")
            }
            PagerFooterPreference(summary = "没有更多了", withExtraMarginBottom = false)
        }

        onLoadMore != null -> Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = CouiLayoutTokens.HorizontalMargin),
        ) {
            BasicComponent(
                title = "加载更多",
                onClick = onLoadMore,
            )
        }

        else -> PagerFooterPreference(
            summary = "继续上滑自动加载下一页",
            modifier = modifier,
            withExtraMarginBottom = false,
        )
    }
}

/**
 * 分页脚：直接吃整份 [PagedFeed]，调用方只交状态机与失败文案。
 *
 * 四分支由状态机推出，页面不再各自把 loadingMore/loadMoreFailed/hasMore 翻成三个布尔。
 */
@Composable
fun CouiLoadMoreFooter(
    feed: PagedFeed<*>,
    modifier: Modifier = Modifier,
    errorMessage: String = CouiStrings.LoadMoreFailedRetry,
    onLoadMore: (() -> Unit)? = null,
    onRetry: () -> Unit,
) {
    CouiLoadMoreFooter(
        loading = feed.loadingMore,
        error = if (feed.loadMoreFailed) errorMessage else null,
        endReached = !feed.hasMore,
        modifier = modifier,
        onLoadMore = onLoadMore,
        onRetry = onRetry,
    )
}

/** 顶栏刷新胶囊：信息流各页共用同一形态，避免每页各写一套。 */
@Composable
fun CouiFeedRefreshAction(
    refreshing: Boolean,
    modifier: Modifier = Modifier,
    onRefresh: () -> Unit,
) {
    val colors = COUITheme.colorScheme
    val tint = if (refreshing) colors.labelSecondary else colors.primaryText
    Button(
        onClick = onRefresh,
        modifier = modifier,
        enabled = !refreshing,
        colors = ButtonDefaults.buttonColors(
            color = Color.Transparent,
            disabledColor = Color.Transparent,
            contentColor = colors.primaryText,
            disabledContentColor = colors.labelSecondary,
        ),
        minWidth = 0.dp,
        minHeight = 32.dp,
        insideMargin = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (refreshing) {
                InfiniteProgressIndicator(size = 16.dp, strokeWidth = 2.dp, color = tint)
            } else {
                Icon(
                    imageVector = CouiIcons.Refresh,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                text = if (refreshing) CouiStrings.Refreshing else CouiStrings.Refresh,
                style = COUITheme.textStyles.bodyXS,
                color = tint,
                maxLines = 1,
            )
        }
    }
}

/** 登录过期或未登录时的落点卡片：解释原因并提供明确的「去登录」入口。 */
@Composable
fun CouiFeedLoginGroup(
    modifier: Modifier = Modifier,
    message: String = CouiStrings.LoginExpired,
    title: String = CouiStrings.NeedLogin,
    onOpenLogin: () -> Unit,
) {
    val colors = COUITheme.colorScheme
    val displayText = message.ifBlank { CouiStrings.LoginExpired }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        PreferenceCategory(title = title)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CouiLayoutTokens.HorizontalMargin),
        ) {
            BasicComponent(
                title = CouiStrings.OpenLogin,
                endActions = {
                    Icon(
                        imageVector = CouiIcons.Forward,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = colors.primary,
                    )
                },
                onClick = onOpenLogin,
            )
        }
        PagerFooterPreference(summary = displayText, withExtraMarginBottom = false)
    }
}

/**
 * 屏级失败块：会话失效给登录入口，其余给重试。
 *
 * 会话失效时「重试」只会再吃一次 401，因此入口直接换成 [CouiFeedLoginGroup]，
 * 不再把登录伪装成重试按钮；两者共用同一套「标题 + 说明 + 一行动作」形态。
 */
@Composable
fun CouiScreenFailureGroup(
    message: String?,
    expired: Boolean,
    onRetry: () -> Unit,
    onOpenLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (expired) {
        CouiFeedLoginGroup(
            message = message.orEmpty().ifBlank { CouiStrings.LoginExpired },
            modifier = modifier,
            onOpenLogin = onOpenLogin,
        )
    } else {
        CouiErrorGroup(message = message.orEmpty(), onRetry = onRetry, modifier = modifier)
    }
}

/** 触底续页：滚动模式下列表末尾进入视口就拉下一页，失败时不自动重试。 */
@Composable
fun CouiAutoLoadMoreEffect(
    feed: PagedFeed<*>,
    onLoadMore: () -> Unit,
) {
    LaunchedEffect(feed.page, feed.loadingMore, feed.loadMoreFailed) {
        if (!feed.canLoadMore || feed.loadingMore || feed.loadMoreFailed) return@LaunchedEffect
        onLoadMore()
    }
}
