package com.jm3.app.miuix.ui

import com.jm3.app.miuix.failureCopy
import com.jm3.core.model.CheckInDay
import com.jm3.core.model.DailyCheckInStatus
import com.jm3.core.session.SessionOutcome
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * 首屏签到状态加载的退避节奏：立即一次，失败后等 1s、再失败等 3s 各重试一次。
 *
 * 启动早期失败多半是域名池刷新或库初始化尚未收敛，一次失败就落终态会把瞬时故障显示成「签到坏了」。
 */
internal val CHECK_IN_RETRY_DELAYS_MS: List<Long> = listOf(0L, 1_000L, 3_000L)

/** 网关就绪等待上限：库首次初始化（拉线路表 + 全域名探活）约 3.5s，留一点余量。 */
internal const val GATEWAY_READY_TIMEOUT_MS = 4_500L

/** 网关就绪轮询间隔：够短以免拖慢首屏，够长以免空转。 */
internal const val GATEWAY_READY_POLL_MS = 250L

/**
 * 按 [CHECK_IN_RETRY_DELAYS_MS] 重试签到状态加载，只在普通失败时重试。
 *
 * 会话失效是终态：账号层已经在处理补注入或重登，继续重试只会再吃一次 401；
 * 成功更没有重试的理由。因此只有 [SessionOutcome.Failed] 会走下一轮。
 */
internal suspend fun <T> retryCheckInLoad(
    attempt: suspend () -> SessionOutcome<T>,
    delays: List<Long> = CHECK_IN_RETRY_DELAYS_MS,
    sleep: suspend (Long) -> Unit = { delay(it) },
): SessionOutcome<T> {
    var last: SessionOutcome<T> = SessionOutcome.Failed(null)
    for ((index, waitMs) in delays.withIndex()) {
        if (index > 0 && waitMs > 0) sleep(waitMs)
        last = attempt()
        if (last !is SessionOutcome.Failed) return last
    }
    return last
}

/**
 * 等网关的域名管理器进入 READY。
 *
 * 未就绪时首个请求会阻塞在库的初始化链上，带上重试会变成「一次阻塞 + 三次超时」；
 * 先等 READY 再发，把等待收敛到一个可预期的上限。超时也返回 false，由调用方照常发请求——
 * 真实错误必须显示出来，不能让页面永远停在加载中。
 */
internal suspend fun awaitGatewayReady(
    ready: () -> Boolean,
    timeoutMs: Long = GATEWAY_READY_TIMEOUT_MS,
    pollMs: Long = GATEWAY_READY_POLL_MS,
    sleep: suspend (Long) -> Unit = { delay(it) },
): Boolean {
    if (ready()) return true
    val polls = (timeoutMs / pollMs).toInt()
    repeat(polls) {
        sleep(pollMs)
        if (ready()) return true
    }
    return false
}

/**
 * 签到失败的可见文案：与其它屏共用 [failureCopy] 的归类，避免第二套话术。
 */
internal fun checkInFailureCopy(failure: SessionOutcome.Failed, fallback: String): String =
    failureCopy(failure, fallback)

/**
 * 签到结果的落地结论：[status] 是采纳后的状态，[markToday] 表示能否把今天记为已签，
 * [message] 是给用户的提示。三者一起返回，调用点不再各自拼一套口径。
 */
internal data class CheckInResolution(
    val status: DailyCheckInStatus?,
    val markToday: Boolean,
    val message: String,
)

/**
 * 汇总签到结果：接口返回成功只说明请求已送达，是否真的落账要以回读到的日历为准。
 *
 * - 回读整体失败：状态不可知，保留调用方现有状态且不标记今天，当天还能重试；
 * - 回读已含今天：直接采纳刷新结果，不重复累加已签天数；
 * - 回读成功但日历未标记：服务端写入或 CDN 缓存（常见约 30 秒）有延迟，
 *   乐观补上当天并说明「稍后更新」，避免把已送达的签到误报成「未生效」。
 */
internal fun resolveCheckInResult(
    current: DailyCheckInStatus?,
    refreshed: DailyCheckInStatus?,
    todayDayOfMonth: Int,
): CheckInResolution {
    if (refreshed == null) {
        return CheckInResolution(
            status = current,
            markToday = false,
            message = "签到已提交，暂时无法确认状态",
        )
    }
    if (refreshed.isSignedOn(todayDayOfMonth)) {
        return CheckInResolution(status = refreshed, markToday = true, message = "签到成功")
    }
    return CheckInResolution(
        status = refreshed.markSignedOn(todayDayOfMonth),
        markToday = true,
        message = "签到已提交，日历稍后更新",
    )
}

/** 在日历上补记 [dayOfMonth]：已有当天条目只改标记，缺省则追加一天，已签天数同步加一。 */
private fun DailyCheckInStatus.markSignedOn(dayOfMonth: Int): DailyCheckInStatus {
    val hasDay = days.any { it.dayOfMonth == dayOfMonth }
    val updatedDays = if (hasDay) {
        days.map { if (it.dayOfMonth == dayOfMonth) it.copy(signed = true) else it }
    } else {
        days + CheckInDay(dayOfMonth = dayOfMonth, signed = true, bonus = false)
    }
    return copy(signedDays = signedDays + 1, days = updatedDays)
}

/**
 * 签到卡展示数据：今天是否已签、本月漏签天数与月度进度。
 * 两页共用同一份推导，避免「已签」与进度各算一遍后互相矛盾。
 */
internal data class CheckInDisplay(
    val signedToday: Boolean,
    val missedDays: Int,
    /** 进度条取值（0..1）；没有签到数据时为 0。 */
    val progress: Float,
    /** 已签占比的整数百分比，与进度条同源。 */
    val progressPercent: Int,
    /** 进度副标题，如「10/30 天 · 33%」。 */
    val progressLabel: String,
)

/**
 * 由签到状态推导展示数据。
 *
 * [checkedInOn] 只在接口回读之前兜住「刚签完」的瞬时态，按日期比较，跨过午夜自动失效；
 * 已签天数只取 record 自算的 signedDays，不叠加活动返回的百分比字符串。
 */
internal fun checkInDisplay(
    status: DailyCheckInStatus?,
    checkedInOn: LocalDate?,
    today: LocalDate,
): CheckInDisplay {
    val signedToday = checkedInOn == today || status?.isSignedOn(today.dayOfMonth) == true
    val missedDays = status?.days?.count { it.dayOfMonth < today.dayOfMonth && !it.signed } ?: 0
    val progress = status?.progress ?: 0f
    val percent = (progress * 100).roundToInt()
    return CheckInDisplay(
        signedToday = signedToday,
        missedDays = missedDays,
        progress = progress,
        progressPercent = percent,
        progressLabel = "${status?.signedDays ?: 0}/${status?.totalDays ?: 0} 天 · $percent%",
    )
}
