package com.jm3.app.miuix.update

import android.content.Context
import android.os.SystemClock
import com.jm3.core.update.ReleaseAsset
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 应用内 APK 下载状态。
 *
 * 取舍：用 StateFlow 而不是 Compose 的 mutableStateOf。下载发生在 IO 线程上，
 * Snapshot 状态在后台线程写入缺少线程安全保证；StateFlow 本身线程安全，
 * 界面侧 collectAsState 即可，下载层也不必依赖 UI 框架。
 */
sealed interface UpdateDownloadState {

    /** 无下载任务：初始态，也是取消、失败后回到的态 */
    data object Idle : UpdateDownloadState

    /**
     * 下载中。
     *
     * [total] 为 0 表示服务器未给出长度（分块传输），此时界面只能展示已下载字节数，
     * [progress] 恒为 0，不应被当成"进度为零"。
     * [speedBytesPerSec] 是 EMA 平滑后的速度，避免界面数字剧烈跳动。
     */
    data class Downloading(
        val downloaded: Long,
        val total: Long,
        val speedBytesPerSec: Long,
    ) : UpdateDownloadState {
        val progress: Float
            get() = if (total > 0L) (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
    }

    /** 下载完成，[file] 可直接交给 [ApkInstaller.install] */
    data class Done(val file: File) : UpdateDownloadState

    /** 下载失败，[message] 可直接展示 */
    data class Failed(val message: String) : UpdateDownloadState
}

/**
 * 应用内 APK 下载器。
 *
 * 取舍一：用 JDK 自带的 [HttpURLConnection] 而不是 OkHttp。仓库里的 okhttp 只通过
 * coil-network-okhttp 传递进来，不是可直接依赖的下载客户端；而 core 的 UpdateChecker
 * 已经用 HttpURLConnection 直连 GitHub，这里沿用同一范式，不引入新的网络栈，
 * 也不把 GitHub 下载塞进 Jm3Gateway（那是 JM 协议的唯一出口）。
 *
 * 取舍二：状态挂在进程级实例上而不是 Compose 里。下载要跨页面销毁继续、重新进入页面状态还在，
 * 因此实例由 Jm3Graph 组装（ENGINEERING.md 一、1），协程跑在传入的 [appScope]（一、2），
 * 界面只观察 [state]，动作走 [start] / [cancel] / [reset] 三个显式入口。
 *
 * 取舍三：用代际号 [generation] 而不是只 cancel Job 来防旧协程回写。
 * Job.cancel() 是异步的，旧协程可能在取消生效前已写出一帧进度；取消后立刻重新开始时，
 * 旧协程继续写就会冲掉新任务的进度，代际号让过期写入直接丢弃。
 */
class UpdateDownloader(private val appScope: CoroutineScope) {

    /** 下载目录名：必须与 res/xml/update_file_paths.xml 里暴露的 cache-path 一致 */
    private val DOWNLOAD_DIR_NAME = "update"

    /** 资产名不可用时的兜底文件名 */
    private val FALLBACK_FILE_NAME = "jm3-update.apk"

    private val CONNECT_TIMEOUT_MS = 15_000
    private val READ_TIMEOUT_MS = 30_000
    private val MAX_REDIRECTS = 5
    private val USER_AGENT = "jm3-android"

    /** 64 KiB：APK 是顺序大文件，再大的 buffer 对吞吐提升有限，却会白占内存 */
    private val BUFFER_SIZE = 64 * 1024

    /** 进度节流：400ms 比界面刷新慢得多，又足以让进度条看起来连续 */
    private val PROGRESS_INTERVAL_MS = 400L

    /** 计算文件名时剔除的字符：路径分隔符、各平台保留字符与控制字符 */
    private val ILLEGAL_FILE_NAME_CHARS = Regex("""[\\/:*?"<>|\p{Cntrl}]""")

    /** 下载作用域：Jm3Graph 传入的 appScope，不另建长期作用域。 */
    private val scope: CoroutineScope get() = appScope

    private val _state = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    val state: StateFlow<UpdateDownloadState> = _state.asStateFlow()

    /** 每次 start/cancel/reset 递增；协程写状态前比对，过期写入丢弃 */
    private val generation = AtomicLong(0L)

    /** 当前下载任务；只在本对象的同步方法里读写 */
    private var job: Job? = null

    /**
     * 开始下载 [asset]。
     *
     * [context] 只用来取 cacheDir，内部取 applicationContext 后持有，不会泄露 Activity。
     * 已在下载中直接返回：按钮可能被连点，重复启动会互相取消并反复删文件。
     */
    @Synchronized
    fun start(context: Context, asset: ReleaseAsset) {
        if (_state.value is UpdateDownloadState.Downloading) return
        job?.cancel()
        val token = generation.incrementAndGet()
        // 先在调用线程切到 Downloading：界面立刻有反馈，连点也能命中上面的守卫；
        // 协程内部随后只在代际未变时写状态
        _state.value = UpdateDownloadState.Downloading(
            downloaded = 0L,
            total = asset.size.coerceAtLeast(0L),
            speedBytesPerSec = 0L,
        )
        val appContext = context.applicationContext ?: context
        job = scope.launch { runDownload(appContext, asset, token) }
    }

    /** 取消下载并丢弃半成品，状态回到 [UpdateDownloadState.Idle]。 */
    @Synchronized
    fun cancel() {
        job?.cancel()
        job = null
        generation.incrementAndGet()
        _state.value = UpdateDownloadState.Idle
    }

    /**
     * 清理上一次的结果，供"重新下载"使用：Done 时删除已下载的安装包，然后回到 Idle。
     * 下载进行中调用会被忽略，否则会删掉正在写入的文件。
     */
    @Synchronized
    fun reset() {
        val current = _state.value
        if (current is UpdateDownloadState.Downloading) return
        if (current is UpdateDownloadState.Done) {
            // 删除失败不影响状态复位：残留文件会在下次 start 时先被清掉
            runCatching { current.file.delete() }
        }
        job?.cancel()
        job = null
        generation.incrementAndGet()
        _state.value = UpdateDownloadState.Idle
    }

    /** 只有代际未变的协程才允许写状态，防止旧任务覆盖新任务。 */
    private fun publish(token: Long, next: UpdateDownloadState) {
        if (generation.get() == token) _state.value = next
    }

    private suspend fun runDownload(context: Context, asset: ReleaseAsset, token: Long) {
        val directory = File(context.cacheDir, DOWNLOAD_DIR_NAME)
        val target = File(directory, safeFileName(asset.name))
        var connection: HttpURLConnection? = null
        try {
            // launch 到真正执行之间用户可能已经取消或重新开始，过期任务直接退出
            if (generation.get() != token) return
            if (!directory.exists() && !directory.mkdirs()) {
                throw IOException("无法创建下载目录")
            }
            // 上次失败的残留（例如进程被杀）先清掉，避免续写到旧字节后面
            if (target.exists() && !target.delete()) {
                throw IOException("无法清理旧的安装包")
            }

            val opened = openFollowingRedirects(asset.downloadUrl)
            connection = opened
            val total = opened.contentLengthLong.takeIf { it > 0L } ?: 0L
            if (total > 0L) publish(token, UpdateDownloadState.Downloading(0L, total, 0L))

            val result = opened.inputStream.use { input ->
                target.outputStream().use { output ->
                    copyWithProgress(input, output, total, token)
                }
            }
            // 收尾再报一帧，避免界面停在"差最后一段"
            publish(token, UpdateDownloadState.Downloading(result.downloaded, total, result.speedBytesPerSec))

            if (total > 0L && result.downloaded != total) {
                target.delete()
                publish(
                    token,
                    UpdateDownloadState.Failed(
                        "安装包不完整（已下载 ${formatBytes(result.downloaded)} / 应为 ${formatBytes(total)}）",
                    ),
                )
                return
            }
            publish(token, UpdateDownloadState.Done(target))
        } catch (cancelled: CancellationException) {
            runCatching { target.delete() }
            // 代际已变的取消（cancel/reset 已写好状态）不回写，避免盖掉新任务
            publish(token, UpdateDownloadState.Idle)
            throw cancelled
        } catch (error: Exception) {
            runCatching { target.delete() }
            // 异常信息可能为空或只有类名，统一兜底成一句可展示的中文
            val reason = error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
            publish(token, UpdateDownloadState.Failed("下载失败：$reason"))
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * 打开下载连接，并手动跟随最多 [MAX_REDIRECTS] 次重定向。
     *
     * instanceFollowRedirects 已经打开，但不同实现、跨协议（例如 GitHub 的 302 落到
     * objects.githubusercontent.com）未必都自动跟随，所以这里再显式处理 3xx：
     * 每次跳转都重新 openConnection，复用旧连接会带着上一个主机的 Host 头。
     */
    private fun openFollowingRedirects(url: String): HttpURLConnection {
        var currentUrl = url
        var hops = 0
        while (true) {
            val connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = true
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/octet-stream")
            }
            val code = connection.responseCode
            val location = connection.getHeaderField("Location")
            if (code in 300..399 && !location.isNullOrBlank()) {
                connection.disconnect()
                if (hops >= MAX_REDIRECTS) throw IOException("重定向次数过多（HTTP $code）")
                hops++
                currentUrl = URL(URL(currentUrl), location).toString()
                continue
            }
            if (code !in 200..299) {
                runCatching { connection.errorStream?.close() }
                connection.disconnect()
                throw IOException("HTTP $code")
            }
            return connection
        }
    }

    /**
     * 边读边写并节流上报进度。
     *
     * 每次写盘前后都检查协程存活：HttpURLConnection 的阻塞 read 无法被 interrupt，
     * 取消只能在读完当前 buffer 后生效，这也是取消延迟的上限。
     */
    private suspend fun copyWithProgress(
        input: InputStream,
        output: OutputStream,
        total: Long,
        token: Long,
    ): TransferResult {
        val buffer = ByteArray(BUFFER_SIZE)
        var downloaded = 0L
        var speed = 0L
        var lastReportAt = SystemClock.elapsedRealtime()
        var lastReportedBytes = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            downloaded += read
            val now = SystemClock.elapsedRealtime()
            val elapsed = now - lastReportAt
            if (elapsed >= PROGRESS_INTERVAL_MS) {
                val instant = (downloaded - lastReportedBytes) * 1000L / elapsed.coerceAtLeast(1L)
                // 指数滑动平均：历史 0.7、本次 0.3，单次抖动不会让数字跳变
                speed = (speed * 7 + instant * 3) / 10
                publish(token, UpdateDownloadState.Downloading(downloaded, total, speed))
                lastReportAt = now
                lastReportedBytes = downloaded
            }
        }
        output.flush()
        return TransferResult(downloaded, speed)
    }

    /**
     * 资产名来自网络，只当文件名用：去掉路径分隔符与保留字符，
     * 并把 "." / ".." 这类会穿越目录的名字换成兜底名。
     */
    private fun safeFileName(name: String): String {
        val cleaned = name.replace(ILLEGAL_FILE_NAME_CHARS, "_").trim()
        return when {
            cleaned.isEmpty() -> FALLBACK_FILE_NAME
            cleaned == "." || cleaned == ".." -> FALLBACK_FILE_NAME
            else -> cleaned
        }
    }

    /** 字节数转可读文本，只用于失败提示；进度展示的格式由界面自己决定。 */
    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}

/** 一次传输的结果：实际字节数与最后一次平滑速度，供收尾上报使用。 */
private class TransferResult(val downloaded: Long, val speedBytesPerSec: Long)
