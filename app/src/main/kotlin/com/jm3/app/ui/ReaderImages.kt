package com.jm3.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import coil3.ComponentRegistry
import coil3.ImageLoader
import coil3.compose.LocalPlatformContext
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import com.jm3.core.Jm3Gateway
import com.jm3.core.download.DownloadStore
import com.jm3.core.download.PageNumbers
import com.jm3.core.model.PageImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.FileSystem

/** 阅读页图片模型直接复用 PageImage；键 = photoId/index，翻回旧页可命中内存缓存。 */
class ReaderKeyer : Keyer<PageImage> {
    override fun key(data: PageImage, options: Options): String? =
        "jm3-reader/" + data.photoId + "/" + data.index
}

/** 取图：已下载页读本地文件，否则经网关在线取（含分割还原字节）。 */
class ReaderFetcherFactory(
    private val gateway: Jm3Gateway,
    private val store: DownloadStore?,
) : Fetcher.Factory<PageImage> {
    override fun create(data: PageImage, options: Options, imageLoader: ImageLoader): Fetcher = Fetcher {
        // 页列表下标 0 起，落盘页号 1 起，统一经 PageNumbers 换算。
        val pageNo = PageNumbers.pageNo(data.index)
        val local = store
            ?.takeIf { it.exists(data.photoId, pageNo) }
            ?.let { withContext(Dispatchers.IO) { it.read(data.photoId, pageNo) } }
        if (local != null) {
            SourceFetchResult(
                source = ImageSource(Buffer().apply { write(local) }, FileSystem.SYSTEM),
                mimeType = null,
                dataSource = DataSource.DISK,
            )
        } else {
            val bytes = withContext(Dispatchers.IO) { gateway.imageBytes(data) }
            SourceFetchResult(
                source = ImageSource(Buffer().apply { write(bytes) }, FileSystem.SYSTEM),
                mimeType = null,
                dataSource = DataSource.NETWORK,
            )
        }
    }
}

@Composable
fun rememberReaderImageLoader(
    gateway: Jm3Gateway,
    store: DownloadStore? = null,
): ImageLoader {
    val context = LocalPlatformContext.current
    val loader = remember(gateway, store) {
        ImageLoader.Builder(context)
            .components(
                ComponentRegistry.Builder()
                    .add(ReaderKeyer())
                    .add(ReaderFetcherFactory(gateway, store))
                    .build(),
            )
            .build()
    }
    DisposableEffect(loader) {
        onDispose { loader.shutdown() }
    }
    return loader
}
