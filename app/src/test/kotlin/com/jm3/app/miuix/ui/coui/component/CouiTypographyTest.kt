// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.component

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 大屏字阶缩放契约：仅当 largeTypography 为 true 时把已指定的字号与行高同比放大，
 * 未指定的 TextUnit.Unspecified 保持原样不误算。
 */
class CouiTypographyTest {

    @Test
    fun 非大屏档位保持字号与行高不变() {
        val base = TextStyle(fontSize = 15.sp, lineHeight = 20.sp)
        assertEquals(base, scaleCouiTextStyle(base, largeTypography = false))
    }

    @Test
    fun 大屏档位按比例放大已指定字号并保留未指定行高() {
        val withLineHeight = TextStyle(fontSize = 15.sp, lineHeight = 20.sp)
        val scaled = scaleCouiTextStyle(withLineHeight, largeTypography = true)
        assertEquals(15f * 1.12f, scaled.fontSize.value, 0.001f)
        assertEquals(20f * 1.12f, scaled.lineHeight.value, 0.001f)

        val withoutLineHeight = TextStyle(fontSize = 18.sp, lineHeight = TextUnit.Unspecified)
        val scaledNoLineHeight = scaleCouiTextStyle(withoutLineHeight, largeTypography = true)
        assertEquals(18f * 1.12f, scaledNoLineHeight.fontSize.value, 0.001f)
        assertEquals(TextUnit.Unspecified, scaledNoLineHeight.lineHeight)
    }
}
