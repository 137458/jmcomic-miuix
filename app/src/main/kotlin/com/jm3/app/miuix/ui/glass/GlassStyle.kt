package com.jm3.app.miuix.ui.glass

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 玻璃材质风格：由皮肤决定，不改变降级三档，只改变液态玻璃档的配方。
 *
 * - [Liquid]：HyperOS 液态玻璃，细磨砂 + 折射透镜 + 饱和高光；
 * - [Frosted]：ColorOS 均匀磨砂，显式关闭折射与 vibrancy，底衬走材质混色。
 */
internal enum class GlassStyle {
    Liquid,
    Frosted,
}

/** 当前皮肤下玻璃材质应走的配方；默认液态玻璃，与旧行为一致。 */
internal val LocalGlassStyle = staticCompositionLocalOf { GlassStyle.Liquid }
