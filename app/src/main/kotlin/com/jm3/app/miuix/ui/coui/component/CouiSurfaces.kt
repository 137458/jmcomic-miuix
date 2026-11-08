// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.component

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.jm3.app.miuix.ui.coui.theme.CouiMotion
import com.jm3.app.miuix.ui.coui.theme.CouiShapes
import com.jm3.app.miuix.ui.coui.theme.CouiType
import com.jm3.app.miuix.ui.coui.theme.LocalCouiColors
import top.yukonga.miuix.kmp.squircle.squircleClip

/**
 * 按压反馈：97% 缩放 + 68%/6% 的浅深反转覆盖层。
 *
 * 覆盖层在内容之后绘制，因此不会盖住图标与文字的可读性；只作用于整卡，不参与滚动测量。
 */
@Composable
private fun Modifier.couiPressFeedback(
    interaction: MutableInteractionSource,
    shapeRadiusDp: Float,
): Modifier {
    val colors = LocalCouiColors.current
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = CouiMotion.PressSpring,
        label = "couiPressScale",
    )
    val overlay by animateFloatAsState(
        targetValue = if (pressed) 0.06f else 0f,
        animationSpec = CouiMotion.PressSpring,
        label = "couiPressOverlay",
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clip(RoundedCornerShape(shapeRadiusDp.dp))
        .drawWithContent {
            drawContent()
            if (overlay > 0f) {
                drawRect(
                    if (colors.isDark) Color.White.copy(alpha = overlay) else Color.Black.copy(alpha = overlay),
                )
            }
        }
}

/**
 * COUI 主卡片：20dp 连续曲率、浅色双层投影、深色 1dp 描边。
 *
 * 不投影时仍靠描边与底色差保持层次，这是验收口径“无投影仍有层次”的落点。
 */
@Composable
fun CouiCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalCouiColors.current
    val radius = CouiShapes.cardRadius.value
    val shape = RoundedCornerShape(CouiShapes.cardRadius)
    val interaction = remember { MutableInteractionSource() }
    val clickableModifier = if (onClick != null) {
        Modifier
            .couiPressFeedback(interaction, radius)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
    } else {
        Modifier
    }
    Column(
        modifier = modifier
            .then(if (colors.isDark) Modifier else Modifier.shadow(
                elevation = 10.dp,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.10f),
                spotColor = Color.Black.copy(alpha = 0.12f),
            ))
            .squircleClip(CouiShapes.cardRadius)
            .background(colors.card)
            .then(if (colors.isDark) Modifier.border(1.dp, colors.cardBorder, shape) else Modifier)
            .then(clickableModifier)
            .padding(contentPadding),
        content = content,
    )
}

/** COUI 嵌套卡片：16dp 曲率 + 1dp 边框，用于主卡片里的分组行。 */
@Composable
fun CouiNestedCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalCouiColors.current
    val shape = RoundedCornerShape(CouiShapes.nestedCardRadius)
    Column(
        modifier = modifier
            .squircleClip(CouiShapes.nestedCardRadius)
            .background(colors.nestedCard)
            .border(1.dp, colors.cardBorder, shape)
            .padding(contentPadding),
        content = content,
    )
}

/** 分组标题：17sp/600，统一 28dp 分组间距基线。 */
@Composable
fun CouiSectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    BasicText(
        text = text,
        style = CouiType.section.copy(color = LocalCouiColors.current.textPrimary),
        modifier = modifier.padding(top = 28.dp, bottom = 12.dp),
    )
}

/**
 * COUI 弹窗：页内浮层而非独立 Window。
 *
 * 独立 Window 的遮罩由系统 Dialog 决定，无法稳定压到 35%；页内浮层能直接控制遮罩、
 * 圆角与弹簧入场，也不受 NavDisplay 尺寸变化影响。返回键在浮层显示期间只关弹窗。
 */
@Composable
fun CouiDialogOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!visible) return
    BackHandler(onBack = onDismiss)
    val colors = LocalCouiColors.current
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, CouiMotion.DialogSpring) }
    val shape = RoundedCornerShape(CouiShapes.dialogRadius)
    val consumeInteraction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.scrim)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 400.dp)
                .graphicsLayer {
                    val value = progress.value
                    scaleX = 0.92f + 0.08f * value
                    scaleY = 0.92f + 0.08f * value
                    alpha = value
                }
                .shadow(
                    elevation = 28.dp,
                    shape = shape,
                    clip = false,
                    ambientColor = Color.Black.copy(alpha = 0.12f),
                    spotColor = Color.Black.copy(alpha = 0.14f),
                )
                .squircleClip(CouiShapes.dialogRadius)
                .background(colors.card)
                .clickable(
                    interactionSource = consumeInteraction,
                    indication = null,
                    onClick = {},
                )
                .padding(24.dp),
            content = content,
        )
    }
}

/** COUI 底部面板：顶部 28dp 圆角 + 32×4 拖拽条，300ms 级弹簧入场。 */
@Composable
fun CouiBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!visible) return
    BackHandler(onBack = onDismiss)
    val colors = LocalCouiColors.current
    val progress = remember { Animatable(0f) }
    var sheetHeight by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { progress.animateTo(1f, CouiMotion.SheetSpring) }
    val consumeInteraction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.scrim)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { sheetHeight = it.height }
                .graphicsLayer {
                    translationY = (1f - progress.value) * sheetHeight
                }
                .clip(RoundedCornerShape(topStart = CouiShapes.sheetRadius, topEnd = CouiShapes.sheetRadius))
                .background(colors.card)
                .clickable(
                    interactionSource = consumeInteraction,
                    indication = null,
                    onClick = {},
                )
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 32.dp, height = 4.dp)
                        .background(colors.textTertiary.copy(alpha = 0.6f), CircleShape),
                )
            }
            content()
        }
    }
}
