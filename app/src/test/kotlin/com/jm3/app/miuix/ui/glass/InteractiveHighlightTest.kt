package com.jm3.app.miuix.ui.glass

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InteractiveHighlightTest {

    @Test
    fun `createSpotShaderOrNull skips factory when runtime shader unsupported`() {
        var called = false
        val result = createSpotShaderOrNull(supported = false) {
            called = true
            "shader"
        }

        assertFalse(called)
        assertNull(result)
    }

    @Test
    fun `createSpotShaderOrNull returns null when shader creation throws`() {
        var called = false
        val result = createSpotShaderOrNull<String>(supported = true) {
            called = true
            throw IllegalArgumentException("unsupported AGSL")
        }

        assertTrue(called)
        assertNull(result)
    }

    @Test
    fun `createSpotShaderOrNull returns created shader when supported and succeeds`() {
        val result = createSpotShaderOrNull(supported = true) { "compiled-shader" }

        assertEquals("compiled-shader", result)
    }
}
