package com.jm3.app.miuix.ui

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 作品卡尺寸令牌契约：
 * 行卡封面尺寸与固有尺寸锁定在 72dp x 96dp（严格 3:4），
 * 避免不同分辨率的网络封面图片把列表行高撑开，保证同一列表里每张卡片高度绝对一致。
 */
class WorkCardTokensTest {

    @Test
    fun 行卡封面严格保持三比四比例() {
        val width = WorkCardTokens.RowCoverWidth.value
        val height = WorkCardTokens.RowCoverHeight.value
        assertEquals(72f, width)
        assertEquals(96f, height)
        assertEquals(3f / 4f, width / height)
        assertEquals(WorkCardTokens.CoverAspect, width / height)
    }

    @Test
    fun 缩略图严格保持三比四比例() {
        val width = WorkCardTokens.ThumbWidth.value
        val height = WorkCardTokens.ThumbHeight.value
        assertEquals(48f, width)
        assertEquals(64f, height)
        assertEquals(3f / 4f, width / height)
    }

    @Test
    fun 行卡文字上下内边距收紧为八() {
        assertEquals(8.dp, WorkCardTokens.RowTextPaddingV)
    }
}
