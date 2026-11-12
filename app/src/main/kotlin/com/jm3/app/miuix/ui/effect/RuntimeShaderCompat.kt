// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.effect

import android.graphics.RuntimeShader
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SolidColor

/**
 * 着色器与 Brush 的兼容包装：低版本或着色器编译失败时退化为全透明，调用方不需要分支。
 *
 * 能力门槛只有 [isRuntimeShaderSupported] 一处；本文件内的 API 33 平台符号调用直接以它作守卫，
 * 不再各自比较版本号（它的 `@ChecksSdkIntAtLeast` 声明同时满足 lint 的 NewApi 数据流校验）。
 */
class RuntimeShaderCompat(
    sksl: String,
) {
    private val shader: RuntimeShader? =
        if (isRuntimeShaderSupported()) {
            try {
                RuntimeShader(sksl)
            } catch (_: Throwable) {
                null
            }
        } else {
            null
        }

    fun setFloatUniform(
        name: String,
        value: Float,
    ) {
        if (isRuntimeShaderSupported()) {
            shader?.setFloatUniform(name, value)
        }
    }

    fun setFloatUniform(
        name: String,
        values: FloatArray,
    ) {
        if (isRuntimeShaderSupported()) {
            shader?.setFloatUniform(name, values)
        }
    }

    /** 画刷每次读取都来自缓存：painter 每帧读它，重复 new 会让绘制层持续失效。 */
    val brush: Brush =
        if (shader != null) {
            ShaderBrush(shader)
        } else {
            SolidColor(Color.Transparent)
        }
}
