package com.jm3.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.jm3.app.Jm3Graph
import com.jm3.core.mergedWith
import com.jm3.core.model.AlbumDetail
import com.jm3.core.model.CommentItem
import com.jm3.core.model.CommentPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

sealed interface AlbumUiState {
    data object Loading : AlbumUiState
    data class Loaded(val detail: AlbumDetail) : AlbumUiState
    data class Failed(val message: String) : AlbumUiState
}

private sealed interface CommentsUiState {
    data object Loading : CommentsUiState
    data class Loaded(val page: CommentPage, val lastPage: Int) : CommentsUiState
    data object Failed : CommentsUiState
}

@Composable
fun AlbumDetailScreen(
    albumId: String,
    onBack: () -> Unit,
    onOpenReader: (String) -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    var state by remember(albumId) { mutableStateOf<AlbumUiState>(AlbumUiState.Loading) }
    var comments by remember(albumId) { mutableStateOf<CommentsUiState>(CommentsUiState.Loading) }
    var favoriteBusy by remember(albumId) { mutableStateOf(false) }
    var favorited by remember(albumId) { mutableStateOf(false) }
    var commentsLoadingMore by remember(albumId) { mutableStateOf(false) }
    var pullRefreshing by remember(albumId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun toggleFavorite() {
        if (favoriteBusy) return
        favoriteBusy = true
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { Jm3Graph.gateway.toggleFavorite(albumId) }.isSuccess
            }
            favoriteBusy = false
            if (ok) {
                favorited = !favorited
                snackbarHostState.showSnackbar(if (favorited) "已收藏" else "已取消收藏")
            }
        }
    }

    fun loadMoreComments() {
        val loaded = comments as? CommentsUiState.Loaded ?: return
        if (commentsLoadingMore || loaded.page.items.size >= loaded.page.total) return
        commentsLoadingMore = true
        scope.launch {
            val next = withContext(Dispatchers.IO) {
                runCatching { Jm3Graph.gateway.comments(albumId, loaded.lastPage + 1) }.getOrNull()
            }
            commentsLoadingMore = false
            if (next != null) {
                comments = CommentsUiState.Loaded(loaded.page.mergedWith(next), loaded.lastPage + 1)
            }
        }
    }

    suspend fun loadDetail() {
        state = withContext(Dispatchers.IO) {
            runCatching { AlbumUiState.Loaded(Jm3Graph.gateway.album(albumId)) }
                .getOrElse { AlbumUiState.Failed(it.message ?: "加载失败") }
        }
        // 详情返回后才拿到收藏态，用库返回值初始化，避免把已收藏相册当未收藏
        (state as? AlbumUiState.Loaded)?.let { loaded -> favorited = loaded.detail.favorited }
    }

    suspend fun loadComments() {
        comments = withContext(Dispatchers.IO) {
            runCatching { CommentsUiState.Loaded(Jm3Graph.gateway.comments(albumId, 1), 1) }
                .getOrDefault(CommentsUiState.Failed)
        }
    }

    LaunchedEffect(albumId) {
        loadDetail()
        loadComments()
    }

    fun refresh() {
        if (pullRefreshing) return
        pullRefreshing = true
        scope.launch {
            try {
                loadDetail()
                loadComments()
            } finally {
                pullRefreshing = false
            }
        }
    }

    when (val s = state) {
        is AlbumUiState.Loading -> Scaffold(
            topBar = { TopAppBar(title = "详情", navigationIcon = { BackButton(onBack = onBack) }) },
        ) { innerPadding ->
            LoadingScreen(modifier = Modifier.padding(innerPadding))
        }
        is AlbumUiState.Failed -> Scaffold(
            topBar = { TopAppBar(title = "详情", navigationIcon = { BackButton(onBack = onBack) }) },
        ) { innerPadding ->
            ErrorScreen(
                message = "详情加载失败：${s.message}",
                onRetry = { scope.launch { loadDetail() } },
                modifier = Modifier.padding(innerPadding),
            )
        }
        is AlbumUiState.Loaded -> {
            val detail = s.detail
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = detail.title,
                        navigationIcon = { BackButton(onBack = onBack) },
                        actions = {
                            TextButton(
                                text = if (favorited) "取消收藏" else "收藏",
                                onClick = ::toggleFavorite,
                                enabled = !favoriteBusy,
                            )
                            if (detail.chapters.size > 1) {
                                TextButton(
                                    text = "下载全部",
                                    onClick = {
                                        detail.chapters.forEach { chapter ->
                                            Jm3Graph.downloadManager.enqueueChapter(
                                                photoId = chapter.id,
                                                albumId = detail.id,
                                                title = chapter.title,
                                            )
                                        }
                                    },
                                )
                            }
                        },
                    )
                },
                snackbarHost = { SnackbarHost(snackbarHostState) },
            ) { innerPadding ->
                PullToRefresh(
                    isRefreshing = pullRefreshing,
                    onRefresh = ::refresh,
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentPadding = innerPadding,
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
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
                                endActions = {
                                    TextButton(
                                        text = "下载",
                                        onClick = {
                                            Jm3Graph.downloadManager.enqueueChapter(
                                                photoId = chapter.id,
                                                albumId = detail.id,
                                                title = chapter.title,
                                            )
                                        },
                                    )
                                },
                            )
                        }
                        item(key = "comments-title") {
                            val total = (comments as? CommentsUiState.Loaded)?.page?.total
                            SmallTitle(text = if (total != null) "评论（$total）" else "评论")
                        }
                        when (val cs = comments) {
                            is CommentsUiState.Loading -> item(key = "comments-loading") {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                            is CommentsUiState.Failed -> item(key = "comments-failed") {
                                ErrorScreen(
                                    message = "评论加载失败",
                                    onRetry = { scope.launch { loadComments() } },
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                )
                            }
                            is CommentsUiState.Loaded -> {
                                item(key = "comments-card") {
                                    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                            for (comment in cs.page.items) {
                                                CommentRow(item = comment)
                                            }
                                            if (cs.page.items.size < cs.page.total) {
                                                Box(
                                                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    if (commentsLoadingMore) {
                                                        CircularProgressIndicator(size = 20.dp)
                                                    } else {
                                                        TextButton(
                                                            text = "加载更多评论",
                                                            onClick = ::loadMoreComments,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        item(key = "bottom-space") { Spacer(modifier = Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }
}

/** 只读评论区：作者/日期/赞/内容，回复缩进展示；整体挂在单张卡片内逐条排列。 */
@Composable
private fun CommentRow(item: CommentItem, depth: Int = 0) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp + (depth * 16).dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = item.author,
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            item.postDate?.let {
                Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                Text(
                    text = it,
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
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
    for (reply in item.replies) {
        CommentRow(item = reply, depth = depth + 1)
    }
}
