package com.jm3.app.miuix.ui.coui.page

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itos.coui.backdrop.couiBackdrop
import com.itos.coui.backdrop.rememberCouiBackdrop
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.Dialog
import com.itos.coui.effect.SpotlightPreset
import com.itos.coui.layout.Group
import com.itos.coui.layout.Item
import com.itos.coui.picker.WheelPicker
import com.itos.coui.scroll.couiVerticalScroll
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.text.rememberCouiTextStyle
import com.itos.coui.theme.CouiTheme as CouiKitTheme
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.ui.COMMENT_REPLY_MAX_LENGTH
import com.jm3.app.miuix.ui.GlassSnackbarHost
import com.jm3.app.miuix.ui.readableWidth
import com.jm3.app.miuix.ui.ScreenLoadPhase
import com.jm3.app.miuix.ui.ScreenLoadPlan
import com.jm3.app.miuix.ui.novelActionNotice
import com.jm3.app.miuix.ui.planLoad
import com.jm3.app.miuix.ui.coui.component.CouiEmptyGroup
import com.jm3.app.miuix.ui.coui.component.CouiErrorGroup
import com.jm3.app.miuix.ui.coui.component.CouiFeedLoginGroup
import com.jm3.app.miuix.ui.coui.component.CouiLoadMoreFooter
import com.jm3.app.miuix.ui.coui.component.CouiLoadingGroup
import com.jm3.app.miuix.ui.coui.component.CouiMinTouchTarget
import com.jm3.app.miuix.ui.coui.component.CouiTopBarPlainBackButton
import com.jm3.app.miuix.ui.coui.component.couiPressable
import com.jm3.app.miuix.ui.coui.icon.CouiIcons
import com.jm3.app.miuix.ui.coui.theme.CouiStrings
import com.jm3.app.miuix.ui.coui.theme.CouiTheme
import com.jm3.core.appendedBy
import com.jm3.core.model.CommentItem
import com.jm3.core.model.NovelChapter
import com.jm3.core.model.NovelChapterMeta
import com.jm3.core.model.NovelDetail
import com.jm3.core.paging.PagedFeed
import com.jm3.core.replacedBy
import com.jm3.core.session.SessionOutcome
import com.jm3.core.settings.SettingsRepository
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.SnackbarHostState

/**
 * 小说阅读页的 COUI 版：正文独占整屏，顶栏与底栏是浮在正文之上的 HUD。
 *
 * 数据流、章节切换规则与失败处置与原版 NovelReaderScreen 逐条一致，这里只替换外观：
 * 顶栏给返回、章节名、书名与简繁切换，底栏给字号、上一章、下一章、评论与收藏。
 */
@Composable
fun CouiNovelReaderScreen(
    deps: ScreenDeps,
    novelId: String,
    initialChapterId: String,
    onBack: () -> Unit,
    onOpenLogin: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val authCall = deps.authCall
    val user by deps.account.user.collectAsState()
    var detail by remember { mutableStateOf<NovelDetail?>(null) }
    var chapter by remember { mutableStateOf<NovelChapter?>(null) }
    var currentId by rememberSaveable { mutableStateOf(initialChapterId) }
    var fontSize by remember { mutableIntStateOf(SettingsRepository.DEFAULT_NOVEL_FONT_SIZE) }
    var lang by rememberSaveable { mutableStateOf(SettingsRepository.DEFAULT_NOVEL_LANG) }
    var loading by remember { mutableStateOf(true) }
    var failure by remember { mutableStateOf<ScreenLoadPlan?>(null) }
    var favorited by remember { mutableStateOf(false) }
    var commentsOpen by remember { mutableStateOf(false) }
    var fontSheetOpen by remember { mutableStateOf(false) }
    var loadToken by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    // 正文先挂进 backdrop，HUD 的两条栏再采样它画透镜；栏是正文的兄弟节点，不能塞进正文子树
    val hudBackdrop = rememberCouiBackdrop(diagnosticName = "novelHud")
    val bars = WindowInsets.systemBars.asPaddingValues()
    val scheme = CouiKitTheme.colorScheme

    fun loadChapter(chapterId: String, language: String, showLoading: Boolean) {
        loadToken++
        val token = loadToken
        if (showLoading) loading = true
        failure = null
        scope.launch {
            val outcome = authCall.call { deps.gateway.novelChapter(chapterId, language) }
            if (token != loadToken) return@launch
            loading = false
            if (outcome is SessionOutcome.Ok) {
                chapter = outcome.value
                currentId = chapterId
                favorited = outcome.value.favorite
                scope.launch { deps.settings.setNovelLastChapter(novelId, chapterId) }
            } else {
                // 翻到下一章失败时旧正文还在：保留正文只提示，不把正在读的章节顶掉
                val plan = planLoad(
                    outcome = outcome,
                    hasContent = chapter != null && !showLoading,
                    failureMessage = "章节加载失败",
                )
                if (plan.keepContent) {
                    snackbarHostState.showSnackbar(plan.notice.orEmpty())
                } else {
                    failure = plan
                }
            }
        }
    }

    LaunchedEffect(novelId) {
        fontSize = deps.settings.novelFontSize()
        lang = deps.settings.novelLang()
        // 目录只是辅助数据：普通失败保持静默（正文该能读就读），只有会话失效才提示去登录
        when (val detailOutcome = authCall.call { deps.gateway.novelDetail(novelId) }) {
            is SessionOutcome.Ok -> {
                detail = detailOutcome.value
                favorited = detailOutcome.value.favorite
            }

            SessionOutcome.Expired -> snackbarHostState.showSnackbar(SessionOutcome.Expired.message)

            is SessionOutcome.Failed -> Unit
        }
        loadChapter(currentId, lang, showLoading = true)
    }

    val chapters = detail?.chapters.orEmpty()
    val currentIndex = chapters.indexOfFirst { it.ncid == currentId }
    val prevChapter = chapters.getOrNull(currentIndex - 1)
    val nextChapter = chapters.getOrNull(currentIndex + 1)

    fun goTo(target: NovelChapterMeta?) {
        if (target == null) {
            scope.launch { snackbarHostState.showSnackbar("已到尽头") }
            return
        }
        if (target.needBuy) {
            scope.launch { snackbarHostState.showSnackbar("该章节需付费购买，当前版本暂不支持购买") }
            return
        }
        loadChapter(target.ncid, lang, showLoading = true)
    }

    fun toggleFavorite() {
        if (user == null) {
            onOpenLogin()
            return
        }
        scope.launch {
            val outcome = authCall.call { deps.gateway.toggleNovelFavorite(novelId) }
            val notice = novelActionNotice(outcome, failurePrefix = "收藏失败：")
            if (notice == null) {
                favorited = !favorited
                snackbarHostState.showSnackbar(if (favorited) "已加入收藏" else "已取消收藏")
            } else {
                snackbarHostState.showSnackbar(notice.message)
                if (notice.openLogin) onOpenLogin()
            }
        }
    }

    fun changeLang(next: String) {
        lang = next
        scope.launch { deps.settings.setNovelLang(next) }
        loadChapter(currentId, next, showLoading = false)
    }

    fun applyFontSize(next: Int) {
        val clamped = next.coerceIn(
            SettingsRepository.NOVEL_FONT_SIZE_MIN,
            SettingsRepository.NOVEL_FONT_SIZE_MAX,
        )
        if (clamped == fontSize) return
        fontSize = clamped
        scope.launch { deps.settings.setNovelFontSize(clamped) }
    }

    CouiTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize().couiBackdrop(hudBackdrop)) {
                // 正文上下各让出 HUD 高度，正文才能从浮栏下方穿过去；让位放在滚动容器内部，
                // 只有第一屏和最后一屏能看到额外留白
                val topInset = bars.calculateTopPadding() + ReaderHudTopHeight + 12.dp
                val bottomInset = bars.calculateBottomPadding() + ReaderHudBottomHeight + 12.dp
                when {
                    loading -> Box(
                        modifier = Modifier.fillMaxSize().padding(top = topInset, bottom = bottomInset),
                        contentAlignment = Alignment.Center,
                    ) {
                        CouiLoadingGroup(text = "正在加载章节…", modifier = Modifier.fillMaxWidth())
                    }

                    failure?.phase == ScreenLoadPhase.Expired -> Box(
                        modifier = Modifier.fillMaxSize().padding(top = topInset, bottom = bottomInset),
                        contentAlignment = Alignment.Center,
                    ) {
                        CouiFeedLoginGroup(
                            message = failure?.message.orEmpty(),
                            onOpenLogin = onOpenLogin,
                        )
                    }

                    failure != null -> Box(
                        modifier = Modifier.fillMaxSize().padding(top = topInset, bottom = bottomInset),
                        contentAlignment = Alignment.Center,
                    ) {
                        CouiErrorGroup(
                            message = failure?.message.orEmpty(),
                            onRetry = { loadChapter(currentId, lang, showLoading = true) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    chapter == null -> Box(
                        modifier = Modifier.fillMaxSize().padding(top = topInset, bottom = bottomInset),
                        contentAlignment = Alignment.Center,
                    ) {
                        CouiEmptyGroup(
                            text = "章节不存在，可能已下架",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    else -> chapter?.let { body ->
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .couiVerticalScroll(rememberScrollState())
                                .readableWidth()
                                .padding(
                                    start = 20.dp,
                                    end = 20.dp,
                                    top = topInset,
                                    bottom = bottomInset,
                                ),
                        ) {
                            Text(
                                text = body.title,
                                kind = CouiTextKind.SectionTitle,
                                textAlign = TextAlign.Start,
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            // 正文要跟字号设置走，文本件按 kind 固定字号，这里用基础文本配主题行高
                            BasicText(
                                text = body.contentText,
                                style = rememberCouiTextStyle(CouiTextKind.Body).copy(
                                    color = scheme.onSurface,
                                    fontSize = fontSize.sp,
                                    lineHeight = (fontSize * 1.75).sp,
                                ),
                            )
                        }
                    }
                }
            }

            if (chapter != null || !loading) {
                ReaderHudBar(
                    backdrop = hudBackdrop,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .fillMaxWidth()
                        .readableWidth(),
                    height = ReaderHudTopHeight,
                    diagnosticName = "novelHud/top",
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // HUD 栏自带浮动材质、不在 COUI 顶栏作用域内，只能用自绘圆键
                        CouiTopBarPlainBackButton(onClick = onBack)
                        Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                            Text(
                                text = chapter?.title ?: "阅读",
                                kind = CouiTextKind.Toolbar,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val novelName = detail?.name.orEmpty()
                            if (novelName.isNotBlank()) {
                                Text(
                                    text = novelName,
                                    kind = CouiTextKind.Description,
                                    color = scheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Text(
                            text = if (lang == "tw") "繁" else "简",
                            kind = CouiTextKind.PreferenceValue,
                            color = scheme.accent,
                            modifier = Modifier
                                // 所在 HUD 面板本身已有凝光，这里不再叠第二层
                                .couiPressable(spotlight = null, minTouchTarget = CouiMinTouchTarget) { changeLang(if (lang == "tw") "cn" else "tw") }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            maxLines = 1,
                        )
                    }
                }

                ReaderHudBar(
                    backdrop = hudBackdrop,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .fillMaxWidth()
                        .readableWidth(),
                    height = ReaderHudBottomHeight,
                    diagnosticName = "novelHud/bottom",
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        ReaderHudAction(
                            icon = CouiIcons.Settings,
                            label = "字号",
                            onClick = { fontSheetOpen = true },
                        )
                        ReaderHudAction(
                            icon = CouiIcons.Back,
                            label = "上一章",
                            onClick = { goTo(prevChapter) },
                        )
                        ReaderHudAction(
                            icon = CouiIcons.Forward,
                            label = "下一章",
                            onClick = { goTo(nextChapter) },
                        )
                        ReaderHudAction(
                            icon = CouiIcons.Comment,
                            label = "评论",
                            onClick = { commentsOpen = true },
                        )
                        ReaderHudAction(
                            icon = CouiIcons.Folder,
                            label = "收藏",
                            active = favorited,
                            onClick = ::toggleFavorite,
                        )
                    }
                }
            }

            // 提示条叠在两条栏之上：它不属于任何一条栏，否则会被栏的裁剪带走
            GlassSnackbarHost(
                state = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = ReaderHudBottomHeight + 8.dp),
            )
        }

        Dialog(
            onDismissRequest = { fontSheetOpen = false },
            visible = fontSheetOpen,
            title = "字号",
            confirmAction = CouiDialogAction(text = "完成", onClick = { fontSheetOpen = false }),
        ) {
            Group {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = "当前字号 " + fontSize + "sp",
                        kind = CouiTextKind.PreferenceTitle,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // 字号是离散档位，滚轮比滑杆更容易一档一档停准；不环回，避免从最大档直接跳回最小档
                    WheelPicker(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        value = fontSize,
                        onValueChange = { applyFontSize(it) },
                        valueRange = SettingsRepository.NOVEL_FONT_SIZE_MIN..
                            SettingsRepository.NOVEL_FONT_SIZE_MAX,
                        label = "字号",
                        wrap = false,
                        verticalFading = true,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        NovelFontStep(
                            label = "A-",
                            enabled = fontSize > SettingsRepository.NOVEL_FONT_SIZE_MIN,
                            onClick = { applyFontSize(fontSize - 1) },
                            modifier = Modifier.weight(1f),
                        )
                        NovelFontStep(
                            label = "A+",
                            enabled = fontSize < SettingsRepository.NOVEL_FONT_SIZE_MAX,
                            onClick = { applyFontSize(fontSize + 1) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        Dialog(
            onDismissRequest = { commentsOpen = false },
            visible = commentsOpen,
            title = "评论",
            confirmAction = CouiDialogAction(text = "关闭", onClick = { commentsOpen = false }),
        ) {
            Group {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp)
                        .couiVerticalScroll(rememberScrollState()),
                ) {
                    NovelCommentsPanel(
                        deps = deps,
                        novelId = novelId,
                        onOpenLogin = onOpenLogin,
                        snackbarHostState = snackbarHostState,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** 字号增减的胶囊按钮：禁用时降到次要色，形状与底栏动作保持一致。 */
@Composable
private fun NovelFontStep(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = CouiKitTheme.colorScheme
    Box(
        modifier = modifier
            .clip(CouiCapsuleShape)
            .background(scheme.container)
            // 先铺不透明底再挂可点件：光画在内容之前，排在底色之上才看得见
            .couiPressable(
                shape = CouiCapsuleShape,
                enabled = enabled,
                spotlight = SpotlightPreset.ChipTab,
                minTouchTarget = CouiMinTouchTarget,
                onClick = onClick,
            )
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            kind = CouiTextKind.PreferenceTitle,
            color = if (enabled) scheme.accent else scheme.onSurfaceVariant,
        )
    }
}

/**
 * 小说评论区：列表、续页、发评论与回复。
 *
 * 与阅读页共用同一套评论件，续页改成显式「加载更多」行：服务端给出空页却说还有下一页时，
 * 自动续页会一直转圈，交给用户点一次更稳。
 */
@Composable
private fun NovelCommentsPanel(
    deps: ScreenDeps,
    novelId: String,
    onOpenLogin: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val authCall = deps.authCall
    val user by deps.account.user.collectAsState()
    var feed by remember { mutableStateOf(PagedFeed<CommentItem>()) }
    var loading by remember { mutableStateOf(true) }
    var failure by remember { mutableStateOf<ScreenLoadPlan?>(null) }
    var draft by rememberSaveable { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var replyTarget by remember { mutableStateOf<CommentItem?>(null) }
    var replyDraft by rememberSaveable { mutableStateOf("") }
    var replyBusy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun load(targetPage: Int, replace: Boolean, showLoading: Boolean) {
        // 还能不能拉、在不在拉全由 feed 判定：重复点「加载更多」不会再并发打同一页
        val requested = if (replace) feed else feed.beginLoadMore()
        if (!replace && requested === feed) return
        if (showLoading) loading = true
        failure = null
        feed = requested
        scope.launch {
            val outcome = authCall.call { deps.gateway.novelComments(novelId, targetPage) }
            loading = false
            if (outcome is SessionOutcome.Ok) {
                feed = if (replace) {
                    PagedFeed<CommentItem>().replacedBy(outcome.value)
                } else {
                    requested.appendedBy(outcome.value)
                }
            } else {
                // 翻页失败保留已加载评论并让页脚可重试；首屏失败才整块换掉
                val plan = planLoad(
                    outcome = outcome,
                    hasContent = !replace && requested.items.isNotEmpty(),
                    failureMessage = "评论加载失败",
                )
                if (plan.keepContent) {
                    feed = requested.failLoadMore()
                    snackbarHostState.showSnackbar(plan.notice.orEmpty())
                } else {
                    feed = requested.copy(loadingMore = false)
                    failure = plan
                }
            }
        }
    }

    LaunchedEffect(novelId) { load(1, replace = true, showLoading = true) }

    fun send() {
        val text = draft.trim()
        if (text.isEmpty() || sending) return
        if (user == null) {
            onOpenLogin()
            return
        }
        sending = true
        scope.launch {
            val outcome = authCall.call { deps.gateway.postNovelComment(novelId, text, "") }
            sending = false
            val notice = novelActionNotice(outcome, failurePrefix = "发布失败：")
            if (notice == null) {
                draft = ""
                snackbarHostState.showSnackbar("评论已发布")
                load(1, replace = true, showLoading = false)
            } else {
                snackbarHostState.showSnackbar(notice.message)
                if (notice.openLogin) onOpenLogin()
            }
        }
    }

    fun submitReply() {
        val target = replyTarget ?: return
        val text = replyDraft.trim()
        if (text.isEmpty() || replyBusy) return
        replyBusy = true
        scope.launch {
            val outcome = authCall.call { deps.gateway.replyNovelComment(novelId, text, target.id, "") }
            replyBusy = false
            val notice = novelActionNotice(outcome, failurePrefix = "回复失败：")
            if (notice == null) {
                replyTarget = null
                replyDraft = ""
                snackbarHostState.showSnackbar("回复已发送")
                load(1, replace = true, showLoading = false)
            } else {
                snackbarHostState.showSnackbar(notice.message)
                if (notice.openLogin) onOpenLogin()
            }
        }
    }

    Column(modifier = modifier) {
        when {
            loading -> CouiLoadingGroup(text = "正在加载评论…", modifier = Modifier.fillMaxWidth())

            failure?.phase == ScreenLoadPhase.Expired -> CouiFeedLoginGroup(
                message = failure?.message.orEmpty(),
                onOpenLogin = onOpenLogin,
            )

            failure != null -> CouiErrorGroup(
                message = failure?.message.orEmpty(),
                onRetry = { load(1, replace = true, showLoading = true) },
                modifier = Modifier.fillMaxWidth(),
            )

            feed.items.isEmpty() -> CouiEmptyGroup(
                text = CouiStrings.EmptyComments,
                modifier = Modifier.fillMaxWidth(),
            )

            else -> Column(modifier = Modifier.fillMaxWidth()) {
                feed.items.forEach { item ->
                    ReaderCommentRow(
                        item = item,
                        onReply = {
                            if (user == null) {
                                onOpenLogin()
                            } else {
                                replyTarget = item
                                replyDraft = ""
                            }
                        },
                    )
                }
                when {
                    feed.loadingMore -> CouiLoadMoreFooter(
                        loading = true,
                        error = null,
                        endReached = false,
                        onRetry = {},
                    )

                    feed.loadMoreFailed -> CouiLoadMoreFooter(
                        loading = false,
                        error = "下一页加载失败",
                        endReached = false,
                        onRetry = { load(feed.page + 1, replace = false, showLoading = false) },
                    )

                    feed.hasMore -> Group {
                        Item(
                            title = {
                                Text(
                                    text = "加载更多",
                                    kind = CouiTextKind.Body,
                                    textAlign = TextAlign.Start,
                                )
                            },
                            onClick = { load(feed.page + 1, replace = false, showLoading = false) },
                        )
                    }

                    else -> CouiLoadMoreFooter(
                        loading = false,
                        error = null,
                        endReached = true,
                        onRetry = {},
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            ReaderCommentComposer(
                loggedIn = user != null,
                text = draft,
                busy = sending,
                onTextChange = { draft = it.take(COMMENT_REPLY_MAX_LENGTH) },
                onLogin = onOpenLogin,
                onSend = ::send,
            )
        }
    }

    Dialog(
        onDismissRequest = { if (!replyBusy) replyTarget = null },
        visible = replyTarget != null,
        title = "回复 " + replyTarget?.author.orEmpty(),
        confirmAction = CouiDialogAction(
            text = if (replyBusy) "发送中…" else "发送",
            enabled = !replyBusy,
            onClick = { submitReply() },
        ),
        dismissAction = CouiDialogAction(
            text = "取消",
            onClick = { if (!replyBusy) replyTarget = null },
        ),
    ) {
        Group {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                ReaderCommentComposer(
                    loggedIn = true,
                    text = replyDraft,
                    busy = replyBusy,
                    onTextChange = { replyDraft = it.take(COMMENT_REPLY_MAX_LENGTH) },
                    onLogin = {},
                    onSend = { submitReply() },
                )
            }
        }
    }
}
