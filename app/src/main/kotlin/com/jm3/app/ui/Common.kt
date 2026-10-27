package com.jm3.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jm3.core.model.SearchPage
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 标题栏返回键。 */
@Composable
fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(
            imageVector = MiuixIcons.Back,
            contentDescription = "返回",
            modifier = Modifier.size(24.dp),
        )
    }
}

/** 全屏加载态：进度指示 + 文案；调用方需传入 innerPadding 以免被标题栏覆盖。 */
@Composable
fun LoadingScreen(modifier: Modifier = Modifier, text: String = "加载中…") {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = text)
    }
}

/** 全屏错误态：错误信息 + 重试入口；调用方需传入 innerPadding。 */
@Composable
fun ErrorScreen(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(12.dp))
        TextButton(text = "重试", onClick = onRetry)
    }
}

/** 列表空态：标题 + 可选引导文案。 */
@Composable
fun EmptyState(title: String, hint: String? = null, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = title)
        if (hint != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = hint,
                fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                textAlign = TextAlign.Center,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
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
            EmptyState(title = "无结果", hint = "换个关键词再试试")
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
                    CircularProgressIndicator(size = 20.dp)
                } else {
                    TextButton(text = "加载更多", onClick = onLoadMore)
                }
            }
        }
    }
}
