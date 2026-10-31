package com.jm3.app.miuix.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChapterGroupToggleTest {

    @Test
    fun 单话分组不给展开入口() {
        assertFalse(shouldShowChapterGroupToggle(1))
    }

    @Test
    fun 空分组不给展开入口() {
        assertFalse(shouldShowChapterGroupToggle(0))
    }

    @Test
    fun 两话分组给展开入口() {
        assertTrue(shouldShowChapterGroupToggle(2))
    }

    @Test
    fun 满组十话给展开入口() {
        assertTrue(shouldShowChapterGroupToggle(10))
    }
}
