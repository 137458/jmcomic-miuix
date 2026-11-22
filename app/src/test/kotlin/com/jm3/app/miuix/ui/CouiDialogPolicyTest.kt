package com.jm3.app.miuix.ui

import com.jm3.app.miuix.ui.coui.component.CouiDialogPolicy
import com.jm3.app.miuix.ui.coui.component.shouldUseCouiBlur
import com.jm3.app.miuix.ui.coui.component.shouldUsePageBackdrop
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CouiDialogPolicyTest {

    @Test
    @Suppress("DEPRECATION")
    fun 低于Android13一律不使用弹窗材质() {
        assertFalse(CouiDialogPolicy.shouldUseMaterial(sdkInt = 31, hasBackdrop = true))
        assertFalse(CouiDialogPolicy.shouldUseMaterial(sdkInt = 32, hasBackdrop = true))
        assertFalse(CouiDialogPolicy.shouldUseMaterial(sdkInt = 31, hasBackdrop = false))
    }

    @Test
    @Suppress("DEPRECATION")
    fun Android13以上缺少窗口背景源也不使用弹窗材质() {
        assertFalse(CouiDialogPolicy.shouldUseMaterial(sdkInt = 33, hasBackdrop = false))
        assertFalse(CouiDialogPolicy.shouldUseMaterial(sdkInt = 34, hasBackdrop = false))
    }

    @Test
    @Suppress("DEPRECATION")
    fun Android13以上且有窗口背景源才使用弹窗材质() {
        assertTrue(CouiDialogPolicy.shouldUseMaterial(sdkInt = 33, hasBackdrop = true))
        assertTrue(CouiDialogPolicy.shouldUseMaterial(sdkInt = 34, hasBackdrop = true))
    }

    @Test
    fun 页面背景录制仅在开启模糊且支持RuntimeShader时创建() {
        assertTrue(shouldUsePageBackdrop(blurEnabled = true, runtimeShaderSupported = true))
        assertFalse(shouldUsePageBackdrop(blurEnabled = false, runtimeShaderSupported = true))
        assertFalse(shouldUsePageBackdrop(blurEnabled = true, runtimeShaderSupported = false))
        assertFalse(shouldUsePageBackdrop(blurEnabled = false, runtimeShaderSupported = false))
    }

    @Test
    fun 材质模糊仅在开启模糊且有采样源且支持RuntimeShader时启用() {
        assertTrue(shouldUseCouiBlur(blurEnabled = true, hasBackdrop = true, runtimeShaderSupported = true))
        assertFalse(shouldUseCouiBlur(blurEnabled = false, hasBackdrop = true, runtimeShaderSupported = true))
        assertFalse(shouldUseCouiBlur(blurEnabled = true, hasBackdrop = false, runtimeShaderSupported = true))
        assertFalse(shouldUseCouiBlur(blurEnabled = true, hasBackdrop = true, runtimeShaderSupported = false))
        assertFalse(shouldUseCouiBlur(blurEnabled = false, hasBackdrop = false, runtimeShaderSupported = false))
    }
}
