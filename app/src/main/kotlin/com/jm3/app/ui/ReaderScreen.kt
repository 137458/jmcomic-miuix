package com.jm3.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.jm3.app.Jm3Graph
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

sealed interface PageState {
    data object Loading : PageState
    data class Ready(val bitmap: Bitmap) : PageState
    data class Failed(val message: String) : PageState
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
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = s.photo.title,
                        navigationIcon = { BackButton(onBack = onBack) },
                    )
                },
            ) { innerPadding ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = innerPadding,
                ) {
                    items(s.photo.images, key = { it.index }) { image ->
                        ReaderPage(image = image)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderPage(image: PageImage) {
    val pageState by produceState<PageState>(PageState.Loading, image) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = Jm3Graph.gateway.imageBytes(image)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }.fold(
                onSuccess = { bmp ->
                    if (bmp != null) PageState.Ready(bmp) else PageState.Failed("图片解码失败")
                },
                onFailure = { PageState.Failed(it.message ?: "图片加载失败") },
            )
        }
    }
    when (val p = pageState) {
        is PageState.Loading -> Box(modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f)) {
            Text(text = "加载中…", modifier = Modifier.align(Alignment.Center))
        }
        is PageState.Ready -> Image(
            bitmap = p.bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth(),
        )
        is PageState.Failed -> Box(modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f)) {
            Text(text = p.message, modifier = Modifier.align(Alignment.Center))
        }
    }
}
