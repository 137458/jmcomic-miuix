package com.jm3.app.miuix.ui.mine

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.ui.CheckInDisplay
import com.jm3.app.miuix.ui.CheckInViewState
import com.jm3.app.miuix.ui.MineCheckInHolder
import com.jm3.app.miuix.ui.checkInDisplay
import com.jm3.app.miuix.ui.formatCheckInSummary
import com.jm3.app.miuix.ui.rememberMineCheckInHolder
import com.jm3.core.download.activeDownloadCount
import com.jm3.core.model.DailyCheckInStatus
import com.jm3.core.model.Jm3User
import com.jm3.core.session.UnreadBadge
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.SnackbarHostState

/**
 * 「我的」页的共享骨架：两套皮肤原先逐字重复的「账号/未读/在途计数取值、签到行推导、入口守卫、
 * 登出编排」收在这里。
 *
 * 抽出来的是行为，不是外观：分组、顺序、图标、卡片形态两皮肤差异很大，全部留在各自皮肤。
 * 只把「点哪一行算哪一个入口、哪些入口需要登录」收成 [MineEntry] 一张表，
 * 把签到行的标题/副标题/可点/进度收成 [mineCheckInRow] 一份推导，避免两皮肤各算一遍后互相矛盾。
 *
 * 两皮肤的真实差异用参数表达：[logoutFailureNotice] 为 null 时登出失败直接抛出（HyperOS 皮肤原行为：
 * 异常交给协程上下文），非 null 时弹出该文案（COUI 皮肤「退出登录失败，请稍后重试」）。
 */
internal enum class MineEntry(val requiresLogin: Boolean) {
    Favorites(requiresLogin = false),
    History(requiresLogin = false),
    LocalShelf(requiresLogin = false),
    Forum(requiresLogin = false),
    Novel(requiresLogin = false),
    Library(requiresLogin = false),
    Settings(requiresLogin = false),
    Notifications(requiresLogin = true),
    Tracking(requiresLogin = true),
    Tasks(requiresLogin = true),
    WatchHistory(requiresLogin = true),
    Profile(requiresLogin = true),
}

/** 签到区的窄端口：状态类不需要知道 AuthCall、缓存与网关。 */
internal interface MineCheckInPort {
    val viewState: CheckInViewState

    fun reload()

    fun checkIn(status: DailyCheckInStatus)
}

/** 提示通道：把 SnackbarHostState 收成一句话，状态类因此可以纯 JVM 测试。 */
internal interface MineNotice {
    suspend fun show(message: String)
}

/** 账号动作的窄端口：登出与账号维度缓存的作废。 */
internal interface MineAccountPort {
    suspend fun logout()

    fun clearSessionCache()
}

/** 三个外部取值来源：皮肤注入，状态类只读。 */
internal class MineScaffoldFields internal constructor(
    val user: () -> Jm3User?,
    val unread: () -> Int,
    val inFlightDownloads: () -> Int,
)

/**
 * 签到行的一次推导：标题、副标题、可点与进度都从同一份签到状态算出。
 *
 * [showsProgress] 不画 0/0：接口没给天数时进度条本身就是坏的。
 */
internal data class MineCheckInRow(
    val status: DailyCheckInStatus,
    val display: CheckInDisplay,
    val busy: Boolean,
    val title: String,
    val summary: String,
    val enabled: Boolean,
) {
    val signedToday: Boolean get() = display.signedToday

    val showsProgress: Boolean get() = status.totalDays > 0
}

/** 由签到状态推导行文案；还没有状态（只加载中或只报错）时返回 null，交给皮肤画加载/失败态。 */
internal fun mineCheckInRow(view: CheckInViewState, today: LocalDate): MineCheckInRow? {
    val status = view.status ?: return null
    val display = checkInDisplay(status, view.checkedInOn, today)
    val busy = view.busy
    return MineCheckInRow(
        status = status,
        display = display,
        busy = busy,
        title = when {
            busy -> "签到中…"
            display.signedToday -> "今日已签到"
            else -> "立即签到"
        },
        summary = formatCheckInSummary(status.eventName, display.signedToday),
        // 已签到只去掉点击、不置灰：禁用态会让人以为这一行坏了
        enabled = !(busy || display.signedToday),
    )
}

private class HolderCheckInPort(private val holder: MineCheckInHolder) : MineCheckInPort {
    override val viewState: CheckInViewState get() = holder.viewState

    override fun reload() {
        holder.reload()
    }

    override fun checkIn(status: DailyCheckInStatus) {
        holder.checkIn(status)
    }
}

private fun ScreenDeps.asMineAccountPort(): MineAccountPort = object : MineAccountPort {
    override suspend fun logout() {
        account.logout()
    }

    override fun clearSessionCache() {
        sessionCache.clear()
    }
}

/**
 * 我的页的可变状态与动作：入口守卫、签到动作、登出二次确认与执行。
 *
 * [today] 用取值函数注入，测试可以钉死日期判定「今天是否已签到」而不用等真实时间。
 */
@Stable
internal class MineScaffoldState internal constructor(
    private val scope: CoroutineScope,
    private val account: MineAccountPort,
    private val notice: MineNotice,
    private val checkInPort: MineCheckInPort,
    private val fields: MineScaffoldFields,
    private val onOpenLogin: () -> Unit,
    private val logoutFailureNotice: String? = null,
    private val today: () -> LocalDate = { LocalDate.now() },
) {
    var logoutDialogVisible: Boolean by mutableStateOf(false)
        private set

    val user: Jm3User? get() = fields.user()

    val unread: Int get() = fields.unread()

    val inFlightDownloads: Int get() = fields.inFlightDownloads()

    val checkInState: CheckInViewState get() = checkInPort.viewState

    /** 签到卡的可见条件：登录了就显示，没登录但签到接口报了会话失效也要显示（否则「去登录」入口没了）。 */
    val showsCheckIn: Boolean get() = user != null || checkInPort.viewState.expiredMessage != null

    /** 当前签到行；没有状态时为 null。 */
    val checkInRow: MineCheckInRow? get() = mineCheckInRow(checkInPort.viewState, today())

    fun openLogin() {
        onOpenLogin()
    }

    /** 入口守卫：需要登录却没登录时先引导登录，否则执行该入口自己的跳转。 */
    fun enter(entry: MineEntry, open: () -> Unit) {
        if (entry.requiresLogin && user == null) onOpenLogin() else open()
    }

    fun askLogout() {
        logoutDialogVisible = true
    }

    fun dismissLogout() {
        logoutDialogVisible = false
    }

    /** 弹窗确认后立刻收起弹窗再执行登出，与原两皮肤的点击顺序一致。 */
    fun submitLogout() {
        logoutDialogVisible = false
        scope.launch { logout() }
    }

    /**
     * 执行登出。
     *
     * 成功：作废账号维度缓存并提示「已退出登录」。
     * 失败：[logoutFailureNotice] 为 null 时重新抛出（HyperOS 皮肤原行为），否则提示该文案且不动缓存。
     */
    suspend fun logout() {
        val outcome = runCatching { account.logout() }
        val failure = outcome.exceptionOrNull()
        if (failure != null) {
            val message = logoutFailureNotice
            if (message == null) throw failure
            notice.show(message)
            return
        }
        account.clearSessionCache()
        notice.show("已退出登录")
    }

    fun reloadCheckIn() {
        checkInPort.reload()
    }

    /** 签到：有状态才发请求，避免加载中的空态被当成可签。 */
    fun checkIn() {
        checkInPort.viewState.status?.let(checkInPort::checkIn)
    }
}

/**
 * 建一份我的页状态。
 *
 * 账号、未读数、在途下载数与签到状态都来自原来的数据源，皮肤不再各自 collect 一遍；
 * [snackbarHostState] 只在建状态时被包成 [MineNotice]，状态类本身不依赖它。
 */
@Composable
internal fun rememberMineScaffoldState(
    deps: ScreenDeps,
    unreadBadge: UnreadBadge,
    snackbarHostState: SnackbarHostState,
    onOpenLogin: () -> Unit,
    logoutFailureNotice: String? = null,
): MineScaffoldState {
    val scope = rememberCoroutineScope()
    val userState = deps.account.user.collectAsState()
    val unreadState = unreadBadge.count.collectAsState()
    val downloadsState = deps.downloadManager.queue.updates.collectAsState()
    val holder = rememberMineCheckInHolder(
        deps = deps,
        uid = userState.value?.uid,
        snackbarHostState = snackbarHostState,
    )
    val notice = remember(snackbarHostState) {
        object : MineNotice {
            override suspend fun show(message: String) {
                snackbarHostState.showSnackbar(message)
            }
        }
    }
    val checkInPort = remember(holder) { HolderCheckInPort(holder) }
    val accountPort = remember(deps) { deps.asMineAccountPort() }
    // 三个取值函数每次重组重建，但都闭在稳定的快照 State 上，因此读到的永远是最新值
    val fields = MineScaffoldFields(
        user = { userState.value },
        unread = { unreadState.value },
        inFlightDownloads = { downloadsState.value.activeDownloadCount() },
    )
    return remember(deps, scope, notice, checkInPort, accountPort, logoutFailureNotice) {
        MineScaffoldState(
            scope = scope,
            account = accountPort,
            notice = notice,
            checkInPort = checkInPort,
            fields = fields,
            onOpenLogin = onOpenLogin,
            logoutFailureNotice = logoutFailureNotice,
        )
    }
}
