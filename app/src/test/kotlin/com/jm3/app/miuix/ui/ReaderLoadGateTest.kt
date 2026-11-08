package com.jm3.app.miuix.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderLoadGateTest {
    @Test
    fun `重新开始后旧代际不再是最新`() {
        val gate = ReaderLoadGate()
        val first = gate.start()

        assertTrue(gate.isLatest(first))
        val second = gate.start()
        assertFalse(gate.isLatest(first))
        assertTrue(gate.isLatest(second))
    }

    @Test
    fun `从未开始的代际不是最新`() {
        val gate = ReaderLoadGate()

        assertFalse(gate.isLatest(1))
        assertEquals(1, gate.start())
    }
}
