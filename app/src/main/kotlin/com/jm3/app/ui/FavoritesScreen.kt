package com.jm3.app.ui

import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.dp
import com.jm3.app.Jm3Graph
import com.jm3.core.mergedWith
import com.jm3.core.model.SearchPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.RadioButtonPreference

@Composable
fun FavoritesScreen(onBack: () -> Unit, onOpenAlbum: (String) -> Unit) {
    var page by remember { mutableStateOf<SearchPage?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loadingMore by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    // 收藏夹筛选：0 = 全部；切换后重新拉第一页。
    var folderId by rememberSaveable { mutableStateOf(0) }
    var folders by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var showFolders by remember { mutableStateOf(false) }
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
        showFolders = false
        if (targetFolderId == folderId) return
        folderId = targetFolderId
        loadFirstPage()
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
        topBar = { TopAppBar(title = "我的收藏", navigationIcon = { BackButton(onBack = onBack) }) },
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
                            ArrowPreference(
                                title = "收藏夹",
                                summary = currentFolderName,
                                onClick = { showFolders = true },
                            )
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
            show = showFolders,
            title = "选择收藏夹",
            onDismissRequest = { showFolders = false },
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
                folderOptions.forEach { (id, name) ->
                    RadioButtonPreference(
                        title = name,
                        selected = folderId == id,
                        onClick = { switchFolder(id) },
                    )
                }
            }
        }
    }
}
