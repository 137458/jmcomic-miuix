// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.page

import com.itos.coui.layout.CouiPageDefaults
import com.jm3.app.miuix.ui.coui.component.CouiWorkCardTokens
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * COUI 皮肤「每周连载更新」卡片一致性测试：
 * 无论是榜单页内嵌列表还是分类页顶栏弹层，均应与其他 COUI 页面保持相同的独立浮岛行卡形态，
 * 不外包 Group、不夹带分割线，并沿用统一间距与页边距令牌。
 */
class CouiWeeklyCardLayoutTest {

    @Test
    fun `榜单内每周更新使用无Group无分割线的独立行卡`() {
        val spec = couiWeeklyCardLayoutSpec(inDialog = false)
        assertFalse(spec.wrapInGroup)
        assertFalse(spec.showDivider)
        assertEquals(CouiWorkCardTokens.RowSpacing.value, spec.cardSpacingDp)
        assertEquals(CouiPageDefaults.horizontalMargin.value, spec.horizontalMarginDp)
    }

    @Test
    fun `分类弹层每周更新同样移除分割线并使用标准行卡间距`() {
        val spec = couiWeeklyCardLayoutSpec(inDialog = true)
        assertFalse(spec.wrapInGroup)
        assertFalse(spec.showDivider)
        assertEquals(CouiWorkCardTokens.RowSpacing.value, spec.cardSpacingDp)
        assertEquals(0f, spec.horizontalMarginDp)
    }
}
