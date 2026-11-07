package com.jm3.app.miuix.ui

import androidx.compose.ui.unit.IntSize
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReaderPageLogicTest {

    @Test
    fun 宽高比按解码后的宽除以高算() {
        assertEquals(0.75f, pageAspectRatio(1080f, 1440f))
        // 800x20000 的长条图：真实比例约 1:25，长图模式必须按它解码
        assertEquals(0.04f, pageAspectRatio(800f, 20000f))
    }

    @Test
    fun 宽或高为零时没有比例() {
        assertNull(pageAspectRatio(0f, 1440f))
        assertNull(pageAspectRatio(1080f, 0f))
        assertNull(pageAspectRatio(0f, 0f))
    }

    @Test
    fun 负的宽高不算比例() {
        assertNull(pageAspectRatio(-1080f, 1440f))
        assertNull(pageAspectRatio(1080f, -1440f))
    }

    @Test
    fun NaN与无穷大不算比例() {
        assertNull(pageAspectRatio(Float.NaN, 1440f))
        assertNull(pageAspectRatio(1080f, Float.NaN))
        assertNull(pageAspectRatio(Float.POSITIVE_INFINITY, 1440f))
        assertNull(pageAspectRatio(1080f, Float.POSITIVE_INFINITY))
    }

    @Test
    fun 比例未知时按三比四兜底() {
        // 写死 1080x1440：比例未知时解码尺寸必须与旧的 3:4 占位盒一致，不能悄悄变大变小
        assertEquals(IntSize(1080, 1440), readerPageDecodeSize(1080, null))
    }

    @Test
    fun 三比四的页解码尺寸与兜底一致() {
        // 普通竖版页解出真实比例后应算出同一个尺寸，否则每页都会重解码一次
        assertEquals(IntSize(1080, 1440), readerPageDecodeSize(1080, 0.75f))
    }

    @Test
    fun 长条图按真实比例解码铺满页宽() {
        // 1:2 的页：宽度铺满页宽，高度按比例撑开，不再压进 3:4 的框
        assertEquals(IntSize(1080, 2160), readerPageDecodeSize(1080, 0.5f))
    }

    @Test
    fun 宽图解码高度按比例收窄() {
        // 2:1 的横图：宽度仍是页宽，高度只有一半
        assertEquals(IntSize(1080, 540), readerPageDecodeSize(1080, 2f))
    }

    @Test
    fun 超长条幅按单边上限与像素预算收敛() {
        // 800x20000 的长条图按 1080 页宽原比例解码要 1080x27000（约 116 MB），
        // 收敛后是 328x8192（约 10.7 MB），既守住 16 MB 上界又比写死 3:4 清晰得多
        val size = readerPageDecodeSize(1080, 800f / 20000f)
        assertEquals(IntSize(328, 8192), size)
        assertTrue(size.width.toLong() * size.height <= 4_000_000L)
    }

    @Test
    fun 极窄的页只受单边上限约束() {
        // 页宽 480、比例 1:100：高度先被单边上限截断到 8192，面积仍在上界内，宽度等比缩到 82
        val size = readerPageDecodeSize(480, 0.01f)
        assertEquals(IntSize(82, 8192), size)
        assertTrue(size.width.toLong() * size.height <= 4_000_000L)
    }

    @Test
    fun 页宽未知时至少解码一像素() {
        assertEquals(IntSize(1, 1), readerPageDecodeSize(0, null))
        assertEquals(IntSize(1, 2), readerPageDecodeSize(0, 0.5f))
    }

    @Test
    fun 非法比例退回三比四兜底() {
        assertEquals(IntSize(1080, 1440), readerPageDecodeSize(1080, 0f))
        assertEquals(IntSize(1080, 1440), readerPageDecodeSize(1080, -0.5f))
        assertEquals(IntSize(1080, 1440), readerPageDecodeSize(1080, Float.NaN))
        assertEquals(IntSize(1080, 1440), readerPageDecodeSize(1080, Float.POSITIVE_INFINITY))
    }

    @Test
    fun 本页已知比例优先于章节种子() {
        assertEquals(0.5f, readerPagePlaceholderRatio(0.5f, 0.75f))
    }

    @Test
    fun 本页比例未知时借用章节种子() {
        // 同一话各页比例基本一致，借用前一页的比例可以少跳一次
        assertEquals(0.04f, readerPagePlaceholderRatio(null, 0.04f))
    }

    @Test
    fun 都未知时用三比四占位() {
        assertEquals(0.75f, readerPagePlaceholderRatio(null, null))
    }

    @Test
    fun 非法比例不参与占位() {
        assertEquals(0.04f, readerPagePlaceholderRatio(0f, 0.04f))
        assertEquals(0.75f, readerPagePlaceholderRatio(Float.NaN, null))
    }

    @Test
    fun 条幅不参与章节种子() {
        // 1:25 的条幅若当章节种子，后续未解码页的占位框会被撑到上万 px，加载完成后又塌回来
        assertNull(chapterSeedCandidate(0.04f))
    }

    @Test
    fun 章节种子下限取到一比二() {
        // 边界值本身可以当种子，更窄的长条交给本页自己的比例
        assertEquals(0.5f, chapterSeedCandidate(0.5f))
        assertEquals(0.75f, chapterSeedCandidate(0.75f))
    }

    @Test
    fun 非法比例不能当章节种子() {
        assertNull(chapterSeedCandidate(0f))
        assertNull(chapterSeedCandidate(Float.NaN))
        assertNull(chapterSeedCandidate(Float.POSITIVE_INFINITY))
    }

    @Test
    fun 请求尺寸只跟随解码比例() {
        // 布局比例已是 3:4，请求尺寸仍按解码比例 1:2 算：两份状态各管一段
        val frame = readerPageFrame(1080, knownRatio = 0.75f, chapterSeed = null, decodeRatio = 0.5f)
        assertEquals(0.75f, frame.layoutRatio)
        assertEquals(IntSize(1080, 2160), frame.decodeSize)
    }

    @Test
    fun 解码比例未知时请求按三比四兜底() {
        val frame = readerPageFrame(1080, knownRatio = 0.5f, chapterSeed = null, decodeRatio = null)
        assertEquals(0.5f, frame.layoutRatio)
        assertEquals(IntSize(1080, 1440), frame.decodeSize)
    }

    @Test
    fun 布局比例变化不改变请求尺寸() {
        // 回填真实比例只重排布局；请求尺寸跟着变会让 coil3 判缓存失效，每页都重解一次
        val unknown = readerPageFrame(1080, knownRatio = null, chapterSeed = 0.75f, decodeRatio = null)
        val detected = readerPageFrame(1080, knownRatio = 0.5f, chapterSeed = 0.5f, decodeRatio = null)
        assertEquals(0.75f, unknown.layoutRatio)
        assertEquals(0.5f, detected.layoutRatio)
        assertEquals(unknown.decodeSize, detected.decodeSize)
    }

    @Test
    fun 顶满请求框才算受框限制() {
        // 源码 800x1200 小于 1080x1440 的请求框：两维都没顶满，重算只会再解一次同一张图
        assertFalse(isBoxLimited(1080, 1440, 800f, 1200f))
        // 解出 77x1440：高度顶满，按真实比例重算才能换到 328x8192
        assertTrue(isBoxLimited(1080, 1440, 77f, 1440f))
        // 宽度顶满同样算受框限制
        assertTrue(isBoxLimited(1080, 1440, 1080f, 540f))
    }

    @Test
    fun 各比例收敛后仍保持等比() {
        // 离散点用例可能同时通过，等比这条不变式才能锁住钳位链路
        for (ratio in listOf(0.25f, 0.5f, 0.667f, 0.75f, 1f, 1.5f, 2f)) {
            val size = readerPageDecodeSize(1080, ratio)
            val actual = size.width.toFloat() / size.height.toFloat()
            assertTrue(abs(actual - ratio) / ratio < 0.01f, "比例 $ratio 收敛后为 $actual")
        }
    }

    @Test
    fun 面积上限边界() {
        // 正好 4 MP 不缩放
        assertEquals(IntSize(1000, 4000), readerPageDecodeSize(1000, 0.25f))
        // 超过 4 MP 按面积缩回预算内，取整不能把面积又顶出去
        val shrunk = readerPageDecodeSize(1000, 0.24f)
        assertEquals(IntSize(979, 4082), shrunk)
        assertTrue(shrunk.width.toLong() * shrunk.height <= MAX_PAGE_DECODE_PIXELS)
    }

    @Test
    fun 极端比例不崩且至少一像素() {
        // 宽 / 极小比例会溢出到 Int.MAX，两维仍须不小于 1：coil3 的 Dimension.Pixels 要求 px > 0
        assertEquals(IntSize(1, 8192), readerPageDecodeSize(1080, 1e-30f))
    }

    @Test
    fun 超大页宽也不越预算() {
        // 单边上限先于面积预算生效，页宽再大也要落回预算内
        val size = readerPageDecodeSize(Int.MAX_VALUE, 0.75f)
        assertTrue(size.width.toLong() * size.height <= MAX_PAGE_DECODE_PIXELS)
    }

    @Test
    fun 解码预算按图片质量档参数化() {
        // 1440x2880 的页约 4.15 MP：省流档要缩到 2 MP 内，标准档缩到 4 MP 内，高清档原尺寸
        val budgets = listOf(EconomyBudget, StandardBudget, HighBudget)
        val sizes = budgets.map { readerPageDecodeSize(1440, 0.5f, maxPixels = it) }

        sizes.forEachIndexed { index, size ->
            assertTrue(
                size.width.toLong() * size.height <= budgets[index],
                "档位 ${budgets[index]} 解出 ${size.width}x${size.height}",
            )
        }
        assertTrue(sizes[0].width < sizes[1].width)
        assertTrue(sizes[1].width < sizes[2].width)
        assertEquals(IntSize(1440, 2880), sizes[2])
    }

    @Test
    fun 展示换算与预取共用同一质量档() {
        val frame = readerPageFrame(
            pageWidthPx = 1440,
            knownRatio = 0.5f,
            chapterSeed = null,
            decodeRatio = 0.5f,
            maxPixels = EconomyBudget,
        )
        assertTrue(frame.decodeSize.width.toLong() * frame.decodeSize.height <= EconomyBudget)
        // 收敛后仍保持 1:2，且比不限制预算的原尺寸小
        val actualRatio = frame.decodeSize.width.toFloat() / frame.decodeSize.height.toFloat()
        assertTrue(abs(actualRatio - 0.5f) < 0.01f, "收敛后比例为 $actualRatio")
        assertTrue(frame.decodeSize.width < readerPageDecodeSize(1440, 0.5f).width)
    }

    @Test
    fun 双页并存的解码预算按两页分摊不翻倍() {
        val windowWidth = 1600
        val windowHeight = 1200
        listOf(EconomyBudget, StandardBudget, HighBudget).forEach { budget ->
            val page = readerSpreadPageDecodeSize(windowWidth, windowHeight, budget)
            // 两页合计不能超过一档预算
            assertTrue(
                page.width.toLong() * page.height * 2 <= budget.toLong(),
                "预算 $budget 两页合计 ${page.width}x${page.height} x2 超了",
            )
            // 单页预算取一半，宽度取半屏
            assertTrue(page.width <= windowWidth / 2)
        }
    }

    @Test
    fun 双页单页尺寸与整屏收敛后的密度一致() {
        // 1600x1200 在标准档：整屏约 1365x1024，双页每页约 965x724，两页合计仍在一档预算内
        val single = readerScreenDecodeSize(1600, 1200)
        val spreadPage = readerSpreadPageDecodeSize(1600, 1200)
        assertTrue(spreadPage.width < single.width)
        assertTrue(
            spreadPage.width.toLong() * spreadPage.height * 2 <= StandardBudget.toLong(),
            "双页合计必须留在标准档预算内",
        )
    }

    private companion object {
        const val EconomyBudget = 2_000_000
        const val StandardBudget = 4_000_000
        const val HighBudget = 6_000_000
    }
}

