package com.jm3.app.miuix.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.failureCopy
import com.jm3.app.miuix.data.download.decodePhotoPages
import com.jm3.app.miuix.ui.glass.GlassTopAppBar
import com.jm3.core.runCatchingSuspending
import com.jm3.core.download.DownloadRecord
import com.jm3.core.download.DownloadStatus
import com.jm3.core.history.HistoryEntry
import com.jm3.core.model.AlbumDetail
import com.jm3.core.model.Chapter
import com.jm3.core.model.PhotoPages
import com.jm3.core.settings.ReaderSettings
import com.jm3.core.session.SessionOutcome
import com.jm3.app.miuix.ui.glass.GlassScaffold
import com.jm3.app.miuix.ui.route.AppRoutes
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.SnackbarHostState

/** 阅读进度落盘前的静置时长：翻页停下来才写，避免连滚时逐页写设置存储。 */
internal const val PROGRESS_SAVE_DEBOUNCE_MS = 400L
internal const val VOLUME_THROTTLE_MS = 150L
internal const val TRACK_MUTE_DAYS_MS = 3L * 24 * 60 * 60 * 1000

/** 音量键翻页分发：Activity 收到按键后转交挂载中的阅读器，未挂载时返回 false 走系统音量。 */
object ReaderVolumeKeys {
    @Volatile
    var handler: ((Int) -> Boolean)? = null

    fun dispatch(keyCode: Int): Boolean = handler?.invoke(keyCode) ?: false
}

/** 从 Compose 的 Context 链上找宿主 Activity，窗口亮度与常亮标记都要打在它上面。 */
internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

sealed interface ReaderUiState {
    data object Loading : ReaderUiState
    data class Loaded(
        val photo: PhotoPages,
        val chapters: List<Chapter>,
        val chapterIndex: Int,
        val favorited: Boolean,
        val liked: Boolean,
    ) : ReaderUiState
    /** [sessionExpired] 为真时整屏换成登录引导：重试必然再吃一次 401。 */
    data class Failed(val message: String, val sessionExpired: Boolean = false) : ReaderUiState
}

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
    is SessionOutcome.Failed -> ReaderUiState.Failed(failureCopy(outcome, "章节加载失败"))
}

@Composable
internal fun ReaderScreen(
    deps: ScreenDeps,
    photoId: String,
    startPage: Int? = null,
    settings: ReaderSettings,
    onBack: () -> Unit,
    nav: AppRoutes,
    snackbarHostState: SnackbarHostState,
) {
    var state by remember(photoId) { mutableStateOf<ReaderUiState>(ReaderUiState.Loading) }
    val scope = rememberCoroutineScope()
    // 首载与重试共享一次去重：新一次加载先取消上一次，结局写回前校验代际，
    // 避免连点重试并行发出多个 gateway.photo，旧响应覆盖新状态。
    val loadGate = remember(photoId) { ReaderLoadGate() }
    var loadJob by remember { mutableStateOf<Job?>(null) }

    // 优先读已下载落盘数据，缺失时回退网关；首载与失败重试复用同一段请求
    suspend fun loadContent() {
        val token = loadGate.start()
        // 落盘页是本地文件，不经网关
        val saved = runCatchingSuspending { deps.downloadManager.find(photoId) }.getOrNull()
        val local = saved
            ?.takeIf { it.record.status == DownloadStatus.DONE }
            ?.pagesJson
            ?.let { decodePhotoPages(it) }
            ?.takeIf { it.images.isNotEmpty() }
        val outcome: SessionOutcome<PhotoPages> = if (local != null) {
            SessionOutcome.Ok(local)
        } else {
            deps.authCall.call { deps.gateway.photo(photoId) }
        }
        when (outcome) {
            is SessionOutcome.Ok -> {
                val photo = outcome.value
                // 离线页的 albumId 未落盘，用下载记录的 albumId 补章节列表与收藏/点赞状态
                val albumId = saved?.record?.albumId ?: photo.albumId
                // 详情仅用于章节列表与收藏/点赞状态；失败不阻塞阅读
                val detail = (deps.authCall.call { deps.gateway.album(albumId) } as? SessionOutcome.Ok)?.value
                // 期间又发起了新的加载：本次结局作废，不覆盖新状态
                if (!loadGate.isLatest(token)) return
                state = resolveReaderLoadedState(photo = photo, savedRecord = saved?.record, detail = detail)
            }

            else -> {
                if (!loadGate.isLatest(token)) return
                readerLoadFailure(outcome)?.let { state = it }
            }
        }
    }

    /** 失败重试入口：先取消在途加载，避免连点并行。 */
    fun load() {
        loadJob?.cancel()
        state = ReaderUiState.Loading
        loadJob = scope.launch { loadContent() }
    }

    LaunchedEffect(photoId) {
        // 同槽位切换 photoId 时先取消前一个章节的在途加载，避免旧请求继续占位或竞争状态
        loadJob?.cancel()
        // 首载也要登记 Job：失败重试入口靠它取消在途加载，注释与实现才一致
        loadJob = scope.launch { loadContent() }
    }

    when (val s = state) {
        is ReaderUiState.Loading -> GlassScaffold(
            topBar = {
                GlassTopAppBar(
                    title = "阅读",
                    navigationIcon = { BackButton(onBack = onBack) },
                    // 透明底色：玻璃底色与模糊由 GlassScaffold 统一绘制
                    color = Color.Transparent,
                )
            },
        ) { innerPadding ->
            LoadingScreen(modifier = Modifier.padding(innerPadding))
        }
        is ReaderUiState.Failed -> GlassScaffold(
            topBar = {
                GlassTopAppBar(
                    title = "阅读",
                    navigationIcon = { BackButton(onBack = onBack) },
                    // 透明底色：玻璃底色与模糊由 GlassScaffold 统一绘制
                    color = Color.Transparent,
                )
            },
        ) { innerPadding ->
            // 失效不给重试：重试必然再吃一次 401，只能去登录
            if (s.sessionExpired) {
                LoginExpiredScreen(onOpenLogin = nav::login, modifier = Modifier.padding(innerPadding))
            } else {
                ErrorScreen(
                    message = s.message,
                    onRetry = ::load,
                    modifier = Modifier.padding(innerPadding),
                )
            }
        }
        is ReaderUiState.Loaded -> {
            LaunchedEffect(s.photo.photoId) {
                val coverUrl = (deps.authCall.call { deps.images.coverUrl(s.photo.albumId) } as? SessionOutcome.Ok)?.value
                runCatchingSuspending {
                    deps.history.upsert(
                        HistoryEntry(
                            photoId = s.photo.photoId,
                            albumId = s.photo.albumId,
                            title = s.photo.title,
                            coverUrl = coverUrl,
                            updatedAt = 0L,
                        ),
                    )
                }
            }
            ReaderPages(
                deps = deps,
                photo = s.photo,
                startPage = startPage,
                chapters = s.chapters,
                chapterIndex = s.chapterIndex,
                favoritedInit = s.favorited,
                likedInit = s.liked,
                settings = settings,
                onBack = onBack,
                onOpenChapter = nav::nextChapter,
                onOpenLogin = nav::login,
                snackbarHostState = snackbarHostState,
            )
        }
    }
}