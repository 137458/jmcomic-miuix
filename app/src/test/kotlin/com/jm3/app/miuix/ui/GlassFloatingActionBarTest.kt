package com.jm3.app.miuix.ui

import com.jm3.app.miuix.ui.glass.FloatingBottomBarMode
import com.jm3.app.miuix.ui.glass.glassFabCapsuleWidthDp
import com.jm3.app.miuix.ui.glass.glassFabItemSizeDp
import com.jm3.app.miuix.ui.glass.resolveFloatingBottomBarMode
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 右下角操作胶囊的布局与降级契约：
 * 宽度必须等于「左右内边距 + 按钮边长 + 间距」的显式算式（胶囊不随内容偷偷变形），
 * 单枚按钮点击区不得低于 44dp，三态降级不得在缺少采样源时仍走玻璃。
 */
class GlassFloatingActionBarTest {

    @Test
    fun 没有动作时胶囊不占宽度() {
        assertEquals(
            0f,
            glassFabCapsuleWidthDp(itemCount = 0, itemSizeDp = 44f, gapDp = 4f, horizontalPaddingDp = 4f),
        )
        // 负数按空列表处理，不产生负宽度
        assertEquals(
            0f,
            glassFabCapsuleWidthDp(itemCount = -2, itemSizeDp = 44f, gapDp = 4f, horizontalPaddingDp = 4f),
        )
    }

    @Test
    fun 单枚动作的胶囊只含内边距与一枚按钮() {
        assertEquals(
            52f,
            glassFabCapsuleWidthDp(itemCount = 1, itemSizeDp = 44f, gapDp = 4f, horizontalPaddingDp = 4f),
        )
        // 换一组尺寸：内边距 6、按钮 48、无间距时宽度 = 12 + 48
        assertEquals(
            60f,
            glassFabCapsuleWidthDp(itemCount = 1, itemSizeDp = 48f, gapDp = 0f, horizontalPaddingDp = 6f),
        )
    }

    @Test
    fun 两枚动作的胶囊叠加一次间距() {
        assertEquals(
            100f,
            glassFabCapsuleWidthDp(itemCount = 2, itemSizeDp = 44f, gapDp = 4f, horizontalPaddingDp = 4f),
        )
        // 第二枚只比第一枚多出「按钮边长 + 间距」= 44 + 4 = 48
        assertEquals(
            48f,
            glassFabCapsuleWidthDp(itemCount = 2, itemSizeDp = 44f, gapDp = 4f, horizontalPaddingDp = 4f) -
                glassFabCapsuleWidthDp(itemCount = 1, itemSizeDp = 44f, gapDp = 4f, horizontalPaddingDp = 4f),
        )
        // 换一组尺寸：内边距 8、按钮 44、间距 6 → 16 + 88 + 6
        assertEquals(
            110f,
            glassFabCapsuleWidthDp(itemCount = 2, itemSizeDp = 44f, gapDp = 6f, horizontalPaddingDp = 8f),
        )
    }

    @Test
    fun 按钮边长铺满胶囊高度减去上下内边距() {
        assertEquals(44f, glassFabItemSizeDp(capsuleHeightDp = 52f, verticalPaddingDp = 4f))
        assertEquals(48f, glassFabItemSizeDp(capsuleHeightDp = 56f, verticalPaddingDp = 4f))
        assertEquals(52f, glassFabItemSizeDp(capsuleHeightDp = 64f, verticalPaddingDp = 6f))
    }

    @Test
    fun 胶囊压得太矮时按钮边长不低于无障碍下限() {
        // 48 - 8 = 40，低于 44 的下限
        assertEquals(44f, glassFabItemSizeDp(capsuleHeightDp = 48f, verticalPaddingDp = 4f))
        // 40 - 8 = 32，同样被抬到下限
        assertEquals(44f, glassFabItemSizeDp(capsuleHeightDp = 40f, verticalPaddingDp = 4f))
    }

    @Test
    fun 液态玻璃要求采样源与着色器同时可用() {
        assertEquals(
            FloatingBottomBarMode.LiquidGlass,
            resolveFloatingBottomBarMode(FloatingBottomBarMode.LiquidGlass, hasBackdrop = true, shaderSupported = true),
        )
        // 有采样源但没有 RuntimeShader：退回实色，不降到模糊（与底栏判定一致）
        assertEquals(
            FloatingBottomBarMode.None,
            resolveFloatingBottomBarMode(FloatingBottomBarMode.LiquidGlass, hasBackdrop = true, shaderSupported = false),
        )
        assertEquals(
            FloatingBottomBarMode.None,
            resolveFloatingBottomBarMode(FloatingBottomBarMode.LiquidGlass, hasBackdrop = false, shaderSupported = true),
        )
        assertEquals(
            FloatingBottomBarMode.None,
            resolveFloatingBottomBarMode(FloatingBottomBarMode.LiquidGlass, hasBackdrop = false, shaderSupported = false),
        )
    }

    @Test
    fun 模糊降级只要求采样源() {
        assertEquals(
            FloatingBottomBarMode.Blur,
            resolveFloatingBottomBarMode(FloatingBottomBarMode.Blur, hasBackdrop = true, shaderSupported = true),
        )
        assertEquals(
            FloatingBottomBarMode.Blur,
            resolveFloatingBottomBarMode(FloatingBottomBarMode.Blur, hasBackdrop = true, shaderSupported = false),
        )
        assertEquals(
            FloatingBottomBarMode.None,
            resolveFloatingBottomBarMode(FloatingBottomBarMode.Blur, hasBackdrop = false, shaderSupported = true),
        )
        assertEquals(
            FloatingBottomBarMode.None,
            resolveFloatingBottomBarMode(FloatingBottomBarMode.Blur, hasBackdrop = false, shaderSupported = false),
        )
    }

    @Test
    fun 请求实色时不会被升级成玻璃() {
        assertEquals(
            FloatingBottomBarMode.None,
            resolveFloatingBottomBarMode(FloatingBottomBarMode.None, hasBackdrop = true, shaderSupported = true),
        )
        assertEquals(
            FloatingBottomBarMode.None,
            resolveFloatingBottomBarMode(FloatingBottomBarMode.None, hasBackdrop = true, shaderSupported = false),
        )
        assertEquals(
            FloatingBottomBarMode.None,
            resolveFloatingBottomBarMode(FloatingBottomBarMode.None, hasBackdrop = false, shaderSupported = false),
        )
    }
}
