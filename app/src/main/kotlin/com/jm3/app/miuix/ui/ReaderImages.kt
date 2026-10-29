package com.jm3.app.miuix.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import coil3.ComponentRegistry
import coil3.ImageLoader
import coil3.compose.LocalPlatformContext
import coil3.decode.DataSource
import coil3.disk.DiskCache
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
import okio.Path.Companion.toPath

/** 阅读页图片模型直接复用 PageImage；键 = photoId/index，翻回旧页可命中内存缓存。 */
class ReaderKeyer : Keyer<PageImage> {
    override fun key(data: PageImage, options: Options): String = keyOf(data)

    companion object {
        /** 内存缓存与磁盘缓存共用同一键，避免两处各拼一份前缀而写错。 */
        fun keyOf(data: PageImage): String = "jm3-reader/" + data.photoId + "/" + data.index
    }
}

/** 取图：已下载页读本地文件，否则经网关在线取（含分割还原字节）。 */
class ReaderFetcherFactory(
    private val gateway: Jm3Gateway,
    private val store: DownloadStore?,
) : Fetcher.Factory<PageImage> {
    override fun create(data: PageImage, options: Options, imageLoader: ImageLoader): Fetcher = Fetcher {
        // 字节来源只有一处：磁盘缓存命中就直接复用，命中不到再取。
        // 必须走磁盘缓存：telephoto 的分块超采样只能从磁盘缓存快照构造，
        // 直接抛内存流时它拿不到 diskCacheKey，会退回整图解码。
        val diskCache = imageLoader.diskCache
        val cacheKey = ReaderKeyer.keyOf(data)
        diskCache?.openSnapshot(cacheKey)?.let { snapshot ->
            return@Fetcher SourceFetchResult(
                source = ImageSource(snapshot.data, diskCache.fileSystem, cacheKey, snapshot),
                mimeType = null,
                dataSource = DataSource.DISK,
            )
        }
        // 页列表下标 0 起，落盘页号 1 起，统一经 PageNumbers 换算。
        val pageNo = PageNumbers.pageNo(data.index)
        val local = store
            ?.takeIf { it.exists(data.photoId, pageNo) }
            ?.let { withContext(Dispatchers.IO) { it.read(data.photoId, pageNo) } }
        val bytes = local ?: withContext(Dispatchers.IO) { gateway.imageBytes(data) }
        val dataSource = if (local != null) DataSource.DISK else DataSource.NETWORK
        // 无磁盘缓存（例如页面误用了不带缓存的 ImageLoader）时退回内存流
        val editor = diskCache?.openEditor(cacheKey)
            ?: return@Fetcher SourceFetchResult(
                source = ImageSource(Buffer().apply { write(bytes) }, FileSystem.SYSTEM),
                mimeType = null,
                dataSource = dataSource,
            )
        try {
            diskCache.fileSystem.write(editor.data) { write(bytes) }
            val snapshot = editor.commitAndOpenSnapshot()
            if (snapshot == null) {
                // 提交成功但快照打不开（极少数情况）：本次退回内存流，条目下次命中仍可用
                SourceFetchResult(
                    source = ImageSource(Buffer().apply { write(bytes) }, FileSystem.SYSTEM),
                    mimeType = null,
                    dataSource = dataSource,
                )
            } else {
                SourceFetchResult(
                    source = ImageSource(snapshot.data, diskCache.fileSystem, cacheKey, snapshot),
                    mimeType = null,
                    dataSource = dataSource,
                )
            }
        } catch (t: Throwable) {
            // 写盘或提交失败时放弃这次编辑，避免留下半截缓存条目
            editor.abort()
            throw t
        }
    }
}

// 进程级共享的阅读器 ImageLoader。Coil3 对同一磁盘目录只保留一个 DiskCache 实例，
// 按屏创建再按屏 shutdown 会让旧屏在换章重叠时序下关掉新屏正在用的缓存，
// 表现为换章后所有图片加载失败；因此共享实例只在网关或下载源变化时重建，
// 生命周期跟随进程，页面销毁时不做 shutdown。
private val sharedLoaderLock = Any()

@Volatile
private var sharedLoader: ImageLoader? = null

@Volatile
private var sharedLoaderKey: Pair<Jm3Gateway, DownloadStore?>? = null

private fun acquireReaderImageLoader(
    gateway: Jm3Gateway,
    store: DownloadStore?,
    build: () -> ImageLoader,
): ImageLoader = synchronized(sharedLoaderLock) {
    val key = gateway to store
    val existing = sharedLoader
    if (existing != null && sharedLoaderKey == key) {
        existing
    } else {
        // 先摘引用再关旧实例：构建失败时不会留下指向已关闭实例的缓存
        sharedLoader = null
        sharedLoaderKey = null
        existing?.shutdown()
        build().also {
            sharedLoader = it
            sharedLoaderKey = key
        }
    }
}

@Composable
fun rememberReaderImageLoader(
    gateway: Jm3Gateway,
    store: DownloadStore? = null,
): ImageLoader {
    val context = LocalPlatformContext.current
    // remember 保证 composition 内稳定返回同一实例；真正跨屏复用的是上面的进程级缓存
    return remember(gateway, store) {
        acquireReaderImageLoader(gateway, store) {
            ImageLoader.Builder(context)
                .components(
                    ComponentRegistry.Builder()
                        .add(ReaderKeyer())
                        .add(ReaderFetcherFactory(gateway, store))
                        .build(),
                )
                // 阅读器的分块超采样（telephoto）只能从磁盘缓存快照构造分段位图，
                // 自建 ImageLoader 默认不带缓存，这里补一个，页图才会按视口分块解码。
                .diskCache {
                    DiskCache.Builder()
                        .directory(context.cacheDir.absolutePath.toPath().resolve("reader_images"))
                        .maxSizePercent(0.02)
                        .build()
                }
                .build()
        }
    }
}
