package com.jm3.app.miuix.ui

import com.jm3.app.miuix.ui.coui.component.shouldUseCouiBlur
import com.jm3.app.miuix.ui.coui.component.shouldUsePageBackdrop
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CouiDialogPolicyTest {

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
