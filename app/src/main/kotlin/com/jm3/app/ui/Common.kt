package com.jm3.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jm3.core.model.SearchPage
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton

/** 标题栏返回键（miuix 内置图标集无 ArrowBack，用字符替代）。 */
@Composable
fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Text(text = "\u2190", fontSize = 22.sp)
    }
}

@Composable
fun LoadingScreen(text: String = "加载中…") {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = text)
    }
}

@Composable
fun ErrorScreen(message: String) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text = message, modifier = Modifier.fillMaxWidth())
    }
}

/** 搜索/浏览共用的结果列表与加载更多入口；hasMore 由 currentPage/totalPages 推断。 */
fun LazyListScope.searchResultItems(
    page: SearchPage,
    loadingMore: Boolean,
    onLoadMore: () -> Unit,
    onOpenAlbum: (String) -> Unit,
) {
    if (page.items.isEmpty()) {
        item(key = "empty") {
            Text(text = "无结果", modifier = Modifier.fillMaxWidth().padding(16.dp))
        }
    }
    items(page.items, key = { it.id }) { result ->
        BasicComponent(
            title = result.title,
            summary = listOf(result.authors.joinToString(" "), result.tags.joinToString(" "))
                .filter { it.isNotBlank() }
                .joinToString(" · ")
                .ifBlank { null },
            onClick = { onOpenAlbum(result.id) },
        )
    }
    if (page.currentPage < page.totalPages) {
        item(key = "load-more") {
            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                if (loadingMore) {
                    Text(text = "加载中…")
                } else {
                    TextButton(text = "加载更多", onClick = onLoadMore)
                }
            }
        }
    }
}
