package com.jm3.app.miuix.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jm3.app.miuix.Jm3Graph
import com.jm3.app.miuix.ui.glass.GlassPullToRefresh
import com.jm3.app.miuix.ui.glass.GlassScaffold
import com.jm3.app.miuix.ui.glass.GlassTopAppBar
import com.jm3.core.model.CommentItem
import com.jm3.core.appendedBy
import com.jm3.core.model.ForumScope
import com.jm3.core.paging.PagedFeed
import com.jm3.core.replacedBy
import com.jm3.core.session.AuthCall
import com.jm3.core.session.SessionOutcome
import com.jm3.core.settings.PaginationMode
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 论坛每页条数：接口固定一页 10 条，原版 ForumList 的 pageSize 也是 10。 */
private const val FORUM_PAGE_SIZE = 10

/** 闲聊区规则正文：折叠态只显示提要，展开态显示全文。 */
private const val FORUM_RULES_TEXT = "禁止发布违法违规、骚扰与剧透内容；发言限 200 字，违规内容会被删除。"

private sealed interface ForumUiState {
    data object Loading : ForumUiState
    data object Loaded : ForumUiState
    // sessionExpired 区分「重试可能好」和「会话没了」：后者只能去登录，给重试按钮是骗人的
    data class Failed(val plan: ScreenLoadPlan) : ForumUiState
}

/**
 * 独立论坛页：全部/漫画评论/闲聊三档，支持发帖、回复与两种分页模式。
 *
 * 分页总数由接口 total 推算（原版 pageLimit = ceil(total / 10)），不再以单页条数猜是否有下一页。
 */
@Composable
fun ForumScreen(
    onBack: () -> Unit,
    onOpenLogin: () -> Unit,
    snackbarHostState: SnackbarHostState,
    authCall: AuthCall,
    onOpenAlbum: (albumId: String) -> Unit = {},
) {
    val user by Jm3Graph.account.user.collectAsState()
    val pageMode = Jm3Graph.preferences.paginationMode
    var scopeIndex by rememberSaveable { mutableIntStateOf(0) }
    val selectionHaptic = rememberSelectionHaptic()
    val forumScope = ForumScope.entries[scopeIndex.coerceIn(0, ForumScope.entries.lastIndex)]
    var feed by remember { mutableStateOf(PagedFeed<CommentItem>()) }
    var state by remember { mutableStateOf<ForumUiState>(ForumUiState.Loading) }
    var refreshing by remember { mutableStateOf(false) }
    // 记录最近一次失败的页码：重试要回到失败的那一页，而不是一律回到第一页
    var failedPage by remember { mutableIntStateOf(1) }
    val refreshState = rememberPullToRefreshState(refreshThreshold = 0.18f)
    // 档位切换与翻页都会整块换掉列表内容，滚动位置必须一起回到顶部：
    // 否则新内容会沿用上一档/上一页的偏移，进来先看到列表中段
    val listState = rememberLazyListState()
    var replyTarget by remember { mutableStateOf<CommentItem?>(null) }
    var replyBusy by remember { mutableStateOf(false) }
    var composing by remember { mutableStateOf(false) }
    var composeBusy by remember { mutableStateOf(false) }
    var rulesExpanded by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    /** 总页数：接口 total 可用时按每页 10 条折算，否则退化为当前页。 */
    val totalPages = feed.pageCount ?: feed.page

    /** 失效与失败共用一条落点：有内容就保留列表、只标记页脚与提示，没内容才整屏。 */
    fun applyFailure(outcome: SessionOutcome<*>, inFlight: PagedFeed<CommentItem>) {
        val plan = planLoad(
            outcome = outcome,
            hasContent = inFlight.items.isNotEmpty(),
            failureMessage = "论坛加载失败",
            stalePrefix = "加载失败：",
        )
        // 保留内容时只把页脚标成可重试；整屏失败要撤掉在途标记，否则页脚永远转圈
        feed = if (plan.keepContent) inFlight.failLoadMore() else inFlight.copy(loadingMore = false)
        if (plan.phase == ScreenLoadPhase.Content) {
            state = ForumUiState.Loaded
            plan.notice?.let { notice -> scope.launch { snackbarHostState.showSnackbar(notice) } }
        } else {
            state = ForumUiState.Failed(plan)
        }
    }

    fun fetch(targetPage: Int, replace: Boolean, showLoading: Boolean) {
        // 翻页请求在途时不再发起新的翻页：连点会并发打同一页，返回顺序错乱后列表会重复
        val requested = if (replace) feed else feed.beginLoadMore()
        if (!replace && requested === feed) return
        if (showLoading) state = ForumUiState.Loading
        // 追加请求要立刻进入加载态，页脚才能显示进度；置位同时充当在途守卫
        feed = requested
        scope.launch {
            val outcome = authCall.call { Jm3Graph.gateway.forum(forumScope, targetPage) }
            refreshing = false
            when (outcome) {
                is SessionOutcome.Ok -> {
                    val resultPage = outcome.value
                    // 评论页没有页码字段：页码与总页数由请求页和 total 补上，点击分页条才有得显示
                    val pageCount = if (resultPage.total > 0) {
                        (resultPage.total + FORUM_PAGE_SIZE - 1) / FORUM_PAGE_SIZE
                    } else {
                        null
                    }
                    feed = if (replace) {
                        PagedFeed<CommentItem>().replacedBy(resultPage)
                    } else {
                        requested.appendedBy(resultPage)
                    }.copy(page = targetPage, pageCount = pageCount)
                    state = ForumUiState.Loaded
                }

                // 会话失效：同一张 401 不能再报成「论坛加载失败」，能做的只有去登录
                SessionOutcome.Expired -> applyFailure(outcome, requested)

                is SessionOutcome.Failed -> applyFailure(outcome, requested)
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
            val outcome = authCall.call { Jm3Graph.gateway.replyComment(albumId, text, target.id) }
            replyBusy = false
            when (outcome) {
                is SessionOutcome.Ok -> {
                    replyTarget = null
                    snackbarHostState.showSnackbar("回复已发送")
                    fetch(feed.page, replace = true, showLoading = false)
                }

                SessionOutcome.Expired -> snackbarHostState.showSnackbar(planLoad(outcome, hasContent = false, failureMessage = "回复失败").message.orEmpty())

                is SessionOutcome.Failed -> snackbarHostState.showSnackbar("回复失败：" + outcome.messageOr("请稍后重试"))
            }
        }
    }

    /** 发帖：原版全站发帖不带 aid，这里以空串提交，由服务端按账号归属落库。 */
    fun postTopic(text: String) {
        if (text.isBlank() || composeBusy) return
        composeBusy = true
        scope.launch {
            val outcome = authCall.call { Jm3Graph.gateway.postComment("", text) }
            composeBusy = false
            when (outcome) {
                is SessionOutcome.Ok -> {
                    composing = false
                    snackbarHostState.showSnackbar("发布成功")
                    // 新帖落在第一页首位，回到顶部才能看到刚发的内容
                    scope.launch { listState.scrollToItem(0) }
                    fetch(targetPage = 1, replace = true, showLoading = false)
                }

                SessionOutcome.Expired -> snackbarHostState.showSnackbar(planLoad(outcome, hasContent = false, failureMessage = "发布失败").message.orEmpty())

                is SessionOutcome.Failed -> snackbarHostState.showSnackbar("发布失败：" + outcome.messageOr("请稍后重试"))
            }
        }
    }

    /** 发帖入口的登录门禁：顶栏按钮与空态按钮共用同一判定。 */
    fun openCompose() {
        if (user == null) onOpenLogin() else composing = true
    }

    // 切换档位或分页模式时按新语义重载第一页，避免两种翻页状态混用
    LaunchedEffect(scopeIndex, pageMode) {
        // 首帧这里列表还没进组合（Loading 态不是列表），scrollToItem 会挂起等待布局而不返回，
        // 取数协程因此永远不执行；requestScrollToItem 只登记待处理滚动，不阻塞取数。
        listState.requestScrollToItem(0)
        fetch(targetPage = 1, replace = true, showLoading = true)
    }

    GlassScaffold(
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                GlassTopAppBar(
                    title = "社区论坛",
                    navigationIcon = { BackButton(onBack = onBack) },
                    actions = {
                        GlassActionChip(
                            text = "发帖",
                            onClick = ::openCompose,
                            emphasized = true,
                        )
                    },
                    // 透明底色：玻璃底色与模糊由 GlassScaffold 统一绘制
                    color = Color.Transparent,
                )
                // 档位筛选固定于列表之外：与顶栏同处玻璃层
                GlassChipRow(
                    tabs = ForumScope.entries.map { it.label },
                    selectedTabIndex = scopeIndex,
                    onTabSelected = {
                        selectionHaptic()
                        scopeIndex = it
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                )
            }
        },
        snackbarHost = { GlassSnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when (val s = state) {
            ForumUiState.Loading -> LoadingScreen(modifier = Modifier.padding(innerPadding))
            is ForumUiState.Failed -> if (s.plan.phase == ScreenLoadPhase.Expired) {
                LoginExpiredScreen(onOpenLogin = onOpenLogin, modifier = Modifier.padding(innerPadding))
            } else {
                ErrorScreen(
                    message = s.plan.message.orEmpty(),
                    onRetry = { fetch(targetPage = failedPage, replace = true, showLoading = true) },
                    modifier = Modifier.padding(innerPadding),
                )
            }
            // 加载成功就渲染列表：即使没有内容也保留列表本体，
            // 闲聊档的规则卡与下拉刷新在空态下才不会跟着一起消失
            ForumUiState.Loaded -> {
                // 列表内容穿到玻璃顶栏（顶栏 + 档位条）下方，顶栏才能采样到真实背景
                GlassPullToRefresh(
                    isRefreshing = refreshing,
                    onRefresh = {
                        refreshing = true
                        fetch(targetPage = 1, replace = true, showLoading = false)
                    },
                    pullToRefreshState = refreshState,
                    modifier = Modifier.fillMaxSize(),
                    // 刷新指示器下移到玻璃顶栏之下，避免被顶栏遮住
                    contentPadding = innerPadding,
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding = innerPadding,
                    ) {
                        if (forumScope == ForumScope.CHAT) {
                            item(key = "rules") {
                                ForumRulesCard(
                                    expanded = rulesExpanded,
                                    onToggle = { rulesExpanded = !rulesExpanded },
                                )
                            }
                        }
                        val topicTotal = feed.total ?: 0
                        if (feed.items.isEmpty()) {
                            item(key = "empty") {
                                ForumEmptyState(forumScope = forumScope, onCompose = ::openCompose)
                            }
                        } else {
                            if (topicTotal > 0) {
                                item(key = "count") {
                                    Text(
                                        text = "共 $topicTotal 条话题",
                                        style = MiuixTheme.textStyles.footnote2,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 20.dp, top = 6.dp, bottom = 2.dp),
                                    )
                                }
                            }
                            items(feed.items, key = { it.id }) { item ->
                                ForumTopicCard(
                                    item = item,
                                    onReply = ::startReply,
                                    onOpenAlbum = onOpenAlbum,
                                )
                            }
                            item(key = "footer") {
                                if (pageMode == PaginationMode.CLICK) {
                                    // 只有一页时翻页条没有信息量，不占位
                                    if (totalPages > 1) {
                                        PagerBar(
                                            page = feed.page,
                                            totalPages = totalPages,
                                            // 目标页就是当前页时不重发；在途判定由 feed 自己持有
                                            onPageChange = { target ->
                                                if (target != feed.page) {
                                                    scope.launch { listState.scrollToItem(0) }
                                                    fetch(target, replace = true, showLoading = false)
                                                }
                                            },
                                        )
                                    }
                                } else {
                                    LoadMoreFooter(
                                        feed = feed,
                                        onLoadMore = { fetch(feed.page + 1, replace = false, showLoading = false) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 发帖面板与回复面板同级挂载，避免底部抽屉叠加导致高度抖动
        ForumComposeSheet(
            show = composing,
            busy = composeBusy,
            onDismiss = { composing = false },
            onSend = ::postTopic,
        )
        CommentReplySheet(
            show = replyTarget != null,
            targetAuthor = replyTarget?.author,
            busy = replyBusy,
            onDismiss = { replyTarget = null },
            onSend = ::sendReply,
        )
    }
}

/**
 * 论坛「相关作品」行的几何：行内边距、底色圆角、缩略图与文案的间距。
 *
 * 只服务这一行，与 [WorkCardTokens] 的文字内距不是一回事；收成常量是为了让三处对齐关系
 * （行内边距、圆角、图文间距）改一处即全动，不会漏掉其中一处。
 */
private object RelatedWorkTokens {
    /** 行底色块的圆角。 */
    val RelatedWorkCorner = 10.dp

    /** 缩略图与右侧文案的间距，也是两行文案左缘对齐的基准。 */
    val RelatedWorkThumbGap = 10.dp

    /** 行外层内边距：左右让出卡片内边距，底部留一点呼吸。 */
    val RelatedWorkOuterInset = PaddingValues(start = 12.dp, end = 12.dp, bottom = 10.dp)

    /** 行内边距：底色块内的内容让出圆角，避免文字贴边。 */
    val RelatedWorkInnerPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
}

/**
 * 论坛单条：楼层内容沿用评论区排版（头像/等级/徽章/分段正文），底部补关联作品入口。
 *
 * 关联作品做成一行紧凑卡片：左侧 3:4 缩略封面（[WorkThumbnail]，与历史、追更行同尺寸），
 * 右侧两行文案——上方说明用途、下方是作品名（形如 JM123456）——行尾箭头给出可点的视觉暗示。
 * 缩略图缺图时 [WorkThumbnail] 只占同尺寸空位，行宽行高都不会跟着抖动。
 * 整行可点，跳作品详情，与原文字行的点击语义一致。
 */
@Composable
private fun ForumTopicCard(
    item: CommentItem,
    onReply: (CommentItem) -> Unit,
    onOpenAlbum: (albumId: String) -> Unit,
) {
    val albumId = item.albumId
        ?: item.albumName?.filter { it.isDigit() }?.takeIf { it.isNotBlank() }
    val albumName = item.albumName?.takeIf { albumId != null }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            CommentRow(item = item, onReply = onReply, onOpenAlbum = onOpenAlbum)
            if (albumName != null && albumId != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(RelatedWorkTokens.RelatedWorkOuterInset)
                        .squircleBackground(MiuixTheme.colorScheme.surfaceContainerHigh, RelatedWorkTokens.RelatedWorkCorner)
                        .clickable { onOpenAlbum(albumId) }
                        // 触感交给点击本身，避免整行点击时重复震动
                        .pressScale(hapticFeedback = false)
                        .padding(RelatedWorkTokens.RelatedWorkInnerPadding),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WorkThumbnail(
                        url = item.albumCoverUrl,
                        albumId = albumId,
                        contentDescription = albumName,
                    )
                    // 间距与缩略图间距同值，两行内容左缘才对得齐
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = RelatedWorkTokens.RelatedWorkThumbGap),
                    ) {
                        Text(
                            text = "相关作品",
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        Text(
                            text = albumName,
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Icon(
                        imageVector = MiuixIcons.ChevronForward,
                        // 整行已是可点区域，箭头只作视觉暗示，不单独播报，避免读屏重复描述同一动作
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }
    }
}

/** 闲聊档的社区规则卡：折叠态给一行提要，展开态给全文，避免只剩标题时看不出内容。 */
@Composable
private fun ForumRulesCard(expanded: Boolean, onToggle: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioLowBouncy)),
    ) {
        BasicComponent(
            title = "社区规则",
            summary = if (expanded) FORUM_RULES_TEXT else "闲聊区发言前请先了解规则",
            onClick = onToggle,
            endActions = {
                Text(
                    text = if (expanded) "收起" else "展开",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.primary,
                )
            },
        )
    }
}

/** 列表空态：按档位给出不同的引导，并保留一个直接可用的发帖入口。 */
@Composable
private fun ForumEmptyState(forumScope: ForumScope, onCompose: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EmptyState(
            title = if (forumScope == ForumScope.CHAT) "闲聊区还没有帖子" else "暂无话题",
            hint = if (forumScope == ForumScope.CHAT) {
                "来开第一帖，聊聊作品之外的事"
            } else {
                "换个档位看看，或发一条新帖"
            },
        )
        Button(
            onClick = onCompose,
            colors = ButtonDefaults.buttonColorsPrimary(),
        ) {
            Text(text = "发布新帖")
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

/** 发帖面板：200 字上限、实时计数与发布态。 */
@Composable
private fun ForumComposeSheet(
    show: Boolean,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSend: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(show) {
        if (show) text = ""
    }
    OverlayBottomSheet(
        show = show,
        title = "发布新帖",
        onDismissRequest = onDismiss,
    ) {
        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                TextField(
                    value = text,
                    onValueChange = { text = it.take(COMMENT_REPLY_MAX_LENGTH) },
                    label = "说点什么（最多 $COMMENT_REPLY_MAX_LENGTH 字）",
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "${text.length}/$COMMENT_REPLY_MAX_LENGTH",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Button(
                        onClick = { onSend(text.trim()) },
                        enabled = !busy && text.isNotBlank(),
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) {
                        Text(text = if (busy) "发布中…" else "发布")
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}
