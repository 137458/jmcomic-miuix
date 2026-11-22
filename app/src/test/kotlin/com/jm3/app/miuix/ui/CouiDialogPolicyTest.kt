package com.jm3.app.miuix.ui

import com.jm3.app.miuix.ui.coui.component.CouiDialogPolicy
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CouiDialogPolicyTest {

    @Test
    fun 低于Android13一律不使用弹窗材质() {
        assertFalse(CouiDialogPolicy.shouldUseMaterial(sdkInt = 31, hasBackdrop = true))
        assertFalse(CouiDialogPolicy.shouldUseMaterial(sdkInt = 32, hasBackdrop = true))
        assertFalse(CouiDialogPolicy.shouldUseMaterial(sdkInt = 31, hasBackdrop = false))
    }

    @Test
    fun Android13以上缺少窗口背景源也不使用弹窗材质() {
        assertFalse(CouiDialogPolicy.shouldUseMaterial(sdkInt = 33, hasBackdrop = false))
        assertFalse(CouiDialogPolicy.shouldUseMaterial(sdkInt = 34, hasBackdrop = false))
    }

    @Test
    fun Android13以上且有窗口背景源才使用弹窗材质() {
        assertTrue(CouiDialogPolicy.shouldUseMaterial(sdkInt = 33, hasBackdrop = true))
        assertTrue(CouiDialogPolicy.shouldUseMaterial(sdkInt = 34, hasBackdrop = true))
    }
}
