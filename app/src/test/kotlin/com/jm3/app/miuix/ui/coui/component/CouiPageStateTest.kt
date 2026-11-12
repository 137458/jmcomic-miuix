// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.component

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * 分组行圆角形状契约：首尾行仅一端保留超椭圆圆角、中间行退化为直角矩形，
 * 半径为零或零尺寸时不能构造异常路径。
 */
class CouiPageStateTest {

    private val density = Density(2f)

    @Test
    fun 上下都不圆角时返回精确矩形轮廓() {
        val shape = CouiRowShape(
            radius = 20.dp,
            roundTop = false,
            roundBottom = false,
            cornerWeight = 2.6f,
        )

        val outline = shape.createOutline(
            size = Size(200f, 80f),
            layoutDirection = LayoutDirection.Ltr,
            density = density,
        )

        val rect = assertIs<Outline.Rectangle>(outline)
        assertEquals(Rect(0f, 0f, 200f, 80f), rect.rect)
    }

    @Test
    fun 半径为零或尺寸为零时退化为矩形() {
        val zeroRadius = CouiRowShape(
            radius = 0.dp,
            roundTop = true,
            roundBottom = true,
            cornerWeight = 2.6f,
        ).createOutline(Size(120f, 60f), LayoutDirection.Ltr, density)
        assertIs<Outline.Rectangle>(zeroRadius)

        val zeroSize = CouiRowShape(
            radius = 16.dp,
            roundTop = true,
            roundBottom = false,
            cornerWeight = 2.6f,
        ).createOutline(Size(0f, 60f), LayoutDirection.Ltr, density)
        val zeroRect = assertIs<Outline.Rectangle>(zeroSize)
        assertEquals(Rect(0f, 0f, 0f, 60f), zeroRect.rect)
    }

    @Test
    fun 仅顶部或仅底部圆角时返回通用路径且超限半径不崩溃() {
        val topRounded = CouiRowShape(
            radius = 16.dp,
            roundTop = true,
            roundBottom = false,
            cornerWeight = 2.6f,
        ).createOutline(Size(200f, 80f), LayoutDirection.Ltr, density)
        assertIs<Outline.Generic>(topRounded)

        val bottomRounded = CouiRowShape(
            radius = 16.dp,
            roundTop = false,
            roundBottom = true,
            cornerWeight = 2.6f,
        ).createOutline(Size(200f, 80f), LayoutDirection.Ltr, density)
        assertIs<Outline.Generic>(bottomRounded)

        val oversized = CouiRowShape(
            radius = 999.dp,
            roundTop = true,
            roundBottom = true,
            cornerWeight = 2.6f,
        ).createOutline(Size(40f, 20f), LayoutDirection.Ltr, density)
        assertIs<Outline.Generic>(oversized)
    }
}
