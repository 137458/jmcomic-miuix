package com.jm3.app.miuix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 作品卡的统一尺寸令牌：所有作品卡（网格卡、行卡、缩略图）只从这里取尺寸，
 * 圆角一律走 [HERO_CARD_CORNER_DP] 等 AlbumHero 常量，调用点禁止再写 dp 字面量。
 *
 * 封面比例固定 3:4：[HERO_COVER_WIDTH_DP] / [HERO_COVER_HEIGHT_DP] 就是按 3:4 解码的，
 * 卡片用同一比例才不会额外裁切，列表、覆盖层、详情头图才能共用同一张缓存 Bitmap。
 */
internal object WorkCardTokens {
    /** 封面统一 3:4 竖版比例。 */
    const val CoverAspect = 0.75f

    /** 横滑轨道与窄栏里的固定卡宽。 */
    val CompactWidth: Dp = 112.dp

    /** 卡内文字区左右内边距。 */
    val TextPaddingH: Dp = 12.dp

    /** 卡内文字区上下内边距。 */
    val TextPaddingV: Dp = 10.dp

    /** 标题与次级信息之间的行间距。 */
    val TextGap: Dp = 2.dp

    /** 封面角标相对封面边缘的内缩距离。 */
    val BadgeInset: Dp = 6.dp

    /** 行卡缩略封面尺寸（固定 3:4）。 */
    val ThumbWidth: Dp = 48.dp
    val ThumbHeight: Dp = 64.dp

    /** 作品网格的列间距与行间距。 */
    val GridSpacing: Dp = 8.dp
}

/**
 * 作品封面：贴边封面（网格卡、行卡）与详情头图共用同一套解码与内存缓存。
 *
 * 不自带 clip：贴边封面由 Miuix Card 自身的 squircle 裁切（卡片内边距为 0，封面贴满卡片），
 * 自己再 clip 一次只会多一层裁剪；卡内缩略图请用 [WorkThumbnail]。
 *
 * @param albumId 传入时注册共享元素矩形，卡片点击才能播封面飞行；不参与飞行时传 null。
 */
@Composable
internal fun WorkCover(
    url: String?,
    modifier: Modifier = Modifier,
    albumId: String? = null,
    title: String? = null,
    contentDescription: String? = null,
    cornerDp: Float = HERO_CARD_CORNER_DP,
    contentScale: ContentScale = ContentScale.Crop,
) {
    Box(modifier = modifier.background(MiuixTheme.colorScheme.surfaceContainerHigh)) {
        if (url == null) return@Box
        val heroModifier = if (albumId != null) {
            Modifier.albumHeroCover(albumId = albumId, url = url, title = title, cornerDp = cornerDp)
        } else {
            Modifier
        }
        AlbumCoverImage(
            url = url,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = Modifier.fillMaxSize().then(heroModifier),
        )
    }
}

/**
 * 封面角标：名次、平台等叠在封面上的短标签。
 *
 * 半透明底保证在任意封面上可读；[highlighted] 用于名次前三这类需要主色强调的场景。
 */
@Composable
internal fun WorkCoverBadge(
    text: String,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.footnote2,
        fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Medium,
        color = if (highlighted) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSurface,
        maxLines = 1,
        modifier = modifier
            .squircleBackground(
                if (highlighted) {
                    MiuixTheme.colorScheme.primary
                } else {
                    MiuixTheme.colorScheme.surface.copy(alpha = 0.86f)
                },
                HERO_BADGE_CORNER_DP.dp,
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/**
 * 作品卡标签行：把标签压成一行 "#tag"，超宽省略。
 *
 * 标签数量与长度都不可控（日文长标签很常见），所以只取前 [maxTags] 个并强制单行，
 * 避免卡片高度随数据抖动。
 */
@Composable
internal fun WorkTagLine(
    tags: List<String>,
    modifier: Modifier = Modifier,
    maxTags: Int = 3,
) {
    if (tags.isEmpty()) return
    Text(
        text = tags.take(maxTags).joinToString(" ") { "#" + it },
        style = MiuixTheme.textStyles.footnote2,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(top = WorkCardTokens.TextGap),
    )
}

/** 封面右上角的车号角标：把对齐与内缩收进来，调用点不用重复写。 */
@Composable
internal fun BoxScope.WorkAlbumCodeBadge(code: String?) {
    if (code.isNullOrBlank()) return
    WorkCoverBadge(
        text = "#" + code,
        modifier = Modifier.align(Alignment.TopEnd).padding(WorkCardTokens.BadgeInset),
    )
}

/**
 * 让内容不参与父级固有尺寸测量。
 *
 * Coil 的位图自带远大于卡片的固有尺寸；行卡靠 [IntrinsicSize.Min] 让封面顶满行高，
 * 结果封面反而把整行撑成一张竖图的高度（首页「最新上架」每行约 210dp）。
 * 把固有尺寸报成 0 之后，行高只由右侧文字决定。
 */
private object NoIntrinsicMeasurePolicy : MeasurePolicy {
    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurables.first().measure(constraints)
        return layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int) = 0

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int) = 0

    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int) = 0

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int) = 0
}

/** [NoIntrinsicMeasurePolicy] 的调用入口：只包一个子节点，尺寸完全由它自己决定。 */
@Composable
private fun NoIntrinsicSize(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier, measurePolicy = NoIntrinsicMeasurePolicy)
}

/**
 * 次级信息行：作者在前、分类在后（分类用主色区分）。两者都缺时不占位。
 *
 * 分类是接口给的主分类标题，缺主分类时映射层已退回子分类，这里只负责排版。
 */
@Composable
private fun WorkCardSubtitleText(
    authors: String?,
    category: String?,
    modifier: Modifier = Modifier,
) {
    val authorText = authors?.trim()?.takeIf { it.isNotEmpty() }
    val categoryText = category?.trim()?.takeIf { it.isNotEmpty() }
    if (authorText == null && categoryText == null) return
    Text(
        text = buildAnnotatedString {
            if (authorText != null) append(authorText)
            if (authorText != null && categoryText != null) append(" · ")
            if (categoryText != null) {
                withStyle(SpanStyle(color = MiuixTheme.colorScheme.primary)) { append(categoryText) }
            }
        },
        style = MiuixTheme.textStyles.footnote1,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(top = WorkCardTokens.TextGap),
    )
}

/**
 * 行卡附加信息行：左侧标签、右侧车号（#123456，口径与详情页一致），两者都缺时不占位。
 *
 * 标签占满剩余宽度并让车号常驻，长标签先省略而不是把车号挤出屏幕。
 */
@Composable
internal fun WorkCardMetaRow(
    tags: List<String>,
    albumCode: String?,
    modifier: Modifier = Modifier,
) {
    if (tags.isEmpty() && albumCode.isNullOrBlank()) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(top = WorkCardTokens.TextGap),
    ) {
        if (tags.isNotEmpty()) {
            Text(
                text = tags.take(3).joinToString(" ") { "#" + it },
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }
        if (!albumCode.isNullOrBlank()) {
            Text(
                text = "#" + albumCode,
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

/**
 * 竖版作品卡：封面顶满卡片宽度，下方是标题、次级信息与可选的附加行。
 *
 * 外观契约与 [WorkRowCard] 一致：圆角来自 [HERO_CARD_CORNER_DP]、标题 body1/body2 加粗、
 * 次级信息 footnote1、角标走 [WorkCoverBadge]；不同场景只允许改宽度与标题行数。
 *
 * @param category 主分类标题，与作者同一行展示（主色）；为空时不占位。
 * @param badge 封面角标槽位，调用方用 `Modifier.align(...)` 决定落点。
 * @param footer 标题下方的附加行（作者、日期等），由调用方按需补充。
 */
@Composable
internal fun WorkGridCard(
    title: String,
    coverUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    albumId: String? = null,
    coverContentDescription: String? = null,
    subtitle: String? = null,
    category: String? = null,
    titleMaxLines: Int = 2,
    badge: (@Composable BoxScope.() -> Unit)? = null,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val heroHost = LocalAlbumHeroHost.current
    Card(
        modifier = modifier.pressScale(),
        insideMargin = PaddingValues(0.dp),
        onClick = {
            if (albumId != null) {
                heroHost?.startForward(albumId, coverUrl, HERO_CARD_CORNER_DP, title)
            }
            onClick()
        },
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            WorkCover(
                url = coverUrl,
                albumId = albumId,
                title = title,
                contentDescription = coverContentDescription,
                modifier = Modifier.fillMaxWidth().aspectRatio(WorkCardTokens.CoverAspect),
            )
            badge?.invoke(this)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = WorkCardTokens.TextPaddingH,
                    vertical = WorkCardTokens.TextPaddingV,
                ),
        ) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.body2,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = titleMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
            WorkCardSubtitleText(authors = subtitle, category = category)
            footer?.invoke(this)
        }
    }
}

/**
 * 行卡：封面顶满整行高度、贴齐左缘，右侧是标题、次级信息与附加内容。
 *
 * 行高只由文字决定，所以先用 height(IntrinsicSize.Min) 量出文字高度，封面再按 3:4 换算宽度；
 * 这与 [WorkGridCard] 是同一套外观契约，只是把封面换成了竖向撑满的变体。
 *
 * @param category 主分类标题，与作者同一行展示（主色）；为空时不占位。
 * @param leading 封面前方的槽位（如榜单名次）。
 * @param trailing 行尾槽位（如删除、点赞按钮）。落在卡片点击区之内，按钮自身的点击不受影响。
 */
@Composable
internal fun WorkRowCard(
    coverUrl: String?,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    albumId: String? = null,
    subtitle: String? = null,
    category: String? = null,
    coverContentDescription: String? = null,
    leading: (@Composable RowScope.() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    textEndPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val heroHost = LocalAlbumHeroHost.current
    Card(
        modifier = modifier.pressScale(),
        insideMargin = PaddingValues(0.dp),
        onClick = {
            if (albumId != null) {
                heroHost?.startForward(albumId, coverUrl, HERO_CARD_CORNER_DP, title)
            }
            onClick()
        },
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            leading?.invoke(this)
            NoIntrinsicSize(modifier = Modifier.fillMaxHeight()) {
                WorkCover(
                    url = coverUrl,
                    albumId = albumId,
                    title = title,
                    contentDescription = coverContentDescription,
                    modifier = Modifier.fillMaxHeight().aspectRatio(WorkCardTokens.CoverAspect),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(
                        start = WorkCardTokens.TextPaddingH,
                        end = textEndPadding,
                        top = WorkCardTokens.TextPaddingV,
                        bottom = WorkCardTokens.TextPaddingV,
                    ),
            ) {
                Text(
                    text = title,
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                WorkCardSubtitleText(authors = subtitle, category = category)
                content()
            }
            trailing?.invoke(this)
        }
    }
}

/**
 * 卡内缩略封面（历史、收藏、追更这类紧凑行）：固定 3:4、内缩圆角 [HERO_THUMB_CORNER_DP]。
 *
 * 与贴边封面的区别只有尺寸与圆角，解码与缓存仍走 [AlbumCoverImage]，
 * 这样从这些紧凑行点进详情也能播封面飞行。
 */
@Composable
internal fun WorkThumbnail(
    url: String?,
    modifier: Modifier = Modifier,
    albumId: String? = null,
    contentDescription: String? = null,
) {
    val thumbSize = Modifier.size(WorkCardTokens.ThumbWidth, WorkCardTokens.ThumbHeight)
    if (url == null) {
        Spacer(modifier = modifier.then(thumbSize))
        return
    }
    val heroModifier = if (albumId != null) {
        Modifier.albumHeroCover(albumId = albumId, url = url, cornerDp = HERO_THUMB_CORNER_DP)
    } else {
        Modifier
    }
    AlbumCoverImage(
        url = url,
        contentDescription = contentDescription,
        modifier = modifier
            .then(thumbSize)
            .squircleClip(HERO_THUMB_CORNER_DP.dp)
            .then(heroModifier),
    )
}
