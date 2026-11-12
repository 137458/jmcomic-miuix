// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.page

import com.jm3.core.history.HistoryEntry
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 阅读历史按作品折叠契约：保留首次出现的作品顺序（对应接口返回的最近阅读时间倒序），
 * 同一作品的多章阅读记录折叠为最新一章并累计已读章数。
 */
class CouiHistoryGroupingTest {

    private fun entry(photoId: String, albumId: String, updatedAt: Long) = HistoryEntry(
        photoId = photoId,
        albumId = albumId,
        title = "title-$photoId",
        coverUrl = null,
        updatedAt = updatedAt,
    )

    @Test
    fun 空列表聚合后为空() {
        assertEquals(emptyList(), emptyList<HistoryEntry>().couiGroupByAlbum())
    }

    @Test
    fun 同作品多条记录保持首现顺序并统计章数() {
        val entries = listOf(
            entry(photoId = "p3", albumId = "a1", updatedAt = 300L),
            entry(photoId = "p2", albumId = "a2", updatedAt = 200L),
            entry(photoId = "p1", albumId = "a1", updatedAt = 100L),
            entry(photoId = "p4", albumId = "a3", updatedAt = 50L),
            entry(photoId = "p0", albumId = "a1", updatedAt = 10L),
        )

        val grouped = entries.couiGroupByAlbum()

        assertEquals(listOf("a1", "a2", "a3"), grouped.map { it.albumId })
        assertEquals(listOf("p3", "p2", "p4"), grouped.map { it.latest.photoId })
        assertEquals(listOf(3, 1, 1), grouped.map { it.chapters })
    }
}
