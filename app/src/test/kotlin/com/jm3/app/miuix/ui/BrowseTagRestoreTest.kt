package com.jm3.app.miuix.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BrowseTagRestoreTest {

    @Test
    fun `normalizeBrowseTagKeyword strips whitespace and rejects blank tags`() {
        assertEquals("全彩", normalizeBrowseTagKeyword("  全彩 "))
        assertNull(normalizeBrowseTagKeyword(null))
        assertNull(normalizeBrowseTagKeyword("   \t"))
    }

    @Test
    fun `shouldReloadRestoredTag triggers only when restored empty tag is idle`() {
        assertTrue(shouldReloadRestoredTag("全彩", itemCount = 0, loading = false, hasFailure = false))
        assertFalse(shouldReloadRestoredTag(null, itemCount = 0, loading = false, hasFailure = false))
        assertFalse(shouldReloadRestoredTag("   ", itemCount = 0, loading = false, hasFailure = false))
        assertFalse(shouldReloadRestoredTag("全彩", itemCount = 12, loading = false, hasFailure = false))
        assertFalse(shouldReloadRestoredTag("全彩", itemCount = 0, loading = true, hasFailure = false))
        assertFalse(shouldReloadRestoredTag("全彩", itemCount = 0, loading = false, hasFailure = true))
    }
}
