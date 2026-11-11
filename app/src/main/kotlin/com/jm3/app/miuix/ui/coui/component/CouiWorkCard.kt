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
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
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
 * 皮肤差异只体现在曲率与文字档位；宽度、封面比、行内衬这些常量两套皮肤必须一致。
 */
internal object CouiWorkCardTokens {
    /** 封面宽高比：3:4 竖版，与详情页头图、hero 共享元素的飞行终点一致。 */
    const val CoverAspect = 0.75f
    /** 竖卡宽度：与原版卡一致，卡片内衬较小时正文列仍有 104dp。 */
    val GridWidth = 112.dp
    val GridSpacing = 8.dp
    val TextPaddingH = 4.dp
    val TextPaddingV = 8.dp
    val TextGap = 2.dp
    val BadgeInset = 6.dp
    val RowCoverWidth = 72.dp
    val RowCoverHeight = 96.dp
    val RowTextPaddingH = 12.dp
    val RowTextPaddingV = 8.dp
    val ThumbWidth = 48.dp
    val ThumbHeight = 64.dp
    const val TitleMaxLines = 2
    /** 行高缺省系数：库内文本按平台度量给行高，未指定时用它折算占位高度。 */
    const val LineHeightFactor = 1.35f
    val BadgePaddingH = 6.dp
    val BadgePaddingV = 1.dp

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
 * @param subtitle 作者等次级信息，与 [category] 同一行展示。
 * @param category 主分类标题，用强调色区分；为空时不占位。
 * @param badge 封面角标槽位，调用方用 Modifier.align 决定落点。
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
                .clip(shape)
                .background(CouiTheme.colorScheme.container),
        ) {
            CouiWorkCover(
                url = coverUrl,
                albumId = albumId,
                title = title,
                contentDescription = coverContentDescription,
                shape = shape,
                modifier = Modifier.fillMaxWidth().aspectRatio(CouiWorkCardTokens.CoverAspect),
            )
            badge?.invoke(this)
        }
        Column(
            modifier = Modifier.padding(
                horizontal = CouiWorkCardTokens.TextPaddingH,
                vertical = CouiWorkCardTokens.TextPaddingV,
            ),
        ) {
            CouiWorkTitleText(title = title, maxLines = titleMaxLines)
            CouiWorkSubtitleText(authors = subtitle, category = category)
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
            .couiPressMotion(press = press, cancelOnConsumedMove = true)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
            ) {
                if (albumId != null) heroHost?.startForward(albumId, coverUrl, cornerDp, title)
                onClick()
            }
            .graphicsLayer {
                scaleX = press.scale.value
                scaleY = press.scale.value
            }
            .clip(shape)
            // 全宽行也是不透明表面：垫卡片底色后再叠光；封面不透明，光画在内容之上才看得见
            .background(CouiTheme.colorScheme.card)
            .couiSpotlightLight(SpotlightPreset.OpaqueMedium1, interactive = true, aboveContent = true),
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

/** 卡片标题：按 [maxLines] 行占位，同一排里的卡片高度才一致。 */
@Composable
private fun CouiWorkTitleText(title: String, maxLines: Int) {
    Text(
        text = title,
        kind = CouiTextKind.PreferenceTitle,
        color = CouiTheme.colorScheme.onSurface,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = couiReservedLineHeight(CouiTextKind.PreferenceTitle, maxLines)),
    )
}

/** 次级信息行：作者在前、分类在后（分类用强调色）；两者都缺时不占位。 */
@Composable
private fun CouiWorkSubtitleText(authors: String?, category: String?) {
    val authorText = authors?.trim()?.takeIf { it.isNotEmpty() }
    val categoryText = category?.trim()?.takeIf { it.isNotEmpty() }
    if (authorText == null && categoryText == null) return
    val accent = CouiTheme.colorScheme.accent
    val subtitleColor = CouiTheme.colorScheme.onSurfaceVariant
    val annotated = remember(authorText, categoryText, accent) {
        buildAnnotatedString {
            if (authorText != null) append(authorText)
            if (authorText != null && categoryText != null) append(" · ")
            if (categoryText != null) {
                withStyle(SpanStyle(color = accent)) { append(categoryText) }
            }
        }
    }
    BasicText(
        text = annotated,
        style = rememberCouiTextStyle(CouiTextKind.PreferenceSummary),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = CouiWorkCardTokens.TextGap),
        color = { subtitleColor },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * 预留行高：库内文本按平台度量给行高，未指定时用 [CouiWorkCardTokens.LineHeightFactor] 折算。
 * 结果用于给标题占位，避免单行/两行标题把同一排卡片撑成不同高度。
 */
@Composable
private fun couiReservedLineHeight(kind: CouiTextKind, lines: Int): Dp {
    val style = rememberCouiTextStyle(kind)
    val density = LocalDensity.current
    val lineHeight = if (style.lineHeight.isSpecified) {
        style.lineHeight
    } else {
        style.fontSize * CouiWorkCardTokens.LineHeightFactor
    }
    return with(density) { (lineHeight.toPx() * lines).toDp() }
}
