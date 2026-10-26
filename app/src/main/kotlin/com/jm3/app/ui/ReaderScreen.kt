package com.jm3.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.jm3.app.Jm3Graph
import com.jm3.core.history.HistoryEntry
import com.jm3.core.model.PageImage
import com.jm3.core.model.PhotoPages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar

sealed interface ReaderUiState {
    data object Loading : ReaderUiState
    data class Loaded(val photo: PhotoPages) : ReaderUiState
    data class Failed(val message: String) : ReaderUiState
}

@Composable
fun ReaderScreen(photoId: String, onBack: () -> Unit) {
    var state by remember(photoId) { mutableStateOf<ReaderUiState>(ReaderUiState.Loading) }

    LaunchedEffect(photoId) {
        state = withContext(Dispatchers.IO) {
            runCatching { ReaderUiState.Loaded(Jm3Graph.gateway.photo(photoId)) }
                .getOrElse { ReaderUiState.Failed(it.message ?: "章节加载失败") }
        }
    }

    when (val s = state) {
        is ReaderUiState.Loading -> LoadingScreen()
        is ReaderUiState.Failed -> ErrorScreen("章节加载失败：${s.message}")
        is ReaderUiState.Loaded -> {
            LaunchedEffect(s.photo.photoId) {
                runCatching {
                    Jm3Graph.history.upsert(
                        HistoryEntry(
                            photoId = s.photo.photoId,
                            albumId = s.photo.albumId,
                            title = s.photo.title,
                            coverUrl = null,
                            updatedAt = 0L,
                        ),
                    )
                }
            }
            ReaderPages(photo = s.photo, onBack = onBack)
        }
    }
}

@Composable
private fun ReaderPages(photo: PhotoPages, onBack: () -> Unit) {
    val imageLoader = rememberReaderImageLoader(Jm3Graph.gateway)
    val listState = rememberLazyListState()
    val context = LocalPlatformContext.current
    // 已发起预加载的页号；翻页向前向后都只发一次
    val prefetched = remember { mutableSetOf<Int>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = photo.title,
                navigationIcon = { BackButton(onBack = onBack) },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding,
        ) {
            items(photo.images, key = { it.index }) { image ->
                ReaderPage(image = image, imageLoader = imageLoader)
            }
        }
    }

    // 预加载可见页之后的 1..3 页
    LaunchedEffect(photo.images, imageLoader) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { first ->
                for (offset in 1..3) {
                    val index = first + offset
                    if (index < photo.images.size && prefetched.add(index)) {
                        val request = ImageRequest.Builder(context)
                            .data(photo.images[index])
                            .build()
                        imageLoader.enqueue(request)
                    }
                }
            }
    }
}

@Composable
private fun ReaderPage(image: PageImage, imageLoader: ImageLoader) {
    var failed by remember(image) { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f)) {
        if (failed) {
            Text(text = "图片加载失败", modifier = Modifier.align(Alignment.Center))
        }
        AsyncImage(
            model = image,
            contentDescription = null,
            imageLoader = imageLoader,
            contentScale = ContentScale.Fit,
            onSuccess = { failed = false },
            onError = { failed = true },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
