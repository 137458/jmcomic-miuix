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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.ui.components.BackButton
import com.jm3.app.miuix.ui.components.COMMENT_REPLY_MAX_LENGTH
import com.jm3.app.miuix.ui.components.CommentReplySheet
import com.jm3.app.miuix.ui.components.EmptyState
import com.jm3.app.miuix.ui.components.ErrorScreen
import com.jm3.app.miuix.ui.components.LoadMoreFooter
import com.jm3.app.miuix.ui.components.LoadMoreOnScroll
import com.jm3.app.miuix.ui.components.LoadingScreen
import com.jm3.app.miuix.ui.components.PagerBar
import com.jm3.app.miuix.ui.components.TrackedLazyColumn
import com.jm3.app.miuix.ui.components.pressScale
import com.jm3.app.miuix.ui.components.rememberSelectionHaptic
import com.jm3.app.miuix.ui.forum.FORUM_LOAD_FAILED
import com.jm3.app.miuix.ui.forum.FORUM_RULES_HINT
import com.jm3.app.miuix.ui.forum.FORUM_RULES_TEXT
import com.jm3.app.miuix.ui.forum.FORUM_STALE_PREFIX
import com.jm3.app.miuix.ui.forum.forumEmptyHint
import com.jm3.app.miuix.ui.forum.forumEmptyTitle
import com.jm3.app.miuix.ui.forum.forumSlice
import com.jm3.app.miuix.ui.forum.rememberForumScaffoldState
import com.jm3.app.miuix.ui.glass.GlassPullToRefresh
import com.jm3.app.miuix.ui.glass.GlassScaffold
import com.jm3.app.miuix.ui.glass.GlassTopAppBar
import com.jm3.app.miuix.ui.list.ListLoadState
import com.jm3.app.miuix.ui.list.SessionListLoader
import com.jm3.app.miuix.ui.route.AppRoutes
import com.jm3.app.miuix.ui.skin.LocalSkin
import com.jm3.core.model.CommentItem
import com.jm3.core.model.ForumScope
import com.jm3.core.paging.PagedFeed
import com.jm3.core.paging.pagerTargetPage
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

/** 点击分页模式是否显示翻页条：总页数大于 1、仍有下一页或当前不在第 1 页时显示。 */
internal fun shouldShowForumPager(feed: PagedFeed<*>): Boolean =
    (feed.pageCount ?: 0) > 1 || feed.hasMore || feed.page > 1

/**
 * 独立论坛页：全部/漫画评论/闲聊三档，支持发帖、回复与两种分页模式。
 *
 * 分页总数由接口 total 推算（原版 pageLimit = ceil(total / 10)），不再以单页条数猜是否有下一页。
 */
@Composable
internal fun ForumScreen(
    deps: ScreenDeps,
    onBack: () -> Unit,
    nav: AppRoutes,
    snackbarHostState: SnackbarHostState,
) {
    val user by deps.account.user.collectAsState()
    val pageMode = deps.paginationMode
    // 档位、回复/发帖面板与规则卡的状态编排在共享骨架里：两套皮肤原先各写一份
    val forum = rememberForumScaffoldState(
        deps = deps,
        nav = nav,
        loggedIn = user != null,
        notify = { message -> snackbarHostState.showSnackbar(message) },
    )
    val selectionHaptic = rememberSelectionHaptic()
    val clampedScopeIndex = forum.clampedScopeIndex
    // 失败重试要回到出错的那一页：loader 不携带目标页，只能在这里按档位登记
    val retryPages = remember { mutableStateMapOf<Int, Int>() }
    // 翻页成功后的回顶：loader 没有成功回调，只能比对 feed 页码后补一次滚动
    val pendingScrollPages = remember { mutableStateMapOf<Int, Int>() }
    val refreshState = rememberPullToRefreshState(refreshThreshold = 0.18f)
    // 每个档位各自保存滚动偏移，左右滑切页后回到原档位不再丢失位置
    val listStates = remember { List(ForumScope.entries.size) { LazyListState() } }
    val pagerState = rememberSyncedTabPagerState(
        selectedTab = clampedScopeIndex,
        pageCount = ForumScope.entries.size,
        onTabSelected = { next ->
            selectionHaptic()
            forum.selectScope(next)
        },
        useMiuixSpring = true,
    )
    val scope = forum.scope
    var lastPageMode by remember { mutableStateOf(pageMode) }

    /**
     * 三档各自的取数编排交给 [SessionListLoader]：分页守卫、代次令牌与失败降级都在 loader 内部。
     *
     * 页面只剩两件事：这一档取哪一页（fetchPage），失败怎么提示（notice）。
     */
    val loaders = remember(pageMode, user?.uid, snackbarHostState) {
        List(ForumScope.entries.size) { idx ->
            val forumScope = ForumScope.entries[idx]
            SessionListLoader<CommentItem>(
                scope = scope,
                idOf = { it.id },
                failureMessage = FORUM_LOAD_FAILED,
                stalePrefix = FORUM_STALE_PREFIX,
                fetchPage = { page ->
                    // 失败的页要能原地重试：loader 不携带目标页，取数前先登记
                    retryPages[idx] = page
                    val outcome = deps.authCall.call { deps.gateway.forum(forumScope, page) }
                    when (outcome) {
                        // 评论页没有页码字段：页码与总页数由请求页和 total 补上，点击分页条才有得显示
                        is SessionOutcome.Ok -> SessionOutcome.Ok(forumSlice(outcome.value, page))

                        SessionOutcome.Expired -> SessionOutcome.Expired

                        is SessionOutcome.Failed -> outcome
                    }
                },
                notice = { message -> scope.launch { snackbarHostState.showSnackbar(message) } },
            )
        }
    }

    /**
     * 点击分页：页码解析、越界守卫与加载态都在 loader 内，这里只登记回顶目标。
     *
     * 翻页不再有页内「请求在途」标志：loader 用代次令牌丢弃晚到的旧响应。
     */
    fun jumpToPage(target: Int, targetTab: Int) {
        val loader = loaders.getOrNull(targetTab) ?: return
        val currentFeed = loader.feed
        val resolved = pagerTargetPage(currentFeed.page, target, currentFeed.pageCount)
        if (resolved == currentFeed.page) return
        if (resolved > currentFeed.page && currentFeed.pageCount == null && !currentFeed.hasMore) return
        pendingScrollPages[targetTab] = resolved
        loader.goToPage(resolved)
    }

    // 切换档位或分页模式时按新语义重载第一页：分页模式进 loader 的 remember key，换模式即重建
    LaunchedEffect(loaders, pageMode, clampedScopeIndex) {
        if (lastPageMode != pageMode) {
            lastPageMode = pageMode
            retryPages.clear()
            pendingScrollPages.clear()
        }
        val loader = loaders.getOrNull(clampedScopeIndex) ?: return@LaunchedEffect
        if (loader.state is ListLoadState.Loading) {
            // 首帧这里列表还没进组合（Loading 态不是列表），scrollToItem 会挂起等待布局而不返回，
            // 取数协程因此永远不执行；requestScrollToItem 只登记待处理滚动，不阻塞取数。
            listStates[clampedScopeIndex].requestScrollToItem(0)
            loader.loadFirstPage()
        }
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
                            onClick = forum::openCompose,
                            emphasized = true,
                        )
                    },
                    // 透明底色：玻璃底色与模糊由 GlassScaffold 统一绘制
                    color = Color.Transparent,
                )
                // 档位筛选固定于列表之外：与顶栏同处玻璃层
                GlassChipRow(
                    tabs = ForumScope.entries.map { it.label },
                    selectedTabIndex = clampedScopeIndex,
                    onTabSelected = {
                        selectionHaptic()
                        forum.selectScope(it)
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                )
            }
        },
        snackbarHost = { GlassSnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { pageIdx ->
            val pageScope = ForumScope.entries[pageIdx]
            val loader = loaders.getOrNull(pageIdx) ?: return@HorizontalPager
            val snapshot by loader.snapshot.collectAsState()
            val pageFeed = snapshot.feed
            val pageListState = listStates[pageIdx]
            val pageTotalPages = pageFeed.pageCount
            when (val state = snapshot.state) {
                ListLoadState.Loading -> LoadingScreen(modifier = Modifier.padding(innerPadding))
                is ListLoadState.Failed -> if (state.sessionExpired) {
                    LoginExpiredScreen(onOpenLogin = nav::login, modifier = Modifier.padding(innerPadding))
                } else {
                    ErrorScreen(
                        message = state.message,
                        onRetry = { loader.goToPage(retryPages[pageIdx] ?: 1) },
                        modifier = Modifier.padding(innerPadding),
                    )
                }
                // 加载成功就渲染列表：即使没有内容也保留列表本体，
                // 闲聊档的规则卡与下拉刷新在空态下才不会跟着一起消失
                ListLoadState.Loaded -> {
                    // 翻页成功后回顶：loader 没有成功回调，用登记的待回顶页与落地页码比对
                    LaunchedEffect(pageFeed.page) {
                        if (pendingScrollPages[pageIdx] == pageFeed.page) {
                            pendingScrollPages[pageIdx] = 0
                            pageListState.scrollToItem(0)
                        }
                    }
                    if (pageMode != PaginationMode.CLICK) {
                        LoadMoreOnScroll(
                            listState = pageListState,
                            feed = pageFeed,
                            onLoadMore = loader::loadMore,
                        )
                    }
                    // 列表内容穿到玻璃顶栏（顶栏 + 档位条）下方，顶栏才能采样到真实背景
                    GlassPullToRefresh(
                        isRefreshing = snapshot.refreshing,
                        onRefresh = loader::refresh,
                        pullToRefreshState = refreshState,
                        modifier = Modifier.fillMaxSize(),
                        // 刷新指示器下移到玻璃顶栏之下，避免被顶栏遮住
                        contentPadding = innerPadding,
                    ) {
                        TrackedLazyColumn(
                            state = pageListState,
                            contentPadding = innerPadding,
                            modifier = Modifier.fillMaxSize().readableWidth(),
                        ) {
                            if (pageScope == ForumScope.CHAT) {
                                item(key = "rules") {
                                    ForumRulesCard(
                                        expanded = forum.rulesExpanded,
                                        onToggle = forum::toggleRules,
                                    )
                                }
                            }
                            val topicTotal = pageFeed.total ?: 0
                            if (pageFeed.items.isEmpty()) {
                                item(key = "empty") {
                                    ForumEmptyState(forumScope = pageScope, onCompose = forum::openCompose)
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
                                items(pageFeed.items, key = { it.id }) { item ->
                                    ForumTopicCard(
                                        item = item,
                                        onReply = forum::startReply,
                                        onOpenAlbum = nav::album,
                                    )
                                }
                                item(key = "footer") {
                                    if (pageMode == PaginationMode.CLICK) {
                                        // 只有一页且无更多页时翻页条没有信息量，不占位
                                        if (shouldShowForumPager(pageFeed)) {
                                            PagerBar(
                                                page = pageFeed.page,
                                                totalPages = pageTotalPages,
                                                hasMore = pageFeed.hasMore,
                                                onPageChange = { target -> jumpToPage(target, pageIdx) },
                                            )
                                        }
                                    } else {
                                        LoadMoreFooter(
                                            feed = pageFeed,
                                            onLoadMore = loader::loadMore,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 发帖面板与回复面板同级挂载，避免底部抽屉叠加导致高度抖动
        ForumComposeSheet(
            show = forum.composing,
            busy = forum.composeBusy,
            onDismiss = forum::dismissCompose,
            onSend = { text ->
                forum.postTopic(text) {
                    // 新帖落在第一页首位，回到顶部才能看到刚发的内容
                    val activeTab = forum.clampedScopeIndex
                    scope.launch { listStates[activeTab].scrollToItem(0) }
                    loaders[activeTab].refresh()
                }
            },
        )
        CommentReplySheet(
            show = forum.replyTarget != null,
            targetAuthor = forum.replyTarget?.author,
            busy = forum.replyBusy,
            onDismiss = forum::dismissReply,
            onSend = { text ->
                forum.sendReply(text) {
                    // 回复落在当前页：停在原页重取，不把用户弹回第一页
                    val activeLoader = loaders[forum.clampedScopeIndex]
                    val activePage = activeLoader.feed.page
                    if (activePage <= 1) activeLoader.refresh() else activeLoader.goToPage(activePage)
                }
            },
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
    val skin = LocalSkin.current
    val heroHost = LocalAlbumHeroHost.current
    val albumId = item.albumId
        ?: item.albumName?.filter { it.isDigit() }?.takeIf { it.isNotBlank() }
    val albumName = item.albumName?.takeIf { albumId != null }
    Card(
        cornerRadius = skin.shapes.cardCorner,
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
                        .clickable {
                            heroHost?.startForward(albumId, item.albumCoverUrl, HERO_THUMB_CORNER_DP, albumName)
                            onOpenAlbum(albumId)
                        }
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
    val skin = LocalSkin.current
    Card(
        cornerRadius = skin.shapes.cardCorner,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioLowBouncy)),
    ) {
        BasicComponent(
            title = "社区规则",
            summary = if (expanded) FORUM_RULES_TEXT else FORUM_RULES_HINT,
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
            title = forumEmptyTitle(forumScope),
            hint = forumEmptyHint(forumScope),
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
    val skin = LocalSkin.current
    var text by rememberSavedDialogDraft(isOpen = show, targetKey = null)
    OverlayBottomSheet(
        show = show,
        title = "发布新帖",
        cornerRadius = skin.shapes.sheetCorner,
        onDismissRequest = onDismiss,
    ) {
        Card(cornerRadius = skin.shapes.cardCorner, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
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
