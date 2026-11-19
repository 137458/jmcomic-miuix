package com.jm3.app.miuix.ui.album

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import com.jm3.app.miuix.data.download.DownloadManager
import com.jm3.app.miuix.ui.AlbumDetailStateHolder
import com.jm3.app.miuix.ui.AlbumDetailUiSnapshot
import com.jm3.app.miuix.ui.AlbumDetailUiSnapshotSaver
import com.jm3.app.miuix.ui.AlbumDetailViewState
import com.jm3.app.miuix.ui.AlbumHeroHost
import com.jm3.app.miuix.ui.AlbumUiState
import com.jm3.app.miuix.ui.LocalAlbumHeroHost
import com.jm3.app.miuix.ui.SessionCache
import com.jm3.app.miuix.ui.copyTextToClipboard
import com.jm3.core.AlbumPreviewPlan
import com.jm3.core.ChapterLabels
import com.jm3.core.Jm3Gateway
import com.jm3.core.albumPreviewPlan
import com.jm3.core.asAlbumDetailPort
import com.jm3.core.history.HistoryRepository
import com.jm3.core.model.AlbumDetail
import com.jm3.core.model.Chapter
import com.jm3.core.model.PageImage
import com.jm3.core.model.PhotoPages
import com.jm3.core.session.AccountManager
import com.jm3.core.session.AuthCall
import com.jm3.core.model.Jm3User
import com.jm3.core.session.SessionOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.SnackbarHostState

/** 页首标题的末级回落：详情标题与转场带入的标题都不可用时显示它。 */
internal const val ALBUM_DETAIL_TITLE_FALLBACK = "详情"

/** 分享链接：域名还没就绪时返回 null，此时只分享标题。 */
internal fun albumShareLink(host: String?, albumId: String): String? =
    host?.takeIf { it.isNotBlank() }?.let { "https://" + it + "/album/" + albumId }

/** 分享正文：标题 + 可选链接。 */
internal fun albumShareText(title: String, host: String?, albumId: String): String {
    val link = albumShareLink(host, albumId)
    return buildString {
        append(title)
        if (link != null) append('\n').append(link)
    }
}

/** 页首标题：详情标题优先，其次转场带入的标题，最后回落 [ALBUM_DETAIL_TITLE_FALLBACK]。 */
internal fun albumDetailTitle(detailTitle: String?, fallbackTitle: String?): String =
    detailTitle?.takeIf { it.isNotBlank() }
        ?: fallbackTitle?.takeIf { it.isNotBlank() }
        ?: ALBUM_DETAIL_TITLE_FALLBACK

/** 续读位置：只有该话仍在当前章节树里才生效，避免旧记录把入口带到别的作品。 */
internal fun albumResumePhotoId(latestPhotoId: String?, detail: AlbumDetail?): String? =
    latestPhotoId?.takeIf { id -> detail?.chapters?.any { it.id == id } == true }

/** 预览块的首章：未登录或没有章节时不取预览，整块隐藏。 */
internal fun albumPreviewChapterId(loggedIn: Boolean, detail: AlbumDetail?): String? =
    if (loggedIn) detail?.chapters?.firstOrNull()?.id else null

/** 预览取图结果：失败一律当空，界面据此隐藏整块，不额外提示。 */
internal fun albumPreviewPages(outcome: SessionOutcome<PhotoPages>): List<PageImage> =
    (outcome as? SessionOutcome.Ok)?.value?.images.orEmpty()

/** 批量下载反馈：全部已在队列/已下载时如实说明没有新增。 */
internal fun albumBatchDownloadMessage(accepted: Int): String =
    if (accepted > 0) "已加入下载队列（$accepted 个章节）" else "这些章节已在下载队列或已下载"

/** 单话下载反馈。 */
internal fun albumChapterDownloadMessage(accepted: Boolean): String =
    if (accepted) "已加入下载队列" else "该章节已在下载队列或已下载"

/** 批量入队结果：[accepted] 是本次真正入队的章节数，[message] 是给用户的反馈文案。 */
internal data class AlbumDownloadOutcome(val accepted: Int, val message: String)

/**
 * 逐话入队并汇总结果。
 *
 * 空章节列表返回 null：这次点击不该弹任何提示。入队动作由调用方注入，
 * 测试可以换成假的入队器，不必碰真实下载队列。
 */
internal fun albumDownloadAll(
    albumId: String,
    chapters: List<Chapter>,
    enqueue: (photoId: String, albumId: String, title: String) -> Boolean,
): AlbumDownloadOutcome? {
    if (chapters.isEmpty()) return null
    var accepted = 0
    chapters.forEachIndexed { index, chapter ->
        val title = ChapterLabels.chapterTitle(chapter.title, index)
        if (enqueue(chapter.id, albumId, title)) accepted++
    }
    return AlbumDownloadOutcome(accepted, albumBatchDownloadMessage(accepted))
}

/** 收藏夹面板是否要在进页时重新打开：上次开着，且收藏夹还没加载过。 */
internal fun shouldRestoreFolderSheet(snapshot: AlbumDetailUiSnapshot, folders: Map<String, String>): Boolean =
    snapshot.showFolderSheet && folders.isEmpty()

/**
 * 相册详情页的共享状态编排：两套皮肤里逐字重复的那部分接线（快照读写、进页装载、
 * 预览取图、下载/复制/分享、收藏与关注动作）收在这里，皮肤只留视觉结构与 slot。
 *
 * 本文件不引用任何 Miuix/COUI 控件，也不渲染；[uiState] 等属性直读 Compose 状态，
 * 组合期读取照旧触发重组。
 */
@Stable
internal class AlbumDetailScaffoldState internal constructor(
    private val context: Context,
    private val scope: CoroutineScope,
    private val snackbarHostState: SnackbarHostState,
    private val downloadManager: DownloadManager,
    private val onOpenLogin: () -> Unit,
    /** 分享链接的域名来源：Miuix 走会话闸门读网关，COUI 直读当前网关，故由皮肤注入。 */
    private val shareHostRef: State<suspend () -> String?>,
    private val uiStateRef: State<AlbumDetailViewState>,
    private val userRef: State<Jm3User?>,
    private val previewPages: MutableState<List<PageImage>>,
    private val initialTitle: String?,
    private val initialCoverUrl: String?,
    private val heroHost: AlbumHeroHost?,
    /** 加载、收藏/喜欢/追踪、评论分页与章节展开态仍由状态持有者持有，皮肤直接取用。 */
    val stateHolder: AlbumDetailStateHolder,
    val albumId: String,
) {
    /** 当前页面状态。 */
    val uiState: AlbumDetailViewState get() = uiStateRef.value

    /** 当前登录用户；未登录为 null。 */
    val user: Jm3User? get() = userRef.value

    /** 详情装载成功后的作品；加载中与失败态为 null。 */
    val loadedDetail: AlbumDetail? get() = (uiState.albumState as? AlbumUiState.Loaded)?.detail

    /** 转场带入的标题；详情未就绪时页首用它兜底。 */
    val effectiveTitle: String?
        get() = initialTitle?.takeIf { it.isNotBlank() } ?: heroHost?.albumTitle(albumId)?.takeIf { it.isNotBlank() }

    /** 转场带入的封面；详情未就绪时页首用它兜底。 */
    val effectiveCoverUrl: String?
        get() = initialCoverUrl?.takeIf { it.isNotBlank() } ?: heroHost?.coverUrl(albumId)?.takeIf { it.isNotBlank() }

    /** 顶栏标题。 */
    val currentTitle: String get() = albumDetailTitle(loadedDetail?.title, effectiveTitle)

    /** 续读位置；不在当前章节树里时为 null。 */
    val resumeId: String? get() = albumResumePhotoId(uiState.latestPhotoId, loadedDetail)

    /** 预览块计划：未登录、无章节、取图失败都为 null，整块不渲染。 */
    val previewPlan: AlbumPreviewPlan?
        get() = loadedDetail?.let { albumPreviewPlan(it.chapters, previewPages.value) }

    /** 进页装载：详情、章节与首页评论由状态持有者统一编排。 */
    fun load(userUid: String?) = stateHolder.loadAll(userUid)

    /** 收藏入口：未登录先跳登录，已收藏走取消，未收藏开收藏夹面板。 */
    fun favorite() = stateHolder.onFavoriteClick(userRef.value != null, onOpenLogin)

    /** 喜欢；已喜欢过由状态持有者回提示。 */
    fun like() = stateHolder.like(userRef.value != null, onOpenLogin)

    /** 连载追踪开关。 */
    fun track() = stateHolder.toggleTracking(userRef.value != null, onOpenLogin)

    /** 批量下载：只有全部章节都已在队列/已下载时才提示“无新增”，否则报告本次实际入队数量。 */
    fun downloadAll(albumId: String, chapters: List<Chapter>) {
        val outcome = albumDownloadAll(albumId, chapters) { photoId, target, title ->
            downloadManager.enqueueChapter(photoId, target, title)
        } ?: return
        scope.launch { snackbarHostState.showSnackbar(outcome.message) }
    }

    /** 单话下载。 */
    fun downloadChapter(chapter: Chapter, albumId: String, title: String) {
        val accepted = downloadManager.enqueueChapter(chapter.id, albumId, title)
        scope.launch { snackbarHostState.showSnackbar(albumChapterDownloadMessage(accepted)) }
    }

    /** 复制到剪贴板并提示。 */
    fun copyText(label: String, value: String) =
        copyTextToClipboard(context, scope, snackbarHostState, label, value)

    /** 分享作品：域名取不到时只分享标题。 */
    fun share(detail: AlbumDetail) {
        scope.launch {
            val text = albumShareText(detail.title, shareHostRef.value(), detail.id)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            context.startActivity(Intent.createChooser(send, "分享作品"))
        }
    }
}

/**
 * 建立并记住详情页的共享状态骨架，同时完成页面级编排：
 * 快照保存、收藏夹面板恢复、进页装载、预览取图。
 */
@Composable
internal fun rememberAlbumDetailScaffoldState(
    albumId: String,
    initialTitle: String?,
    initialCoverUrl: String?,
    snackbarHostState: SnackbarHostState,
    gateway: Jm3Gateway,
    authCall: AuthCall,
    history: HistoryRepository,
    account: AccountManager,
    sessionCache: SessionCache,
    downloadManager: DownloadManager,
    onOpenLogin: () -> Unit,
    shareHost: suspend () -> String?,
): AlbumDetailScaffoldState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val heroHost = LocalAlbumHeroHost.current
    val userState = account.user.collectAsState()
    val shareHostRef = rememberUpdatedState(shareHost)
    val snapshotState = rememberSaveable(albumId, stateSaver = AlbumDetailUiSnapshotSaver) {
        mutableStateOf(AlbumDetailUiSnapshot())
    }
    val stateHolder = remember(albumId) {
        AlbumDetailStateHolder(
            albumId = albumId,
            gateway = gateway.asAlbumDetailPort(),
            authCall = authCall,
            history = history,
            scope = scope,
            showMessage = { message -> scope.launch { snackbarHostState.showSnackbar(message) } },
            onOpenLogin = onOpenLogin,
            engagement = sessionCache.engagement,
        ).apply { restoreUiSnapshot(snapshotState.value) }
    }
    val uiStateState = stateHolder.uiState.collectAsState()
    val commentDraftState = stateHolder.commentDraft.collectAsState()
    val uiState = uiStateState.value
    // 草稿、回复对象与展开态每变一次就写回快照，旋转或进程重建后原样恢复
    LaunchedEffect(
        albumId,
        commentDraftState.value,
        uiState.replyTarget,
        uiState.descriptionExpanded,
        uiState.expandedGroups,
        uiState.showFolderSheet,
    ) {
        snapshotState.value = stateHolder.saveUiSnapshot()
    }
    LaunchedEffect(albumId) {
        if (shouldRestoreFolderSheet(snapshotState.value, uiStateState.value.folders)) {
            stateHolder.openFolderSheet()
        }
    }
    // 登录态变化会重装详情；首屏装载交给状态持有者统一编排
    LaunchedEffect(albumId, userState.value?.uid) {
        stateHolder.loadAll(userState.value?.uid)
    }
    // 详情页预览：登录且详情就绪后取首章前几页；未登录、无章节、请求失败一律隐藏整块
    val previewPages = remember(albumId) { mutableStateOf<List<PageImage>>(emptyList()) }
    val previewChapterId = albumPreviewChapterId(
        loggedIn = userState.value != null,
        detail = (uiState.albumState as? AlbumUiState.Loaded)?.detail,
    )
    LaunchedEffect(albumId, userState.value?.uid, previewChapterId) {
        previewPages.value = emptyList()
        if (previewChapterId == null) return@LaunchedEffect
        previewPages.value = albumPreviewPages(authCall.call { gateway.photo(previewChapterId) })
    }
    return remember(albumId, stateHolder, initialTitle, initialCoverUrl, heroHost) {
        AlbumDetailScaffoldState(
            context = context,
            scope = scope,
            snackbarHostState = snackbarHostState,
            downloadManager = downloadManager,
            onOpenLogin = onOpenLogin,
            shareHostRef = shareHostRef,
            uiStateRef = uiStateState,
            userRef = userState,
            previewPages = previewPages,
            initialTitle = initialTitle,
            initialCoverUrl = initialCoverUrl,
            heroHost = heroHost,
            stateHolder = stateHolder,
            albumId = albumId,
        )
    }
}
