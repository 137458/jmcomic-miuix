package com.jm3.app.miuix.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.ui.components.LoadMoreOnScroll
import com.jm3.app.miuix.ui.components.MinTouchTarget
import com.jm3.app.miuix.ui.components.ScreenFailureOrLogin
import com.jm3.app.miuix.ui.components.SectionHorizontalPadding
import com.jm3.app.miuix.ui.components.SectionTextStartPadding
import com.jm3.app.miuix.ui.components.TrackedLazyColumn
import com.jm3.app.miuix.ui.components.largeScreenTitleStyle
import com.jm3.app.miuix.ui.components.overlayDialogMaxWidth
import com.jm3.app.miuix.ui.components.rememberSelectionHaptic
import com.jm3.app.miuix.ui.components.resultItems
import com.jm3.app.miuix.ui.glass.CollapsibleSearchAction
import com.jm3.app.miuix.ui.glass.CollapsibleSearchBar
import com.jm3.app.miuix.ui.glass.GlassFabItem
import com.jm3.app.miuix.ui.glass.GlassFloatingActionBar
import com.jm3.app.miuix.ui.glass.GlassPullToRefresh
import com.jm3.app.miuix.ui.glass.GlassScaffold
import com.jm3.app.miuix.ui.glass.GlassSearchInputField
import com.jm3.app.miuix.ui.glass.GlassTopAppBar
import com.jm3.app.miuix.ui.glass.LocalFloatingBarContentPadding
import com.jm3.app.miuix.ui.glass.LocalGlassBottomBarTokens
import com.jm3.app.miuix.ui.glass.collapsibleSearchScroll
import com.jm3.app.miuix.ui.glass.rememberCollapsibleSearchState
import com.jm3.app.miuix.ui.glass.withSpacing
import com.jm3.app.miuix.ui.home.HOT_TAG_COLLAPSED
import com.jm3.app.miuix.ui.home.HomePageJump
import com.jm3.app.miuix.ui.home.HomePaginationReload
import com.jm3.app.miuix.ui.home.HomeScaffoldParams
import com.jm3.app.miuix.ui.home.HomeScrollPolicy
import com.jm3.app.miuix.ui.home.HomeSearchLoad
import com.jm3.app.miuix.ui.home.HomeSideLoad
import com.jm3.app.miuix.ui.home.rememberHomeScaffoldState
import com.jm3.app.miuix.ui.route.AppRoutes
import com.jm3.app.miuix.ui.search.searchHeadline
import com.jm3.app.miuix.ui.skin.LocalSkin
import com.jm3.core.model.BrowseOrder
import com.jm3.core.model.SearchResult
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TooltipBox
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Close
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Help
import top.yukonga.miuix.kmp.icon.extended.Promotions
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Weeks
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

// 列表三态与会话缓存统一在 SessionCache.kt 定义；loader 快照到页面三态的翻译在 list/ListStateAdapters.kt

/** 首页：固定搜索栏 + 发现流（连载更新/热门标签/随便看看/最新上架）或搜索结果。 */
@Composable
internal fun HomeScreen(
    deps: ScreenDeps,
    nav: AppRoutes,
) {
    val skin = LocalSkin.current
    // 下拉阻尼阈值调低：首页内容多，轻拉即可触发刷新
    val refreshState = rememberPullToRefreshState(refreshThreshold = 0.18f)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    // 搜索栏折叠与顶栏折叠各自独立：这里只跟随列表已消费的滚动位移，不改动顶栏行为。
    val searchCollapse = rememberCollapsibleSearchState(listState = listState)
    // 清焦点与落键盘是 Compose 副作用，只在这里实现；状态机只负责决定「该收起了」
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val snackbarHostState = remember { SnackbarHostState() }

    // 状态编排（两个 loader、搜索条件、侧栏与历史、动作口径）全部来自共享骨架；
    // 这里只保留视觉结构、滚动容器与返回键。
    val state = rememberHomeScaffoldState(
        deps = deps,
        snackbarHostState = snackbarHostState,
        onDismissInput = {
            // 只藏键盘不清焦点的话，任何一次状态刷新都会让输入框把键盘再拉回来
            focusManager.clearFocus()
            keyboard?.hide()
        },
        onOpenAlbum = { albumId -> nav.album(albumId) },
        params = HomeScaffoldParams(
            searchLoad = HomeSearchLoad.Sticky,
            jump = HomePageJump.Clamped,
            paginationReload = HomePaginationReload.BothLoaders,
            sideLoad = HomeSideLoad.Sequential,
            loadRankTrack = false,
            scrollPolicy = HomeScrollPolicy.ExactPage,
            searchFailureMessage = "网络错误",
            feedFailureMessage = "加载失败",
        ),
    )

    // 处于搜索态时拦截系统返回键，退回主页发现流而非直接退出应用
    BackHandler(enabled = state.searching) {
        state.clearSearch()
    }

    // 换页落地后回顶：目标页在请求前已知，但要等内容真正落地再滚，否则会滚到空列表上
    LaunchedEffect(state.activeSnapshot.feed.page, state.activeSnapshot.state) {
        if (state.scrollRequestedAfterLanding()) listState.scrollToItem(0)
    }

    // 接近列表尾部时自动加载下一页；点击分页模式下改由页码控件驱动。
    val activeFeed = state.activeFeed
    if (!state.clickPaging && activeFeed != null) {
        LoadMoreOnScroll(listState = listState, feed = activeFeed, onLoadMore = state::loadMore)
    }

    // 与原版一致：列表滚动后出现悬浮操作（随机一部/回到顶部）
    val showActions by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 100 }
    }

    // 悬浮操作栏要让开底栏胶囊。底边距为「8dp + 导航条 inset」，而页面内读到的 inset
    // 已被主框架消费为 0，算式无法直接换算；按手势导航真机实测标定：底栏高度 + 21dp
    // 即得 16dp 视觉间距。底栏档位切换后高度变化会自动跟随。
    val bottomBarTokens = LocalGlassBottomBarTokens.current
    val floatingBarContentPadding = LocalFloatingBarContentPadding.current
    val floatingActionsBottom = if (floatingBarContentPadding == 0.dp) {
        // 宽屏改用侧栏、没有悬浮底栏，直接贴右下角
        16.dp
    } else {
        bottomBarTokens.height + 21.dp
    }

    val selectionHaptic = rememberSelectionHaptic()

    GlassScaffold(
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                GlassTopAppBar(
                    title = "JM3 Miuix",
                    // 透明底色：玻璃底色与模糊由 GlassScaffold 统一绘制
                    color = Color.Transparent,
                    actions = {
                        CollapsibleSearchAction(state = searchCollapse)
                        TooltipBox(text = "每周必看") {
                            IconButton(onClick = nav::week) {
                                Icon(imageVector = MiuixIcons.Weeks, contentDescription = "每周必看")
                            }
                        }
                    },
                )
                // 搜索栏固定于列表之外：与顶栏同处玻璃层；向下滚动内容时收起，向上滚动回展。
                CollapsibleSearchBar(state = searchCollapse) {
                    SearchBar(
                        insideMargin = DpSize(16.dp, 0.dp),
                        inputField = {
                            GlassSearchInputField(
                                query = state.query,
                                // 清空输入即退出搜索态，回到发现流
                                onQueryChange = { state.editedInput(it) },
                                onSearch = { state.submitSearch() },
                                expanded = state.searchExpanded,
                                onExpandedChange = { state.searchExpanded = it },
                                interactionSource = searchCollapse.interactionSource,
                                label = "搜索 / 车号",
                                // 自绘尾部动作：保留清空入口，并常驻「?」语法帮助。
                                trailingIcon = {
                                    Row(
                                        modifier = Modifier.padding(end = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        if (state.query.isNotEmpty()) {
                                            IconButton(
                                                onClick = { state.editedInput("") },
                                                minWidth = 44.dp,
                                                minHeight = 44.dp,
                                            ) {
                                                Icon(
                                                    imageVector = MiuixIcons.Basic.Close,
                                                    contentDescription = "清空输入",
                                                    tint = MiuixTheme.colorScheme.onSurfaceContainerHigh,
                                                    modifier = Modifier.size(16.dp),
                                                )
                                            }
                                        }
                                        IconButton(
                                            onClick = { state.showSearchHelp = true },
                                            minWidth = 44.dp,
                                            minHeight = 44.dp,
                                        ) {
                                            Icon(
                                                imageVector = MiuixIcons.Help,
                                                contentDescription = "搜索语法帮助",
                                                tint = MiuixTheme.colorScheme.onSurfaceContainerHigh,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                        onExpandedChange = { state.searchExpanded = it },
                        modifier = Modifier.fillMaxWidth(),
                        expanded = state.searchExpanded,
                        outsideEndAction = {
                            IconButton(onClick = state::submitSearch) {
                                Icon(
                                    imageVector = MiuixIcons.Search,
                                    contentDescription = "搜索",
                                )
                            }
                        },
                    ) {}
                }
                // 搜索结果排序常驻玻璃顶栏：与内容同处玻璃采样区，列表滚动时背景随之实时更新
                if (state.searching) {
                    GlassChipRow(
                        tabs = BrowseOrder.entries.map { it.label },
                        selectedTabIndex = BrowseOrder.entries.indexOf(state.order),
                        onTabSelected = {
                            BrowseOrder.entries.getOrNull(it)?.let { selected ->
                                selectionHaptic()
                                state.runSearch(state.activeQuery, state.searchByTag, selected)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    )
                }
            }
        },
        floatingActionButton = {
            if (showActions) {
                GlassFloatingActionBar(
                    items = listOf(
                        GlassFabItem(
                            icon = MiuixIcons.Promotions,
                            contentDescription = "随机看一部",
                            onClick = state::openRandom,
                        ),
                        GlassFabItem(
                            // 库里没有现成的向上箭头，用标准返回箭头顺时针转 90° 得到有箭杆的「↑」
                            icon = MiuixIcons.Back,
                            contentDescription = "回到顶部",
                            onClick = { scope.launch { listState.animateScrollToItem(0) } },
                            rotationDegrees = 90f,
                        ),
                    ),
                    // 右下角摆放，整体上移让开悬浮底栏；右边距与底栏胶囊的 24dp 对齐
                    modifier = Modifier.padding(end = 12.dp, bottom = floatingActionsBottom),
                )
            }
        },
        collapsibleSearch = searchCollapse,
        snackbarHost = { GlassSnackbarHost(state = snackbarHostState) },
    ) { innerPadding ->
        val listContentPadding = remember(innerPadding, floatingBarContentPadding, floatingActionsBottom) {
            val extraBottom = glassHomeExtraBottomDp(
                hasActions = true,
                floatingBarContentPaddingDp = floatingBarContentPadding.value,
                floatingActionsBottomDp = floatingActionsBottom.value,
            ).dp
            innerPadding.withSpacing(extraBottom = extraBottom)
        }
        // 列表内容滚到玻璃顶栏（顶栏 + 搜索栏）下方，顶栏才能采样到真实背景
        GlassPullToRefresh(
            isRefreshing = state.refreshing,
            onRefresh = state::refresh,
            modifier = Modifier.fillMaxSize(),
            pullToRefreshState = refreshState,
            // 刷新指示器下移到玻璃顶栏之下，避免被顶栏遮住
            contentPadding = innerPadding,
        ) {
            // 大屏行卡两列：列数由 WideLayout.feedColumns 判定，手机断点仍是单列
            val feedColumns = LocalWideLayout.current.feedColumns
            TrackedLazyColumn(
                state = listState,
                // 首项下移让出顶栏高度，底部额外让出悬浮操作胶囊高度
                contentPadding = listContentPadding,
                modifier = Modifier.fillMaxSize().readableWidth().collapsibleSearchScroll(searchCollapse),
            ) {
                if (state.searching) {
                    item(key = "search-header") {
                        SearchResultHeader(
                            query = state.activeQuery,
                            byTag = state.searchByTag,
                            total = (state.results as? ListState.Loaded)?.feed?.total ?: 0,
                        )
                    }
                    when (val s = state.results) {
                        null, is ListState.Loading -> if (!state.refreshing) {
                            item(key = "search-loading") { InlineLoading() }
                        }
                        is ListState.Failed -> item(key = "search-error") {
                            ScreenFailureOrLogin(
                                message = s.message,
                                expired = s.sessionExpired,
                                onRetry = { state.runSearch(state.activeQuery, state.searchByTag, state.order) },
                                onOpenLogin = nav::login,
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                            )
                        }
                        is ListState.Loaded -> resultItems(
                            feed = s.feed,
                            clickPaging = state.clickPaging,
                            onLoadMore = state::loadMore,
                            onPageChange = state::jumpToPage,
                            onOpenAlbum = { state.openAlbum(it) },
                            columns = feedColumns,
                        )
                    }
                } else {
                    // 附属区块（搜索记录/连载更新/热门标签/随便看看）的数据是异步到达的，
                    // 这里合并成单个 item：若各自作为独立 item 插到列表头部，LazyColumn 会按 key
                    // 锚定原首个可见项「最新上架」，首页打开后就不是真正的顶部了。
                    item(key = "discovery-header") {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // 仅当搜索框为空且未进入搜索态时展示搜索记录
                            if (state.query.isBlank() && state.searchHistory.isNotEmpty()) {
                                SearchHistoryCard(
                                    history = state.searchHistory,
                                    onPick = { word ->
                                        state.query = word
                                        state.runSearch(word, byTag = false, sort = state.order)
                                    },
                                    onRemove = state::removeHistory,
                                    onClear = state::clearHistory,
                                )
                            }
                            if (state.serialUpdates.isNotEmpty()) {
                                SmallTitle(text = "连载更新")
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = SectionHorizontalPadding),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    items(state.serialUpdates, key = { it.id }) { update ->
                                        SerialUpdateCard(update = update, onClick = { state.openAlbum(update.id) })
                                    }
                                }
                            }
                            if (state.hotTags.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    SmallTitle(text = "热门标签", modifier = Modifier.weight(1f))
                                    if (state.hotTags.size > HOT_TAG_COLLAPSED) {
                                        MinTouchTarget(
                                            onClick = { state.showAllTags = !state.showAllTags },
                                            modifier = Modifier.padding(end = 16.dp),
                                        ) {
                                            TextButton(
                                                text = if (state.showAllTags) "收起" else "更多",
                                                onClick = { state.showAllTags = !state.showAllTags },
                                                minWidth = 0.dp,
                                                minHeight = 36.dp,
                                                insideMargin = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                            )
                                        }
                                    }
                                }
                                // 卡内 FlowRow 自带 12dp：外缘保持 16dp，标签胶囊与标题同在 28dp
                                Card(cornerRadius = skin.shapes.cardCorner, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                                    HotTagFlow(
                                        tags = state.hotTags,
                                        expanded = state.showAllTags,
                                        onClick = { tag ->
                                            state.query = tag
                                            state.runSearch(tag, byTag = true, sort = state.order)
                                        },
                                    )
                                }
                            }
                            if (state.randomPicks.isNotEmpty()) {
                                SmallTitle(text = "随便看看")
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = SectionHorizontalPadding),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    items(state.randomPicks, key = { it.id }) { pick ->
                                        RandomPickCard(pick = pick, onClick = { state.openAlbum(pick.id) })
                                    }
                                }
                            }
                        }
                    }
                    item(key = "latest-title") { SmallTitle(text = "最新上架") }
                    when (val s = state.feed) {
                        is ListState.Loading -> if (!state.refreshing) {
                            item(key = "feed-loading") { InlineLoading() }
                        }
                        is ListState.Failed -> item(key = "feed-error") {
                            ScreenFailureOrLogin(
                                message = s.message,
                                expired = s.sessionExpired,
                                onRetry = state::reload,
                                onOpenLogin = nav::login,
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                            )
                        }
                        is ListState.Loaded -> resultItems(
                            feed = s.feed,
                            clickPaging = state.clickPaging,
                            onLoadMore = state::loadMore,
                            onPageChange = state::jumpToPage,
                            onOpenAlbum = { state.openAlbum(it) },
                            columns = feedColumns,
                        )
                    }
                }
            }
        }

        // 原版搜索语法帮助：空格=同时包含，+ 必须包含，- 排除；按钮沿用 HyperOS 纵向全宽惯例。
        OverlayDialog(
            show = state.showSearchHelp,
            maxWidth = overlayDialogMaxWidth(),
            title = "搜索语法",
            cornerRadius = skin.shapes.dialogCorner,
            summary = "用空格与 +/- 组合关键词，可缩小搜索范围。",
            onDismissRequest = { state.showSearchHelp = false },
        ) {
            Card(cornerRadius = skin.shapes.cardCorner, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                BasicComponent(title = "全彩 人妻", summary = "同时包含这两个关键词（空格分隔）")
                BasicComponent(title = "全彩 +人妻", summary = "两个关键词都必须包含")
                BasicComponent(title = "全彩 -人妻", summary = "包含「全彩」，排除「人妻」")
            }
            // 水平内边距由弹窗自带，这里只补垂直间距，避免左右变成 48dp
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                TextButton(
                    text = "知道了",
                    onClick = { state.showSearchHelp = false },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** 搜索记录卡：标题右侧清除全部，点击词条复搜，行尾删除单条。 */
@Composable
private fun SearchHistoryCard(
    history: List<String>,
    onPick: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
) {
    val skin = LocalSkin.current
    Card(cornerRadius = skin.shapes.cardCorner, modifier = Modifier.fillMaxWidth().padding(horizontal = SectionHorizontalPadding, vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "搜索记录",
                style = MiuixTheme.textStyles.subtitle,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                modifier = Modifier.weight(1f),
            )
            MinTouchTarget(onClick = onClear) {
                TextButton(
                    text = "清除",
                    onClick = onClear,
                    minWidth = 0.dp,
                    minHeight = 36.dp,
                    insideMargin = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                )
            }
        }
        history.forEach { word ->
            BasicComponent(
                title = word,
                onClick = { onPick(word) },
                endActions = {
                    IconButton(
                        onClick = { onRemove(word) },
                        minWidth = 48.dp,
                        minHeight = 48.dp,
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Basic.Close,
                            contentDescription = "删除该记录",
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                },
            )
        }
    }
}


/** 热门标签胶囊流：默认折叠，更多/收起由区块标题控制。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HotTagFlow(tags: List<String>, expanded: Boolean, onClick: (String) -> Unit) {
    val visible = if (expanded) tags else tags.take(HOT_TAG_COLLAPSED)
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            // 展开/收起时高度弹性过渡，避免列表瞬间跳变
            .animateContentSize(
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 700f),
            ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        visible.forEach { tag ->
            MinTouchTarget(onClick = { onClick(tag) }) {
                TextButton(
                    text = tag,
                    onClick = { onClick(tag) },
                    minWidth = 0.dp,
                    minHeight = 32.dp,
                    insideMargin = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        }
    }
}

/** 连载更新卡：封面 + 标题 + 作者，横向滑动浏览；外观走 [WorkGridCard] 的统一契约。 */
@Composable
private fun SerialUpdateCard(update: SearchResult, onClick: () -> Unit) {
    WorkGridCard(
        title = update.title,
        coverUrl = update.coverUrl,
        onClick = onClick,
        albumId = update.id,
        coverContentDescription = update.title,
        subtitle = update.authors.joinToString(" / ").ifBlank { "未知作者" },
        category = update.category,
        modifier = Modifier.width(WorkCardTokens.CompactWidth),
        badge = { WorkAlbumCodeBadge(update.id) },
        footer = { WorkTagLine(update.tags) },
    )
}

/** 搜索结果摘要：关键词/结果数；排序已常驻玻璃顶栏。 */
@Composable
private fun SearchResultHeader(
    query: String,
    byTag: Boolean,
    total: Int,
) {
    Text(
        // 摘要前缀与 COUI 首页同源：标签检索用「标签：」，关键词检索用引号
        text = searchHeadline(query, byTag) + if (total > 0) " · 共 $total 结果" else "",
        style = MiuixTheme.textStyles.footnote1,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SectionTextStartPadding, vertical = 12.dp),
    )
}

@Composable
private fun InlineLoading() {
    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        InfiniteProgressIndicator()
    }
}

/** 随机推荐卡：封面 + 两行标题，横向滑动浏览；外观走 [WorkGridCard] 的统一契约。 */
@Composable
private fun RandomPickCard(pick: SearchResult, onClick: () -> Unit) {
    // 行高缺省时会按字形最大度量排版：日文假名、emoji 能让个别标题每行高出一点，
    // 同排卡片便出现毫厘之差。三行文字都按字号折算固定行高，整卡高度只由行数决定。
    val titleLineHeight = largeScreenTitleStyle(MiuixTheme.textStyles.body2).pinnedLineHeight()
    val subtitleLineHeight = MiuixTheme.textStyles.footnote1.pinnedLineHeight()
    val tagLineHeight = MiuixTheme.textStyles.footnote2.pinnedLineHeight()
    WorkGridCard(
        title = pick.title,
        coverUrl = pick.coverUrl,
        onClick = onClick,
        albumId = pick.id,
        coverContentDescription = pick.title,
        // 缺作者/缺标签也占住对应行：随机推荐是等高卡片，少一行就会比同排卡片矮
        subtitle = pick.authors.joinToString(" / ").ifBlank { "未知作者" },
        category = pick.category,
        titleLineHeight = titleLineHeight,
        subtitleLineHeight = subtitleLineHeight,
        modifier = Modifier.width(WorkCardTokens.CompactWidth),
        badge = { WorkAlbumCodeBadge(pick.id) },
        footer = { WorkTagLine(pick.tags, reserveLine = true, lineHeight = tagLineHeight) },
    )
}
