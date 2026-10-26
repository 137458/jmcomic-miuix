package com.jm3.app.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jm3.app.Jm3Graph
import com.jm3.core.mergedWith
import com.jm3.core.model.SearchPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar

@Composable
fun FavoritesScreen(onBack: () -> Unit, onOpenAlbum: (String) -> Unit) {
    var page by remember { mutableStateOf<SearchPage?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loadingMore by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val result = withContext(Dispatchers.IO) {
            runCatching { Jm3Graph.gateway.favorites(1) }
        }
        result
            .onSuccess { page = SearchPage(it.currentPage, it.totalPages, it.items) }
            .onFailure { error = it.message ?: "收藏加载失败" }
    }

    fun loadMore() {
        val current = page ?: return
        if (loadingMore || current.currentPage >= current.totalPages) return
        loadingMore = true
        scope.launch {
            val next = withContext(Dispatchers.IO) {
                runCatching { Jm3Graph.gateway.favorites(current.currentPage + 1) }.getOrNull()
            }
            loadingMore = false
            if (next != null) {
                page = current.mergedWith(SearchPage(next.currentPage, next.totalPages, next.items))
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = "我的收藏", navigationIcon = { BackButton(onBack = onBack) }) },
    ) { innerPadding ->
        val p = page
        val err = error
        when {
            err != null -> ErrorScreen(message = err)
            p == null -> LoadingScreen()
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            ) {
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
}
