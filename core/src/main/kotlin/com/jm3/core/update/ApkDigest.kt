package com.jm3.core.update

import java.io.File
import java.security.MessageDigest
import java.util.HexFormat

/**
 * 安装包摘要校验结论。
 *
 * 三态而不是布尔：没有摘要与摘要不匹配必须区分开。前者是发布流程没提供 digest，
 * 只跳过校验、不能拦下载；后者是内容与发布信息不一致，必须删包并报错。
 */
sealed interface DigestCheck {

    /** 远端没有可用 SHA-256 摘要，跳过校验。 */
    data object Skipped : DigestCheck

    /** 摘要一致。 */
    data object Matched : DigestCheck

    /** 摘要不一致，[expected] / [actual] 均为小写 hex，供提示与排查。 */
    data class Mismatched(val expected: String, val actual: String) : DigestCheck
}

/**
 * 安装包 SHA-256 摘要解析与校验。
 *
 * 取舍：只认 GitHub `digest` 字段里的 `sha256:<hex>`。GitHub 也可能下发其它算法，
 * 但发布资产用 SHA-256 是当前唯一实际出现的形态；其它算法一律当作“没有摘要”跳过，
 * 而不是按不认识的算法误判失败。
 */
object ApkDigest {

    private const val SHA256_PREFIX = "sha256:"
    private val sha256HexPattern = Regex("[0-9a-fA-F]{64}")

    /** 解析 `sha256:<64 位 hex>`，返回小写 hex；缺失、其它算法或格式非法时返回 null。 */
    fun expectedSha256(digest: String?): String? {
        val raw = digest?.trim().orEmpty()
        if (!raw.startsWith(SHA256_PREFIX, ignoreCase = true)) return null
        val hex = raw.substring(SHA256_PREFIX.length)
        if (!sha256HexPattern.matches(hex)) return null
        return hex.lowercase()
    }

    /** 计算文件 SHA-256，返回小写 hex。 */
    fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().toLowerHex()
    }

    /** 按远端 digest 原文校验文件；没有可用摘要时 [DigestCheck.Skipped]。 */
    fun verify(file: File, digest: String?): DigestCheck {
        val expected = expectedSha256(digest) ?: return DigestCheck.Skipped
        return verify(expected, sha256Hex(file))
    }

    /** 按已解析的摘要校验实际值；[expectedSha256] 为 null 时跳过。 */
    fun verify(expectedSha256: String?, actualSha256: String): DigestCheck {
        if (expectedSha256 == null) return DigestCheck.Skipped
        val actual = actualSha256.lowercase()
        return if (expectedSha256 == actual) {
            DigestCheck.Matched
        } else {
            DigestCheck.Mismatched(expected = expectedSha256, actual = actual)
        }
    }

    /** 校验失败时给用户看的文案，带上两段前缀便于排查。 */
    fun mismatchMessage(expected: String, actual: String): String =
        "安装包校验失败：SHA-256 不匹配（期望 ${expected.take(12)}…，实际 ${actual.take(12)}…）"

    private fun ByteArray.toLowerHex(): String = HexFormat.of().formatHex(this)
}
