// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.component

import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * COUI 作品卡排版契约：
 * 卡内文字左缘要与分组标题的左缘落在同一条竖线上；卡片高度只由预留行数决定，与作者、分类的实际字形无关。
 * 这些常量与折算函数一旦被改动，卡片会重新错位或高低不齐。
 */
class CouiWorkCardLayoutTest {

    @Test
    fun 竖卡卡内文字内衬取页面边距的十六() {
        assertEquals(16.dp, CouiWorkCardTokens.TextPaddingH)
    }

    @Test
    fun 行卡文字内衬与竖卡一致() {
        assertEquals(16.dp, CouiWorkCardTokens.RowTextPaddingH)
    }

    @Test
    fun 分组标题文字左缘固定三十二() {
        assertEquals(32.dp, CouiWorkCardTokens.SectionTextInset)
    }

    @Test
    fun 轨道留白让卡内文字对上分组标题() {
        assertEquals(
            CouiWorkCardTokens.SectionTextInset,
            CouiWorkCardTokens.TrackContentPadding + CouiWorkCardTokens.TextPaddingH,
        )
    }

    @Test
    fun 轨道留白落在页面边距上() {
        // 页面边距 16dp 加卡内内衬 16dp 正好是分组标题的 32dp，轨道自己不应再额外缩进
        assertEquals(16.dp, CouiWorkCardTokens.TrackContentPadding)
    }

    @Test
    fun 未声明行高时按系数折算成固定行高() {
        val pinned = couiPinnedLineHeight(fontSize = 16.sp, declaredLineHeight = TextUnit.Unspecified)
        assertEquals(16f * 1.35f, pinned.value, 1e-4f)
        assertEquals(TextUnitType.Sp, pinned.type)
    }

    @Test
    fun 较小字号同样按系数折算() {
        val pinned = couiPinnedLineHeight(fontSize = 13.sp, declaredLineHeight = TextUnit.Unspecified)
        assertEquals(13f * 1.35f, pinned.value, 1e-4f)
    }

    @Test
    fun 已声明行高时原样保留() {
        assertEquals(20.sp, couiPinnedLineHeight(fontSize = 16.sp, declaredLineHeight = 20.sp))
    }

    @Test
    fun 文字块占位高度等于行高乘预留行数() {
        assertEquals(21.6f * 2f, couiTextBlockHeight(21.6.sp, 2).value, 1e-4f)
        assertEquals(17.55f, couiTextBlockHeight(17.55.sp, 1).value, 1e-4f)
    }

    @Test
    fun 作者与分类各自成段并去掉首尾空白() {
        val parts = couiWorkSubtitleParts(" 张三 ", " 同人 ")
        assertEquals("张三", parts.author)
        assertEquals("同人", parts.category)
    }

    @Test
    fun 空白作者与空白分类归一成空() {
        val parts = couiWorkSubtitleParts("   ", " ")
        assertNull(parts.author)
        assertNull(parts.category)
    }

    @Test
    fun 只有分类时作者为空() {
        val parts = couiWorkSubtitleParts(null, "Cosplay")
        assertNull(parts.author)
        assertEquals("Cosplay", parts.category)
    }

    @Test
    fun 超长作者不会被并入分类() {
        val longAuthor = "[中国翻訳] " + "とても長い作者名".repeat(4)
        val parts = couiWorkSubtitleParts(longAuthor, "同人")
        assertEquals(longAuthor, parts.author)
        assertEquals("同人", parts.category)
    }

    @Test
    fun 分类角标字号为十号() {
        assertEquals(10.sp, CouiWorkCardTokens.CategoryBadgeFontSize)
    }

    @Test
    fun 分类角标内边距与距封面内缩() {
        assertEquals(6.dp, CouiWorkCardTokens.CategoryBadgePaddingH)
        assertEquals(2.dp, CouiWorkCardTokens.CategoryBadgePaddingV)
        assertEquals(6.dp, CouiWorkCardTokens.CategoryBadgeInset)
    }

    @Test
    fun 分类角标最大宽度不超过封面宽减两个内缩() {
        assertEquals(CouiWorkCardTokens.GridWidth - CouiWorkCardTokens.CategoryBadgeInset * 2, couiCategoryBadgeMaxWidth(CouiWorkCardTokens.GridWidth))
    }

    @Test
    fun 封面过窄时角标最大宽度不为负() {
        assertEquals(0.dp, couiCategoryBadgeMaxWidth(12.dp))
    }

    @Test
    fun 角标文案去空白后为空则不绘制() {
        assertNull(couiNormalizedText(null))
        assertNull(couiNormalizedText("   "))
        assertEquals("同人", couiNormalizedText(" 同人 "))
    }

    @Test
    fun 竖卡副标题只留作者分类改挂角标() {
        // 竖卡正文列只有 80dp，分类留在副标题行会把作者挤到只剩一两个字，分类因此挪到封面角标
        val labels = couiWorkGridLabels("李万姬", "韩漫")
        assertEquals("李万姬", labels.author)
        assertEquals("韩漫", labels.categoryBadge)
    }

    @Test
    fun 超长作者不会把分类并入副标题() {
        val longAuthor = "[中国翻訳] " + "とても長い作者名".repeat(4)
        val labels = couiWorkGridLabels(longAuthor, "同人")
        assertEquals(longAuthor, labels.author)
        assertEquals("同人", labels.categoryBadge)
    }

    @Test
    fun 竖卡只有作者时角标为空() {
        val labels = couiWorkGridLabels("NOK", null)
        assertEquals("NOK", labels.author)
        assertNull(labels.categoryBadge)
    }
}
