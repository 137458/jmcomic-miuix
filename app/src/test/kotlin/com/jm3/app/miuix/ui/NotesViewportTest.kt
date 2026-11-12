package com.jm3.app.miuix.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 说明视口的规格与折叠口径。
 *
 * 这些断言钉住的是"视口永远不会拿到无限上界"这条契约：违反它时 Compose 会抛
 * "Vertically scrollable component was measured with an infinity maximum height constraints"，
 * 而弹窗正文槽位恰恰以无限最大高度测量子节点，所以只能在这一层把非法输入压成有限值。
 */
class NotesViewportTest {

    @Test
    fun `spec keeps legal height and fade`() {
        val spec = notesViewportSpec(maxHeight = 240.dp, fade = 40.dp)

        assertEquals(240.dp, spec.maxHeight)
        assertEquals(40.dp, spec.fade)
    }

    @Test
    fun `spec falls back to minimum height when bound is infinite`() {
        val spec = notesViewportSpec(maxHeight = Dp.Infinity, fade = 40.dp)

        assertEquals(NotesViewportMinHeight, spec.maxHeight)
        assertEquals(24.dp, spec.fade)
    }

    @Test
    fun `spec falls back to minimum height when bound is below minimum`() {
        val spec = notesViewportSpec(maxHeight = 24.dp, fade = 8.dp)

        assertEquals(NotesViewportMinHeight, spec.maxHeight)
        assertEquals(8.dp, spec.fade)
    }

    @Test
    fun `spec drops fade when fade is not finite`() {
        assertEquals(0.dp, notesViewportSpec(maxHeight = 240.dp, fade = Dp.Infinity).fade)
    }

    @Test
    fun `spec clamps negative fade to zero`() {
        assertEquals(0.dp, notesViewportSpec(maxHeight = 240.dp, fade = (-8).dp).fade)
    }

    @Test
    fun `spec clamps fade to half of viewport height`() {
        val spec = notesViewportSpec(maxHeight = 240.dp, fade = 300.dp)

        assertEquals(240.dp, spec.maxHeight)
        assertEquals(120.dp, spec.fade)
    }

    @Test
    fun `preset viewports both expose finite bounds`() {
        assertTrue(NotesDialogViewport.maxHeight.value.isFinite())
        assertTrue(NotesPageViewport.maxHeight.value.isFinite())
        assertEquals(240.dp, NotesDialogViewport.maxHeight)
        assertEquals(320.dp, NotesPageViewport.maxHeight)
    }

    @Test
    fun `fold row stays visible only when it actually hides content`() {
        assertTrue(updateNotesFoldVisible(hiddenLineCount = 3, expanded = false))
        assertFalse(updateNotesFoldVisible(hiddenLineCount = 0, expanded = false))
        assertTrue(updateNotesFoldVisible(hiddenLineCount = 0, expanded = true))
    }

    @Test
    fun `fold label reports hidden line count only while collapsed`() {
        assertEquals("收起说明", updateNotesFoldLabel(expanded = true, hiddenLineCount = 5))
        assertEquals("展开全部（还有 5 行）", updateNotesFoldLabel(expanded = false, hiddenLineCount = 5))
    }

    @Test
    fun `fold is absent when nothing is hidden`() {
        var toggled = 0
        val fold = updateNotesFold(expanded = false, hiddenLineCount = 0, onToggle = { toggled++ })

        assertNull(fold)
        assertEquals(0, toggled)
    }

    @Test
    fun `fold carries expanded state label and toggle`() {
        var toggled = 0
        val collapsed = assertNotNull(updateNotesFold(expanded = false, hiddenLineCount = 7, onToggle = { toggled++ }))
        assertEquals(false, collapsed.expanded)
        assertEquals("展开全部（还有 7 行）", collapsed.label)

        collapsed.onToggle()
        assertEquals(1, toggled)

        val expanded = assertNotNull(updateNotesFold(expanded = true, hiddenLineCount = 0, onToggle = { toggled++ }))
        assertTrue(expanded.expanded)
        assertEquals("收起说明", expanded.label)
    }
}
