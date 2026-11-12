package com.jm3.app.miuix.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.failureCopy
import com.jm3.core.Jm3Log
import com.jm3.core.model.CheckInDay
import com.jm3.core.model.DailyCheckInStatus
import com.jm3.core.session.AuthCall
import com.jm3.core.session.SessionOutcome
import com.jm3.core.settings.CheckInSettings
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.SnackbarHostState

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
    if (isSignedOn(dayOfMonth)) return this
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

/** 初屏/切页加载签到状态后的合并结论：避免服务端 CDN 旧缓存覆盖已确认的今日签到态。 */
internal data class CheckInLoadResolution(
    val status: DailyCheckInStatus,
    val checkedInOn: LocalDate?,
    val confirmedByServer: Boolean,
)

internal fun parseRecordedCheckInDate(raw: String?): LocalDate? {
    val text = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return runCatching { LocalDate.parse(text) }.getOrNull()
}

internal fun isAlreadyCheckedInMessage(message: String?): Boolean {
    val text = message?.trim().orEmpty()
    if (text.isEmpty()) return false
    return text.contains("已签到") ||
        text.contains("已经签到") ||
        text.contains("已簽到") ||
        text.contains("已經簽到")
}

internal fun reconcileLoadedCheckInStatus(
    loaded: DailyCheckInStatus,
    recordedCheckedInOn: LocalDate?,
    today: LocalDate,
): CheckInLoadResolution {
    if (loaded.isSignedOn(today.dayOfMonth)) {
        return CheckInLoadResolution(
            status = loaded,
            checkedInOn = today,
            confirmedByServer = true,
        )
    }
    if (recordedCheckedInOn == today) {
        return CheckInLoadResolution(
            status = loaded.markSignedOn(today.dayOfMonth),
            checkedInOn = today,
            confirmedByServer = false,
        )
    }
    return CheckInLoadResolution(
        status = loaded,
        checkedInOn = null,
        confirmedByServer = false,
    )
}

/** 每日签到页面的共享 UI 状态。 */
internal data class CheckInViewState(
    val status: DailyCheckInStatus? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val expiredMessage: String? = null,
    val busy: Boolean = false,
    val checkedInOn: LocalDate? = null,
)

/**
 * 每日签到状态协作者：两套皮肤共用同一套持久化和 CDN 缓存兜底逻辑。
 *
 * 1. 切换 tab 重建页面时先从 [sessionCache] 读取同日快照，避免先闪一次「加载中」或丢掉已签到态；
 * 2. 冷启动/重开时从 [settings]（`jm3_settings`）恢复当天已确认的签到日期，即便服务端 `/daily`
 *    仍返回过期的 CDN 缓存，也会把状态维持在「今日已签到」并补齐当天进度。
 */
internal class CheckInCoordinator(
    private val settings: CheckInSettings,
    private val sessionCache: SessionCache,
    private val initialUid: String? = null,
    private val today: () -> LocalDate = { LocalDate.now() },
    private val sleep: suspend (Long) -> Unit = { delay(it) },
    private val awaitReady: suspend () -> Boolean,
    private val fetchStatus: suspend (String) -> SessionOutcome<DailyCheckInStatus>,
    private val fetchStatusOnce: suspend (String) -> SessionOutcome<DailyCheckInStatus?> = fetchStatus,
    private val submitCheckIn: suspend (String, Int) -> SessionOutcome<Unit>,
    private val showMessage: suspend (String) -> Unit,
    private val logWarn: (String, Throwable?) -> Unit = { message, cause -> Jm3Log.warn("CheckIn", message, cause) },
    private val onStateChanged: (CheckInViewState) -> Unit = {},
) {
    private var activeUid: String? = initialUid
    private var loadToken: Long = 0L

    var state: CheckInViewState = seedInitialState(initialUid)
        private set

    private fun seedInitialState(uid: String?): CheckInViewState {
        val normalizedUid = uid ?: return CheckInViewState()
        val now = today()
        val cached = sessionCache.checkInForToday(normalizedUid, now) ?: return CheckInViewState()
        val checkedInToday = cached.checkedInOn?.takeIf { it == now }
        return CheckInViewState(status = cached.status, checkedInOn = checkedInToday)
    }

    private inline fun update(transform: (CheckInViewState) -> CheckInViewState) {
        state = transform(state)
        onStateChanged(state)
    }

    suspend fun syncAccount(uid: String?) {
        val token = ++loadToken
        activeUid = uid
        if (uid == null) {
            // 清会话也会把 user 置空，此时不能顺手抹掉失效提示，否则用户看不到「为什么突然要重新登录」
            update { it.copy(status = null, loading = false, error = null, busy = false, checkedInOn = null) }
            return
        }
        val now = today()
        val cached = sessionCache.checkInForToday(uid, now)
        val persisted = parseRecordedCheckInDate(settings.lastCheckInDate(uid))?.takeIf { it == now }
        if (token != loadToken || activeUid != uid) return
        val knownCheckedIn = persisted ?: cached?.checkedInOn?.takeIf { it == now }
        val seededStatus = cached?.status?.let { s ->
            if (knownCheckedIn == now) reconcileLoadedCheckInStatus(s, now, now).status else s
        }
        update {
            CheckInViewState(
                status = seededStatus,
                checkedInOn = knownCheckedIn,
            )
        }
        load()
    }

    suspend fun load() {
        val uid = activeUid ?: return
        val token = ++loadToken
        update { it.copy(loading = it.status == null, error = null) }
        awaitReady()
        if (token != loadToken || activeUid != uid) return
        val outcome = retryCheckInLoad(
            attempt = { fetchStatus(uid) },
            sleep = sleep,
        )
        if (token != loadToken || activeUid != uid) return
        update { it.copy(loading = false) }
        when (outcome) {
            is SessionOutcome.Ok -> {
                val now = today()
                val persisted = parseRecordedCheckInDate(settings.lastCheckInDate(uid))?.takeIf { it == now }
                if (token != loadToken || activeUid != uid) return
                val knownCheckedIn = persisted ?: state.checkedInOn?.takeIf { it == now }
                val resolved = reconcileLoadedCheckInStatus(
                    loaded = outcome.value,
                    recordedCheckedInOn = knownCheckedIn,
                    today = now,
                )
                update {
                    it.copy(
                        status = resolved.status,
                        checkedInOn = resolved.checkedInOn,
                        error = null,
                        expiredMessage = null,
                    )
                }
                sessionCache.rememberCheckIn(uid, resolved.status, resolved.checkedInOn, now)
                if (resolved.confirmedByServer) {
                    settings.setLastCheckInDate(uid, now.toString())
                }
            }

            SessionOutcome.Expired -> {
                val msg = planLoad(outcome, hasContent = false, failureMessage = "签到状态加载失败").message
                update { it.copy(expiredMessage = msg, error = null) }
            }

            is SessionOutcome.Failed -> {
                logWarn(
                    "签到状态加载失败（${outcome.cause?.javaClass?.name ?: "无异常"}）：${outcome.message.orEmpty()}",
                    outcome.cause,
                )
                update { cur ->
                    if (cur.status == null) {
                        cur.copy(error = checkInFailureCopy(outcome, "签到状态加载失败，请稍后重试"))
                    } else {
                        cur
                    }
                }
            }
        }
    }

    private suspend fun refreshOnce(uid: String): DailyCheckInStatus? = when (val outcome = fetchStatusOnce(uid)) {
        is SessionOutcome.Ok -> outcome.value
        SessionOutcome.Expired -> {
            val msg = planLoad(outcome, hasContent = false, failureMessage = "签到状态加载失败").message
            update { it.copy(expiredMessage = msg, error = null) }
            null
        }
        is SessionOutcome.Failed -> null
    }

    suspend fun checkIn(targetStatus: DailyCheckInStatus) {
        val uid = activeUid ?: return
        val now = today()
        if (state.busy || state.checkedInOn == now) return
        update { it.copy(busy = true, error = null) }
        val outcome = submitCheckIn(uid, targetStatus.dailyId)
        if (activeUid != uid) return
        update { it.copy(busy = false) }
        when {
            outcome is SessionOutcome.Ok -> {
                var refreshed = refreshOnce(uid)
                if (refreshed != null && !refreshed.isSignedOn(now.dayOfMonth)) {
                    sleep(400L)
                    if (activeUid != uid) return
                    val retry = refreshOnce(uid)
                    if (retry != null) refreshed = retry
                }
                if (activeUid != uid) return
                val resolution = resolveCheckInResult(
                    current = state.status ?: targetStatus,
                    refreshed = refreshed,
                    todayDayOfMonth = now.dayOfMonth,
                )
                val nextStatus = resolution.status ?: state.status
                val nextCheckedIn = if (resolution.markToday) now else state.checkedInOn
                update { it.copy(status = nextStatus, checkedInOn = nextCheckedIn) }
                if (resolution.markToday) {
                    settings.setLastCheckInDate(uid, now.toString())
                    sessionCache.rememberCheckIn(uid, nextStatus, now, now)
                }
                showMessage(resolution.message)
            }

            outcome is SessionOutcome.Failed && isAlreadyCheckedInMessage(outcome.message) -> {
                val refreshed = refreshOnce(uid)
                if (activeUid != uid) return
                val base = refreshed ?: state.status ?: targetStatus
                val resolved = reconcileLoadedCheckInStatus(
                    loaded = base,
                    recordedCheckedInOn = now,
                    today = now,
                )
                update {
                    it.copy(
                        status = resolved.status,
                        checkedInOn = now,
                        error = null,
                        expiredMessage = null,
                    )
                }
                settings.setLastCheckInDate(uid, now.toString())
                sessionCache.rememberCheckIn(uid, resolved.status, now, now)
                showMessage("今日已签到")
            }

            outcome is SessionOutcome.Expired -> {
                val msg = planLoad(outcome, hasContent = false, failureMessage = "签到失败，请重试").message
                update { it.copy(expiredMessage = msg, error = null) }
            }

            outcome is SessionOutcome.Failed -> {
                update { it.copy(error = checkInFailureCopy(outcome, "签到失败，请稍后重试")) }
            }
        }
    }
}

/** 每日签到状态单次回读：直接经 [AuthCall.call] 透传网络与会话失效异常，不吞异常。 */
internal suspend fun fetchDailyCheckInStatusOnce(
    authCall: AuthCall,
    fetch: suspend () -> DailyCheckInStatus,
): SessionOutcome<DailyCheckInStatus?> = authCall.call { fetch() }

/** 面向我的页的统一签到状态持有人：ColorOS 与 HyperOS 双皮肤共享同一条链路。 */
@Stable
internal class MineCheckInHolder(
    private val deps: ScreenDeps,
    private val snackbarHostState: SnackbarHostState,
    private val scope: kotlinx.coroutines.CoroutineScope,
) {
    var viewState by mutableStateOf(CheckInViewState())
        private set

    private var loadJob: Job? = null

    private val coordinator = CheckInCoordinator(
        settings = deps.settings,
        sessionCache = deps.sessionCache,
        awaitReady = {
            awaitGatewayReady(ready = { runCatching { deps.routes.domainsReady() }.getOrDefault(false) })
        },
        fetchStatus = { targetUid ->
            deps.authCall.call { deps.gateway.dailyCheckInStatus(targetUid) }
        },
        fetchStatusOnce = { targetUid ->
            fetchDailyCheckInStatusOnce(deps.authCall) { deps.gateway.dailyCheckInStatus(targetUid) }
        },
        submitCheckIn = { targetUid, dailyId ->
            deps.authCall.call { deps.gateway.dailyCheckIn(targetUid, dailyId) }
        },
        showMessage = { message ->
            snackbarHostState.showSnackbar(message)
        },
        onStateChanged = { next -> viewState = next },
    )

    suspend fun syncAccount(uid: String?) {
        loadJob?.cancel()
        loadJob = null
        coordinator.syncAccount(uid)
    }

    fun seedIfPresent(uid: String?) {
        if (uid == null) return
        val cached = deps.sessionCache.checkInForToday(uid, LocalDate.now()) ?: return
        if (viewState.status == null && cached.status != null) {
            viewState = CheckInViewState(
                status = cached.status,
                checkedInOn = cached.checkedInOn?.takeIf { it == LocalDate.now() },
            )
        }
    }

    fun reload() {
        loadJob?.cancel()
        loadJob = scope.launch { coordinator.load() }
    }

    fun checkIn(status: DailyCheckInStatus) {
        scope.launch { coordinator.checkIn(status) }
    }
}

@Composable
internal fun rememberMineCheckInHolder(
    deps: ScreenDeps,
    uid: String?,
    snackbarHostState: SnackbarHostState,
): MineCheckInHolder {
    val scope = rememberCoroutineScope()
    val holder = remember(deps, snackbarHostState, scope) {
        MineCheckInHolder(deps = deps, snackbarHostState = snackbarHostState, scope = scope)
    }
    LaunchedEffect(holder, uid) {
        holder.seedIfPresent(uid)
        holder.syncAccount(uid)
    }
    return holder
}
