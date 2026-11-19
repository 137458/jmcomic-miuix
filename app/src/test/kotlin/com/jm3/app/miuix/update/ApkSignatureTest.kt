package com.jm3.app.miuix.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApkSignatureTest {

    @Test
    fun `证书摘要为小写hex且长度固定`() {
        val empty = ByteArray(0)
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            ApkSignature.certSha256(empty),
        )
        val sample = ApkSignature.certSha256(byteArrayOf(1, 2, 3))
        assertEquals(64, sample.length)
        assertEquals(sample.lowercase(), sample)
        // 三角验证：不同输入必须得到不同摘要
        assertTrue(sample != ApkSignature.certSha256(byteArrayOf(1, 2, 4)))
    }

    @Test
    fun `签名集合有交集即同源`() {
        assertTrue(ApkSignature.sameSigner(setOf("a", "b"), setOf("b", "c")))
        assertTrue(ApkSignature.sameSigner(setOf("a"), setOf("a")))
    }

    @Test
    fun `签名集合无交集或为空都判不一致`() {
        assertFalse(ApkSignature.sameSigner(setOf("a"), setOf("b")))
        assertFalse(ApkSignature.sameSigner(emptySet(), setOf("a")))
        assertFalse(ApkSignature.sameSigner(setOf("a"), emptySet()))
        assertFalse(ApkSignature.sameSigner(emptySet(), emptySet()))
    }
}
