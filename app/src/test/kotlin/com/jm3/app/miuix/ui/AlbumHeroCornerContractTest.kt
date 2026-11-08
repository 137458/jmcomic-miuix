package com.jm3.app.miuix.ui

import com.jm3.app.miuix.ui.skin.skinTokensFor
import com.jm3.core.settings.UiSkin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
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
    fun 旧的固定卡片圆角常量入口已删除() {
        // 运行期只允许从 LocalSkin.current.shapes.cardCorner 取角；保留公开常量会给回退留后门
        val oldGetter = runCatching {
            Class.forName("com.jm3.app.miuix.ui.AlbumHeroKt")
                .getDeclaredMethod("getHERO_CARD_CORNER_DP")
        }.getOrNull()
        assertNull(oldGetter)
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
