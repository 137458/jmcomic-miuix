package com.jm3.app.miuix.ui.coui.component

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class CouiDetailButtonShapeTest {

    @Test
    fun `详情页主阅读按钮圆角与次级功能瓦片及菜单行统一为 16dp`() {
        assertEquals(CouiCommentFieldShape, CouiDetailButtonShape)
        assertEquals(16.dp, CouiDetailButtonShape.radius)
        assertEquals(CouiDetailButtonShape, CouiPrimaryActionDefaultShape)
    }
}
