package com.jm3.app.miuix.ui.effect

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.floor

/**
 * HyperOS 流光渐变背景：四个彩色光点缓慢漂移，颜色每 [BgEffectConfig.Config.colorInterpPeriod] 秒
 * 在预设的三组之间插值一次。
 *
 * @param alpha 背景整体不透明度，用于随滚动淡出；取值为 0 时停掉动画。
 * @param animated 是否播放动画（低端机或需要省电时可以关掉）。
 * @param isFullSize 光点覆盖范围，true 取高度的 80%，false 取 50%。
 */
@Composable
fun BgEffectBackground(
    modifier: Modifier = Modifier,
    animated: Boolean = true,
    isFullSize: Boolean = true,
    alpha: () -> Float = { 1f },
    content: @Composable BoxScope.() -> Unit,
) {
    if (!isRuntimeShaderSupported()) {
        Box(modifier = modifier, content = content)
        return
    }

    val surface = MiuixTheme.colorScheme.surface
    // 用 surface 亮度判断明暗，而不是 isSystemInDarkTheme：应用内主题可以覆盖系统设置
    val isDarkTheme = surface.luminance() < 0.5f
    val isTablet = LocalConfiguration.current.screenWidthDp >= 600
    val deviceType = if (isTablet) DeviceType.PAD else DeviceType.PHONE
    val painter = remember { BgEffectPainter() }
    val preset = remember(deviceType, isDarkTheme) { BgEffectConfig.get(deviceType, isDarkTheme) }
    val colorStage = remember { Animatable(0f) }

    LaunchedEffect(animated, preset) {
        if (!animated) return@LaunchedEffect
        val animatesColors = preset.colors1 !== preset.colors2 || preset.colors2 !== preset.colors3
        if (!animatesColors) return@LaunchedEffect

        var targetStage = floor(colorStage.value) + 1f
        while (isActive) {
            delay((preset.colorInterpPeriod * 500).toLong())
            colorStage.animateTo(
                targetValue = targetStage,
                animationSpec = spring(dampingRatio = 0.9f, stiffness = 35f),
            )
            targetStage += 1f
        }
    }

    Box(modifier = modifier) {
        Spacer(
            modifier =
                Modifier
                    .fillMaxSize()
                    .bgEffectDraw(
                        painter = painter,
                        preset = preset,
                        deviceType = deviceType,
                        isDarkTheme = isDarkTheme,
                        surface = surface,
                        isFullSize = isFullSize,
                        animated = animated,
                        colorStage = { colorStage.value },
                        alpha = alpha,
                    ),
        )
        content()
    }
}
