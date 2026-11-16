// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.page

import com.jm3.core.model.Jm3User
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 我的页账号摘要：等级、硬币与经验压在一行。
 *
 * 等级称号是可选字段，空白或无称号时只显示等级数值，不能留下「Lv.5  · 硬币」这种双空格；
 * 经验按「当前/下级」原样给出，不在这里做百分比换算。
 */
class CouiMineScreenTest {

    private fun user(
        level: Int = 5,
        levelName: String? = "萌新",
        coin: Int = 120,
        currentExp: Long = 30L,
        nextLevelExp: Long = 100L,
    ): Jm3User = Jm3User(
        uid = "uid-1",
        username = "鲸娘",
        email = null,
        emailVerified = false,
        photoUrl = null,
        coin = coin,
        albumFavorites = 0,
        maxAlbumFavorites = 0,
        level = level,
        levelName = levelName,
        nextLevelExp = nextLevelExp,
        currentExp = currentExp,
        expPercent = 30,
    )

    @Test
    fun 有称号时等级后接称号() {
        assertEquals("Lv.5 萌新 · 硬币 120 · 经验 30/100", mineAccountSummary(user()))
    }

    @Test
    fun 称号为空白时只显示等级() {
        assertEquals("Lv.5 · 硬币 120 · 经验 30/100", mineAccountSummary(user(levelName = "   ")))
    }

    @Test
    fun 称号缺省时只显示等级() {
        assertEquals("Lv.5 · 硬币 120 · 经验 30/100", mineAccountSummary(user(levelName = null)))
    }

    @Test
    fun 称号两侧空白在拼接前被去掉() {
        assertEquals(
            "Lv.8 大佬 · 硬币 0 · 经验 0/0",
            mineAccountSummary(user(level = 8, levelName = " 大佬 ", coin = 0, currentExp = 0L, nextLevelExp = 0L)),
        )
    }

    @Test
    fun 一级零硬币零经验同样完整拼出三段() {
        assertEquals(
            "Lv.1 · 硬币 0 · 经验 0/0",
            mineAccountSummary(user(level = 1, levelName = null, coin = 0, currentExp = 0L, nextLevelExp = 0L)),
        )
    }
}
