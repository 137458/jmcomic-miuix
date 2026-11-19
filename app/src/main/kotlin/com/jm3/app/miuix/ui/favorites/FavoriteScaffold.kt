// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.favorites

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.failureCopy
import com.jm3.app.miuix.ui.IntStringPairNullableSaver
import com.jm3.app.miuix.ui.list.SessionListLoader
import com.jm3.core.model.FavoriteFolderAction
import com.jm3.core.model.FavoritePage
import com.jm3.core.model.FolderOperation
import com.jm3.core.model.SearchPage
import com.jm3.core.model.SearchResult
import com.jm3.core.model.TagFavorite
import com.jm3.core.paging.PageSlice
import com.jm3.core.session.SessionOutcome
import com.jm3.core.toSlice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 收藏页（Miuix 与 COUI 两块皮肤）的状态编排唯一真源。
 *
 * 两块皮肤此前各写了一份逐字相同的状态：分段与当前收藏夹、收藏夹名册、新建/重命名/删除弹窗、
 * 移动到收藏夹、标签增删的动作态与失败态，以及四个网络动作对 SessionOutcome 的同一套分支。
 * 这里把行为收口到一处：皮肤侧只留各自的控件、弹窗外观与 slot，以及逐字差异（失败文案策略、
 * 刷新失败提示语、一页切片口径、标签列表的加载方式）作为参数注入。
 *
 * 骨架不 import 任何 Miuix/COUI 控件：文案与判定是纯函数，状态转移是纯数据类，
 * 需要挂副作用的动作方法只在 [FavoriteScaffoldState] 里出现，编排入口是
 * [rememberFavoriteScaffoldState]。
 */

// ── 文案与阈值 ────────────────────────────────────────────────────────────

internal const val FAVORITE_TAB_FOLDERS = 0
internal const val FAVORITE_TAB_TAGS = 1
internal val FAVORITE_TAB_TITLES: List<String> = listOf("收藏夹", "标签收藏")

internal const val FAVORITE_FOLDER_ALL_ID = 0
internal const val FAVORITE_FOLDER_ALL_NAME = "全部"

/** 收藏夹名与标签名共用同一个字数上限（两皮肤都在输入框里 take(20)）。 */
internal const val FAVORITE_NAME_LIMIT = 20
internal const val FAVORITE_FOLDER_NAME_LABEL = "收藏夹名称（最多 20 字）"
internal const val FAVORITE_TAG_NAME_LABEL = "标签名称（最多 20 字）"

internal const val FAVORITE_FEED_FAILURE = "收藏加载失败"

/** Miuix 皮肤的刷新失败提示语；COUI 用 [SessionListLoader] 的默认值。 */
internal const val FAVORITE_STALE_PREFIX = "刷新失败："
internal const val FAVORITE_STALE_PREFIX_DEFAULT = "加载失败："

/** COUI 皮肤的失败文案后缀：兜底文案 + 这一句。 */
internal const val FAVORITE_FAILURE_SUFFIX = "，请稍后重试"

internal const val FAVORITE_ACTION_FALLBACK = "操作失败"
internal const val FAVORITE_MOVE_FALLBACK = "移动失败"
internal const val FAVORITE_ADD_TAG_FALLBACK = "添加标签失败"
internal const val FAVORITE_DELETE_TAG_FALLBACK = "删除标签失败"

internal const val FAVORITE_FOLDER_NAME_BLANK = "名称不能为空"
internal const val FAVORITE_TAG_BLANK = "标签不能为空"
internal const val FAVORITE_TAG_DUPLICATE = "该标签已收藏"
internal const val FAVORITE_LOGIN_FIRST = "请先登录后再添加标签"

internal const val FAVORITE_ADD_FOLDER_TITLE = "新建收藏夹"
internal const val FAVORITE_EDIT_FOLDER_TITLE = "重命名收藏夹"
internal const val FAVORITE_DELETE_FOLDER_TITLE = "删除收藏夹"
internal const val FAVORITE_MOVE_TITLE = "移动到收藏夹"
internal const val FAVORITE_MOVE_EMPTY_HINT = "暂无其他收藏夹，请先新建收藏夹"
internal const val FAVORITE_ADD_TAG_TITLE = "添加标签"
internal const val FAVORITE_DELETE_TAG_TITLE = "删除标签"

internal const val FAVORITE_ADD_ACTION_LABEL = "新建收藏夹"
internal const val FAVORITE_TAG_ACTION_LABEL = "添加标签"
internal const val FAVORITE_CREATE_CONFIRM = "新建"
internal const val FAVORITE_EDIT_CONFIRM = "保存"
internal const val FAVORITE_DELETE_CONFIRM = "确认删除"
internal const val FAVORITE_ADD_CONFIRM = "添加"
internal const val FAVORITE_CANCEL_LABEL = "取消"
internal const val FAVORITE_CLOSE_LABEL = "关闭"

internal fun favoriteAddActionLabel(tabIndex: Int): String =
    if (tabIndex == FAVORITE_TAB_FOLDERS) FAVORITE_ADD_ACTION_LABEL else FAVORITE_TAG_ACTION_LABEL

internal fun favoriteDeleteFolderMessage(folderName: String): String =
    "将删除收藏夹「" + folderName + "」，夹内作品不会取消收藏。"

internal fun favoriteDeleteTagMessage(tag: String): String = "将取消收藏标签「#" + tag + "」。"

internal fun favoriteMovedMessage(folderName: String): String = "已移动到「" + folderName + "」"

internal fun favoriteTagAddedMessage(tag: String): String = "已添加标签 #" + tag

internal fun favoriteTagDeletedMessage(tag: String): String = "已删除标签 #" + tag

// ── 失败文案策略（两皮肤唯一的文案差异） ───────────────────────────────────

/** 失败结局 → 弹窗内文案。两皮肤各注入一份，动作分支只认这个函数。 */
internal typealias FavoriteFailureText = (SessionOutcome.Failed, String) -> String

/** Miuix 皮肤：按异常归类产出可读文案，未知异常回落到各自兜底。 */
internal fun favoriteDetailFailure(failure: SessionOutcome.Failed, fallback: String): String =
    failureCopy(failure, fallback)

/** COUI 皮肤：不看异常类型，固定「兜底 + 请稍后重试」。 */
@Suppress("UNUSED_PARAMETER")
internal fun favoriteFixedFailure(failure: SessionOutcome.Failed, fallback: String): String =
    fallback + FAVORITE_FAILURE_SUFFIX

// ── 一页切片策略（两皮肤的退化口径差异） ──────────────────────────────────

/** Miuix 皮肤：手建切片，接口没给总页数时 pageCount 记 null。 */
internal fun favoritePageSlice(next: FavoritePage): PageSlice<SearchResult> =
    PageSlice(
        items = next.items,
        hasMore = next.currentPage < next.totalPages,
        page = next.currentPage,
        pageCount = next.totalPages.takeIf { it > 0 },
    )

/** COUI 皮肤：走 [SearchPage.toSlice]，pageCount 直接取接口给的 totalPages（含 0）。 */
internal fun favoritePageSliceKeepingPageCount(next: FavoritePage): PageSlice<SearchResult> =
    SearchPage(next.currentPage, next.totalPages, next.items).toSlice()

// ── 收藏夹名册 ────────────────────────────────────────────────────────────

internal data class FavoriteFolderOption(val id: Int, val name: String)

/** 名册 → 下拉项：首项固定「全部」，其余按 id 升序，解析不出 id 的脏键丢弃。 */
internal fun favoriteFolderOptions(folders: Map<String, String>): List<FavoriteFolderOption> = buildList {
    add(FavoriteFolderOption(FAVORITE_FOLDER_ALL_ID, FAVORITE_FOLDER_ALL_NAME))
    folders.entries
        .mapNotNull { (key, name) -> key.toIntOrNull()?.let { FavoriteFolderOption(it, name) } }
        .filter { it.id != FAVORITE_FOLDER_ALL_ID }
        .sortedBy { it.id }
        .forEach { add(it) }
}

internal fun favoriteCurrentFolderName(options: List<FavoriteFolderOption>, folderId: Int): String =
    options.firstOrNull { it.id == folderId }?.name ?: FAVORITE_FOLDER_ALL_NAME

/** 「移动到收藏夹」的可选项：「全部」是聚合视图，不能当目标。 */
internal fun favoriteMovableFolders(options: List<FavoriteFolderOption>): List<FavoriteFolderOption> =
    options.filter { it.id != FAVORITE_FOLDER_ALL_ID }

/** 换上一页带回的名册：空名册不覆盖已有映射（两皮肤旧实现都这么写）。 */
internal fun favoriteFoldersAfter(current: Map<String, String>, next: Map<String, String>): Map<String, String> =
    if (next.isNotEmpty()) next else current

// ── 动作态 ────────────────────────────────────────────────────────────────

/** 一次网络动作的进行中/失败态：收藏夹、移动、标签三个动作各持一份。 */
internal data class FavoriteRequestState(
    val busy: Boolean = false,
    val errorMessage: String? = null,
) {
    val idle: Boolean get() = !busy

    /** 发起前：已经在跑就不再发第二次（旧实现是 `if (busy) return`）。 */
    fun started(): FavoriteRequestState = if (busy) this else copy(busy = true, errorMessage = null)

    /** 只清错误，不动进行中标记。 */
    fun cleared(): FavoriteRequestState = if (errorMessage == null) this else copy(errorMessage = null)

    /** 失败：解锁并留下文案（旧实现在回调里先解锁、再写错误）。 */
    fun failed(message: String): FavoriteRequestState = copy(busy = false, errorMessage = message)

    /** 结束请求但不写文案（成功分支与「失败文案单独给」的分支都从这里过）。 */
    fun finished(): FavoriteRequestState = if (!busy) this else copy(busy = false)
}

/** 列表动作的后续：要不要重拉、重拉哪一档。 */
internal enum class FavoriteFeedReload { None, FirstPage, Refresh }

// ── 收藏夹增删改 ──────────────────────────────────────────────────────────

/**
 * 收藏夹弹窗与动作态：新建/重命名共用一层，删除单独一层；
 * 三个弹窗互斥（打开一个就要关掉另一个），因此关窗统一走 [dismissed]。
 */
internal data class FavoriteFolderOps(
    val showCreate: Boolean = false,
    val editTarget: Pair<Int, String>? = null,
    val deleteTarget: Pair<Int, String>? = null,
    val nameInput: String = "",
    val request: FavoriteRequestState = FavoriteRequestState(),
) {
    val dialogVisible: Boolean get() = showCreate || editTarget != null
    val deleteVisible: Boolean get() = deleteTarget != null
    val dialogTitle: String get() = if (showCreate) FAVORITE_ADD_FOLDER_TITLE else FAVORITE_EDIT_FOLDER_TITLE
    val confirmLabel: String get() = if (showCreate) FAVORITE_CREATE_CONFIRM else FAVORITE_EDIT_CONFIRM
    val busy: Boolean get() = request.busy
    val errorMessage: String? get() = request.errorMessage

    /** COUI 的弹窗退场动画要记住模式：true=新建，false=重命名。 */
    val dialogMode: Pair<Boolean, Int>?
        get() = when {
            showCreate -> true to 0
            editTarget != null -> false to editTarget.first
            else -> null
        }

    fun creating(): FavoriteFolderOps =
        copy(showCreate = true, editTarget = null, nameInput = "", request = request.cleared())

    fun renaming(folderId: Int, currentName: String): FavoriteFolderOps =
        copy(showCreate = false, editTarget = folderId to currentName, nameInput = currentName, request = request.cleared())

    fun confirmingDelete(folderId: Int, currentName: String): FavoriteFolderOps =
        copy(deleteTarget = folderId to currentName)

    fun named(value: String): FavoriteFolderOps = copy(nameInput = value.take(FAVORITE_NAME_LIMIT))

    /** 关窗：只清可见性与错误；输入框内容和旧实现一样留着。 */
    fun dismissed(): FavoriteFolderOps =
        copy(showCreate = false, editTarget = null, deleteTarget = null, request = request.cleared())

    /** 提交新建/重命名：名称为空只写回弹窗错误，不发请求。 */
    fun submitRequest(): FavoriteFolderSubmit {
        if (request.busy) return FavoriteFolderSubmit(this)
        val name = nameInput.trim()
        if (name.isEmpty()) return FavoriteFolderSubmit(copy(request = request.failed(FAVORITE_FOLDER_NAME_BLANK)))
        if (showCreate) {
            return FavoriteFolderSubmit(copy(request = request.started()), FavoriteFolderAction.ADD, "", name)
        }
        val editing = editTarget ?: return FavoriteFolderSubmit(this)
        return FavoriteFolderSubmit(copy(request = request.started()), FavoriteFolderAction.EDIT, editing.first.toString(), name)
    }

    /** 提交删除：目标来自打开弹窗时记下的收藏夹。 */
    fun deleteRequest(): FavoriteFolderSubmit {
        if (request.busy) return FavoriteFolderSubmit(this)
        val target = deleteTarget ?: return FavoriteFolderSubmit(this)
        return FavoriteFolderSubmit(copy(request = request.started()), FavoriteFolderAction.DELETE, target.first.toString(), "")
    }

    fun failed(message: String): FavoriteFolderOps = copy(request = request.failed(message))

    fun finished(): FavoriteFolderOps = copy(request = request.finished())

    /**
     * 成功：关掉全部弹窗并清输入框；删掉当前所在收藏夹时回落到「全部」，
     * folderId 一变 loader 就会重建重拉，没有变化才显式重拉第一页。
     */
    fun succeeded(action: FavoriteFolderAction, targetId: String, currentFolderId: Int): FavoriteFolderEffect {
        val resetFolder = action == FavoriteFolderAction.DELETE && targetId.toIntOrNull() == currentFolderId
        return FavoriteFolderEffect(
            ops = FavoriteFolderOps(request = request.finished()),
            resetFolder = resetFolder,
            feedReload = if (resetFolder) FavoriteFeedReload.None else FavoriteFeedReload.FirstPage,
        )
    }
}

/** 待发起的收藏夹动作；[action] 为空表示这一下不该发请求（名称为空/缺目标/正在忙）。 */
internal data class FavoriteFolderSubmit(
    val ops: FavoriteFolderOps,
    val action: FavoriteFolderAction? = null,
    val folderId: String = "",
    val name: String = "",
)

internal data class FavoriteFolderEffect(
    val ops: FavoriteFolderOps,
    val resetFolder: Boolean = false,
    val feedReload: FavoriteFeedReload = FavoriteFeedReload.None,
    val notice: String? = null,
    val loginRequired: Boolean = false,
)

/** 收藏夹动作结局 → 状态转移。皮肤差异只经由 [failureText] 进来。 */
internal fun FavoriteFolderOps.reduce(
    action: FavoriteFolderAction,
    targetId: String,
    currentFolderId: Int,
    outcome: SessionOutcome<FolderOperation>,
    failureText: FavoriteFailureText,
): FavoriteFolderEffect = when (outcome) {
    is SessionOutcome.Ok -> {
        val operation = outcome.value
        if (operation.ok) {
            succeeded(action, targetId, currentFolderId)
        } else {
            FavoriteFolderEffect(failed(operation.message ?: FAVORITE_ACTION_FALLBACK))
        }
    }

    // 账号态操作失效：解锁、提示一次、直接给登录入口，弹窗内容保持原样
    SessionOutcome.Expired -> FavoriteFolderEffect(
        ops = finished(),
        notice = SessionOutcome.Expired.message,
        loginRequired = true,
    )

    is SessionOutcome.Failed -> FavoriteFolderEffect(failed(failureText(outcome, FAVORITE_ACTION_FALLBACK)))
}

// ── 移动到收藏夹 ──────────────────────────────────────────────────────────

internal data class FavoriteMoveOps(
    val target: SearchResult? = null,
    val request: FavoriteRequestState = FavoriteRequestState(),
) {
    val visible: Boolean get() = target != null
    val busy: Boolean get() = request.busy
    val errorMessage: String? get() = request.errorMessage

    fun opening(result: SearchResult): FavoriteMoveOps = copy(target = result, request = request.cleared())

    /** 关层：进行中不关（旧实现同样挡着）。 */
    fun dismissing(): FavoriteMoveOps = if (request.busy) this else copy(target = null, request = request.cleared())

    /** 选好目标夹：作品 id 取自当前选择层里的那一条。 */
    fun moveRequest(folderId: Int, folderName: String): FavoriteMoveSubmit? {
        if (request.busy) return null
        val moving = target ?: return null
        return FavoriteMoveSubmit(copy(request = request.started()), moving.id, folderName, folderId)
    }

    fun failed(message: String): FavoriteMoveOps = copy(request = request.failed(message))

    fun finished(): FavoriteMoveOps = copy(request = request.finished())
}

internal data class FavoriteMoveSubmit(
    val ops: FavoriteMoveOps,
    val albumId: String,
    val folderName: String,
    val targetFolderId: Int,
)

internal data class FavoriteMoveEffect(
    val ops: FavoriteMoveOps,
    val feedReload: FavoriteFeedReload = FavoriteFeedReload.None,
    val notice: String? = null,
    val loginRequired: Boolean = false,
)

internal fun FavoriteMoveOps.reduce(
    folderName: String,
    outcome: SessionOutcome<FolderOperation>,
    failureText: FavoriteFailureText,
): FavoriteMoveEffect = when (outcome) {
    is SessionOutcome.Ok -> {
        val operation = outcome.value
        if (operation.ok) {
            FavoriteMoveEffect(
                ops = copy(target = null, request = request.finished()),
                feedReload = FavoriteFeedReload.Refresh,
                notice = favoriteMovedMessage(folderName),
            )
        } else {
            FavoriteMoveEffect(failed(operation.message ?: FAVORITE_MOVE_FALLBACK))
        }
    }

    SessionOutcome.Expired -> FavoriteMoveEffect(
        ops = finished(),
        notice = SessionOutcome.Expired.message,
        loginRequired = true,
    )

    is SessionOutcome.Failed -> FavoriteMoveEffect(failed(failureText(outcome, FAVORITE_MOVE_FALLBACK)))
}

// ── 标签收藏 ──────────────────────────────────────────────────────────────

internal data class FavoriteTagOps(
    val showAdd: Boolean = false,
    val input: String = "",
    val deleteTarget: String? = null,
    val request: FavoriteRequestState = FavoriteRequestState(),
) {
    val addVisible: Boolean get() = showAdd
    val deleteVisible: Boolean get() = deleteTarget != null
    val busy: Boolean get() = request.busy
    val errorMessage: String? get() = request.errorMessage

    fun adding(): FavoriteTagOps = copy(showAdd = true, input = "", request = request.cleared())

    fun named(value: String): FavoriteTagOps = copy(input = value.take(FAVORITE_NAME_LIMIT))

    fun confirmingDelete(tag: String): FavoriteTagOps = copy(deleteTarget = tag, request = request.cleared())

    fun dismissingAdd(): FavoriteTagOps = if (request.busy) this else copy(showAdd = false, request = request.cleared())

    fun dismissingDelete(): FavoriteTagOps = if (request.busy) this else copy(deleteTarget = null, request = request.cleared())

    /** 添加：空值与重复只做轻提示，不动弹窗里的错误行。 */
    fun addRequest(existing: List<TagFavorite>): FavoriteTagSubmit {
        val tag = input.trim()
        if (tag.isEmpty()) return FavoriteTagSubmit(this, notice = FAVORITE_TAG_BLANK)
        if (existing.any { it.tag == tag }) return FavoriteTagSubmit(this, notice = FAVORITE_TAG_DUPLICATE)
        if (request.busy) return FavoriteTagSubmit(this)
        return FavoriteTagSubmit(copy(request = request.started()), tag = tag)
    }

    fun deleteRequest(): FavoriteTagSubmit {
        if (request.busy) return FavoriteTagSubmit(this)
        val tag = deleteTarget ?: return FavoriteTagSubmit(this)
        return FavoriteTagSubmit(copy(request = request.started()), tag = tag)
    }

    fun failed(message: String): FavoriteTagOps = copy(request = request.failed(message))

    fun finished(): FavoriteTagOps = copy(request = request.finished())
}

/** 待发起的标签动作；[tag] 为空表示只该弹轻提示（[notice]）。 */
internal data class FavoriteTagSubmit(
    val ops: FavoriteTagOps,
    val tag: String? = null,
    val notice: String? = null,
)

internal data class FavoriteTagEffect(
    val ops: FavoriteTagOps,
    val reloadTags: Boolean = false,
    val notice: String? = null,
    val loginRequired: Boolean = false,
)

internal fun FavoriteTagOps.reduceAdd(
    tag: String,
    outcome: SessionOutcome<Unit>,
    failureText: FavoriteFailureText,
): FavoriteTagEffect = when (outcome) {
    is SessionOutcome.Ok -> FavoriteTagEffect(
        ops = copy(showAdd = false, input = "", request = request.finished()),
        reloadTags = true,
        notice = favoriteTagAddedMessage(tag),
    )

    SessionOutcome.Expired -> FavoriteTagEffect(
        ops = finished(),
        notice = SessionOutcome.Expired.message,
        loginRequired = true,
    )

    is SessionOutcome.Failed -> FavoriteTagEffect(failed(failureText(outcome, FAVORITE_ADD_TAG_FALLBACK)))
}

internal fun FavoriteTagOps.reduceDelete(
    tag: String,
    outcome: SessionOutcome<Unit>,
    failureText: FavoriteFailureText,
): FavoriteTagEffect = when (outcome) {
    is SessionOutcome.Ok -> FavoriteTagEffect(
        ops = copy(deleteTarget = null, request = request.finished()),
        reloadTags = true,
        notice = favoriteTagDeletedMessage(tag),
    )

    SessionOutcome.Expired -> FavoriteTagEffect(
        ops = finished(),
        notice = SessionOutcome.Expired.message,
        loginRequired = true,
    )

    is SessionOutcome.Failed -> FavoriteTagEffect(failed(failureText(outcome, FAVORITE_DELETE_TAG_FALLBACK)))
}

// ── 可保存字段 ────────────────────────────────────────────────────────────

/**
 * 骨架持有的字段。可保存的那些（分段、当前夹、弹窗可见性、两处输入框）走 rememberSaveable，
 * 名册与动作态是瞬时的，重进页面重新拉取 —— 与两块皮肤旧实现逐字对齐。
 */
internal class FavoriteScaffoldFields internal constructor(
    val tabIndex: MutableIntState,
    val folderId: MutableIntState,
    val folders: MutableState<Map<String, String>>,
    val showCreate: MutableState<Boolean>,
    val editTarget: MutableState<Pair<Int, String>?>,
    val deleteTarget: MutableState<Pair<Int, String>?>,
    val folderNameInput: MutableState<String>,
    val folderRequest: MutableState<FavoriteRequestState>,
    val moveTarget: MutableState<SearchResult?>,
    val moveRequest: MutableState<FavoriteRequestState>,
    val showAddTag: MutableState<Boolean>,
    val tagInput: MutableState<String>,
    val tagDeleteTarget: MutableState<String?>,
    val tagRequest: MutableState<FavoriteRequestState>,
)

@Composable
private fun rememberFavoriteScaffoldFields(): FavoriteScaffoldFields {
    val tabIndex = rememberSaveable { mutableIntStateOf(FAVORITE_TAB_FOLDERS) }
    val folderId = rememberSaveable { mutableIntStateOf(FAVORITE_FOLDER_ALL_ID) }
    val folders = remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val showCreate = rememberSaveable { mutableStateOf(false) }
    val editTarget = rememberSaveable(stateSaver = IntStringPairNullableSaver) {
        mutableStateOf<Pair<Int, String>?>(null)
    }
    val deleteTarget = rememberSaveable(stateSaver = IntStringPairNullableSaver) {
        mutableStateOf<Pair<Int, String>?>(null)
    }
    val folderNameInput = rememberSaveable { mutableStateOf("") }
    val folderRequest = remember { mutableStateOf(FavoriteRequestState()) }
    val moveTarget = remember { mutableStateOf<SearchResult?>(null) }
    val moveRequest = remember { mutableStateOf(FavoriteRequestState()) }
    val showAddTag = rememberSaveable { mutableStateOf(false) }
    val tagInput = rememberSaveable { mutableStateOf("") }
    val tagDeleteTarget = rememberSaveable { mutableStateOf<String?>(null) }
    val tagRequest = remember { mutableStateOf(FavoriteRequestState()) }
    return remember(tabIndex, folderId, folders, showCreate, editTarget, deleteTarget, folderNameInput) {
        FavoriteScaffoldFields(
            tabIndex = tabIndex,
            folderId = folderId,
            folders = folders,
            showCreate = showCreate,
            editTarget = editTarget,
            deleteTarget = deleteTarget,
            folderNameInput = folderNameInput,
            folderRequest = folderRequest,
            moveTarget = moveTarget,
            moveRequest = moveRequest,
            showAddTag = showAddTag,
            tagInput = tagInput,
            tagDeleteTarget = tagDeleteTarget,
            tagRequest = tagRequest,
        )
    }
}

// ── 状态类 ────────────────────────────────────────────────────────────────

/**
 * 收藏页的状态与动作。皮肤只读它的字段、调它的方法；控件与弹窗外观留在皮肤里。
 *
 * 网络动作与旧实现一致：先解锁、再把结局折成状态转移、然后提示/重拉/要登录，
 * 顺序一点都不换。
 */
@Stable
internal class FavoriteScaffoldState internal constructor(
    private val deps: ScreenDeps,
    private val scope: CoroutineScope,
    private val notice: suspend (String) -> Unit,
    private val failureText: FavoriteFailureText,
    private val stalePrefix: String,
    private val onLoginRequired: () -> Unit,
    private val favoriteTags: () -> List<TagFavorite>,
    private val onTagsChanged: () -> Unit,
    private val pageSlice: (FavoritePage) -> PageSlice<SearchResult>,
    initialUid: String?,
    private val fields: FavoriteScaffoldFields,
    private val resetSelectionOnAccountChange: Boolean = true,
) {
    private val uidState = mutableStateOf(initialUid)
    private val loaderState = mutableStateOf(newLoader())

    val uid: String? get() = uidState.value
    val signedIn: Boolean get() = uidState.value != null

    // ── 分段 ──
    val tabs: List<String> get() = FAVORITE_TAB_TITLES
    val tabIndex: Int get() = fields.tabIndex.intValue
    val onFoldersTab: Boolean get() = tabIndex == FAVORITE_TAB_FOLDERS
    val addActionLabel: String get() = favoriteAddActionLabel(tabIndex)

    fun selectTab(index: Int) {
        if (index == tabIndex) return
        fields.tabIndex.intValue = index
    }

    // ── 收藏夹与名册 ──
    val folderId: Int get() = fields.folderId.intValue
    val folders: Map<String, String> get() = fields.folders.value
    val folderOptions: List<FavoriteFolderOption> get() = favoriteFolderOptions(folders)
    val currentFolderName: String get() = favoriteCurrentFolderName(folderOptions, folderId)
    val movableFolders: List<FavoriteFolderOption> get() = favoriteMovableFolders(folderOptions)

    /** 收藏夹列表：换夹/换号重建一次，等于丢掉旧夹内容。 */
    val loader: SessionListLoader<SearchResult> get() = loaderState.value

    /** 首屏加载；由 [rememberFavoriteScaffoldState] 的 LaunchedEffect 调一次。 */
    fun enter() {
        loader.loadFirstPage()
    }

    fun switchFolder(targetFolderId: Int) {
        if (targetFolderId == folderId) return
        useFolder(targetFolderId)
    }

    /**
     * 账号变化：重建 loader 重拉（旧实现靠 remember 键 + LaunchedEffect 达成）。
     *
     * [resetSelectionOnAccountChange] 为真时再清名册、回「全部」——旧 Miuix 页有这条回落，旧 COUI 页没有，
     * 差异按参数留在皮肤侧，不强行统一。
     */
    fun accountChanged(nextUid: String?) {
        if (nextUid == uidState.value) return
        uidState.value = nextUid
        if (!resetSelectionOnAccountChange) {
            loaderState.value = newLoader()
            loaderState.value.loadFirstPage()
            return
        }
        fields.folders.value = emptyMap()
        useFolder(FAVORITE_FOLDER_ALL_ID)
    }

    // ── 收藏夹弹窗 ──
    val folderOps: FavoriteFolderOps
        get() = FavoriteFolderOps(
            showCreate = fields.showCreate.value,
            editTarget = fields.editTarget.value,
            deleteTarget = fields.deleteTarget.value,
            nameInput = fields.folderNameInput.value,
            request = fields.folderRequest.value,
        )

    fun openFolderCreate() {
        write(folderOps.creating())
    }

    /** 重命名入口：皮肤只在当前夹不是「全部」时给按钮。 */
    fun openFolderRename() {
        write(folderOps.renaming(folderId, currentFolderName))
    }

    fun openFolderDelete() {
        write(folderOps.confirmingDelete(folderId, currentFolderName))
    }

    fun setFolderName(value: String) {
        write(folderOps.named(value))
    }

    fun dismissFolderDialogs() {
        write(folderOps.dismissed())
    }

    fun submitFolderDialog() {
        val submit = folderOps.submitRequest()
        write(submit.ops)
        val action = submit.action ?: return
        manageFolder(action, submit.folderId, submit.name)
    }

    fun confirmFolderDelete() {
        val submit = folderOps.deleteRequest()
        write(submit.ops)
        val action = submit.action ?: return
        manageFolder(action, submit.folderId, submit.name)
    }

    // ── 移动到收藏夹 ──
    val moveOps: FavoriteMoveOps
        get() = FavoriteMoveOps(target = fields.moveTarget.value, request = fields.moveRequest.value)

    fun openMove(result: SearchResult) {
        write(moveOps.opening(result))
    }

    fun dismissMove() {
        write(moveOps.dismissing())
    }

    fun moveToFolder(targetFolderId: Int, folderName: String) {
        val submit = moveOps.moveRequest(targetFolderId, folderName) ?: return
        write(submit.ops)
        scope.launch {
            val outcome = deps.authCall.call {
                deps.gateway.manageFavoriteFolder(
                    FavoriteFolderAction.MOVE,
                    submit.targetFolderId.toString(),
                    "",
                    submit.albumId,
                )
            }
            applyMoveEffect(moveOps.reduce(submit.folderName, outcome, failureText))
        }
    }

    // ── 标签收藏弹窗 ──
    val tagOps: FavoriteTagOps
        get() = FavoriteTagOps(
            showAdd = fields.showAddTag.value,
            input = fields.tagInput.value,
            deleteTarget = fields.tagDeleteTarget.value,
            request = fields.tagRequest.value,
        )

    /** 顶栏「+」与标签页的「添加标签」共用：未登录只提示，不弹输入框。 */
    fun openAddTag() {
        if (!signedIn) {
            scope.launch { notice(FAVORITE_LOGIN_FIRST) }
            return
        }
        write(tagOps.adding())
    }

    fun setTagInput(value: String) {
        write(tagOps.named(value))
    }

    fun dismissTagAdd() {
        write(tagOps.dismissingAdd())
    }

    fun submitTag() {
        val submit = tagOps.addRequest(favoriteTags())
        write(submit.ops)
        val hint = submit.notice
        if (hint != null) {
            scope.launch { notice(hint) }
            return
        }
        val tag = submit.tag ?: return
        scope.launch {
            val outcome = deps.authCall.call { deps.gateway.addFavoriteTags(listOf(tag)) }
            applyTagEffect(tagOps.reduceAdd(tag, outcome, failureText))
        }
    }

    fun openTagDelete(tag: String) {
        write(tagOps.confirmingDelete(tag))
    }

    fun dismissTagDelete() {
        write(tagOps.dismissingDelete())
    }

    fun confirmTagDelete() {
        val submit = tagOps.deleteRequest()
        write(submit.ops)
        val tag = submit.tag ?: return
        scope.launch {
            val outcome = deps.authCall.call { deps.gateway.removeFavoriteTags(listOf(tag)) }
            applyTagEffect(tagOps.reduceDelete(tag, outcome, failureText))
        }
    }

    // ── 内部编排 ──

    private fun manageFolder(action: FavoriteFolderAction, targetId: String, name: String) {
        scope.launch {
            val outcome = deps.authCall.call {
                deps.gateway.manageFavoriteFolder(action, targetId, name)
            }
            applyFolderEffect(folderOps.reduce(action, targetId, folderId, outcome, failureText))
        }
    }

    private suspend fun applyFolderEffect(effect: FavoriteFolderEffect) {
        write(effect.ops)
        effect.notice?.let { notice(it) }
        if (effect.resetFolder) useFolder(FAVORITE_FOLDER_ALL_ID)
        if (effect.feedReload == FavoriteFeedReload.FirstPage) loader.loadFirstPage()
        if (effect.loginRequired) onLoginRequired()
    }

    private suspend fun applyMoveEffect(effect: FavoriteMoveEffect) {
        write(effect.ops)
        effect.notice?.let { notice(it) }
        if (effect.feedReload == FavoriteFeedReload.Refresh) loader.refresh()
        if (effect.loginRequired) onLoginRequired()
    }

    private suspend fun applyTagEffect(effect: FavoriteTagEffect) {
        write(effect.ops)
        effect.notice?.let { notice(it) }
        if (effect.reloadTags) onTagsChanged()
        if (effect.loginRequired) onLoginRequired()
    }

    /** 换到某个收藏夹：重建 loader 并重拉第一页（等价于旧实现的 remember 键 + LaunchedEffect）。 */
    private fun useFolder(targetFolderId: Int) {
        fields.folderId.intValue = targetFolderId
        loaderState.value = newLoader()
        loaderState.value.loadFirstPage()
    }

    private fun newLoader(): SessionListLoader<SearchResult> {
        val folder = fields.folderId.intValue
        return SessionListLoader(
            scope = scope,
            idOf = { item: SearchResult -> item.id },
            failureMessage = FAVORITE_FEED_FAILURE,
            stalePrefix = stalePrefix,
            fetchPage = { page ->
                when (val outcome = deps.authCall.call { deps.gateway.favorites(folder, page) }) {
                    is SessionOutcome.Ok -> {
                        val next = outcome.value
                        // 名册随任一页返回，顺手更新；空名册不覆盖已有映射
                        fields.folders.value = favoriteFoldersAfter(fields.folders.value, next.folders)
                        SessionOutcome.Ok(pageSlice(next))
                    }

                    SessionOutcome.Expired -> SessionOutcome.Expired
                    is SessionOutcome.Failed -> outcome
                }
            },
            notice = { message -> scope.launch { notice(message) } },
        )
    }

    private fun write(ops: FavoriteFolderOps) {
        fields.showCreate.value = ops.showCreate
        fields.editTarget.value = ops.editTarget
        fields.deleteTarget.value = ops.deleteTarget
        fields.folderNameInput.value = ops.nameInput
        fields.folderRequest.value = ops.request
    }

    private fun write(ops: FavoriteMoveOps) {
        fields.moveTarget.value = ops.target
        fields.moveRequest.value = ops.request
    }

    private fun write(ops: FavoriteTagOps) {
        fields.showAddTag.value = ops.showAdd
        fields.tagInput.value = ops.input
        fields.tagDeleteTarget.value = ops.deleteTarget
        fields.tagRequest.value = ops.request
    }
}

/**
 * 收藏页状态入口：记住字段、建一次状态类，然后跑两条与旧实现等价的 effect。
 *
 * @param onLoginRequired 会话失效时的登录入口（皮肤传 nav::login）
 * @param onNotice 轻提示（皮肤传 snackbar 的 showSnackbar）
 * @param favoriteTags 当前标签收藏列表，用于判重
 * @param onTagsChanged 标签增删成功后重拉标签列表（Miuix 走 tagsLoader，COUI 走 loadTags）
 * @param failureText 失败文案策略，两皮肤各注入一份
 * @param stalePrefix 刷新失败提示语
 * @param pageSlice 一页切片的退化口径
 * @param resetSelectionOnAccountChange 换号时是否清名册并回「全部」（旧 Miuix 是，旧 COUI 否）
 */
@Composable
internal fun rememberFavoriteScaffoldState(
    deps: ScreenDeps,
    onLoginRequired: () -> Unit,
    onNotice: suspend (String) -> Unit,
    favoriteTags: () -> List<TagFavorite>,
    onTagsChanged: () -> Unit,
    failureText: FavoriteFailureText = ::favoriteDetailFailure,
    stalePrefix: String = FAVORITE_STALE_PREFIX_DEFAULT,
    pageSlice: (FavoritePage) -> PageSlice<SearchResult> = ::favoritePageSlice,
    resetSelectionOnAccountChange: Boolean = true,
): FavoriteScaffoldState {
    val scope = rememberCoroutineScope()
    val user by deps.account.user.collectAsState()
    val noticeRef = rememberUpdatedState(onNotice)
    val loginRef = rememberUpdatedState(onLoginRequired)
    val tagsRef = rememberUpdatedState(favoriteTags)
    val tagsChangedRef = rememberUpdatedState(onTagsChanged)
    val failureRef = rememberUpdatedState(failureText)
    val fields = rememberFavoriteScaffoldFields()
    val state = remember(deps, scope, fields) {
        FavoriteScaffoldState(
            deps = deps,
            scope = scope,
            notice = { message -> noticeRef.value(message) },
            failureText = { failure, fallback -> failureRef.value(failure, fallback) },
            stalePrefix = stalePrefix,
            onLoginRequired = { loginRef.value() },
            favoriteTags = { tagsRef.value() },
            onTagsChanged = { tagsChangedRef.value() },
            pageSlice = pageSlice,
            initialUid = user?.uid,
            fields = fields,
            resetSelectionOnAccountChange = resetSelectionOnAccountChange,
        )
    }
    LaunchedEffect(state) { state.enter() }
    // 账号切换：重建 loader 重拉（是否回「全部」由参数定）；首次组合时 uid 与初始值相同，直接跳过
    LaunchedEffect(state, user?.uid) { state.accountChanged(user?.uid) }
    return state
}
