package com.jm3.app.miuix.ui.effect

import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 把流光背景画在节点背景层。
 *
 * 始终先画一层 surface（保证内容底部不透明），再按 [alpha] 叠着色器结果；
 * [alpha] 为 0 时停掉逐帧任务，滚动到头部不可见后不再耗电。
 */
fun Modifier.bgEffectDraw(
    painter: BgEffectPainter,
    preset: BgEffectConfig.Config,
    deviceType: DeviceType,
    isDarkTheme: Boolean,
    surface: Color,
    isFullSize: Boolean,
    animated: Boolean,
    colorStage: () -> Float,
    alpha: () -> Float,
): Modifier =
    this then
        BgEffectElement(
            painter = painter,
            preset = preset,
            deviceType = deviceType,
            isDarkTheme = isDarkTheme,
            surface = surface,
            isFullSize = isFullSize,
            animated = animated,
            colorStage = colorStage,
            alpha = alpha,
        )

private data class BgEffectElement(
    val painter: BgEffectPainter,
    val preset: BgEffectConfig.Config,
    val deviceType: DeviceType,
    val isDarkTheme: Boolean,
    val surface: Color,
    val isFullSize: Boolean,
    val animated: Boolean,
    val colorStage: () -> Float,
    val alpha: () -> Float,
) : ModifierNodeElement<BgEffectNode>() {
    override fun create(): BgEffectNode =
        BgEffectNode(
            painter = painter,
            preset = preset,
            deviceType = deviceType,
            isDarkTheme = isDarkTheme,
            surface = surface,
            isFullSize = isFullSize,
            animated = animated,
            colorStage = colorStage,
            alpha = alpha,
        )

    override fun update(node: BgEffectNode) {
        node.update(
            painter = painter,
            preset = preset,
            deviceType = deviceType,
            isDarkTheme = isDarkTheme,
            surface = surface,
            isFullSize = isFullSize,
            animated = animated,
            colorStage = colorStage,
            alpha = alpha,
        )
    }
}

private class BgEffectNode(
    private var painter: BgEffectPainter,
    private var preset: BgEffectConfig.Config,
    private var deviceType: DeviceType,
    private var isDarkTheme: Boolean,
    private var surface: Color,
    private var isFullSize: Boolean,
    private var animated: Boolean,
    private var colorStage: () -> Float,
    private var alpha: () -> Float,
) : Modifier.Node(),
    DrawModifierNode {
    private var animationJob: Job? = null
    private var animTime: Float = 0f
    private var startOffset: Float = 0f

    override fun onAttach() {
        if (animated) startAnimation()
    }

    override fun onDetach() {
        animationJob?.cancel()
        animationJob = null
    }

    fun update(
        painter: BgEffectPainter,
        preset: BgEffectConfig.Config,
        deviceType: DeviceType,
        isDarkTheme: Boolean,
        surface: Color,
        isFullSize: Boolean,
        animated: Boolean,
        colorStage: () -> Float,
        alpha: () -> Float,
    ) {
        this.painter = painter
        this.preset = preset
        this.deviceType = deviceType
        this.isDarkTheme = isDarkTheme
        this.surface = surface
        this.isFullSize = isFullSize
        this.colorStage = colorStage
        this.alpha = alpha

        if (this.animated != animated) {
            this.animated = animated
            if (animated) {
                startAnimation()
            } else {
                animationJob?.cancel()
                animationJob = null
            }
        }
        invalidateDraw()
    }

    private fun startAnimation() {
        animationJob?.cancel()
        startOffset = animTime
        animationJob =
            coroutineScope.launch {
                val origin = withFrameNanos { it }
                while (isActive) {
                    val now = withFrameNanos { it }
                    animTime = startOffset + (now - origin) / 1_000_000_000f
                    invalidateDraw()
                }
            }
    }

    override fun ContentDrawScope.draw() {
        drawRect(surface)
        if (size.width > 0f && size.height > 0f) {
            val alphaValue = alpha()
            if (alphaValue <= 0f) {
                animationJob?.cancel()
                animationJob = null
            } else if (animated && animationJob == null) {
                startAnimation()
            }
            if (alphaValue > 0f) {
                val drawHeight = if (isFullSize) size.height * 0.8f else size.height * 0.5f

                painter.updateResolution(size.width, size.height)
                painter.updateBoundIfNeeded(drawHeight, size.height, size.width)
                painter.updatePresetIfNeeded(deviceType, isDarkTheme)
                painter.updateColors(preset, colorStage())
                painter.updateAnimTime(animTime)
                painter.updatePointsAnim(animTime, preset)

                drawRect(painter.brush, alpha = alphaValue)
            }
        }
        drawContent()
    }
}
