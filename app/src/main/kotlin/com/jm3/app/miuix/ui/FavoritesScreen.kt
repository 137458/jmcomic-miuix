package com.jm3.app.miuix.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.jm3.app.miuix.ui.glass.GlassPullToRefresh
import com.jm3.app.miuix.ui.glass.GlassTopAppBar
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.ui.components.BackButton
import com.jm3.app.miuix.ui.components.CenteredEmptyState
import com.jm3.app.miuix.ui.components.DialogActionRow
import com.jm3.app.miuix.ui.components.EmptyState
import com.jm3.app.miuix.ui.components.ErrorScreen
import com.jm3.app.miuix.ui.components.LoadMoreFooter
import com.jm3.app.miuix.ui.components.LoadMoreOnScroll
import com.jm3.app.miuix.ui.components.LoadingScreen
import com.jm3.app.miuix.ui.components.PagerBar
import com.jm3.app.miuix.ui.components.TrackedLazyColumn
import com.jm3.app.miuix.ui.components.overlayDialogMaxWidth
import com.jm3.app.miuix.ui.components.overlayMaxHeight
import com.jm3.app.miuix.ui.components.rememberSelectionHaptic
import com.jm3.app.miuix.ui.favorites.FAVORITE_ADD_CONFIRM
import com.jm3.app.miuix.ui.favorites.FAVORITE_ADD_TAG_TITLE
import com.jm3.app.miuix.ui.favorites.FAVORITE_CANCEL_LABEL
import com.jm3.app.miuix.ui.favorites.FAVORITE_DELETE_CONFIRM
import com.jm3.app.miuix.ui.favorites.FAVORITE_DELETE_FOLDER_TITLE
import com.jm3.app.miuix.ui.favorites.FAVORITE_DELETE_TAG_TITLE
import com.jm3.app.miuix.ui.favorites.FAVORITE_FOLDER_ALL_ID
import com.jm3.app.miuix.ui.favorites.FAVORITE_FOLDER_NAME_LABEL
import com.jm3.app.miuix.ui.favorites.FAVORITE_MOVE_EMPTY_HINT
import com.jm3.app.miuix.ui.favorites.FAVORITE_MOVE_TITLE
import com.jm3.app.miuix.ui.favorites.FAVORITE_STALE_PREFIX
import com.jm3.app.miuix.ui.favorites.FAVORITE_TAB_TAGS
import com.jm3.app.miuix.ui.favorites.FAVORITE_TAG_ACTION_LABEL
import com.jm3.app.miuix.ui.favorites.FAVORITE_TAG_NAME_LABEL
import com.jm3.app.miuix.ui.favorites.favoriteDeleteFolderMessage
import com.jm3.app.miuix.ui.favorites.favoriteDeleteTagMessage
import com.jm3.app.miuix.ui.favorites.favoriteDetailFailure
import com.jm3.app.miuix.ui.favorites.rememberFavoriteScaffoldState
import com.jm3.app.miuix.ui.glass.GlassScaffold
import com.jm3.app.miuix.ui.list.ListLoadState
import com.jm3.app.miuix.ui.list.SessionListLoader
import com.jm3.core.model.TagFavorite
import com.jm3.core.paging.PageSlice
import com.jm3.core.paging.pagerTargetPage
import com.jm3.core.session.SessionOutcome
import com.jm3.core.settings.PaginationMode
import com.jm3.app.miuix.ui.route.AppRoutes
import com.jm3.app.miuix.ui.skin.LocalSkin
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TooltipBox
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.MoveFile
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 收藏页：收藏夹（作品）与标签收藏两个分段。 */
@Composable
internal fun FavoritesScreen(
    deps: ScreenDeps,
    onBack: () -> Unit,
    nav: AppRoutes,
) {
    val skin = LocalSkin.current
    val user by deps.account.user.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val selectionHaptic = rememberSelectionHaptic()
    val scope = rememberCoroutineScope()

    val mode = deps.paginationMode
    val clickPaging = mode == PaginationMode.CLICK
    val listState = rememberLazyListState()
    val refreshState = rememberPullToRefreshState(refreshThreshold = 0.18f)

    // 标签收藏没有分页：整页取一次，加载态/重试/失效都由 loader 承担
    val tagsLoader = remember(user?.uid, snackbarHostState) {
        SessionListLoader<TagFavorite>(
            scope = scope,
            idOf = { it.tag },
            failureMessage = "标签收藏加载失败",
            fetchPage = {
                when (val outcome = deps.authCall.call { deps.gateway.tagsFavorite() }) {
                    is SessionOutcome.Ok -> SessionOutcome.Ok(
                        PageSlice(items = outcome.value, hasMore = false, page = 1, pageCount = 1),
                    )

                    SessionOutcome.Expired -> SessionOutcome.Expired
                    is SessionOutcome.Failed -> outcome
                }
            },
            notice = { message -> scope.launch { snackbarHostState.showSnackbar(message) } },
        )
    }
    val tagsSnapshot by tagsLoader.snapshot.collectAsState()

    // 分段、当前收藏夹、名册、四个弹窗与三个网络动作的编排都在骨架里；本屏只留控件与弹窗外观
    val state = rememberFavoriteScaffoldState(
        deps = deps,
        onLoginRequired = nav::login,
        onNotice = { message -> snackbarHostState.showSnackbar(message) },
        favoriteTags = { tagsLoader.feed.items },
        onTagsChanged = tagsLoader::loadFirstPage,
        failureText = ::favoriteDetailFailure,
        stalePrefix = FAVORITE_STALE_PREFIX,
    )
    val snapshot by state.loader.snapshot.collectAsState()
    val feed = snapshot.feed
    var pendingScrollPage by remember { mutableIntStateOf(0) }

    // 翻页成功后要回顶，但 loader 没有成功回调：靠 feed.page 的变化对齐登记的目标页
    LaunchedEffect(feed.page) {
        if (pendingScrollPage > 0 && pendingScrollPage == feed.page) {
            pendingScrollPage = 0
            listState.scrollToItem(0)
        }
    }

    // 首次切到标签页才加载；未登录时界面直接给登录引导，不发请求
    LaunchedEffect(state.tabIndex, state.uid) {
        if (state.uid != null && state.tabIndex == FAVORITE_TAB_TAGS && tagsLoader.state is ListLoadState.Loading) {
            tagsLoader.loadFirstPage()
        }
    }

    val folderOptions = state.folderOptions
    val currentFolderName = state.currentFolderName
    val tagListState = rememberLazyListState()
    val pagerState = rememberSyncedTabPagerState(
        selectedTab = state.tabIndex,
        pageCount = state.tabs.size,
        onTabSelected = { next ->
            selectionHaptic()
            state.selectTab(next)
        },
        useMiuixSpring = true,
    )
    // 滚动分页只在列表已落地时挂载：首屏 Loading 期间挂上去会立刻触发第二次首页请求
    if (!clickPaging && snapshot.state == ListLoadState.Loaded) {
        LoadMoreOnScroll(listState = listState, feed = feed, onLoadMore = state.loader::loadMore)
    }

    GlassScaffold(
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                GlassTopAppBar(
                    title = "我的收藏",
                    navigationIcon = { BackButton(onBack = onBack) },
                    actions = {
                        val addDesc = state.addActionLabel
                        TooltipBox(text = addDesc) {
                            IconButton(
                                onClick = {
                                    if (state.onFoldersTab) state.openFolderCreate() else state.openAddTag()
                                },
                            ) {
                                Icon(
                                    MiuixIcons.Add,
                                    contentDescription = addDesc,
                                )
                            }
                        }
                    },
                    // 透明底色：玻璃底色与模糊由 GlassScaffold 统一绘制
                    color = Color.Transparent,
                )
                // 分段切换固定于列表之外：与顶栏同处玻璃层
                GlassChipRow(
                    tabs = state.tabs,
                    selectedTabIndex = state.tabIndex,
                    onTabSelected = {
                        selectionHaptic()
                        state.selectTab(it)
                    },
                )
            }
        },
        snackbarHost = { GlassSnackbarHost(state = snackbarHostState) },
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            if (page == 0) {
            when (val loadState = snapshot.state) {
                ListLoadState.Loading -> LoadingScreen(modifier = Modifier.padding(innerPadding))
                is ListLoadState.Failed -> if (loadState.sessionExpired) {
                    LoginExpiredScreen(
                        onOpenLogin = nav::login,
                        modifier = Modifier.padding(innerPadding),
                    )
                } else {
                    ErrorScreen(
                        message = loadState.message,
                        onRetry = state.loader::loadFirstPage,
                        modifier = Modifier.padding(innerPadding),
                    )
                }
                // 列表内容穿到玻璃顶栏（顶栏 + 分段条）下方，顶栏才能采样到真实背景
                ListLoadState.Loaded -> GlassPullToRefresh(
                    isRefreshing = snapshot.refreshing,
                    onRefresh = state.loader::refresh,
                    pullToRefreshState = refreshState,
                    modifier = Modifier.fillMaxSize(),
                    // 刷新指示器下移到玻璃顶栏之下，避免被顶栏遮住
                    contentPadding = innerPadding,
                ) {
                    // 大屏行卡两列：列数由 WideLayout.feedColumns 判定，手机断点仍是单列
                    val feedColumns = LocalWideLayout.current.feedColumns
                    TrackedLazyColumn(
                        state = listState,
                        contentPadding = innerPadding,
                        modifier = Modifier.fillMaxSize().readableWidth(),
                    ) {
                        item(key = "folder-selector") {
                            Card(cornerRadius = skin.shapes.cardCorner, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                                // OverlaySpinnerPreference 内置弹出选择层，替代手写 Dialog + RadioButtonPreference。
                                val selectedIndex = folderOptions.indexOfFirst { it.id == state.folderId }
                                OverlaySpinnerPreference(
                                    items = folderOptions.map { folder ->
                                        DropdownItem(text = folder.name, selected = folder.id == state.folderId)
                                    },
                                    selectedIndex = selectedIndex.coerceAtLeast(0),
                                    title = "收藏夹",
                                    summary = currentFolderName,
                                    showValue = false,
                                    onSelectedIndexChange = { index ->
                                        // 切夹即重建 loader：旧夹在途结果作废，新夹自动取第一页
                                        folderOptions.getOrNull(index)?.let { state.switchFolder(it.id) }
                                    },
                                )
                                // 仅自建收藏夹可改名/删除；「全部」是聚合视图，不提供操作
                                if (state.folderId != FAVORITE_FOLDER_ALL_ID) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.End,
                                    ) {
                                        TooltipBox(text = "重命名收藏夹") {
                                            IconButton(onClick = { state.openFolderRename() }) {
                                                Icon(MiuixIcons.Edit, contentDescription = "重命名收藏夹")
                                            }
                                        }
                                        TooltipBox(text = "删除收藏夹") {
                                            IconButton(onClick = { state.openFolderDelete() }) {
                                                Icon(MiuixIcons.Delete, contentDescription = "删除收藏夹")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (feed.items.isEmpty()) {
                            item(key = "empty") {
                                EmptyState(title = "暂无收藏", hint = "在作品详情页点击收藏后会出现在这里")
                            }
                        }
                        // key 仍是作品 id：与单列时逐字一致，升级后滚动位置不丢
                        feedRowItems(
                            count = feed.items.size,
                            columns = feedColumns,
                            keyPrefix = "",
                            keyOf = { index -> feed.items[index].id },
                        ) { index ->
                            val result = feed.items[index]
                            WorkRowCard(
                                coverUrl = result.coverUrl,
                                title = result.title,
                                onClick = { nav.album(result.id) },
                                albumId = result.id,
                                subtitle = result.authors.authorNames(),
                                category = result.category,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                                textEndPadding = 4.dp,
                                trailing = {
                                    IconButton(
                                        onClick = { state.openMove(result) },
                                    ) {
                                        Icon(MiuixIcons.MoveFile, contentDescription = "移动到收藏夹")
                                    }
                                },
                            ) {
                                WorkCardMetaRow(tags = result.tags, albumCode = result.id)
                            }
                        }
                        if (feed.items.isNotEmpty()) {
                            item(key = "pagination-footer") {
                                if (clickPaging) {
                                    PagerBar(
                                        page = feed.page,
                                        totalPages = feed.pageCount,
                                        hasMore = feed.hasMore,
                                        onPageChange = { target ->
                                            val resolved = pagerTargetPage(feed.page, target, feed.pageCount)
                                            if (resolved != feed.page) {
                                                // 成功回顶要等新首页落地，登记目标页由 feed.page 变化触发
                                                pendingScrollPage = resolved
                                                state.loader.goToPage(resolved)
                                            }
                                        },
                                    )
                                } else {
                                    LoadMoreFooter(feed = feed, onLoadMore = state.loader::loadMore)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            val list = tagsSnapshot.feed.items
            val tagsState = tagsSnapshot.state
            when {
                user == null -> CenteredEmptyState(
                    title = "登录后查看标签收藏",
                    hint = "请先在「我的」页面登录",
                    modifier = Modifier.padding(innerPadding).readableWidth(),
                )
                tagsState is ListLoadState.Failed -> if (tagsState.sessionExpired) {
                    LoginExpiredScreen(
                        onOpenLogin = nav::login,
                        modifier = Modifier.padding(innerPadding),
                    )
                } else {
                    ErrorScreen(
                        message = tagsState.message,
                        onRetry = tagsLoader::loadFirstPage,
                        modifier = Modifier.padding(innerPadding).readableWidth(),
                    )
                }
                tagsState == ListLoadState.Loading -> LoadingScreen(
                    modifier = Modifier.padding(innerPadding).readableWidth(),
                )
                list.isEmpty() -> CenteredEmptyState(
                    title = "暂无标签收藏",
                    hint = "点击右上角「+」添加标签，这里就会显示",
                    modifier = Modifier.padding(innerPadding).readableWidth(),
                )
                // 列表内容穿到玻璃顶栏（顶栏 + 分段条）下方，顶栏才能采样到真实背景
                else -> TrackedLazyColumn(
                    state = tagListState,
                    contentPadding = innerPadding,
                    modifier = Modifier.fillMaxSize().readableWidth(),
                ) {
                    item(key = "add-tag") {
                        Card(cornerRadius = skin.shapes.cardCorner, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                            BasicComponent(
                                title = FAVORITE_TAG_ACTION_LABEL,
                                summary = "共 ${list.size} 个收藏标签",
                                onClick = { state.openAddTag() },
                            )
                        }
                    }
                    items(list, key = { it.tag }) { item ->
                        Card(cornerRadius = skin.shapes.cardCorner, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                            BasicComponent(
                                title = "#${item.tag}",
                                summary = item.updatedAt?.let { "更新于 $it" } ?: "更新时间未知",
                                endActions = {
                                    IconButton(
                                        onClick = { state.openTagDelete(item.tag) },
                                    ) {
                                        Icon(MiuixIcons.Delete, contentDescription = "删除标签")
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
        }

        val folderOps = state.folderOps
        OverlayDialog(
            show = folderOps.dialogVisible,
            maxWidth = overlayDialogMaxWidth(),
            title = folderOps.dialogTitle,
            cornerRadius = skin.shapes.dialogCorner,
            onDismissRequest = { state.dismissFolderDialogs() },
        ) {
            // 输入框与按钮都落在弹窗自带的内边距里，不再重复加水平 padding
            Column(modifier = Modifier.fillMaxWidth()) {
                TextField(
                    value = folderOps.nameInput,
                    onValueChange = state::setFolderName,
                    label = FAVORITE_FOLDER_NAME_LABEL,
                    modifier = Modifier.fillMaxWidth(),
                )
                folderOps.errorMessage?.let { message ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = message,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.error,
                    )
                }
            }
            DialogActionRow(
                primaryText = folderOps.confirmLabel,
                primaryEnabled = !folderOps.busy,
                onSecondary = { state.dismissFolderDialogs() },
                onPrimary = { state.submitFolderDialog() },
            )
        }

        OverlayDialog(
            show = folderOps.deleteVisible,
            maxWidth = overlayDialogMaxWidth(),
            title = FAVORITE_DELETE_FOLDER_TITLE,
            cornerRadius = skin.shapes.dialogCorner,
            summary = folderOps.deleteTarget?.let { favoriteDeleteFolderMessage(it.second) },
            onDismissRequest = { state.dismissFolderDialogs() },
        ) {
            folderOps.errorMessage?.let { message ->
                Text(
                    text = message,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            DialogActionRow(
                primaryText = FAVORITE_DELETE_CONFIRM,
                destructive = true,
                primaryEnabled = !folderOps.busy,
                onSecondary = { state.dismissFolderDialogs() },
                onPrimary = { state.confirmFolderDelete() },
            )
        }

        // 移动到收藏夹：只列自建收藏夹，排除「全部」(id=0)
        val moveOps = state.moveOps
        OverlayBottomSheet(
            show = moveOps.visible,
            title = FAVORITE_MOVE_TITLE,
            cornerRadius = skin.shapes.sheetCorner,
            onDismissRequest = { state.dismissMove() },
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                moveOps.errorMessage?.let { message ->
                    Text(
                        text = message,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                if (state.movableFolders.isEmpty()) {
                    Text(
                        text = FAVORITE_MOVE_EMPTY_HINT,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = overlayMaxHeight(320.dp))) {
                        items(state.movableFolders, key = { it.id }) { folder ->
                            Card(cornerRadius = skin.shapes.cardCorner, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                BasicComponent(
                                    title = folder.name,
                                    enabled = !moveOps.busy,
                                    onClick = { state.moveToFolder(folder.id, folder.name) },
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    text = FAVORITE_CANCEL_LABEL,
                    onClick = { state.dismissMove() },
                    enabled = !moveOps.busy,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // 添加标签
        val tagOps = state.tagOps
        OverlayDialog(
            show = tagOps.addVisible,
            maxWidth = overlayDialogMaxWidth(),
            title = FAVORITE_ADD_TAG_TITLE,
            cornerRadius = skin.shapes.dialogCorner,
            onDismissRequest = { state.dismissTagAdd() },
        ) {
            // 水平内边距由弹窗自带，这里只补垂直间距，避免左右变成 48dp
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                TextField(
                    value = tagOps.input,
                    onValueChange = state::setTagInput,
                    label = FAVORITE_TAG_NAME_LABEL,
                    modifier = Modifier.fillMaxWidth(),
                )
                tagOps.errorMessage?.let { message ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = message,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.error,
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(
                    text = FAVORITE_ADD_CONFIRM,
                    onClick = state::submitTag,
                    enabled = !tagOps.busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    text = FAVORITE_CANCEL_LABEL,
                    onClick = { state.dismissTagAdd() },
                    enabled = !tagOps.busy,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // 删除标签确认
        OverlayDialog(
            show = tagOps.deleteVisible,
            maxWidth = overlayDialogMaxWidth(),
            title = FAVORITE_DELETE_TAG_TITLE,
            cornerRadius = skin.shapes.dialogCorner,
            summary = tagOps.deleteTarget?.let { favoriteDeleteTagMessage(it) },
            onDismissRequest = { state.dismissTagDelete() },
        ) {
            // 同上：按钮列只留垂直间距，水平留给弹窗自身
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                tagOps.errorMessage?.let { message ->
                    Text(
                        text = message,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.error,
                    )
                }
                TextButton(
                    text = FAVORITE_CANCEL_LABEL,
                    onClick = { state.dismissTagDelete() },
                    enabled = !tagOps.busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    text = FAVORITE_DELETE_CONFIRM,
                    // 与取消键同底色、仅文字用错误色：透明底会让危险键看起来像不可点
                    colors = ButtonDefaults.textButtonColors(textColor = MiuixTheme.colorScheme.error),
                    onClick = { state.confirmTagDelete() },
                    enabled = !tagOps.busy,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

