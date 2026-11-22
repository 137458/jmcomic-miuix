package com.jm3.app.miuix.ui.coui.component

import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import kotlin.test.Test
import kotlin.test.assertEquals

class CouiDetailButtonShapeTest {

    @Test
    fun `详情页主阅读按钮圆角与次级功能瓦片及菜单行统一为 16dp`() {
        assertEquals(CouiCommentFieldShape, CouiDetailButtonShape)
        assertEquals(16.dp, CouiDetailButtonCornerRadius)
        assertEquals(SquircleShape(cornerRadius = 16.dp, weight = 3f), CouiDetailButtonShape)
        assertEquals(CouiDetailButtonShape, CouiPrimaryActionDefaultShape)
    }
}
