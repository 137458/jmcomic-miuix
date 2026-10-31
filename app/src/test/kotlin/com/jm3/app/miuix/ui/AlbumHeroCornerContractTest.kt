package com.jm3.app.miuix.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class AlbumHeroCornerContractTest {

    @Test
    fun testHeroCornerConstantsContract() {
        assertEquals(16f, HERO_CARD_CORNER_DP)
        assertEquals(HERO_CARD_CORNER_DP, MIUIX_CARD_CORNER_DP)
        assertEquals(8f, HERO_HISTORY_ROW_CORNER_DP)
        assertEquals(6f, HERO_FAVORITE_ROW_CORNER_DP)
    }

    @Test
    fun testDefaultCornerInModifierIsCardCorner() {
        assertEquals(16f, HERO_CARD_CORNER_DP)
    }
}
