package com.jm3.app.miuix.ui.mine

import androidx.compose.runtime.mutableStateOf
import com.jm3.app.miuix.ui.CheckInViewState
import com.jm3.core.model.CheckInDay
import com.jm3.core.model.DailyCheckInStatus
import com.jm3.core.model.Jm3User
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 「我的」骨架的行为测试：签到行推导、入口守卫、签到动作、登出编排。
 *
 * 三个外部端口（签到、账号、提示）都由用例摆放结局，因此不需要 Android、不需要 Miuix；
 * 日期用固定的 [FIXED_TODAY] 注入，判定「今天是否已签到」不随真实时间漂移。
 */
private val FIXED_TODAY: LocalDate = LocalDate.of(2025, 3, 10)

/** 假的签到端口：状态由用例摆放，动作只记调用。 */
private class FakeCheckInPort(initial: CheckInViewState = CheckInViewState()) : MineCheckInPort {
    override var viewState: CheckInViewState = initial
    var reloads = 0
    val checkedIn = mutableListOf<DailyCheckInStatus>()

    override fun reload() {
        reloads++
    }

    override fun checkIn(status: DailyCheckInStatus) {
        checkedIn += status
    }
}

/** 假的账号端口：可以注入登出失败。 */
private class FakeAccountPort : MineAccountPort {
    var failure: Throwable? = null
    var logouts = 0
    var cleared = 0

    override suspend fun logout() {
        logouts++
        failure?.let { throw it }
    }

    override fun clearSessionCache() {
        cleared++
    }
}

/** 假的提示通道：只记被要求弹的文案。 */
private class RecordingMineNotice : MineNotice {
    val messages = mutableListOf<String>()

    override suspend fun show(message: String) {
        messages += message
    }
}

private fun testUser(uid: String = "uid-1"): Jm3User = Jm3User(
    uid = uid,
    username = "鲸娘",
    email = null,
    emailVerified = false,
    photoUrl = null,
    coin = 120,
    albumFavorites = 0,
    maxAlbumFavorites = 0,
    level = 5,
    levelName = "萌新",
    nextLevelExp = 100L,
    currentExp = 30L,
    expPercent = 30,
)

private fun checkInStatus(
    eventName: String = "活动",
    signedDays: Int = 10,
    totalDays: Int = 30,
    days: List<CheckInDay> = emptyList(),
): DailyCheckInStatus = DailyCheckInStatus(
    dailyId = 7,
    eventName = eventName,
    currentProgress = "",
    signedDays = signedDays,
    totalDays = totalDays,
    days = days,
)

/**
 * 状态机与它的替身。
 *
 * 账号/未读/在途下载数走三份快照 State，测试可以随时改值；
 * 跳转用 [opened] 记下入口名，登录用 [loginCalls] 计数。
 */
private class MineHarness(
    scope: CoroutineScope,
    logoutFailureNotice: String? = null,
) {
    val user = mutableStateOf<Jm3User?>(null)
    val unread = mutableStateOf(0)
    val downloads = mutableStateOf(0)
    val checkIn = FakeCheckInPort()
    val account = FakeAccountPort()
    val notice = RecordingMineNotice()
    val opened = mutableListOf<String>()
    var loginCalls = 0

    val subject = MineScaffoldState(
        scope = scope,
        account = account,
        notice = notice,
        checkInPort = checkIn,
        fields = MineScaffoldFields(
            user = { user.value },
            unread = { unread.value },
            inFlightDownloads = { downloads.value },
        ),
        onOpenLogin = { loginCalls++ },
        logoutFailureNotice = logoutFailureNotice,
        today = { FIXED_TODAY },
    )

    fun signedIn() {
        user.value = testUser()
    }

    fun enter(entry: MineEntry) {
        subject.enter(entry) { opened += entry.name }
    }
}

class MineScaffoldTest {

    @Test
    fun `初始态未登录时既不显示签到卡也不打开登出弹窗`() = runTest {
        val harness = MineHarness(this)

        assertFalse(harness.subject.showsCheckIn)
        assertNull(harness.subject.checkInRow)
        assertFalse(harness.subject.logoutDialogVisible)
        assertNull(harness.subject.user)
    }

    @Test
    fun `已登录时显示签到卡但还没有签到状态就不出行`() = runTest {
        val harness = MineHarness(this)
        harness.signedIn()

        assertTrue(harness.subject.showsCheckIn)
        assertNull(harness.subject.checkInRow)
        assertNull(mineCheckInRow(CheckInViewState(), FIXED_TODAY))
    }

    @Test
    fun `未登录但签到会话失效时仍显示签到卡`() = runTest {
        val harness = MineHarness(this)
        harness.checkIn.viewState = CheckInViewState(expiredMessage = "登录已失效，请重新登录")

        assertNull(harness.subject.user)
        assertTrue(harness.subject.showsCheckIn)
    }

    @Test
    fun `未登录点需要登录的入口先去登录而不跳转`() = runTest {
        val harness = MineHarness(this)

        harness.enter(MineEntry.Profile)
        harness.enter(MineEntry.Notifications)

        assertEquals(2, harness.loginCalls)
        assertTrue(harness.opened.isEmpty())
    }

    @Test
    fun `未登录点不需要登录的入口直接跳转`() = runTest {
        val harness = MineHarness(this)

        harness.enter(MineEntry.Favorites)
        harness.enter(MineEntry.Settings)

        assertEquals(0, harness.loginCalls)
        assertEquals(listOf("Favorites", "Settings"), harness.opened)
    }

    @Test
    fun `已登录时所有入口都直接跳转`() = runTest {
        val harness = MineHarness(this)
        harness.signedIn()

        MineEntry.entries.forEach { harness.enter(it) }

        assertEquals(0, harness.loginCalls)
        assertEquals(MineEntry.entries.map { it.name }, harness.opened)
    }

    @Test
    fun `未签到时行文案为立即签到且可点`() = runTest {
        val harness = MineHarness(this)
        harness.signedIn()
        harness.checkIn.viewState = CheckInViewState(
            status = checkInStatus(
                days = listOf(
                    CheckInDay(dayOfMonth = 1, signed = true, bonus = false),
                    CheckInDay(dayOfMonth = 2, signed = false, bonus = false),
                    CheckInDay(dayOfMonth = 3, signed = false, bonus = true),
                ),
            ),
        )

        val row = harness.subject.checkInRow
        requireNotNull(row)
        assertEquals("立即签到", row.title)
        assertEquals("活动 · 签到可得金币与经验", row.summary)
        assertTrue(row.enabled)
        assertFalse(row.signedToday)
        assertTrue(row.showsProgress)
        assertEquals("10/30 天 · 33%", row.display.progressLabel)
        assertEquals(2, row.display.missedDays)
    }

    @Test
    fun `今日已签到时行为今日已签到且不可点`() = runTest {
        val harness = MineHarness(this)
        harness.signedIn()
        harness.checkIn.viewState = CheckInViewState(
            status = checkInStatus(days = listOf(CheckInDay(dayOfMonth = 10, signed = true, bonus = false))),
        )

        val row = harness.subject.checkInRow
        requireNotNull(row)
        assertEquals("今日已签到", row.title)
        assertEquals("活动 · 今天的奖励已入账", row.summary)
        assertTrue(row.signedToday)
        assertFalse(row.enabled)
    }

    @Test
    fun `签到进行中时行为签到中且不可点`() = runTest {
        val harness = MineHarness(this)
        harness.signedIn()
        harness.checkIn.viewState = CheckInViewState(status = checkInStatus(), busy = true)

        val row = harness.subject.checkInRow
        requireNotNull(row)
        assertEquals("签到中…", row.title)
        assertTrue(row.busy)
        assertFalse(row.enabled)
    }

    @Test
    fun `签到没有天数时不显示进度`() = runTest {
        val harness = MineHarness(this)
        harness.signedIn()
        harness.checkIn.viewState = CheckInViewState(status = checkInStatus(signedDays = 0, totalDays = 0))

        val row = harness.subject.checkInRow
        requireNotNull(row)
        assertFalse(row.showsProgress)
        assertEquals("0/0 天 · 0%", row.display.progressLabel)
    }

    @Test
    fun `点击签到只在已有签到状态时下发`() = runTest {
        val harness = MineHarness(this)
        harness.signedIn()

        harness.subject.checkIn()
        assertTrue(harness.checkIn.checkedIn.isEmpty())

        val status = checkInStatus()
        harness.checkIn.viewState = CheckInViewState(status = status)
        harness.subject.checkIn()

        assertEquals(listOf(status), harness.checkIn.checkedIn)
    }

    @Test
    fun `重载签到委托给签到端口`() = runTest {
        val harness = MineHarness(this)

        harness.subject.reloadCheckIn()

        assertEquals(1, harness.checkIn.reloads)
    }

    @Test
    fun `登出成功作废账号缓存并提示已退出登录`() = runTest {
        val harness = MineHarness(this)

        harness.subject.logout()

        assertEquals(1, harness.account.logouts)
        assertEquals(1, harness.account.cleared)
        assertEquals(listOf("已退出登录"), harness.notice.messages)
    }

    @Test
    fun `登出失败且配置了提示时不作废缓存并提示该文案`() = runTest {
        val harness = MineHarness(this, logoutFailureNotice = "退出登录失败，请稍后重试")
        harness.account.failure = IllegalStateException("network down")

        harness.subject.logout()

        assertEquals(1, harness.account.logouts)
        assertEquals(0, harness.account.cleared)
        assertEquals(listOf("退出登录失败，请稍后重试"), harness.notice.messages)
    }

    @Test
    fun `登出失败且未配置提示时抛出并不作废缓存`() = runTest {
        val harness = MineHarness(this)
        harness.account.failure = IllegalStateException("network down")

        assertFailsWith<IllegalStateException> { harness.subject.logout() }

        assertEquals(1, harness.account.logouts)
        assertEquals(0, harness.account.cleared)
        assertTrue(harness.notice.messages.isEmpty())
    }

    @Test
    fun `确认登出先收起弹窗再执行登出`() = runTest {
        val harness = MineHarness(this)
        harness.subject.askLogout()
        assertTrue(harness.subject.logoutDialogVisible)

        harness.subject.submitLogout()
        assertFalse(harness.subject.logoutDialogVisible)
        assertEquals(0, harness.account.logouts)

        advanceUntilIdle()

        assertEquals(1, harness.account.logouts)
        assertEquals(listOf("已退出登录"), harness.notice.messages)
    }

    @Test
    fun `取消登出只收起弹窗`() = runTest {
        val harness = MineHarness(this)
        harness.subject.askLogout()

        harness.subject.dismissLogout()
        advanceUntilIdle()

        assertFalse(harness.subject.logoutDialogVisible)
        assertEquals(0, harness.account.logouts)
    }

    @Test
    fun `未读数与在途下载数直接取自注入字段`() = runTest {
        val harness = MineHarness(this)
        harness.unread.value = 3
        harness.downloads.value = 2

        assertEquals(3, harness.subject.unread)
        assertEquals(2, harness.subject.inFlightDownloads)
    }
}
