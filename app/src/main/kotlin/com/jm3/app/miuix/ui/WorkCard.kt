package com.jm3.app.miuix.ui

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
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.ui.layout.LayoutModifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import com.jm3.app.miuix.ui.skin.LocalSkin
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 作品卡的统一尺寸令牌：所有作品卡（网格卡、行卡、缩略图）只从这里取尺寸，
 * 外圆角一律走皮肤 cardCorner（HyperOS 与 Miuix 默认 16dp 同源），
 * 卡内缩略图与角标走 [HERO_THUMB_CORNER_DP] / [HERO_BADGE_CORNER_DP]，调用点禁止再写 dp 字面量。
 *
 * 封面比例固定 3:4：[HERO_COVER_WIDTH_DP] / [HERO_COVER_HEIGHT_DP] 就是按 3:4 解码的，
 * 卡片用同一比例才不会额外裁切，列表、覆盖层、详情头图才能共用同一张缓存 Bitmap。
 */
internal object WorkCardTokens {
    /** 封面统一 3:4 竖版比例。 */
    const val CoverAspect = 0.75f

    /** 横滑轨道与窄栏里的固定卡宽。 */
    val CompactWidth: Dp = 112.dp

    /** 行卡标题固定占的行数；单行标题也占满，保证同一列表里卡片等高。 */
    const val RowTitleLines = 2

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

    /** 行卡文字区上下内边距：比默认内边距稍收紧，保证 4 行文字在 96dp 封面内完整排下。 */
    val RowTextPaddingV: Dp = 8.dp

    /**
     * 行卡贴边封面的高度与宽度：按 3:4 比例换算为 72dp 宽 x 96dp 高。
     *
     * 封面向父级汇报的固有尺寸固定为此值，阻断异步加载的图片原始分辨率向父级泄露，
     * 保证同一列表里封面大小与卡片高度绝对一致。
     */
    val RowCoverHeight: Dp = 96.dp
    val RowCoverWidth: Dp = 72.dp

    /** 作品网格的列间距与行间距。 */
    val GridSpacing: Dp = 8.dp

    /**
     * 卡内文字固定行高与字号之比。
     *
     * Miuix 的 textStyles 只声明字号、没有行高；行高缺省时按字形最大度量排版，
     * 含日文假名、emoji 的标题每行会比普通标题高出一截，同排卡片于是高矮不齐。
     * 按字号折算固定行高后，卡片高度只由字号与行数决定，与标题内容无关。
     */
    const val LineHeightRatio = 1.42f
}

/** 固定行高：样式已声明行高就沿用，否则按 [WorkCardTokens.LineHeightRatio] 从字号折算。 */
internal fun TextStyle.pinnedLineHeight(): TextUnit =
    if (lineHeight.isSpecified) lineHeight else fontSize * WorkCardTokens.LineHeightRatio

/**
 * 作品封面：贴边封面（网格卡、行卡）与详情头图共用同一套解码与内存缓存。
 *
 * 不自带 clip：贴边封面由 Miuix Card 自身的 squircle 裁切（卡片内边距为 0，封面贴满卡片），
 * 自己再 clip 一次只会多一层裁剪；卡内缩略图请用 [WorkThumbnail]。
 *
 * @param albumId 传入时注册共享元素矩形，卡片点击才能播封面飞行；不参与飞行时传 null。
 * @param cornerDp 卡片实际裁切圆角（取自皮肤 cardCorner），飞行两端必须用同一个值。
 */
@Composable
internal fun WorkCover(
    url: String?,
    cornerDp: Float,
    modifier: Modifier = Modifier,
    albumId: String? = null,
    title: String? = null,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
) {
    Box(modifier = modifier) {
        // 缺图时封面位置由 AlbumCoverImage 自己的骨架底衬占住，卡片尺寸一律由调用方给定
        val heroModifier = if (albumId != null && url != null) {
            Modifier.albumHeroCover(albumId = albumId, url = url, title = title, cornerDp = cornerDp)
        } else {
            Modifier
        }
        AlbumCoverImage(
            url = url,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = Modifier.fillMaxSize().then(heroModifier),
            skeleton = true,
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
    // 无标签时也占一行：同一行卡片要等高时传 true，否则有没有标签的卡片会差一行
    reserveLine: Boolean = false,
    lineHeight: TextUnit = TextUnit.Unspecified,
) {
    if (tags.isEmpty() && !reserveLine) return
    Text(
        text = if (tags.isEmpty()) "\u00A0" else tags.take(maxTags).joinToString(" ") { "#" + it },
        style = MiuixTheme.textStyles.footnote2,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        lineHeight = lineHeight,
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
 * 次级信息行：作者在前、分类在后（分类用主色区分）。两者都缺时不占位。
 *
 * 分类是接口给的主分类标题，缺主分类时映射层已退回子分类，这里只负责排版。
 */
@Composable
private fun WorkCardSubtitleText(
    authors: String?,
    category: String?,
    modifier: Modifier = Modifier,
    lineHeight: TextUnit = TextUnit.Unspecified,
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
        lineHeight = lineHeight,
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
 * 外观契约与 [WorkRowCard] 一致：圆角取自皮肤 cardCorner（HyperOS 16dp / ColorOS 18dp）、
 * 标题 body1/body2 加粗、次级信息 footnote1、角标走 [WorkCoverBadge]；不同场景只允许改宽度与标题行数。
 *
 * @param category 主分类标题，与作者同一行展示（主色）；为空时不占位。
 * @param titleLineHeight 标题固定行高，缺省时由字体度量决定（需要同排卡片绝对等高时传入）。
 * @param subtitleLineHeight 次级信息固定行高，语义同上。
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
    titleLineHeight: TextUnit = TextUnit.Unspecified,
    subtitleLineHeight: TextUnit = TextUnit.Unspecified,
    badge: (@Composable BoxScope.() -> Unit)? = null,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val heroHost = LocalAlbumHeroHost.current
    val cardCorner = LocalSkin.current.shapes.cardCorner
    Card(
        modifier = modifier.pressScale(),
        cornerRadius = cardCorner,
        insideMargin = PaddingValues(0.dp),
        onClick = {
            if (albumId != null) {
                heroHost?.startForward(albumId, coverUrl, cardCorner.value, title)
            }
            onClick()
        },
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            WorkCover(
                url = coverUrl,
                cornerDp = cardCorner.value,
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
                lineHeight = titleLineHeight,
                // 与标题行数对齐占位：同一行里单行/两行标题的卡片高度一致
                minLines = titleMaxLines,
                maxLines = titleMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
            WorkCardSubtitleText(authors = subtitle, category = category, lineHeight = subtitleLineHeight)
            footer?.invoke(this)
        }
    }
}

/**
 * 行卡：封面贴齐左缘、固定 3:4，右侧是标题、次级信息与附加内容。
 *
 * 封面高度取 [WorkCardTokens.RowCoverHeight]，宽度按 3:4 换算；行高 = max(封面高, 文字高)，
 * 所以同一列表里的封面大小一致，不会随标题占几行而变。
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
    val cardCorner = LocalSkin.current.shapes.cardCorner
    Card(
        modifier = modifier.pressScale(),
        cornerRadius = cardCorner,
        insideMargin = PaddingValues(0.dp),
        onClick = {
            if (albumId != null) {
                heroHost?.startForward(albumId, coverUrl, cardCorner.value, title)
            }
            onClick()
        },
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            leading?.invoke(this)
            WorkCover(
                url = coverUrl,
                cornerDp = cardCorner.value,
                albumId = albumId,
                title = title,
                contentDescription = coverContentDescription,
                modifier = Modifier
                    .fixedIntrinsicSize(
                        width = WorkCardTokens.RowCoverWidth,
                        height = WorkCardTokens.RowCoverHeight,
                    )
                    .fillMaxHeight()
                    .aspectRatio(WorkCardTokens.CoverAspect),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(
                        start = WorkCardTokens.TextPaddingH,
                        end = textEndPadding,
                        top = WorkCardTokens.RowTextPaddingV,
                        bottom = WorkCardTokens.RowTextPaddingV,
                    ),
            ) {
                Text(
                    text = title,
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                    // 固定占两行：单行标题的行不再比两行标题的行矮，列表里每张卡高度一致
                    minLines = WorkCardTokens.RowTitleLines,
                    maxLines = WorkCardTokens.RowTitleLines,
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
    Box(
        modifier = modifier
            .then(thumbSize)
            .squircleClip(HERO_THUMB_CORNER_DP.dp),
    ) {
        AlbumCoverImage(
            url = url,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize().then(heroModifier),
            skeleton = true,
        )
    }
}

/**
 * 锁定向父级汇报的固有尺寸（Intrinsic Size），
 * 彻底阻断异步加载的图片原始物理像素尺寸向父级泄露，
 * 避免不同分辨率与比例的封面图片导致卡片高度被撑大或参差不齐。
 */
private class FixedIntrinsicSizeModifier(
    private val width: Dp,
    private val height: Dp,
) : LayoutModifier {
    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            placeable.placeRelative(0, 0)
        }
    }

    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurable: IntrinsicMeasurable, width: Int): Int =
        height.roundToPx()

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurable: IntrinsicMeasurable, width: Int): Int =
        height.roundToPx()

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int): Int =
        width.roundToPx()

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int): Int =
        width.roundToPx()
}

internal fun Modifier.fixedIntrinsicSize(width: Dp, height: Dp): Modifier =
    this.then(FixedIntrinsicSizeModifier(width, height))
