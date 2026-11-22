package com.jm3.app.miuix.ui.coui.page

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.reader.ReaderSession
import com.jm3.app.miuix.ui.CommentItemNullableSaver
import com.jm3.app.miuix.ui.GlassSnackbarHost
import com.jm3.app.miuix.ui.ReaderVolumeKeys
import com.jm3.app.miuix.ui.ScreenLoadPhase
import com.jm3.app.miuix.ui.ScreenLoadPlan
import com.jm3.app.miuix.ui.coui.component.CouiEmptyGroup
import com.jm3.app.miuix.ui.coui.component.CouiErrorGroup
import com.jm3.app.miuix.ui.coui.component.CouiFeedLoginGroup
import com.jm3.app.miuix.ui.coui.component.CouiLoadMoreFooter
import com.jm3.app.miuix.ui.coui.component.CouiLoadingGroup
import com.jm3.app.miuix.ui.coui.component.CouiMinTouchTarget
import com.jm3.app.miuix.ui.coui.component.CouiPageTopBarAction
import com.jm3.app.miuix.ui.coui.component.CouiSectionTitleText
import com.jm3.app.miuix.ui.coui.component.LocalPageBackdrop
import com.jm3.app.miuix.ui.coui.component.couiDialogListMaxHeight
import com.jm3.app.miuix.ui.coui.component.couiDialogSurfaceColor
import com.jm3.app.miuix.ui.coui.component.couiPressable
import com.jm3.app.miuix.ui.coui.component.panelBlur
import com.jm3.app.miuix.ui.coui.component.rememberCouiBodyTextStyle
import com.jm3.app.miuix.ui.coui.component.rememberMaterialBackdropProvider
import com.jm3.app.miuix.ui.coui.component.rememberPageBackdrop
import com.jm3.app.miuix.ui.coui.component.rememberPopupMaterialBlur
import com.jm3.app.miuix.ui.coui.component.rememberRetainedTarget
import com.jm3.app.miuix.ui.coui.icon.CouiIcons
import com.jm3.app.miuix.ui.coui.theme.CouiStrings
import com.jm3.app.miuix.ui.coui.theme.CouiTheme
import com.jm3.app.miuix.ui.novelActionNotice
import com.jm3.app.miuix.ui.planLoad
import com.jm3.app.miuix.ui.readableWidth
import com.jm3.app.miuix.ui.rememberSavedDialogDraft
import com.jm3.app.miuix.ui.route.AppRoutes
import com.jm3.core.appendedBy
import com.jm3.core.model.CommentItem
import com.jm3.core.model.NovelChapter
import com.jm3.core.model.NovelChapterMeta
import com.jm3.core.model.NovelDetail
import com.jm3.core.paging.PagedFeed
import com.jm3.core.replacedBy
import com.jm3.core.session.SessionOutcome
import com.jm3.core.settings.ReaderPreferences
import com.jm3.core.settings.SettingsRepository
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.NumberPicker
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.blur.MaterialBlurDefaults
import io.github.suqi8.coui.kmp.blur.layerBackdrop
import io.github.suqi8.coui.kmp.effect.LocalMaterialBackdrop
import io.github.suqi8.coui.kmp.effect.LocalPopupMaterialBlur
import io.github.suqi8.coui.kmp.layout.BottomSheetDefaults
import io.github.suqi8.coui.kmp.layout.DialogButtonBar
import io.github.suqi8.coui.kmp.layout.DialogButtonBarAction
import io.github.suqi8.coui.kmp.layout.DialogDefaults
import io.github.suqi8.coui.kmp.overlay.OverlayBottomSheet
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.theme.COUIRoundCorner
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.overScrollVertical
import io.github.suqi8.coui.kmp.utils.rememberCOUIFlingBehavior
import io.github.suqi8.coui.kmp.utils.scrollEndHaptic
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.SnackbarHostState

private val NovelHudCapsuleShape = SquircleShape(
    cornerRadius = COUIRoundCorner.Full,
    weight = COUIRoundCorner.CapsuleWeight,
)

/**
 * 小说阅读页的 COUI 版：正文独占整屏，顶栏与底栏是浮在正文之上的 HUD。
 *
 * 与漫画阅读器共享互斥弹窗模型、窗口亮度/常亮租约、音量键翻滚及实色底 Backdrop，
 * 点击正文可切换上下 HUD 显隐；评论与回复弹窗完全平级互斥，切换章节自动回到章首。
 */
@Composable
internal fun CouiNovelReaderScreen(
    deps: ScreenDeps,
    novelId: String,
    initialChapterId: String,
    onBack: () -> Unit,
    nav: AppRoutes,
    snackbarHostState: SnackbarHostState,
) {
    CouiTheme {
        CouiNovelReaderContent(
            deps = deps,
            novelId = novelId,
            initialChapterId = initialChapterId,
            onBack = onBack,
            onOpenLogin = nav::login,
            snackbarHostState = snackbarHostState,
        )
    }
}

@Composable
private fun CouiNovelReaderContent(
    deps: ScreenDeps,
    novelId: String,
    initialChapterId: String,
    onBack: () -> Unit,
    onOpenLogin: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val authCall = deps.authCall
    val user by deps.account.user.collectAsState()
    var detail by remember(novelId) { mutableStateOf<NovelDetail?>(null) }
    var chapter by remember(novelId) { mutableStateOf<NovelChapter?>(null) }
    var currentId by rememberSaveable(novelId) { mutableStateOf(initialChapterId) }
    var fontSize by remember { mutableIntStateOf(SettingsRepository.DEFAULT_NOVEL_FONT_SIZE) }
    var lang by rememberSaveable(novelId) { mutableStateOf(SettingsRepository.DEFAULT_NOVEL_LANG) }
    var volumeKeyPaging by remember { mutableStateOf(true) }
    var keepScreenOn by remember { mutableStateOf(true) }
    var readerBrightness by remember { mutableIntStateOf(ReaderPreferences.FOLLOW_SYSTEM_BRIGHTNESS) }
    var loading by remember(novelId) { mutableStateOf(true) }
    var failure by remember(novelId) { mutableStateOf<ScreenLoadPlan?>(null) }
    var favorited by remember(novelId) { mutableStateOf(false) }
    var chromeVisible by rememberSaveable(novelId) { mutableStateOf(true) }
    var modalState by rememberSaveable(novelId, stateSaver = CouiReaderModalStateSaver) {
        mutableStateOf(CouiReaderModalState())
    }
    var replyTarget by rememberSaveable(novelId, stateSaver = CommentItemNullableSaver) {
        mutableStateOf<CommentItem?>(null)
    }
    var replyDraft by rememberSavedDialogDraft(
        isOpen = replyTarget != null,
        targetKey = replyTarget?.id,
        resetKey = novelId,
    )
    var replyBusy by remember(novelId) { mutableStateOf(false) }
    var replyNotice by remember(novelId) { mutableStateOf<String?>(null) }
    var commentsNotice by remember(novelId) { mutableStateOf<String?>(null) }
    var commentsRefreshKey by remember(novelId) { mutableIntStateOf(0) }
    // 章节加载代际：小说屏没有页码计划与预取，用 chapterOnly 形态只借代际闸门
    val session = remember(novelId) { ReaderSession.chapterOnly() }
    var lastResetChapterId by rememberSaveable(novelId) { mutableStateOf<String?>(null) }
    var lastVolumeAt by remember { mutableLongStateOf(0L) }
    var viewportHeightPx by remember { mutableIntStateOf(0) }

    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val bars = WindowInsets.systemBars.asPaddingValues()
    val colors = COUITheme.colorScheme
    val pageBackdrop = rememberPageBackdrop()
    val materialBackdrop = rememberMaterialBackdropProvider(pageBackdrop)
    val popupBlur = rememberPopupMaterialBlur(pageBackdrop)

    BindCouiReaderWindowEffects(
        keepScreenOn = keepScreenOn,
        readerBrightness = readerBrightness,
    )

    fun openLoginFromModal() {
        modalState = modalState.dismissAllForNavigation()
        replyTarget = null
        replyNotice = null
        commentsNotice = null
        onOpenLogin()
    }

    fun loadChapter(chapterId: String, language: String, showLoading: Boolean) {
        val token = session.beginChapterLoad()
        if (showLoading) loading = true
        failure = null
        scope.launch {
            val outcome = authCall.call { deps.gateway.novelChapter(chapterId, language) }
            if (!session.isLatestChapterLoad(token)) return@launch
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
        volumeKeyPaging = deps.settings.readerVolumeKeyPaging()
        keepScreenOn = deps.settings.readerKeepScreenOn()
        readerBrightness = deps.settings.readerBrightness()
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

    // 只有切换到新章节才回到章首：同章切换简繁或旋转屏幕保留当前阅读位置
    val loadedChapterId = chapter?.ncid
    LaunchedEffect(loadedChapterId) {
        if (loadedChapterId != null && loadedChapterId != lastResetChapterId) {
            lastResetChapterId = loadedChapterId
            scrollState.scrollTo(0)
        }
    }

    val chapters = detail?.chapters.orEmpty()
    val (prevChapter, nextChapter) = couiAdjacentNovelChapters(chapters, currentId)

    fun goTo(target: NovelChapterMeta?) {
        if (target == null) return
        if (target.needBuy) {
            scope.launch { snackbarHostState.showSnackbar("该章节需付费购买，当前版本暂不支持购买") }
            return
        }
        loadChapter(target.ncid, lang, showLoading = true)
    }

    fun toggleFavorite() {
        if (user == null) {
            openLoginFromModal()
            return
        }
        scope.launch {
            val outcome = authCall.call { deps.gateway.toggleNovelFavorite(novelId) }
            val notice = novelActionNotice(outcome, failurePrefix = "收藏失败：")
            if (notice == null) {
                favorited = !favorited
                snackbarHostState.showSnackbar(if (favorited) "已加入收藏" else "已取消收藏")
            } else {
                if (notice.openLogin) openLoginFromModal()
                snackbarHostState.showSnackbar(notice.message)
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

    fun submitReply() {
        val target = replyTarget ?: return
        val text = replyDraft.trim()
        if (text.isEmpty() || replyBusy) return
        replyBusy = true
        replyNotice = null
        scope.launch {
            val outcome = authCall.call { deps.gateway.replyNovelComment(novelId, text, target.id, "") }
            replyBusy = false
            val notice = novelActionNotice(outcome, failurePrefix = "回复失败：")
            if (notice == null) {
                replyTarget = null
                replyDraft = ""
                replyNotice = null
                modalState = modalState.exitReply()
                commentsNotice = "回复已发送"
                commentsRefreshKey++
            } else if (notice.openLogin) {
                openLoginFromModal()
                snackbarHostState.showSnackbar(notice.message)
            } else {
                replyNotice = notice.message
            }
        }
    }

    val modalOpen by rememberUpdatedState(couiHasOpenReaderModal(modalState, replyTarget != null))
    val volumeStep by rememberUpdatedState(
        newValue = { delta: Int ->
            val target = couiNovelVolumeScrollTarget(
                currentScrollPx = scrollState.value,
                maxScrollPx = scrollState.maxValue,
                viewportHeightPx = viewportHeightPx,
                direction = delta,
            )
            if (target != scrollState.value) {
                scope.launch { scrollState.animateScrollTo(target) }
            }
        },
    )
    DisposableEffect(volumeKeyPaging) {
        if (!volumeKeyPaging) return@DisposableEffect onDispose {}
        val mine: (Int) -> Boolean = { keyCode ->
            val delta = when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> -1
                KeyEvent.KEYCODE_VOLUME_DOWN -> 1
                else -> 0
            }
            if (!couiShouldHandleReaderVolumeKey(delta, hasZoomOverlay = false, hasOpenModal = modalOpen)) {
                false
            } else {
                val now = System.currentTimeMillis()
                if (now - lastVolumeAt >= READER_VOLUME_THROTTLE_MS) {
                    lastVolumeAt = now
                    volumeStep(delta)
                }
                true
            }
        }
        ReaderVolumeKeys.handler = mine
        onDispose {
            if (ReaderVolumeKeys.handler === mine) ReaderVolumeKeys.handler = null
        }
    }

    val topHudVisible = chromeVisible || loading || failure != null || chapter == null
    val bottomHudVisible = chromeVisible && !loading

    // COUI Scaffold 提供 COUIPopupHost，确保 OverlayDialog / OverlayBottomSheet 正常挂载与模糊采样
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = colors.background,
    ) { _ ->
        CompositionLocalProvider(
            LocalPageBackdrop provides pageBackdrop,
            LocalPopupMaterialBlur provides popupBlur,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.background),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { viewportHeightPx = it.height }
                        .then(if (pageBackdrop != null) Modifier.layerBackdrop(pageBackdrop) else Modifier),
                ) {
                    // 正文上下各让出 HUD 高度，正文才能从浮栏下方穿过去；让位放在滚动容器内部，
                    // 只有第一屏和最后一屏能看到额外留白
                    val topInset = bars.calculateTopPadding() + ReaderHudTopHeight + ReaderHudBarVerticalMargin * 2 + 4.dp
                    val bottomInset = bars.calculateBottomPadding() + ReaderHudBottomHeight + ReaderHudBarVerticalMargin * 2 + 4.dp
                    when {
                        loading -> Box(
                            modifier = Modifier.fillMaxSize().padding(top = topInset, bottom = bottomInset),
                            contentAlignment = Alignment.Center,
                        ) {
                            CouiLoadingGroup(text = "正在加载章节…", modifier = Modifier.fillMaxWidth().readableWidth())
                        }

                        failure?.phase == ScreenLoadPhase.Expired -> Box(
                            modifier = Modifier.fillMaxSize().padding(top = topInset, bottom = bottomInset),
                            contentAlignment = Alignment.Center,
                        ) {
                            CouiFeedLoginGroup(
                                message = failure?.message.orEmpty(),
                                modifier = Modifier.fillMaxWidth().readableWidth(),
                                onOpenLogin = ::openLoginFromModal,
                            )
                        }

                        failure != null -> Box(
                            modifier = Modifier.fillMaxSize().padding(top = topInset, bottom = bottomInset),
                            contentAlignment = Alignment.Center,
                        ) {
                            CouiErrorGroup(
                                message = failure?.message.orEmpty(),
                                onRetry = { loadChapter(currentId, lang, showLoading = true) },
                                modifier = Modifier.fillMaxWidth().readableWidth(),
                            )
                        }

                        chapter == null -> Box(
                            modifier = Modifier.fillMaxSize().padding(top = topInset, bottom = bottomInset),
                            contentAlignment = Alignment.Center,
                        ) {
                            CouiEmptyGroup(
                                text = "章节不存在，可能已下架",
                                modifier = Modifier.fillMaxWidth().readableWidth(),
                            )
                        }

                        else -> chapter?.let { body ->
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .scrollEndHaptic()
                                    .overScrollVertical()
                                    .verticalScroll(
                                        state = scrollState,
                                        flingBehavior = rememberCOUIFlingBehavior(),
                                    )
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) { chromeVisible = !chromeVisible }
                                    .readableWidth()
                                    .padding(
                                        start = 20.dp,
                                        end = 20.dp,
                                        top = topInset,
                                        bottom = bottomInset,
                                    ),
                            ) {
                                CouiSectionTitleText(
                                    text = body.title,
                                    textAlign = TextAlign.Start,
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                // 正文要跟字号设置走，用基础文本配主题行高
                                BasicText(
                                    text = body.contentText,
                                    style = rememberCouiBodyTextStyle().copy(
                                        color = colors.labelPrimary,
                                        fontSize = fontSize.sp,
                                        lineHeight = (fontSize * 1.75).sp,
                                    ),
                                )
                            }
                        }
                    }
                }

                // HUD 顶底栏处在录制层之外，向按钮与浮动栏提供 LocalMaterialBackdrop
                CompositionLocalProvider(LocalMaterialBackdrop provides materialBackdrop) {
                    ReaderTopAppBarShell(
                        visible = topHudVisible,
                        backdrop = pageBackdrop,
                        title = chapter?.title ?: "阅读",
                        subtitle = detail?.name.orEmpty(),
                        onBack = onBack,
                    ) {
                        Text(
                            text = if (lang == "tw") "繁" else "简",
                            style = COUITheme.textStyles.buttonM,
                            color = colors.primaryText,
                            modifier = Modifier
                                .couiPressable(
                                    shape = NovelHudCapsuleShape,
                                    minTouchTarget = CouiMinTouchTarget,
                                ) {
                                    changeLang(if (lang == "tw") "cn" else "tw")
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            maxLines = 1,
                        )
                    }

                    if (bottomHudVisible) {
                        ReaderHudBar(
                            backdrop = pageBackdrop,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(horizontal = 12.dp, vertical = ReaderHudBarVerticalMargin)
                                .fillMaxWidth()
                                .readableWidth(),
                            height = ReaderHudBottomHeight,
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                ReaderHudAction(
                                    icon = CouiIcons.Settings,
                                    label = "字号",
                                    modifier = Modifier.weight(1f),
                                    onClick = { modalState = modalState.open(CouiReaderModal.FONT_SIZE) },
                                )
                                ReaderHudAction(
                                    icon = CouiIcons.Back,
                                    label = "上一章",
                                    modifier = Modifier.weight(1f),
                                    enabled = prevChapter != null,
                                    onClick = { goTo(prevChapter) },
                                )
                                ReaderHudAction(
                                    icon = CouiIcons.Forward,
                                    label = "下一章",
                                    modifier = Modifier.weight(1f),
                                    enabled = nextChapter != null,
                                    onClick = { goTo(nextChapter) },
                                )
                                ReaderHudAction(
                                    icon = CouiIcons.Comment,
                                    label = "评论",
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        commentsNotice = null
                                        modalState = modalState.open(CouiReaderModal.COMMENTS)
                                    },
                                )
                                ReaderHudAction(
                                    icon = CouiIcons.Folder,
                                    label = "收藏",
                                    modifier = Modifier.weight(1f),
                                    active = favorited,
                                    onClick = ::toggleFavorite,
                                )
                            }
                        }
                    }
                }

                // 提示条随底栏显隐切换避让高度，始终位于系统导航栏之上
                val snackbarBottomPaddingDp = couiReaderSnackbarBottomPaddingDp(
                    hudVisible = bottomHudVisible,
                    bottomBarHeightDp = ReaderHudBottomHeightDp,
                    barVerticalMarginDp = ReaderHudBarVerticalMarginDp,
                )
                GlassSnackbarHost(
                    state = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = snackbarBottomPaddingDp.dp),
                )

                val visibleModal = couiVisibleReaderModal(modalState, hasReplyTarget = replyTarget != null)

                val fontBlur = panelBlur(MaterialBlurDefaults::dialog)
                OverlayDialog(
                    show = visibleModal == CouiReaderModal.FONT_SIZE,
                    title = "字号",
                    backgroundModifier = fontBlur?.backgroundModifier ?: Modifier,
                    backgroundColor = fontBlur?.backgroundColor ?: DialogDefaults.backgroundColor(),
                    materialStroke = fontBlur != null,
                    onDismissRequest = { modalState = modalState.dismiss() },
                    customContent = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = "当前字号 " + fontSize + "sp",
                                style = COUITheme.textStyles.headlineXS,
                                color = colors.labelPrimary,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            // 字号是离散档位，滚轮比滑杆更容易一档一档停准；不环回，避免从最大档直接跳回最小档
                            NumberPicker(
                                value = fontSize,
                                onValueChange = { applyFontSize(it) },
                                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                                range = SettingsRepository.NOVEL_FONT_SIZE_MIN..
                                    SettingsRepository.NOVEL_FONT_SIZE_MAX,
                                unit = "sp",
                                wrapAround = false,
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
                    },
                ) {
                    DialogButtonBar(
                        negative = null,
                        positive = DialogButtonBarAction(
                            text = "完成",
                            recommended = true,
                            onClick = { modalState = modalState.dismiss() },
                        ),
                    )
                }

                val commentsBlur = panelBlur(MaterialBlurDefaults::bottomSheet)
                OverlayBottomSheet(
                    show = visibleModal == CouiReaderModal.COMMENTS,
                    title = "评论",
                    showDragHandle = true,
                    backgroundModifier = commentsBlur?.backgroundModifier ?: Modifier,
                    backgroundColor = commentsBlur?.backgroundColor ?: BottomSheetDefaults.backgroundColor(),
                    materialStroke = commentsBlur != null,
                    endAction = {
                        CouiPageTopBarAction(
                            icon = CouiIcons.Close,
                            contentDescription = "关闭",
                            onClick = { modalState = modalState.dismiss() },
                        )
                    },
                    onDismissRequest = { modalState = modalState.dismiss() },
                ) {
                    NovelCommentsPanel(
                        deps = deps,
                        novelId = novelId,
                        notice = commentsNotice,
                        refreshKey = commentsRefreshKey,
                        onNoticeChange = { commentsNotice = it },
                        onReply = { item ->
                            if (user == null) {
                                openLoginFromModal()
                            } else {
                                replyTarget = item
                                replyDraft = ""
                                replyNotice = null
                                modalState = modalState.enterReply()
                            }
                        },
                        onOpenLogin = ::openLoginFromModal,
                        onLoginExpired = { message ->
                            openLoginFromModal()
                            scope.launch { snackbarHostState.showSnackbar(message) }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                    )
                }

                // 回复弹窗与评论弹窗保持同级互斥，直接承接 OverlayDialog 玻璃材质
                val retainedReplyTarget = rememberRetainedTarget(replyTarget)
                val replyBlur = panelBlur(MaterialBlurDefaults::dialog)
                OverlayDialog(
                    show = replyTarget != null,
                    title = "回复 " + retainedReplyTarget?.author.orEmpty(),
                    backgroundModifier = replyBlur?.backgroundModifier ?: Modifier,
                    backgroundColor = replyBlur?.backgroundColor ?: DialogDefaults.backgroundColor(),
                    materialStroke = replyBlur != null,
                    onDismissRequest = {
                        if (!replyBusy) {
                            replyTarget = null
                            replyNotice = null
                            modalState = modalState.exitReply()
                        }
                    },
                    customContent = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 4.dp),
                        ) {
                            ReaderCommentComposer(
                                loggedIn = true,
                                text = replyDraft,
                                busy = replyBusy,
                                notice = replyNotice,
                                onTextChange = { replyDraft = couiClampCommentInput(it) },
                                onLogin = {},
                                onSend = ::submitReply,
                                showSendButton = false,
                            )
                        }
                    },
                ) {
                    DialogButtonBar(
                        negative = DialogButtonBarAction(
                            text = "取消",
                            enabled = !replyBusy,
                            onClick = {
                                if (!replyBusy) {
                                    replyTarget = null
                                    replyNotice = null
                                    modalState = modalState.exitReply()
                                }
                            },
                        ),
                        positive = DialogButtonBarAction(
                            text = if (replyBusy) "发送中…" else "发送",
                            enabled = !replyBusy && replyDraft.isNotBlank(),
                            recommended = true,
                            onClick = ::submitReply,
                        ),
                    )
                }
            }
        }
    }
}

/** 字号增减的胶囊按钮：底色经 couiPressable 传入，保证可见胶囊与 48dp 触区分离。 */
@Composable
private fun NovelFontStep(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = COUITheme.colorScheme
    Box(
        modifier = modifier
            .couiPressable(
                shape = NovelHudCapsuleShape,
                backgroundColor = couiDialogSurfaceColor(elevated = true),
                enabled = enabled,
                minTouchTarget = CouiMinTouchTarget,
                onClick = onClick,
            )
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = COUITheme.textStyles.headlineXS,
            color = if (enabled) colors.primaryText else colors.labelSecondary,
        )
    }
}

/**
 * 小说评论区：虚拟化列表、续页、发评论与内联反馈，回复动作交由外层平级弹窗承接。
 */
@Composable
private fun NovelCommentsPanel(
    deps: ScreenDeps,
    novelId: String,
    notice: String?,
    refreshKey: Int,
    onNoticeChange: (String?) -> Unit,
    onReply: (CommentItem) -> Unit,
    onOpenLogin: () -> Unit,
    onLoginExpired: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val authCall = deps.authCall
    val user by deps.account.user.collectAsState()
    var feed by remember(novelId) { mutableStateOf(PagedFeed<CommentItem>()) }
    var loading by remember(novelId) { mutableStateOf(true) }
    var failure by remember(novelId) { mutableStateOf<ScreenLoadPlan?>(null) }
    var draft by rememberSaveable(novelId) { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun load(targetPage: Int, replace: Boolean, showLoading: Boolean) {
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
                // 翻页失败保留已加载评论并允许页脚重试，失效则交外层收起弹窗并引导登录
                val plan = planLoad(
                    outcome = outcome,
                    hasContent = !replace && requested.items.isNotEmpty(),
                    failureMessage = "评论加载失败",
                )
                if (plan.keepContent) {
                    feed = requested.failLoadMore()
                    if (outcome == SessionOutcome.Expired) {
                        onLoginExpired(plan.notice.orEmpty())
                    } else {
                        onNoticeChange(plan.notice)
                    }
                } else {
                    feed = requested.copy(loadingMore = false)
                    failure = plan
                }
            }
        }
    }

    LaunchedEffect(novelId, refreshKey) {
        load(1, replace = true, showLoading = refreshKey == 0 && feed.items.isEmpty())
    }

    fun send() {
        val text = draft.trim()
        if (text.isEmpty() || sending) return
        if (user == null) {
            onOpenLogin()
            return
        }
        sending = true
        onNoticeChange(null)
        scope.launch {
            val outcome = authCall.call { deps.gateway.postNovelComment(novelId, text, "") }
            sending = false
            val actionNotice = novelActionNotice(outcome, failurePrefix = "发布失败：")
            if (actionNotice == null) {
                draft = ""
                onNoticeChange("评论已发布")
                load(1, replace = true, showLoading = false)
            } else if (actionNotice.openLogin) {
                onLoginExpired(actionNotice.message)
            } else {
                onNoticeChange(actionNotice.message)
            }
        }
    }

    Column(modifier = modifier) {
        when {
            loading -> ReaderDialogHint(text = "正在加载评论…")

            failure?.phase == ScreenLoadPhase.Expired -> ReaderDialogHint(
                text = failure?.message.orEmpty() + "，点此登录",
                onClick = onOpenLogin,
            )

            failure != null -> ReaderDialogHint(
                text = failure?.message.orEmpty() + "，点此重试",
                onClick = { load(1, replace = true, showLoading = true) },
            )

            feed.items.isEmpty() -> ReaderDialogHint(text = CouiStrings.EmptyComments)

            else -> LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = couiDialogListMaxHeight(320.dp)),
            ) {
                items(feed.items, key = { it.id }) { item ->
                    ReaderCommentRow(
                        item = item,
                        onReply = { onReply(item) },
                    )
                    if (item.id != feed.items.last().id || feed.hasMore || feed.loadingMore || feed.loadMoreFailed) {
                        HorizontalDivider()
                    }
                }
                item(key = "novel-comments-footer") {
                    CouiLoadMoreFooter(
                        feed = feed,
                        errorMessage = "下一页加载失败",
                        onLoadMore = { load(feed.page + 1, replace = false, showLoading = false) },
                        onRetry = { load(feed.page + 1, replace = false, showLoading = false) },
                    )
                }
            }
        }
        HorizontalDivider()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
        ) {
            ReaderCommentComposer(
                loggedIn = user != null,
                text = draft,
                busy = sending,
                notice = notice,
                onTextChange = { draft = couiClampCommentInput(it) },
                onLogin = onOpenLogin,
                onSend = ::send,
            )
        }
    }
}
