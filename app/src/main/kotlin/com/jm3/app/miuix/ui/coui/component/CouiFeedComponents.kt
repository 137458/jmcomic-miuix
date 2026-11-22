// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.jm3.core.model.CommentItem
import com.jm3.core.model.CommentSegment
import io.github.suqi8.coui.kmp.basic.ButtonDefaults
import io.github.suqi8.coui.kmp.basic.SegmentButton
import io.github.suqi8.coui.kmp.basic.SegmentButtonDefaults
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.effect.LocalMaterialBackdrop
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.CouiHapticEffect
import io.github.suqi8.coui.kmp.utils.rememberCouiHaptic

/**
 * 降级 FAB 的内容色：原厂 Fab 会自行下发 contentColor，低版本分支用这里补齐，
 * 页面写 Icon(tint = LocalCouiFallbackContentColor.current) 即可在两种实现下取同一颜色。
 */
val LocalCouiFallbackContentColor = staticCompositionLocalOf<Color> { Color.Unspecified }

/**
 * 胶囊分段选择：委托给 suqi8/coui 官方 [SegmentButton]（内置弹簧滑选、橡皮筋拖拽、悬停蒙层与触觉反馈）。
 *
 * 当外层提供了 [LocalMaterialBackdrop]（如顶栏槽位）时自动启用材质模糊，在滚动列表或卡片组内保持不透明。
 * [small] 对应库内 32dp 紧凑档（默认 40dp）。
 */
@Composable
fun CouiSegmentedCapsules(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    small: Boolean = false,
    enabled: Boolean = true,
    materialEffect: Boolean = LocalMaterialBackdrop.current != null,
) {
    if (items.isEmpty()) return
    val clampedIndex = selectedIndex.coerceIn(0, items.lastIndex)
    SegmentButton(
        items = items,
        selectedIndex = clampedIndex,
        onSelectedIndexChange = onSelect,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        materialEffect = materialEffect,
        responsiveWidthEnabled = false,
        height = if (small) SegmentButtonDefaults.HeightSmall else SegmentButtonDefaults.Height,
        padding = if (small) SegmentButtonDefaults.PaddingSmall else SegmentButtonDefaults.Padding,
    )
}

/**
 * 按压即触觉：手指按下（[PressInteraction.Press]）时触发一次 COUI 触觉，而不是等抬起的点击。
 *
 * 供自己拼手势的组件使用（作品卡、融合滑动行、个性化横幅等）；一次按住只触发一次。
 */
@Composable
fun CouiPressHapticEffect(
    interactionSource: InteractionSource,
    feedback: CouiHapticEffect = CouiHapticEffect.Switch,
) {
    val haptic = rememberCouiHaptic()
    LaunchedEffect(interactionSource, feedback, haptic) {
        interactionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Press) {
                haptic(feedback)
            }
        }
    }
}

/**
 * 统一的圆形头像与徽章：底色圆垫在下方，缺图、加载中或失败均不留透明空洞；
 * 支持传入昵称首字母占位。
 */
@Composable
fun CouiAvatar(
    url: String?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    name: String? = null,
    contentDescription: String? = null,
) {
    val colors = COUITheme.colorScheme
    val initial = name?.trim()?.firstOrNull()?.uppercaseChar()?.toString()
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(colors.container4),
        contentAlignment = Alignment.Center,
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = contentDescription ?: name ?: "头像",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape),
            )
        } else if (initial != null) {
            if (size >= 56.dp) {
                CouiSectionTitleText(
                    text = initial,
                    color = colors.labelSecondary,
                    textAlign = TextAlign.Center,
                )
            } else {
                Text(
                    text = initial,
                    style = COUITheme.textStyles.headlineXS,
                    color = colors.labelSecondary,
                )
            }
        }
    }
}

/** 回复线：深层回复贴在缩进区外侧的竖线，标识回复归属层级。 */
fun Modifier.drawReplyLine(depth: Int, color: Color): Modifier =
    if (depth <= 0) {
        this
    } else {
        drawBehind {
            val x = -6.dp.toPx()
            drawLine(
                color = color,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 2.dp.toPx(),
            )
        }
    }

const val COUI_COMMENT_MAX_INDENT_DEPTH = 3

/**
 * 统一评论行组件：收拢相册详情、论坛、阅读器与小说详情的评论呈现。
 */
@Composable
fun CouiCommentRow(
    item: CommentItem,
    modifier: Modifier = Modifier,
    depth: Int = 0,
    maxIndentDepth: Int = COUI_COMMENT_MAX_INDENT_DEPTH,
    authorColor: Color = COUITheme.colorScheme.primaryText,
    avatarSize: Dp? = null,
    // 默认不截断：正文最长可见行数由各页按自己的信息密度传入，组件不替页面决定
    bodyMaxLines: Int = Int.MAX_VALUE,
    onReply: ((CommentItem) -> Unit)? = null,
) {
    val colors = COUITheme.colorScheme
    val indentDepth = depth.coerceAtMost(maxIndentDepth)
    val resolvedAvatarSize = avatarSize ?: if (depth == 0) 40.dp else 30.dp

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp + (indentDepth * 12).dp, end = 16.dp, top = 10.dp, bottom = 10.dp)
            .drawReplyLine(depth = depth, color = colors.divider),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CouiAvatar(
                url = item.avatarUrl,
                size = resolvedAvatarSize,
                name = item.author,
                contentDescription = item.author,
            )
            item.level?.let { level ->
                Text(
                    text = "Lv.$level",
                    style = COUITheme.textStyles.bodyXS,
                    color = colors.labelSecondary,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = item.author,
                        style = COUITheme.textStyles.bodyS,
                        color = authorColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    item.levelName?.takeIf { it.isNotBlank() }?.let { levelName ->
                        Text(
                            text = "· $levelName",
                            style = COUITheme.textStyles.bodyXS,
                            color = colors.labelSecondary,
                            maxLines = 1,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
                if (item.likes > 0) {
                    Text(
                        text = "赞 ${item.likes}",
                        style = COUITheme.textStyles.bodyXS,
                        color = colors.labelSecondary,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            item.postDate?.takeIf { it.isNotBlank() }?.let { date ->
                Text(
                    text = date,
                    style = COUITheme.textStyles.bodyXS,
                    color = colors.labelSecondary,
                    maxLines = 1,
                    textAlign = TextAlign.Start,
                )
            }
            if (item.badges.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    item.badges.forEach { badge ->
                        CouiAvatar(
                            url = badge,
                            size = 22.dp,
                            contentDescription = "徽章",
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            val segments = item.body.ifEmpty { listOf(CommentSegment.Text(item.content)) }
            Column(modifier = Modifier.fillMaxWidth()) {
                segments.forEach { segment ->
                    when (segment) {
                        is CommentSegment.Text -> if (segment.text.isNotBlank()) {
                            CouiBodyText(
                                text = segment.text,
                                color = colors.labelPrimary,
                                maxLines = bodyMaxLines,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Start,
                            )
                        }
                        is CommentSegment.Album -> CouiBodyText(
                            text = segment.label,
                            color = colors.primaryText,
                            textAlign = TextAlign.Start,
                        )
                    }
                }
            }
            onReply?.let { reply ->
                val replyInteraction = remember { MutableInteractionSource() }
                CouiPressHapticEffect(replyInteraction)
                TextButton(
                    text = "回复",
                    onClick = { reply(item) },
                    colors = ButtonDefaults.textButtonColorsBorderless(
                        textColor = colors.primaryText,
                    ),
                    minWidth = 0.dp,
                    minHeight = 32.dp,
                    insideMargin = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                    textStyle = COUITheme.textStyles.buttonM,
                    interactionSource = replyInteraction,
                )
            }
        }
    }
}
