package com.jm3.app.miuix.data.download

import com.jm3.core.model.PageImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 离线页列表持久化格式契约（DownloadPagesCodec.kt:10-16）。
 *
 * 两条业务规则：编码时 photoId 以入参为准、albumId/title 留空（以记录表为准）；
 * 解码遇到任何损坏形态都返回 null，让调用方回退在线取页。
 */
class DownloadPagesCodecTest {

    private fun page(index: Int, suffix: String = ""): PageImage = PageImage(
        photoId = "1051",
        scrambleId = "sc$index",
        index = index,
        filename = "0000$index$suffix.jpg",
        url = "https://img.example/media/photos/1051/0000$index$suffix.jpg",
    )

    @Test
    fun `编码再解码保留页清单与photoId`() {
        val images = listOf(page(1), page(2, "-h"))

        val decoded = decodePhotoPages(encodePhotoPages("1051", images))

        assertEquals(images, decoded?.images)
        assertEquals("1051", decoded?.photoId)
    }

    @Test
    fun `albumId与title以记录表为准编码为空`() {
        val decoded = decodePhotoPages(encodePhotoPages("1051", listOf(page(1))))

        assertEquals("", decoded?.albumId)
        assertEquals("", decoded?.title)
    }

    @Test
    fun `photoId以入参为准而不是页内字段`() {
        val images = listOf(page(1).copy(photoId = "9999"))

        assertEquals("1051", decodePhotoPages(encodePhotoPages("1051", images))?.photoId)
    }

    @Test
    fun `空页列表往返为空表`() {
        assertEquals(emptyList(), decodePhotoPages(encodePhotoPages("1051", emptyList()))?.images)
    }

    @Test
    fun `多余字段被忽略`() {
        val json = """{"photoId":"1051","albumId":"","title":"","images":[],"extra":123,"legacy":{"a":1}}"""

        assertEquals("1051", decodePhotoPages(json)?.photoId)
        assertEquals(emptyList(), decodePhotoPages(json)?.images)
    }

    @Test
    fun `截断的JSON返回null`() {
        val json = encodePhotoPages("1051", listOf(page(1), page(2)))

        assertNull(decodePhotoPages(json.dropLast(12)))
    }

    @Test
    fun `非JSON文本与空串返回null`() {
        assertNull(decodePhotoPages("not json"))
        assertNull(decodePhotoPages(""))
    }

    @Test
    fun `缺少必填字段返回null`() {
        assertNull(decodePhotoPages("""{"photoId":"1051"}"""))
        assertNull(decodePhotoPages("""{"photoId":"1051","albumId":"","title":""}"""))
    }
}
