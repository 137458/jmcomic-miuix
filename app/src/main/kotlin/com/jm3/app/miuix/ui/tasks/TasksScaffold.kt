package com.jm3.app.miuix.ui.tasks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.failureCopy
import com.jm3.app.miuix.ui.list.SessionListLoader
import com.jm3.app.miuix.ui.route.AppRoutes
import com.jm3.core.model.Jm3User
import com.jm3.core.model.TaskFilter
import com.jm3.core.model.TaskItem
import com.jm3.core.model.TaskPage
import com.jm3.core.model.TaskType
import com.jm3.core.paging.PageSlice
import com.jm3.core.session.SessionOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.SnackbarHostState

/*
 * 任务页共享骨架（C4 双皮肤）。
 *
 * 两套皮肤的任务页此前各写一份逐字相同的状态编排：称号/勋章两档列表的取回与首屏时机、
 * 换筛选时清页内文案、佩戴切换（门禁、在途标记、成功/失败提示）与佩戴态本地覆盖，
 * 以及空态标题与提示文案。这里把行为收口到一处，皮肤侧只留自己的控件与布局；
 * 骨架不引用任何皮肤控件，Miuix 与 COUI 都能直接 import。
 *
 * 两皮肤的真实差异一律用参数表达，不强行统一：
 * - [TaskHostResolution]：勋章图域名的解析时机与通道（HyperOS 请求前同步解析并整屏写一次，
 *   ColorOS 成功取回后再走 AuthCall 解析并按档记录）；
 * - [rememberTasksScaffoldState] 的 failureMessage / failureFallback：整屏加载失败标题与
 *   佩戴失败兜底各有历史文案；
 * - [rememberTasksScaffoldState] 的 pageCount：HyperOS 侧把任务列表当「只有 1 页」喂给分页容器，
 *   ColorOS 侧不报总页数；
 * - [rememberTasksScaffoldState] 的 reloadOnAccount：HyperOS 侧换账号重建列表，ColorOS 侧只按筛选重建。
 */

/** 成就状态的无障碍描述与行内标签：两套皮肤逐字相同。 */
internal const val TASK_STATE_DONE = "已达成"
internal const val TASK_STATE_UNDONE = "未达成"

/** 佩戴失败里的会话失效文案：两侧原先各写一份字面量。 */
internal const val TASK_CLAIM_EXPIRED = "登录已失效，请重新登录"

/** 一档列表的服务端附加信息：message 与该档解析到的勋章图域名。 */
internal data class TaskFeedExtra(val message: String? = null, val imageHost: String = "")

/**
 * 各档的页内附加信息。
 *
 * 任务接口不分页，服务端 message 与勋章图域名没有能装进 [PageSlice] 的位置，按档另记一份。
 * 换筛选整组作废（[cleared]）：HyperOS 侧只读 message、域名另走整屏一份，ColorOS 侧两者都按档读。
 */
internal data class TaskFeedExtras(private val byTab: Map<Int, TaskFeedExtra> = emptyMap()) {

    operator fun get(tab: Int): TaskFeedExtra? = byTab[tab]

    /** 记录一档的附加信息：后取的覆盖先取的。 */
    fun recorded(tab: Int, extra: TaskFeedExtra): TaskFeedExtras =
        copy(byTab = byTab + (tab to extra))

    /** 换筛选后整组作废。 */
    fun cleared(): TaskFeedExtras = TaskFeedExtras()
}

/**
 * 佩戴态的本地覆盖：称号记内容文本、勋章记 id。
 *
 * 佩戴成功后服务端已更新，但账号缓存不会立刻刷新，界面以这份覆盖为准；两档互斥。
 */
internal data class TasksEquipped(val title: String? = null, val badgeId: String? = null) {

    /** 一次成功佩戴后的覆盖：写哪一档由 [type] 决定。 */
    fun withClaim(type: TaskType, item: TaskItem): TasksEquipped = when (type) {
        TaskType.TITLE -> copy(title = item.content)
        TaskType.BADGE -> copy(badgeId = item.id)
    }
}

/** 佩戴点击的门禁：未登录先去登录页，已有一次在途就忽略重复点击。 */
internal sealed interface TasksClaimGate {
    data object Login : TasksClaimGate

    data object Busy : TasksClaimGate

    data class Start(val uid: String) : TasksClaimGate
}

internal fun gateTaskClaim(uid: String?, claimingId: String?): TasksClaimGate = when {
    uid.isNullOrBlank() -> TasksClaimGate.Login
    claimingId != null -> TasksClaimGate.Busy
    else -> TasksClaimGate.Start(uid)
}

/** 一次佩戴取回的结局：写入本地覆盖并提示佩戴成功，或只给失败提示。 */
internal sealed interface TasksClaimPlan {
    val message: String

    data class Equipped(val equipped: TasksEquipped, override val message: String) : TasksClaimPlan

    data class Rejected(override val message: String) : TasksClaimPlan
}

/**
 * 佩戴取回的落地：成功写本地覆盖并提示「已佩戴」，失败保留服务端提示，
 * 便于区分「没权限」与「网络错」——两支提示与原实现逐字一致。
 */
internal fun planTaskClaim(
    equipped: TasksEquipped,
    type: TaskType,
    item: TaskItem,
    outcome: SessionOutcome<String?>,
    failureFallback: String,
): TasksClaimPlan = when (outcome) {
    is SessionOutcome.Ok -> outcome.value
        ?.let { TasksClaimPlan.Rejected("切换失败：" + it) }
        ?: TasksClaimPlan.Equipped(
            equipped = equipped.withClaim(type, item),
            message = "已佩戴「" + item.name + "」",
        )

    SessionOutcome.Expired -> TasksClaimPlan.Rejected("切换失败：" + TASK_CLAIM_EXPIRED)

    is SessionOutcome.Failed -> TasksClaimPlan.Rejected(
        "切换失败：" + failureCopy(outcome, failureFallback),
    )
}

/**
 * 佩戴判定：称号按展示文本、勋章按 id。
 *
 * [equippedTitle] 由皮肤决定是否带账号称号兜底：HyperOS 侧带（没有本地覆盖时认账号当前称号），
 * ColorOS 侧只认本地覆盖。
 */
internal fun isTaskEquipped(
    type: TaskType,
    item: TaskItem,
    equippedTitle: String?,
    equippedBadgeId: String?,
): Boolean = when (type) {
    TaskType.TITLE -> equippedTitle != null && item.content == equippedTitle
    TaskType.BADGE -> equippedBadgeId == item.id
}

/** 空态标题：两套皮肤同一句「暂无称号 / 暂无勋章」。 */
internal fun tasksEmptyTitle(type: TaskType): String = "暂无" + type.label

/** 空态提示：每档的解锁条件各一句。 */
internal fun tasksEmptyHint(type: TaskType): String = when (type) {
    TaskType.TITLE -> "继续阅读与收藏即可解锁称号"
    TaskType.BADGE -> "完成成就后可在此获得勋章"
}

/** 一次成功取回落到分页容器上的切片：任务接口不分页，永远只有第 1 页、没有下一页。 */
internal fun tasksFeedSlice(items: List<TaskItem>, pageCount: Int?): PageSlice<TaskItem> =
    PageSlice(items = items, hasMore = false, page = 1, pageCount = pageCount)

/**
 * 勋章图域名的解析时机与通道：两套皮肤的历史实现不同，行为必须原样保留。
 *
 * [BeforeRequest]：请求前同步解析（取不到留空，仍空则退回当前 API 域名），
 * 且无论取回成败都把域名写进整屏状态；[AfterSuccess]：成功取回后再各走一次 AuthCall 解析，
 * 失效或失败留空串，并按档记录。
 */
internal enum class TaskHostResolution { BeforeRequest, AfterSuccess }

/** 骨架持有的可保存字段：档位下标跨旋转保留，其余是本次会话的瞬时态。 */
internal class TasksScaffoldFields internal constructor(
    val typeIndex: MutableIntState,
    val filterIndex: MutableIntState,
    /** 上一次真正处理过的筛选下标：与当前不同即视为换筛选。 */
    val appliedFilterIndex: MutableIntState,
    val equipped: MutableState<TasksEquipped>,
    val claimingId: MutableState<String?>,
    val extras: MutableState<TaskFeedExtras>,
    val imageHost: MutableState<String>,
)

/** 建立一组骨架字段：页签下标沿用 rememberSaveable，佩戴与页内文案属于本次会话。 */
@Composable
private fun rememberTasksScaffoldFields(): TasksScaffoldFields = TasksScaffoldFields(
    typeIndex = rememberSaveable { mutableIntStateOf(0) },
    filterIndex = rememberSaveable { mutableIntStateOf(0) },
    appliedFilterIndex = remember { mutableIntStateOf(0) },
    equipped = remember { mutableStateOf(TasksEquipped()) },
    claimingId = remember { mutableStateOf<String?>(null) },
    extras = remember { mutableStateOf(TaskFeedExtras()) },
    imageHost = remember { mutableStateOf("") },
)

/**
 * 任务页共享骨架：两套皮肤逐字重复的状态编排与佩戴动作。
 *
 * 骨架只管行为，不管长相：两档列表的 loader、首屏时机、页内文案、佩戴门禁与提示都在这里；
 * 皮肤侧拿状态去渲染自己的控件。回退与跳转入口只做转发，导航语义仍由组装根决定。
 */
@Stable
internal class TasksScaffoldState internal constructor(
    private val scope: CoroutineScope,
    private val fields: TasksScaffoldFields,
    private val failureMessage: String,
    private val failureFallback: String,
    private val hostResolution: TaskHostResolution,
    private val pageCount: Int?,
    private val userRef: State<Jm3User?>,
    private val hapticRef: State<() -> Unit>,
    private val filterResetRef: State<() -> Unit>,
    private val tabStartedRef: State<(Int) -> Unit>,
    /** 提示出口：皮肤侧接自己的 Snackbar。 */
    private val notify: suspend (String) -> Unit,
    /** 一档成就取回（已过登录闸门）。 */
    private val fetchTaskPage: suspend (TaskType, TaskFilter) -> SessionOutcome<TaskPage>,
    /** 切换佩戴（已过登录闸门）：null 表示成功，非空为服务端失败文案。 */
    private val claimTask: suspend (TaskType, String, String) -> SessionOutcome<String?>,
    /** 按解析时机取勋章图域名：两条历史路径的差异留在组装根。 */
    private val resolveImageHost: suspend (TaskHostResolution) -> String,
    /** 返回与跳转入口：皮肤只把导航动作转发到这里。 */
    val nav: AppRoutes,
) {

    /** 称号与勋章两档，顺序即页签顺序。 */
    val types: List<TaskType> = TaskType.entries

    /** 我的 / 全部两档筛选，顺序即页签顺序。 */
    val filters: List<TaskFilter> = TaskFilter.entries

    /** 已登录账号：佩戴时取 uid，称号兜底取账号当前称号。 */
    val user: Jm3User? get() = userRef.value

    /** 本代 loader 里已经发起过首屏请求的档位。 */
    private val startedTabs = mutableSetOf<Int>()

    /** 称号与勋章各一个 loader：两类条目互斥，切档不会打断另一档的在途请求。 */
    val loaders: List<SessionListLoader<TaskItem>> = types.mapIndexed { tab, type ->
        SessionListLoader(
            scope = scope,
            idOf = { it.id },
            failureMessage = failureMessage,
            fetchPage = { fetchTasks(type = type, filter = filters[clampedFilterIndex], tab = tab) },
            notice = { message -> scope.launch { notify(message) } },
        )
    }

    /** 档位下标原文（越界由 [clampedTypeIndex] 兜底）。 */
    fun selectType(index: Int) {
        fields.typeIndex.intValue = index
    }

    /** 筛选下标原文（越界由 [clampedFilterIndex] 兜底）。 */
    fun selectFilter(index: Int) {
        fields.filterIndex.intValue = index
    }

    val clampedTypeIndex: Int get() = fields.typeIndex.intValue.coerceIn(0, types.lastIndex)

    val clampedFilterIndex: Int get() = fields.filterIndex.intValue.coerceIn(0, filters.lastIndex)

    /** 整屏勋章图域名：HyperOS 侧读它渲染，ColorOS 侧按档读 [extra]。 */
    val imageHost: String get() = fields.imageHost.value

    /** 某一档的页内附加信息。 */
    fun extra(tab: Int): TaskFeedExtra? = fields.extras.value[tab]

    /** 本地覆盖的称号内容；null 表示没有覆盖。 */
    val equippedTitleOverride: String? get() = fields.equipped.value.title

    /** 本地覆盖的勋章 id；null 表示没有覆盖。 */
    val equippedBadgeId: String? get() = fields.equipped.value.badgeId

    /** 正在切换佩戴的条目 id；null 表示没有在途动作。 */
    val claimingId: String? get() = fields.claimingId.value

    /** 进页或切档：换筛选时先清页内文案并让皮肤复位视觉，每档只发起一次首屏请求。 */
    fun enterTab() {
        val filter = clampedFilterIndex
        if (fields.appliedFilterIndex.intValue != filter) {
            fields.appliedFilterIndex.intValue = filter
            fields.extras.value = fields.extras.value.cleared()
            filterResetRef.value()
        }
        val tab = clampedTypeIndex
        if (!startedTabs.add(tab)) return
        tabStartedRef.value(tab)
        loaders.getOrNull(tab)?.loadFirstPage()
    }

    /** 换账号后旧账号的佩戴覆盖不再成立。 */
    fun resetEquipped() {
        fields.equipped.value = TasksEquipped()
    }

    /** 点选已获得的称号/勋章并切换佩戴；失败保留服务端提示，便于区分「没权限」和「网络错」。 */
    fun claim(type: TaskType, item: TaskItem) {
        when (val gate = gateTaskClaim(uid = user?.uid, claimingId = fields.claimingId.value)) {
            TasksClaimGate.Login -> nav.login()
            TasksClaimGate.Busy -> Unit
            is TasksClaimGate.Start -> {
                fields.claimingId.value = item.id
                scope.launch {
                    val outcome = claimTask(type, gate.uid, item.id)
                    fields.claimingId.value = null
                    val plan = planTaskClaim(
                        equipped = fields.equipped.value,
                        type = type,
                        item = item,
                        outcome = outcome,
                        failureFallback = failureFallback,
                    )
                    if (plan is TasksClaimPlan.Equipped) {
                        fields.equipped.value = plan.equipped
                        hapticRef.value()
                    }
                    notify(plan.message)
                }
            }
        }
    }

    /** 一次任务页取回：域名解析时机按 [hostResolution] 走两皮肤各自的历史路径。 */
    private suspend fun fetchTasks(
        type: TaskType,
        filter: TaskFilter,
        tab: Int,
    ): SessionOutcome<PageSlice<TaskItem>> {
        val preResolved = if (hostResolution == TaskHostResolution.BeforeRequest) {
            resolveImageHost(TaskHostResolution.BeforeRequest)
        } else {
            ""
        }
        val outcome = fetchTaskPage(type, filter)
        // 请求前解析的皮肤无论成败都把域名落到整屏状态，与原实现一致
        if (hostResolution == TaskHostResolution.BeforeRequest) fields.imageHost.value = preResolved
        return when (outcome) {
            is SessionOutcome.Ok -> {
                val page = outcome.value
                val tabHost = if (hostResolution == TaskHostResolution.AfterSuccess) {
                    resolveImageHost(TaskHostResolution.AfterSuccess)
                } else {
                    preResolved
                }
                fields.extras.value = fields.extras.value.recorded(
                    tab = tab,
                    extra = TaskFeedExtra(message = page.message, imageHost = tabHost),
                )
                SessionOutcome.Ok(tasksFeedSlice(items = page.items, pageCount = pageCount))
            }

            SessionOutcome.Expired -> SessionOutcome.Expired
            is SessionOutcome.Failed -> outcome
        }
    }
}

/**
 * 勋章图域名的两条历史路径：解析时机由骨架决定，解析方式与失败降级留在这里。
 *
 * [TaskHostResolution.BeforeRequest]：请求前同步解析，取不到留空串，仍空则退回当前 API 域名；
 * [TaskHostResolution.AfterSuccess]：成功取回后再走 AuthCall 解析，失效或失败一律留空串。
 */
private suspend fun resolveScreenDepsImageHost(deps: ScreenDeps, resolution: TaskHostResolution): String =
    when (resolution) {
        TaskHostResolution.BeforeRequest -> runCatching { deps.images.imageHost() }
            .getOrDefault("")
            .ifBlank { runCatching { deps.routes.usedDomain() }.getOrNull().orEmpty() }

        TaskHostResolution.AfterSuccess ->
            (deps.authCall.call { deps.images.imageHost() } as? SessionOutcome.Ok)?.value.orEmpty()
                .ifBlank {
                    (deps.authCall.call { deps.routes.usedDomain() } as? SessionOutcome.Ok)?.value.orEmpty()
                }
    }

/**
 * 建立任务页共享骨架。
 *
 * [failureMessage] 是整屏加载失败标题，[failureFallback] 是佩戴失败兜底文案，两者各皮肤历史上不同；
 * [hostResolution] 决定勋章图域名的解析时机；[pageCount] 决定喂给分页容器的总页数；
 * [reloadOnAccount] 为 true 时换账号重建整组 loader（HyperOS 侧），否则只按筛选重建。
 * [onFilterReset] 与 [onTabStarted] 是皮肤自己的视觉复位槽：换筛选时复位全部、
 * 某档首次进入时复位该档，两者按皮肤需要各自实现。
 */
@Composable
internal fun rememberTasksScaffoldState(
    deps: ScreenDeps,
    nav: AppRoutes,
    snackbarHostState: SnackbarHostState,
    haptic: () -> Unit,
    failureMessage: String,
    failureFallback: String,
    hostResolution: TaskHostResolution,
    pageCount: Int? = null,
    reloadOnAccount: Boolean = false,
    onFilterReset: () -> Unit = {},
    onTabStarted: (Int) -> Unit = {},
): TasksScaffoldState {
    val scope = rememberCoroutineScope()
    val fields = rememberTasksScaffoldFields()
    val user by deps.account.user.collectAsState()
    val userRef = rememberUpdatedState(user)
    val hapticRef = rememberUpdatedState(haptic)
    val filterResetRef = rememberUpdatedState(onFilterReset)
    val tabStartedRef = rememberUpdatedState(onTabStarted)
    // 注入点各自记住：remember 的键保持不变，骨架对象就不会因重组而重建
    val notify: suspend (String) -> Unit = remember(snackbarHostState) {
        { message -> snackbarHostState.showSnackbar(message) }
    }
    val fetchTaskPage: suspend (TaskType, TaskFilter) -> SessionOutcome<TaskPage> = remember(deps) {
        { type, filter -> deps.authCall.call { deps.gateway.tasks(type, filter) } }
    }
    val claimTask: suspend (TaskType, String, String) -> SessionOutcome<String?> = remember(deps) {
        { type, uid, taskId -> deps.authCall.call { deps.gateway.claimTask(type, uid, taskId) } }
    }
    val resolveImageHost: suspend (TaskHostResolution) -> String = remember(deps) {
        { resolution -> resolveScreenDepsImageHost(deps, resolution) }
    }
    // 换筛选（以及声明了 reloadOnAccount 的皮肤换账号）会重建整组 loader：首屏标记与在途请求一起作废
    val filterIndex = fields.filterIndex.intValue.coerceIn(0, TaskFilter.entries.lastIndex)
    val accountKey = if (reloadOnAccount) user?.uid else null
    val state = remember(
        deps,
        nav,
        snackbarHostState,
        fields,
        filterIndex,
        accountKey,
        pageCount,
        hostResolution,
        failureMessage,
        failureFallback,
    ) {
        TasksScaffoldState(
            scope = scope,
            fields = fields,
            failureMessage = failureMessage,
            failureFallback = failureFallback,
            hostResolution = hostResolution,
            pageCount = pageCount,
            userRef = userRef,
            hapticRef = hapticRef,
            filterResetRef = filterResetRef,
            tabStartedRef = tabStartedRef,
            notify = notify,
            fetchTaskPage = fetchTaskPage,
            claimTask = claimTask,
            resolveImageHost = resolveImageHost,
            nav = nav,
        )
    }

    // 换账号后旧账号的佩戴覆盖不能留在界面上
    LaunchedEffect(state.user?.uid) { state.resetEquipped() }

    // 首次进入该档才取数据；换筛选时先清文案、复位视觉，再发起首屏请求
    LaunchedEffect(state, state.clampedTypeIndex) { state.enterTab() }

    return state
}
