// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.page

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import com.itos.coui.layout.Divider
import com.itos.coui.layout.Group
import com.itos.coui.layout.Item
import com.itos.coui.scroll.rememberCouiOverscrollEffect
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.text.rememberCouiTextStyle
import com.itos.coui.theme.CouiTheme as CouiKitTheme
import com.itos.coui.topappbar.CouiTopAppBarDefaults
import com.itos.coui.topappbar.LargeTopAppBar
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.ui.AlbumCoverImage
import com.jm3.app.miuix.ui.CommentItemNullableSaver
import com.jm3.app.miuix.ui.GlassSnackbarHost
import com.jm3.app.miuix.ui.LocalWideLayout
import com.jm3.app.miuix.ui.ScreenLoadPlan
import com.jm3.app.miuix.ui.ScreenLoadPhase
import com.jm3.app.miuix.ui.StringSetSaver
import com.jm3.app.miuix.ui.rememberSavedDialogDraft
import com.jm3.app.miuix.ui.coui.component.CouiSectionTitleText
import com.jm3.app.miuix.ui.coui.component.CouiBodyText
import com.jm3.app.miuix.ui.coui.component.rememberCouiBodyTextStyle
import com.jm3.app.miuix.ui.coui.component.CouiAppScaffold
import com.jm3.app.miuix.ui.coui.component.CouiCommentRow
import com.jm3.app.miuix.ui.coui.component.CouiEmptyGroup
import com.jm3.app.miuix.ui.coui.component.CouiGroupRow
import com.jm3.app.miuix.ui.coui.component.CouiGroupTitle
import com.jm3.app.miuix.ui.coui.component.CouiLoadMoreFooter
import com.jm3.app.miuix.ui.coui.component.CouiLoadingGroup
import com.jm3.app.miuix.ui.coui.component.CouiMinTouchTarget
import com.jm3.app.miuix.ui.coui.component.CouiScreenFailureGroup
import com.jm3.app.miuix.ui.coui.component.CouiSelectedContainerAlpha
import com.jm3.app.miuix.ui.coui.component.CouiTopBarAction
import com.jm3.app.miuix.ui.coui.component.CouiTopBarBackButton
import com.jm3.app.miuix.ui.coui.component.CouiTopBarTitle
import com.jm3.app.miuix.ui.coui.component.couiPressable
import com.jm3.app.miuix.ui.coui.component.couiDialogMaterial
import com.jm3.app.miuix.ui.coui.component.couiScreenContentPadding
import com.jm3.app.miuix.ui.coui.component.couiTopAppBarColors
import com.jm3.app.miuix.ui.coui.component.rememberRetainedTarget
import com.jm3.app.miuix.ui.coui.icon.CouiIcons
import com.jm3.app.miuix.ui.coui.theme.CouiStrings
import com.jm3.app.miuix.ui.coui.theme.CouiTheme
import com.jm3.app.miuix.ui.formatEpochSeconds
import com.jm3.app.miuix.ui.novelActionNotice
import com.jm3.app.miuix.ui.novelGroupTitle
import com.jm3.app.miuix.ui.planLoad
import com.jm3.app.miuix.ui.readableWidth
import com.jm3.app.miuix.ui.rememberDateFormatter
import com.jm3.app.miuix.ui.shouldShowChapterGroupToggle
import com.jm3.app.miuix.ui.skin.LocalSkin
import com.jm3.core.appendedBy
import com.jm3.core.model.CommentItem
import com.jm3.core.model.NovelChapterMeta
import com.jm3.core.model.NovelDetail
import com.jm3.core.model.NovelMeta
import com.jm3.core.model.SearchScope
import com.jm3.core.paging.PagedFeed
import com.jm3.core.reader.ChapterGroup
import com.jm3.core.reader.ChapterGroups
import com.jm3.core.replacedBy
import com.jm3.core.session.SessionOutcome
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SnackbarHostState

/** 简介折叠阈值：与 HyperOS 版小说详情一致，超过它才给展开入口。 */
private const val NOVEL_DESCRIPTION_COLLAPSE_THRESHOLD = 80

/** 单条评论正文上限：与小说评论接口一致。 */
private const val NOVEL_COMMENT_MAX_LENGTH = 300

/** 评论正文最多铺多少行，超出交给「展开回复」与翻页，不把一屏塞满。 */
private const val NOVEL_COMMENT_BODY_MAX_LINES = 12

private const val NOVEL_COMMENT_LOAD_FAILED = "评论加载失败"

private const val NOVEL_COMMENT_LOAD_MORE_FAILED = CouiStrings.LoadMoreFailedRetry

/** 相关作品卡的宽度与封面高度：与浏览页相关作品行同尺寸，避免同一屏出现两种卡宽。 */
private val NOVEL_HERO_COVER_WIDTH = 100.dp

private val NOVEL_HERO_COVER_HEIGHT = 136.dp

private val NOVEL_RELATED_CARD_WIDTH = 104.dp

private val NOVEL_RELATED_COVER_HEIGHT = 138.dp

/** 详情页整屏形态：加载中 / 失败（含会话失效）/ 已加载。 */
private sealed interface CouiNovelDetailState {
    data object Loading : CouiNovelDetailState

    data class Failed(val plan: ScreenLoadPlan) : CouiNovelDetailState

    data class Loaded(val detail: NovelDetail) : CouiNovelDetailState
}

/** 目录行：组头（十张一组）与章节行共用一份懒列表，键必须唯一。 */
private sealed interface CouiNovelChapterRow {
    val key: String

    data class GroupHeader(
        val groupId: String,
        val title: String,
        val summary: String,
        val expanded: Boolean,
        val showToggle: Boolean,
    ) : CouiNovelChapterRow {
        override val key: String get() = "novel-group-$groupId"
    }

    data class ChapterLine(
        val chapter: NovelChapterMeta,
        val marker: String?,
        val highlighted: Boolean,
    ) : CouiNovelChapterRow {
        override val key: String get() = "novel-chapter-${chapter.ncid}"
    }
}

/**
 * COUI 小说详情页。
 *
 * 宽屏（[LocalWideLayout.twoPaneDetail]）把版面拆成 0.38 / 0.62 两栏：
 * 左栏放封面头图、阅读动作、简介与作品信息，右栏放目录、相关作品与评论。
 * 窄屏合成一条长列表，内容顺序与两栏一致，滚动位置各记一份。
 *
 * 数据接线与 HyperOS 版小说详情逐条对齐：同一份 authCall 会话包装、同一套
 * planLoad 屏级形态、同样的点赞/收藏/章节需购买提示；评论走 novelComments 翻页，
 * 评论树摊平复用相册详情页的 [flattenComments]。
 */
@Composable
internal fun CouiNovelDetailScreen(
    deps: ScreenDeps,
    novelId: String,
    onBack: () -> Unit,
    onOpenNovel: (String) -> Unit,
    onOpenReader: (novelId: String, chapterId: String) -> Unit,
    onOpenSearch: (query: String, scope: SearchScope) -> Unit,
    onOpenLogin: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    CouiTheme {
        CouiNovelDetailBody(
            deps = deps,
            novelId = novelId,
            onBack = onBack,
            onOpenNovel = onOpenNovel,
            onOpenReader = onOpenReader,
            onOpenSearch = onOpenSearch,
            onOpenLogin = onOpenLogin,
            snackbarHostState = snackbarHostState,
        )
    }
}

/**
 * 正文：把详情与评论两套状态收在同一个作用域里。
 *
 * 两栏时不复制状态——左右两栏共用同一份 detail 与评论 feed，只各持一份 LazyListState，
 * 所以宽屏切窄屏不会重新请求，滚动位置按栏各记一份。
 */
@Composable
private fun CouiNovelDetailBody(
    deps: ScreenDeps,
    novelId: String,
    onBack: () -> Unit,
    onOpenNovel: (String) -> Unit,
    onOpenReader: (novelId: String, chapterId: String) -> Unit,
    onOpenSearch: (query: String, scope: SearchScope) -> Unit,
    onOpenLogin: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val gateway = deps.gateway
    val authCall = deps.authCall
    val user by deps.account.user.collectAsState()
    val wideLayout = LocalWideLayout.current
    val dateFormat = rememberDateFormatter("yyyy-MM-dd")
    val scope = rememberCoroutineScope()

    var state by remember(novelId) { mutableStateOf<CouiNovelDetailState>(CouiNovelDetailState.Loading) }
    var detailToken by remember(novelId) { mutableIntStateOf(0) }
    var savedChapterId by remember(novelId) { mutableStateOf<String?>(null) }
    var actionBusy by remember { mutableStateOf(false) }
    var ascending by rememberSaveable(novelId) { mutableStateOf(true) }
    var selectedGroupId by rememberSaveable(novelId) { mutableStateOf<String?>(null) }
    var allGroupsCollapsed by rememberSaveable(novelId) { mutableStateOf(false) }
    var descriptionExpanded by rememberSaveable(novelId) { mutableStateOf(false) }

    var commentFeed by remember(novelId) { mutableStateOf(PagedFeed<CommentItem>()) }
    var commentTotal by remember(novelId) { mutableIntStateOf(0) }
    var commentLoading by remember(novelId) { mutableStateOf(true) }
    var commentFailure by remember(novelId) { mutableStateOf<ScreenLoadPlan?>(null) }
    var commentDraft by rememberSaveable(novelId) { mutableStateOf("") }
    var commentSending by remember(novelId) { mutableStateOf(false) }
    var expandedReplies by rememberSaveable(novelId, stateSaver = StringSetSaver) {
        mutableStateOf<Set<String>>(emptySet())
    }
    var commentToken by remember(novelId) { mutableIntStateOf(0) }
    // 回复目标存整条评论：弹窗标题要作者名，提交要拿 id 当父评论，少一次回查列表
    var replyTarget by rememberSaveable(novelId, stateSaver = CommentItemNullableSaver) {
        mutableStateOf<CommentItem?>(null)
    }
    var replyBusy by remember(novelId) { mutableStateOf(false) }

    val singleListState = rememberLazyListState()
    val leftListState = rememberLazyListState()
    val rightListState = rememberLazyListState()

    fun load() {
        detailToken++
        val token = detailToken
        state = CouiNovelDetailState.Loading
        scope.launch {
            val outcome = authCall.call { gateway.novelDetail(novelId) }
            if (token != detailToken) return@launch
            state = if (outcome is SessionOutcome.Ok) {
                CouiNovelDetailState.Loaded(outcome.value)
            } else {
                CouiNovelDetailState.Failed(
                    planLoad(outcome = outcome, hasContent = false, failureMessage = CouiStrings.LoadFailed),
                )
            }
        }
    }

    fun loadComments(targetPage: Int, replace: Boolean, showLoading: Boolean) {
        // 还能不能拉、在不在拉全交给 feed 判定：重复点「加载更多」不会再并发打同一页
        val requested = if (replace) commentFeed else commentFeed.beginLoadMore()
        if (!replace && requested === commentFeed) return
        commentToken++
        val token = commentToken
        if (showLoading) commentLoading = true
        commentFailure = null
        commentFeed = requested
        scope.launch {
            val outcome = authCall.call { gateway.novelComments(novelId, targetPage) }
            if (token != commentToken) return@launch
            commentLoading = false
            if (outcome is SessionOutcome.Ok) {
                commentTotal = outcome.value.total
                commentFeed = if (replace) {
                    PagedFeed<CommentItem>().replacedBy(outcome.value)
                } else {
                    requested.appendedBy(outcome.value)
                }
            } else {
                // 翻页失败保留已加载评论并让页脚可重试；首屏失败才整块换掉
                val plan = planLoad(
                    outcome = outcome,
                    hasContent = !replace && requested.items.isNotEmpty(),
                    failureMessage = NOVEL_COMMENT_LOAD_FAILED,
                )
                if (plan.keepContent) {
                    commentFeed = requested.failLoadMore()
                    snackbarHostState.showSnackbar(plan.notice.orEmpty())
                } else {
                    commentFeed = requested.copy(loadingMore = false)
                    commentFailure = plan
                }
            }
        }
    }

    LaunchedEffect(novelId) {
        savedChapterId = deps.settings.novelLastChapter(novelId)
        load()
        loadComments(targetPage = 1, replace = true, showLoading = true)
    }

    fun currentDetail(): NovelDetail? = (state as? CouiNovelDetailState.Loaded)?.detail

    fun openChapter(chapter: NovelChapterMeta) {
        if (chapter.needBuy) {
            scope.launch { snackbarHostState.showSnackbar("该章节需付费购买，当前版本暂不支持购买") }
            return
        }
        onOpenReader(novelId, chapter.ncid)
    }

    fun startReading() {
        val current = currentDetail() ?: return
        val target = current.chapters.firstOrNull { it.ncid == savedChapterId } ?: current.chapters.firstOrNull()
        if (target == null) {
            scope.launch { snackbarHostState.showSnackbar(CouiStrings.EmptyChapters) }
            return
        }
        openChapter(target)
    }

    fun toggleLike() {
        val current = currentDetail() ?: return
        if (current.liked) {
            scope.launch { snackbarHostState.showSnackbar("已经点过赞了") }
            return
        }
        if (actionBusy) return
        actionBusy = true
        scope.launch {
            val outcome = authCall.call { gateway.toggleNovelLike(novelId) }
            actionBusy = false
            val notice = novelActionNotice(outcome, failurePrefix = "点赞失败：")
            if (notice == null) {
                state = CouiNovelDetailState.Loaded(current.copy(liked = true))
                snackbarHostState.showSnackbar("点赞成功")
            } else {
                snackbarHostState.showSnackbar(notice.message)
                if (notice.openLogin) onOpenLogin()
            }
        }
    }

    fun toggleFavorite() {
        val current = currentDetail() ?: return
        if (user == null) {
            onOpenLogin()
            return
        }
        if (actionBusy) return
        actionBusy = true
        scope.launch {
            val outcome = authCall.call { gateway.toggleNovelFavorite(novelId) }
            actionBusy = false
            val notice = novelActionNotice(outcome, failurePrefix = "收藏失败：")
            if (notice == null) {
                state = CouiNovelDetailState.Loaded(current.copy(favorite = !current.favorite))
                snackbarHostState.showSnackbar(if (current.favorite) "已取消收藏" else "已加入收藏")
            } else {
                snackbarHostState.showSnackbar(notice.message)
                if (notice.openLogin) onOpenLogin()
            }
        }
    }

    fun sendComment() {
        val text = commentDraft.trim()
        if (text.isEmpty() || commentSending) return
        if (user == null) {
            onOpenLogin()
            return
        }
        commentSending = true
        scope.launch {
            val outcome = authCall.call { gateway.postNovelComment(novelId, text, "") }
            commentSending = false
            val notice = novelActionNotice(outcome, failurePrefix = "发布失败：")
            if (notice == null) {
                commentDraft = ""
                snackbarHostState.showSnackbar("评论已发布")
                loadComments(targetPage = 1, replace = true, showLoading = false)
            } else {
                snackbarHostState.showSnackbar(notice.message)
                if (notice.openLogin) onOpenLogin()
            }
        }
    }

    /**
     * 回复某条评论：父评论 id 取 [replyTarget]，chapterId 留空——本页评论挂在整部作品下。
     *
     * 成功后重新拉第一页而不是往本地评论树里插节点：接口返回的回复带自己的层级与时间戳，
     * 本地拼出来的节点会和后续翻页结果对不上，同一棵树上出现两份同 id 节点。
     */
    fun sendReply(text: String) {
        val target = replyTarget ?: return
        val body = text.trim()
        if (body.isEmpty() || replyBusy) return
        replyBusy = true
        scope.launch {
            val outcome = authCall.call { gateway.replyNovelComment(novelId, body, target.id, "") }
            replyBusy = false
            val notice = novelActionNotice(outcome, failurePrefix = "回复失败：")
            if (notice == null) {
                replyTarget = null
                snackbarHostState.showSnackbar("回复已发送")
                loadComments(targetPage = 1, replace = true, showLoading = false)
            } else {
                // 失败保留弹窗与草稿：改完能直接重发，不必重新点一次「回复」
                snackbarHostState.showSnackbar(notice.message)
                if (notice.openLogin) onOpenLogin()
            }
        }
    }

    val detail = currentDetail()
    val chapterGroups = remember(detail, ascending) {
        detail?.let { ChapterGroups.of(items = it.chapters, keyOf = NovelChapterMeta::ncid, descending = !ascending) }
            .orEmpty()
    }
    // 未选中时默认展开首组；用户收起后记下「全部收起」，否则会被默认值重新展开
    val expandedGroupId = if (allGroupsCollapsed) null else (selectedGroupId ?: chapterGroups.firstOrNull()?.id)
    val chapterRows = remember(chapterGroups, expandedGroupId, savedChapterId) {
        planNovelChapterRows(
            groups = chapterGroups,
            expandedGroupId = expandedGroupId,
            savedChapterId = savedChapterId,
        )
    }
    val onToggleGroup: (String) -> Unit = { groupId ->
        val collapsing = expandedGroupId == groupId
        allGroupsCollapsed = collapsing
        selectedGroupId = if (collapsing) null else groupId
    }

    val scrollBehavior = CouiTopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        canScroll = {
            if (wideLayout.twoPaneDetail) {
                leftListState.canScrollForward || leftListState.canScrollBackward ||
                    rightListState.canScrollForward || rightListState.canScrollBackward
            } else {
                singleListState.canScrollForward || singleListState.canScrollBackward
            }
        },
    )
    val title = detail?.name ?: "小说详情"

    val headerItems: LazyListScope.() -> Unit = {
        when (val current = state) {
            CouiNovelDetailState.Loading -> item(key = "novel-loading") { CouiLoadingGroup() }
            is CouiNovelDetailState.Failed -> item(key = "novel-failed") {
                CouiScreenFailureGroup(
                    message = current.plan.message,
                    expired = current.plan.phase == ScreenLoadPhase.Expired,
                    onRetry = { load() },
                    onOpenLogin = onOpenLogin,
                )
            }
            is CouiNovelDetailState.Loaded -> {
                item(key = "novel-hero") {
                    CouiNovelHeroCard(
                        detail = current.detail,
                        dateFormat = dateFormat,
                        onOpenSearch = onOpenSearch,
                    )
                }
                item(key = "novel-actions") {
                    CouiNovelActionsGroup(
                        detail = current.detail,
                        savedChapter = savedChapterId,
                        busy = actionBusy,
                        onStartReading = { startReading() },
                        onToggleLike = { toggleLike() },
                        onToggleFavorite = { toggleFavorite() },
                    )
                }
                item(key = "novel-description") {
                    CouiNovelDescriptionGroup(
                        text = current.detail.description,
                        expanded = descriptionExpanded,
                        onToggle = { descriptionExpanded = !descriptionExpanded },
                    )
                }
                item(key = "novel-info") {
                    CouiNovelInfoGroup(detail = current.detail, dateFormat = dateFormat)
                }
            }
        }
    }

    val catalogItems: LazyListScope.() -> Unit = {
        val current = detail
        if (current != null) {
            item(key = "novel-chapters-title") {
                CouiGroupTitle(
                    title = "章节（${current.chapters.size}）",
                    actions = { CouiNovelSortAction(ascending = ascending, onToggle = { ascending = !ascending }) },
                )
            }
            if (chapterRows.isEmpty()) {
                item(key = "novel-chapters-empty") { CouiEmptyGroup(text = CouiStrings.EmptyChapters) }
            } else {
                itemsIndexed(
                    items = chapterRows,
                    key = { _, row -> row.key },
                    contentType = { _, row -> row.contentTypeKey() },
                ) { index, row ->
                    CouiNovelChapterRowCard(
                        row = row,
                        isFirst = index == 0,
                        isLast = index == chapterRows.lastIndex,
                        onToggleGroup = onToggleGroup,
                        onOpenChapter = { openChapter(it) },
                    )
                }
            }
            item(key = "novel-related-title") { CouiGroupTitle(title = "相关作品") }
            if (current.related.isEmpty()) {
                item(key = "novel-related-empty") { CouiEmptyGroup(text = "暂无相关作品") }
            } else {
                item(key = "novel-related") {
                    CouiNovelRelatedGroup(
                        related = current.related,
                        dateFormat = dateFormat,
                        onOpenNovel = onOpenNovel,
                    )
                }
            }
            item(key = "novel-comments-title") { CouiGroupTitle(title = "评论（$commentTotal）") }
            item(key = "novel-comment-composer") {
                CouiNovelCommentComposer(
                    draft = commentDraft,
                    sending = commentSending,
                    loggedIn = user != null,
                    onDraft = { commentDraft = it.take(NOVEL_COMMENT_MAX_LENGTH) },
                    onSend = { sendComment() },
                    onOpenLogin = onOpenLogin,
                )
            }
            when {
                commentLoading -> item(key = "novel-comments-loading") {
                    CouiLoadingGroup(text = "正在加载评论…")
                }
                commentFailure != null -> item(key = "novel-comments-failed") {
                    CouiScreenFailureGroup(
                        message = commentFailure!!.message,
                        expired = commentFailure!!.phase == ScreenLoadPhase.Expired,
                        onRetry = { loadComments(targetPage = 1, replace = true, showLoading = true) },
                        onOpenLogin = onOpenLogin,
                    )
                }
                commentFeed.items.isEmpty() -> item(key = "novel-comments-empty") {
                    CouiEmptyGroup(text = CouiStrings.EmptyComments)
                }
                else -> {
                    val rows = flattenComments(roots = commentFeed.items, expandedIds = expandedReplies)
                    itemsIndexed(
                        items = rows,
                        key = { _, row -> row.key },
                        contentType = { _, row -> row.contentType },
                    ) { index, row ->
                        when (row) {
                            is CommentRow.Comment -> CouiNovelCommentItemCard(
                                row = row,
                                isFirst = index == 0,
                                onReply = { target ->
                                    // 未登录先送去登录：等用户在弹窗里写完再拦会把草稿白填一遍
                                    if (user == null) onOpenLogin() else replyTarget = target
                                },
                            )
                            is CommentRow.RepliesToggle -> CouiNovelRepliesToggleRow(
                                row = row,
                                onToggle = { parentId ->
                                    expandedReplies = if (parentId in expandedReplies) {
                                        expandedReplies - parentId
                                    } else {
                                        expandedReplies + parentId
                                    }
                                },
                            )
                        }
                    }
                    item(key = "novel-comments-footer") {
                        CouiLoadMoreFooter(
                            feed = commentFeed,
                            errorMessage = NOVEL_COMMENT_LOAD_MORE_FAILED,
                            onRetry = {
                                loadComments(
                                    targetPage = commentFeed.page + 1,
                                    replace = false,
                                    showLoading = false,
                                )
                            },
                        )
                    }
                }
            }
        }
    }

    // 评论在列表末尾：接近底部就补一页，点页脚只是兜底重试
    CouiPrefetchEffect(
        listState = if (wideLayout.twoPaneDetail) rightListState else singleListState,
    ) { lastIndex, totalItemsCount, prefetchRows ->
        if (!commentFeed.canLoadMore || commentFeed.loadMoreFailed) return@CouiPrefetchEffect
        if (!shouldPrefetch(lastIndex, totalItemsCount, prefetchRows)) return@CouiPrefetchEffect
        loadComments(targetPage = commentFeed.page + 1, replace = false, showLoading = false)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CouiAppScaffold(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                LargeTopAppBar(
                    title = { CouiTopBarTitle(text = title) },
                    navigationIcon = { CouiTopBarBackButton(onClick = onBack) },
                    actions = {
                        CouiTopBarAction(
                            icon = CouiIcons.Refresh,
                            contentDescription = "刷新",
                            onClick = {
                                load()
                                loadComments(targetPage = 1, replace = true, showLoading = false)
                            },
                        )
                    },
                    scrollBehavior = scrollBehavior,
                    colors = couiTopAppBarColors(),
                )
            },
        ) { innerPadding ->
            // 折叠位移留到测量期读取：组合期算成字面量会把首帧的顶栏高度冻结住
            val contentPadding = couiScreenContentPadding(
                scrollBehavior = scrollBehavior,
                innerPadding = innerPadding,
                horizontalPadding = null,
                extraBottom = if (WindowInsets.isImeVisible) 0.dp else 24.dp,
            )
            if (wideLayout.twoPaneDetail) {
                Row(modifier = Modifier.fillMaxSize().readableWidth().consumeWindowInsets(innerPadding)) {
                    LazyColumn(
                        state = leftListState,
                        modifier = Modifier.weight(0.38f).fillMaxHeight(),
                        contentPadding = contentPadding,
                        overscrollEffect = rememberCouiOverscrollEffect(),
                    ) {
                        couiInclude(headerItems)
                    }
                    LazyColumn(
                        state = rightListState,
                        modifier = Modifier.weight(0.62f).fillMaxHeight(),
                        contentPadding = contentPadding,
                        overscrollEffect = rememberCouiOverscrollEffect(),
                    ) {
                        couiInclude(catalogItems)
                    }
                }
            } else {
                LazyColumn(
                    state = singleListState,
                    modifier = Modifier.fillMaxSize().readableWidth().consumeWindowInsets(innerPadding),
                    contentPadding = contentPadding,
                    overscrollEffect = rememberCouiOverscrollEffect(),
                ) {
                    couiInclude(headerItems)
                    couiInclude(catalogItems)
                }
            }
        }
        GlassSnackbarHost(state = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        // 回复面板放在 Scaffold 外：它自带窗口，压在列表与 snackbar 之上，两栏/单栏都用同一份
        CouiNovelReplyDialog(
            visible = replyTarget != null,
            targetAuthor = replyTarget?.author,
            targetKey = replyTarget?.id,
            busy = replyBusy,
            onDismiss = { replyTarget = null },
            onSend = { sendReply(it) },
        )
    }
}

/** 组头与章节行的复用类型：行布局不同，分开复用避免互踩测量结果。 */
private fun CouiNovelChapterRow.contentTypeKey(): String = when (this) {
    is CouiNovelChapterRow.GroupHeader -> "novel-group-header"
    is CouiNovelChapterRow.ChapterLine -> "novel-chapter-line"
}

/**
 * 把分组摊成懒列表行序列：展开的组逐章铺开，收起的组只保留组头。
 *
 * 组默认展开规则由调用方给 [expandedGroupId]（null 表示全部收起）；
 * 只有一章的组不给折叠入口，直接铺开，与相册详情的目录一致。
 */
private fun planNovelChapterRows(
    groups: List<ChapterGroup<NovelChapterMeta>>,
    expandedGroupId: String?,
    savedChapterId: String?,
): List<CouiNovelChapterRow> {
    val rows = ArrayList<CouiNovelChapterRow>()
    groups.forEach { group ->
        val showToggle = shouldShowChapterGroupToggle(group.items.size)
        val expanded = if (showToggle) group.id == expandedGroupId else true
        rows += CouiNovelChapterRow.GroupHeader(
            groupId = group.id,
            title = novelGroupTitle(group.items),
            summary = "${group.items.size} 章 · 点击${if (expanded) "收起" else "展开"}",
            expanded = expanded,
            showToggle = showToggle,
        )
        if (expanded) {
            group.items.forEach { chapter ->
                val marker = novelChapterMarker(chapter, savedChapterId)
                rows += CouiNovelChapterRow.ChapterLine(
                    chapter = chapter,
                    marker = marker,
                    highlighted = marker == "上次读到",
                )
            }
        }
    }
    return rows
}

/** 单章摘要：上次读到 > 需购买 > 新章节 > 已购买，与 HyperOS 版小说目录同序。 */
private fun novelChapterMarker(chapter: NovelChapterMeta, savedChapterId: String?): String? = when {
    chapter.ncid == savedChapterId -> "上次读到"
    chapter.needBuy -> "需购买"
    chapter.isNew -> "新章节"
    chapter.purchased -> "已购买"
    else -> null
}


/** 详情头图：封面 + 书名 + 作者胶囊 + 连载信息 + 标签，标签点击进标签检索。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CouiNovelHeroCard(
    detail: NovelDetail,
    dateFormat: DateTimeFormatter,
    onOpenSearch: (String, SearchScope) -> Unit,
) {
    val scheme = CouiKitTheme.colorScheme
    val corner = LocalSkin.current.shapes.cardCorner
    val added = remember(detail.addtime) { formatEpochSeconds(detail.addtime, dateFormat) }
    val author = detail.author.ifBlank { "佚名" }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CouiPageDefaults.horizontalMargin)
            .padding(top = CouiPageDefaults.categoryTopSpacing, bottom = CouiPageDefaults.groupSpacing)
            .clip(CouiPageDefaults.shape)
            .background(scheme.card),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(width = NOVEL_HERO_COVER_WIDTH, height = NOVEL_HERO_COVER_HEIGHT)
                    .clip(RoundedCornerShape(corner))
                    .background(scheme.container),
            ) {
                AlbumCoverImage(
                    url = detail.coverUrl,
                    contentDescription = detail.name,
                    modifier = Modifier.fillMaxSize(),
                    crossfade = false,
                    skeleton = true,
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CouiSectionTitleText(
                    text = detail.name,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Start,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "NVL${detail.id}",
                        kind = CouiTextKind.GroupFooter,
                        color = scheme.onSurfaceVariant,
                        textAlign = TextAlign.Start,
                    )
                    Box(
                        modifier = Modifier
                            .couiPressable(
                                shape = CouiCapsuleShape,
                                backgroundColor = scheme.accent.copy(alpha = CouiSelectedContainerAlpha),
                                spotlight = SpotlightPreset.ChipTab,
                                minTouchTarget = CouiMinTouchTarget,
                                onClick = { onOpenSearch(author, SearchScope.AUTHOR) },
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = author,
                            kind = CouiTextKind.GroupFooter,
                            color = scheme.accent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Text(
                    text = buildString {
                        append(if (detail.isEnd) "已完结" else "连载中")
                        if (detail.serialStatus.isNotBlank()) append(" · ${detail.serialStatus}")
                        append(" · ${detail.chapters.size} 章")
                    },
                    kind = CouiTextKind.GroupFooter,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Start,
                )
                Text(
                    text = buildString {
                        if (added.isNotBlank()) append("上架 $added")
                        detail.totalViews?.let { append(" · 阅 $it") }
                        detail.likes?.let { append(" · ♥ $it") }
                    },
                    kind = CouiTextKind.GroupFooter,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Start,
                )
            }
        }
        if (detail.tags.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                detail.tags.forEach { tag ->
                    CouiNovelTagChip(text = tag, onClick = { onOpenSearch(tag, SearchScope.TAG) })
                }
            }
        }
    }
}

/** 阅读动作组：继续阅读一行 + 收藏/喜欢/热度三颗胶囊。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CouiNovelActionsGroup(
    detail: NovelDetail,
    savedChapter: String?,
    busy: Boolean,
    onStartReading: () -> Unit,
    onToggleLike: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val scheme = CouiKitTheme.colorScheme
    val resumeTitle = detail.chapters.firstOrNull { it.ncid == savedChapter }?.title
        ?: detail.chapters.firstOrNull()?.title
    Group {
        Item(
            title = {
                Text(
                    text = if (savedChapter == null) "开始阅读" else "继续阅读",
                    kind = CouiTextKind.ItemTitle,
                    color = if (detail.chapters.isEmpty()) scheme.onSurfaceVariant else scheme.accent,
                )
            },
            summary = resumeTitle?.let { title ->
                {
                    Text(
                        text = title,
                        kind = CouiTextKind.GroupFooter,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Start,
                    )
                }
            },
            trailing = {
                Icon(
                    imageVector = CouiIcons.Forward,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = scheme.onSurfaceVariant,
                )
            },
            enabled = detail.chapters.isNotEmpty(),
            onClick = onStartReading,
        )
        Divider()
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CouiNovelCapsuleButton(
                text = if (detail.favorite) "已收藏" else "收藏",
                accent = detail.favorite,
                enabled = !busy,
                onClick = onToggleFavorite,
            )
            CouiNovelCapsuleButton(
                text = if (detail.liked) "已喜欢" else "喜欢",
                accent = detail.liked,
                enabled = !busy && !detail.liked,
                onClick = onToggleLike,
            )
            val likes = detail.likes?.takeIf { it.isNotBlank() }
            if (likes != null) {
                CouiNovelCapsuleButton(text = "♥ $likes", enabled = false, onClick = {})
            }
        }
    }
}

/** 简介组：超过阈值给展开入口，收起时四行截断。 */
@Composable
private fun CouiNovelDescriptionGroup(text: String, expanded: Boolean, onToggle: () -> Unit) {
    val scheme = CouiKitTheme.colorScheme
    Group(
        title = "简介",
        headerActions = {
            if (text.length > NOVEL_DESCRIPTION_COLLAPSE_THRESHOLD) {
                Text(
                    text = if (expanded) "收起" else "显示全部",
                    kind = CouiTextKind.GroupFooter,
                    color = scheme.accent,
                    modifier = Modifier
                        .clip(CouiCapsuleShape)
                        .couiPressable(
                            shape = CouiCapsuleShape, spotlight = SpotlightPreset.ChipTab,
                            minTouchTarget = CouiMinTouchTarget,
                            onClick = onToggle,
                        )
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            CouiBodyText(
                text = text.ifBlank { "暂无简介" },
                color = scheme.onSurface,
                maxLines = if (expanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start,
            )
        }
    }
}

/** 作品信息组：连载状态、章节数、上架时间、热度与车号。 */
@Composable
private fun CouiNovelInfoGroup(detail: NovelDetail, dateFormat: DateTimeFormatter) {
    val added = remember(detail.addtime) { formatEpochSeconds(detail.addtime, dateFormat) }
    val heat = remember(detail.totalViews, detail.likes) {
        buildString {
            detail.totalViews?.let { append("阅 $it") }
            detail.likes?.let {
                if (isNotEmpty()) append(" · ")
                append("♥ $it")
            }
        }
    }
    Group(title = "作品信息") {
        CouiNovelInfoRow(title = "连载状态", value = if (detail.isEnd) "已完结" else detail.serialStatus.ifBlank { "连载中" })
        Divider()
        CouiNovelInfoRow(title = "章节数", value = "${detail.chapters.size} 章")
        Divider()
        CouiNovelInfoRow(title = "上架时间", value = added.ifBlank { "未知" })
        Divider()
        CouiNovelInfoRow(title = "热度", value = heat.ifBlank { "—" })
        Divider()
        CouiNovelInfoRow(title = "车号", value = "NVL${detail.id}")
    }
}

@Composable
private fun CouiNovelInfoRow(title: String, value: String) {
    val scheme = CouiKitTheme.colorScheme
    Item(
        title = { Text(text = title, kind = CouiTextKind.ItemTitle, textAlign = TextAlign.Start) },
        trailing = {
            Text(
                text = value,
                kind = CouiTextKind.ItemValue,
                color = scheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
            )
        },
    )
}

/** 目录排序切换：只改本地排列顺序，不重新请求详情。 */
@Composable
private fun CouiNovelSortAction(ascending: Boolean, onToggle: () -> Unit) {
    Text(
        text = if (ascending) "正序" else "倒序",
        kind = CouiTextKind.GroupFooter,
        color = CouiKitTheme.colorScheme.accent,
        modifier = Modifier
            .clip(CouiCapsuleShape)
            .couiPressable(shape = CouiCapsuleShape, spotlight = SpotlightPreset.ChipTab, minTouchTarget = CouiMinTouchTarget, onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** 目录行卡：组头复用 CouiGroupRow 的整块卡片，章节行共享父卡片并按章节缩进。 */
@Composable
private fun CouiNovelChapterRowCard(
    row: CouiNovelChapterRow,
    isFirst: Boolean,
    isLast: Boolean,
    onToggleGroup: (String) -> Unit,
    onOpenChapter: (NovelChapterMeta) -> Unit,
) {
    val scheme = CouiKitTheme.colorScheme
    when (row) {
        is CouiNovelChapterRow.GroupHeader -> CouiGroupRow(isFirst = isFirst, isLast = isLast) {
            Item(
                title = {
                    Text(
                        text = row.title,
                        kind = CouiTextKind.ItemTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Start,
                    )
                },
                summary = if (row.showToggle) {
                    {
                        Text(
                            text = row.summary,
                            kind = CouiTextKind.GroupFooter,
                            color = scheme.onSurfaceVariant,
                            textAlign = TextAlign.Start,
                        )
                    }
                } else {
                    null
                },
                trailing = {
                    if (row.showToggle) {
                        Text(
                            text = if (row.expanded) "收起" else "展开",
                            kind = CouiTextKind.ItemValue,
                            color = scheme.accent,
                        )
                    }
                },
                onClick = if (row.showToggle) {
                    { onToggleGroup(row.groupId) }
                } else {
                    null
                },
            )
        }

        is CouiNovelChapterRow.ChapterLine -> CouiGroupRow(
            isFirst = isFirst,
            isLast = isLast,
            showDivider = false,
        ) {
            Divider(startIndent = 32.dp, endIndent = 16.dp)
            Item(
                contentPadding = PaddingValues(start = 32.dp, top = 10.dp, end = 16.dp, bottom = 10.dp),
                title = {
                    CouiBodyText(
                        text = row.chapter.title.ifBlank { "未命名章节" },
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Start,
                    )
                },
                summary = row.marker?.let { marker ->
                    {
                        Text(
                            text = marker,
                            kind = CouiTextKind.GroupFooter,
                            color = if (row.highlighted) scheme.accent else scheme.onSurfaceVariant,
                            textAlign = TextAlign.Start,
                        )
                    }
                },
                trailing = {
                    Icon(
                        imageVector = CouiIcons.Forward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = scheme.onSurfaceVariant,
                    )
                },
                onClick = { onOpenChapter(row.chapter) },
            )
        }
    }
}

/** 相关作品横滑区：卡宽固定，标题两行，副标题给作者与更新时间。 */
@Composable
private fun CouiNovelRelatedGroup(
    related: List<NovelMeta>,
    dateFormat: DateTimeFormatter,
    onOpenNovel: (String) -> Unit,
) {
    Group {
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items = related, key = { it.id }) { item ->
                CouiNovelRelatedCard(
                    item = item,
                    dateFormat = dateFormat,
                    onClick = { onOpenNovel(item.id) },
                )
            }
        }
    }
}

@Composable
private fun CouiNovelRelatedCard(item: NovelMeta, dateFormat: DateTimeFormatter, onClick: () -> Unit) {
    val scheme = CouiKitTheme.colorScheme
    val shape = RoundedCornerShape(LocalSkin.current.shapes.cardCorner)
    val updated = remember(item.updateAt) { formatEpochSeconds(item.updateAt, dateFormat) }
    val subtitle = remember(item.author, updated) {
        listOfNotNull(
            item.author.ifBlank { "佚名" },
            updated.takeIf { it.isNotBlank() }?.let { "更新 $it" },
        ).joinToString(" · ")
    }
    Column(
        modifier = Modifier
            .width(NOVEL_RELATED_CARD_WIDTH)
            .clip(shape)
            // 卡片以封面图为主体：光画在内容之下会被整张封面吃掉
            .couiPressable(shape = shape, spotlightAboveContent = true, onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(NOVEL_RELATED_COVER_HEIGHT)
                .clip(shape)
                .background(scheme.container),
        ) {
            AlbumCoverImage(
                url = item.coverUrl,
                contentDescription = item.name,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = item.name,
            kind = CouiTextKind.ItemSummary,
            color = scheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Start,
        )
        Text(
            text = subtitle,
            kind = CouiTextKind.GroupFooter,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Start,
        )
    }
}

/** 评论输入块：未登录只给一行登录入口，登录后才铺输入框与计数。 */
@Composable
private fun CouiNovelCommentComposer(
    draft: String,
    sending: Boolean,
    loggedIn: Boolean,
    onDraft: (String) -> Unit,
    onSend: () -> Unit,
    onOpenLogin: () -> Unit,
) {
    val scheme = CouiKitTheme.colorScheme
    Group {
        if (!loggedIn) {
            Item(
                title = { Text(text = "登录后发表评论", kind = CouiTextKind.ItemTitle) },
                summary = {
                    Text(
                        text = "登录账号即可参与讨论",
                        kind = CouiTextKind.GroupFooter,
                        color = scheme.onSurfaceVariant,
                        textAlign = TextAlign.Start,
                    )
                },
                trailing = {
                    Icon(
                        imageVector = CouiIcons.Forward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = scheme.onSurfaceVariant,
                    )
                },
                onClick = onOpenLogin,
            )
        } else {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                BasicTextField(
                    value = draft,
                    onValueChange = onDraft,
                    textStyle = rememberCouiBodyTextStyle().copy(color = scheme.onSurface),
                    cursorBrush = SolidColor(scheme.accent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 84.dp)
                        .clip(RoundedCornerShape(LocalSkin.current.shapes.controlCorner))
                        .background(scheme.container)
                        .padding(12.dp),
                    decorationBox = { innerTextField ->
                        Box {
                            if (draft.isEmpty()) {
                                CouiBodyText(
                                    text = "写下你的评论（最多 $NOVEL_COMMENT_MAX_LENGTH 字）",
                                    color = scheme.onSurfaceVariant,
                                    textAlign = TextAlign.Start,
                                )
                            }
                            innerTextField()
                        }
                    },
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${draft.length}/$NOVEL_COMMENT_MAX_LENGTH",
                        kind = CouiTextKind.GroupFooter,
                        color = scheme.onSurfaceVariant,
                        textAlign = TextAlign.Start,
                    )
                    CouiNovelCapsuleButton(
                        text = if (sending) "发表中…" else "发表",
                        accent = true,
                        enabled = !sending && draft.isNotBlank(),
                        onClick = onSend,
                    )
                }
            }
        }
    }
}

/** 单条评论：行卡由 [CouiGroupRow] 提供，内容与其余页面共用 [CouiCommentRow]，避免第四份评论行实现。 */
@Composable
private fun CouiNovelCommentItemCard(
    row: CommentRow.Comment,
    isFirst: Boolean,
    onReply: (CommentItem) -> Unit,
) {
    CouiGroupRow(isFirst = isFirst, isLast = false, showDivider = false) {
        CouiCommentRow(
            item = row.item,
            depth = row.depth,
            authorColor = CouiKitTheme.colorScheme.onSurface,
            avatarSize = 28.dp,
            bodyMaxLines = NOVEL_COMMENT_BODY_MAX_LINES,
            onReply = onReply,
        )
    }
}

/**
 * 回复输入弹窗：标题带被回复作者，确认键把正文交给 sendReply 走 replyNovelComment。
 *
 * 落位取 Bottom 而不是居中：面板与输入框同侧，键盘弹起时视线不用在屏幕上下跳。
 * 草稿跟着 visible 换代重置——visible 转 false 后库并不卸载组合，remember 不换代
 * 会把上一次的草稿带进下一条回复。
 */
@Composable
private fun CouiNovelReplyDialog(
    visible: Boolean,
    targetAuthor: String?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSend: (String) -> Unit,
    targetKey: String? = null,
) {
    val scheme = CouiKitTheme.colorScheme
    val author = rememberRetainedTarget(targetAuthor)?.takeIf { it.isNotBlank() }
    var replyText by rememberSavedDialogDraft(isOpen = visible, targetKey = targetKey ?: author)
    Dialog(
        onDismissRequest = onDismiss,
        visible = visible,
        material = couiDialogMaterial(),
        confirmAction = CouiDialogAction(
            text = if (busy) "发送中…" else "发送",
            onClick = { onSend(replyText) },
            enabled = !busy && replyText.isNotBlank(),
            style = CouiDialogActionStyle.Primary,
            colors = CouiDialogDefaults.primaryActionColors(scheme.accent),
        ),
        dismissAction = CouiDialogAction(text = "取消", onClick = onDismiss),
        dark = CouiKitTheme.isDark,
        title = if (author != null) "回复 $author" else "回复",
        placement = CouiDialogPlacement.Bottom,
    ) {
        BasicTextField(
            value = replyText,
            // 上限与评论区一致：回复走的也是小说评论接口，超长会被服务端整条拒掉
            onValueChange = { replyText = it.take(NOVEL_COMMENT_MAX_LENGTH) },
            textStyle = rememberCouiBodyTextStyle().copy(color = scheme.onSurface),
            cursorBrush = SolidColor(scheme.accent),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 96.dp)
                .clip(RoundedCornerShape(LocalSkin.current.shapes.controlCorner))
                .background(scheme.container)
                .padding(12.dp),
            decorationBox = { innerTextField ->
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (replyText.isEmpty()) {
                        CouiBodyText(
                            text = "写下你的回复（最多 $NOVEL_COMMENT_MAX_LENGTH 字）",
                            color = scheme.onSurfaceVariant,
                            textAlign = TextAlign.Start,
                        )
                    }
                    innerTextField()
                }
            },
        )
        Text(
            text = "${replyText.length}/$NOVEL_COMMENT_MAX_LENGTH",
            kind = CouiTextKind.GroupFooter,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Start,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** 回复折叠开关：收起时显示隐藏条数，展开后换成收起。 */
@Composable
private fun CouiNovelRepliesToggleRow(row: CommentRow.RepliesToggle, onToggle: (String) -> Unit) {
    val scheme = CouiKitTheme.colorScheme
    CouiGroupRow(isFirst = false, isLast = false, showDivider = false) {
        Item(
            contentPadding = PaddingValues(start = 16.dp + (12 * row.depth).dp, top = 6.dp, end = 16.dp, bottom = 6.dp),
            title = {
                Text(
                    text = if (row.expanded) "收起回复" else "展开其余 ${row.hiddenCount} 条回复",
                    kind = CouiTextKind.GroupFooter,
                    color = scheme.accent,
                    textAlign = TextAlign.Start,
                )
            },
            onClick = { onToggle(row.parentId) },
        )
    }
}

/** 胶囊按钮：与相册详情的动作胶囊同一套取值，保证同屏内按钮尺寸一致。 */
@Composable
private fun CouiNovelCapsuleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Boolean = false,
) {
    val scheme = CouiKitTheme.colorScheme
    val background = when {
        !enabled -> scheme.onSurface.copy(alpha = 0.06f)
        accent -> scheme.accent
        else -> scheme.onSurface.copy(alpha = 0.08f)
    }
    val foreground = when {
        !enabled -> scheme.onSurface.copy(alpha = 0.38f)
        accent -> scheme.onAccent
        else -> scheme.onSurface
    }
    Box(
        modifier = modifier
            .couiPressable(
                shape = CouiCapsuleShape,
                backgroundColor = background,
                spotlight = SpotlightPreset.ChipTab,
                enabled = enabled,
                minTouchTarget = CouiMinTouchTarget,
                onClick = onClick,
            )
            .heightIn(min = 32.dp)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, kind = CouiTextKind.ItemValue, color = foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CouiNovelTagChip(text: String, onClick: () -> Unit) {
    val scheme = CouiKitTheme.colorScheme
    Text(
        text = text,
        kind = CouiTextKind.GroupFooter,
        color = scheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Start,
        modifier = Modifier
            .couiPressable(
                shape = CouiCapsuleShape,
                backgroundColor = scheme.onSurface.copy(alpha = 0.06f),
                spotlight = SpotlightPreset.ChipTab,
                minTouchTarget = CouiMinTouchTarget,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** 把内容块塞进懒列表作用域；宽屏两栏各自 include 一次，避免重复定义槽位。 */
private fun LazyListScope.couiInclude(content: LazyListScope.() -> Unit) = content(this)
