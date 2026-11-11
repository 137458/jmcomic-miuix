// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.page

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.CouiDialogActionStyle
import com.itos.coui.dialog.CouiDialogDefaults
import com.itos.coui.dialog.CouiDialogPlacement
import com.itos.coui.dialog.Dialog
import com.itos.coui.effect.SpotlightPreset
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.layout.Footer
import com.itos.coui.scroll.rememberCouiOverscrollEffect
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.shape.CouiShape
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.text.rememberCouiTextStyle
import com.itos.coui.theme.CouiTheme as CouiKitTheme
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.failureCopy
import com.jm3.app.miuix.ui.AlbumCoverImage
import com.jm3.app.miuix.ui.COMMENT_REPLY_MAX_LENGTH
import com.jm3.app.miuix.ui.CommentItemNullableSaver
import com.jm3.app.miuix.ui.rememberSavedDialogDraft
import com.jm3.app.miuix.ui.GlassSnackbarHost
import com.jm3.app.miuix.ui.ScreenLoadPhase
import com.jm3.app.miuix.ui.ScreenLoadPlan
import com.jm3.app.miuix.ui.coui.component.CouiBodyText
import com.jm3.app.miuix.ui.coui.component.rememberCouiBodyTextStyle
import com.jm3.app.miuix.ui.coui.component.CouiCommentRow
import com.jm3.app.miuix.ui.coui.component.CouiEmptyGroup
import com.jm3.app.miuix.ui.coui.component.CouiErrorGroup
import com.jm3.app.miuix.ui.coui.component.CouiFeedLoginGroup
import com.jm3.app.miuix.ui.coui.component.CouiFeedRefreshAction
import com.jm3.app.miuix.ui.coui.component.CouiGroupRow
import com.jm3.app.miuix.ui.coui.component.CouiLoadMoreFooter
import com.jm3.app.miuix.ui.coui.component.CouiLoadingGroup
import com.jm3.app.miuix.ui.coui.component.CouiMinTouchTarget
import com.jm3.app.miuix.ui.coui.component.CouiPagingGroup
import com.jm3.app.miuix.ui.coui.component.CouiScreenScaffold
import com.jm3.app.miuix.ui.coui.component.CouiTopBarAction
import com.jm3.app.miuix.ui.coui.component.CouiTopBarSegmentedTabs
import com.jm3.app.miuix.ui.coui.component.rememberCouiScreenScrollBehavior
import com.jm3.app.miuix.ui.coui.component.couiDialogMaterial
import com.jm3.app.miuix.ui.coui.component.couiPressable
import com.jm3.app.miuix.ui.coui.component.couiWorkCardShape
import com.jm3.app.miuix.ui.coui.component.rememberRetainedTarget
import com.jm3.app.miuix.ui.coui.icon.CouiIcons
import com.jm3.app.miuix.ui.coui.theme.CouiStrings
import com.jm3.app.miuix.ui.coui.theme.CouiTheme
import com.jm3.app.miuix.ui.planLoad
import com.jm3.app.miuix.ui.readableWidth
import com.jm3.app.miuix.ui.rememberSelectionHaptic
import com.jm3.app.miuix.ui.rememberSyncedTabPagerState
import com.jm3.core.appendedBy
import com.jm3.core.model.CommentItem
import com.jm3.core.model.CommentSegment
import com.jm3.core.model.ForumScope
import com.jm3.core.paging.PagedFeed
import com.jm3.core.replacedBy
import com.jm3.core.session.SessionOutcome
import com.jm3.core.settings.PaginationMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SnackbarHostState

/** 论坛每页条数：接口固定一页 10 条，总页数由 total 折算。 */
private const val FORUM_PAGE_SIZE = 10

/** 闲聊区规则正文：折叠态只显示提要，展开态显示全文。 */
private const val FORUM_RULES_TEXT = "禁止发布违法违规、骚扰与剧透内容；发言限 200 字，违规内容会被删除。"

/** 论坛正文限行：超长楼层会铺满整屏并顶掉后续话题。 */
private const val FORUM_BODY_MAX_LINES = 8

/** 回复缩进层级上限：再深不再吃正文宽度。 */
private const val FORUM_MAX_INDENT_DEPTH = 2

private sealed interface ForumUiState {
    data object Loading : ForumUiState
    data object Loaded : ForumUiState
    // 会话失效与普通失败分开：前者只能去登录，给重试按钮是骗人的
    data class Failed(val plan: ScreenLoadPlan) : ForumUiState
}

/**
 * COUI 社区论坛：全部/漫画评论/闲聊三档，支持发帖、回复与两种分页模式。
 *
 * 话题按行卡分组，楼层沿用账号头像 + 等级 + 徽章 + 分段正文的信息结构；
 * 总页数由接口 total 折算（接口本身不带页码），点击分页与滚动分页都保。
 */
@Composable
internal fun CouiForumScreen(
    deps: ScreenDeps,
    onBack: () -> Unit,
    onOpenLogin: () -> Unit,
    snackbarHostState: SnackbarHostState,
    onOpenAlbum: (albumId: String) -> Unit = {},
) {
    val user by deps.account.user.collectAsState()
    val pageMode = deps.preferences.paginationMode
    val selectionHaptic = rememberSelectionHaptic()
    val scope = rememberCoroutineScope()
    val listStates = remember { List(ForumScope.entries.size) { LazyListState() } }

    var scopeIndex by rememberSaveable { mutableIntStateOf(0) }
    val clampedScopeIndex = scopeIndex.coerceIn(0, ForumScope.entries.lastIndex)
    val feeds = remember { mutableStateMapOf<Int, PagedFeed<CommentItem>>() }
    val states = remember { mutableStateMapOf<Int, ForumUiState>() }
    var refreshing by remember { mutableStateOf(false) }
    // 失败页码：重试要回到失败的那一页，而不是一律回第一页
    val failedPages = remember { mutableStateMapOf<Int, Int>() }
    var replyTarget by rememberSaveable(stateSaver = CommentItemNullableSaver) {
        mutableStateOf<CommentItem?>(null)
    }
    var replyBusy by remember { mutableStateOf(false) }
    var composing by rememberSaveable { mutableStateOf(false) }
    var composeBusy by remember { mutableStateOf(false) }
    var rulesExpanded by rememberSaveable { mutableStateOf(false) }
    // 请求代际号 + 在途协程：切档/翻页后晚到的旧响应不得覆盖新状态
    var loadToken by remember { mutableIntStateOf(0) }
    var inFlightJob by remember { mutableStateOf<Job?>(null) }
    var lastPageMode by remember { mutableStateOf(pageMode) }

    fun applyFailure(targetTab: Int, outcome: SessionOutcome<*>, inFlight: PagedFeed<CommentItem>) {
        val plan = planLoad(
            outcome = outcome,
            hasContent = inFlight.items.isNotEmpty(),
            failureMessage = "论坛加载失败",
            stalePrefix = "加载失败：",
        )
        // 保留内容时只把页脚标成可重试；整屏失败要撤掉在途标记，否则页脚永远转圈
        feeds[targetTab] = if (plan.keepContent) inFlight.failLoadMore() else inFlight.copy(loadingMore = false)
        if (plan.phase == ScreenLoadPhase.Content) {
            states[targetTab] = ForumUiState.Loaded
            plan.notice?.let { notice -> scope.launch { snackbarHostState.showSnackbar(notice) } }
        } else {
            states[targetTab] = ForumUiState.Failed(plan)
        }
    }

    fun fetch(
        targetPage: Int,
        replace: Boolean,
        showLoading: Boolean,
        targetTab: Int = clampedScopeIndex,
    ) {
        val currentFeed = feeds[targetTab] ?: PagedFeed()
        val targetScope = ForumScope.entries[targetTab]
        // 追加请求在途时不再发起新的翻页：连点会并发打同一页，返回顺序错乱后列表会重复
        val requested = if (replace) currentFeed else currentFeed.beginLoadMore()
        if (!replace && requested === currentFeed) return
        if (showLoading) states[targetTab] = ForumUiState.Loading
        feeds[targetTab] = requested
        failedPages[targetTab] = targetPage
        inFlightJob?.cancel()
        val token = ++loadToken
        inFlightJob = scope.launch {
            val outcome = deps.authCall.call { deps.gateway.forum(targetScope, targetPage) }
            if (token != loadToken) return@launch
            refreshing = false
            when (outcome) {
                is SessionOutcome.Ok -> {
                    val resultPage = outcome.value
                    // 评论页没有页码字段：页码与总页数由请求页和 total 补上
                    val pageCount = if (resultPage.total > 0) {
                        (resultPage.total + FORUM_PAGE_SIZE - 1) / FORUM_PAGE_SIZE
                    } else {
                        null
                    }
                    feeds[targetTab] = if (replace) {
                        PagedFeed<CommentItem>().replacedBy(resultPage)
                    } else {
                        requested.appendedBy(resultPage)
                    }.copy(page = targetPage, pageCount = pageCount)
                    states[targetTab] = ForumUiState.Loaded
                }
                SessionOutcome.Expired -> applyFailure(targetTab, outcome, requested)
                is SessionOutcome.Failed -> applyFailure(targetTab, outcome, requested)
            }
        }
    }

    /** 回复楼层：闲聊帖缺少作品归属，无法定位接口实体，直接给出提示。 */
    fun startReply(target: CommentItem) {
        when {
            user == null -> onOpenLogin()
            target.albumId == null -> scope.launch {
                snackbarHostState.showSnackbar("该楼层没有作品归属，暂不支持回复")
            }
            else -> replyTarget = target
        }
    }

    fun sendReply(text: String) {
        val target = replyTarget ?: return
        val albumId = target.albumId ?: return
        if (text.isBlank() || replyBusy) return
        replyBusy = true
        scope.launch {
            val outcome = deps.authCall.call { deps.gateway.replyComment(albumId, text, target.id) }
            replyBusy = false
            when (outcome) {
                is SessionOutcome.Ok -> {
                    replyTarget = null
                    snackbarHostState.showSnackbar("回复已发送")
                    val activePage = (feeds[clampedScopeIndex] ?: PagedFeed()).page
                    fetch(activePage, replace = true, showLoading = false, targetTab = clampedScopeIndex)
                }
                SessionOutcome.Expired -> snackbarHostState.showSnackbar(
                    planLoad(outcome, hasContent = false, failureMessage = "回复失败").message.orEmpty(),
                )
                is SessionOutcome.Failed -> snackbarHostState.showSnackbar(
                    "回复失败：" + failureCopy(outcome.cause, CouiStrings.RetryLater),
                )
            }
        }
    }

    /** 发帖：全站发帖不带 aid，按空串提交，由服务端按账号归属落库。 */
    fun postTopic(text: String) {
        if (text.isBlank() || composeBusy) return
        composeBusy = true
        scope.launch {
            val outcome = deps.authCall.call { deps.gateway.postComment("", text) }
            composeBusy = false
            when (outcome) {
                is SessionOutcome.Ok -> {
                    composing = false
                    snackbarHostState.showSnackbar("发布成功")
                    // 新帖落在第一页首位，回到顶部才能看到刚发的内容
                    val activeTab = clampedScopeIndex
                    listStates[activeTab].requestScrollToItem(0)
                    fetch(targetPage = 1, replace = true, showLoading = false, targetTab = activeTab)
                }
                SessionOutcome.Expired -> snackbarHostState.showSnackbar(
                    planLoad(outcome, hasContent = false, failureMessage = "发布失败").message.orEmpty(),
                )
                is SessionOutcome.Failed -> snackbarHostState.showSnackbar(
                    "发布失败：" + failureCopy(outcome.cause, CouiStrings.RetryLater),
                )
            }
        }
    }

    /** 发帖入口的登录门禁：顶栏按钮与空态按钮共用同一判定。 */
    fun openCompose() {
        if (user == null) onOpenLogin() else composing = true
    }

    // 切档或换分页模式时重载；已缓存档位划回时保留列表并静默刷新
    LaunchedEffect(clampedScopeIndex, pageMode) {
        val modeChanged = lastPageMode != pageMode
        if (modeChanged) {
            feeds.clear()
            states.clear()
            failedPages.clear()
            lastPageMode = pageMode
        }
        val showLoading = modeChanged || states[clampedScopeIndex] !is ForumUiState.Loaded
        if (modeChanged) {
            listStates[clampedScopeIndex].requestScrollToItem(0)
        }
        fetch(targetPage = 1, replace = true, showLoading = showLoading, targetTab = clampedScopeIndex)
    }

    CouiTheme {
        val overscroll = rememberCouiOverscrollEffect()
        val scrollBehavior = rememberCouiScreenScrollBehavior {
            val active = listStates[clampedScopeIndex]
            active.canScrollForward || active.canScrollBackward
        }
        val pagerState = rememberSyncedTabPagerState(
            selectedTab = clampedScopeIndex,
            pageCount = ForumScope.entries.size,
            onTabSelected = {
                if (it != scopeIndex) {
                    selectionHaptic()
                    scopeIndex = it
                }
            },
        )

        CouiScreenScaffold(
            title = "社区论坛",
            onBack = onBack,
            scrollBehavior = scrollBehavior,
            actions = {
                CouiTopBarAction(
                    icon = CouiIcons.Comment,
                    contentDescription = "发帖",
                    onClick = { openCompose() },
                    modifier = Modifier.padding(end = 4.dp),
                )
                CouiFeedRefreshAction(
                    refreshing = refreshing,
                    onRefresh = {
                        if (!refreshing) {
                            refreshing = true
                            fetch(targetPage = 1, replace = true, showLoading = false, targetTab = clampedScopeIndex)
                        }
                    },
                )
            },
            topBarBottom = {
                CouiTopBarSegmentedTabs(
                    items = ForumScope.entries.map { it.label },
                    selectedIndex = clampedScopeIndex,
                    onSelect = {
                        if (it != scopeIndex) {
                            selectionHaptic()
                            scopeIndex = it
                        }
                    },
                )
            },
        ) { contentPadding ->
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { pageIdx ->
                val pageScope = ForumScope.entries[pageIdx]
                val pageFeed = feeds[pageIdx] ?: PagedFeed()
                val pageState = states[pageIdx] ?: ForumUiState.Loading
                val pageFailedAt = failedPages[pageIdx] ?: 1
                val pageListState = listStates[pageIdx]
                val pageItems = pageFeed.items
                val pageTopicTotal = pageFeed.total ?: 0
                val pageTotalPages = pageFeed.pageCount ?: pageFeed.page

                CouiFeedPrefetchEffect(
                    feed = pageFeed,
                    rowStart = 0,
                    listState = pageListState,
                    enabled = pageMode != PaginationMode.CLICK &&
                        pageState == ForumUiState.Loaded &&
                        pageIdx == clampedScopeIndex,
                    onPrefetch = {
                        fetch(pageFeed.page + 1, replace = false, showLoading = false, targetTab = pageIdx)
                    },
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .readableWidth()
                        .consumeWindowInsets(contentPadding),
                    state = pageListState,
                    contentPadding = contentPadding,
                    overscrollEffect = overscroll,
                ) {
                    when (pageState) {
                        ForumUiState.Loading -> item(key = "forum-loading") {
                            CouiLoadingGroup(
                                modifier = Modifier.padding(top = 8.dp),
                                text = "正在加载话题…",
                            )
                        }
                        is ForumUiState.Failed -> if (pageState.plan.phase == ScreenLoadPhase.Expired) {
                            item(key = "forum-expired") {
                                CouiFeedLoginGroup(
                                    message = pageState.plan.message.orEmpty(),
                                    onOpenLogin = onOpenLogin,
                                )
                            }
                        } else {
                            item(key = "forum-error") {
                                CouiErrorGroup(
                                    message = pageState.plan.message.orEmpty(),
                                    onRetry = {
                                        fetch(
                                            targetPage = pageFailedAt,
                                            replace = true,
                                            showLoading = true,
                                            targetTab = pageIdx,
                                        )
                                    },
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                        }
                        ForumUiState.Loaded -> {
                            // 闲聊档的规则卡：折叠态给提要，展开态给全文
                            if (pageScope == ForumScope.CHAT) {
                                item(key = "forum-rules") {
                                    CouiForumRulesRow(
                                        expanded = rulesExpanded,
                                        onToggle = { rulesExpanded = !rulesExpanded },
                                    )
                                }
                            }
                            if (pageItems.isEmpty()) {
                                item(key = "forum-empty") {
                                    CouiEmptyGroup(
                                        text = if (pageScope == ForumScope.CHAT) "闲聊区还没有帖子" else "暂无话题",
                                        modifier = Modifier.padding(top = 8.dp),
                                    )
                                }
                                item(key = "forum-empty-hint") {
                                    Footer(
                                        text = if (pageScope == ForumScope.CHAT) {
                                            "来开第一帖，聊聊作品之外的事"
                                        } else {
                                            "换个档位看看，或发一条新帖"
                                        },
                                    )
                                }
                                item(key = "forum-empty-action") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = CouiPageDefaults.horizontalMargin, vertical = 8.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        ForumCapsuleButton(text = "发布新帖", onClick = { openCompose() })
                                    }
                                }
                            } else {
                                if (pageTopicTotal > 0) {
                                    item(key = "forum-count") { Footer(text = "共 " + pageTopicTotal + " 条话题") }
                                }
                                itemsIndexed(pageItems, key = { _, item -> "forum-" + item.id }) { index, item ->
                                    CouiGroupRow(
                                        isFirst = index == 0,
                                        isLast = index == pageItems.lastIndex,
                                    ) {
                                        CouiForumTopic(
                                            item = item,
                                            onReply = { startReply(it) },
                                            onOpenAlbum = onOpenAlbum,
                                        )
                                    }
                                }
                                item(key = "forum-footer") {
                                    if (pageMode == PaginationMode.CLICK) {
                                        // 只有一页时翻页条没有信息量，不占位
                                        if (pageTotalPages > 1) {
                                            CouiPagingGroup(
                                                page = pageFeed.page,
                                                pageCount = pageTotalPages,
                                                hasMore = pageFeed.hasMore,
                                                onJumpPage = { target ->
                                                    if (target != pageFeed.page) {
                                                        pageListState.requestScrollToItem(0)
                                                        fetch(target, replace = true, showLoading = false, targetTab = pageIdx)
                                                    }
                                                },
                                                modifier = Modifier.padding(top = 8.dp),
                                            )
                                        }
                                    } else {
                                        CouiLoadMoreFooter(
                                            feed = pageFeed,
                                            errorMessage = CouiStrings.LoadFailed,
                                            onRetry = {
                                                fetch(pageFeed.page + 1, replace = false, showLoading = false, targetTab = pageIdx)
                                            },
                                            modifier = Modifier.padding(top = 8.dp),
                                        )
                                    }
                                }
                                item(key = "forum-tail") {
                                    Spacer(modifier = Modifier.height(CouiPageDefaults.groupSpacing))
                                }
                            }
                        }
                    }
                }
            }
        }

        GlassSnackbarHost(
            state = snackbarHostState,
            modifier = Modifier.fillMaxSize(),
        )

        CouiForumInputDialog(
            visible = composing,
            title = "发布新帖",
            busy = composeBusy,
            confirmText = "发布",
            busyConfirmText = "发布中…",
            placeholder = "说点什么（最多 " + COMMENT_REPLY_MAX_LENGTH + " 字）",
            onDismiss = { composing = false },
            onSend = { postTopic(it) },
        )
        val retainedReplyAuthor = rememberRetainedTarget(replyTarget?.author?.takeIf { it.isNotBlank() })
        CouiForumInputDialog(
            visible = replyTarget != null,
            title = if (retainedReplyAuthor != null) "回复 $retainedReplyAuthor" else "回复",
            targetKey = replyTarget?.id,
            busy = replyBusy,
            confirmText = "发送",
            busyConfirmText = "发送中…",
            placeholder = "写下你的回复（最多 " + COMMENT_REPLY_MAX_LENGTH + " 字）",
            onDismiss = { replyTarget = null },
            onSend = { sendReply(it) },
        )

    }
}

/**
 * 闲聊档规则行：折叠只给提要，展开给全文；整行可点，行尾给出动作提示。
 */
@Composable
private fun CouiForumRulesRow(expanded: Boolean, onToggle: () -> Unit) {
    val scheme = CouiKitTheme.colorScheme
    CouiGroupRow(isFirst = true, isLast = true) {
        val shape = couiWorkCardShape()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .couiPressable(shape = shape, minTouchTarget = CouiMinTouchTarget, onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "社区规则",
                    kind = CouiTextKind.ItemTitle,
                    color = scheme.onSurface,
                    maxLines = 1,
                    textAlign = TextAlign.Start,
                )
                Text(
                    text = if (expanded) FORUM_RULES_TEXT else "闲聊区发言前请先了解规则",
                    kind = CouiTextKind.GroupFooter,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Start,
                )
            }
            Text(
                text = if (expanded) "收起" else "展开",
                kind = CouiTextKind.ItemValue,
                color = scheme.accent,
                maxLines = 1,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

/**
 * 单条话题：楼层内容 + 关联作品入口。
 *
 * 楼层按扁平化后的行渲染，回复带缩进竖线但不递归，避免深层回复把正文挤成一条。
 */
@Composable
private fun CouiForumTopic(
    item: CommentItem,
    onReply: (CommentItem) -> Unit,
    onOpenAlbum: (String) -> Unit,
) {
    val scheme = CouiKitTheme.colorScheme
    val albumId = item.albumId
        ?: item.albumName?.filter { it.isDigit() }?.takeIf { it.isNotBlank() }
    val albumName = item.albumName?.takeIf { albumId != null }
    Column(modifier = Modifier.fillMaxWidth()) {
        CouiCommentRow(item = item, depth = 0, bodyMaxLines = FORUM_BODY_MAX_LINES, onReply = onReply)
        val replies = remember(item.id) { flattenForumReplies(item) }
        replies.forEach { (reply, depth) ->
            CouiCommentRow(item = reply, depth = depth, bodyMaxLines = FORUM_BODY_MAX_LINES, onReply = onReply)
        }
        if (albumName != null && albumId != null) {
            val rowShape = CouiShape(radius = 10.dp, cornerWeight = 3f)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
                    .clip(rowShape)
                    .background(scheme.container)
                    .couiPressable(shape = rowShape, onClick = { onOpenAlbum(albumId) })
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .width(48.dp)
                        .height(64.dp)
                        .clip(CouiShape(radius = 8.dp, cornerWeight = 3f))
                        .background(scheme.card),
                ) {
                    AlbumCoverImage(
                        url = item.albumCoverUrl,
                        contentDescription = albumName,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "相关作品",
                        kind = CouiTextKind.GroupFooter,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        textAlign = TextAlign.Start,
                    )
                    Text(
                        text = albumName,
                        kind = CouiTextKind.ItemSummary,
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Start,
                    )
                }
                Icon(
                    imageVector = CouiIcons.Forward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 楼层扁平化：只保留两层缩进，更深的回复按最深层级展示，避免正文被挤成一条。 */
private fun flattenForumReplies(root: CommentItem): List<Pair<CommentItem, Int>> {
    val rows = mutableListOf<Pair<CommentItem, Int>>()
    fun walk(item: CommentItem, depth: Int) {
        for (reply in item.replies) {
            val next = depth.coerceAtMost(FORUM_MAX_INDENT_DEPTH)
            rows.add(reply to next)
            walk(reply, depth + 1)
        }
    }
    walk(root, 1)
    return rows
}

/** 轻量胶囊按钮：空态里的"发布新帖"入口。 */
@Composable
private fun ForumCapsuleButton(text: String, onClick: () -> Unit) {
    val scheme = CouiKitTheme.colorScheme
    Box(
        modifier = Modifier
            .couiPressable(
                shape = CouiCapsuleShape,
                backgroundColor = scheme.accent,
                spotlight = SpotlightPreset.OpaqueMedium1,
                minTouchTarget = CouiMinTouchTarget,
                onClick = onClick,
            )
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            kind = CouiTextKind.ItemValue,
            color = scheme.onAccent,
        )
    }
}

/**
 * 发帖 / 回复共用的输入弹窗：单行标题 + 多行输入 + 字数计数。
 *
 * 草稿按 visible 换代重置：visible 传 null 后库不会卸载组合，remember 必须跟着换代。
 */
@Composable
private fun CouiForumInputDialog(
    visible: Boolean,
    title: String,
    busy: Boolean,
    confirmText: String,
    busyConfirmText: String,
    placeholder: String,
    onDismiss: () -> Unit,
    onSend: (String) -> Unit,
    targetKey: String? = null,
) {
    val scheme = CouiKitTheme.colorScheme
    var draft by rememberSavedDialogDraft(isOpen = visible, targetKey = targetKey)
    Dialog(
        onDismissRequest = onDismiss,
        visible = visible,
        confirmAction = CouiDialogAction(
            text = if (busy) busyConfirmText else confirmText,
            onClick = { onSend(draft.trim()) },
            enabled = !busy && draft.isNotBlank(),
            style = CouiDialogActionStyle.Primary,
            colors = CouiDialogDefaults.primaryActionColors(scheme.accent),
        ),
        dismissAction = CouiDialogAction(text = "取消", onClick = onDismiss),
        dark = CouiKitTheme.isDark,
        material = couiDialogMaterial(),
        title = title,
        // 回复框是多行输入，贴底弹出更符合"从下往上写"的直觉；显式声明以免依赖库默认值
        placement = CouiDialogPlacement.Bottom,
    ) {
        BasicTextField(
            value = draft,
            onValueChange = { draft = it.take(COMMENT_REPLY_MAX_LENGTH) },
            textStyle = rememberCouiBodyTextStyle().copy(color = scheme.onSurface),
            cursorBrush = SolidColor(scheme.accent),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 96.dp)
                .clip(CouiShape(radius = 16.dp, cornerWeight = 3f))
                .background(scheme.container)
                .padding(12.dp),
            decorationBox = { innerTextField ->
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (draft.isEmpty()) {
                        CouiBodyText(
                            text = placeholder,
                            color = scheme.onSurfaceVariant,
                            textAlign = TextAlign.Start,
                        )
                    }
                    innerTextField()
                }
            },
        )
        Text(
            text = draft.length.toString() + "/" + COMMENT_REPLY_MAX_LENGTH,
            kind = CouiTextKind.GroupFooter,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Start,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
