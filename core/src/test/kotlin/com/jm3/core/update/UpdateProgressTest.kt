package com.jm3.core.update

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 下载进度的换算契约。
 *
 * 两份界面文案（弹窗主按钮、页面下载卡）与下载中状态本身都从
 * [UpdateProgress] 取数，所以边界只在这里定义一次：总长未知时比例为 0（界面改报已下字节），
 * 已下载越界时夹到满格，百分比一律向下取整——99.6% 不能显示成 100%，否则用户会以为已下完。
 */
class UpdateProgressTest {

    @Test
    fun 总长未知时不给比例() {
        assertEquals(0f, UpdateProgress.fraction(downloaded = 4_096, total = 0))
        assertEquals(0f, UpdateProgress.fraction(downloaded = 0, total = 0))
        assertEquals(0f, UpdateProgress.fraction(downloaded = 1_024, total = -1))
    }

    @Test
    fun 已下载越界时夹到满格() {
        assertEquals(1f, UpdateProgress.fraction(downloaded = 3, total = 2))
    }

    @Test
    fun 常规下载按字节比例换算() {
        assertEquals(0.5f, UpdateProgress.fraction(downloaded = 512, total = 1_024))
        assertEquals(1f, UpdateProgress.fraction(downloaded = 1_024, total = 1_024))
        assertEquals(0f, UpdateProgress.fraction(downloaded = 0, total = 1_024))
    }

    @Test
    fun 百分比向下取整不进位() {
        assertEquals(99, UpdateProgress.percent(0.996f))
        assertEquals(87, UpdateProgress.percent(0.874f))
        assertEquals(0, UpdateProgress.percent(0.004f))
    }

    @Test
    fun 百分比对越界比例夹紧() {
        assertEquals(100, UpdateProgress.percent(1.4f))
        assertEquals(0, UpdateProgress.percent(-0.2f))
    }
}
