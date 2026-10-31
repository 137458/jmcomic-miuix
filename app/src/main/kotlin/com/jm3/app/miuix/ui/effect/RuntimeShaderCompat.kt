package com.jm3.app.miuix.ui.effect

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SolidColor

/** Android 13 起才有 AGSL 运行时着色器。 */
fun isRuntimeShaderSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

/**
 * 着色器与 Brush 的兼容包装：低版本或着色器编译失败时退化为全透明，调用方不需要分支。
 */
class RuntimeShaderCompat(
    sksl: String,
) {
    private val shader: RuntimeShader? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            shader?.setFloatUniform(name, value)
        }
    }

    fun setFloatUniform(
        name: String,
        values: FloatArray,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            shader?.setFloatUniform(name, values)
        }
    }

    val brush: Brush
        get() =
            if (shader != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ShaderBrush(shader)
            } else {
                SolidColor(Color.Transparent)
            }
}
