package com.jm3.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.jm3.app.Jm3Graph
import com.jm3.core.model.AlbumDetail
import com.jm3.core.model.CommentItem
import com.jm3.core.model.CommentPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

sealed interface AlbumUiState {
    data object Loading : AlbumUiState
    data class Loaded(val detail: AlbumDetail) : AlbumUiState
    data class Failed(val message: String) : AlbumUiState
}

private sealed interface CommentsUiState {
    data object Loading : CommentsUiState
    data class Loaded(val page: CommentPage) : CommentsUiState
    data object Failed : CommentsUiState
}

@Composable
fun AlbumDetailScreen(albumId: String, onBack: () -> Unit, onOpenReader: (String) -> Unit) {
    var state by remember(albumId) { mutableStateOf<AlbumUiState>(AlbumUiState.Loading) }
    var comments by remember(albumId) { mutableStateOf<CommentsUiState>(CommentsUiState.Loading) }

    LaunchedEffect(albumId) {
        coroutineScope {
            launch {
                state = withContext(Dispatchers.IO) {
                    runCatching { AlbumUiState.Loaded(Jm3Graph.gateway.album(albumId)) }
                        .getOrElse { AlbumUiState.Failed(it.message ?: "加载失败") }
                }
            }
            launch {
                comments = withContext(Dispatchers.IO) {
                    runCatching { CommentsUiState.Loaded(Jm3Graph.gateway.comments(albumId, 1)) }
                        .getOrDefault(CommentsUiState.Failed)
                }
            }
        }
    }

    when (val s = state) {
        is AlbumUiState.Loading -> LoadingScreen()
        is AlbumUiState.Failed -> ErrorScreen("详情加载失败：${s.message}")
        is AlbumUiState.Loaded -> {
            val detail = s.detail
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = detail.title,
                        navigationIcon = { BackButton(onBack = onBack) },
                    )
                },
            ) { innerPadding ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                ) {
                    item(key = "header") {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            detail.coverUrl?.let { url ->
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    AsyncImage(
                                        model = url,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f),
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                            Text(text = detail.title)
                            Spacer(modifier = Modifier.height(8.dp))
                            val meta = buildList {
                                if (detail.authors.isNotEmpty()) add("作者：${detail.authors.joinToString()}")
                                if (detail.works.isNotEmpty()) add("社团：${detail.works.joinToString()}")
                                if (detail.actors.isNotEmpty()) add("角色：${detail.actors.joinToString()}")
                                detail.likes?.let { add("喜欢：$it") }
                                detail.views?.let { add("浏览：$it") }
                            }
                            for (line in meta) {
                                Text(text = line)
                            }
                            if (detail.tags.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(text = detail.tags.joinToString(separator = " ") { "#$it" })
                            }
                        }
                    }
                    item(key = "chapters-title") {
                        SmallTitle(text = "章节")
                    }
                    items(detail.chapters, key = { it.id }) { chapter ->
                        BasicComponent(
                            title = chapter.title,
                            summary = null,
                            onClick = { onOpenReader(chapter.id) },
                        )
                    }
                    item(key = "comments-title") {
                        val total = (comments as? CommentsUiState.Loaded)?.page?.total
                        SmallTitle(text = if (total != null) "评论（$total）" else "评论")
                    }
                    when (val cs = comments) {
                        is CommentsUiState.Loading -> item(key = "comments-loading") {
                            Text(text = "评论加载中…", modifier = Modifier.fillMaxWidth().padding(16.dp))
                        }
                        is CommentsUiState.Failed -> item(key = "comments-failed") {
                            Text(text = "评论加载失败", modifier = Modifier.fillMaxWidth().padding(16.dp))
                        }
                        is CommentsUiState.Loaded -> items(cs.page.items, key = { "c" + it.id }) { item ->
                            CommentColumn(item)
                        }
                    }
                    item(key = "bottom-space") { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        }
    }
}

/** 只读评论区：作者/日期/赞/内容，回复缩进展示。 */
@Composable
private fun CommentColumn(item: CommentItem, depth: Int = 0) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (depth * 16).dp, end = 16.dp, top = 8.dp),
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = item.author,
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                item.postDate?.let {
                    Text(
                        text = it,
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = item.content, fontSize = 15.sp)
                if (item.likes > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "赞 ${item.likes}",
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }
        for (reply in item.replies) {
            CommentColumn(item = reply, depth = depth + 1)
        }
    }
}
