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
import com.jm3.app.miuix.ui.glass.GlassScaffold
import com.jm3.core.appendedBy
import com.jm3.core.model.FavoriteFolderAction
import com.jm3.core.model.SearchPage
import com.jm3.core.model.SearchResult
import com.jm3.core.model.TagFavorite
import com.jm3.core.paging.PagedFeed
import com.jm3.core.paging.pagerTargetPage
import com.jm3.core.session.AuthCall
import com.jm3.core.session.SessionOutcome
import com.jm3.core.settings.PaginationMode
import com.jm3.core.toFeed
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
    authCall: AuthCall,
    onOpenLogin: () -> Unit = {},
) {
    val skin = LocalSkin.current
    val user by deps.account.user.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    // 0 = 收藏夹；1 = 标签收藏
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    val selectionHaptic = rememberSelectionHaptic()

    // null 表示首页还没落地（整屏 Loading）；在途与失败标记由 PagedFeed 自己记
    var feed by remember { mutableStateOf<PagedFeed<SearchResult>?>(null) }
    // 失效与失败共用一个落点：计划自己决定「整屏去登录」还是「给重试按钮」
    var failure by remember { mutableStateOf<ScreenLoadPlan?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    val refreshState = rememberPullToRefreshState(refreshThreshold = 0.18f)
    // 收藏夹筛选：0 = 全部；切换后重新拉第一页。
    var folderId by rememberSaveable { mutableIntStateOf(0) }
    var folders by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var showCreate by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var deleteTarget by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var folderNameInput by remember { mutableStateOf("") }
    var folderBusy by remember { mutableStateOf(false) }
    var folderError by remember { mutableStateOf<String?>(null) }

    // 移动到收藏夹：待移动作品、目标收藏夹选择层与请求状态
    var moveTarget by remember { mutableStateOf<SearchResult?>(null) }
    var moveBusy by remember { mutableStateOf(false) }
    var moveError by remember { mutableStateOf<String?>(null) }

    // 标签收藏：null 表示加载中，空列表表示无数据
    var tags by remember { mutableStateOf<List<TagFavorite>?>(null) }
    var tagsError by remember { mutableStateOf<String?>(null) }
    var tagBusy by remember { mutableStateOf(false) }
    var tagError by remember { mutableStateOf<String?>(null) }
    var showAddTag by remember { mutableStateOf(false) }
    var tagInput by remember { mutableStateOf("") }
    var tagDeleteTarget by remember { mutableStateOf<String?>(null) }

    val mode = deps.preferences.paginationMode
    val clickPaging = mode == PaginationMode.CLICK
    val listState = rememberLazyListState()
    // 翻页在途守卫：请求期间列表仍可读，但翻页条要禁用，避免连点重复请求
    var pageBusy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    /** 失效与失败共用一个落点：有内容就保留列表只提示，没内容才换整屏（失效换成「去登录」）。 */
    fun applyFailure(
        outcome: SessionOutcome<*>,
        hasContent: Boolean,
        failureMessage: String,
        stalePrefix: String = "刷新失败：",
    ) {
        val plan = planLoad(
            outcome = outcome,
            hasContent = hasContent,
            failureMessage = failureMessage,
            stalePrefix = stalePrefix,
        )
        if (plan.keepContent) {
            scope.launch { snackbarHostState.showSnackbar(plan.notice.orEmpty()) }
        } else {
            failure = plan
        }
    }

    fun loadFirstPage() {
        val targetFolderId = folderId
        scope.launch {
            failure = null
            feed = null
            // 启动闸门、IO 切线程与失效通知都在 AuthCall 里
            val outcome = authCall.call { deps.gateway.favorites(targetFolderId, 1) }
            // 请求期间切换了收藏夹：丢弃旧夹结果，避免覆盖新夹内容。
            if (targetFolderId != folderId) return@launch
            when (outcome) {
                is SessionOutcome.Ok -> {
                    val next = outcome.value
                    if (next.folders.isNotEmpty()) folders = next.folders
                    feed = SearchPage(next.currentPage, next.totalPages, next.items).toFeed()
                }

                SessionOutcome.Expired -> applyFailure(outcome, hasContent = false, failureMessage = "")

                is SessionOutcome.Failed -> applyFailure(outcome, hasContent = false, failureMessage = "收藏加载失败")
            }
        }
    }

    /** 点击分页：按目标页整页替换，页码换算复用通用判据。 */
    fun jumpToPage(target: Int) {
        if (pageBusy) return
        val current = feed ?: return
        val resolved = pagerTargetPage(current.page, target, current.pageCount)
        if (resolved == current.page) return
        pageBusy = true
        val targetFolderId = folderId
        scope.launch {
            try {
                val outcome = authCall.call { deps.gateway.favorites(targetFolderId, resolved) }
                if (targetFolderId != folderId) return@launch
                when (outcome) {
                    is SessionOutcome.Ok -> {
                        val next = outcome.value
                        if (next.folders.isNotEmpty()) folders = next.folders
                        feed = SearchPage(next.currentPage, next.totalPages, next.items).toFeed()
                        listState.scrollToItem(0)
                    }

                    SessionOutcome.Expired -> applyFailure(outcome, hasContent = true, failureMessage = "")

                    is SessionOutcome.Failed -> applyFailure(outcome, hasContent = true, failureMessage = "收藏翻页失败")
                }
            } finally {
                pageBusy = false
            }
        }
    }

    // 下拉刷新：保留当前列表，仅用第一页结果替换，避免整屏 Loading 闪烁。
    fun refresh() {
        if (refreshing) return
        refreshing = true
        scope.launch {
            val targetFolderId = folderId
            val outcome = authCall.call { deps.gateway.favorites(targetFolderId, 1) }
            refreshing = false
            if (targetFolderId != folderId) return@launch
            when (outcome) {
                is SessionOutcome.Ok -> {
                    val next = outcome.value
                    if (next.folders.isNotEmpty()) folders = next.folders
                    feed = SearchPage(next.currentPage, next.totalPages, next.items).toFeed()
                }

                // 刷新失败/失效都不该把已有列表清掉：保留内容只提示，没有内容才换整屏
                SessionOutcome.Expired -> applyFailure(
                    outcome,
                    hasContent = feed != null,
                    failureMessage = "",
                )

                is SessionOutcome.Failed -> applyFailure(
                    outcome,
                    hasContent = feed != null,
                    failureMessage = "收藏刷新失败",
                )
            }
        }
    }

    fun switchFolder(targetFolderId: Int) {
        if (targetFolderId == folderId) return
        folderId = targetFolderId
        loadFirstPage()
    }

    /** 收藏夹增删改：成功后关闭弹窗并重拉列表；失败把服务端消息留在弹窗内。 */
    fun manageFolder(action: FavoriteFolderAction, targetId: String, name: String = "") {
        if (folderBusy) return
        folderBusy = true
        folderError = null
        scope.launch {
            val outcome = authCall.call { deps.gateway.manageFavoriteFolder(action, targetId, name) }
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
                    // 删除当前所在收藏夹后回落到全部，避免停留在已不存在的夹
                    if (action == FavoriteFolderAction.DELETE && targetId.toIntOrNull() == folderId) {
                        folderId = 0
                    }
                    loadFirstPage()
                }

                // 账号态操作失效：提示一次并直接给登录入口，不让用户对着按钮重试
                SessionOutcome.Expired -> {
                    snackbarHostState.showSnackbar(SessionOutcome.Expired.message)
                    onOpenLogin()
                }

                is SessionOutcome.Failed -> folderError = outcome.messageOr("操作失败")
            }
        }
    }

    /** 移动作品：成功后关闭选择层、提示并静默刷新当前列表；失败把消息留在选择层内。 */
    fun moveAlbum(albumId: String, folderName: String, targetFolderId: Int) {
        if (moveBusy) return
        moveBusy = true
        moveError = null
        scope.launch {
            val outcome = authCall.call {
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
                    refresh()
                }

                SessionOutcome.Expired -> {
                    snackbarHostState.showSnackbar(SessionOutcome.Expired.message)
                    onOpenLogin()
                }

                is SessionOutcome.Failed -> moveError = outcome.messageOr("移动失败")
            }
        }
    }

    /** 标签收藏列表；未登录不发请求，由界面直接展示登录提示。 */
    fun loadTags() {
        // 绑定发起请求时的用户，避免登出/换号后把旧结果写回界面
        val uid = user?.uid ?: return
        scope.launch {
            tagsError = null
            tags = null
            val outcome = authCall.call { deps.gateway.tagsFavorite() }
            if (user?.uid != uid) return@launch
            when (outcome) {
                is SessionOutcome.Ok -> tags = outcome.value
                // 会话失效后用户会置空，标签页自动回落到上面的未登录引导，不必再留错误文案
                SessionOutcome.Expired -> Unit
                is SessionOutcome.Failed -> tagsError = outcome.messageOr("标签收藏加载失败")
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
        if (tags.orEmpty().any { it.tag == tag }) {
            scope.launch { snackbarHostState.showSnackbar("该标签已收藏") }
            return
        }
        if (tagBusy) return
        tagBusy = true
        tagError = null
        scope.launch {
            val outcome = authCall.call { deps.gateway.addFavoriteTags(listOf(tag)) }
            tagBusy = false
            when (outcome) {
                is SessionOutcome.Ok -> {
                    showAddTag = false
                    tagInput = ""
                    snackbarHostState.showSnackbar("已添加标签 #$tag")
                    loadTags()
                }

                SessionOutcome.Expired -> {
                    snackbarHostState.showSnackbar(SessionOutcome.Expired.message)
                    onOpenLogin()
                }

                is SessionOutcome.Failed -> tagError = outcome.messageOr("添加标签失败")
            }
        }
    }

    /** 删除标签：确认后调用接口并刷新列表。 */
    fun deleteTag(tag: String) {
        if (tagBusy) return
        tagBusy = true
        tagError = null
        scope.launch {
            val outcome = authCall.call { deps.gateway.removeFavoriteTags(listOf(tag)) }
            tagBusy = false
            when (outcome) {
                is SessionOutcome.Ok -> {
                    tagDeleteTarget = null
                    snackbarHostState.showSnackbar("已删除标签 #$tag")
                    loadTags()
                }

                SessionOutcome.Expired -> {
                    snackbarHostState.showSnackbar(SessionOutcome.Expired.message)
                    onOpenLogin()
                }

                is SessionOutcome.Failed -> tagError = outcome.messageOr("删除标签失败")
            }
        }
    }

    LaunchedEffect(Unit) {
        loadFirstPage()
    }

    PaginationModeReloadEffect(mode) { loadFirstPage() }

    // 首次切到标签页才加载；登录态变化时清空旧数据，避免登出后残留
    LaunchedEffect(tabIndex, user?.uid) {
        if (user == null) {
            tags = null
            tagsError = null
        } else if (tabIndex == 1 && tags == null && tagsError == null) {
            loadTags()
        }
    }

    fun loadMore() {
        val current = feed ?: return
        val requested = current.beginLoadMore()
        if (requested === current) return
        val targetFolderId = folderId
        feed = requested
        scope.launch {
            val outcome = authCall.call { deps.gateway.favorites(targetFolderId, requested.page + 1) }
            // 加载期间切换了收藏夹：这批结果属于旧收藏夹，丢弃以免串夹。
            if (targetFolderId != folderId) return@launch
            when (outcome) {
                is SessionOutcome.Ok -> {
                    val next = outcome.value
                    feed = requested.appendedBy(
                        SearchPage(next.currentPage, next.totalPages, next.items),
                    )
                }

                // 翻页撞上会话失效：已加载的列表不能顶掉，保留内容并把页脚置成可重试，只用提示告知去登录
                SessionOutcome.Expired -> {
                    feed = requested.failLoadMore()
                    applyFailure(outcome, hasContent = true, failureMessage = "")
                }

                is SessionOutcome.Failed -> feed = requested.failLoadMore()
            }
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
    if (!clickPaging) {
        val scrollFeed = feed
        if (scrollFeed != null) {
            LoadMoreOnScroll(listState = listState, feed = scrollFeed, onLoadMore = ::loadMore)
        }
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
        if (tabIndex == 0) {
            val p = feed
            val planned = failure
            when {
                planned != null -> if (planned.phase == ScreenLoadPhase.Expired) {
                    LoginExpiredScreen(
                        onOpenLogin = onOpenLogin,
                        modifier = Modifier.padding(innerPadding),
                    )
                } else {
                    ErrorScreen(
                        message = planned.message.orEmpty(),
                        onRetry = ::loadFirstPage,
                        modifier = Modifier.padding(innerPadding),
                    )
                }
                p == null -> LoadingScreen(modifier = Modifier.padding(innerPadding))
                // 列表内容穿到玻璃顶栏（顶栏 + 分段条）下方，顶栏才能采样到真实背景
                else -> GlassPullToRefresh(
                    isRefreshing = refreshing,
                    onRefresh = ::refresh,
                    pullToRefreshState = refreshState,
                    modifier = Modifier.fillMaxSize(),
                    // 刷新指示器下移到玻璃顶栏之下，避免被顶栏遮住
                    contentPadding = innerPadding,
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = innerPadding,
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
                                        folderOptions.getOrNull(index)?.let { switchFolder(it.first) }
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
                        if (p.items.isEmpty()) {
                            item(key = "empty") {
                                EmptyState(title = "暂无收藏", hint = "在作品详情页点击收藏后会出现在这里")
                            }
                        }
                        items(p.items, key = { it.id }) { result ->
                            WorkRowCard(
                                coverUrl = result.coverUrl,
                                title = result.title,
                                onClick = { onOpenAlbum(result.id) },
                                albumId = result.id,
                                subtitle = result.authors.joinToString(" / ").ifBlank { null },
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
                        if (p.items.isNotEmpty()) {
                            item(key = "pagination-footer") {
                                if (clickPaging) {
                                    PagerBar(
                                        page = p.page,
                                        totalPages = p.pageCount,
                                        hasMore = p.hasMore,
                                        enabled = !pageBusy,
                                        onPageChange = ::jumpToPage,
                                    )
                                } else {
                                    LoadMoreFooter(feed = p, onLoadMore = ::loadMore)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            val list = tags
            val tagsErr = tagsError
            when {
                user == null -> CenteredEmptyState(
                    title = "登录后查看标签收藏",
                    hint = "请先在「我的」页面登录",
                    modifier = Modifier.padding(innerPadding),
                )
                tagsErr != null -> ErrorScreen(
                    message = tagsErr,
                    onRetry = ::loadTags,
                    modifier = Modifier.padding(innerPadding),
                )
                list == null -> LoadingScreen(modifier = Modifier.padding(innerPadding))
                list.isEmpty() -> CenteredEmptyState(
                    title = "暂无标签收藏",
                    hint = "点击右上角「+」添加标签，这里就会显示",
                    modifier = Modifier.padding(innerPadding),
                )
                // 列表内容穿到玻璃顶栏（顶栏 + 分段条）下方，顶栏才能采样到真实背景
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = innerPadding,
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

        OverlayDialog(
            show = showCreate || editTarget != null,
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
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
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
