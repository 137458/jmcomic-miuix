package com.jm3.app.miuix.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
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
import android.graphics.Bitmap
import coil3.request.ImageRequest
import coil3.request.Options
import coil3.request.bitmapConfig
import com.jm3.app.miuix.failureCopy
import com.jm3.core.Jm3Gateway
import com.jm3.core.download.DownloadStore
import com.jm3.core.download.PageNumbers
import com.jm3.core.download.PhotoSource
import com.jm3.core.model.PageImage
import com.jm3.core.session.AuthCall
import com.jm3.core.session.SessionOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.FileSystem
import okio.Path.Companion.toPath
import java.io.IOException

/** 阅读页图片模型直接复用 PageImage；键 = photoId/index，翻回旧页可命中内存缓存。 */
class ReaderKeyer : Keyer<PageImage> {
    override fun key(data: PageImage, options: Options): String = keyOf(data)

    companion object {
        /** 内存缓存与磁盘缓存共用同一键，避免两处各拼一份前缀而写错。 */
        fun keyOf(data: PageImage): String = "jm3-reader/" + data.photoId + "/" + data.index
    }
}

/** 按缓存键做单飞互斥：同键并发竞争时只让首个请求读网/写盘，后续方直接复用已落盘快照。 */
internal class ReaderKeyLockPool {
    private class Entry(val mutex: Mutex = Mutex(), var refs: Int = 0)

    private val guard = Any()
    private val entries = HashMap<String, Entry>()

    suspend fun <T> withKey(key: String, block: suspend () -> T): T {
        val entry = synchronized(guard) {
            entries.getOrPut(key) { Entry() }.also { it.refs++ }
        }
        return try {
            entry.mutex.withLock { block() }
        } finally {
            synchronized(guard) {
                entry.refs--
                if (entry.refs == 0 && entries[key] === entry) {
                    entries.remove(key)
                }
            }
        }
    }

    internal fun activeKeyCount(): Int = synchronized(guard) { entries.size }
}

/** 取图：已下载页读本地文件，否则经网关在线取（含分割还原字节）。 */
class ReaderFetcherFactory(
    private val gateway: Jm3Gateway,
    private val store: DownloadStore?,
    /**
     * 在线取图也必须经 [AuthCall]：启动闸门与会话失效处置不能因为走图片通道就绕过去。
     * 传取值函数而不是实例：共享 loader 只建一次，接线的会话通道要在取图那一刻再读，
     * 因此详情页省略 [AuthCall]、或会话通道后接线，都不会触发 loader 重建。
     * 未接入时取到 null，保持旧的直连行为。
     */
    private val authCall: () -> AuthCall? = { null },
) : Fetcher.Factory<PageImage> {
    private val keyLocks = ReaderKeyLockPool()

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
        keyLocks.withKey(cacheKey) {
            // 同键前序请求刚刚提交快照时，竞争方直接复用磁盘 FileImageSource，不重复取图也不退回内存流
            diskCache?.openSnapshot(cacheKey)?.let { snapshot ->
                return@withKey SourceFetchResult(
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
            val bytes = local ?: fetchRemote(data)
            val dataSource = if (local != null) DataSource.DISK else DataSource.NETWORK
            // 无磁盘缓存（例如页面误用了不带缓存的 ImageLoader）时退回内存流
            val editor = diskCache?.openEditor(cacheKey)
                ?: return@withKey SourceFetchResult(
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

    /**
     * 在线取图：接了 [AuthCall] 就走与其它网关调用相同的一道闸门；
     * 结局翻译成异常交给 Coil，页内仍按图片加载失败处理并提供重试。
     */
    private suspend fun fetchRemote(data: PageImage): ByteArray {
        val call = authCall() ?: return withContext(Dispatchers.IO) { gateway.imageBytes(data) }
        return when (val outcome = call.call { gateway.imageBytes(data) }) {
            is SessionOutcome.Ok -> outcome.value
            SessionOutcome.Expired -> throw IOException(SessionOutcome.Expired.message)
            is SessionOutcome.Failed -> throw IOException(readerRemoteFailureMessage(outcome))
        }
    }
}

internal fun readerRemoteFailureMessage(outcome: SessionOutcome.Failed): String =
    failureCopy(outcome, "图片加载失败")

// 进程级共享的阅读器 ImageLoader。Coil3 对同一磁盘目录只保留一个 DiskCache 实例，
// 按屏创建再按屏 shutdown 会让旧屏在换章重叠时序下关掉新屏正在用的缓存，
// 表现为换章后所有图片加载失败；因此共享实例只在网关或下载源变化时重建，
// 生命周期跟随进程，页面销毁时不做 shutdown。
private val sharedLoaderLock = Any()

@Volatile
private var sharedLoader: ImageLoader? = null

@Volatile
private var sharedLoaderKey: ReaderLoaderKey? = null

/**
 * 共享实例的判等键：只由网关与下载源决定。
 *
 * 会话通道（[AuthCall]）不参与判等：详情页调用入口没有接线，若它参与比较，
 * 每次进出详情页都会因键不等而 shutdown 重建，阅读器磁盘缓存（reader_images）随之清空；
 * 反向切回阅读器时 remember 键未变，却仍持有已被关掉的实例。
 * 网关取 [PhotoSource]（[Jm3Gateway] 的超接口）：键只关心图片通道身份，测试也能用两方法的假实现穿透。
 */
internal data class ReaderLoaderKey(val gateway: PhotoSource, val store: DownloadStore?)

internal fun readerLoaderKeyOf(gateway: PhotoSource, store: DownloadStore?): ReaderLoaderKey =
    ReaderLoaderKey(gateway, store)

/** 复用判定：键相同才复用；没有实例时必须重建。 */
internal fun shouldReuseReaderLoader(held: ReaderLoaderKey?, next: ReaderLoaderKey): Boolean =
    held != null && held == next

/**
 * 会话通道的运行期引用：共享 loader 只建一次，所以不能在构建时把 [AuthCall] 固定进取图工厂，
 * 否则后接线的调用方永远读不到新值。
 */
internal class ReaderAuthChannel(@Volatile private var authCall: AuthCall? = null) {
    /** 只接受接线：省略参数的调用方传 null，不许清掉别处已经接好的会话闸门。 */
    fun wire(authCall: AuthCall?) {
        if (authCall != null) this.authCall = authCall
    }

    fun current(): AuthCall? = authCall
}

private val readerAuthChannel = ReaderAuthChannel()

private fun acquireReaderImageLoader(
    gateway: Jm3Gateway,
    store: DownloadStore?,
    build: () -> ImageLoader,
): ImageLoader = synchronized(sharedLoaderLock) {
    val key = readerLoaderKeyOf(gateway, store)
    val existing = sharedLoader
    if (existing != null && shouldReuseReaderLoader(sharedLoaderKey, key)) {
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
    authCall: AuthCall? = null,
): ImageLoader {
    val context = LocalPlatformContext.current
    // 会话通道在落地后写入进程级引用：loader 只建一次，值变化不需要也不应该触发重建
    SideEffect { readerAuthChannel.wire(authCall) }
    // remember 只认网关与下载源：会话通道变化不再命中重建；跨屏复用的仍是上面的进程级缓存
    return remember(gateway, store) {
        acquireReaderImageLoader(gateway, store) {
            ImageLoader.Builder(context)
                .components(
                    ComponentRegistry.Builder()
                        .add(ReaderKeyer())
                        .add(ReaderFetcherFactory(gateway, store) { readerAuthChannel.current() })
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

/**
 * 阅读器页图请求的标准配置：
 * 统一指定 RGB_565 减少 50% 内存开销（单页 4MP 仅占约 8MB，大幅降低瀑布流连续解码的 GC 压力与 OOM 风险），
 * 并保证预取与展示请求的配置完全对齐，避免因配置漂移导致 Coil 缓存未命中重新解码。
 */
fun ImageRequest.Builder.configureReaderDefaults(): ImageRequest.Builder = apply {
    bitmapConfig(Bitmap.Config.RGB_565)
}
