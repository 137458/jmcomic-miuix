package com.jm3.app.miuix.ui

import com.jm3.app.miuix.ui.skin.skinTokensFor
import com.jm3.core.settings.UiSkin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import top.yukonga.miuix.kmp.basic.CardDefaults

class AlbumHeroCornerContractTest {

    @Test
    fun testHeroCornerConstantsContract() {
        // 常量只是 HyperOS 档与 Miuix Card 默认值的同源基准，运行期两端都取皮肤 cardCorner
        assertEquals(CardDefaults.CornerRadius.value, HERO_CARD_CORNER_DP)
        assertEquals(skinTokensFor(UiSkin.HYPEROS).shapes.cardCorner.value, HERO_CARD_CORNER_DP)
        // ColorOS 皮肤与其他卡片一致改为 18dp，飞行两端必须跟着皮肤取值才不会错位
        assertNotEquals(skinTokensFor(UiSkin.COLOROS).shapes.cardCorner.value, HERO_CARD_CORNER_DP)
        // 卡内缩略图圆角小于各皮肤卡片外圆角，否则小图会被裁成胶囊
        assertEquals(8f, HERO_THUMB_CORNER_DP)
        assertEquals(6f, HERO_BADGE_CORNER_DP)
    }

    @Test
    fun testThumbCornerIsSmallerThanCardCorner() {
        listOf(UiSkin.HYPEROS, UiSkin.COLOROS).forEach { skin ->
            val cardCorner = skinTokensFor(skin).shapes.cardCorner.value
            assertEquals(true, HERO_THUMB_CORNER_DP < cardCorner)
            assertEquals(true, HERO_BADGE_CORNER_DP <= HERO_THUMB_CORNER_DP)
        }
    }
}
