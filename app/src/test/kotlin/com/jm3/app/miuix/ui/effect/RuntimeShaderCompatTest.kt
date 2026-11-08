package com.jm3.app.miuix.ui.effect

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class RuntimeShaderCompatTest {
    /**
     * brush 每帧被读取：低版本无着色器时必须复用同一个透明画刷，
     * 否则每次重组都会新建对象，给绘制层制造无谓的分配与失效。
     */
    @Test
    fun `无着色器时画刷跨读取保持同一实例`() {
        val compat = RuntimeShaderCompat("uniform float u;")

        val first = compat.brush
        val second = compat.brush

        assertSame(first, second)
        assertEquals(SolidColor(Color.Transparent), first)
    }
}
