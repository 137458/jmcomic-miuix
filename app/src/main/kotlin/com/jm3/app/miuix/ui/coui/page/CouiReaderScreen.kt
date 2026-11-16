package com.jm3.app.miuix.ui.coui.page

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.itos.coui.theme.CouiTheme as CouiKitTheme
import com.itos.coui.topappbar.LargeTopAppBar
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.data.download.decodePhotoPages
import com.jm3.app.miuix.failureCopy
import com.jm3.app.miuix.ui.ReaderLoadGate
import com.jm3.app.miuix.ui.coui.component.CouiAppScaffold
import com.jm3.app.miuix.ui.coui.component.CouiErrorGroup
import com.jm3.app.miuix.ui.coui.component.CouiFeedLoginGroup
import com.jm3.app.miuix.ui.coui.component.CouiLoadingGroup
import com.jm3.app.miuix.ui.coui.component.CouiTopBarBackButton
import com.jm3.app.miuix.ui.coui.component.CouiTopBarTitle
import com.jm3.app.miuix.ui.coui.component.couiTopAppBarColors
import com.jm3.app.miuix.ui.coui.theme.CouiTheme
import com.jm3.app.miuix.ui.readableWidth
import com.jm3.app.miuix.ui.route.AppRoutes
import com.jm3.core.download.DownloadStatus
import com.jm3.core.history.HistoryEntry
import com.jm3.core.model.Chapter
import com.jm3.core.model.PhotoPages
import com.jm3.core.runCatchingSuspending
import com.jm3.core.session.SessionOutcome
import com.jm3.core.settings.ReaderSettings
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.SnackbarHostState

/** 阅读器首载状态：加载、内容、失败三分支，失败再分「登录失效」与「可重试」两种结局。 */
private sealed interface ReaderUiState {
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

/**
 * 首载结局 → 阅读器失败态；成功返回 null，由调用方继续走内容分支。
 *
 * 失效与失败必须分开：前者只能去登录，后者才值得重试。
 */
private fun readerLoadFailure(outcome: SessionOutcome<*>): ReaderUiState.Failed? = when (outcome) {
    is SessionOutcome.Ok -> null
    SessionOutcome.Expired -> ReaderUiState.Failed(SessionOutcome.Expired.message, sessionExpired = true)
    is SessionOutcome.Failed -> ReaderUiState.Failed(failureCopy(outcome, "章节加载失败"))
}

@Composable
internal fun CouiReaderScreen(
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
    var loadJob by remember(photoId) { mutableStateOf<Job?>(null) }

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
                val resolvedPhoto = resolveCouiLoadedPhoto(
                    photo = photo,
                    savedAlbumId = saved?.record?.albumId,
                    savedTitle = saved?.record?.title,
                    detailTitle = detail?.title,
                )
                val sortedChapters = sortCouiReaderChapters(detail?.chapters.orEmpty())
                state = ReaderUiState.Loaded(
                    photo = resolvedPhoto,
                    chapters = sortedChapters,
                    chapterIndex = sortedChapters.indexOfFirst { it.id == resolvedPhoto.photoId },
                    favorited = detail?.favorited == true,
                    liked = detail?.liked == true,
                )
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
        // 首载也要先取消残留任务再登记 Job：失败重试与切页复用同一条取消链
        loadJob?.cancel()
        loadJob = scope.launch { loadContent() }
    }

    when (val s = state) {
        is ReaderUiState.Loading -> ReaderStateScreen(title = "阅读", onBack = onBack) {
            CouiLoadingGroup(
                modifier = Modifier.padding(top = 8.dp),
                text = "正在加载章节…",
            )
        }
        is ReaderUiState.Failed -> ReaderStateScreen(title = "阅读", onBack = onBack) {
            // 失效不给重试：重试必然再吃一次 401，只能去登录
            if (s.sessionExpired) {
                CouiFeedLoginGroup(message = s.message, onOpenLogin = nav::login)
            } else {
                CouiErrorGroup(
                    message = s.message,
                    onRetry = ::load,
                    modifier = Modifier.padding(top = 8.dp),
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
            CouiTheme {
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
}

/** 首载态外壳：加载、失败、登录失效共用一顶栏，只换正文。 */
@Composable
private fun ReaderStateScreen(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    CouiTheme {
        CouiAppScaffold(
            modifier = Modifier
                .fillMaxSize()
                .background(CouiKitTheme.colorScheme.background),
            topBar = {
                LargeTopAppBar(
                    title = { CouiTopBarTitle(text = title, textAlign = TextAlign.Start) },
                    navigationIcon = { CouiTopBarBackButton(onClick = onBack) },
                    colors = couiTopAppBarColors(),
                )
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .readableWidth()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding),
            ) {
                content()
            }
        }
    }
}