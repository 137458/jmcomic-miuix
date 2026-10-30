package com.jm3.app.miuix.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AlbumHeroStateMachineTest {

    private val listCover = HeroRect(left = 16f, top = 400f, width = 48f, height = 64f)
    private val detailHeader = HeroRect(left = 48f, top = 220f, width = 336f, height = 448f)

    @Test
    fun 启动正向飞行后pending非空且origin已记录() {
        val initial = HeroState()
        val next = initial.startForward(id = 1, albumId = "a1", url = "https://cover", from = listCover, cornerDp = 6f)
        val pending = next.pending
        assertNotNull(pending)
        assertEquals("a1", pending.albumId)
        assertEquals("https://cover", pending.url)
        assertEquals(listCover, pending.from)
        assertEquals(6f, pending.cornerFromDp)
        assertTrue("a1" in next.heroOrigins)
    }

    @Test
    fun 矩形非法时启动正向飞行状态不变() {
        val initial = HeroState()
        val bad = HeroRect(left = 0f, top = 0f, width = 0f, height = 0f)
        val next = initial.startForward(id = 1, albumId = "a1", url = "https://cover", from = bad, cornerDp = 6f)
        assertEquals(initial, next)
        assertNull(next.pending)
        assertTrue(next.heroOrigins.isEmpty())
    }

    @Test
    fun 头图矩形缺失时resolveForward清空pending且flight为空() {
        val initial = HeroState().startForward(id = 1, albumId = "a1", url = "https://cover", from = listCover, cornerDp = 6f)
        val next = initial.resolveForward(headerRect = null, headerCornerDp = 16f)
        assertNull(next.pending)
        assertNull(next.flight)
        assertTrue("a1" in next.heroOrigins)
    }

    @Test
    fun 头图矩形有效时resolveForward清空pending且flight端点正确() {
        val initial = HeroState().startForward(id = 1, albumId = "a1", url = "https://cover", from = listCover, cornerDp = 6f)
        val next = initial.resolveForward(headerRect = detailHeader, headerCornerDp = 16f)
        assertNull(next.pending)
        val flight = next.flight
        assertNotNull(flight)
        assertEquals("a1", flight.albumId)
        assertEquals(listCover, flight.from)
        assertEquals(detailHeader, flight.to)
        assertEquals(6f, flight.cornerFromDp)
        assertEquals(16f, flight.cornerToDp)
    }

    @Test
    fun 两端重合时resolveForward不产生flight且pending仍清空() {
        val initial = HeroState().startForward(id = 1, albumId = "a1", url = "https://cover", from = listCover, cornerDp = 6f)
        val next = initial.resolveForward(headerRect = listCover, headerCornerDp = 6f)
        assertNull(next.pending)
        assertNull(next.flight)
    }

    @Test
    fun cancelPending清空pending且保留origin() {
        val initial = HeroState().startForward(id = 1, albumId = "a1", url = "https://cover", from = listCover, cornerDp = 6f)
        val next = initial.cancelPending()
        assertNull(next.pending)
        assertTrue("a1" in next.heroOrigins)
    }

    @Test
    fun origin不含该详情时startBackward不产生飞行且清空pending() {
        val initial = HeroState(heroOrigins = setOf("other"))
            .startForward(id = 1, albumId = "other", url = "https://cover", from = listCover, cornerDp = 6f)
        val next = initial.startBackward(
            id = 2,
            albumId = "not_in_origin",
            url = "https://cover2",
            headerRect = detailHeader,
            headerCornerDp = 16f,
            coverRect = listCover,
            coverCornerDp = 6f,
        )
        assertNull(next.flight)
        assertNull(next.pending)
        assertFalse("not_in_origin" in next.heroOrigins)
        assertTrue("other" in next.heroOrigins)
    }

    @Test
    fun origin含该详情且两端矩形齐全则产生反向飞行并移除origin() {
        val initial = HeroState(heroOrigins = setOf("a1"))
        val next = initial.startBackward(
            id = 2,
            albumId = "a1",
            url = "https://cover",
            headerRect = detailHeader,
            headerCornerDp = 16f,
            coverRect = listCover,
            coverCornerDp = 6f,
        )
        val flight = next.flight
        assertNotNull(flight)
        assertEquals(2, flight.id)
        assertEquals("a1", flight.albumId)
        assertEquals(detailHeader, flight.from)
        assertEquals(listCover, flight.to)
        assertEquals(16f, flight.cornerFromDp)
        assertEquals(6f, flight.cornerToDp)
        assertNull(next.pending)
        assertFalse("a1" in next.heroOrigins)
    }

    @Test
    fun origin含该详情但矩形缺失则不飞行且移除origin并清空pending() {
        val initial = HeroState(heroOrigins = setOf("a1"))
            .startForward(id = 1, albumId = "a1", url = "https://cover", from = listCover, cornerDp = 6f)
        val next = initial.startBackward(
            id = 2,
            albumId = "a1",
            url = "https://cover",
            headerRect = null,
            headerCornerDp = 16f,
            coverRect = listCover,
            coverCornerDp = 6f,
        )
        assertNull(next.flight)
        assertNull(next.pending)
        assertFalse("a1" in next.heroOrigins)
    }

    @Test
    fun finishFlight命中id则flight清空() {
        val initial = HeroState(
            flight = HeroPlan(
                id = 42,
                albumId = "a1",
                url = "https://cover",
                from = listCover,
                to = detailHeader,
                cornerFromDp = 6f,
                cornerToDp = 16f,
            )
        )
        val next = initial.finishFlight(42)
        assertNull(next.flight)
    }

    @Test
    fun finishFlight未命中id则flight保留() {
        val plan = HeroPlan(
            id = 42,
            albumId = "a1",
            url = "https://cover",
            from = listCover,
            to = detailHeader,
            cornerFromDp = 6f,
            cornerToDp = 16f,
        )
        val initial = HeroState(flight = plan)
        val next = initial.finishFlight(999)
        assertEquals(plan, next.flight)
    }

    @Test
    fun pending已被清掉后再resolveForward状态不变() {
        val initial = HeroState()
        val next = initial.resolveForward(headerRect = detailHeader, headerCornerDp = 16f)
        assertEquals(initial, next)
    }
}
