package com.jm3.core

import io.github.jukomu.jmcomic.core.crypto.JmImageTool
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScrambleTest {

    private fun md5Hex(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    /** 测试自身预言机：按公开公式独立推定期望段数。 */
    private fun expected(aid: Long, filename: String): Int {
        if (aid < Scramble.SCRAMBLE_268850) return 10
        val x = if (aid < Scramble.SCRAMBLE_421926) 10 else 8
        val last = md5Hex(aid.toString() + filename).last().code
        return (last % x) * 2 + 2
    }

    @Test
    fun 边界_照片号小于分割号_为0() {
        assertEquals(0, Scramble.numSegments(scrambleId = 500000L, photoId = 499999L, filenameWithoutSuffix = "1.jpg"))
    }

    @Test
    fun 边界_照片号等于分割号阈值_进入常规分支() {
        // 268850 边界：不小于阈值，走 x 分支
        val got = Scramble.numSegments(scrambleId = 0L, photoId = Scramble.SCRAMBLE_268850, filenameWithoutSuffix = "1.jpg")
        assertEquals(expected(Scramble.SCRAMBLE_268850, "1.jpg"), got)
        assertTrue(got in 2..22)
    }

    @Test
    fun 旧区间_小于268850_固定10段() {
        assertEquals(10, Scramble.numSegments(scrambleId = 0L, photoId = 100000L, filenameWithoutSuffix = "1.jpg"))
        assertEquals(10, Scramble.numSegments(scrambleId = 99999L, photoId = 268849L, filenameWithoutSuffix = "a.png"))
    }

    @Test
    fun 常规区间_段数为偶数且在2到2x之间() {
        for (aid in listOf(300000L, 421925L, 421926L, 500000L, 999999999L)) {
            val got = Scramble.numSegments(scrambleId = 0L, photoId = aid, filenameWithoutSuffix = "1.jpg")
            val x = if (aid < Scramble.SCRAMBLE_421926) 10 else 8
            assertTrue(got in 2..x * 2, "aid=$aid got=$got")
            assertEquals(0, got % 2, "段数必须为偶数, aid=$aid")
        }
    }

    @Test
    fun 差分_与jmcomic库实现一致_常规向量() {
        for ((scrambleId, photoId, filename) in listOf(
            Triple(0L, 300000L, "1.jpg"),
            Triple(0L, 421926L, "cover.jpg"),
            Triple(999999L, 999999999L, "00001.webp"),
            Triple(450000L, 450000L, "x.png"),
            Triple(100L, 268850L, "y.jpg"),
        )) {
            assertEquals(
                JmImageTool.calculateNumSegments(scrambleId, photoId, filename),
                Scramble.numSegments(scrambleId, photoId, filename),
                "scrambleId=$scrambleId photoId=$photoId filename=$filename",
            )
        }
    }

    @Test
    fun 差分_随机向量50组_与库实现一致() {
        var seed = 20260927L
        fun next(): Long { seed = seed * 6364136223846793005L + 1442695040888963407L; return (seed ushr 1) }
        repeat(50) {
            val photoId = next() % 1_000_000L
            val scrambleId = next() % 600_000L
            val filename = "${next() % 100}.jpg"
            assertEquals(
                JmImageTool.calculateNumSegments(scrambleId, photoId, filename),
                Scramble.numSegments(scrambleId, photoId, filename),
                "scrambleId=$scrambleId photoId=$photoId filename=$filename",
            )
        }
    }
}
