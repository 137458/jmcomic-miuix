package com.jm3.app.miuix.ui.shelf

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.data.download.DownloadManager
import com.jm3.app.miuix.ui.orDegrade
import com.jm3.core.download.DownloadAction
import com.jm3.core.download.DownloadRecord
import com.jm3.core.download.DownloadRow
import com.jm3.core.session.SessionOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/*
 * 书架页状态编排骨架（C4 双皮肤去重）。
 *
 * 抽出来的是行为：本机下载快照的首次读取、队列事件驱动的重复读取、首帧标记、
 * 封面域名整屏取一次、待确认删除项与本地剔除、行主操作的三个分支，
 * 以及「#车号 · 状态 进度」副标题与删除确认文案的口径。
 * 皮肤侧只留自己的列表控件、卡片样式与弹窗外观；本文件不引用任何 Miuix/COUI 控件。
 *
 * 两皮肤的真实差异用字段与参数表达，不强行统一：
 * - 首帧标记 [ShelfScaffoldState.loaded] 只有 ColorOS 侧读（先给加载态，避免空列表一闪），HyperOS 侧不看；
 * - 删除确认弹窗、进度条与栅格布局都属于视觉，留在各自皮肤。
 */

/** 书架页标题，两皮肤共用一份文案。 */
internal const val SHELF_TITLE = "本地书架"

/** 删除确认弹窗的标题与按钮文案。 */
internal const val SHELF_DELETE_TITLE = "删除下载"
internal const val SHELF_DELETE_CONFIRM = "删除"
internal const val SHELF_DELETE_DISMISS = "取消"

/** 车号：空串与 "-1" 都视为没有车号，此时只显示进度。 */
internal fun shelfAlbumId(record: DownloadRecord): String? =
    record.albumId.trim().takeIf { it.isNotEmpty() && it != "-1" }

/** 书架行副标题：有车号先给「#车号 · 」，后面跟状态文案与页码进度。 */
internal fun shelfSummary(record: DownloadRecord, row: DownloadRow, label: String): String {
    val progress = label + " " + row.donePages + "/" + row.total
    val albumId = shelfAlbumId(record) ?: return progress
    return "#" + albumId + " · " + progress
}

/** 删除确认正文：书名可能为空，退到作品编号。 */
internal fun shelfDeleteMessage(record: DownloadRecord): String =
    "确定要删除《" + record.title.ifBlank { record.photoId } + "》及已下载的本地文件吗？"

/**
 * 书架编排要的下载侧能力：读账本、起队列、订阅队列事件、暂停/续传、删除。
 *
 * 只开这一组方法，是为了让状态机能在纯 kotlin.test 里跑起来（假端口即可），
 * 不必构造真实的 [DownloadManager] 与其 Android 侧依赖。
 */
internal interface ShelfDownloadPort {
    /** 队列事件流：任一进度变化都触发书架重读账本。元素类型用不到，只当变更信号。 */
    val updates: Flow<Any?>

    fun ensureStarted()

    suspend fun records(): List<DownloadRecord>

    fun pause(photoId: String)

    fun resumeRecord(record: DownloadRecord): Boolean

    suspend fun remove(photoId: String)
}

/** 书架页状态：下载快照、首帧标记、封面域名与删除二次确认。 */
@Stable
internal class ShelfScaffoldState internal constructor(
    private val scope: CoroutineScope,
    private val downloads: ShelfDownloadPort,
    private val imageHostOutcome: suspend () -> SessionOutcome<String>,
) {
    /** 本机下载全量快照，队列事件驱动刷新。 */
    var records: List<DownloadRecord> by mutableStateOf(emptyList())
        private set

    /** 待确认删除的记录；null 表示没有待确认项。 */
    var pendingDeleteRecord: DownloadRecord? by mutableStateOf(null)
        private set

    /** 封面域名：整屏只取一次，取不到留空串。 */
    var imageHost: String by mutableStateOf("")
        private set

    /** 首帧是否已读到下载快照；只有 ColorOS 侧据此先给加载态。 */
    var loaded: Boolean by mutableStateOf(false)
        private set

    /** 进页：起队列 → 读一次账本 → 标记首帧完成 → 订阅队列事件持续重读。 */
    suspend fun enter() {
        downloads.ensureStarted()
        records = downloads.records()
        loaded = true
        downloads.updates.collect { records = downloads.records() }
    }

    /** 封面域名加载：失败与会话失效都退化为空串，书架照常显示。 */
    suspend fun loadImageHost() {
        imageHost = imageHostOutcome().orDegrade("")
    }

    /** 行主操作：只有 Open 需要皮肤跳阅读器，其余就地改队列状态。 */
    fun performPrimaryAction(record: DownloadRecord, row: DownloadRow): Boolean = when (row.action) {
        DownloadAction.Open -> true
        DownloadAction.Pause -> {
            downloads.pause(record.photoId)
            false
        }
        DownloadAction.Resume, DownloadAction.Retry -> {
            downloads.resumeRecord(record)
            false
        }
    }

    fun requestDelete(record: DownloadRecord) {
        pendingDeleteRecord = record
    }

    fun dismissDelete() {
        pendingDeleteRecord = null
    }

    /** 确认删除：先清待确认项，再落账并从本地快照剔除该行（与两皮肤原顺序一致）。 */
    fun confirmDelete() {
        val target = pendingDeleteRecord ?: return
        scope.launch {
            downloads.remove(target.photoId)
            records = records.filterNot { it.photoId == target.photoId }
        }
        pendingDeleteRecord = null
    }
}

/** 真实下载管理器 → 书架编排要的窄端口。 */
private fun DownloadManager.asShelfPort(): ShelfDownloadPort {
    val manager = this
    return object : ShelfDownloadPort {
        override val updates: Flow<Any?> get() = manager.queue.updates

        override fun ensureStarted() {
            manager.ensureStarted()
        }

        override suspend fun records(): List<DownloadRecord> = manager.records()

        override fun pause(photoId: String) {
            manager.pause(photoId)
        }

        override fun resumeRecord(record: DownloadRecord): Boolean = manager.resumeRecord(record)

        override suspend fun remove(photoId: String) {
            manager.remove(photoId)
        }
    }
}

/**
 * 书架页唯一入口：接 [ScreenDeps]，把两条编排（进页订阅队列、取封面域名）挂成效果。
 *
 * 皮肤不再自己 remember 这三个状态与两条 LaunchedEffect，只读 [ShelfScaffoldState] 的字段。
 */
@Composable
internal fun rememberShelfScaffoldState(deps: ScreenDeps): ShelfScaffoldState {
    val scope = rememberCoroutineScope()
    val state = remember(deps, scope) {
        ShelfScaffoldState(
            scope = scope,
            downloads = deps.downloadManager.asShelfPort(),
            imageHostOutcome = { deps.authCall.call { deps.images.imageHost() } },
        )
    }
    LaunchedEffect(state) { state.enter() }
    LaunchedEffect(state, deps.images, deps.authCall) { state.loadImageHost() }
    return state
}
