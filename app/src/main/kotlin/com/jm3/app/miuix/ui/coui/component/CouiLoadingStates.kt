// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.itos.coui.effect.SpotlightPreset
import com.itos.coui.layout.Footer
import com.itos.coui.layout.Group
import com.itos.coui.layout.Item
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme
import com.jm3.app.miuix.ui.coui.icon.CouiIcons
import com.jm3.app.miuix.ui.coui.theme.CouiStrings
import com.jm3.core.paging.PagedFeed
import top.yukonga.miuix.kmp.basic.Icon

/** 加载态：单行说明卡片；首屏与分页都能直接放，尺寸由库 Group/Item 的 CouiPageDefaults 决定。 */
@Composable
fun CouiLoadingGroup(
    modifier: Modifier = Modifier,
    text: String = "正在加载…",
) {
    Group(modifier = modifier) {
        Item(
            title = {
                CouiBodyText(text = text, textAlign = TextAlign.Start)
            },
        )
    }
}

/** 空态：单行说明卡片；文案由页面给出（例如「这里还没有作品」）。 */
@Composable
fun CouiEmptyGroup(
    text: String,
    modifier: Modifier = Modifier,
) {
    Group(modifier = modifier) {
        Item(
            title = {
                CouiBodyText(text = text, textAlign = TextAlign.Start)
            },
        )
    }
}

/** 失败态：标题给结论、脚注给原因，「重试」行始终可见且可点。 */
@Composable
fun CouiErrorGroup(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CouiTheme.colorSchemeFor(CouiTheme.isDark)
    Group(modifier = modifier, title = CouiStrings.LoadFailed, footer = message) {
        Item(
            title = {
                CouiBodyText(text = CouiStrings.Retry, textAlign = TextAlign.Start)
            },
            trailing = {
                Icon(
                    imageVector = CouiIcons.Refresh,
                    // 行标题已念出「重试」，图标不再重复播报
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = colors.accent,
                )
            },
            onClick = onRetry,
        )
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
    val colors = CouiTheme.colorSchemeFor(CouiTheme.isDark)
    when {
        error != null -> Group(modifier = modifier, footer = error) {
            Item(
                title = {
                    CouiBodyText(text = CouiStrings.Reload, textAlign = TextAlign.Start)
                },
                trailing = {
                    Icon(
                        imageVector = CouiIcons.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = colors.accent,
                    )
                },
                onClick = onRetry,
            )
        }

        loading -> Group(modifier = modifier, footer = "正在获取下一页…") {
            Item(
                title = {
                    CouiBodyText(text = "正在加载更多…", textAlign = TextAlign.Start)
                },
            )
        }

        endReached -> Group(modifier = modifier, footer = "没有更多了") {
            Item(
                title = {
                    CouiBodyText(text = "已经到底了", textAlign = TextAlign.Start)
                },
            )
        }

        onLoadMore != null -> Group(modifier = modifier) {
            Item(
                title = {
                    CouiBodyText(text = "加载更多", textAlign = TextAlign.Start)
                },
                onClick = onLoadMore,
            )
        }

        else -> Footer(modifier = modifier, text = "继续上滑自动加载下一页")
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
    val colors = CouiTheme.colorSchemeFor(CouiTheme.isDark)
    val tint = if (refreshing) colors.onSurfaceVariant else colors.accent
    Row(
        modifier = modifier
            .clip(CouiCapsuleShape)
            .couiPressable(
                shape = CouiCapsuleShape,
                spotlight = SpotlightPreset.ChipTab,
                enabled = !refreshing,
                minTouchTarget = CouiMinTouchTarget,
                onClick = onRefresh,
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(imageVector = CouiIcons.Refresh, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Text(text = if (refreshing) CouiStrings.Refreshing else CouiStrings.Refresh, kind = CouiTextKind.GroupFooter, color = tint)
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
    val colors = CouiTheme.colorSchemeFor(CouiTheme.isDark)
    val displayText = message.ifBlank { CouiStrings.LoginExpired }
    Group(
        modifier = modifier.padding(top = 8.dp),
        title = title,
        footer = displayText,
    ) {
        Item(
            title = {
                CouiBodyText(text = CouiStrings.OpenLogin, textAlign = TextAlign.Start)
            },
            trailing = {
                Icon(
                    imageVector = CouiIcons.Forward,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = colors.accent,
                )
            },
            onClick = onOpenLogin,
        )
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
