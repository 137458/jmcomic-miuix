package com.jm3.app.miuix.ui.coui.component

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CouiImePolicyTest {

    @Test
    fun 键盘只在宿主窗口自己持有焦点时才算占用本页() {
        assertTrue(couiImeOccupiesPage(imeVisible = true, windowFocused = true))
        // 输入弹窗或分屏另一侧持焦点：宿主窗口仍能读到 IME 尺寸，此时不能用它收缩页面
        assertFalse(couiImeOccupiesPage(imeVisible = true, windowFocused = false))
        assertFalse(couiImeOccupiesPage(imeVisible = false, windowFocused = true))
        assertFalse(couiImeOccupiesPage(imeVisible = false, windowFocused = false))
    }
}
