package com.jm3.app.miuix.ui

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class AdaptiveLayoutTest {

    @Test
    fun 断点取整向下取整不越过600与840下限() {
        assertEquals(599, 599.6.dp.toLayoutDpInt())
        assertEquals(599, 599.9.dp.toLayoutDpInt())
        assertEquals(600, 600.dp.toLayoutDpInt())
        assertEquals(839, 839.99.dp.toLayoutDpInt())
        assertEquals(840, 840.dp.toLayoutDpInt())
    }

    @Test
    fun 负尺寸收敛到0() {
        assertEquals(0, (-1).dp.toLayoutDpInt())
        assertEquals(0, 0.dp.toLayoutDpInt())
    }
}
