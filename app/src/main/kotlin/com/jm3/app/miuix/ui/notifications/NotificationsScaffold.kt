package com.jm3.app.miuix.ui.notifications

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.failureCopy
import com.jm3.app.miuix.ui.ScreenLoadPhase
import com.jm3.app.miuix.ui.planLoad
import com.jm3.core.NOTIFICATION_TYPE_FOLLOW
import com.jm3.core.model.NotificationItem
import com.jm3.core.model.NotificationPage
import com.jm3.core.session.SessionOutcome
import com.jm3.core.session.UnreadBadge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.SnackbarHostState

/**
 * 信箱页的状态编排骨架：Miuix 与 COUI 两份皮肤共用同一套分类、文案与装载状态。
 *
 * 本文件不含任何皮肤控件：只有不可变状态、纯文案/筛选函数，以及一个负责 remember 与
 * LaunchedEffect 编排的入口。皮肤只保留视觉结构、页签保存方式与手势。
 */

/** 展开后最多直接铺开的作品更新条数；再多就让用户手动「展开全部」。 */
internal const val NOTIFICATION_PREVIEW_UPDATES = 2

/** 信箱的分类档位；[type] 为 null 表示不筛。 */
internal enum class NotificationTab(val label: String, val type: String?) {
    ALL("全部", null),
    FOLLOW("追更", NOTIFICATION_TYPE_FOLLOW),
    NOTICE("站内", "site_notice"),
}

/** 列表装载的整体失败文案；已有内容时由 planLoad 降级成刷新失败提示。 */
internal const val NotificationLoadFailureMessage: String = "信箱加载失败"

/** 标记已读失败时的 planLoad 文案（会话失效分支用它当兜底）。 */
internal const val NotificationMarkFailureMessage: String = "标记失败"

/** 标记已读失败的提示前缀与通用兜底（两份皮肤原来的字面量相同）。 */
internal const val NotificationMarkFailurePrefix: String = "标记失败："
internal const val NotificationRetryLater: String = "请稍后重试"

/** 单条通知的类型标签。 */
internal fun notificationTypeLabel(type: String?): String =
    if (type == NOTIFICATION_TYPE_FOLLOW) "漫画追踪通知" else "站内通知"

/** 追更列表的展开/收起文案：条目超过预览数才会出现。 */
internal fun notificationUpdatesToggleText(expanded: Boolean, count: Int): String =
    if (expanded) "收起" else "展开全部 $count 条"

/** 底部标记动作的文案：在途时先盖住读态。[marking] 为 false 时同侧滑露出的动作复用。 */
internal fun notificationMarkActionText(marking: Boolean, read: Boolean): String = when {
    marking -> "处理中…"
    read -> "标记未读"
    else -> "标记已读"
}

/** 顶栏副标题：未读数与「全部已读」只在拿到列表后才有意义。 */
internal fun notificationSubtitle(page: NotificationPage?, unread: Int): String = when {
    page == null -> ""
    unread > 0 -> "$unread 条未读"
    else -> "全部已读"
}

/** 页签标签：有未读时带上计数。 */
internal fun notificationTabLabels(items: List<NotificationItem>): List<String> =
    NotificationTab.entries.map { entry ->
        val unread = items.count { !it.read && (entry.type == null || it.type == entry.type) }
        if (unread > 0) "${entry.label} $unread" else entry.label
    }

/** 当前页签可见的通知。 */
internal fun notificationItemsFor(
    items: List<NotificationItem>,
    tab: NotificationTab,
): List<NotificationItem> = items.filter { tab.type == null || it.type == tab.type }

/** 空态说明按档位给具体去处，比统一的「暂无」更能指路。 */
internal fun notificationEmptyHint(tab: NotificationTab): String = when (tab) {
    NotificationTab.NOTICE -> "暂无站内消息"
    NotificationTab.FOLLOW -> "订阅作品更新后会在这里收到通知"
    NotificationTab.ALL -> "订阅更新与站内消息都会出现在这里"
}

/** 展开态切换：同一 id 再点一次收起。 */
internal fun notificationExpandedToggled(expandedIds: Set<String>, itemId: String): Set<String> =
    if (itemId in expandedIds) expandedIds - itemId else expandedIds + itemId

/** 一次装载的落点：更新后的状态，以及需要弹给用户的提示（降级成提示而非整屏错误时非空）。 */
internal data class NotificationLoadResult(
    val state: NotificationListState,
    val notice: String?,
)

/** 信箱列表里会随装载变化的部分：可比较的不可变快照，皮肤只读不改。 */
internal data class NotificationListState(
    val page: NotificationPage? = null,
    val loading: Boolean = true,
    val error: String? = null,
    /** 会话失效与「刷新失败」要分开：前者给重试按钮是骗人的，只能去登录 */
    val sessionExpired: Boolean = false,
    val refreshing: Boolean = false,
    val markingId: String? = null,
    val requestToken: Int = 0,
    val loadedUid: String? = null,
) {
    val items: List<NotificationItem> get() = page?.items.orEmpty()
    val unread: Int get() = items.count { !it.read }
    val subtitle: String get() = notificationSubtitle(page, unread)
    val tabLabels: List<String> get() = notificationTabLabels(items)

    /** 发起一次装载：编号自增用于丢弃过期响应，[showLoading] 决定要不要整屏转圈。 */
    fun loadStarted(showLoading: Boolean): NotificationListState =
        copy(requestToken = requestToken + 1, loading = loading || showLoading)

    /**
     * 装载落定：编号已不是最新则整条结论丢弃（返回 null），这就是换页签/换账号时的竞态守卫。
     *
     * 有数据时保留列表、只弹提示；没有数据才落到整屏的失效或错误态。
     * [guardStaleToken] 为 false 时不丢弃过期结论：COUI 皮肤原本没有这道守卫。
     */
    fun loadDelivered(
        token: Int,
        outcome: SessionOutcome<NotificationPage>,
        clearFailureOnKeptContent: Boolean,
        guardStaleToken: Boolean = true,
    ): NotificationLoadResult? {
        if (guardStaleToken && token != requestToken) return null
        val plan = planLoad(outcome, hasContent = page != null, failureMessage = NotificationLoadFailureMessage)
        if (plan.phase == ScreenLoadPhase.Content) {
            val settled = when (outcome) {
                is SessionOutcome.Ok -> copy(page = outcome.value, error = null, sessionExpired = false)
                // 保留内容时是否顺手清掉上一次的错误：两份皮肤原来的写法不同，用参数维持各自行为
                else -> if (clearFailureOnKeptContent) copy(error = null, sessionExpired = false) else this
            }
            return NotificationLoadResult(settled.copy(loading = false, refreshing = false), plan.notice)
        }
        return NotificationLoadResult(
            copy(
                loading = false,
                refreshing = false,
                sessionExpired = plan.phase == ScreenLoadPhase.Expired,
                error = plan.message,
            ),
            null,
        )
    }

    /** 开始标记某条：已有条目在途时返回 null，调用方据此放弃这次点击。 */
    fun markingStarted(itemId: String): NotificationListState? =
        if (markingId != null) null else copy(markingId = itemId)

    /** 标记请求落定（不论结论）：先清在途标记。 */
    fun markingCleared(): NotificationListState = copy(markingId = null)

    /** 标记成功：把该条目标成目标读态。 */
    fun marked(itemId: String, read: Boolean): NotificationListState = copy(
        markingId = null,
        page = page?.let { current ->
            current.copy(items = current.items.map { if (it.id == itemId) it.copy(read = read) else it })
        },
    )

    /** 登出：清空列表与全部瞬时态，并作废旧请求。 */
    fun accountCleared(): NotificationListState = copy(
        requestToken = requestToken + 1,
        page = null,
        error = null,
        sessionExpired = false,
        loading = false,
        refreshing = false,
        markingId = null,
        loadedUid = null,
    )

    /** 换号：只有真的换了人才清空列表。 */
    fun accountSwitched(uid: String): NotificationListState =
        if (loadedUid != null && loadedUid != uid) {
            copy(page = null, error = null, sessionExpired = false, loadedUid = uid)
        } else {
            copy(loadedUid = uid)
        }

    /** 这个账号的列表要不要拉：没有内容、失效、报错或换了人时都要。 */
    fun needsLoadFor(uid: String): Boolean =
        page == null || sessionExpired || error != null || loadedUid != uid
}

/**
 * 信箱页的动作真源。
 *
 * 未读角标 [UnreadBadge] 是底栏的唯一真源，本类只在装载成功与标记成功后同步它，
 * 皮肤不再各自维护「列表变了要记得刷角标」这条约束。
 */
@Stable
internal class NotificationsScaffoldState internal constructor(
    private val deps: ScreenDeps,
    private val scope: CoroutineScope,
    private val snackbarHostState: SnackbarHostState,
    private val unreadBadge: UnreadBadge,
    /** Miuix 皮肤按账号与请求代号丢弃过期响应；COUI 皮肤原本不设这道守卫 */
    private val guardStaleResponses: Boolean,
    /** 保留内容时是否顺手清掉上一次的错误：COUI 清理，Miuix 保留 */
    private val clearFailureOnKeptContent: Boolean,
    private val expandedIdsSource: MutableState<Set<String>>,
    initial: NotificationListState = NotificationListState(),
) {
    private var fields by mutableStateOf(initial)

    val page: NotificationPage? get() = fields.page
    val loading: Boolean get() = fields.loading
    val error: String? get() = fields.error
    val sessionExpired: Boolean get() = fields.sessionExpired
    val refreshing: Boolean get() = fields.refreshing
    val markingId: String? get() = fields.markingId
    val items: List<NotificationItem> get() = fields.items
    val unread: Int get() = fields.unread
    val subtitle: String get() = fields.subtitle
    val tabLabels: List<String> get() = fields.tabLabels
    val expandedIds: Set<String> get() = expandedIdsSource.value

    fun itemsFor(tab: NotificationTab): List<NotificationItem> = notificationItemsFor(fields.items, tab)

    fun isExpanded(itemId: String): Boolean = itemId in expandedIdsSource.value

    fun toggleExpanded(itemId: String) {
        expandedIdsSource.value = notificationExpandedToggled(expandedIdsSource.value, itemId)
    }

    private fun syncUnread(items: List<NotificationItem>) {
        unreadBadge.sync(items.count { !it.read })
    }

    /**
     * 账号态入口：登出清空并重拉，换号清列表，首次进入直接拉。
     *
     * 与装载共用同一个 requestToken：登出时自增一次，正在路上的旧响应回来就会被丢弃。
     */
    fun syncAccount(uid: String?) {
        if (uid == null) {
            fields = fields.accountCleared()
            expandedIdsSource.value = emptySet()
            load()
        } else if (fields.needsLoadFor(uid)) {
            val switching = fields.loadedUid != null && fields.loadedUid != uid
            fields = fields.accountSwitched(uid)
            if (switching) expandedIdsSource.value = emptySet()
            load()
        }
    }

    fun load(showLoading: Boolean = true) {
        fields = fields.loadStarted(showLoading)
        val token = fields.requestToken
        scope.launch {
            val outcome = deps.authCall.call { deps.gateway.notifications() }
            val result = fields.loadDelivered(token, outcome, clearFailureOnKeptContent, guardStaleResponses)
                ?: return@launch
            fields = result.state
            if (outcome is SessionOutcome.Ok) syncUnread(fields.items)
            result.notice?.let { notice -> scope.launch { snackbarHostState.showSnackbar(notice) } }
        }
    }

    /** 下拉刷新：不动整屏转圈，只置刷新态再重拉。 */
    fun refresh() {
        fields = fields.copy(refreshing = true)
        load(showLoading = false)
    }

    /** COUI 皮肤的刷新手势：已经在刷新就不重复触发。 */
    fun refreshIfIdle() {
        if (!fields.refreshing) refresh()
    }

    /** 已读/未读可双向切换：原版格子上的读信图标同样按当前状态取反。 */
    fun toggleRead(item: NotificationItem) {
        // 单条串行：同一行连点两次会打架，用 markingId 挡住
        fields = fields.markingStarted(item.id) ?: return
        val target = !item.read
        val uidAtStart = deps.account.user.value?.uid
        val tokenAtStart = fields.requestToken
        scope.launch {
            val outcome = deps.authCall.call { deps.gateway.markNotification(item.id, target) }
            fields = fields.markingCleared()
            val stale = uidAtStart != deps.account.user.value?.uid || tokenAtStart != fields.requestToken
            if (guardStaleResponses && stale) return@launch
            when (outcome) {
                is SessionOutcome.Ok -> {
                    fields = fields.marked(item.id, target)
                    syncUnread(fields.items)
                }

                SessionOutcome.Expired -> snackbarHostState.showSnackbar(
                    planLoad(outcome, hasContent = false, failureMessage = NotificationMarkFailureMessage)
                        .message
                        .orEmpty(),
                )

                is SessionOutcome.Failed -> snackbarHostState.showSnackbar(
                    NotificationMarkFailurePrefix + failureCopy(outcome, NotificationRetryLater),
                )
            }
        }
    }
}

/**
 * 皮肤入口：负责 remember、展开态的保存与账号切换的 LaunchedEffect 编排。
 *
 * 差异用参数表达——过期响应守卫与「保留内容时清不清错误」，两份皮肤原来的写法不同。
 */
@Composable
internal fun rememberNotificationsScaffoldState(
    deps: ScreenDeps,
    snackbarHostState: SnackbarHostState,
    unreadBadge: UnreadBadge,
    guardStaleResponses: Boolean,
    clearFailureOnKeptContent: Boolean,
): NotificationsScaffoldState {
    val scope = rememberCoroutineScope()
    val user by deps.account.user.collectAsState()
    val uid = user?.uid
    // 展开态要熬过旋屏重建：Set 不在 Bundle 可存类型里，拆成 List 存取
    val expandedIds = rememberSaveable(
        stateSaver = listSaver<Set<String>, String>(
            save = { it.toList() },
            restore = { it.toSet() },
        ),
    ) { mutableStateOf(emptySet<String>()) }
    val state = remember(deps) {
        NotificationsScaffoldState(
            deps = deps,
            scope = scope,
            snackbarHostState = snackbarHostState,
            unreadBadge = unreadBadge,
            guardStaleResponses = guardStaleResponses,
            clearFailureOnKeptContent = clearFailureOnKeptContent,
            expandedIdsSource = expandedIds,
        )
    }
    LaunchedEffect(state, uid) {
        if (guardStaleResponses) state.syncAccount(uid) else state.load()
    }
    return state
}
