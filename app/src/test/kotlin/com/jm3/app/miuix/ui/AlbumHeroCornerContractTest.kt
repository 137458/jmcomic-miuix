package com.jm3.app.miuix.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import top.yukonga.miuix.kmp.basic.CardDefaults

class AlbumHeroCornerContractTest {

    @Test
    fun testHeroCornerConstantsContract() {
        // 卡片外圆角与 Miuix Card 默认值同源，飞行两端才不会错位
        assertEquals(CardDefaults.CornerRadius.value, HERO_CARD_CORNER_DP)
        assertEquals(16f, HERO_CARD_CORNER_DP)
        // 卡内缩略图圆角小于卡片外圆角，否则小图会被裁成胶囊
        assertEquals(8f, HERO_THUMB_CORNER_DP)
        assertEquals(6f, HERO_BADGE_CORNER_DP)
    }

    @Test
    fun testThumbCornerIsSmallerThanCardCorner() {
        assertEquals(true, HERO_THUMB_CORNER_DP < HERO_CARD_CORNER_DP)
        assertEquals(true, HERO_BADGE_CORNER_DP <= HERO_THUMB_CORNER_DP)
    }
}
