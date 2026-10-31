package com.jm3.app.miuix.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 封面解码尺寸契约：列表卡片、飞行覆盖层、详情头图三端共用同一个固定尺寸，
 * 才能让 Coil 内存缓存里的同一张 Bitmap 在三端互相复用。
 */
class AlbumHeroCoverSizeTest {

    @Test
    fun 封面常量与详情头图尺寸对齐() {
        assertEquals(144f, HERO_COVER_WIDTH_DP)
        assertEquals(192f, HERO_COVER_HEIGHT_DP)
    }

    @Test
    fun 常规密度下解码尺寸保持三比四宽高比() {
        val hdpi = heroCoverSizePx(2.0f)
        assertEquals(288, hdpi.width)
        assertEquals(384, hdpi.height)
        assertEquals(4 * hdpi.width, 3 * hdpi.height)

        val xxhdpi = heroCoverSizePx(3.0f)
        assertEquals(432, xxhdpi.width)
        assertEquals(576, xxhdpi.height)
        assertEquals(4 * xxhdpi.width, 3 * xxhdpi.height)
    }

    @Test
    fun 解码尺寸按密度换算并四舍五入() {
        val single = heroCoverSizePx(1.0f)
        assertEquals(144, single.width)
        assertEquals(192, single.height)

        // 2.75（440dpi 屏）：144*2.75=396、192*2.75=528，换算后仍是整数
        val oddDensity = heroCoverSizePx(2.75f)
        assertEquals(396, oddDensity.width)
        assertEquals(528, oddDensity.height)

        // 2.7：144*2.7=388.8 进位到 389，192*2.7=518.4 舍到 518
        val rounded = heroCoverSizePx(2.7f)
        assertEquals(389, rounded.width)
        assertEquals(518, rounded.height)
    }

    @Test
    fun 极端小密度下解码尺寸不小于一像素() {
        val tiny = heroCoverSizePx(0.001f)
        assertTrue(tiny.width >= 1)
        assertTrue(tiny.height >= 1)
    }
}
