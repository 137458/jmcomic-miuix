package com.jm3.app.miuix.ui.glass

/**
 * 玻璃表面实际画到哪一级。
 *
 * 从高到低：液态玻璃（双次采样 + 折射 + 色散）、模糊（只要采样源）、实色（只要底色）。
 * 每一级需要什么能力写在 [resolveGlassTier] 里，表面自己不再各判一遍。
 */
internal enum class GlassTier {
    LiquidGlass,
    Blur,
    Solid,
}

/** 这一级是否要采样源（实色不需要）。 */
internal val GlassTier.drawsBackdrop: Boolean
    get() = this != GlassTier.Solid

/**
 * 把「请求的形态 + 设备能力」判成实际画的那一级。
 *
 * - 没有采样源：只能实色。
 * - 请求液态玻璃：采样源与着色器都要有，缺着色器退实色（不降到模糊，保持底栏既有观感）。
 * - 请求模糊：有采样源即可。
 *
 * @param hasBackdrop 是否拿到了采样源（[FloatingBottomBar] 的 backdrop、GlassScaffold 的 LocalGlassBackdrop）
 * @param shaderSupported 是否有运行时着色器
 * @param requested 调用方希望的形态：默认液态玻璃，降级由本函数决定
 */
internal fun resolveGlassTier(
    hasBackdrop: Boolean,
    shaderSupported: Boolean,
    requested: FloatingBottomBarMode = FloatingBottomBarMode.LiquidGlass,
): GlassTier = when {
    !hasBackdrop -> GlassTier.Solid
    requested == FloatingBottomBarMode.Blur -> GlassTier.Blur
    requested == FloatingBottomBarMode.LiquidGlass && shaderSupported -> GlassTier.LiquidGlass
    else -> GlassTier.Solid
}

/**
 * 未显式指定形态时的默认值：能力够就请求液态玻璃，否则请求模糊。
 *
 * 真正能画出哪一级仍由 [resolveGlassTier] 决定（没有采样源时模糊同样会退成实色）。
 */
internal fun defaultFloatingBottomBarMode(
    hasBackdrop: Boolean,
    shaderSupported: Boolean,
): FloatingBottomBarMode =
    if (hasBackdrop && shaderSupported) FloatingBottomBarMode.LiquidGlass else FloatingBottomBarMode.Blur
