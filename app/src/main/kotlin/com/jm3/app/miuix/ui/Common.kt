package com.jm3.app.miuix.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.squircle.squircleClip
import com.jm3.core.model.SearchPage
import com.jm3.core.model.SearchResult
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme



/** 评论回复面板：显示被回复作者、200 字上限与发送状态；换目标时清空草稿。 */
@Composable
fun CommentReplySheet(
    show: Boolean,
    targetAuthor: String?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSend: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    // 每次重新展开时清掉上一次草稿，避免把回复 A 的内容发到 B
    LaunchedEffect(show, targetAuthor) {
        if (show) text = ""
    }
    OverlayBottomSheet(
        show = show,
        title = targetAuthor?.let { "回复 $it" } ?: "回复",
        onDismissRequest = onDismiss,
    ) {
        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
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

/** 行卡封面比例 3:4：宽度由撑满后的行高换算，卡片变高封面跟着变宽。 */
private const val SEARCH_ROW_COVER_ASPECT = 0.75f

/** 行卡封面左侧圆角：封面贴满卡片左缘，只有朝内的一侧需要自己裁，外侧与卡片同曲率。 */
private val SearchRowCoverShape = RoundedCornerShape(
    topStart = MIUIX_CARD_CORNER_DP.dp,
    bottomStart = MIUIX_CARD_CORNER_DP.dp,
)

/**
 * 搜索结果行封面：撑满卡片高度、贴齐卡片左缘。
 *
 * 行高由标题行数决定，封面按同一高度等比换算宽度，所以每行封面都顶满卡片而不是内缩成小缩略图；
 * 缺失封面时用同尺寸底色占位，保证行高稳定。
 */
@Composable
private fun SearchResultCover(
    albumId: String,
    url: String?,
    title: String? = null,
    modifier: Modifier = Modifier,
) {
    if (url == null) {
        Box(modifier = modifier.background(MiuixTheme.colorScheme.surfaceContainer))
        return
    }
    AlbumCoverImage(
        url = url,
        contentDescription = null,
        modifier = modifier
            .clip(SearchRowCoverShape)
            .albumHeroCover(albumId, url, title, MIUIX_CARD_CORNER_DP),
    )
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

/** 列表加载更多三态：加载中 / 出错重试 / 没有更多了；与 HyperOS 加载反馈一致。 */
@Composable
fun LoadMoreFooter(
    loadingMore: Boolean,
    hasMore: Boolean,
    loadMoreFailed: Boolean,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        when {
            loadingMore -> InfiniteProgressIndicator(modifier = Modifier.size(20.dp))
            loadMoreFailed -> TextButton(text = "加载失败，点击重试", onClick = onLoadMore, minWidth = 0.dp)
            !hasMore -> Text(
                text = "没有更多了",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            else -> TextButton(text = "加载更多", onClick = onLoadMore)
        }
    }
}

/**
 * 逐行结果卡：封面 + 标题 + 作者标签摘要，榜单模式在封面前补名次。
 *
 * 封面顶满卡片：卡片内边距为 0，封面贴齐左缘并撑满整行高度，行高只由右侧文字决定；
 * 因此需要 height(IntrinsicSize.Min) 先量出文字高度，封面才能按比例换算宽度。
 *
 * 标题与摘要都限行：超长日文标题会撑到四行，把整行拉得高矮不一，两侧留白全被吃掉。
 */
@Composable
private fun SearchResultCard(
    result: SearchResult,
    onClick: () -> Unit,
    rank: Int? = null,
) {
    val summary = listOf(result.authors.joinToString(" "), result.tags.joinToString(" "))
        .filter { it.isNotBlank() }
        .joinToString(" · ")
        .ifBlank { null }
    val heroHost = LocalAlbumHeroHost.current
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).pressScale(),
        insideMargin = PaddingValues(0.dp),
        onClick = {
            heroHost?.startForward(result.id, result.coverUrl, MIUIX_CARD_CORNER_DP, result.title)
            onClick()
        },
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            if (rank != null) {
                Box(modifier = Modifier.fillMaxHeight().width(30.dp), contentAlignment = Alignment.Center) {
                    RankIndexBadge(rank = rank)
                }
            }
            SearchResultCover(
                albumId = result.id,
                url = result.coverUrl,
                title = result.title,
                modifier = Modifier.fillMaxHeight().aspectRatio(SEARCH_ROW_COVER_ASPECT),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
            ) {
                Text(
                    text = result.title,
                    style = MiuixTheme.textStyles.headline1,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (summary != null) {
                    Text(
                        text = summary,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * 搜索/浏览共用的结果列表与加载更多入口；hasMore 由 currentPage/totalPages 推断。
 *
 * @param ranked 榜单模式：在封面前补名次序号（跨页累加，合并多页后依然连续）。
 */
fun LazyListScope.searchResultItems(
    page: SearchPage,
    loadingMore: Boolean,
    loadMoreFailed: Boolean,
    onLoadMore: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    ranked: Boolean = false,
) {
    if (page.items.isEmpty()) {
        item(key = "empty") {
            EmptyState(title = "无结果", hint = "换个关键词再试试")
        }
    }
    itemsIndexed(page.items, key = { _, result -> result.id }) { index, result ->
        SearchResultCard(
            result = result,
            rank = if (ranked) index + 1 else null,
            onClick = { onOpenAlbum(result.id) },
        )
    }
    // 单页结果（如每周必看）不展示加载更多区；多页列表即使到底也给出「没有更多了」反馈
    if (page.items.isNotEmpty() && page.totalPages > 1) {
        item(key = "load-more") {
            LoadMoreFooter(
                loadingMore = loadingMore,
                hasMore = page.currentPage < page.totalPages,
                loadMoreFailed = loadMoreFailed,
                onLoadMore = onLoadMore,
            )
        }
    }
}

/** 点击分页条：上一页 / 页码 / 下一页，供任意结果列表在“点击切换分页”模式下复用。 */
@Composable
fun ResultPagerBar(page: SearchPage, onPageChange: (Int) -> Unit) {
    PagerBar(page = page.currentPage, totalPages = page.totalPages, onPageChange = onPageChange)
}

/** 通用点击分页条；页码与总页数由调用方给出，避免各页自己拼一套翻页控件。 */
@Composable
fun PagerBar(page: Int, totalPages: Int, onPageChange: (Int) -> Unit) {
    val current = page.coerceAtLeast(1)
    val total = totalPages.coerceAtLeast(1)
    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.layout.Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(
                text = "上一页",
                onClick = { onPageChange(current - 1) },
                enabled = current > 1,
                minWidth = 0.dp,
                minHeight = 36.dp,
            )
            Text(
                text = "$current / $total",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            TextButton(
                text = "下一页",
                onClick = { onPageChange(current + 1) },
                enabled = current < total,
                minWidth = 0.dp,
                minHeight = 36.dp,
            )
        }
    }
}

/** 点击分页结果列表：单页整页展示，底部附翻页条；与无限滚动共用同一行样式。 */
fun LazyListScope.pagedSearchResultItems(
    page: SearchPage,
    onPageChange: (Int) -> Unit,
    onOpenAlbum: (String) -> Unit,
) {
    if (page.items.isEmpty()) {
        item(key = "empty") { EmptyState(title = "无结果", hint = "换个关键词再试试") }
    }
    items(page.items, key = { it.id }) { result ->
        SearchResultCard(result = result, onClick = { onOpenAlbum(result.id) })
    }
    item(key = "pager") { ResultPagerBar(page = page, onPageChange = onPageChange) }
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

/** 筛选胶囊圆角：与 Miuix 卡片同档曲率，避免玻璃层内出现两种曲率。 */
private val FilterChipCornerRadius = 16.dp

/**
 * 横滑胶囊筛选条：选中项主色实底白字，未选中项 surfaceContainer，按压回弹并带轻触感。
 *
 * 与 Miuix TabRow 的差异：分段控件把选项均分到一屏，条目多时逐个缩到只剩省略号；胶囊条
 * 按内容宽度排布并允许横滑，选项全集都能保持可读。
 */
@Composable
fun FilterChipRow(
    labels: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
) {
    val haptic = rememberSelectionHaptic()
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(labels.size) { index ->
            FilterChip(
                text = labels[index],
                selected = index == selectedIndex,
                onClick = {
                    if (index != selectedIndex) {
                        haptic()
                        onSelected(index)
                    }
                },
            )
        }
    }
}

/** 单个筛选胶囊：选中与未选中共用同一按压回弹，切换后手感不中断。 */
@Composable
private fun FilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.body2,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = if (selected) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSurface,
        maxLines = 1,
        modifier = Modifier
            .pressScale(hapticFeedback = false)
            .squircleClip(FilterChipCornerRadius)
            .background(
                if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.surfaceContainer,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

/** 榜单封面卡的封面比例：3:4 竖版略扁，封面原始比例跨度大时以裁切换齐整。 */
private const val RANK_COVER_ASPECT = 0.72f

/**
 * 榜单瀑布流结果：两列封面卡，名次叠在封面左上角。
 *
 * 单页结果不展示加载更多区；多页列表即使到底也给出「没有更多了」反馈。
 */
fun LazyStaggeredGridScope.rankResultGrid(
    page: SearchPage,
    loadingMore: Boolean,
    loadMoreFailed: Boolean,
    onLoadMore: () -> Unit,
    onOpenAlbum: (String) -> Unit,
) {
    if (page.items.isEmpty()) {
        item(key = "rank-empty", span = StaggeredGridItemSpan.FullLine) {
            EmptyState(title = "无结果", hint = "换个筛选条件再试试")
        }
    }
    items(
        count = page.items.size,
        key = { index -> page.items[index].id },
        contentType = { "rank_card" },
    ) { index ->
        val result = page.items[index]
        RankGridCard(
            result = result,
            rank = index + 1,
            onClick = { onOpenAlbum(result.id) },
        )
    }
    if (page.items.isNotEmpty() && page.totalPages > 1) {
        item(key = "rank-load-more", span = StaggeredGridItemSpan.FullLine) {
            LoadMoreFooter(
                loadingMore = loadingMore,
                hasMore = page.currentPage < page.totalPages,
                loadMoreFailed = loadMoreFailed,
                onLoadMore = onLoadMore,
            )
        }
    }
}

/** 榜单封面卡：封面 + 左上角名次角标 + 单行标题与作者，字号层级与 pixez 封面卡一致。 */
@Composable
private fun RankGridCard(result: SearchResult, rank: Int, onClick: () -> Unit) {
    val heroHost = LocalAlbumHeroHost.current
    Card(
        modifier = Modifier.fillMaxWidth().pressScale(),
        insideMargin = PaddingValues(0.dp),
        onClick = {
            heroHost?.startForward(result.id, result.coverUrl, MIUIX_CARD_CORNER_DP)
            onClick()
        },
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            if (result.coverUrl != null) {
                AlbumCoverImage(
                    url = result.coverUrl,
                    contentDescription = result.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(RANK_COVER_ASPECT)
                        .albumHeroCover(result.id, result.coverUrl, MIUIX_CARD_CORNER_DP),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(RANK_COVER_ASPECT)
                        .background(MiuixTheme.colorScheme.surfaceContainer),
                )
            }
            RankCoverBadge(
                rank = rank,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Text(
                text = result.title,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = result.authors.joinToString(" / ").ifBlank { "未知作者" },
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** 封面名次角标：前三名主色实底，其余半透明底，保证在任意封面上可读。 */
@Composable
private fun RankCoverBadge(rank: Int, modifier: Modifier = Modifier) {
    val highlighted = rank <= 3
    Text(
        text = rank.toString(),
        style = MiuixTheme.textStyles.footnote2,
        fontWeight = FontWeight.Bold,
        color = if (highlighted) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSurface,
        modifier = modifier
            .squircleClip(8.dp)
            .background(
                if (highlighted) {
                    MiuixTheme.colorScheme.primary
                } else {
                    MiuixTheme.colorScheme.surface.copy(alpha = 0.86f)
                },
            )
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}
