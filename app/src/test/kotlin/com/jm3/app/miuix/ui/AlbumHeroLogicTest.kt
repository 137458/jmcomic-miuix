package com.jm3.app.miuix.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AlbumHeroLogicTest {

    private val listCover = HeroRect(left = 16f, top = 400f, width = 48f, height = 64f)
    private val detailHeader = HeroRect(left = 48f, top = 220f, width = 336f, height = 448f)

    @Test
    fun 插值零点等于起点() {
        assertEquals(listCover, lerpHeroRect(listCover, detailHeader, 0f))
    }

    @Test
    fun 插值一点等于终点() {
        assertEquals(detailHeader, lerpHeroRect(listCover, detailHeader, 1f))
    }

    @Test
    fun 插值中点是两端平均() {
        val mid = lerpHeroRect(listCover, detailHeader, 0.5f)
        assertEquals(32f, mid.left, 0.001f)
        assertEquals(310f, mid.top, 0.001f)
        assertEquals(192f, mid.width, 0.001f)
        assertEquals(256f, mid.height, 0.001f)
    }

    @Test
    fun 插值进度负值夹到起点() {
        assertEquals(listCover, lerpHeroRect(listCover, detailHeader, -0.4f))
    }

    @Test
    fun 插值进度超过一夹到终点() {
        assertEquals(detailHeader, lerpHeroRect(listCover, detailHeader, 1.8f))
    }

    @Test
    fun 圆角插值中点落在一半() {
        assertEquals(11f, lerpHeroCorner(6f, 16f, 0.5f), 0.001f)
        assertEquals(6f, lerpHeroCorner(6f, 16f, -1f))
        assertEquals(16f, lerpHeroCorner(6f, 16f, 2f))
    }

    @Test
    fun 正常矩形可用() {
        assertTrue(isFiniteHeroRect(listCover))
        assertTrue(isFiniteHeroRect(detailHeader))
    }

    @Test
    fun 零宽矩形不可用() {
        assertFalse(isFiniteHeroRect(HeroRect(10f, 10f, 0f, 64f)))
        assertFalse(isFiniteHeroRect(HeroRect(10f, 10f, 48f, 0f)))
    }

    @Test
    fun 负宽矩形不可用() {
        assertFalse(isFiniteHeroRect(HeroRect(10f, 10f, -48f, 64f)))
    }

    @Test
    fun 非有限值矩形不可用() {
        assertFalse(isFiniteHeroRect(HeroRect(Float.NaN, 10f, 48f, 64f)))
        assertFalse(isFiniteHeroRect(HeroRect(10f, Float.POSITIVE_INFINITY, 48f, 64f)))
    }

    @Test
    fun 缺少任一端就不飞() {
        assertFalse(shouldRunHero(null, detailHeader))
        assertFalse(shouldRunHero(listCover, null))
        assertFalse(shouldRunHero(null, null))
    }

    @Test
    fun 端点矩形无效时不飞() {
        assertFalse(shouldRunHero(listCover, HeroRect(48f, 220f, 0f, 448f)))
        assertFalse(shouldRunHero(HeroRect(16f, 400f, 48f, 64f).copy(width = Float.NaN), detailHeader))
    }

    @Test
    fun 中心位移一像素不飞() {
        val moved = detailHeader.copy(left = listCover.centerX - detailHeader.width / 2f + 1f, top = listCover.centerY - detailHeader.height / 2f)
        assertFalse(shouldRunHero(listCover, moved))
    }

    @Test
    fun 中心位移三像素要飞() {
        val moved = detailHeader.copy(left = listCover.centerX - detailHeader.width / 2f + 3f, top = listCover.centerY - detailHeader.height / 2f)
        assertTrue(shouldRunHero(listCover, moved))
    }

    @Test
    fun 同中心只变大小不飞() {
        val sameCenter = HeroRect(
            left = 100f - 40f,
            top = 300f - 40f,
            width = 80f,
            height = 80f,
        )
        val sameCenterLarger = HeroRect(
            left = 100f - 120f,
            top = 300f - 120f,
            width = 240f,
            height = 240f,
        )
        assertFalse(shouldRunHero(sameCenter, sameCenterLarger))
    }

    @Test
    fun 正向飞行列表封面到详情头图() {
        val plan = planForwardFlight(
            id = 7,
            pending = HeroPending("album-1", "https://example.com/a.jpg", listCover, 6f),
            headerRect = detailHeader,
            headerCornerDp = 16f,
        )
        assertEquals(
            HeroPlan(7, "album-1", "https://example.com/a.jpg", listCover, detailHeader, 6f, 16f),
            plan,
        )
    }

    @Test
    fun 正向飞行等不到头图矩形时不起飞() {
        val pending = HeroPending("album-2", null, listCover, 6f)
        assertNull(planForwardFlight(3, pending, null, 16f))
        assertNull(planForwardFlight(3, null, detailHeader, 16f))
    }

    @Test
    fun 正向飞行两端重合时不起飞() {
        val pending = HeroPending("album-3", null, detailHeader, 16f)
        assertNull(planForwardFlight(4, pending, detailHeader, 16f))
    }

    @Test
    fun 反向飞行详情头图回到列表封面() {
        val plan = planBackwardFlight(
            id = 9,
            albumId = "album-4",
            url = "https://example.com/b.jpg",
            headerRect = detailHeader,
            headerCornerDp = 16f,
            coverRect = listCover,
            coverCornerDp = 6f,
        )
        assertEquals(
            HeroPlan(9, "album-4", "https://example.com/b.jpg", detailHeader, listCover, 16f, 6f),
            plan,
        )
    }

    @Test
    fun 反向飞行缺列表卡片矩形时不起飞() {
        assertNull(
            planBackwardFlight(
                id = 9,
                albumId = "album-5",
                url = null,
                headerRect = detailHeader,
                headerCornerDp = 16f,
                coverRect = null,
                coverCornerDp = 6f,
            ),
        )
    }

    @Test
    fun 反向飞行头图矩形缺失时不起飞() {
        assertNull(
            planBackwardFlight(
                id = 10,
                albumId = "album-6",
                url = null,
                headerRect = null,
                headerCornerDp = 16f,
                coverRect = listCover,
                coverCornerDp = 6f,
            ),
        )
    }

    @Test
    fun 待飞超时足够长于飞行时长() {
        // 超时太短会在头图刚出现时放弃，太长会让列表封面长时间消失
        assertTrue(HERO_PENDING_TIMEOUT_MS > HERO_FLIGHT_DURATION_MS)
        assertTrue(HERO_PENDING_TIMEOUT_MS <= 3000L)
    }
}
