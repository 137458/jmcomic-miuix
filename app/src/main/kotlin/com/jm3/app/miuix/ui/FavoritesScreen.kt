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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.jm3.app.miuix.ui.glass.GlassPullToRefresh
import com.jm3.app.miuix.ui.glass.GlassTopAppBar
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.failureCopy
import com.jm3.app.miuix.ui.glass.GlassScaffold
import com.jm3.app.miuix.ui.list.ListLoadState
import com.jm3.app.miuix.ui.list.SessionListLoader
import com.jm3.core.model.FavoriteFolderAction
import com.jm3.core.model.SearchResult
import com.jm3.core.model.TagFavorite
import com.jm3.core.paging.PageSlice
import com.jm3.core.paging.pagerTargetPage
import com.jm3.core.session.SessionOutcome
import com.jm3.core.settings.PaginationMode
import com.jm3.core.toSlice
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
fun FavoritesScreen(
    deps: ScreenDeps,
    onBack: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenLogin: () -> Unit = {},
) {
    val skin = LocalSkin.current
    val user by deps.account.user.collectAsState()
    val uid = user?.uid
    val snackbarHostState = remember { SnackbarHostState() }
    // 0 = 收藏夹；1 = 标签收藏
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    val selectionHaptic = rememberSelectionHaptic()

    val mode = deps.paging.paginationMode
    val clickPaging = mode == PaginationMode.CLICK
    val listState = rememberLazyListState()
    val refreshState = rememberPullToRefreshState(refreshThreshold = 0.18f)
    // 收藏夹筛选：0 = 全部；切夹即换 loader 实例，旧夹的在途结果随请求代次作废。
    var folderId by rememberSaveable { mutableIntStateOf(0) }
    var folders by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var showCreate by rememberSaveable { mutableStateOf(false) }
    var editTarget by rememberSaveable(stateSaver = IntStringPairNullableSaver) {
        mutableStateOf<Pair<Int, String>?>(null)
    }
    var deleteTarget by rememberSaveable(stateSaver = IntStringPairNullableSaver) {
        mutableStateOf<Pair<Int, String>?>(null)
    }
    var folderNameInput by rememberSaveable { mutableStateOf("") }
    var folderBusy by remember { mutableStateOf(false) }
    var folderError by remember { mutableStateOf<String?>(null) }

    // 移动到收藏夹：待移动作品、目标收藏夹选择层与请求状态
    var moveTarget by remember { mutableStateOf<SearchResult?>(null) }
    var moveBusy by remember { mutableStateOf(false) }
    var moveError by remember { mutableStateOf<String?>(null) }

    // 标签收藏的加载态交给 loader；本屏只剩弹层与行内错误文案
    var tagBusy by remember { mutableStateOf(false) }
    var tagError by remember { mutableStateOf<String?>(null) }
    var showAddTag by rememberSaveable { mutableStateOf(false) }
    var tagInput by rememberSaveable { mutableStateOf("") }
    var tagDeleteTarget by rememberSaveable { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    // 收藏夹作品列表：首屏、翻页、刷新与失败落点都在 loader 里，本屏只给「取哪一页」与「怎么提示」。
    val loader = remember(mode, uid, folderId, snackbarHostState) {
        val folder = folderId
        SessionListLoader<SearchResult>(
            scope = scope,
            idOf = { it.id },
            failureMessage = "收藏加载失败",
            stalePrefix = "刷新失败：",
            fetchPage = { page ->
                val outcome = deps.authCall.call { deps.gateway.favorites(folder, page) }
                when (outcome) {
                    is SessionOutcome.Ok -> {
                        val next = outcome.value
                        // 收藏夹清单随每页一起回来：loader 只装条目，夹名落到本屏状态
                        if (next.folders.isNotEmpty()) folders = next.folders
                        // 收藏接口自带页码与总页数，直接按它建页片，不必再调分页判据
                        SessionOutcome.Ok(
                            PageSlice(
                                items = next.items,
                                hasMore = next.currentPage < next.totalPages,
                                page = next.currentPage,
                                pageCount = next.totalPages.takeIf { it > 0 },
                            ),
                        )
                    }

                    SessionOutcome.Expired -> SessionOutcome.Expired
                    is SessionOutcome.Failed -> outcome
                }
            },
            notice = { message -> scope.launch { snackbarHostState.showSnackbar(message) } },
        )
    }
    val snapshot by loader.snapshot.collectAsState()
    val feed = snapshot.feed
    // 翻页成功后要回顶，但 loader 没有成功回调：登记目标页，等 feed.page 对上再滚
    var pendingScrollPage by remember { mutableIntStateOf(0) }

    // 标签收藏没有分页：整页取一次，加载态/重试/失效都由 loader 承担
    val tagsLoader = remember(uid, snackbarHostState) {
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

    LaunchedEffect(loader) {
        loader.loadFirstPage()
    }

    // 翻页成功后要回顶，但 loader 没有成功回调：靠 feed.page 的变化对齐登记的目标页
    LaunchedEffect(feed.page) {
        if (pendingScrollPage > 0 && pendingScrollPage == feed.page) {
            pendingScrollPage = 0
            listState.scrollToItem(0)
        }
    }

    /** 收藏夹增删改：成功后关闭弹窗并重拉列表；失败把服务端消息留在弹窗内。 */
    fun manageFolder(action: FavoriteFolderAction, targetId: String, name: String = "") {
        if (folderBusy) return
        folderBusy = true
        folderError = null
        scope.launch {
            val outcome = deps.authCall.call { deps.gateway.manageFavoriteFolder(action, targetId, name) }
            folderBusy = false
            when (outcome) {
                is SessionOutcome.Ok -> {
                    val operation = outcome.value
                    if (!operation.ok) {
                        folderError = operation.message ?: "操作失败"
                        return@launch
                    }
                    showCreate = false
                    editTarget = null
                    deleteTarget = null
                    folderNameInput = ""
                    // 删除当前所在收藏夹后回落到全部，避免停留在已不存在的夹；
                    // 改夹会让 loader 以新 key 重建并自动重取，夹未变时才需显式重拉
                    if (action == FavoriteFolderAction.DELETE && targetId.toIntOrNull() == folderId) {
                        folderId = 0
                    } else {
                        loader.loadFirstPage()
                    }
                }

                // 账号态操作失效：提示一次并直接给登录入口，不让用户对着按钮重试
                SessionOutcome.Expired -> {
                    snackbarHostState.showSnackbar(SessionOutcome.Expired.message)
                    onOpenLogin()
                }

                is SessionOutcome.Failed -> folderError = failureCopy(outcome, "操作失败")
            }
        }
    }

    /** 移动作品：成功后关闭选择层、提示并静默刷新当前列表；失败把消息留在选择层内。 */
    fun moveAlbum(albumId: String, folderName: String, targetFolderId: Int) {
        if (moveBusy) return
        moveBusy = true
        moveError = null
        scope.launch {
            val outcome = deps.authCall.call {
                deps.gateway.manageFavoriteFolder(
                    FavoriteFolderAction.MOVE,
                    targetFolderId.toString(),
                    "",
                    albumId,
                )
            }
            moveBusy = false
            when (outcome) {
                is SessionOutcome.Ok -> {
                    val operation = outcome.value
                    if (!operation.ok) {
                        moveError = operation.message ?: "移动失败"
                        return@launch
                    }
                    moveTarget = null
                    snackbarHostState.showSnackbar("已移动到「$folderName」")
                    // 静默刷新：保留当前列表，只把第一页结果盖回来
                    loader.refresh()
                }

                SessionOutcome.Expired -> {
                    snackbarHostState.showSnackbar(SessionOutcome.Expired.message)
                    onOpenLogin()
                }

                is SessionOutcome.Failed -> moveError = failureCopy(outcome, "移动失败")
            }
        }
    }

    /** 添加标签：空值与重复只做轻提示，不打断输入框。 */
    fun addTag() {
        val tag = tagInput.trim()
        if (tag.isEmpty()) {
            scope.launch { snackbarHostState.showSnackbar("标签不能为空") }
            return
        }
        if (tagsSnapshot.feed.items.any { it.tag == tag }) {
            scope.launch { snackbarHostState.showSnackbar("该标签已收藏") }
            return
        }
        if (tagBusy) return
        tagBusy = true
        tagError = null
        scope.launch {
            val outcome = deps.authCall.call { deps.gateway.addFavoriteTags(listOf(tag)) }
            tagBusy = false
            when (outcome) {
                is SessionOutcome.Ok -> {
                    showAddTag = false
                    tagInput = ""
                    snackbarHostState.showSnackbar("已添加标签 #$tag")
                    tagsLoader.loadFirstPage()
                }

                SessionOutcome.Expired -> {
                    snackbarHostState.showSnackbar(SessionOutcome.Expired.message)
                    onOpenLogin()
                }

                is SessionOutcome.Failed -> tagError = failureCopy(outcome, "添加标签失败")
            }
        }
    }

    /** 删除标签：确认后调用接口并刷新列表。 */
    fun deleteTag(tag: String) {
        if (tagBusy) return
        tagBusy = true
        tagError = null
        scope.launch {
            val outcome = deps.authCall.call { deps.gateway.removeFavoriteTags(listOf(tag)) }
            tagBusy = false
            when (outcome) {
                is SessionOutcome.Ok -> {
                    tagDeleteTarget = null
                    snackbarHostState.showSnackbar("已删除标签 #$tag")
                    tagsLoader.loadFirstPage()
                }

                SessionOutcome.Expired -> {
                    snackbarHostState.showSnackbar(SessionOutcome.Expired.message)
                    onOpenLogin()
                }

                is SessionOutcome.Failed -> tagError = failureCopy(outcome, "删除标签失败")
            }
        }
    }

    LaunchedEffect(uid) {
        // 未登录或换号：loader 以 uid 为 key 自行重建并重取，本屏只需清掉夹与标签残留
        folders = emptyMap()
        folderId = 0
    }

    // 首次切到标签页才加载；未登录时界面直接给登录引导，不发请求
    LaunchedEffect(tabIndex, uid) {
        if (uid != null && tabIndex == 1 && tagsLoader.state is ListLoadState.Loading) {
            tagsLoader.loadFirstPage()
        }
    }

    val folderOptions = buildList {
        add(0 to "全部")
        folders.entries
            .mapNotNull { (key, name) -> key.toIntOrNull()?.let { it to name } }
            .filter { it.first != 0 }
            .sortedBy { it.first }
            .forEach { add(it) }
    }
    val currentFolderName = folderOptions.firstOrNull { it.first == folderId }?.second ?: "全部"
    val tagListState = rememberLazyListState()
    val pagerState = rememberSyncedTabPagerState(
        selectedTab = tabIndex,
        pageCount = 2,
        onTabSelected = { next ->
            selectionHaptic()
            tabIndex = next
        },
        useMiuixSpring = true,
    )
    // 滚动分页只在列表已落地时挂载：首屏 Loading 期间挂上去会立刻触发第二次首页请求
    if (!clickPaging && snapshot.state == ListLoadState.Loaded) {
        LoadMoreOnScroll(listState = listState, feed = feed, onLoadMore = loader::loadMore)
    }

    GlassScaffold(
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                GlassTopAppBar(
                    title = "我的收藏",
                    navigationIcon = { BackButton(onBack = onBack) },
                    actions = {
                        val addDesc = if (tabIndex == 0) "新建收藏夹" else "添加标签"
                        TooltipBox(text = addDesc) {
                            IconButton(
                                onClick = {
                                    if (tabIndex == 0) {
                                        folderNameInput = ""
                                        folderError = null
                                        showCreate = true
                                    } else if (user == null) {
                                        scope.launch { snackbarHostState.showSnackbar("请先登录后再添加标签") }
                                    } else {
                                        tagInput = ""
                                        tagError = null
                                        showAddTag = true
                                    }
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
                    tabs = listOf("收藏夹", "标签收藏"),
                    selectedTabIndex = tabIndex,
                    onTabSelected = {
                        selectionHaptic()
                        tabIndex = it
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
            when (val state = snapshot.state) {
                ListLoadState.Loading -> LoadingScreen(modifier = Modifier.padding(innerPadding))
                is ListLoadState.Failed -> if (state.sessionExpired) {
                    LoginExpiredScreen(
                        onOpenLogin = onOpenLogin,
                        modifier = Modifier.padding(innerPadding),
                    )
                } else {
                    ErrorScreen(
                        message = state.message,
                        onRetry = loader::loadFirstPage,
                        modifier = Modifier.padding(innerPadding),
                    )
                }
                // 列表内容穿到玻璃顶栏（顶栏 + 分段条）下方，顶栏才能采样到真实背景
                ListLoadState.Loaded -> GlassPullToRefresh(
                    isRefreshing = snapshot.refreshing,
                    onRefresh = loader::refresh,
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
                                val selectedIndex = folderOptions.indexOfFirst { it.first == folderId }
                                OverlaySpinnerPreference(
                                    items = folderOptions.map { (id, name) ->
                                        DropdownItem(text = name, selected = id == folderId)
                                    },
                                    selectedIndex = selectedIndex.coerceAtLeast(0),
                                    title = "收藏夹",
                                    summary = currentFolderName,
                                    showValue = false,
                                    onSelectedIndexChange = { index ->
                                        // 切夹即改 loader 的 key：旧夹在途结果作废，新夹自动取第一页
                                        folderOptions.getOrNull(index)?.let { folderId = it.first }
                                    },
                                )
                                // 仅自建收藏夹可改名/删除；「全部」是聚合视图，不提供操作
                                if (folderId != 0) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.End,
                                    ) {
                                        TooltipBox(text = "重命名收藏夹") {
                                            IconButton(
                                                onClick = {
                                                    folderNameInput = currentFolderName
                                                    folderError = null
                                                    editTarget = folderId to currentFolderName
                                                },
                                            ) {
                                                Icon(MiuixIcons.Edit, contentDescription = "重命名收藏夹")
                                            }
                                        }
                                        TooltipBox(text = "删除收藏夹") {
                                            IconButton(onClick = { deleteTarget = folderId to currentFolderName }) {
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
                                onClick = { onOpenAlbum(result.id) },
                                albumId = result.id,
                                subtitle = result.authors.authorNames(),
                                category = result.category,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                                textEndPadding = 4.dp,
                                trailing = {
                                    IconButton(
                                        onClick = {
                                            moveError = null
                                            moveTarget = result
                                        },
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
                                                loader.goToPage(resolved)
                                            }
                                        },
                                    )
                                } else {
                                    LoadMoreFooter(feed = feed, onLoadMore = loader::loadMore)
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
                        onOpenLogin = onOpenLogin,
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
                                title = "添加标签",
                                summary = "共 ${list.size} 个收藏标签",
                                onClick = {
                                    tagInput = ""
                                    tagError = null
                                    showAddTag = true
                                },
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
                                        onClick = {
                                            tagError = null
                                            tagDeleteTarget = item.tag
                                        },
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

        OverlayDialog(
            show = showCreate || editTarget != null,
            maxWidth = overlayDialogMaxWidth(),
            title = if (showCreate) "新建收藏夹" else "重命名收藏夹",
            cornerRadius = skin.shapes.dialogCorner,
            onDismissRequest = {
                showCreate = false
                editTarget = null
                folderError = null
            },
        ) {
            // 输入框与按钮都落在弹窗自带的内边距里，不再重复加水平 padding
            Column(modifier = Modifier.fillMaxWidth()) {
                TextField(
                    value = folderNameInput,
                    onValueChange = { folderNameInput = it.take(20) },
                    label = "收藏夹名称（最多 20 字）",
                    modifier = Modifier.fillMaxWidth(),
                )
                folderError?.let { message ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = message,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.error,
                    )
                }
            }
            DialogActionRow(
                primaryText = if (showCreate) "新建" else "保存",
                primaryEnabled = !folderBusy,
                onSecondary = {
                    showCreate = false
                    editTarget = null
                    folderError = null
                },
                onPrimary = {
                    val name = folderNameInput.trim()
                    if (name.isEmpty()) {
                        folderError = "名称不能为空"
                    } else if (showCreate) {
                        manageFolder(FavoriteFolderAction.ADD, targetId = "", name = name)
                    } else {
                        editTarget?.let { manageFolder(FavoriteFolderAction.EDIT, it.first.toString(), name) }
                    }
                },
            )
        }

        OverlayDialog(
            show = deleteTarget != null,
            maxWidth = overlayDialogMaxWidth(),
            title = "删除收藏夹",
            cornerRadius = skin.shapes.dialogCorner,
            summary = deleteTarget?.let { "将删除收藏夹「${it.second}」，夹内作品不会取消收藏。" },
            onDismissRequest = { deleteTarget = null; folderError = null },
        ) {
            folderError?.let { message ->
                Text(
                    text = message,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            DialogActionRow(
                primaryText = "确认删除",
                destructive = true,
                primaryEnabled = !folderBusy,
                onSecondary = { deleteTarget = null; folderError = null },
                onPrimary = {
                    deleteTarget?.let {
                        manageFolder(FavoriteFolderAction.DELETE, it.first.toString())
                    }
                },
            )
        }

        // 移动到收藏夹：只列自建收藏夹，排除「全部」(id=0)
        val movableFolders = folderOptions.filter { it.first != 0 }
        OverlayBottomSheet(
            show = moveTarget != null,
            title = "移动到收藏夹",
            cornerRadius = skin.shapes.sheetCorner,
            onDismissRequest = {
                if (!moveBusy) {
                    moveTarget = null
                    moveError = null
                }
            },
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                moveError?.let { message ->
                    Text(
                        text = message,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                if (movableFolders.isEmpty()) {
                    Text(
                        text = "暂无其他收藏夹，请先新建收藏夹",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = overlayMaxHeight(320.dp))) {
                        items(movableFolders, key = { it.first }) { (id, name) ->
                            Card(cornerRadius = skin.shapes.cardCorner, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                BasicComponent(
                                    title = name,
                                    enabled = !moveBusy,
                                    onClick = {
                                        moveTarget?.let { moveAlbum(it.id, name, id) }
                                    },
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    text = "取消",
                    onClick = {
                        if (!moveBusy) {
                            moveTarget = null
                            moveError = null
                        }
                    },
                    enabled = !moveBusy,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // 添加标签
        OverlayDialog(
            show = showAddTag,
            maxWidth = overlayDialogMaxWidth(),
            title = "添加标签",
            cornerRadius = skin.shapes.dialogCorner,
            onDismissRequest = {
                if (!tagBusy) {
                    showAddTag = false
                    tagError = null
                }
            },
        ) {
            // 水平内边距由弹窗自带，这里只补垂直间距，避免左右变成 48dp
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                TextField(
                    value = tagInput,
                    onValueChange = { tagInput = it.take(20) },
                    label = "标签名称（最多 20 字）",
                    modifier = Modifier.fillMaxWidth(),
                )
                tagError?.let { message ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = message,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.error,
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(
                    text = "添加",
                    onClick = ::addTag,
                    enabled = !tagBusy,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    text = "取消",
                    onClick = {
                        showAddTag = false
                        tagError = null
                    },
                    enabled = !tagBusy,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // 删除标签确认
        OverlayDialog(
            show = tagDeleteTarget != null,
            maxWidth = overlayDialogMaxWidth(),
            title = "删除标签",
            cornerRadius = skin.shapes.dialogCorner,
            summary = tagDeleteTarget?.let { "将取消收藏标签「#$it」。" },
            onDismissRequest = {
                if (!tagBusy) {
                    tagDeleteTarget = null
                    tagError = null
                }
            },
        ) {
            // 同上：按钮列只留垂直间距，水平留给弹窗自身
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                tagError?.let { message ->
                    Text(
                        text = message,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.error,
                    )
                }
                TextButton(
                    text = "取消",
                    onClick = {
                        tagDeleteTarget = null
                        tagError = null
                    },
                    enabled = !tagBusy,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    text = "确认删除",
                    // 与取消键同底色、仅文字用错误色：透明底会让危险键看起来像不可点
                    colors = ButtonDefaults.textButtonColors(textColor = MiuixTheme.colorScheme.error),
                    onClick = { tagDeleteTarget?.let { deleteTag(it) } },
                    enabled = !tagBusy,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
