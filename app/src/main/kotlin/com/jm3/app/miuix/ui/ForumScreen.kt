package com.jm3.app.miuix.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import com.jm3.app.miuix.Jm3Graph
import com.jm3.core.model.CommentItem
import com.jm3.core.model.ForumScope
import com.jm3.core.settings.PaginationMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 论坛单页条数；接口不返回分页总数，以返回条数推断是否还有下一页。 */
private const val FORUM_PAGE_SIZE = 20

private sealed interface ForumUiState {
    data object Loading : ForumUiState
    data object Loaded : ForumUiState
    data class Failed(val message: String) : ForumUiState
}

/** 独立论坛页：全部/漫画评论/闲聊三档，支持发帖、回复与两种分页模式。 */
@Composable
fun ForumScreen(
    onBack: () -> Unit,
    onOpenLogin: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val user by Jm3Graph.account.user.collectAsState()
    val pageMode = Jm3Graph.paginationMode
    var scopeIndex by rememberSaveable { mutableIntStateOf(0) }
    val forumScope = ForumScope.entries[scopeIndex.coerceIn(0, ForumScope.entries.lastIndex)]
    var items by remember { mutableStateOf<List<CommentItem>>(emptyList()) }
    var total by remember { mutableIntStateOf(0) }
    var page by remember { mutableIntStateOf(1) }
    var hasNext by remember { mutableStateOf(false) }
    var state by remember { mutableStateOf<ForumUiState>(ForumUiState.Loading) }
    var loadingMore by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    val refreshState = rememberPullToRefreshState(refreshThreshold = 0.18f)
    var replyTarget by remember { mutableStateOf<CommentItem?>(null) }
    var replyBusy by remember { mutableStateOf(false) }
    var composing by remember { mutableStateOf(false) }
    var composeBusy by remember { mutableStateOf(false) }
    var rulesExpanded by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun fetch(targetPage: Int, replace: Boolean, showLoading: Boolean) {
        if (showLoading) state = ForumUiState.Loading
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { Jm3Graph.gateway.forum(forumScope, targetPage) }
            }
            refreshing = false
            loadingMore = false
            result
                .onSuccess { resultPage ->
                    items = if (replace) {
                        resultPage.items
                    } else {
                        (items + resultPage.items).distinctBy { it.id }
                    }
                    total = resultPage.total
                    page = targetPage
                    hasNext = resultPage.items.size >= FORUM_PAGE_SIZE
                    state = ForumUiState.Loaded
                }
                .onFailure {
                    if (replace) {
                        state = ForumUiState.Failed(it.message ?: "论坛加载失败")
                    } else {
                        // 追加失败保留已有列表，只提示并停止继续翻页
                        hasNext = false
                        scope.launch {
                            snackbarHostState.showSnackbar("加载失败：" + (it.message ?: "请稍后重试"))
                        }
                    }
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
            val result = withContext(Dispatchers.IO) {
                runCatching { Jm3Graph.gateway.replyComment(albumId, text, target.id) }
            }
            replyBusy = false
            result
                .onSuccess {
                    replyTarget = null
                    snackbarHostState.showSnackbar("回复已发送")
                    fetch(page, replace = true, showLoading = false)
                }
                .onFailure {
                    snackbarHostState.showSnackbar("回复失败：" + (it.message ?: "请稍后重试"))
                }
        }
    }

    /** 发帖：原版全站发帖不带 aid，这里以空串提交，由服务端按账号归属落库。 */
    fun postTopic(text: String) {
        if (text.isBlank() || composeBusy) return
        composeBusy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { Jm3Graph.gateway.postComment("", text) }
            }
            composeBusy = false
            result
                .onSuccess {
                    composing = false
                    snackbarHostState.showSnackbar("发布成功")
                    fetch(targetPage = 1, replace = true, showLoading = false)
                }
                .onFailure {
                    snackbarHostState.showSnackbar("发布失败：" + (it.message ?: "请稍后重试"))
                }
        }
    }

    // 切换档位或分页模式时按新语义重载第一页，避免两种翻页状态混用
    LaunchedEffect(scopeIndex, pageMode) {
        fetch(targetPage = 1, replace = true, showLoading = true)
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = "社区论坛",
                    subtitle = if (state == ForumUiState.Loaded && total > 0) "共 $total 条" else "",
                    navigationIcon = { BackButton(onBack = onBack) },
                    actions = {
                        TextButton(
                            text = "发帖",
                            onClick = { if (user == null) onOpenLogin() else composing = true },
                            minWidth = 0.dp,
                            minHeight = 36.dp,
                        )
                    },
                )
                TabRow(
                    tabs = ForumScope.entries.map { it.label },
                    selectedTabIndex = scopeIndex,
                    onTabSelected = { scopeIndex = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when (val s = state) {
            ForumUiState.Loading -> LoadingScreen(modifier = Modifier.padding(innerPadding))
            is ForumUiState.Failed -> ErrorScreen(
                message = s.message,
                onRetry = { fetch(targetPage = 1, replace = true, showLoading = true) },
                modifier = Modifier.padding(innerPadding),
            )
            ForumUiState.Loaded -> if (items.isEmpty()) {
                CenteredEmptyState(
                    title = "暂无帖子",
                    hint = "换个档位看看，或发一条新帖",
                    modifier = Modifier.padding(innerPadding),
                )
            } else {
                PullToRefresh(
                    isRefreshing = refreshing,
                    onRefresh = {
                        refreshing = true
                        fetch(targetPage = 1, replace = true, showLoading = false)
                    },
                    pullToRefreshState = refreshState,
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentPadding = innerPadding,
                ) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        if (forumScope == ForumScope.CHAT) {
                            item(key = "rules") {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 3.dp),
                                ) {
                                    BasicComponent(
                                        title = "社区规则",
                                        summary = if (rulesExpanded) {
                                            "禁止发布违法违规、骚扰与剧透内容；发言限 200 字，违规内容会被删除。"
                                        } else {
                                            null
                                        },
                                        onClick = { rulesExpanded = !rulesExpanded },
                                        endActions = {
                                            Text(
                                                text = if (rulesExpanded) "收起" else "展开",
                                                fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                                                color = MiuixTheme.colorScheme.primary,
                                            )
                                        },
                                    )
                                }
                            }
                        }
                        items(items, key = { it.id }) { item ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 3.dp),
                            ) {
                                CommentRow(item = item, onReply = ::startReply)
                            }
                        }
                        item(key = "footer") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                when {
                                    pageMode == PaginationMode.CLICK -> Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        TextButton(
                                            text = "上一页",
                                            onClick = { fetch(page - 1, replace = true, showLoading = false) },
                                            enabled = page > 1,
                                            minWidth = 0.dp,
                                            minHeight = 36.dp,
                                        )
                                        Text(
                                            text = "第 $page 页",
                                            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        )
                                        TextButton(
                                            text = "下一页",
                                            onClick = { fetch(page + 1, replace = true, showLoading = false) },
                                            enabled = hasNext,
                                            minWidth = 0.dp,
                                            minHeight = 36.dp,
                                        )
                                    }
                                    loadingMore -> InfiniteProgressIndicator(modifier = Modifier.size(20.dp))
                                    hasNext -> TextButton(
                                        text = "加载更多",
                                        onClick = {
                                            loadingMore = true
                                            fetch(page + 1, replace = false, showLoading = false)
                                        },
                                    )
                                    else -> Text(
                                        text = "已经到底了",
                                        fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
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
                        fontSize = MiuixTheme.textStyles.footnote1.fontSize,
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
