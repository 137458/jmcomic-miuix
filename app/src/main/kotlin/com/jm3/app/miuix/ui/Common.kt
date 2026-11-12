package com.jm3.app.miuix.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImagePainter
import com.jm3.core.paging.PagedFeed
import com.jm3.core.paging.shouldLoadMore
import com.jm3.core.model.SearchResult
import com.jm3.app.miuix.ui.skin.LocalSkin
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.layout.DialogDefaults
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 分组内容（卡片、横滑轨道、结果行）的水平外留白。
 *
 * Miuix 的 SmallTitle 内建 28dp 水平内边距，卡片内部组件自带 16dp；外缘取 12dp 时
 * 标题文字与卡内文字同处 28dp。卡内留白不是 16dp 的内容（标签流、评论行）不套用本值，
 * 否则卡内文字会比标题再偏左 4dp。
 */
internal val SectionHorizontalPadding: Dp = 12.dp

/**
 * 无外观膨胀的最小触区包装：内层紧凑按钮保持原尺寸，外层至少撑到 [minSize]×[minSize]，
 * 仅在子控件未消费的边缘留白补接点击（启用状态为真时触发），避免灰底胶囊膨胀或重复朗读。
 */
@Composable
internal fun MinTouchTarget(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    minSize: Dp = 48.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val latestOnClick by rememberUpdatedState(onClick)
    val latestEnabled by rememberUpdatedState(enabled)
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = minSize, minHeight = minSize)
            .pointerInput(Unit) {
                detectTapGestures {
                    if (latestEnabled) latestOnClick()
                }
            },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/**
 * 纯文字分区头的水平起点：与 [SmallTitle] 内建的 28dp 文字基线对齐。
 *
 * 卡片正文的起点是 [SectionHorizontalPadding] 12dp + 卡片内建 16dp = 28dp；
 * 没有卡片内衬的裸文字头必须直接取本值，不能再按 12dp 排。
 */
internal val SectionTextStartPadding: Dp = SectionHorizontalPadding + 16.dp

/**
 * 弹窗宽度上限：大屏按断点放宽（440/520dp），手机返回组件库默认 420dp。
 *
 * 组件库默认值固定 420dp，1280dp 窗口下弹窗只占屏宽三分之一，标题与操作按钮都被挤压。
 * 只给 [OverlayDialog] 用：抽屉的库默认是 640dp，已经宽于本值，再收紧反而是倒退。
 */
@Composable
internal fun overlayDialogMaxWidth(): Dp =
    LocalWideLayout.current.dialogWidth ?: DialogDefaults.MaxWidth

/**
 * 弹层内容高度上限：不超过窗口推导值，也不超过调用方原本的保守上限。
 *
 * 两个方向都必要：竖屏手机窗口高 800dp 时推导值 480dp 会放宽原有 320/420/440dp 的观感，
 * 而 800x400dp 矮横屏推导值只有 368dp，写死的高度会把列表压出窗口、末尾几行点不到。
 */
@Composable
internal fun overlayMaxHeight(preferred: Dp): Dp =
    minOf(preferred, LocalWideLayout.current.dialogMaxHeight ?: preferred)

/**
 * 大屏字号档：宽 840dp 以上把标题类文字升 2sp，行高同步 +3sp。
 *
 * 1280dp 窗口下网格卡片宽到 196dp，标题仍是手机的 16sp，标题与卡片不成比例；
 * 只抬标题层级、正文保持原样，避免整页变松。行高必须跟字号一起抬：
 * 调用方会传固定标题行高（[WorkCard] 的 pinnedLineHeight），只放大字号会把文字裁掉。
 */
@Composable
internal fun largeScreenTitleStyle(base: TextStyle): TextStyle {
    if (!LocalWideLayout.current.largeTypography) return base
    // 只在 Sp 单位上做加法：Em 或 Unspecified 保持原样，避免把未指定的行高算成 NaN
    return base.copy(
        fontSize = if (base.fontSize.isSp) (base.fontSize.value + 2f).sp else base.fontSize,
        lineHeight = if (base.lineHeight.isSp) (base.lineHeight.value + 3f).sp else base.lineHeight,
    )
}

/** 评论回复面板：显示被回复作者、200 字上限与发送状态；换目标时清空草稿。 */
@Composable
fun CommentReplySheet(
    show: Boolean,
    targetAuthor: String?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSend: (String) -> Unit,
) {
    val skin = LocalSkin.current
    var text by rememberSavedDialogDraft(isOpen = show, targetKey = targetAuthor)
    OverlayBottomSheet(
        show = show,
        title = targetAuthor?.let { "回复 $it" } ?: "回复",
        cornerRadius = skin.shapes.sheetCorner,
        onDismissRequest = onDismiss,
    ) {
        Card(cornerRadius = skin.shapes.cardCorner, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                TextField(
                    value = text,
                    onValueChange = { text = it.take(COMMENT_REPLY_MAX_LENGTH) },
                    label = "写下你的回复（最多 $COMMENT_REPLY_MAX_LENGTH 字）",
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "${text.length}/$COMMENT_REPLY_MAX_LENGTH",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Button(
                        onClick = { onSend(text.trim()) },
                        enabled = !busy && text.isNotBlank(),
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) {
                        Text(text = if (busy) "发送中…" else "发送")
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}

/** 评论字数上限与原版一致（发帖/评论/回复共用 200）。 */
const val COMMENT_REPLY_MAX_LENGTH = 200

/** 纵向图标单元：详情动作台与悬浮工具条共用；禁用降灰、激活主色、按压微缩放。 */
@Composable
fun ActionCell(
    icon: ImageVector? = null,
    glyph: String? = null,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    active: Boolean = false,
) {
    val color = when {
        !enabled -> MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.4f)
        active -> MiuixTheme.colorScheme.primary
        else -> MiuixTheme.colorScheme.onSurface
    }
    Column(
        modifier = modifier
            // 点击涟漪按胶囊裁剪：底部工具栏与详情操作栏都是圆形/胶囊外形，
            // 不裁的话 ripple 会按单元格矩形铺开，看起来就是一块方块。
            .clip(RoundedCornerShape(percent = 50))
            .clickable(enabled = enabled, onClick = onClick)
            .pressScale(hapticFeedback = true)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(22.dp),
            )
        } else if (glyph != null) {
            // 这里是图标字形单元而非正文：字号与 22dp 图标对齐，随 fontScale 放大属于预期，
            // 因此不用 textStyles 收口，避免行高被样式带偏
            Text(text = glyph, fontSize = 22.sp, color = color)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote2,
            color = color,
            maxLines = 1,
        )
    }
}
/** 标题栏返回键。 */
@Composable
fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(
            imageVector = MiuixIcons.Back,
            contentDescription = "返回",
            modifier = Modifier.size(24.dp),
        )
    }
}

/** 全屏加载态：进度指示 + 文案；调用方需传入 innerPadding 以免被标题栏覆盖。 */
@Composable
fun LoadingScreen(modifier: Modifier = Modifier, text: String = "加载中…") {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        InfiniteProgressIndicator()
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = text)
    }
}

/** 全屏错误态：错误信息 + 重试入口；调用方需传入 innerPadding。 */
@Composable
fun ErrorScreen(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(12.dp))
        TextButton(text = "重试", onClick = onRetry)
    }
}

/**
 * Miuix 弹窗的按钮排布：取消在左、主操作在右，两颗按钮等分一行。
 *
 * 弹窗内容已经由 `DialogDefaults.insideMargin` 给出 24dp 内边距，调用方不要再补水平 padding，
 * 否则左右会变成 48dp，与系统弹窗以及 Miuix 分页/诊断面板的观感不一致。
 * [destructive] 仅改文字色，背景仍与取消键同档，避免在浅色底上出现整块红色。
 */
@Composable
fun DialogActionRow(
    primaryText: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryText: String = "取消",
    onSecondary: () -> Unit,
    primaryEnabled: Boolean = true,
    destructive: Boolean = false,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            text = secondaryText,
            onClick = onSecondary,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            text = primaryText,
            onClick = onPrimary,
            enabled = primaryEnabled,
            colors = if (destructive) {
                // 只换文字色、底色仍与取消键同档：整块红在浅色底上过于刺眼，也确实会让两颗按钮不成对
                ButtonDefaults.textButtonColors(textColor = MiuixTheme.colorScheme.error)
            } else {
                ButtonDefaults.textButtonColors()
            },
            modifier = Modifier.weight(1f),
        )
    }
}

/** 列表空态：标题 + 可选引导文案。 */
@Composable
fun EmptyState(title: String, hint: String? = null, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = title)
        if (hint != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = hint,
                style = MiuixTheme.textStyles.footnote1,
                textAlign = TextAlign.Center,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

/** 全屏空态：在可用区域内垂直居中，供无列表的空页面使用。 */
@Composable
fun CenteredEmptyState(title: String, hint: String? = null, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EmptyState(title = title, hint = hint)
    }
}

/** 榜单名次：前三名用主色强调，其余用次级文字色；固定宽度保证纵向对齐成列。 */
@Composable
private fun RankIndexBadge(rank: Int) {
    val highlighted = rank <= 3
    Box(modifier = Modifier.width(26.dp), contentAlignment = Alignment.Center) {
        Text(
            text = rank.toString(),
            style = MiuixTheme.textStyles.title4,
            fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Medium,
            color = if (highlighted) {
                MiuixTheme.colorScheme.primary
            } else {
                MiuixTheme.colorScheme.onSurfaceVariantSummary
            },
        )
    }
}

/**
 * 列表加载更多三态：加载中 / 出错重试 / 没有更多了；与 HyperOS 加载反馈一致。
 *
 * 三个开关都在 [PagedFeed] 里，调用方只交整份状态，不再各自摆三个布尔。
 */
@Composable
fun LoadMoreFooter(
    feed: PagedFeed<*>,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        when {
            feed.loadingMore -> InfiniteProgressIndicator(modifier = Modifier.size(20.dp))
            feed.loadMoreFailed -> MinTouchTarget(onClick = onLoadMore) {
                TextButton(text = "加载失败，点击重试", onClick = onLoadMore, minWidth = 0.dp)
            }
            !feed.hasMore -> Text(
                text = "没有更多了",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            else -> TextButton(text = "加载更多", onClick = onLoadMore)
        }
    }
}

/**
 * 触底加载下一页：列表只交出滚动信息，判定（还有没有下一页、在不在拉、距末尾几项、上次是否失败）
 * 全在 [PagedFeed.shouldLoadMore] 里。
 *
 * 只在还能自动加载时订阅滚动；一旦拿到下一页或到底，[PagedFeed.canLoadMore] 变为假，订阅随之解除。
 * 失败后会停在原地等用户点页脚重试，不会借「滚动信息还在末尾」反复重发。
 */
@Composable
fun LoadMoreOnScroll(
    listState: LazyListState,
    feed: PagedFeed<*>,
    onLoadMore: () -> Unit,
) {
    val canLoadMore = feed.canLoadMore
    val latestFeed by rememberUpdatedState(feed)
    val latestOnLoadMore by rememberUpdatedState(onLoadMore)
    LaunchedEffect(listState, canLoadMore) {
        if (!canLoadMore) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo }.collect { info ->
            if (latestFeed.shouldLoadMore(info.visibleItemsInfo.lastOrNull()?.index ?: -1, info.totalItemsCount)) {
                latestOnLoadMore()
            }
        }
    }
}

/** 两列瀑布流的触底加载：取所有可见列里的最大下标，与列表共用同一判据。 */
@Composable
fun LoadMoreOnScroll(
    gridState: LazyStaggeredGridState,
    feed: PagedFeed<*>,
    onLoadMore: () -> Unit,
) {
    val canLoadMore = feed.canLoadMore
    val latestFeed by rememberUpdatedState(feed)
    val latestOnLoadMore by rememberUpdatedState(onLoadMore)
    LaunchedEffect(gridState, canLoadMore) {
        if (!canLoadMore) return@LaunchedEffect
        snapshotFlow { gridState.layoutInfo }.collect { info ->
            val lastIndex = info.visibleItemsInfo.maxOfOrNull { it.index } ?: -1
            if (latestFeed.shouldLoadMore(lastIndex, info.totalItemsCount)) latestOnLoadMore()
        }
    }
}

/**
 * 列表滚动条：贴右缘铺满可用高度，轨道按 [trackPadding] 让出顶栏与底部安全区。
 *
 * 只由 [TrackedLazyColumn] 在同一层 [Box] 内调用，轨道内边距与列表内边距共用同一份参数；
 * 调用方不再手工让两者对齐，也不必自己决定要不要滚动条。
 */
@Composable
private fun BoxScope.ListScrollBar(
    listState: LazyListState,
    trackPadding: PaddingValues,
) {
    VerticalScrollBar(
        adapter = rememberScrollBarAdapter(listState),
        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        trackPadding = trackPadding,
    )
}

/**
 * 带滚动条的列表容器：把 [Box]、[LazyColumn] 与 [ListScrollBar] 收进一处。
 *
 * 滚动条落点与列表首尾留白都取自同一个 [contentPadding]，新增列表不会再漏挂滚动条，
 * 也不会因为两处 padding 各写一遍而让轨道错位。所有调用方都是纵向列表，滚动条一律常开；
 * 横向翻页走 HorizontalPager，不经过这里。
 */
@Composable
fun TrackedLazyColumn(
    state: LazyListState,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    Box(modifier = modifier) {
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            content = content,
        )
        ListScrollBar(listState = state, trackPadding = contentPadding)
    }
}

/**
 * 逐行结果卡：封面 + 标题 + 作者标签摘要，榜单模式在封面前补名次。
 *
 * 外观走 [WorkRowCard] 的统一契约；摘要只保留一行，超长日文标题限两行，
 * 否则整行会被撑到四行、高矮不一。
 */
@Composable
private fun SearchResultCard(
    result: SearchResult,
    onClick: () -> Unit,
    rank: Int? = null,
) {
    val authors = result.authors.joinToString(" / ").ifBlank { null }
    WorkRowCard(
        coverUrl = result.coverUrl,
        title = result.title,
        onClick = onClick,
        albumId = result.id,
        subtitle = authors,
        category = result.category,
        modifier = Modifier.fillMaxWidth().padding(horizontal = SectionHorizontalPadding, vertical = 6.dp),
        leading = if (rank == null) {
            null
        } else {
            {
                Box(modifier = Modifier.fillMaxHeight().width(30.dp), contentAlignment = Alignment.Center) {
                    RankIndexBadge(rank = rank)
                }
            }
        },
    ) {
        WorkCardMetaRow(tags = result.tags, albumCode = result.id)
    }
}

/**
 * 搜索/浏览共用的结果列表与加载更多入口；是否还有下一页、在不在拉、上次是否失败都由 [PagedFeed] 回答。
 *
 * @param ranked 榜单模式：在封面前补名次序号（跨页累加，合并多页后依然连续）。
 * @param columns 大屏列数：由 [WideLayout.feedColumns] 给出，1 表示与手机一致的单列行卡。
 */
fun LazyListScope.searchResultItems(
    feed: PagedFeed<SearchResult>,
    onLoadMore: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    ranked: Boolean = false,
    clickPaging: Boolean = false,
    onPageChange: ((Int) -> Unit)? = null,
    columns: Int = 1,
) {
    if (feed.items.isEmpty()) {
        item(key = "empty") {
            EmptyState(title = "无结果", hint = "换个关键词再试试")
        }
    }
    // key 仍是作品 id（与单列时逐字一致），名次按切片前的顺序右移一位
    feedRowItems(
        count = feed.items.size,
        columns = columns,
        keyPrefix = "",
        keyOf = { index -> feed.items[index].id },
    ) { index ->
        val result = feed.items[index]
        SearchResultCard(
            result = result,
            rank = if (ranked) index + 1 else null,
            onClick = { onOpenAlbum(result.id) },
        )
    }
    // 单页结果（如每周必看）不展示加载更多区；多页列表即使到底也给出「没有更多了」反馈
    if (feed.items.isNotEmpty() && (feed.pageCount ?: 1) > 1) {
        if (clickPaging && onPageChange != null) {
            item(key = "pager") {
                PagerBar(
                    page = feed.page,
                    totalPages = feed.pageCount,
                    hasMore = feed.hasMore,
                    onPageChange = onPageChange,
                )
            }
        } else {
            item(key = "load-more") {
                LoadMoreFooter(feed = feed, onLoadMore = onLoadMore)
            }
        }
    }
}

/**
 * 通用点击分页条；页码与总页数由调用方给出，避免各页自己拼一套翻页控件。
 *
 * [totalPages] 为 null 表示接口不返回总数：这时只显示当前页，下一页能不能点按 [hasMore] 判。
 * [enabled] 为 false 时两键都禁用，供翻页请求在途期间使用（屏幕仍保留当前列表）。
 */
@Composable
fun PagerBar(
    page: Int,
    totalPages: Int? = null,
    hasMore: Boolean = false,
    enabled: Boolean = true,
    onPageChange: (Int) -> Unit,
) {
    val current = page.coerceAtLeast(1)
    val total = totalPages?.coerceAtLeast(1)
    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.layout.Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val prevEnabled = enabled && current > 1
            val nextEnabled = enabled && (total?.let { current < it } ?: hasMore)
            MinTouchTarget(
                onClick = { onPageChange(current - 1) },
                enabled = prevEnabled,
            ) {
                TextButton(
                    text = "上一页",
                    onClick = { onPageChange(current - 1) },
                    enabled = prevEnabled,
                    minWidth = 0.dp,
                    minHeight = 36.dp,
                )
            }
            Text(
                text = total?.let { "$current / $it" } ?: "第 $current 页",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            MinTouchTarget(
                onClick = { onPageChange(current + 1) },
                enabled = nextEnabled,
            ) {
                TextButton(
                    text = "下一页",
                    onClick = { onPageChange(current + 1) },
                    enabled = nextEnabled,
                    minWidth = 0.dp,
                    minHeight = 36.dp,
                )
            }
        }
    }
}

/** 点击分页结果列表：单页整页展示，底部附翻页条；与无限滚动共用同一行样式。 *//** 屏级/列表区失败块：会话失效切换为去登录，普通失败给重试。 */
@Composable
internal fun ScreenFailureOrLogin(
    message: String,
    expired: Boolean,
    onRetry: () -> Unit,
    onOpenLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (expired) {
        LoginExpiredScreen(onOpenLogin = onOpenLogin, modifier = modifier)
    } else {
        ErrorScreen(message = message, onRetry = onRetry, modifier = modifier)
    }
}

fun LazyListScope.pagedSearchResultItems(
    feed: PagedFeed<SearchResult>,
    onPageChange: (Int) -> Unit,
    onOpenAlbum: (String) -> Unit,
    columns: Int = 1,
) {
    if (feed.items.isEmpty()) {
        item(key = "empty") { EmptyState(title = "无结果", hint = "换个关键词再试试") }
    }
    feedRowItems(
        count = feed.items.size,
        columns = columns,
        keyPrefix = "",
        keyOf = { index -> feed.items[index].id },
    ) { index ->
        val result = feed.items[index]
        SearchResultCard(result = result, onClick = { onOpenAlbum(result.id) })
    }
    item(key = "pager") {
        PagerBar(page = feed.page, totalPages = feed.pageCount ?: 1, onPageChange = onPageChange)
    }
}

/**
 * 一屏结果列表：按分页模式分发到「无限滚动」与「点击翻页」两条渲染路径。
 *
 * 各屏只交出当前这一页与两个动作，不必各自写一遍 clickPaging 的 when。
 */
fun LazyListScope.resultItems(
    feed: PagedFeed<SearchResult>,
    clickPaging: Boolean,
    onLoadMore: () -> Unit,
    onPageChange: (Int) -> Unit,
    onOpenAlbum: (String) -> Unit,
    columns: Int = 1,
) {
    if (clickPaging) {
        pagedSearchResultItems(
            feed = feed,
            onPageChange = onPageChange,
            onOpenAlbum = onOpenAlbum,
            columns = columns,
        )
    } else {
        searchResultItems(
            feed = feed,
            onLoadMore = onLoadMore,
            onOpenAlbum = onOpenAlbum,
            columns = columns,
        )
    }
}

/**
 * 按压微缩放：叠加弹簧回弹（阻尼 0.55 / 刚度 900）与可选轻触觉反馈。
 * 用 pointerInput 旁路监听而不消费事件，因此不会吞掉 Card/列表项自身的点击。
 */
@Composable
fun Modifier.pressScale(scaleDown: Float = 0.96f, hapticFeedback: Boolean = true): Modifier {
    var pressed by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        targetValue = if (pressed) scaleDown else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 900f),
        label = "pressScale",
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(hapticFeedback) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                pressed = true
                if (hapticFeedback) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                waitForUpOrCancellation()
                pressed = false
            }
        }
}

/** 切换类控件（Tab/筛选项）的轻触觉反馈；返回可直接调用的回调。 */
@Composable
fun rememberSelectionHaptic(): () -> Unit {
    val haptics = LocalHapticFeedback.current
    return remember(haptics) { { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) } }
}

/** 微光掠过一轮的时长：慢一档像呼吸，快一档像卡顿。 */
private const val SHIMMER_PERIOD_MS = 1200

/**
 * 微光骨架屏动效：封面图片未完成加载时展现平滑对角线微光掠过，消除纯灰块突兀感。
 *
 * 微光色取主题里高一层表面色的半透明，周期由 SHIMMER_PERIOD_MS 统一：调用点只回答
 * 「要不要闪」，不再各自挑颜色与节奏。
 */
@Composable
fun Modifier.shimmer(visible: Boolean = true): Modifier {
    if (!visible) return this
    val shimmerColor = MiuixTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = SHIMMER_PERIOD_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerProgress",
    )
    return this.drawBehind {
        val width = size.width
        val height = size.height
        val totalDistance = width + height
        val currentOffset = totalDistance * progress
        val brush = Brush.linearGradient(
            colors = listOf(
                Color.Transparent,
                shimmerColor,
                Color.Transparent,
            ),
            start = Offset(currentOffset - height, 0f),
            end = Offset(currentOffset, height),
        )
        drawRect(brush = brush)
    }
}

/** 边缘羽化带的默认宽度：与胶囊条右缘渐隐同一档，滚动区边缘不再出现硬切线。 */
internal val EdgeFadeWidth = 24.dp

/**
 * 边缘羽化底座：离屏合成后用 DstIn 遮罩把内容在指定边缘淡出。
 *
 * 胶囊条右缘渐隐与更新弹窗的说明滚动区共用这一份实现，避免两处各写一遍 Offscreen + 遮罩。
 */
internal fun Modifier.edgeFadeMask(brushFactory: DrawScope.() -> Brush): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
        drawContent()
        if (size.width <= 0f || size.height <= 0f) return@drawWithContent
        drawRect(brush = brushFactory(), blendMode = BlendMode.DstIn)
    }

/**
 * 竖向边缘羽化：滚动区上下两侧的内容淡出，替代硬裁切。
 *
 * 只在 [top] / [bottom] 为 true 的一侧铺渐变：滚到顶或底时对应一侧完全实心，
 * 不会出现"永远有雾"的假暗示；两侧都为 false 时不增加离屏图层。
 */
internal fun Modifier.verticalEdgeFade(
    top: Boolean,
    bottom: Boolean,
    fade: Dp = EdgeFadeWidth,
): Modifier {
    if (!top && !bottom) return this
    return edgeFadeMask {
        val fadePx = fade.toPx().coerceAtMost(size.height / 2f)
        val stop = (fadePx / size.height).coerceIn(0f, 0.5f)
        when {
            top && bottom -> Brush.verticalGradient(
                0f to Color.Transparent,
                stop to Color.Black,
                1f - stop to Color.Black,
                1f to Color.Transparent,
            )
            top -> Brush.verticalGradient(0f to Color.Transparent, stop to Color.Black)
            else -> Brush.verticalGradient(1f - stop to Color.Black, 1f to Color.Transparent)
        }
    }
}

/**
 * 封面骨架屏状态：图片首帧落地（成功或失败）后置位，微光随之停下。
 *
 * 只认成功会让加载失败的行一直闪——弱网下失败很常见，持续闪烁比原来的纯灰底更刺眼。
 */
@Stable
internal class CoverSkeletonState(settled: Boolean) {
    var settled by mutableStateOf(settled)
        private set

    /** 记录 Coil 的加载结果：成功与失败都算落地，只忽略仍在途中的状态。 */
    fun onImageState(state: AsyncImagePainter.State) {
        if (state is AsyncImagePainter.State.Success || state is AsyncImagePainter.State.Error) {
            settled = true
        }
    }
}

/** 按封面 URL 记住骨架屏状态：换 URL 重新开闪，同一 URL 复用既有状态。 */
@Composable
internal fun rememberCoverSkeletonState(url: String?): CoverSkeletonState =
    remember(url) { CoverSkeletonState(settled = url.isNullOrBlank()) }

/** 封面骨架屏底衬：灰底 + 微光，配合 [CoverSkeletonState.onImageState] 在图片落地后自动停闪。 */
@Composable
internal fun Modifier.coverSkeleton(state: CoverSkeletonState): Modifier =
    this.background(MiuixTheme.colorScheme.surfaceContainerHigh)
        .shimmer(visible = !state.settled)

/**
 * 榜单瀑布流结果：两列封面卡，名次叠在封面左上角。
 *
 * 单页结果不展示加载更多区；多页列表即使到底也给出「没有更多了」反馈。
 */
fun LazyStaggeredGridScope.rankResultGrid(
    feed: PagedFeed<SearchResult>,
    onLoadMore: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    clickPaging: Boolean = false,
    onPageChange: ((Int) -> Unit)? = null,
) {
    if (feed.items.isEmpty()) {
        item(key = "rank-empty", span = StaggeredGridItemSpan.FullLine) {
            EmptyState(title = "无结果", hint = "换个筛选条件再试试")
        }
    }
    items(
        count = feed.items.size,
        key = { index -> feed.items[index].id },
        contentType = { "rank_card" },
    ) { index ->
        val result = feed.items[index]
        RankGridCard(
            result = result,
            rank = index + 1,
            onClick = { onOpenAlbum(result.id) },
        )
    }
    // 单页结果（筛选后只有一页）不展示加载更多区；多页列表即使到底也给出「没有更多了」反馈
    if (feed.items.isNotEmpty() && (feed.pageCount ?: 1) > 1) {
        if (clickPaging && onPageChange != null) {
            item(key = "rank-pager", span = StaggeredGridItemSpan.FullLine) {
                PagerBar(
                    page = feed.page,
                    totalPages = feed.pageCount,
                    hasMore = feed.hasMore,
                    onPageChange = onPageChange,
                )
            }
        } else {
            item(key = "rank-load-more", span = StaggeredGridItemSpan.FullLine) {
                LoadMoreFooter(feed = feed, onLoadMore = onLoadMore)
            }
        }
    }
}

/** 榜单封面卡：封面 + 左上角名次角标 + 单行标题与作者，外观走 [WorkGridCard] 的统一契约。 */
@Composable
private fun RankGridCard(result: SearchResult, rank: Int, onClick: () -> Unit) {
    WorkGridCard(
        title = result.title,
        coverUrl = result.coverUrl,
        onClick = onClick,
        albumId = result.id,
        coverContentDescription = result.title,
        subtitle = result.authors.joinToString(" / ").ifBlank { "未知作者" },
        category = result.category,
        titleMaxLines = 1,
        modifier = Modifier.fillMaxWidth(),
        badge = {
            WorkCoverBadge(
                text = rank.toString(),
                highlighted = rank <= 3,
                modifier = Modifier.align(Alignment.TopStart).padding(WorkCardTokens.BadgeInset),
            )
            WorkAlbumCodeBadge(result.id)
        },
        footer = { WorkTagLine(result.tags) },
    )
}
