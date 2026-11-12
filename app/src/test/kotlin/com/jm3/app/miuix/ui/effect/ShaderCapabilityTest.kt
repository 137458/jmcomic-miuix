package com.jm3.app.miuix.ui.effect

import android.os.Build
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 能力判定的边界：唯一入口只认 API 33，且结构化结果与布尔入口同源。
 *
 * 单测跑在 JVM 上，[Build.VERSION.SDK_INT] 被桩成 0，因此这里只断言「默认值判定为不可用」与
 * 「两条入口给同一结论」，真机版本分支由 [resolveShaderCapability] 的参数覆盖。
 */
class ShaderCapabilityTest {

    @Test
    fun `Android 13 起判定可用且没有不可用原因`() {
        val capability = resolveShaderCapability(Build.VERSION_CODES.TIRAMISU)

        assertTrue(capability.runtimeShaderSupported)
        assertNull(capability.unavailableReason)
        assertEquals(Build.VERSION_CODES.TIRAMISU, capability.sdkInt)
    }

    @Test
    fun `Android 12 及以下判定不可用并给出 API 原因`() {
        for (sdkInt in listOf(Build.VERSION_CODES.S_V2, Build.VERSION_CODES.S)) {
            val capability = resolveShaderCapability(sdkInt)

            assertFalse(capability.runtimeShaderSupported, "API $sdkInt 不应声称支持运行时着色器")
            assertEquals(ShaderUnavailableReason.ApiLevel, capability.unavailableReason)
            assertEquals(sdkInt, capability.sdkInt)
        }
    }

    @Test
    fun `布尔入口与结构化结果来自同一次判定`() {
        assertEquals(
            resolveShaderCapability(Build.VERSION.SDK_INT).runtimeShaderSupported,
            isRuntimeShaderSupported(),
        )
    }
}
