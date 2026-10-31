package com.jm3.core

import com.jm3.core.model.CheckInDay
import com.jm3.core.model.DailyCheckInStatus
import io.github.jukomu.jmcomic.api.model.JmDailyCheckInRecordItem
import io.github.jukomu.jmcomic.api.model.JmDailyCheckInStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 签到日历解析：record 的 date 字段在真机返回值里出现过 "01" 与 "2026-10-01" 两种写法，
 * 必须两种都能解析出当月的日号，UI 才能判断"今天是否已签到"。
 */
class DailyCheckInTodayTest {

    /** JmDailyCheckInStatus 记录组件顺序：dailyId,threeDaysCoin,threeDaysExp,sevenDaysCoin,sevenDaysExp,eventName,backgroundPc,backgroundPhone,currentProgress,record。 */
    private fun checkIn(record: List<List<JmDailyCheckInRecordItem>>?): JmDailyCheckInStatus =
        JmDailyCheckInStatus(1, "10", "20", "30", "40", "每日签到", "pc.jpg", "phone.jpg", "2/7", record)

    private fun record(date: String, signed: Boolean? = null, bonus: Boolean = false) =
        JmDailyCheckInRecordItem(date, signed, bonus)

    @Test
    fun 签到日历_两位日号解析为当月日号() {
        val got = checkIn(listOf(listOf(record("31", signed = true)))).toDailyCheckInStatus()
        assertEquals(listOf(CheckInDay(31, signed = true, bonus = false)), got.days)
        assertTrue(got.isSignedOn(31))
    }

    @Test
    fun 签到日历_完整日期解析为当月日号() {
        val got = checkIn(listOf(listOf(record("2026-10-31", signed = true)))).toDailyCheckInStatus()
        assertEquals(listOf(CheckInDay(31, signed = true, bonus = false)), got.days)
        assertTrue(got.isSignedOn(31))
    }

    @Test
    fun 签到日历_当天未签到时isSignedOn为假() {
        val got = checkIn(
            listOf(listOf(record("30", signed = true), record("31", signed = false))),
        ).toDailyCheckInStatus()
        assertFalse(got.isSignedOn(31))
        assertTrue(got.isSignedOn(30))
    }

    @Test
    fun 签到日历_未来日期保留在日历里但不算已签() {
        val got = checkIn(
            listOf(listOf(record("01", signed = null), record("02", signed = true, bonus = true))),
        ).toDailyCheckInStatus()
        assertEquals(listOf(CheckInDay(1, false, false), CheckInDay(2, true, true)), got.days)
        assertEquals(1, got.signedDays)
        assertEquals(2, got.totalDays)
    }

    @Test
    fun 签到日历_无法解析的日期被跳过且不影响天数统计() {
        val got = checkIn(
            listOf(listOf(record("", signed = true), record("abc", signed = true), record("03", signed = true))),
        ).toDailyCheckInStatus()
        assertEquals(listOf(CheckInDay(3, true, false)), got.days)
        // 天数统计沿用 record 扁平计数，未因解析失败而少算
        assertEquals(3, got.signedDays)
        assertEquals(3, got.totalDays)
    }

    @Test
    fun 签到日历_越界日号被跳过() {
        val got = checkIn(listOf(listOf(record("32", signed = true), record("0", signed = true)))).toDailyCheckInStatus()
        assertEquals(emptyList(), got.days)
    }

    @Test
    fun 签到进度按已签天数占比计算() {
        val got = checkIn(
            listOf(listOf(record("01", signed = true), record("02", signed = true), record("03", signed = false), record("04", signed = false))),
        ).toDailyCheckInStatus()
        assertEquals(0.5f, got.progress)
    }

    @Test
    fun 签到进度在无天数时为0() {
        assertEquals(0f, DailyCheckInStatus(1, "", "", 0, 0).progress)
    }
}
