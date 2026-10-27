package com.jm3.app.miuix.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jm3.app.miuix.Jm3Graph
import com.jm3.core.mergedWith
import com.jm3.core.model.FavoriteFolderAction
import com.jm3.core.model.SearchPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun FavoritesScreen(onBack: () -> Unit, onOpenAlbum: (String) -> Unit) {
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

    LaunchedEffect(Unit) {
        loadFirstPage()
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
            TopAppBar(
                title = "我的收藏",
                navigationIcon = { BackButton(onBack = onBack) },
                actions = {
                    IconButton(onClick = { folderNameInput = ""; folderError = null; showCreate = true }) {
                        Icon(MiuixIcons.Add, contentDescription = "新建收藏夹")
                    }
                },
            )
        },
    ) { innerPadding ->
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
                    searchResultItems(
                        page = p,
                        loadingMore = loadingMore,
                        onLoadMore = ::loadMore,
                        onOpenAlbum = onOpenAlbum,
                    )
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
    }
}
