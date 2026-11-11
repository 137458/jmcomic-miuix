// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.itos.coui.effect.SpotlightPreset
import com.itos.coui.motion.couiPressMotion
import com.itos.coui.motion.rememberCouiPressMotion
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.shape.CouiShape
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.text.rememberCouiTextStyle
import com.itos.coui.theme.CouiTheme
import com.jm3.app.miuix.ui.AlbumCoverImage
import com.jm3.app.miuix.ui.LocalAlbumHeroHost
import com.jm3.app.miuix.ui.albumHeroCover
import com.jm3.app.miuix.ui.skin.LocalSkin

/**
 * COUI 作品卡的尺寸契约：与 Miuix 版 [com.jm3.app.miuix.ui.WorkCardTokens] 共用同一套排版比例，
 * 皮肤差异只体现在曲率与文字档位；宽度、封面比这些常量两套皮肤必须一致。
 *
 * 卡内文字左右内衬取页面边距本身（16dp），卡内文字左缘因此落在 16dp + 16dp = 32dp，
 * 与 [com.jm3.app.miuix.ui.coui.component.CouiGroupTitle] 固定的 32dp 标题左缘同一条竖线。
 */
internal object CouiWorkCardTokens {
    /** 封面宽高比：3:4 竖版，与详情页头图、hero 共享元素的飞行终点一致。 */
    const val CoverAspect = 0.75f
    /** 竖卡宽度：与原版卡一致，扣掉左右内衬后正文列仍有 80dp。 */
    val GridWidth = 112.dp
    val GridSpacing = 8.dp
    val TextPaddingH = 16.dp
    val TextPaddingV = 8.dp
    val TextGap = 2.dp
    /** 分组标题的文字左缘：CouiGroupTitle 固定 32dp 内缩（CouiPageState.kt 的 horizontal = 32.dp）。 */
    val SectionTextInset = 32.dp
    /** 轨道首尾留白：卡片左缘 = 标题左缘 - 卡内内衬，卡内文字才与分组标题落在同一条竖线上。 */
    val TrackContentPadding = SectionTextInset - TextPaddingH
    /** 次级信息恒定占一行：缺作者或分类的卡片也要占住同样高度，同排卡片高度才一致。 */
    const val SubtitleLines = 1
    /** 次级信息里作者与分类之间的分隔符，单独成段后不再靠字符串拼接。 */
    const val SubtitleSeparator = " · "
    val BadgeInset = 6.dp
    val RowCoverWidth = 72.dp
    val RowCoverHeight = 96.dp
    val RowTextPaddingH = 16.dp
    val RowTextPaddingV = 8.dp
    val ThumbWidth = 48.dp
    val ThumbHeight = 64.dp
    const val TitleMaxLines = 2
    /** 行高缺省系数：库内文本按平台度量给行高，未指定时用它折算占位高度。 */
    const val LineHeightFactor = 1.35f
    val BadgePaddingH = 6.dp
    val BadgePaddingV = 1.dp
    /** 分类角标字号：比 Description 档位再小一号，角标只做提示，不抢封面与标题。 */
    val CategoryBadgeFontSize = 10.sp
    val CategoryBadgePaddingH = 6.dp
    val CategoryBadgePaddingV = 2.dp
    /** 分类角标距封面左、下缘的内缩。 */
    val CategoryBadgeInset = 6.dp
    /** 角标宽度上限的预留量：按左右各一个内缩算，长文案（如小说列表的「更新至 第N话」）也尽量排得下。 */
    val CategoryBadgeMaxInset = 12.dp

    /** 角标底色：封面本身颜色不可控，半透明黑底保证白字在任何封面上都能读。 */
    val BadgeScrim = Color(0x73000000)

    // 榜单前三名的金属色：与 COUI 榜单规范一致，不跟皮肤走
    val Gold = Color(0xFFFFD700)
    val Silver = Color(0xFFC0C0C0)
    val Bronze = Color(0xFFCD7F32)
}

/** 封面/卡片圆角：与皮肤 cardCorner 同源，卡片、角标、hero 飞行终点的曲率才一致。 */
@Composable
internal fun couiWorkCardShape(): CouiShape = CouiShape(radius = LocalSkin.current.shapes.cardCorner)

/**
 * 作品封面：3:4 裁剪 + 皮肤曲率 + 骨架屏，可挂共享元素 hero。
 *
 * hero 修饰符必须落在图片自身的修饰符链上：hero 转场靠透明度隐藏原图，骨架底衬若挂在外面，
 * 飞行途中会露出灰块。
 */
@Composable
fun CouiWorkCover(
    url: String?,
    modifier: Modifier = Modifier,
    albumId: String? = null,
    title: String? = null,
    contentDescription: String? = null,
    shape: Shape = couiWorkCardShape(),
) {
    val corner = LocalSkin.current.shapes.cardCorner
    val heroModifier = if (albumId != null && url != null) {
        Modifier.albumHeroCover(albumId = albumId, url = url, title = title, cornerDp = corner.value)
    } else {
        Modifier
    }
    Box(modifier = modifier.clip(shape).background(CouiTheme.colorScheme.container)) {
        AlbumCoverImage(
            url = url,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize().then(heroModifier),
            skeleton = true,
        )
    }
}

/** 封面角标：半透明黑底胶囊；[highlighted] 用强调色表示特殊状态（如新话数）。 */
@Composable
fun CouiWorkBadge(
    text: String,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    val colors = CouiTheme.colorScheme
    Box(
        modifier = modifier
            .clip(CouiCapsuleShape)
            .background(if (highlighted) colors.accent else CouiWorkCardTokens.BadgeScrim)
            .padding(horizontal = CouiWorkCardTokens.BadgePaddingH, vertical = CouiWorkCardTokens.BadgePaddingV),
    ) {
        Text(
            text = text,
            kind = CouiTextKind.Description,
            color = if (highlighted) colors.onAccent else Color.White,
            maxLines = 1,
        )
    }
}

/** 封面右上角的车号角标：对齐与内缩收在这里，调用点只填车号。 */
@Composable
fun BoxScope.CouiWorkAlbumCodeBadge(code: String?) {
    if (code.isNullOrBlank()) return
    CouiWorkBadge(
        text = "#" + code,
        modifier = Modifier.align(Alignment.TopEnd).padding(CouiWorkCardTokens.BadgeInset),
    )
}

/**
 * 封面左下角的分类角标：竖卡正文列只有 80dp，分类留在副标题行会把作者挤成一两个字，
 * 因此改挂封面；底部左对齐，与左上角的名次槽位互不遮挡。
 *
 * 宽度上限按封面宽扣预留量算，长分类名走省略号，不会顶到封面右缘。
 */
@Composable
fun BoxScope.CouiWorkCategoryBadge(category: String?) {
    val label = couiNormalizedText(category) ?: return
    val base = rememberCouiTextStyle(CouiTextKind.Description)
    val lineHeight = couiPinnedLineHeight(CouiWorkCardTokens.CategoryBadgeFontSize, TextUnit.Unspecified)
    // 行高钉死成与字号同源的固定值：角标高低只跟字号有关，不随字形度量浮动
    val style = remember(base, lineHeight) {
        base.copy(
            fontSize = CouiWorkCardTokens.CategoryBadgeFontSize,
            lineHeight = lineHeight,
            fontWeight = FontWeight.Medium,
        )
    }
    Box(
        modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(CouiWorkCardTokens.CategoryBadgeInset)
            .widthIn(max = couiCategoryBadgeMaxWidth(CouiWorkCardTokens.GridWidth))
            .clip(CouiCapsuleShape)
            .background(CouiWorkCardTokens.BadgeScrim)
            .padding(
                horizontal = CouiWorkCardTokens.CategoryBadgePaddingH,
                vertical = CouiWorkCardTokens.CategoryBadgePaddingV,
            ),
    ) {
        BasicText(
            text = label,
            style = style,
            color = { Color.White },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 榜单名次徽章：前三名用金银铜底色，其余用次要文字色。
 * 名次由调用点按全局偏移换算（见 CouiFeedPolicy.displayRank），这里只负责呈现。
 */
@Composable
fun CouiWorkRankBadge(rank: Int, modifier: Modifier = Modifier) {
    val metal = when (rank) {
        1 -> CouiWorkCardTokens.Gold
        2 -> CouiWorkCardTokens.Silver
        3 -> CouiWorkCardTokens.Bronze
        else -> null
    }
    if (metal == null) {
        Text(
            text = rank.toString(),
            kind = CouiTextKind.Stepper,
            color = CouiTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
            maxLines = 1,
        )
        return
    }
    Box(
        modifier = modifier
            .size(width = 30.dp, height = 20.dp)
            .clip(CouiCapsuleShape)
            .background(metal),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = rank.toString(),
            kind = CouiTextKind.Description,
            // 金属底是固定亮色，文字固定深色，不跟明暗主题翻转
            color = Color(0xFF1A1A1A),
            maxLines = 1,
        )
    }
}

/**
 * 竖版作品卡：封面顶满卡片宽度，下方是标题、次级信息与可选的附加行。
 *
 * 手感按 COUI 三段拼：按压弹簧缩放（[rememberCouiPressMotion]）、按压即触觉（[CouiPressHapticEffect]）、
 * 触点点亮（[SpotlightPreset.OpaqueMedium1]）。点亮与卡片同节点、同尺寸：卡片自身垫不透明底后再裁剪叠光，
 * 光心才按整卡几何归一化，不会像早先那样被封面高度反算成纵向压缩。
 *
 * @param subtitle 作者等次级信息，独占副标题行。
 * @param category 主分类标题，画在封面左下角角标；为空时不绘制。
 * @param badge 封面角标槽位，调用方用 Modifier.align 决定落点（榜单名次在左上角，与分类角标不冲突）。
 * @param footer 标题下方的附加行（更新时间等）。
 */
@Composable
fun CouiWorkGridCard(
    title: String,
    coverUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    albumId: String? = null,
    coverContentDescription: String? = null,
    subtitle: String? = null,
    category: String? = null,
    titleMaxLines: Int = CouiWorkCardTokens.TitleMaxLines,
    badge: (@Composable BoxScope.() -> Unit)? = null,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val shape = couiWorkCardShape()
    val press = rememberCouiPressMotion()
    val interaction = remember { MutableInteractionSource() }
    val heroHost = LocalAlbumHeroHost.current
    val cornerDp = LocalSkin.current.shapes.cardCorner.value
    // 分类挪出副标题行：正文列只有 80dp，作者与分类挤同一行时作者会被截到只剩一两个字
    val labels = remember(subtitle, category) { couiWorkGridLabels(subtitle, category) }
    CouiPressHapticEffect(interaction)
    Column(
        modifier = modifier
            .width(CouiWorkCardTokens.GridWidth)
            // 卡片自身是不透明表面：按压缩放、裁剪、垫底色、叠光都落在同一节点上，
            // 光才按整卡轮廓折射，缩放也不会只带动封面而露出卡片色环。
            .graphicsLayer {
                scaleX = press.scale.value
                scaleY = press.scale.value
            }
            .clip(shape)
            .background(CouiTheme.colorScheme.card)
            .couiPressMotion(press = press, cancelOnConsumedMove = true)
            // 封面铺满整卡，光画在内容之下会被整个盖住，改画在内容之上（同库弹窗按钮的画法）
            .couiSpotlightLight(SpotlightPreset.OpaqueMedium1, interactive = true, aboveContent = true)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
            ) {
                if (albumId != null) heroHost?.startForward(albumId, coverUrl, cornerDp, title)
                onClick()
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(CouiTheme.colorScheme.container),
        ) {
            CouiWorkCover(
                url = coverUrl,
                albumId = albumId,
                title = title,
                contentDescription = coverContentDescription,
                shape = RectangleShape,
                modifier = Modifier.fillMaxWidth().aspectRatio(CouiWorkCardTokens.CoverAspect),
            )
            badge?.invoke(this)
            CouiWorkCategoryBadge(category = labels.categoryBadge)
        }
        Column(
            modifier = Modifier.padding(
                horizontal = CouiWorkCardTokens.TextPaddingH,
                vertical = CouiWorkCardTokens.TextPaddingV,
            ),
        ) {
            CouiWorkTitleText(title = title, maxLines = titleMaxLines)
            CouiWorkAuthorText(author = labels.author)
            footer?.invoke(this)
        }
    }
}

/**
 * 行卡：封面贴齐左缘固定 3:4，右侧是标题、次级信息与附加内容。
 *
 * 行高由封面决定（[CouiWorkCardTokens.RowCoverHeight]），标题占几行都不改变同一列表里的封面大小。
 *
 * @param leading 封面前方槽位（榜单名次）。
 * @param trailing 行尾槽位（下载、更多）。落在卡片点击区内，内部按钮自己的点击不受影响。
 */
@Composable
fun CouiWorkRowCard(
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
    val shape = couiWorkCardShape()
    val press = rememberCouiPressMotion()
    val interaction = remember { MutableInteractionSource() }
    val heroHost = LocalAlbumHeroHost.current
    val cornerDp = LocalSkin.current.shapes.cardCorner.value
    CouiPressHapticEffect(interaction)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = press.scale.value
                scaleY = press.scale.value
            }
            .clip(shape)
            // 全宽行也是不透明表面：垫卡片底色后再叠光；封面不透明，光画在内容之上才看得见
            .background(CouiTheme.colorScheme.card)
            .couiPressMotion(press = press, cancelOnConsumedMove = true)
            .couiSpotlightLight(SpotlightPreset.OpaqueMedium1, interactive = true, aboveContent = true)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
            ) {
                if (albumId != null) heroHost?.startForward(albumId, coverUrl, cornerDp, title)
                onClick()
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke(this)
        CouiWorkCover(
            url = coverUrl,
            albumId = albumId,
            title = title,
            contentDescription = coverContentDescription,
            shape = shape,
            modifier = Modifier
                .width(CouiWorkCardTokens.RowCoverWidth)
                .height(CouiWorkCardTokens.RowCoverHeight),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(
                    start = CouiWorkCardTokens.RowTextPaddingH,
                    end = textEndPadding,
                    top = CouiWorkCardTokens.RowTextPaddingV,
                    bottom = CouiWorkCardTokens.RowTextPaddingV,
                ),
        ) {
            CouiWorkTitleText(title = title, maxLines = CouiWorkCardTokens.TitleMaxLines)
            CouiWorkSubtitleText(authors = subtitle, category = category)
            content()
        }
        trailing?.invoke(this)
    }
}

/** 缩略图：评论、动态、通知里的 48×64 小封面。 */
@Composable
fun CouiWorkThumbnail(
    url: String?,
    modifier: Modifier = Modifier,
    albumId: String? = null,
    contentDescription: String? = null,
) {
    CouiWorkCover(
        url = url,
        albumId = albumId,
        contentDescription = contentDescription,
        shape = couiWorkCardShape(),
        modifier = modifier.size(
            width = CouiWorkCardTokens.ThumbWidth,
            height = CouiWorkCardTokens.ThumbHeight,
        ),
    )
}

/** 卡片标题：行高钉死并按 [maxLines] 行占位，单行与两行标题不会把同一排卡片撑成不同高度。 */
@Composable
private fun CouiWorkTitleText(title: String, maxLines: Int) {
    val pinned = couiPinnedText(CouiTextKind.PreferenceTitle, maxLines)
    val color = CouiTheme.colorScheme.onSurface
    BasicText(
        text = title,
        style = pinned.style.copy(fontWeight = FontWeight.Medium),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = pinned.blockHeight),
        color = { color },
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * 次级信息行：作者在前、分类在后（分类用强调色）。
 *
 * 作者与分类拆成两段独立文本：分类先测宽、不参与剩余空间分配，作者可压缩并自己省略。
 * 拼成一条字符串时省略号会把分类整段吞掉，作者一长就分不出作品分类。
 * 两段都缺时仍占住一行高度，同一排卡片的底边才会对齐。
 */
@Composable
private fun CouiWorkSubtitleText(authors: String?, category: String?) {
    val parts = remember(authors, category) { couiWorkSubtitleParts(authors, category) }
    val pinned = couiPinnedText(CouiTextKind.PreferenceSummary, CouiWorkCardTokens.SubtitleLines)
    val muted = CouiTheme.colorScheme.onSurfaceVariant
    val accent = CouiTheme.colorScheme.accent
    val author = parts.author
    val categoryText = parts.category
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = CouiWorkCardTokens.TextGap)
            .heightIn(min = pinned.blockHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (author != null) {
            BasicText(
                text = author,
                style = pinned.style,
                modifier = Modifier.weight(1f, fill = false),
                color = { muted },
                maxLines = CouiWorkCardTokens.SubtitleLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (author != null && categoryText != null) {
            BasicText(
                text = CouiWorkCardTokens.SubtitleSeparator,
                style = pinned.style,
                color = { muted },
                maxLines = CouiWorkCardTokens.SubtitleLines,
            )
        }
        if (categoryText != null) {
            BasicText(
                text = categoryText,
                style = pinned.style,
                color = { accent },
                maxLines = CouiWorkCardTokens.SubtitleLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 竖卡副标题：只放作者，独占整列宽度。
 *
 * 作者缺失时仍占住一行高度，同排卡片的底边才不会因为有无作者而错开。
 */
@Composable
private fun CouiWorkAuthorText(author: String?) {
    val pinned = couiPinnedText(CouiTextKind.PreferenceSummary, CouiWorkCardTokens.SubtitleLines)
    val muted = CouiTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = CouiWorkCardTokens.TextGap)
            .heightIn(min = pinned.blockHeight),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (author != null) {
            BasicText(
                text = author,
                style = pinned.style,
                modifier = Modifier.fillMaxWidth(),
                color = { muted },
                maxLines = CouiWorkCardTokens.SubtitleLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 行高折算：库内文本未声明行高时按字形度量排版，同排卡片会差一两个像素，这里统一折算成固定值。 */
internal fun couiPinnedLineHeight(fontSize: TextUnit, declaredLineHeight: TextUnit): TextUnit =
    if (declaredLineHeight.isSpecified) {
        declaredLineHeight
    } else {
        fontSize * CouiWorkCardTokens.LineHeightFactor
    }

/** 文字块占位高度：固定行高乘以预留行数，卡片高度只跟行数有关。 */
internal fun couiTextBlockHeight(lineHeight: TextUnit, lines: Int): TextUnit = lineHeight * lines.toFloat()

/** 次级信息的两段内容：作者与分类各自独立成段，空串与纯空白归一成 null。 */
internal data class CouiWorkSubtitleParts(val author: String?, val category: String?)

internal fun couiWorkSubtitleParts(authors: String?, category: String?): CouiWorkSubtitleParts =
    CouiWorkSubtitleParts(
        author = couiNormalizedText(authors),
        category = couiNormalizedText(category),
    )

/** 文本归一：去首尾空白，空白串一律当没有内容，避免角标里画出空胶囊。 */
internal fun couiNormalizedText(text: String?): String? = text?.trim()?.takeIf { it.isNotEmpty() }

/** 分类角标宽度上限：封面宽度扣掉两侧留白，封面过窄时收敛到 0 而不是负数。 */
internal fun couiCategoryBadgeMaxWidth(coverWidth: Dp): Dp =
    (coverWidth - CouiWorkCardTokens.CategoryBadgeMaxInset).coerceAtLeast(0.dp)

/** 竖卡的展示分工：副标题行只放作者，分类挂封面角标。 */
internal data class CouiWorkGridLabels(val author: String?, val categoryBadge: String?)

internal fun couiWorkGridLabels(authors: String?, category: String?): CouiWorkGridLabels =
    CouiWorkGridLabels(
        author = couiNormalizedText(authors),
        categoryBadge = couiNormalizedText(category),
    )


/** 钉死行高的文本样式与它占住的高度：渲染高度与占位高度同源，字形误差才不会累积成卡片高差。 */
private class CouiPinnedText(val style: TextStyle, val blockHeight: Dp)

@Composable
private fun couiPinnedText(kind: CouiTextKind, lines: Int): CouiPinnedText {
    val base = rememberCouiTextStyle(kind)
    val lineHeight = couiPinnedLineHeight(base.fontSize, base.lineHeight)
    val style = remember(base, lineHeight) { base.copy(lineHeight = lineHeight) }
    val density = LocalDensity.current
    val blockHeight = with(density) { couiTextBlockHeight(lineHeight, lines).toDp() }
    return remember(style, blockHeight) { CouiPinnedText(style, blockHeight) }
}
