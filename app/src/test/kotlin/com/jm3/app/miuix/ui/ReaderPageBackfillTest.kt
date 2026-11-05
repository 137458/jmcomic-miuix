package com.jm3.app.miuix.ui

import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 比例回填判定的 JVM 单测：调用方（ReaderScreen）只写状态，判定本身必须能被直接测。
 * 输入口径与屏幕一致：frame 是展示时用的那份请求框，detected 已经过 pageAspectRatio 过滤。
 */
class ReaderPageBackfillTest {

    private val frame = readerPageFrame(1080, knownRatio = null, chapterSeed = null, decodeRatio = null)

    @Test
    fun 高度顶满请求框时按真实比例重算解码尺寸() {
        // 1080x1440 的框解出 77x1440：高度顶满，重算才能换到 328x8192
        val backfill = readerPageBackfill(frame, detectedRatio = 0.04f, decodedWidthPx = 77f, decodedHeightPx = 1440f)
        assertEquals(0.04f, backfill?.decodeRatio)
        assertEquals(0.04f, backfill?.layoutRatio)
    }

    @Test
    fun 宽度顶满请求框时同样重算() {
        val backfill = readerPageBackfill(frame, detectedRatio = 2f, decodedWidthPx = 1080f, decodedHeightPx = 540f)
        assertEquals(2f, backfill?.decodeRatio)
        assertEquals(2f, backfill?.chapterSeed)
    }

    @Test
    fun 两维都没顶满时不动解码比例() {
        // 800x1200 的源小于请求框：重算只会再解一次同一张位图，布局照样按真实比例回填
        val backfill = readerPageBackfill(frame, detectedRatio = 2f / 3f, decodedWidthPx = 800f, decodedHeightPx = 1200f)
        assertNull(backfill?.decodeRatio)
        assertEquals(2f / 3f, backfill?.layoutRatio)
        assertEquals(2f / 3f, backfill?.chapterSeed)
    }

    @Test
    fun 条幅只回填布局不进种子也不重算() {
        // 1:25 的条幅没顶满请求框：布局照它排，但绝不能当章节种子把后续页带偏
        val backfill = readerPageBackfill(frame, detectedRatio = 0.04f, decodedWidthPx = 40f, decodedHeightPx = 1000f)
        assertNull(backfill?.chapterSeed)
        assertNull(backfill?.decodeRatio)
        assertEquals(0.04f, backfill?.layoutRatio)
    }

    @Test
    fun 单维差一像素也算顶满() {
        // 取整会让解码尺寸比请求框少 1 px，容差写进函数里，调用方不必各自补一遍
        val backfill = readerPageBackfill(frame, detectedRatio = 0.75f, decodedWidthPx = 1079f, decodedHeightPx = 1000f)
        assertEquals(0.75f, backfill?.decodeRatio)
    }

    @Test
    fun 解码尺寸非法时不回填() {
        assertNull(readerPageBackfill(frame, detectedRatio = 0.75f, decodedWidthPx = 0f, decodedHeightPx = 0f))
        assertNull(readerPageBackfill(frame, detectedRatio = 0.75f, decodedWidthPx = Float.NaN, decodedHeightPx = 100f))
    }

    @Test
    fun 比例非法时不回填() {
        assertNull(readerPageBackfill(frame, detectedRatio = 0f, decodedWidthPx = 800f, decodedHeightPx = 1200f))
        assertNull(readerPageBackfill(frame, detectedRatio = Float.NaN, decodedWidthPx = 800f, decodedHeightPx = 1200f))
        assertNull(readerPageBackfill(frame, detectedRatio = Float.POSITIVE_INFINITY, decodedWidthPx = 800f, decodedHeightPx = 1200f))
    }

    @Test
    fun 回填结果能直接喂回换算入口() {
        // 顶满后把真实比例写进解码比例，下一次换算就必须给出按真实比例的解码尺寸
        val backfill = readerPageBackfill(frame, detectedRatio = 0.5f, decodedWidthPx = 1080f, decodedHeightPx = 2160f)
        assertEquals(IntSize(1080, 1440), frame.decodeSize)
        val next = readerPageFrame(
            pageWidthPx = 1080,
            knownRatio = backfill?.layoutRatio,
            chapterSeed = backfill?.chapterSeed,
            decodeRatio = backfill?.decodeRatio,
        )
        assertEquals(0.5f, next.layoutRatio)
        assertEquals(IntSize(1080, 2160), next.decodeSize)
    }
}
