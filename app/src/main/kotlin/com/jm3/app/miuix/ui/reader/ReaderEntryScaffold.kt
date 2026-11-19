package com.jm3.app.miuix.ui.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.data.download.decodePhotoPages
import com.jm3.app.miuix.failureCopy
import com.jm3.app.miuix.ui.ReaderLoadGate
import com.jm3.core.download.DownloadLedgerEntry
import com.jm3.core.download.DownloadRecord
import com.jm3.core.download.DownloadStatus
import com.jm3.core.history.HistoryEntry
import com.jm3.core.model.AlbumDetail
import com.jm3.core.model.Chapter
import com.jm3.core.model.PhotoPages
import com.jm3.core.runCatchingSuspending
import com.jm3.core.session.SessionOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/*
 * 阅读器入口页的单一真源：HyperOS 与 ColorOS 两个皮肤此前把同一份状态编排逐字写了两遍。
 *
 * 收进来的是行为：载入态、代际闸门、离线落盘优先、失败归类、阅读历史回写。
 * 一行控件都不出——GlassScaffold / CouiAppScaffold、骨架标题、各自空态组件与 ReaderPages
 * 仍留在各皮肤文件里，所以本文件不 import 任何 Miuix / COUI 控件。
 * 皮肤差异一律用参数与字段表达：标题读 [READER_ENTRY_TITLE]，登录引导读面
 * [ReaderUiState.Failed.sessionExpired]，重试入口是 [ReaderEntryScaffoldState.load]。
 */

/** 阅读器入口页标题：两皮肤同名，抽成常量免得改文案时漏改一处。 */
internal const val READER_ENTRY_TITLE = "阅读"

/** 章节加载失败的兜底文案：网关没给出原因时用它，两皮肤共用同一句。 */
internal const val READER_LOAD_FAILURE_FALLBACK = "章节加载失败"

/** 阅读器入口页状态：只有一个入口、一条载入链，故用三态密封接口而不是一组布尔。 */
internal sealed interface ReaderUiState {

    /** 首载与重试都在这一态；两皮肤各自渲染加载态骨架。 */
    data object Loading : ReaderUiState

    /** 章节就绪：photo 是最终页列表（离线落盘优先），chapters 已按排序号排好。 */
    data class Loaded(
        val photo: PhotoPages,
        val chapters: List<Chapter>,
        val chapterIndex: Int,
        val favorited: Boolean,
        val liked: Boolean,
    ) : ReaderUiState

    /** 载入失败；[sessionExpired] 为真时整屏换成登录引导，不给重试（重试必然再吃一次 401）。 */
    data class Failed(val message: String, val sessionExpired: Boolean = false) : ReaderUiState
}

/**
 * 页列表 + 落盘记录 + 相册详情合成内容态。
 *
 * 离线页的 albumId/title 未落盘，落盘记录里有就补上（值没变时复用原实例，少一次多态复制）；
 * 详情缺失不阻塞阅读：章节列表为空、收藏与点赞按未选中处理。
 */
internal fun resolveReaderLoadedState(
    photo: PhotoPages,
    savedRecord: DownloadRecord?,
    detail: AlbumDetail?,
): ReaderUiState.Loaded {
    val resolvedAlbumId = photo.albumId.ifBlank { savedRecord?.albumId.orEmpty() }
    val resolvedTitle = photo.title.ifBlank {
        savedRecord?.title?.takeIf { it.isNotBlank() } ?: detail?.title.orEmpty()
    }
    val resolvedPhoto = if (resolvedAlbumId == photo.albumId && resolvedTitle == photo.title) {
        photo
    } else {
        photo.copy(albumId = resolvedAlbumId, title = resolvedTitle)
    }
    val sortedChapters = detail?.chapters?.sortedBy { it.sortOrder }.orEmpty()
    return ReaderUiState.Loaded(
        photo = resolvedPhoto,
        chapters = sortedChapters,
        chapterIndex = sortedChapters.indexOfFirst { it.id == resolvedPhoto.photoId },
        favorited = detail?.favorited == true,
        liked = detail?.liked == true,
    )
}

/**
 * 首载结局 → 阅读器失败态；成功返回 null，由调用方继续走内容分支。
 *
 * 失效与失败必须分开：前者只能去登录，后者才值得重试。
 */
internal fun readerLoadFailure(outcome: SessionOutcome<*>): ReaderUiState.Failed? = when (outcome) {
    is SessionOutcome.Ok -> null
    SessionOutcome.Expired -> ReaderUiState.Failed(SessionOutcome.Expired.message, sessionExpired = true)
    is SessionOutcome.Failed -> ReaderUiState.Failed(failureCopy(outcome, READER_LOAD_FAILURE_FALLBACK))
}

/**
 * 阅读器入口页的外部依赖：只开载入与历史回写要用的几扇门。
 *
 * 窄端口是为了让状态机能在纯 kotlin.test 里跑（不碰 Android、不碰网关实现），
 * 也把「这个页面到底依赖什么」钉在类型上。
 */
internal interface ReaderEntryPort {

    /** 落盘记录；没有下载或读账本失败都按无落盘处理。 */
    suspend fun savedDownload(photoId: String): DownloadLedgerEntry?

    /** 在线页列表。 */
    suspend fun photo(photoId: String): SessionOutcome<PhotoPages>

    /** 在线相册详情（章节与收藏/点赞初值）。 */
    suspend fun album(albumId: String): SessionOutcome<AlbumDetail>

    /** 相册封面地址（写历史用，拿不到不影响阅读）。 */
    suspend fun coverUrl(albumId: String): SessionOutcome<String?>

    /** 阅读历史落库。 */
    suspend fun upsertHistory(entry: HistoryEntry)
}

/** 下载账本与网关的既有实现转成阅读器入口端口。 */
private fun ScreenDeps.asReaderEntryPort(): ReaderEntryPort {
    val gateway = gateway
    val authCall = authCall
    val images = images
    val history = history
    val downloadManager = downloadManager
    return object : ReaderEntryPort {
        override suspend fun savedDownload(photoId: String): DownloadLedgerEntry? = downloadManager.find(photoId)

        override suspend fun photo(photoId: String): SessionOutcome<PhotoPages> = authCall.call { gateway.photo(photoId) }

        override suspend fun album(albumId: String): SessionOutcome<AlbumDetail> = authCall.call { gateway.album(albumId) }

        override suspend fun coverUrl(albumId: String): SessionOutcome<String?> = authCall.call { images.coverUrl(albumId) }

        override suspend fun upsertHistory(entry: HistoryEntry) {
            history.upsert(entry)
        }
    }
}

/**
 * 阅读器入口页状态机：载入态 + 代际闸门 + 历史回写。
 *
 * 首载 [start] 与重试 [load] 的差别只在重试先把界面退回 [ReaderUiState.Loading]：
 * 首载沿用上一次内容态（切章节时不闪白），重试则要立刻给出「正在重来」的反馈。
 */
@Stable
internal class ReaderEntryScaffoldState internal constructor(
    private val scope: CoroutineScope,
    private val port: ReaderEntryPort,
    private val photoId: String,
) {

    /** 当前载入态；皮肤只读来决定渲染哪个分支。 */
    var uiState: ReaderUiState by mutableStateOf(ReaderUiState.Loading)
        private set

    /** 首载与重试共享一次去重：新一次加载先取消上一次，结局写回前校验代际。 */
    private val loadGate = ReaderLoadGate()

    private var loadJob: Job? = null

    /** 首载：换章时先撤掉在途请求，避免旧响应继续占位或竞争状态。 */
    suspend fun start() {
        loadJob?.cancel()
        loadJob = scope.launch { loadContent() }
    }

    /** 失败重试入口：先取消在途加载，避免连点并行。 */
    fun load() {
        loadJob?.cancel()
        uiState = ReaderUiState.Loading
        loadJob = scope.launch { loadContent() }
    }

    /** 优先读已下载落盘数据，缺失时回退网关；首载与失败重试复用同一段请求。 */
    suspend fun loadContent() {
        val token = loadGate.start()
        // 落盘页是本地文件，不经网关
        val saved = runCatchingSuspending { port.savedDownload(photoId) }.getOrNull()
        val local = saved
            ?.takeIf { it.record.status == DownloadStatus.DONE }
            ?.pagesJson
            ?.let { decodePhotoPages(it) }
            ?.takeIf { it.images.isNotEmpty() }
        val outcome: SessionOutcome<PhotoPages> = if (local != null) {
            SessionOutcome.Ok(local)
        } else {
            port.photo(photoId)
        }
        when (outcome) {
            is SessionOutcome.Ok -> {
                val photo = outcome.value
                // 离线页的 albumId 未落盘，用下载记录的 albumId 补章节列表与收藏/点赞状态
                val albumId = saved?.record?.albumId ?: photo.albumId
                // 详情仅用于章节列表与收藏/点赞状态；失败不阻塞阅读
                val detail = (port.album(albumId) as? SessionOutcome.Ok)?.value
                // 期间又发起了新的加载：本次结局作废，不覆盖新状态
                if (!loadGate.isLatest(token)) return
                uiState = resolveReaderLoadedState(photo = photo, savedRecord = saved?.record, detail = detail)
            }

            else -> {
                if (!loadGate.isLatest(token)) return
                readerLoadFailure(outcome)?.let { uiState = it }
            }
        }
    }

    /** 内容就绪后写一条阅读历史；封面拿不到也照写，写库失败不影响阅读。 */
    suspend fun recordLoadedHistory() {
        val loaded = uiState as? ReaderUiState.Loaded ?: return
        val coverUrl = (port.coverUrl(loaded.photo.albumId) as? SessionOutcome.Ok)?.value
        runCatchingSuspending {
            port.upsertHistory(
                HistoryEntry(
                    photoId = loaded.photo.photoId,
                    albumId = loaded.photo.albumId,
                    title = loaded.photo.title,
                    coverUrl = coverUrl,
                    updatedAt = 0L,
                ),
            )
        }
    }

    /** 页面离开时撤掉在途请求；随后到达的响应会被代际闸门丢掉。 */
    internal fun cancelPendingLoad() {
        loadJob?.cancel()
        loadJob = null
    }
}

/**
 * 建立阅读器入口页状态机并按既有次序接上线：
 * 进入即首载、换章即重建、内容就绪即写历史、离开即撤在途。
 */
@Composable
internal fun rememberReaderEntryScaffoldState(deps: ScreenDeps, photoId: String): ReaderEntryScaffoldState {
    val scope = rememberCoroutineScope()
    val state = remember(deps, photoId, scope) { ReaderEntryScaffoldState(scope, deps.asReaderEntryPort(), photoId) }
    LaunchedEffect(state) { state.start() }
    val loadedPhotoId = (state.uiState as? ReaderUiState.Loaded)?.photo?.photoId
    LaunchedEffect(state, loadedPhotoId) { state.recordLoadedHistory() }
    DisposableEffect(state) { onDispose { state.cancelPendingLoad() } }
    return state
}
