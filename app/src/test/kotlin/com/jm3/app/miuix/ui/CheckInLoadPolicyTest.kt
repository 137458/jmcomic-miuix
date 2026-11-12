package com.jm3.app.miuix.ui

import com.jm3.core.LOGIN_EXPIRED_MESSAGE
import com.jm3.core.model.CheckInDay
import com.jm3.core.model.DailyCheckInStatus
import com.jm3.core.session.AuthCall
import com.jm3.core.session.SessionOutcome
import com.jm3.core.session.StartupGate
import com.jm3.core.settings.InMemorySettingsRepository
import kotlinx.coroutines.Dispatchers
import io.github.jukomu.jmcomic.api.exception.JmClientInitializationException
import io.github.jukomu.jmcomic.api.exception.NetworkException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class CheckInLoadPolicyTest {

    @Test
    fun `首次失败后按 1s 与 3s 退避重试直到成功`() = runBlocking {
        val queue = ArrayDeque<SessionOutcome<String>>(
            listOf(
                SessionOutcome.Failed("network"),
                SessionOutcome.Failed("network"),
                SessionOutcome.Ok("signed"),
            ),
        )
        val waits = mutableListOf<Long>()
        var attempts = 0

        val result = retryCheckInLoad(
            attempt = {
                attempts++
                queue.removeFirst()
            },
            sleep = { waits += it },
        )

        assertEquals(SessionOutcome.Ok("signed"), result)
        assertEquals(3, attempts)
        assertEquals(listOf(1_000L, 3_000L), waits)
    }

    @Test
    fun `三次都失败才返回最后一次失败`() = runBlocking {
        val waits = mutableListOf<Long>()
        var attempts = 0

        val result = retryCheckInLoad(
            attempt = {
                attempts++
                SessionOutcome.Failed("attempt-$attempts")
            },
            sleep = { waits += it },
        )

        assertEquals("attempt-3", (result as SessionOutcome.Failed).message)
        assertEquals(3, attempts)
        assertEquals(listOf(1_000L, 3_000L), waits)
    }

    @Test
    fun `会话失效不重试也不等待`() = runBlocking {
        var attempts = 0

        val result = retryCheckInLoad(
            attempt = {
                attempts++
                SessionOutcome.Expired
            },
            sleep = { error("失效是终态，不应进入退避等待") },
        )

        assertEquals(SessionOutcome.Expired, result)
        assertEquals(1, attempts)
    }

    @Test
    fun `网关已就绪时不再轮询等待`() = runBlocking {
        var checks = 0

        val ready = awaitGatewayReady(
            ready = {
                checks++
                true
            },
            timeoutMs = 1_000L,
            pollMs = 250L,
            sleep = { error("已就绪不应等待") },
        )

        assertTrue(ready)
        assertEquals(1, checks)
    }

    @Test
    fun `网关在等待窗口内就绪后立即放行`() = runBlocking {
        var checks = 0
        val sleeps = mutableListOf<Long>()

        val ready = awaitGatewayReady(
            ready = {
                checks++
                checks >= 3
            },
            timeoutMs = 1_000L,
            pollMs = 250L,
            sleep = { sleeps += it },
        )

        assertTrue(ready)
        assertEquals(3, checks)
        assertEquals(listOf(250L, 250L), sleeps)
    }

    @Test
    fun `网关始终未就绪时按上限返回 false`() = runBlocking {
        var checks = 0
        val sleeps = mutableListOf<Long>()

        val ready = awaitGatewayReady(
            ready = {
                checks++
                false
            },
            timeoutMs = 1_000L,
            pollMs = 250L,
            sleep = { sleeps += it },
        )

        assertFalse(ready)
        assertEquals(4, sleeps.size)
        assertEquals(5, checks)
    }

    @Test
    fun `超时异常归类为网络超时`() {
        val failure = SessionOutcome.Failed("read timed out", SocketTimeoutException("read timed out"))

        assertEquals("网络超时，请稍后重试", checkInFailureCopy(failure, FALLBACK))
    }

    @Test
    fun `网络异常归类为网络连接失败`() {
        assertEquals(
            "网络连接失败，请检查网络后重试",
            checkInFailureCopy(SessionOutcome.Failed("connect failed", NetworkException("connect failed")), FALLBACK),
        )
        assertEquals(
            "网络连接失败，请检查网络后重试",
            checkInFailureCopy(SessionOutcome.Failed("unknown host", UnknownHostException("host")), FALLBACK),
        )
    }

    @Test
    fun `异常链上的网络错误也要归类`() {
        val wrapped = RuntimeException("wrapped", NetworkException("connect failed"))

        assertEquals(
            "网络连接失败，请检查网络后重试",
            checkInFailureCopy(SessionOutcome.Failed("wrapped", wrapped), FALLBACK),
        )
    }

    @Test
    fun `初始化异常优先于其包裹的网络异常`() {
        val init = JmClientInitializationException("all domains dead", SocketTimeoutException("timeout"))

        assertEquals(
            "客户端尚未就绪，请稍后重试",
            checkInFailureCopy(SessionOutcome.Failed("all domains dead", init), FALLBACK),
        )
    }

    @Test
    fun `登录失效文案归类为重新登录`() {
        val failure = SessionOutcome.Failed("""{"code":401,"errorMsg":"請先登入會員"}""")

        assertEquals(LOGIN_EXPIRED_MESSAGE, checkInFailureCopy(failure, FALLBACK))
    }

    @Test
    fun `未知异常回落到入口自己的通用文案`() {
        assertEquals(FALLBACK, checkInFailureCopy(SessionOutcome.Failed("HTTP 502"), FALLBACK))
        assertEquals(FALLBACK, checkInFailureCopy(SessionOutcome.Failed("HTTP 502", IllegalStateException("HTTP 502")), FALLBACK))
        assertEquals(FALLBACK, checkInFailureCopy(SessionOutcome.Failed(null), FALLBACK))
    }

    @Test
    fun `回读失败时保留现有状态且不把今天记为已签`() {
        val todayDay = 15
        val current = checkInStatus(
            signedDays = 9,
            days = listOf(CheckInDay(dayOfMonth = todayDay, signed = false, bonus = false)),
        )

        val result = resolveCheckInResult(current = current, refreshed = null, todayDayOfMonth = todayDay)

        assertSame(current, result.status)
        assertFalse(result.markToday)
        assertFalse(result.status?.isSignedOn(todayDay) == true)
        assertEquals("签到已提交，暂时无法确认状态", result.message)
    }

    @Test
    fun `回读失败且没有现存状态时不伪造签到结果`() {
        val result = resolveCheckInResult(current = null, refreshed = null, todayDayOfMonth = 15)

        assertNull(result.status)
        assertFalse(result.markToday)
        assertEquals("签到已提交，暂时无法确认状态", result.message)
    }

    @Test
    fun `回读已包含当天签到时直接采纳刷新状态`() {
        val todayDay = 15
        val refreshed = checkInStatus(
            signedDays = 10,
            days = listOf(CheckInDay(dayOfMonth = todayDay, signed = true, bonus = false)),
        )

        val result = resolveCheckInResult(current = null, refreshed = refreshed, todayDayOfMonth = todayDay)

        assertSame(refreshed, result.status)
        assertTrue(result.markToday)
        assertEquals("签到成功", result.message)
        assertTrue(result.status?.isSignedOn(todayDay) == true)
        assertEquals(10, result.status?.signedDays)
    }

    @Test
    fun `回读已含当天时已签天数不重复累加`() {
        val todayDay = 15
        val refreshed = checkInStatus(
            signedDays = 10,
            days = listOf(CheckInDay(dayOfMonth = todayDay, signed = true, bonus = false)),
        )

        val result = resolveCheckInResult(current = null, refreshed = refreshed, todayDayOfMonth = todayDay)

        assertEquals(10, result.status?.signedDays)
        assertEquals(1, result.status?.days?.size)
    }

    @Test
    fun `回读遇CDN缓存未标记当天时乐观补签并提示稍后更新`() {
        val todayDay = 15
        val cachedRefreshed = checkInStatus(
            signedDays = 9,
            days = listOf(CheckInDay(dayOfMonth = todayDay, signed = false, bonus = false)),
        )

        val result = resolveCheckInResult(current = null, refreshed = cachedRefreshed, todayDayOfMonth = todayDay)

        assertEquals("签到已提交，日历稍后更新", result.message)
        assertTrue(result.markToday)
        assertTrue(result.status?.isSignedOn(todayDay) == true)
        assertEquals(10, result.status?.signedDays)
        // 日历里已有当天条目时只改标记，不重复追加
        assertEquals(1, result.status?.days?.size)
        // 原始回读对象不被就地改写
        assertFalse(cachedRefreshed.isSignedOn(todayDay))
    }

    @Test
    fun `乐观补签时日历缺少当天会追加一天的记录`() {
        val todayDay = 15
        val refreshed = checkInStatus(
            signedDays = 9,
            days = listOf(CheckInDay(dayOfMonth = 14, signed = true, bonus = false)),
        )

        val result = resolveCheckInResult(current = refreshed, refreshed = refreshed, todayDayOfMonth = todayDay)

        val days = result.status?.days ?: error("乐观补签必须给出状态")
        assertEquals(listOf(14, 15), days.map { it.dayOfMonth })
        assertEquals(CheckInDay(dayOfMonth = todayDay, signed = true, bonus = false), days.last())
        assertEquals(10, result.status?.signedDays)
    }

    @Test
    fun `签到展示按当天标记与回读日期判定今日已签`() {
        val today = LocalDate.of(2026, 11, 15)
        val signedStatus = checkInStatus(
            days = listOf(CheckInDay(dayOfMonth = 15, signed = true, bonus = false)),
        )

        assertTrue(checkInDisplay(signedStatus, checkedInOn = null, today = today).signedToday)
        // 回读还没刷出当天标记时由 checkedInOn 兜住
        assertTrue(checkInDisplay(checkInStatus(), checkedInOn = today, today = today).signedToday)
        // checkedInOn 按日期比较，跨过午夜自动失效
        assertFalse(checkInDisplay(checkInStatus(), checkedInOn = today.minusDays(1), today = today).signedToday)
        assertFalse(checkInDisplay(null, checkedInOn = null, today = today).signedToday)
    }

    @Test
    fun `签到展示只统计今天之前的漏签天数`() {
        val today = LocalDate.of(2026, 11, 15)
        val status = checkInStatus(
            days = listOf(
                CheckInDay(dayOfMonth = 10, signed = true, bonus = false),
                CheckInDay(dayOfMonth = 11, signed = false, bonus = false),
                CheckInDay(dayOfMonth = 12, signed = false, bonus = false),
                CheckInDay(dayOfMonth = 15, signed = false, bonus = false),
                CheckInDay(dayOfMonth = 18, signed = false, bonus = false),
            ),
        )

        // 当天与未来日期都不算漏签
        assertEquals(2, checkInDisplay(status, checkedInOn = null, today = today).missedDays)
        assertEquals(0, checkInDisplay(null, checkedInOn = null, today = today).missedDays)
    }

    @Test
    fun `签到展示按已签天数算出整数百分比与副标题`() {
        val today = LocalDate.of(2026, 11, 15)

        val partial = checkInDisplay(checkInStatus(signedDays = 10, totalDays = 30), checkedInOn = null, today = today)
        assertEquals(1f / 3f, partial.progress, 1e-6f)
        assertEquals(33, partial.progressPercent)
        assertEquals("10/30 天 · 33%", partial.progressLabel)

        val full = checkInDisplay(checkInStatus(signedDays = 7, totalDays = 7), checkedInOn = null, today = today)
        assertEquals(100, full.progressPercent)
        assertEquals("7/7 天 · 100%", full.progressLabel)
    }

    @Test
    fun `无签到状态时展示为未签且进度归零`() {
        val today = LocalDate.of(2026, 11, 15)

        val display = checkInDisplay(null, checkedInOn = null, today = today)

        assertFalse(display.signedToday)
        assertEquals(0, display.missedDays)
        assertEquals(0f, display.progress)
        assertEquals(0, display.progressPercent)
        assertEquals("0/0 天 · 0%", display.progressLabel)
    }

    @Test
    fun `本地已确认今日签到时CDN返回旧日历仍保持已签到并补齐当天进度`() {
        val today = LocalDate.of(2026, 11, 15)
        val staleServer = checkInStatus(
            signedDays = 9,
            days = listOf(
                CheckInDay(dayOfMonth = 14, signed = true, bonus = false),
                CheckInDay(dayOfMonth = 15, signed = false, bonus = false),
            ),
        )

        val resolved = reconcileLoadedCheckInStatus(
            loaded = staleServer,
            recordedCheckedInOn = today,
            today = today,
        )

        assertEquals(today, resolved.checkedInOn)
        assertFalse(resolved.confirmedByServer)
        assertTrue(resolved.status.isSignedOn(15))
        assertEquals(10, resolved.status.signedDays)
        assertTrue(checkInDisplay(resolved.status, resolved.checkedInOn, today).signedToday)
    }

    @Test
    fun `服务端日历已含今日签到时自动同步今日确认状态`() {
        val today = LocalDate.of(2026, 11, 15)
        val freshServer = checkInStatus(
            signedDays = 10,
            days = listOf(CheckInDay(dayOfMonth = 15, signed = true, bonus = false)),
        )

        val resolved = reconcileLoadedCheckInStatus(
            loaded = freshServer,
            recordedCheckedInOn = today.minusDays(1),
            today = today,
        )

        assertEquals(today, resolved.checkedInOn)
        assertTrue(resolved.confirmedByServer)
        assertSame(freshServer, resolved.status)
    }

    @Test
    fun `本地记录为昨天且服务端未签时不误判今日已签到`() {
        val today = LocalDate.of(2026, 11, 15)
        val freshServer = checkInStatus(
            signedDays = 9,
            days = listOf(CheckInDay(dayOfMonth = 15, signed = false, bonus = false)),
        )

        val resolved = reconcileLoadedCheckInStatus(
            loaded = freshServer,
            recordedCheckedInOn = today.minusDays(1),
            today = today,
        )

        assertNull(resolved.checkedInOn)
        assertFalse(resolved.confirmedByServer)
        assertFalse(resolved.status.isSignedOn(15))
        assertEquals(9, resolved.status.signedDays)
    }

    @Test
    fun `繁简体重复签到提示与日期文本解析均可正确识别`() {
        assertTrue(isAlreadyCheckedInMessage("已經簽到過了"))
        assertTrue(isAlreadyCheckedInMessage("今日已签到过"))
        assertFalse(isAlreadyCheckedInMessage("网络超时"))
        assertFalse(isAlreadyCheckedInMessage(null))

        assertEquals(LocalDate.of(2026, 11, 12), parseRecordedCheckInDate("2026-11-12"))
        assertNull(parseRecordedCheckInDate("invalid"))
        assertNull(parseRecordedCheckInDate(null))
    }

    @Test
    fun `签到协作者在切页与重开遇到CDN旧缓存时仍保持今日已签到`() = runBlocking {
        val settings = InMemorySettingsRepository()
        val today = LocalDate.of(2026, 11, 15)
        val staleCdn = checkInStatus(
            signedDays = 9,
            days = listOf(
                CheckInDay(dayOfMonth = 14, signed = true, bonus = false),
                CheckInDay(dayOfMonth = 15, signed = false, bonus = false),
            ),
        )
        val messages = mutableListOf<String>()

        // 第一轮：用户点击签到，服务端接口返回成功但回读仍带 CDN 旧日历
        val firstSession = SessionCache()
        val firstCoordinator = CheckInCoordinator(
            settings = settings,
            sessionCache = firstSession,
            initialUid = "1001",
            today = { today },
            sleep = {},
            awaitReady = { true },
            fetchStatus = { SessionOutcome.Ok(staleCdn) },
            submitCheckIn = { _, _ -> SessionOutcome.Ok(Unit) },
            showMessage = { messages += it },
        )
        firstCoordinator.syncAccount("1001")
        assertFalse(checkInDisplay(firstCoordinator.state.status, firstCoordinator.state.checkedInOn, today).signedToday)

        firstCoordinator.checkIn(firstCoordinator.state.status!!)
        assertEquals(today, firstCoordinator.state.checkedInOn)
        assertTrue(checkInDisplay(firstCoordinator.state.status, firstCoordinator.state.checkedInOn, today).signedToday)
        assertEquals("2026-11-15", settings.lastCheckInDate("1001"))

        // 第二轮：模拟冷启动重建进程（新 SessionCache，沿用已落盘的 SettingsRepository），CDN 依旧返回 staleCdn
        val restartedCoordinator = CheckInCoordinator(
            settings = settings,
            sessionCache = SessionCache(),
            initialUid = "1001",
            today = { today },
            sleep = {},
            awaitReady = { true },
            fetchStatus = { SessionOutcome.Ok(staleCdn) },
            submitCheckIn = { _, _ -> error("不应在已签到后重发签到请求") },
            showMessage = { messages += it },
        )
        restartedCoordinator.syncAccount("1001")
        assertEquals(today, restartedCoordinator.state.checkedInOn)
        assertTrue(checkInDisplay(restartedCoordinator.state.status, restartedCoordinator.state.checkedInOn, today).signedToday)
        assertEquals(10, restartedCoordinator.state.status?.signedDays)
    }

    @Test
    fun `手动点击遇到繁体重复签到提示时自动同步今日已签到并持久化`() = runBlocking {
        val settings = InMemorySettingsRepository()
        val sessionCache = SessionCache()
        val today = LocalDate.of(2026, 11, 15)
        val staleCdn = checkInStatus(
            signedDays = 9,
            days = listOf(CheckInDay(dayOfMonth = 15, signed = false, bonus = false)),
        )
        val messages = mutableListOf<String>()
        val coordinator = CheckInCoordinator(
            settings = settings,
            sessionCache = sessionCache,
            initialUid = "1001",
            today = { today },
            sleep = {},
            awaitReady = { true },
            fetchStatus = { SessionOutcome.Ok(staleCdn) },
            submitCheckIn = { _, _ -> SessionOutcome.Failed("已經簽到過了") },
            showMessage = { messages += it },
        )

        coordinator.syncAccount("1001")
        coordinator.checkIn(coordinator.state.status!!)

        assertEquals(today, coordinator.state.checkedInOn)
        assertNull(coordinator.state.error)
        assertTrue(coordinator.state.status?.isSignedOn(15) == true)
        assertEquals("2026-11-15", settings.lastCheckInDate("1001"))
        assertEquals(listOf("今日已签到"), messages)
    }

    @Test
    fun `单次回读透传会话失效与网络异常给 AuthCall 而不吞成 Ok(null)`() = runBlocking {
        var expiredCalls = 0
        val gate = object : StartupGate {
            override suspend fun awaitReady() = Unit
            override suspend fun onSessionExpired() {
                expiredCalls++
            }
        }
        val authCall = AuthCall(gate, Dispatchers.Unconfined)

        val expired = fetchDailyCheckInStatusOnce(authCall) {
            throw IllegalStateException("請先登入會員")
        }
        assertSame(SessionOutcome.Expired, expired)
        assertEquals(1, expiredCalls)

        val failed = fetchDailyCheckInStatusOnce(authCall) {
            throw SocketTimeoutException("timeout")
        }
        assertTrue(failed is SessionOutcome.Failed)
        assertEquals(1, expiredCalls)
    }

    /** 构造签到状态：日历缺省为「昨天已签」，便于区分「当天缺条目」与「当天未签」。 */
    private fun checkInStatus(
        signedDays: Int = 9,
        totalDays: Int = 30,
        days: List<CheckInDay> = listOf(CheckInDay(dayOfMonth = 14, signed = true, bonus = false)),
    ): DailyCheckInStatus = DailyCheckInStatus(
        dailyId = 1,
        eventName = "Daily",
        currentProgress = "$signedDays/$totalDays",
        signedDays = signedDays,
        totalDays = totalDays,
        days = days,
    )

    private companion object {
        const val FALLBACK = "签到状态加载失败，请稍后重试"
    }
}
