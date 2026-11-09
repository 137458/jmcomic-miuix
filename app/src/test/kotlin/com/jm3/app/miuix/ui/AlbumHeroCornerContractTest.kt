package com.jm3.app.miuix.ui

import com.jm3.app.miuix.ui.skin.skinTokensFor
import com.jm3.core.settings.UiSkin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import top.yukonga.miuix.kmp.basic.CardDefaults

class AlbumHeroCornerContractTest {

    @Test
    fun 卡片圆角只由皮肤提供且HyperOS与库默认值同源() {
        val hyperOs = skinTokensFor(UiSkin.HYPEROS).shapes.cardCorner.value
        val colorOs = skinTokensFor(UiSkin.COLOROS).shapes.cardCorner.value
        assertEquals(CardDefaults.CornerRadius.value, hyperOs)
        assertNotEquals(hyperOs, colorOs)
        // ColorOS 更圆；飞行两端必须跟着皮肤取值，不能有任何一端回退到 16dp
        assertTrue(colorOs > hyperOs)
    }

    @Test
    fun 详情页圆角只从皮肤契约取且小缩略图小一档() {
        // 头图与卡片走 albumCardCorner(cardCorner)，预览条小缩略图走 albumThumbCorner(controlCorner)，
        // 两者都只读 LocalSkin.current.shapes。这里钉住两套皮肤的真实取值：实现里改回任何字面量
        // （例如写死 16dp 或 8dp）时，ColorOS 那一行必然红灯，而不是靠"某个符号不存在"来证明。
        assertEquals(16f, albumCardCorner(skinTokensFor(UiSkin.HYPEROS).shapes).value)
        assertEquals(18f, albumCardCorner(skinTokensFor(UiSkin.COLOROS).shapes).value)
        assertEquals(12f, albumThumbCorner(skinTokensFor(UiSkin.HYPEROS).shapes).value)
        assertEquals(14f, albumThumbCorner(skinTokensFor(UiSkin.COLOROS).shapes).value)
        listOf(UiSkin.HYPEROS, UiSkin.COLOROS).forEach { skin ->
            val shapes = skinTokensFor(skin).shapes
            // 小缩略图必须比卡片小一档：按卡片圆角切 112x150 的图会切掉画面四角
            assertTrue(albumThumbCorner(shapes) < albumCardCorner(shapes))
        }
    }

    @Test
    fun 缩略图与角标圆角始终小于各皮肤卡片外圆角() {
        assertEquals(8f, HERO_THUMB_CORNER_DP)
        assertEquals(6f, HERO_BADGE_CORNER_DP)
        listOf(UiSkin.HYPEROS, UiSkin.COLOROS).forEach { skin ->
            val cardCorner = skinTokensFor(skin).shapes.cardCorner.value
            assertTrue(HERO_THUMB_CORNER_DP < cardCorner)
            assertTrue(HERO_BADGE_CORNER_DP <= HERO_THUMB_CORNER_DP)
        }
    }
}
