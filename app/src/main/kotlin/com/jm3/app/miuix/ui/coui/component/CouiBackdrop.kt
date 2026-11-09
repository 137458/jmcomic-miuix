// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.jm3.app.miuix.ui.coui.theme.LocalCouiColors
import com.jm3.app.miuix.ui.glass.layerBackdropSource
import com.jm3.app.miuix.ui.glass.rememberBlurBackdrop
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import kotlin.math.round

/**
 * COUI 背景：纵向渐变底色 + 两枚低饱和氛围光斑。
 *
 * 光斑用 Compose Brush 而不是 RuntimeShader 绘制，保证 minSdk 26 也有层次，不会退化成纯色；
 * 30 秒级的缓慢漂移只改变光斑中心，肉眼几乎察觉不到，但静态截图与滑动时不会显得死板。
 *
 * 绿色/蓝色透明度按规范：浅色 6%/5%，深色 8%/6%。大面积填充只出现在背景，按钮与卡片不铺绿。
 */
@Composable
fun CouiSurfaceBackground(modifier: Modifier = Modifier) {
    val colors = LocalCouiColors.current
    val transition = rememberInfiniteTransition(label = "couiAmbient")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 30_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "couiAmbientDrift",
    )
    // 漂移量化到 1% 再驱动绘制：30 秒一轮只需约 3Hz 重建画刷，避免每帧全屏重绘
    val quantizedDrift by remember {
        derivedStateOf { round(drift * 100f) / 100f }
    }
    val greenAlpha = if (colors.isDark) 0.08f else 0.06f
    val blueAlpha = if (colors.isDark) 0.06f else 0.05f

    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(colors.backgroundTop, colors.backgroundBottom),
            ),
        )
        val width = size.width
        val height = size.height
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    colors.ambientGreen.copy(alpha = greenAlpha),
                    Color.Transparent,
                ),
                center = Offset(
                    x = width * (0.16f + quantizedDrift * 0.03f),
                    y = height * (0.10f + quantizedDrift * 0.04f),
                ),
                radius = width * 0.62f,
            ),
            size = size,
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    colors.ambientBlue.copy(alpha = blueAlpha),
                    Color.Transparent,
                ),
                center = Offset(
                    x = width * (0.88f - quantizedDrift * 0.04f),
                    y = height * (0.62f - quantizedDrift * 0.03f),
                ),
                radius = width * 0.58f,
            ),
            size = size,
        )
    }
}

/** COUI 玻璃采样源：能力检测与底色保护沿用现有实现，无着色器时返回 null。 */
@Composable
fun rememberCouiBlurBackdrop(): LayerBackdrop? = rememberBlurBackdrop()

/** 与现有玻璃源同一套注册入口，COUI 页面只换名字，不另写一份采样逻辑。 */
fun Modifier.couiBackdropSource(backdrop: LayerBackdrop?): Modifier =
    layerBackdropSource(backdrop)
