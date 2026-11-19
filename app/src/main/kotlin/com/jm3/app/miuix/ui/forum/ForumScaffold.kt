package com.jm3.app.miuix.ui.forum

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.failureCopy
import com.jm3.app.miuix.ui.CommentItemNullableSaver
import com.jm3.app.miuix.ui.planLoad
import com.jm3.app.miuix.ui.route.AppRoutes
import com.jm3.core.model.CommentItem
import com.jm3.core.model.CommentPage
import com.jm3.core.model.ForumScope
import com.jm3.core.paging.PageSlice
import com.jm3.core.paging.pageCountOf
import com.jm3.core.session.SessionOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** 论坛每页条数：接口固定一页 10 条，总页数由 total 折算。 */
internal const val FORUM_PAGE_SIZE = 10

/** 闲聊区规则正文：折叠态只显示提要，展开态显示全文。 */
internal const val FORUM_RULES_TEXT = "禁止发布违法违规、骚扰与剧透内容；发言限 200 字，违规内容会被删除。"

/** 闲聊区规则卡的折叠提要。 */
internal const val FORUM_RULES_HINT = "闲聊区发言前请先了解规则"

/** 论坛加载失败的兜底文案与「保留旧内容」时的前缀，两皮肤口径一致。 */
internal const val FORUM_LOAD_FAILED = "论坛加载失败"
internal const val FORUM_STALE_PREFIX = "加载失败："

/** 楼层回复的三种提示：无作品归属、成功、失败前缀。 */
internal const val FORUM_REPLY_NO_ALBUM = "该楼层没有作品归属，暂不支持回复"
internal const val FORUM_REPLY_SENT = "回复已发送"
internal const val FORUM_REPLY_FAILED = "回复失败"

/** 发帖的两种提示：成功、失败前缀。 */
internal const val FORUM_POSTED = "发布成功"
internal const val FORUM_POST_FAILED = "发布失败"

/** 失败原文不可读时的兜底：皮肤可传自己的主题文案（如 CouiStrings.RetryLater，与本值同串）。 */
internal const val FORUM_FAILURE_FALLBACK = "请稍后重试"

/** 空态标题：闲聊档与其余档位各一句。 */
internal fun forumEmptyTitle(scope: ForumScope): String =
    if (scope == ForumScope.CHAT) "闲聊区还没有帖子" else "暂无话题"

/** 空态引导：闲聊档引导开第一帖，其余档位引导换档或发新帖。 */
internal fun forumEmptyHint(scope: ForumScope): String =
    if (scope == ForumScope.CHAT) "来开第一帖，聊聊作品之外的事" else "换个档位看看，或发一条新帖"

/**
 * 评论页没有页码字段：页码与总页数由请求页和 total 补上，末页之后不再多打一次空页。
 *
 * 两套皮肤原先各写一份（Miuix 内联在 fetchPage 里，COUI 是 couiForumSlice），逐字等价。
 */
internal fun forumSlice(page: CommentPage, requestPage: Int): PageSlice<CommentItem> {
    val pageCount = pageCountOf(page.total, FORUM_PAGE_SIZE)
    return PageSlice(
        items = page.items,
        // total 为 0 时页数未知，只能按本页有没有条目推断是否还有下一页
        hasMore = if (pageCount == null) page.items.isNotEmpty() else requestPage < pageCount,
        total = page.total.takeIf { it > 0 },
        page = requestPage,
        pageCount = pageCount,
    )
}

/**
 * 论坛页的状态编排：档位选择、回复/发帖面板的开关与在途标记、规则卡展开，以及四个动作。
 *
 * 两套皮肤原先各持一份逐字相同的 scopeIndex/replyTarget/replyBusy/composing/composeBusy/
 * rulesExpanded 与同一段 startReply/sendReply/postTopic/openCompose。这里把它们收成一处：
 * 皮肤只画列表与面板，动作怎么排、什么情况下弹哪句提示都在这里。
 *
 * 取数与提示都是注入的（[replyComment]、[postComment]、[notify]）：这里不认识网关与
 * SnackbarHost，因而可以直接在纯 kotlin.test 里驱动。
 *
 * 成功后的重载两皮肤不一样（Miuix 停在原页重取、COUI 滚动模式要合并前序页），
 * 因此不作为构造依赖，而是每次动作传一个 [onSent]/[onPosted]：差异留在调用点，
 * 且调用点能拿到 skin 自己的 loader，不需要在编排里长出一套策略开关。
 */
@Stable
internal class ForumScaffoldState internal constructor(
    internal val scope: CoroutineScope,
    private val fields: ForumScaffoldFields,
    private val loggedIn: () -> Boolean,
    private val openLogin: () -> Unit,
    private val notify: suspend (String) -> Unit,
    private val failureFallback: String,
    private val replyComment: suspend (albumId: String, text: String, commentId: String) -> SessionOutcome<Unit>,
    private val postComment: suspend (text: String) -> SessionOutcome<Unit>,
) {
    /** 选中的档位（未夹取）：越界值只在读取时夹取，与 pager 的落点无关。 */
    val scopeIndex: Int
        get() = fields.scopeIndexState.value

    /** 实际参与取数与渲染的档位：越界的存档值不能让列表取错页。 */
    val clampedScopeIndex: Int
        get() = scopeIndex.coerceIn(0, ForumScope.entries.lastIndex)

    /** 当前待回复的楼层；非空即回复面板可见。 */
    val replyTarget: CommentItem?
        get() = fields.replyTargetState.value

    /** 回复在途：面板的发送键据此禁用。 */
    val replyBusy: Boolean
        get() = fields.replyBusyState.value

    /** 发帖面板是否可见。 */
    val composing: Boolean
        get() = fields.composingState.value

    /** 发帖在途：面板的发布键据此禁用。 */
    val composeBusy: Boolean
        get() = fields.composeBusyState.value

    /** 闲聊档规则卡是否展开。 */
    val rulesExpanded: Boolean
        get() = fields.rulesExpandedState.value

    /** 切档：夹取与 pager 同步仍由皮肤做，这里只落选中值。 */
    fun selectScope(index: Int) {
        fields.scopeIndexState.value = index
    }

    /** 规则卡折叠/展开。 */
    fun toggleRules() {
        fields.rulesExpandedState.value = !fields.rulesExpandedState.value
    }

    /** 关闭回复面板（用户主动收起或点遮罩）。 */
    fun dismissReply() {
        fields.replyTargetState.value = null
    }

    /** 关闭发帖面板。 */
    fun dismissCompose() {
        fields.composingState.value = false
    }

    /** 发帖入口的登录门禁：顶栏按钮与空态按钮共用同一判定。 */
    fun openCompose() {
        if (loggedIn()) fields.composingState.value = true else openLogin()
    }

    /**
     * 回复楼层：闲聊帖缺少作品归属，无法定位接口实体，直接给出提示。
     *
     * 未登录时不落任何状态，只打开登录页——回复面板不能盖在登录页上。
     */
    fun startReply(target: CommentItem) {
        when {
            !loggedIn() -> openLogin()
            target.albumId == null -> scope.launch { notify(FORUM_REPLY_NO_ALBUM) }
            else -> fields.replyTargetState.value = target
        }
    }

    /**
     * 发一次回复。
     *
     * [onSent] 是成功后皮肤自己的重载动作：提示先弹、重载后做，与先前内联实现的顺序一致。
     */
    fun sendReply(text: String, onSent: () -> Unit) {
        val target = replyTarget ?: return
        val albumId = target.albumId ?: return
        if (text.isBlank() || replyBusy) return
        fields.replyBusyState.value = true
        scope.launch {
            val outcome = replyComment(albumId, text, target.id)
            fields.replyBusyState.value = false
            when (outcome) {
                is SessionOutcome.Ok -> {
                    fields.replyTargetState.value = null
                    notify(FORUM_REPLY_SENT)
                    onSent()
                }

                SessionOutcome.Expired -> notify(
                    planLoad(outcome, hasContent = false, failureMessage = FORUM_REPLY_FAILED).message.orEmpty(),
                )

                is SessionOutcome.Failed -> notify(
                    FORUM_REPLY_FAILED + "：" + failureCopy(outcome, failureFallback),
                )
            }
        }
    }

    /**
     * 发一条新帖：全站发帖不带 aid，统一以空串提交，由服务端按账号归属落库。
     *
     * [onPosted] 是成功后皮肤自己的回顶与重载动作（两皮肤的回顶方式与重载策略不同）。
     */
    fun postTopic(text: String, onPosted: () -> Unit) {
        if (text.isBlank() || composeBusy) return
        fields.composeBusyState.value = true
        scope.launch {
            val outcome = postComment(text)
            fields.composeBusyState.value = false
            when (outcome) {
                is SessionOutcome.Ok -> {
                    fields.composingState.value = false
                    notify(FORUM_POSTED)
                    onPosted()
                }

                SessionOutcome.Expired -> notify(
                    planLoad(outcome, hasContent = false, failureMessage = FORUM_POST_FAILED).message.orEmpty(),
                )

                is SessionOutcome.Failed -> notify(
                    FORUM_POST_FAILED + "：" + failureCopy(outcome, failureFallback),
                )
            }
        }
    }
}

/**
 * 承载论坛页可变状态的容器。
 *
 * 由 [rememberForumScaffoldFields] 用 remember/rememberSaveable 建一次；[ForumScaffoldState]
 * 只读不建，因此状态寿命与页面组合一致，不随 remember 键的重建而丢失。
 * 保存粒度沿用原页面：档位、回复目标、发帖面板、规则卡跨进程保留，两个在途标记不保留。
 */
@Stable
internal class ForumScaffoldFields internal constructor(
    internal val scopeIndexState: MutableState<Int>,
    internal val replyTargetState: MutableState<CommentItem?>,
    internal val replyBusyState: MutableState<Boolean>,
    internal val composingState: MutableState<Boolean>,
    internal val composeBusyState: MutableState<Boolean>,
    internal val rulesExpandedState: MutableState<Boolean>,
)

@Composable
private fun rememberForumScaffoldFields(): ForumScaffoldFields {
    val scopeIndex = rememberSaveable { mutableIntStateOf(0) }
    val replyTarget = rememberSaveable(stateSaver = CommentItemNullableSaver) { mutableStateOf<CommentItem?>(null) }
    val replyBusy = remember { mutableStateOf(false) }
    val composing = rememberSaveable { mutableStateOf(false) }
    val composeBusy = remember { mutableStateOf(false) }
    val rulesExpanded = rememberSaveable { mutableStateOf(false) }
    return remember(scopeIndex, replyTarget, replyBusy, composing, composeBusy, rulesExpanded) {
        ForumScaffoldFields(
            scopeIndexState = scopeIndex,
            replyTargetState = replyTarget,
            replyBusyState = replyBusy,
            composingState = composing,
            composeBusyState = composeBusy,
            rulesExpandedState = rulesExpanded,
        )
    }
}

/**
 * 两套论坛皮肤共用的状态入口：remember 状态容器、接线网关取数、登录门禁与提示通道。
 *
 * [loggedIn] 传的是当前值而不是判定函数：编排对象被 remember 固定，
 * 靠 [rememberUpdatedState] 让门禁读到最新那一帧，避免登录后仍判成未登录。
 */
@Composable
internal fun rememberForumScaffoldState(
    deps: ScreenDeps,
    nav: AppRoutes,
    loggedIn: Boolean,
    notify: suspend (String) -> Unit,
    failureFallback: String = FORUM_FAILURE_FALLBACK,
): ForumScaffoldState {
    val fields = rememberForumScaffoldFields()
    val scope = rememberCoroutineScope()
    val loggedInRef = rememberUpdatedState(loggedIn)
    val navRef = rememberUpdatedState(nav)
    val notifyRef = rememberUpdatedState(notify)
    return remember(deps, scope, fields, failureFallback) {
        ForumScaffoldState(
            scope = scope,
            fields = fields,
            loggedIn = { loggedInRef.value },
            openLogin = { navRef.value.login() },
            notify = { message -> notifyRef.value(message) },
            failureFallback = failureFallback,
            replyComment = { albumId, text, commentId ->
                deps.authCall.call { deps.gateway.replyComment(albumId, text, commentId) }
            },
            postComment = { text ->
                deps.authCall.call { deps.gateway.postComment("", text) }
            },
        )
    }
}
