package com.jm3.core.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ApkAssetSelectorTest {

    private fun asset(
        name: String,
        url: String = "https://example.com/" + name,
        size: Long = 2048L,
    ) = ReleaseAsset(name = name, size = size, downloadUrl = url)

    @Test
    fun 优先选择universal包() {
        val selected = ApkAssetSelector.select(
            listOf(
                asset("jm3-v1.2.0-arm64-v8a.apk"),
                asset("jm3-v1.2.0-universal.apk"),
                asset("mapping.txt"),
            ),
        )
        assertEquals("jm3-v1.2.0-universal.apk", selected?.name)
    }

    @Test
    fun universal关键字大小写不敏感() {
        val selected = ApkAssetSelector.select(
            listOf(
                asset("jm3-v1.2.0-armeabi-v7a.apk"),
                asset("jm3-v1.2.0-UNIVERSAL.apk"),
            ),
        )
        assertEquals("jm3-v1.2.0-UNIVERSAL.apk", selected?.name)
        assertEquals(
            "jm3-v1.2.0-Universal.apk",
            ApkAssetSelector.select(listOf(asset("jm3-v1.2.0-Universal.apk")))?.name,
        )
    }

    @Test
    fun 没有universal时取第一个apk() {
        val selected = ApkAssetSelector.select(
            listOf(
                asset("mapping.txt"),
                asset("jm3-v1.2.0-arm64-v8a.apk"),
                asset("jm3-v1.2.0-armeabi-v7a.apk"),
            ),
        )
        assertEquals("jm3-v1.2.0-arm64-v8a.apk", selected?.name)
    }

    @Test
    fun 只有mapping或压缩包时返回null() {
        assertNull(ApkAssetSelector.select(listOf(asset("mapping.txt"), asset("symbols.zip"))))
        assertNull(ApkAssetSelector.select(listOf(asset("jm3-v1.2.0.apk.zip"))))
        assertNull(ApkAssetSelector.select(emptyList()))
    }

    @Test
    fun apk后缀大小写都算安装包() {
        assertEquals("jm3-v1.2.0.APK", ApkAssetSelector.select(listOf(asset("jm3-v1.2.0.APK")))?.name)
        assertEquals("jm3-v1.2.0.Apk", ApkAssetSelector.select(listOf(asset("jm3-v1.2.0.Apk")))?.name)
    }

    @Test
    fun 下载地址为空白的项被排除() {
        assertNull(ApkAssetSelector.select(listOf(asset("jm3-v1.2.0-universal.apk", url = "   "))))
        assertNull(ApkAssetSelector.select(listOf(asset("jm3-v1.2.0.apk", url = ""))))

        val selected = ApkAssetSelector.select(
            listOf(
                asset("jm3-v1.2.0-universal.apk", url = "  "),
                asset("jm3-v1.2.0-arm64-v8a.apk"),
            ),
        )
        assertEquals("jm3-v1.2.0-arm64-v8a.apk", selected?.name)
    }
}
