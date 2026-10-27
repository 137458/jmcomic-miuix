package com.jm3.app.miuix.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.squircle.squircleClip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.jm3.app.miuix.Jm3Graph
import com.jm3.core.mergedWith
import com.jm3.core.model.FavoriteFolderAction
import com.jm3.core.model.SearchPage
import com.jm3.core.model.SearchResult
import com.jm3.core.model.TagFavorite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
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
fun FavoritesScreen(onBack: () -> Unit, onOpenAlbum: (String) -> Unit) {
    val user by Jm3Graph.account.user.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    // 0 = 收藏夹；1 = 标签收藏
    var tabIndex by rememberSaveable { mutableStateOf(0) }

    var page by remember { mutableStateOf<SearchPage?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loadingMore by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    // 收藏夹筛选：0 = 全部；切换后重新拉第一页。
    var folderId by rememberSaveable { mutableStateOf(0) }
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

    val scope = rememberCoroutineScope()

    fun loadFirstPage() {
        val targetFolderId = folderId
        scope.launch {
            error = null
            page = null
            val result = withContext(Dispatchers.IO) {
                runCatching { Jm3Graph.gateway.favorites(targetFolderId, 1) }
            }
            result
                .onSuccess {
                    // 请求期间切换了收藏夹：丢弃旧夹结果，避免覆盖新夹内容。
                    if (targetFolderId != folderId) return@onSuccess
                    if (it.folders.isNotEmpty()) folders = it.folders
                    page = SearchPage(it.currentPage, it.totalPages, it.items)
                }
                .onFailure { if (targetFolderId == folderId) error = it.message ?: "收藏加载失败" }
        }
    }

    // 下拉刷新：保留当前列表，仅用第一页结果替换，避免整屏 Loading 闪烁。
    fun refresh() {
        if (refreshing) return
        refreshing = true
        scope.launch {
            val targetFolderId = folderId
            val result = withContext(Dispatchers.IO) {
                runCatching { Jm3Graph.gateway.favorites(targetFolderId, 1) }
            }
            refreshing = false
            result
                .onSuccess {
                    if (targetFolderId != folderId) return@onSuccess
                    if (it.folders.isNotEmpty()) folders = it.folders
                    page = SearchPage(it.currentPage, it.totalPages, it.items)
                }
                .onFailure { if (targetFolderId == folderId) error = it.message ?: "收藏刷新失败" }
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
            val result = withContext(Dispatchers.IO) {
                runCatching { Jm3Graph.gateway.manageFavoriteFolder(action, targetId, name) }
            }
            folderBusy = false
            result
                .onSuccess { operation ->
                    if (!operation.ok) {
                        folderError = operation.message ?: "操作失败"
                        return@onSuccess
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
                .onFailure { folderError = it.message ?: "操作失败" }
        }
    }

    /** 移动作品：成功后关闭选择层、提示并静默刷新当前列表；失败把消息留在选择层内。 */
    fun moveAlbum(albumId: String, folderName: String, targetFolderId: Int) {
        if (moveBusy) return
        moveBusy = true
        moveError = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    Jm3Graph.gateway.manageFavoriteFolder(
                        FavoriteFolderAction.MOVE,
                        targetFolderId.toString(),
                        "",
                        albumId,
                    )
                }
            }
            moveBusy = false
            result
                .onSuccess { operation ->
                    if (!operation.ok) {
                        moveError = operation.message ?: "移动失败"
                        return@onSuccess
                    }
                    moveTarget = null
                    // onSuccess 非挂起上下文，提示另起协程
                    scope.launch { snackbarHostState.showSnackbar("已移动到「$folderName」") }
                    refresh()
                }
                .onFailure { moveError = it.message ?: "移动失败" }
        }
    }

    /** 标签收藏列表；未登录不发请求，由界面直接展示登录提示。 */
    fun loadTags() {
        // 绑定发起请求时的用户，避免登出/换号后把旧结果写回界面
        val uid = user?.uid ?: return
        scope.launch {
            tagsError = null
            tags = null
            val result = withContext(Dispatchers.IO) {
                runCatching { Jm3Graph.gateway.tagsFavorite() }
            }
            result
                .onSuccess { if (user?.uid == uid) tags = it }
                .onFailure { if (user?.uid == uid) tagsError = it.message ?: "标签收藏加载失败" }
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
            val result = withContext(Dispatchers.IO) {
                runCatching { Jm3Graph.gateway.addFavoriteTags(listOf(tag)) }
            }
            tagBusy = false
            result
                .onSuccess {
                    showAddTag = false
                    tagInput = ""
                    scope.launch { snackbarHostState.showSnackbar("已添加标签 #$tag") }
                    loadTags()
                }
                .onFailure { tagError = it.message ?: "添加标签失败" }
        }
    }

    /** 删除标签：确认后调用接口并刷新列表。 */
    fun deleteTag(tag: String) {
        if (tagBusy) return
        tagBusy = true
        tagError = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { Jm3Graph.gateway.removeFavoriteTags(listOf(tag)) }
            }
            tagBusy = false
            result
                .onSuccess {
                    tagDeleteTarget = null
                    scope.launch { snackbarHostState.showSnackbar("已删除标签 #$tag") }
                    loadTags()
                }
                .onFailure { tagError = it.message ?: "删除标签失败" }
        }
    }

    LaunchedEffect(Unit) {
        loadFirstPage()
    }

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
        val current = page ?: return
        if (loadingMore || current.currentPage >= current.totalPages) return
        val targetFolderId = folderId
        loadingMore = true
        scope.launch {
            val next = withContext(Dispatchers.IO) {
                runCatching { Jm3Graph.gateway.favorites(targetFolderId, current.currentPage + 1) }.getOrNull()
            }
            loadingMore = false
            // 加载期间切换了收藏夹：这批结果属于旧收藏夹，丢弃以免串夹。
            if (next != null && targetFolderId == folderId) {
                page = current.mergedWith(SearchPage(next.currentPage, next.totalPages, next.items))
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

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = "我的收藏",
                    navigationIcon = { BackButton(onBack = onBack) },
                    actions = {
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
                                contentDescription = if (tabIndex == 0) "新建收藏夹" else "添加标签",
                            )
                        }
                    },
                )
                TabRow(
                    tabs = listOf("收藏夹", "标签收藏"),
                    selectedTabIndex = tabIndex,
                    onTabSelected = { tabIndex = it },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        },
        snackbarHost = { SnackbarHost(state = snackbarHostState) },
    ) { innerPadding ->
        if (tabIndex == 0) {
            val p = page
            val err = error
            when {
                err != null -> ErrorScreen(
                    message = err,
                    onRetry = ::loadFirstPage,
                    modifier = Modifier.padding(innerPadding),
                )
                p == null -> LoadingScreen(modifier = Modifier.padding(innerPadding))
                else -> PullToRefresh(
                    isRefreshing = refreshing,
                    onRefresh = ::refresh,
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentPadding = innerPadding,
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        item(key = "folder-selector") {
                            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
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
                                        IconButton(
                                            onClick = {
                                                folderNameInput = currentFolderName
                                                folderError = null
                                                editTarget = folderId to currentFolderName
                                            },
                                        ) {
                                            Icon(MiuixIcons.Edit, contentDescription = "重命名收藏夹")
                                        }
                                        IconButton(onClick = { deleteTarget = folderId to currentFolderName }) {
                                            Icon(MiuixIcons.Delete, contentDescription = "删除收藏夹")
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
                            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp)) {
                                BasicComponent(
                                    title = result.title,
                                    summary = listOf(result.authors.joinToString(" "), result.tags.joinToString(" "))
                                        .filter { it.isNotBlank() }
                                        .joinToString(" · ")
                                        .ifBlank { null },
                                    startAction = { FavoriteRowCover(result.coverUrl) },
                                    endActions = {
                                        IconButton(
                                            onClick = {
                                                moveError = null
                                                moveTarget = result
                                            },
                                        ) {
                                            Icon(MiuixIcons.MoveFile, contentDescription = "移动到收藏夹")
                                        }
                                    },
                                    onClick = { onOpenAlbum(result.id) },
                                )
                            }
                        }
                        if (p.currentPage < p.totalPages) {
                            item(key = "load-more") {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (loadingMore) {
                                        CircularProgressIndicator(size = 20.dp)
                                    } else {
                                        TextButton(text = "加载更多", onClick = ::loadMore)
                                    }
                                }
                            }
                        }
                        item(key = "bottom-space") { Spacer(modifier = Modifier.height(24.dp)) }
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
                else -> LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    item(key = "add-tag") {
                        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
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
                        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp)) {
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
                    item(key = "bottom-space") { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        }

        OverlayDialog(
            show = showCreate || editTarget != null,
            title = if (showCreate) "新建收藏夹" else "重命名收藏夹",
            onDismissRequest = {
                showCreate = false
                editTarget = null
                folderError = null
            },
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
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
                        fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                        color = MiuixTheme.colorScheme.error,
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(
                    text = "保存",
                    onClick = {
                        val name = folderNameInput.trim()
                        if (name.isEmpty()) {
                            folderError = "名称不能为空"
                        } else if (showCreate) {
                            manageFolder(FavoriteFolderAction.ADD, targetId = "", name = name)
                        } else {
                            editTarget?.let { manageFolder(FavoriteFolderAction.EDIT, it.first.toString(), name) }
                        }
                    },
                    enabled = !folderBusy,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    text = "取消",
                    onClick = {
                        showCreate = false
                        editTarget = null
                        folderError = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        OverlayDialog(
            show = deleteTarget != null,
            title = "删除收藏夹",
            summary = deleteTarget?.let { "将删除收藏夹「${it.second}」，夹内作品不会取消收藏。" },
            onDismissRequest = { deleteTarget = null; folderError = null },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                folderError?.let { message ->
                    Text(
                        text = message,
                        fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                        color = MiuixTheme.colorScheme.error,
                    )
                }
                TextButton(
                    text = "取消",
                    onClick = { deleteTarget = null; folderError = null },
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    text = "确认删除",
                    colors = ButtonDefaults.textButtonColors(
                        color = Color.Transparent,
                        textColor = MiuixTheme.colorScheme.error,
                    ),
                    onClick = {
                        deleteTarget?.let {
                            manageFolder(FavoriteFolderAction.DELETE, it.first.toString())
                        }
                    },
                    enabled = !folderBusy,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // 移动到收藏夹：只列自建收藏夹，排除「全部」(id=0)
        val movableFolders = folderOptions.filter { it.first != 0 }
        OverlayBottomSheet(
            show = moveTarget != null,
            title = "移动到收藏夹",
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
                        fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                        color = MiuixTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                if (movableFolders.isEmpty()) {
                    Text(
                        text = "暂无其他收藏夹，请先新建收藏夹",
                        fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                        items(movableFolders, key = { it.first }) { (id, name) ->
                            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
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
            onDismissRequest = {
                if (!tagBusy) {
                    showAddTag = false
                    tagError = null
                }
            },
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
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
                        fontSize = MiuixTheme.textStyles.footnote1.fontSize,
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
            summary = tagDeleteTarget?.let { "将取消收藏标签「#$it」。" },
            onDismissRequest = {
                if (!tagBusy) {
                    tagDeleteTarget = null
                    tagError = null
                }
            },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                tagError?.let { message ->
                    Text(
                        text = message,
                        fontSize = MiuixTheme.textStyles.footnote1.fontSize,
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
                    colors = ButtonDefaults.textButtonColors(
                        color = Color.Transparent,
                        textColor = MiuixTheme.colorScheme.error,
                    ),
                    onClick = { tagDeleteTarget?.let { deleteTag(it) } },
                    enabled = !tagBusy,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** 收藏行封面：与其他列表统一尺寸，缺失时留空占位保持行高稳定。 */
@Composable
private fun FavoriteRowCover(url: String?) {
    if (url == null) {
        Spacer(modifier = Modifier.size(width = 48.dp, height = 64.dp))
        return
    }
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(width = 48.dp, height = 64.dp)
            .squircleClip(6.dp),
    )
}
