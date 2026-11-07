package com.jm3.core.update

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 安装包摘要：GitHub 的 `digest` 只认 `sha256:<64 hex>`，其它算法与坏格式都按“没有摘要”处理，
 * 校验结论必须区分“跳过”和“不匹配”，前者不能拦住下载。
 */
class ApkDigestTest {

    private val hex64 = "0123456789abcdef".repeat(4)
    private val upperHex64 = "0123456789ABCDEF".repeat(4)

    @Test
    fun `parses sha256 digest to lower case hex`() {
        assertEquals(hex64, ApkDigest.expectedSha256("sha256:$hex64"))
        assertEquals(hex64, ApkDigest.expectedSha256("SHA256:$upperHex64"))
        assertEquals(hex64, ApkDigest.expectedSha256("  sha256:$hex64  "))
    }

    @Test
    fun `rejects unsupported algorithms and malformed digests`() {
        assertNull(ApkDigest.expectedSha256(null))
        assertNull(ApkDigest.expectedSha256(""))
        assertNull(ApkDigest.expectedSha256("   "))
        assertNull(ApkDigest.expectedSha256("md5:$hex64"))
        assertNull(ApkDigest.expectedSha256(hex64))
        assertNull(ApkDigest.expectedSha256("sha256:" + "a".repeat(63)))
        assertNull(ApkDigest.expectedSha256("sha256:" + "a".repeat(65)))
        assertNull(ApkDigest.expectedSha256("sha256:" + "g".repeat(64)))
        assertNull(ApkDigest.expectedSha256("sha256:"))
    }

    @Test
    fun `hashes bytes with the known sha256 vectors`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            ApkDigest.sha256Hex("abc".toByteArray()),
        )
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            ApkDigest.sha256Hex(ByteArray(0)),
        )
    }

    @Test
    fun `hashes a file with the same rule as bytes`() {
        val file = Files.createTempFile("jm3-digest", ".apk").toFile()
        try {
            file.writeText("abc")
            assertEquals(ApkDigest.sha256Hex("abc".toByteArray()), ApkDigest.sha256Hex(file))
        } finally {
            file.delete()
        }
    }

    @Test
    fun `verify skips when the asset has no usable digest`() {
        val file = Files.createTempFile("jm3-digest", ".apk").toFile()
        try {
            file.writeText("abc")
            assertEquals(DigestCheck.Skipped, ApkDigest.verify(file, null))
            assertEquals(DigestCheck.Skipped, ApkDigest.verify(file, "md5:$hex64"))
            assertEquals(DigestCheck.Skipped, ApkDigest.verify(file, "sha256:oops"))
        } finally {
            file.delete()
        }
    }

    @Test
    fun `verify matches and mismatches with the expected digest`() {
        val file = Files.createTempFile("jm3-digest", ".apk").toFile()
        try {
            file.writeText("abc")
            val actual = ApkDigest.sha256Hex("abc".toByteArray())
            assertEquals(DigestCheck.Matched, ApkDigest.verify(file, "sha256:$actual"))

            val wrong = "f".repeat(64)
            assertEquals(
                DigestCheck.Mismatched(expected = wrong, actual = actual),
                ApkDigest.verify(file, "sha256:$wrong"),
            )
        } finally {
            file.delete()
        }
    }

    @Test
    fun `verify by precomputed hash treats null expected as skipped`() {
        val actual = "a".repeat(64)
        assertEquals(DigestCheck.Skipped, ApkDigest.verify(expectedSha256 = null, actualSha256 = actual))
        assertEquals(DigestCheck.Matched, ApkDigest.verify(expectedSha256 = actual, actualSha256 = actual))
        assertEquals(
            DigestCheck.Mismatched(expected = "b".repeat(64), actual = actual),
            ApkDigest.verify(expectedSha256 = "b".repeat(64), actualSha256 = actual),
        )
    }

    @Test
    fun `mismatch message names both digests`() {
        val expected = "0123456789ab" + "c".repeat(52)
        val actual = "fedcba987654" + "d".repeat(52)
        val message = ApkDigest.mismatchMessage(expected, actual)
        assertTrue(message.contains("SHA-256"), message)
        assertTrue(message.contains("0123456789ab"), message)
        assertTrue(message.contains("fedcba987654"), message)
        assertTrue(message.contains("失败"), message)
    }
}
