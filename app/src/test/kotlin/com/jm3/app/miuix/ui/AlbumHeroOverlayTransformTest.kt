package com.jm3.app.miuix.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 飞行覆盖层的绘制期变换：以落点矩形为固定尺寸，用图层缩放与位移复现逐帧插值矩形。
 * 两端同为 3:4（列表封面与详情头图都是 144x192 的比例），等比缩放才能同时命中宽高。
 */
class AlbumHeroOverlayTransformTest {

    private val listCover = HeroRect(left = 16f, top = 400f, width = 48f, height = 64f)
    private val detailHeader = HeroRect(left = 48f, top = 220f, width = 336f, height = 448f)

    @Test
    fun 进度零落回起点矩形() {
        val transform = heroOverlayTransform(listCover, detailHeader, 0f)

        // 落点 + 位移与缩放必须正好等于列表封面
        assertEquals(listCover.left, detailHeader.left + transform.translationX, 0.01f)
        assertEquals(listCover.top, detailHeader.top + transform.translationY, 0.01f)
        assertEquals(listCover.width, detailHeader.width * transform.scale, 0.01f)
        assertEquals(listCover.height, detailHeader.height * transform.scale, 0.01f)
    }

    @Test
    fun 进度一回到落点且不缩放() {
        val transform = heroOverlayTransform(listCover, detailHeader, 1f)

        assertEquals(1f, transform.scale, 0.0001f)
        assertEquals(0f, transform.translationX, 0.0001f)
        assertEquals(0f, transform.translationY, 0.0001f)
    }

    @Test
    fun 中间帧与矩形插值一致() {
        val mid = lerpHeroRect(listCover, detailHeader, 0.5f)
        val transform = heroOverlayTransform(listCover, detailHeader, 0.5f)

        assertEquals(mid.left, detailHeader.left + transform.translationX, 0.01f)
        assertEquals(mid.top, detailHeader.top + transform.translationY, 0.01f)
        assertEquals(mid.width, detailHeader.width * transform.scale, 0.01f)
        assertEquals(mid.height, detailHeader.height * transform.scale, 0.01f)
    }

    @Test
    fun 进度越界按矩形插值夹取() {
        val atStart = heroOverlayTransform(listCover, detailHeader, -0.4f)
        val atEnd = heroOverlayTransform(listCover, detailHeader, 1.8f)

        assertEquals(heroOverlayTransform(listCover, detailHeader, 0f), atStart)
        assertEquals(1f, atEnd.scale, 0.0001f)
        assertEquals(0f, atEnd.translationX, 0.0001f)
    }

    @Test
    fun 宽高比不同时宽度与落点仍精确() {
        // 差异数据：竖长列表卡 → 宽扁目标，用于确认缩放取自宽度，位移仍以落点为基准
        val tall = HeroRect(left = 0f, top = 0f, width = 30f, height = 90f)
        val wide = HeroRect(left = 100f, top = 50f, width = 60f, height = 120f)
        val transform = heroOverlayTransform(tall, wide, 0f)

        assertEquals(0.5f, transform.scale, 0.0001f)
        assertEquals(tall.left, wide.left + transform.translationX, 0.01f)
        assertEquals(tall.top, wide.top + transform.translationY, 0.01f)
        assertEquals(tall.width, wide.width * transform.scale, 0.01f)
        // 等比缩放：高度不再等于起点的 90，这是两端比例不一致时的已知取舍
        assertTrue(wide.height * transform.scale < tall.height)
    }

    @Test
    fun 落点零宽时不做除法() {
        val transform = heroOverlayTransform(listCover, HeroRect(0f, 0f, 0f, 0f), 0f)

        assertEquals(1f, transform.scale)
    }
}
