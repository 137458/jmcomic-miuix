package com.jm3.app.miuix.ui.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.jm3.app.miuix.failureCopy
import com.jm3.app.miuix.ui.RemovedSlice
import com.jm3.app.miuix.ui.removeMatching
import com.jm3.app.miuix.ui.restoreAt
import com.jm3.core.history.HistoryCursor
import com.jm3.core.history.HistoryEntry
import com.jm3.core.history.HistoryRepository
import com.jm3.core.paging.PageSlice
import com.jm3.core.paging.PagedFeed
import com.jm3.core.paging.pageLooksFull
import com.jm3.core.paging.pagerTargetPage
import com.jm3.core.runCatchingSuspending
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 阅读历史页的状态编排骨架：Miuix 列表版与 COUI 栅格版共用这一份，皮肤只留视觉结构。
 *
 * 仓库存取、在途守卫、迟到响应处置、删除撤销与清空的分支都在这里；页面把 Snackbar 通道、
 * 列表滚动复位与清空确认框的开合接进来即可。此文件不 import 任何 Miuix/COUI 控件。
 */

/** 每页条数：仓库按它分页，满页即代表还有下一页。 */
internal const val HISTORY_PAGE_SIZE: Int = 20

private const val HISTORY_LOAD_FAILED = "历史加载失败"
private const val HISTORY_DELETE_FAILED = "删除失败"
private const val HISTORY_CLEAR_FAILED = "清除失败"
private const val HISTORY_UNDO_FAILED = "撤销失败"

/** 删除成功后的提示文案与撤销动作标签：两皮肤一致，Snackbar 通道由页面提供。 */
internal const val HISTORY_DELETED_MESSAGE: String = "已删除阅读记录"
internal const val HISTORY_UNDO_ACTION: String = "撤销"

internal sealed interface HistoryListState {
    data object Loading : HistoryListState

    data object Loaded : HistoryListState

    data class Failed(val message: String) : HistoryListState
}

/** 按作品聚合的阅读历史：最近一章用于一键续读，[chapters] 为该作品已读章节数。 */
internal data class HistoryAlbum(
    val albumId: String,
    val latest: HistoryEntry,
    val chapters: Int,
)

/** 保持最近阅读时间倒序，把同一作品的记录折叠成一条。 */
internal fun List<HistoryEntry>.groupByAlbum(): List<HistoryAlbum> {
    val order = mutableListOf<String>()
    val latest = mutableMapOf<String, HistoryEntry>()
    val counts = mutableMapOf<String, Int>()
    for (entry in this) {
        if (entry.albumId !in latest) {
            latest[entry.albumId] = entry
            order += entry.albumId
        }
        counts[entry.albumId] = (counts[entry.albumId] ?: 0) + 1
    }
    return order.map { HistoryAlbum(it, latest.getValue(it), counts.getValue(it)) }
}

/**
 * 两皮肤历史页在「迟到响应」与「清空失败」上的既有差异，用一个开关如实保留，不强行统一。
 *
 * - [DropLateResponses]：每轮装载递增请求令牌，令牌变了的响应整份丢弃；落点按最新列表
 *   （续页追加到当前列表、删除按最新列表重算移除切片、撤销也按令牌门控）；
 *   清空失败保留列表并提示「清除失败」。
 * - [ApplyAsRequested]：不校验令牌，迟到的响应照常落点并追加到发起请求时的快照；
 *   清空不看结果，一律回到空内容态（也不提示）。
 */
internal enum class HistoryResponseHandling {
    DropLateResponses,
    ApplyAsRequested,
}

/**
 * 历史页的可变状态与动作。
 *
 * 依赖全部由构造器注入：仓库、协程作用域、[showMessage]/[showUndoPrompt] 两个提示通道、
 * 跳页成功后的滚动复位，以及清空确认框的开合状态（Compose 侧用 rememberSaveable 建，
 * 单测直接给一份普通 state）。
 */
@Stable
internal class HistoryScaffoldState internal constructor(
    private val repository: HistoryRepository,
    private val scope: CoroutineScope,
    private val responseHandling: HistoryResponseHandling,
    /** 单条提示通道（Snackbar）；挂起直到提示消失，与原地串行的事件顺序一致。 */
    private val showMessage: suspend (String) -> Unit,
    /** 删除成功后的「已删除阅读记录 / 撤销」通道：返回 true 表示用户点了撤销。 */
    private val showUndoPrompt: suspend () -> Boolean,
    /** 跳页成功后的滚动复位：列表版滚 LazyListState，栅格版滚 LazyGridState。 */
    private val scrollToTop: suspend () -> Unit,
    private val clearConfirm: MutableState<Boolean> = mutableStateOf(false),
) {
    // 在途、失败、是否还有下一页都由 PagedFeed 记
    var feed by mutableStateOf(PagedFeed<HistoryEntry>())
        private set

    var state by mutableStateOf<HistoryListState>(HistoryListState.Loading)
        private set

    // 翻页在途守卫：仓库读取期间禁用翻页条，避免连点重复请求
    var pageBusy by mutableStateOf(false)
        private set

    var clearing by mutableStateOf(false)
        private set

    /** 清空确认框是否打开。 */
    val showClearConfirm: Boolean get() = clearConfirm.value

    /** 请求令牌：只用来把迟到响应挡在门外（[HistoryResponseHandling.DropLateResponses]）。 */
    private var requestToken = 0

    private val guardsStaleResponses: Boolean
        get() = responseHandling == HistoryResponseHandling.DropLateResponses

    private fun nextToken(): Int {
        requestToken += 1
        return requestToken
    }

    private fun isStale(token: Int): Boolean = guardsStaleResponses && token != requestToken

    fun requestClearConfirm() {
        clearConfirm.value = true
    }

    fun dismissClearConfirm() {
        clearConfirm.value = false
    }

    fun loadFirstPage() {
        state = HistoryListState.Loading
        val token = nextToken()
        // 令牌变了以后在途翻页的 finally 不会再复位它，这里直接清掉，避免翻页条一直禁用
        if (guardsStaleResponses) pageBusy = false
        scope.launch {
            val outcome = runCatchingSuspending { repository.page(1, HISTORY_PAGE_SIZE) }
            if (isStale(token)) return@launch
            outcome
                .onSuccess { entries ->
                    feed = PagedFeed<HistoryEntry>().replace(
                        PageSlice(
                            items = entries,
                            hasMore = pageLooksFull(entries.size, HISTORY_PAGE_SIZE),
                            page = 1,
                        ),
                    ) { entry -> entry.photoId }
                    state = HistoryListState.Loaded
                }
                .onFailure { state = HistoryListState.Failed(failureCopy(it, HISTORY_LOAD_FAILED)) }
        }
    }

    /** 点击分页：仓库按固定页大小切片，整页替换后回到列表顶部。 */
    fun jumpToPage(target: Int) {
        if (pageBusy) return
        val resolved = pagerTargetPage(feed.page, target, feed.pageCount)
        if (resolved == feed.page) return
        pageBusy = true
        val token = nextToken()
        scope.launch {
            try {
                val outcome = runCatchingSuspending { repository.page(resolved, HISTORY_PAGE_SIZE) }
                if (isStale(token)) return@launch
                outcome
                    .onSuccess { entries ->
                        feed = PagedFeed<HistoryEntry>().replace(
                            PageSlice(
                                items = entries,
                                hasMore = pageLooksFull(entries.size, HISTORY_PAGE_SIZE),
                                page = resolved,
                            ),
                        ) { entry -> entry.photoId }
                        state = HistoryListState.Loaded
                        scrollToTop()
                    }
                    .onFailure { state = HistoryListState.Failed(failureCopy(it, HISTORY_LOAD_FAILED)) }
            } finally {
                if (!isStale(token)) pageBusy = false
            }
        }
    }

    fun loadMore() {
        if (state != HistoryListState.Loaded) return
        val requested = feed.beginLoadMore()
        if (requested === feed) return
        val page = requested.page + 1
        // 续页用游标：列表上方随时可能因为读者自己的动作而多出一条，按页码算 OFFSET 会重复或漏条。
        val cursor = requested.items.lastOrNull()?.let { HistoryCursor(updatedAt = it.updatedAt, photoId = it.photoId) }
        val token = requestToken
        feed = requested
        scope.launch {
            val outcome = runCatchingSuspending { repository.pageAfter(cursor, HISTORY_PAGE_SIZE) }
            if (isStale(token)) return@launch
            // 守卫模式落到最新列表；无守卫模式落到发起时的快照，与各自的既有行为一致
            val base = if (guardsStaleResponses) feed else requested
            outcome
                .onSuccess { next ->
                    feed = base.append(
                        PageSlice(
                            items = next,
                            hasMore = pageLooksFull(next.size, HISTORY_PAGE_SIZE),
                            page = page,
                        ),
                    ) { entry -> entry.photoId }
                }
                .onFailure { feed = base.failLoadMore() }
        }
    }

    /** 删除整部作品的阅读记录：聚合行删除后按 albumId 从列表移除，并提供撤销入口。 */
    fun removeAlbum(albumId: String) {
        val initialSlice = feed.items.removeMatching { it.albumId == albumId }
        if (initialSlice.removed.isEmpty()) return
        val token = requestToken
        scope.launch {
            val deleteOutcome = runCatchingSuspending { repository.deleteAlbum(albumId) }
            if (deleteOutcome.isFailure) {
                showMessage(HISTORY_DELETE_FAILED)
                return@launch
            }
            if (isStale(token)) return@launch
            val removed: List<HistoryEntry>
            val insertIndex: Int
            if (guardsStaleResponses) {
                val applied = removeAlbumFromFeed(feed, albumId)
                removed = applied.slice.removed.ifEmpty { initialSlice.removed }
                insertIndex = if (applied.slice.removed.isNotEmpty()) {
                    applied.slice.insertIndex
                } else {
                    initialSlice.insertIndex
                }
                feed = applied.feed
            } else {
                removed = initialSlice.removed
                insertIndex = initialSlice.insertIndex
                feed = feed.copy(items = initialSlice.remaining)
            }
            if (!showUndoPrompt()) return@launch
            if (isStale(token)) return@launch
            val restoreOutcome = runCatchingSuspending {
                removed.forEach { repository.upsert(it) }
            }
            if (restoreOutcome.isFailure) {
                showMessage(HISTORY_UNDO_FAILED)
                return@launch
            }
            val restored = if (guardsStaleResponses) {
                restoreAlbumToFeed(
                    latestFeed = feed,
                    removed = removed,
                    insertIndex = insertIndex,
                    requestTokenAtDelete = token,
                    currentRequestToken = requestToken,
                )
            } else {
                feed.copy(items = feed.items.restoreAt(removed, insertIndex) { it.photoId })
            }
            if (restored != null) {
                feed = restored
            }
        }
    }

    fun clearAll() {
        if (clearing) return
        clearing = true
        val guarded = guardsStaleResponses
        val token = if (guarded) nextToken() else requestToken
        if (guarded) pageBusy = false
        scope.launch {
            val outcome = try {
                runCatchingSuspending { repository.clear() }
            } finally {
                clearing = false
            }
            dismissClearConfirm()
            if (!guarded) {
                feed = PagedFeed()
                state = HistoryListState.Loaded
                return@launch
            }
            val resolution = resolveClearAllOutcome(
                currentFeed = feed,
                currentState = state,
                outcome = outcome,
                requestTokenAtClear = token,
                currentRequestToken = requestToken,
            )
            feed = resolution.feed
            state = resolution.state
            if (resolution.showFailureSnackbar) {
                showMessage(HISTORY_CLEAR_FAILED)
            }
        }
    }
}

/** 建一份与页面同寿的历史骨架状态，并在首帧装第一页。 */
@Composable
internal fun rememberHistoryScaffoldState(
    repository: HistoryRepository,
    responseHandling: HistoryResponseHandling,
    showMessage: suspend (String) -> Unit,
    showUndoPrompt: suspend () -> Boolean,
    scrollToTop: suspend () -> Unit,
): HistoryScaffoldState {
    val scope = rememberCoroutineScope()
    val clearConfirm = rememberSaveable { mutableStateOf(false) }
    // 提示通道与滚动复位每次重组都是新 lambda：取值时再读最新的，避免骨架抓住旧闭包
    val message = rememberUpdatedState(showMessage)
    val undoPrompt = rememberUpdatedState(showUndoPrompt)
    val scrollTop = rememberUpdatedState(scrollToTop)
    val state = remember(repository, responseHandling, scope, clearConfirm) {
        HistoryScaffoldState(
            repository = repository,
            scope = scope,
            responseHandling = responseHandling,
            showMessage = { text -> message.value(text) },
            showUndoPrompt = { undoPrompt.value() },
            scrollToTop = { scrollTop.value() },
            clearConfirm = clearConfirm,
        )
    }
    LaunchedEffect(state) {
        state.loadFirstPage()
    }
    return state
}

internal data class HistoryRemoveResult(
    val feed: PagedFeed<HistoryEntry>,
    val slice: RemovedSlice<HistoryEntry>,
)

internal fun removeAlbumFromFeed(
    latestFeed: PagedFeed<HistoryEntry>,
    albumId: String,
): HistoryRemoveResult {
    val slice = latestFeed.items.removeMatching { it.albumId == albumId }
    return HistoryRemoveResult(
        feed = latestFeed.copy(items = slice.remaining),
        slice = slice,
    )
}

internal fun restoreAlbumToFeed(
    latestFeed: PagedFeed<HistoryEntry>,
    removed: List<HistoryEntry>,
    insertIndex: Int,
    requestTokenAtDelete: Int,
    currentRequestToken: Int,
): PagedFeed<HistoryEntry>? {
    if (requestTokenAtDelete != currentRequestToken) return null
    return latestFeed.copy(
        items = latestFeed.items.restoreAt(removed, insertIndex) { it.photoId },
    )
}

internal data class ClearAllOutcome(
    val feed: PagedFeed<HistoryEntry>,
    val state: HistoryListState,
    val showFailureSnackbar: Boolean,
)

internal fun resolveClearAllOutcome(
    currentFeed: PagedFeed<HistoryEntry>,
    currentState: HistoryListState,
    outcome: Result<Unit>,
    requestTokenAtClear: Int,
    currentRequestToken: Int,
): ClearAllOutcome = if (outcome.isSuccess) {
    if (requestTokenAtClear == currentRequestToken) {
        ClearAllOutcome(
            feed = PagedFeed(),
            state = HistoryListState.Loaded,
            showFailureSnackbar = false,
        )
    } else {
        ClearAllOutcome(
            feed = currentFeed,
            state = currentState,
            showFailureSnackbar = false,
        )
    }
} else {
    ClearAllOutcome(
        feed = currentFeed,
        state = currentState,
        showFailureSnackbar = true,
    )
}
