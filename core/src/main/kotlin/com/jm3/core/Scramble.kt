package com.jm3.core

import java.security.MessageDigest

/**
 * 禁漫图片分割数计算（与 JM 端 scramble 算法一致）。
 *
 * 规则：
 * - photoId < scrambleId -> 0（无分割）
 * - photoId < 268850 -> 10
 * - 其余 x = 10（photoId < 421926）或 8
 * - 段数 = md5(photoId + 文件名去后缀) 末字符 ASCII % x * 2 + 2
 */
object Scramble {

    const val SCRAMBLE_268850: Long = 268850L
    const val SCRAMBLE_421926: Long = 421926L

    fun numSegments(scrambleId: Long, photoId: Long, filenameWithoutSuffix: String): Int {
        if (photoId < scrambleId) return 0
        if (photoId < SCRAMBLE_268850) return 10
        val x = if (photoId < SCRAMBLE_421926) 10 else 8
        val md5 = md5Hex(photoId.toString() + filenameWithoutSuffix)
        return (md5.last().code % x) * 2 + 2
    }

    private fun md5Hex(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
