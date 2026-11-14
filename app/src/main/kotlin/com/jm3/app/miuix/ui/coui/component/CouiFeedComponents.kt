// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.component

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.effect.CouiFloatingRenderer
import com.itos.coui.effect.SpotlightPreset
import com.itos.coui.floatingactionbutton.Fab
import com.itos.coui.input.CouiHapticFeedback
import com.itos.coui.input.performCouiHapticFeedback
import com.itos.coui.motion.couiPressMotion
import com.itos.coui.motion.rememberCouiPressMotion
import com.itos.coui.segmentbutton.SegmentedControl
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.text.rememberCouiTextStyle
import com.itos.coui.theme.CouiColorScheme
import com.itos.coui.theme.CouiTheme
import com.jm3.core.model.CommentItem
import com.jm3.core.model.CommentSegment
import top.yukonga.miuix.kmp.basic.Icon

/**
 * 降级 FAB 的内容色：原厂 Fab 会自行下发 contentColor，低版本分支用这里补齐，
 * 页面写 Icon(tint = LocalCouiFallbackContentColor.current) 即可在两种实现下取同一颜色。
 */
val LocalCouiFallbackContentColor = staticCompositionLocalOf<Color> { Color.Unspecified }

/**
 * 胶囊分段选择：优先委托给 COUI 库官方 [SegmentedControl]（API 34+ 支持滑选、弹性阻尼与微光），
 * 低版本回落至等分胶囊实现。
 *
 * [backdrop] 默认为空，使用不透明分组底色，可安全放进滚动列表与卡片组内；
 * 传独立前景材质源时走透明模糊。[small] 对应库内 32dp 紧凑档（默认 40dp）。
 */
@Composable
fun CouiSegmentedCapsules(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    small: Boolean = false,
    enabled: Boolean = true,
    backdrop: CouiBackdrop? = null,
) {
    if (items.isEmpty()) return
    val clampedIndex = selectedIndex.coerceIn(0, items.lastIndex)
    if (Build.VERSION.SDK_INT >= 34) {
        CouiLibrarySegmentedControl(
            items = items,
            selectedIndex = clampedIndex,
            onSelect = onSelect,
            modifier = modifier,
            small = small,
            enabled = enabled,
            backdrop = backdrop,
        )
        return
    }
    CouiFallbackSegmentedCapsules(
        items = items,
        selectedIndex = clampedIndex,
        onSelect = onSelect,
        modifier = modifier,
        small = small,
        enabled = enabled,
    )
}

@RequiresApi(34)
@Composable
private fun CouiLibrarySegmentedControl(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier,
    small: Boolean,
    enabled: Boolean,
    backdrop: CouiBackdrop?,
) {
    val renderer = remember(backdrop != null) {
        if (backdrop != null) runCatching { CouiFloatingRenderer.standalone() }.getOrNull() else null
    }
    val effectiveBackdrop = if (renderer != null) backdrop else null
    SegmentedControl(
        modifier = modifier.fillMaxWidth(),
        labels = items,
        selectedIndex = selectedIndex,
        onSelected = onSelect,
        backdrop = effectiveBackdrop,
        dark = CouiTheme.isDark,
        enabled = enabled,
        small = small,
        renderer = renderer,
    )
}

@Composable
private fun CouiFallbackSegmentedCapsules(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier,
    small: Boolean,
    enabled: Boolean,
) {
    val colors: CouiColorScheme = CouiTheme.colorSchemeFor(CouiTheme.isDark)
    val labelStyle = rememberCouiTextStyle(CouiTextKind.ItemValue)
    val capsuleHeight = if (small) 32.dp else 40.dp
    val containerPadding = if (small) 2.dp else 4.dp
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CouiCapsuleShape)
            .background(colors.container)
            .padding(horizontal = containerPadding)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(containerPadding),
    ) {
        items.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val press = rememberCouiPressMotion()
            val capsuleInteraction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(if (small) 40.dp else CouiMinTouchTarget)
                    .selectable(
                        selected = selected,
                        enabled = enabled,
                        interactionSource = capsuleInteraction,
                        indication = null,
                        role = Role.Tab,
                        onClick = { onSelect(index) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(capsuleHeight)
                        .graphicsLayer {
                            scaleX = press.scale.value
                            scaleY = press.scale.value
                        }
                        .clip(CouiCapsuleShape)
                        // 未选中段也垫容器色：凝光要有不透明底才不会被页面底色吃掉
                        .background(
                            if (selected) {
                                colors.accent.copy(alpha = CouiSelectedContainerAlpha)
                            } else {
                                colors.container
                            },
                        )
                        .couiPressMotion(press = press, cancelOnConsumedMove = false)
                        .couiSpotlightLight(
                            preset = SpotlightPreset.ChipTab,
                            interactive = enabled,
                            color = if (selected) colors.accent else colors.onSurfaceVariant,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    val textColor = if (selected) colors.accent else colors.onSurfaceVariant
                    BasicText(
                        text = label,
                        style = labelStyle,
                        color = { if (enabled) textColor else textColor.copy(alpha = 0.38f) },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * 按压即触觉：手指按下（[PressInteraction.Press]）时触发一次 COUI 触觉，而不是等抬起的点击。
 *
 * 供自己拼手势的组件使用（作品卡、融合滑动行、个性化横幅等）；[couiPressable] 已内置同一段逻辑，
 * 且同样保证一次按住只触发一次。ROM 不支持该触觉时库侧返回 false，这里不重试也不报错。
 */
@Composable
fun CouiPressHapticEffect(
    interactionSource: InteractionSource,
    feedback: CouiHapticFeedback = CouiHapticFeedback.Switch,
) {
    val view = LocalView.current
    LaunchedEffect(interactionSource, feedback, view) {
        interactionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Press) {
                view.performCouiHapticFeedback(feedback)
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
    val colors = CouiTheme.colorSchemeFor(CouiTheme.isDark)
    val initial = name?.trim()?.firstOrNull()?.uppercaseChar()?.toString()
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(colors.container),
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
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            } else {
                Text(
                    text = initial,
                    kind = CouiTextKind.ItemTitle,
                    color = colors.onSurfaceVariant,
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
    authorColor: Color = CouiTheme.colorSchemeFor(CouiTheme.isDark).accent,
    avatarSize: Dp? = null,
    // 默认不截断：正文最长可见行数由各页按自己的信息密度传入，组件不替页面决定
    bodyMaxLines: Int = Int.MAX_VALUE,
    onReply: ((CommentItem) -> Unit)? = null,
) {
    val colors = CouiTheme.colorSchemeFor(CouiTheme.isDark)
    val indentDepth = depth.coerceAtMost(maxIndentDepth)
    val resolvedAvatarSize = avatarSize ?: if (depth == 0) 40.dp else 30.dp

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp + (indentDepth * 12).dp, end = 16.dp, top = 10.dp, bottom = 10.dp)
            .drawReplyLine(depth = depth, color = colors.outlineVariant),
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
                    kind = CouiTextKind.GroupFooter,
                    color = colors.onSurfaceVariant,
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
                        kind = CouiTextKind.ItemSummary,
                        color = authorColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    item.levelName?.takeIf { it.isNotBlank() }?.let { levelName ->
                        Text(
                            text = "· $levelName",
                            kind = CouiTextKind.GroupFooter,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
                if (item.likes > 0) {
                    Text(
                        text = "赞 ${item.likes}",
                        kind = CouiTextKind.GroupFooter,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            item.postDate?.takeIf { it.isNotBlank() }?.let { date ->
                Text(
                    text = date,
                    kind = CouiTextKind.GroupFooter,
                    color = colors.onSurfaceVariant,
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
                                color = colors.onSurface,
                                maxLines = bodyMaxLines,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Start,
                            )
                        }
                        is CommentSegment.Album -> CouiBodyText(
                            text = segment.label,
                            color = colors.accent,
                            textAlign = TextAlign.Start,
                        )
                    }
                }
            }
            onReply?.let { reply ->
                Text(
                    text = "回复",
                    kind = CouiTextKind.ItemValue,
                    color = colors.accent,
                    maxLines = 1,
                    textAlign = TextAlign.Start,
                    modifier = Modifier
                        .clip(CouiCapsuleShape)
                        .couiPressable(
                            shape = CouiCapsuleShape,
                            spotlight = SpotlightPreset.ChipTab,
                            minTouchTarget = CouiMinTouchTarget,
                            onClick = { reply(item) },
                        )
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
        }
    }
}
