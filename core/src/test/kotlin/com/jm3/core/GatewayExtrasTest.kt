package com.jm3.core

import com.jm3.core.model.CheckInDay
import com.jm3.core.model.DailyCheckInStatus
import io.github.jukomu.jmcomic.api.model.JmAlbumMeta
import io.github.jukomu.jmcomic.api.model.JmCategoryMeta
import io.github.jukomu.jmcomic.api.model.JmDailyCheckInRecordItem
import io.github.jukomu.jmcomic.api.model.JmDailyCheckInStatus
import io.github.jukomu.jmcomic.api.model.JmFavoritePage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// 库为 Java record，构造器只支持位置实参。
class GatewayExtrasTest {

    private val cat = JmCategoryMeta("0", "分类")
    private val subCat = JmCategoryMeta("1", "子分类")

    private fun albumMeta(id: String = "1", title: String = "标题"): JmAlbumMeta =
        JmAlbumMeta(id, title, listOf("作者A"), listOf("tag1"), null, "cover.jpg", cat, subCat)

    /** JmFavoritePage 记录组件顺序：folderName,folderId,currentPage,totalItems,totalPages,content,folderList。 */
    private fun favoritePage(
        folderName: String? = "我的收藏",
        folderId: Int = 12,
        currentPage: Int = 2,
        totalItems: Int = 37,
        totalPages: Int = 4,
        content: List<JmAlbumMeta>? = listOf(albumMeta()),
        folderList: Map<String, String>? = mapOf("0" to "全部", "12" to "我的收藏"),
    ): JmFavoritePage = JmFavoritePage(folderName, folderId, currentPage, totalItems, totalPages, content, folderList)

    /** JmDailyCheckInStatus 记录组件顺序：dailyId,threeDaysCoin,threeDaysExp,sevenDaysCoin,sevenDaysExp,eventName,backgroundPc,backgroundPhone,currentProgress,record。 */
    private fun checkIn(
        dailyId: Int = 7,
        eventName: String? = "每日签到",
        currentProgress: String? = "2/7",
        record: List<List<JmDailyCheckInRecordItem>>? = emptyList(),
    ): JmDailyCheckInStatus = JmDailyCheckInStatus(
        dailyId, "10", "20", "30", "40", eventName, "pc.jpg", "phone.jpg", currentProgress, record,
    )

    private fun record(date: String, signed: Boolean? = null, bonus: Boolean = false) =
        JmDailyCheckInRecordItem(date, signed, bonus)

    private class FakeDomainControls(
        private val used: String? = null,
        private val latency: Map<String, Int> = emptyMap(),
        private val domains: Set<String> = emptySet(),
        private val cover: String? = null,
    ) : DomainControls {
        var reprobeCount = 0
        var closeCount = 0

        override fun usedDomain(): String? = used

        override fun candidateDomains(): Set<String> = domains

        override fun coverHost(): String? = cover

        override fun domainLatency(): Map<String, Int> = latency

        override fun reprobeDomains() {
            reprobeCount++
        }

        override fun close() {
            closeCount++
        }
    }

    private fun gateway(controls: DomainControls): Jm3GatewayImpl = Jm3GatewayImpl(
        clientProvider = { error("域名能力测试不应触达真实客户端") },
        domainControlsProvider = { controls },
    )

    @Test
    fun 收藏页_映射_透传收藏夹与所属夹号() {
        val got = favoritePage().toFavoritePage()
        assertEquals(2, got.currentPage)
        assertEquals(4, got.totalPages)
        assertEquals(12, got.folderId)
        assertEquals(mapOf("0" to "全部", "12" to "我的收藏"), got.folders)
        assertEquals(listOf("1"), got.items.map { it.id })
        assertEquals(listOf("标题"), got.items.map { it.title })
    }

    @Test
    fun 收藏页_映射_库未返回收藏夹时为空表() {
        val got = favoritePage(content = null, folderList = null).toFavoritePage()
        assertEquals(emptyMap(), got.folders)
        assertEquals(emptyList(), got.items)
    }

    @Test
    fun 收藏页_映射_封面地址由解析器生成() {
        val got = favoritePage(content = listOf(albumMeta(id = "9"))).toFavoritePage { meta ->
            "https://img.example/media/albums/${meta.id()}.jpg"
        }
        assertEquals("https://img.example/media/albums/9.jpg", got.items.single().coverUrl)
    }

    @Test
    fun 收藏页_映射_未传封面解析器时封面为null() {
        assertNull(favoritePage().toFavoritePage().items.single().coverUrl)
    }

    @Test
    fun 签到_映射_空记录时统计为零() {
        val got = checkIn(record = emptyList()).toDailyCheckInStatus()
        assertEquals(DailyCheckInStatus(7, "每日签到", "2/7", 0, 0), got)
    }

    @Test
    fun 签到_映射_混合签到与空标记只计明确签到() {
        val got = checkIn(
            record = listOf(
                listOf(record("2026-10-01", signed = true), record("2026-10-02", signed = false)),
                listOf(record("2026-10-03", signed = null), record("2026-10-04", signed = true, bonus = true)),
            ),
        ).toDailyCheckInStatus()
        assertEquals(2, got.signedDays)
        assertEquals(4, got.totalDays)
        assertEquals("2/7", got.currentProgress)
        assertEquals("每日签到", got.eventName)
    }

    @Test
    fun 签到_映射_全部签到_第二组数据() {
        val got = checkIn(
            dailyId = 21,
            eventName = null,
            currentProgress = null,
            record = listOf(
                listOf(record("1", signed = true), record("2", signed = true)),
                listOf(record("3", signed = true)),
            ),
        ).toDailyCheckInStatus()
        assertEquals(
            DailyCheckInStatus(
                21, "", "", 3, 3,
                days = listOf(
                    CheckInDay(1, signed = true, bonus = false),
                    CheckInDay(2, signed = true, bonus = false),
                    CheckInDay(3, signed = true, bonus = false),
                ),
            ),
            got,
        )
    }

    @Test
    fun 签到_映射_记录列表为null时统计为零() {
        val got = checkIn(record = null).toDailyCheckInStatus()
        assertEquals(0, got.signedDays)
        assertEquals(0, got.totalDays)
    }

    @Test
    fun 收藏夹查询_指定夹号与页码() {
        val q = favoritesQuery(folderId = 12, page = 3)
        assertEquals(12, q.getFolderId())
        assertEquals(3, q.getPage())
    }

    @Test
    fun 收藏夹查询_全部收藏夹号为0() {
        val q = favoritesQuery(folderId = 0, page = 1)
        assertEquals(0, q.getFolderId())
        assertEquals(1, q.getPage())
    }

    @Test
    fun 域名_透传当前使用的域名() {
        assertEquals("a.example", gateway(FakeDomainControls(used = "a.example")).usedDomain())
    }

    @Test
    fun 域名_空白串归一为null() {
        assertNull(gateway(FakeDomainControls(used = "")).usedDomain())
        assertNull(gateway(FakeDomainControls(used = null)).usedDomain())
    }

    @Test
    fun 域名_透传延迟表() {
        val latency = mapOf("a.example" to 120, "b.example" to 340)
        assertEquals(latency, gateway(FakeDomainControls(latency = latency)).domainLatency())
    }

    @Test
    fun 封面域名_取库的线路裁决而不是登录域() {
        assertEquals("cdn.example", gateway(FakeDomainControls(cover = "cdn.example")).imageHost())
        assertEquals("", gateway(FakeDomainControls()).imageHost())
    }

    @Test
    fun 域名_重探测与关闭各调用一次() {
        val fake = FakeDomainControls(used = "a.example")
        val g = gateway(fake)
        g.reprobeDomains()
        g.close()
        assertEquals(1, fake.reprobeCount)
        assertEquals(1, fake.closeCount)
    }
}
