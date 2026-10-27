package com.jm3.app.miuix.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.jm3.core.model.SearchPage
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 评论回复面板：显示被回复作者、200 字上限与发送状态；换目标时清空草稿。 */
@Composable
fun CommentReplySheet(
    show: Boolean,
    targetAuthor: String?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSend: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    // 每次重新展开时清掉上一次草稿，避免把回复 A 的内容发到 B
    LaunchedEffect(show, targetAuthor) {
        if (show) text = ""
    }
    OverlayBottomSheet(
        show = show,
        title = targetAuthor?.let { "回复 $it" } ?: "回复",
        onDismissRequest = onDismiss,
    ) {
        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                TextField(
                    value = text,
                    onValueChange = { text = it.take(COMMENT_REPLY_MAX_LENGTH) },
                    label = "写下你的回复（最多 $COMMENT_REPLY_MAX_LENGTH 字）",
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "${text.length}/$COMMENT_REPLY_MAX_LENGTH",
                        fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Button(
                        onClick = { onSend(text.trim()) },
                        enabled = !busy && text.isNotBlank(),
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) {
                        Text(text = if (busy) "发送中…" else "发送")
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}

/** 评论字数上限与原版一致（发帖/评论/回复共用 200）。 */
const val COMMENT_REPLY_MAX_LENGTH = 200

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

/** 全屏空态：在可用区域内垂直居中，供无列表的空页面使用。 */
@Composable
fun CenteredEmptyState(title: String, hint: String? = null, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EmptyState(title = title, hint = hint)
    }
}

/** 搜索结果行封面：统一尺寸，缺失时留空占位，保持行高稳定。 */
@Composable
private fun SearchResultCover(url: String?) {
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
            .clip(RoundedCornerShape(6.dp)),
    )
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
        // 逐行卡片：封面 + 标题/作者标签摘要，与其他页的卡式布局保持一致
        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp)) {
            BasicComponent(
                title = result.title,
                summary = listOf(result.authors.joinToString(" "), result.tags.joinToString(" "))
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                    .ifBlank { null },
                startAction = { SearchResultCover(result.coverUrl) },
                onClick = { onOpenAlbum(result.id) },
            )
        }
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

/** 点击分页条：上一页 / 页码 / 下一页，供任意结果列表在“点击切换分页”模式下复用。 */
@Composable
fun ResultPagerBar(page: SearchPage, onPageChange: (Int) -> Unit) {
    val total = page.totalPages.coerceAtLeast(1)
    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.layout.Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(
                text = "上一页",
                onClick = { onPageChange(page.currentPage - 1) },
                enabled = page.currentPage > 1,
                minWidth = 0.dp,
                minHeight = 36.dp,
            )
            Text(
                text = "${page.currentPage} / $total",
                fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            TextButton(
                text = "下一页",
                onClick = { onPageChange(page.currentPage + 1) },
                enabled = page.currentPage < total,
                minWidth = 0.dp,
                minHeight = 36.dp,
            )
        }
    }
}

/** 点击分页结果列表：单页整页展示，底部附翻页条；与无限滚动共用同一行样式。 */
fun LazyListScope.pagedSearchResultItems(
    page: SearchPage,
    onPageChange: (Int) -> Unit,
    onOpenAlbum: (String) -> Unit,
) {
    if (page.items.isEmpty()) {
        item(key = "empty") { EmptyState(title = "无结果", hint = "换个关键词再试试") }
    }
    items(page.items, key = { it.id }) { result ->
        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp)) {
            BasicComponent(
                title = result.title,
                summary = listOf(result.authors.joinToString(" "), result.tags.joinToString(" "))
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                    .ifBlank { null },
                startAction = { SearchResultCover(result.coverUrl) },
                onClick = { onOpenAlbum(result.id) },
            )
        }
    }
    item(key = "pager") { ResultPagerBar(page = page, onPageChange = onPageChange) }
}
