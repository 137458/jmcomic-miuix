// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.page

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.CouiDialogActionStyle
import com.itos.coui.dialog.CouiDialogDefaults
import com.itos.coui.dialog.CouiDialogPlacement
import com.itos.coui.dialog.Dialog
import com.itos.coui.effect.SpotlightPreset
import com.itos.coui.scroll.rememberCouiOverscrollEffect
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.text.rememberCouiTextStyle
import com.itos.coui.theme.CouiTheme as CouiKitTheme
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.ui.GlassSnackbarHost
import com.jm3.app.miuix.ui.LocalWideLayout
import com.jm3.app.miuix.ui.PaginationModeReloadEffect
import com.jm3.app.miuix.ui.ScreenLoadPhase
import com.jm3.app.miuix.ui.ScreenLoadPlan
import com.jm3.app.miuix.ui.authorNames
import com.jm3.app.miuix.ui.coui.component.CouiEmptyGroup
import com.jm3.app.miuix.ui.coui.component.CouiErrorGroup
import com.jm3.app.miuix.ui.coui.component.CouiLoadMoreFooter
import com.jm3.app.miuix.ui.coui.component.CouiLoadingGroup
import com.jm3.app.miuix.ui.coui.component.CouiMinTouchTarget
import com.jm3.app.miuix.ui.coui.component.CouiPagingGroup
import com.jm3.app.miuix.ui.coui.component.CouiScreenScaffold
import com.jm3.app.miuix.ui.coui.component.CouiSegmentedCapsules
import com.jm3.app.miuix.ui.coui.component.CouiTopBarAction
import com.jm3.app.miuix.ui.coui.component.CouiWorkCardTokens
import com.jm3.app.miuix.ui.coui.component.CouiWorkGridCard
import com.jm3.app.miuix.ui.coui.component.couiDestructiveConfirmAction
import com.jm3.app.miuix.ui.coui.component.couiPressable
import com.jm3.app.miuix.ui.coui.icon.CouiIcons
import com.jm3.app.miuix.ui.coui.theme.CouiTheme
import com.jm3.app.miuix.ui.planLoad
import com.jm3.app.miuix.ui.readableWidth
import com.jm3.app.miuix.ui.skin.LocalSkin
import com.jm3.core.appendedBy
import com.jm3.core.model.FavoriteFolderAction
import com.jm3.core.model.SearchPage
import com.jm3.core.model.SearchResult
import com.jm3.core.model.TagFavorite
import com.jm3.core.paging.PagedFeed
import com.jm3.core.paging.pagerTargetPage
import com.jm3.core.session.SessionOutcome
import com.jm3.core.settings.PaginationMode
import com.jm3.core.toFeed
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SnackbarHostState

private val COUI_FAVORITE_TABS = listOf("收藏夹", "标签收藏")

/**
 * 我的收藏（COUI 栅格版）：收藏夹与标签收藏两个分段，参数与旧 Miuix 页一比一。
 *
 * 旧页的收藏夹下拉、移动选择层都是 Overlay 件，COUI 改走底部弹窗；旧页的行卡在这里统一成作品栅格卡，
 * 「移动到收藏夹」从行尾图标改成卡片正文里的文字键（COUI 图标集没有移动图标）。
 */
@Composable
fun CouiFavoritesScreen(
    deps: ScreenDeps,
    onBack: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenLogin: () -> Unit = {},
) {
    CouiTheme {
        val user by deps.account.user.collectAsState()
        // 与旧页一致：收藏页自己持有 Snackbar，不占路由槽
        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val gridState = rememberLazyGridState()
        val columns = LocalWideLayout.current.gridColumns
        val mode = deps.preferences.paginationMode
        val clickPaging = mode == PaginationMode.CLICK
        val scheme = CouiKitTheme.colorScheme
        val destructive = CouiDialogDefaults.themeColors(CouiKitTheme.isDark).destructive

        // 0 = 收藏夹；1 = 标签收藏
        var tabIndex by rememberSaveable { mutableIntStateOf(0) }
        // null 表示首页还没落地（整屏 Loading）；在途与失败标记由 PagedFeed 自己记
        var feed by remember { mutableStateOf<PagedFeed<SearchResult>?>(null) }
        var failure by remember { mutableStateOf<ScreenLoadPlan?>(null) }
        var refreshing by remember { mutableStateOf(false) }
        var folderId by rememberSaveable { mutableIntStateOf(0) }
        var folders by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
        var folderPickerOpen by remember { mutableStateOf(false) }
        var showCreate by remember { mutableStateOf(false) }
        var editTarget by remember { mutableStateOf<Pair<Int, String>?>(null) }
        var deleteTarget by remember { mutableStateOf<Pair<Int, String>?>(null) }
        var folderNameInput by remember { mutableStateOf("") }
        var folderBusy by remember { mutableStateOf(false) }
        var folderError by remember { mutableStateOf<String?>(null) }

        // 移动到收藏夹：待移动作品与请求状态
        var moveTarget by remember { mutableStateOf<SearchResult?>(null) }
        var moveBusy by remember { mutableStateOf(false) }
        var moveError by remember { mutableStateOf<String?>(null) }

        // 标签收藏：null 表示加载中，空列表表示无数据
        var tags by remember { mutableStateOf<List<TagFavorite>?>(null) }
        var tagsError by remember { mutableStateOf<String?>(null) }
        var tagBusy by remember { mutableStateOf(false) }
        var tagError by remember { mutableStateOf<String?>(null) }
        var showAddTag by remember { mutableStateOf(false) }
        var tagInput by remember { mutableStateOf("") }
        var tagDeleteTarget by remember { mutableStateOf<String?>(null) }
        // 翻页在途守卫：请求期间列表仍可读，但翻页条要禁用，避免连点重复请求
        var pageBusy by remember { mutableStateOf(false) }

        fun applyFailure(
            outcome: SessionOutcome<*>,
            hasContent: Boolean,
            failureMessage: String,
            stalePrefix: String = "刷新失败：",
        ) {
            val plan = planLoad(
                outcome = outcome,
                hasContent = hasContent,
                failureMessage = failureMessage,
                stalePrefix = stalePrefix,
            )
            if (plan.keepContent) {
                scope.launch { snackbarHostState.showSnackbar(plan.notice.orEmpty()) }
            } else {
                failure = plan
            }
        }

        fun loadFirstPage() {
            val targetFolderId = folderId
            scope.launch {
                failure = null
                feed = null
                val outcome = deps.authCall.call { deps.gateway.favorites(targetFolderId, 1) }
                // 请求期间切换了收藏夹：丢弃旧夹结果，避免覆盖新夹内容
                if (targetFolderId != folderId) return@launch
                when (outcome) {
                    is SessionOutcome.Ok -> {
                        val next = outcome.value
                        if (next.folders.isNotEmpty()) folders = next.folders
                        feed = SearchPage(next.currentPage, next.totalPages, next.items).toFeed()
                    }

                    SessionOutcome.Expired -> applyFailure(outcome, hasContent = false, failureMessage = "")

                    is SessionOutcome.Failed -> applyFailure(outcome, hasContent = false, failureMessage = "收藏加载失败")
                }
            }
        }

        fun jumpToPage(target: Int) {
            if (pageBusy) return
            val current = feed ?: return
            val resolved = pagerTargetPage(current.page, target, current.pageCount)
            if (resolved == current.page) return
            pageBusy = true
            val targetFolderId = folderId
            scope.launch {
                try {
                    val outcome = deps.authCall.call { deps.gateway.favorites(targetFolderId, resolved) }
                    if (targetFolderId != folderId) return@launch
                    when (outcome) {
                        is SessionOutcome.Ok -> {
                            val next = outcome.value
                            if (next.folders.isNotEmpty()) folders = next.folders
                            feed = SearchPage(next.currentPage, next.totalPages, next.items).toFeed()
                            gridState.scrollToItem(0)
                        }

                        SessionOutcome.Expired -> applyFailure(outcome, hasContent = true, failureMessage = "")

                        is SessionOutcome.Failed -> applyFailure(outcome, hasContent = true, failureMessage = "收藏翻页失败")
                    }
                } finally {
                    pageBusy = false
                }
            }
        }

        // 刷新：保留当前列表，仅用第一页结果替换，避免整屏 Loading 闪烁
        fun refresh() {
            if (refreshing) return
            refreshing = true
            scope.launch {
                val targetFolderId = folderId
                val outcome = deps.authCall.call { deps.gateway.favorites(targetFolderId, 1) }
                refreshing = false
                if (targetFolderId != folderId) return@launch
                when (outcome) {
                    is SessionOutcome.Ok -> {
                        val next = outcome.value
                        if (next.folders.isNotEmpty()) folders = next.folders
                        feed = SearchPage(next.currentPage, next.totalPages, next.items).toFeed()
                    }

                    SessionOutcome.Expired -> applyFailure(outcome, hasContent = feed != null, failureMessage = "")

                    is SessionOutcome.Failed -> applyFailure(
                        outcome,
                        hasContent = feed != null,
                        failureMessage = "收藏刷新失败",
                    )
                }
            }
        }

        fun switchFolder(targetFolderId: Int) {
            if (targetFolderId == folderId) return
            folderId = targetFolderId
            loadFirstPage()
        }

        /** 收藏夹增删改：成功后关闭弹窗并重拉列表；失败把服务端消息留在弹窗内。 */
        fun manageFolder(action: FavoriteFolderAction, targetId: String, name: String = "") {
            if (folderBusy) return
            folderBusy = true
            folderError = null
            scope.launch {
                val outcome = deps.authCall.call {
                    deps.gateway.manageFavoriteFolder(action, targetId, name)
                }
                folderBusy = false
                when (outcome) {
                    is SessionOutcome.Ok -> {
                        val operation = outcome.value
                        if (!operation.ok) {
                            folderError = operation.message ?: "操作失败"
                            return@launch
                        }
                        showCreate = false
                        editTarget = null
                        deleteTarget = null
                        folderNameInput = ""
                        // 删除当前所在收藏夹后回落到全部，避免停留在已不存在的夹
                        if (action == FavoriteFolderAction.DELETE && targetId.toIntOrNull() == folderId) {
                            folderId = 0
                        }
                        loadFirstPage()
                    }

                    // 账号态操作失效：提示一次并直接给登录入口，不让用户对着按钮重试
                    SessionOutcome.Expired -> {
                        snackbarHostState.showSnackbar(SessionOutcome.Expired.message)
                        onOpenLogin()
                    }

                    is SessionOutcome.Failed -> folderError = "操作失败，请稍后重试"
                }
            }
        }

        /** 移动作品：成功后关闭弹窗、提示并静默刷新当前列表；失败把消息留在弹窗内。 */
        fun moveAlbum(albumId: String, folderName: String, targetFolderId: Int) {
            if (moveBusy) return
            moveBusy = true
            moveError = null
            scope.launch {
                val outcome = deps.authCall.call {
                    deps.gateway.manageFavoriteFolder(
                        FavoriteFolderAction.MOVE,
                        targetFolderId.toString(),
                        "",
                        albumId,
                    )
                }
                moveBusy = false
                when (outcome) {
                    is SessionOutcome.Ok -> {
                        val operation = outcome.value
                        if (!operation.ok) {
                            moveError = operation.message ?: "移动失败"
                            return@launch
                        }
                        moveTarget = null
                        snackbarHostState.showSnackbar("已移动到「" + folderName + "」")
                        refresh()
                    }

                    SessionOutcome.Expired -> {
                        snackbarHostState.showSnackbar(SessionOutcome.Expired.message)
                        onOpenLogin()
                    }

                    is SessionOutcome.Failed -> moveError = "移动失败，请稍后重试"
                }
            }
        }

        /** 标签收藏列表；未登录不发请求，由界面直接展示登录提示。 */
        fun loadTags() {
            // 绑定发起请求时的用户，避免登出/换号后把旧结果写回界面
            val uid = user?.uid ?: return
            scope.launch {
                tagsError = null
                tags = null
                val outcome = deps.authCall.call { deps.gateway.tagsFavorite() }
                if (user?.uid != uid) return@launch
                when (outcome) {
                    is SessionOutcome.Ok -> tags = outcome.value
                    // 会话失效后用户会置空，标签页自动回落到未登录引导，不必再留错误文案
                    SessionOutcome.Expired -> Unit
                    is SessionOutcome.Failed -> tagsError = "标签收藏加载失败，请稍后重试"
                }
            }
        }

        /** 添加标签：空值与重复只做轻提示，不打断输入框。 */
        fun addTag() {
            val tag = tagInput.trim()
            if (tag.isEmpty()) {
                scope.launch { snackbarHostState.showSnackbar("标签不能为空") }
                return
            }
            if (tags.orEmpty().any { it.tag == tag }) {
                scope.launch { snackbarHostState.showSnackbar("该标签已收藏") }
                return
            }
            if (tagBusy) return
            tagBusy = true
            tagError = null
            scope.launch {
                val outcome = deps.authCall.call { deps.gateway.addFavoriteTags(listOf(tag)) }
                tagBusy = false
                when (outcome) {
                    is SessionOutcome.Ok -> {
                        showAddTag = false
                        tagInput = ""
                        snackbarHostState.showSnackbar("已添加标签 #" + tag)
                        loadTags()
                    }

                    SessionOutcome.Expired -> {
                        snackbarHostState.showSnackbar(SessionOutcome.Expired.message)
                        onOpenLogin()
                    }

                    is SessionOutcome.Failed -> tagError = "添加标签失败，请稍后重试"
                }
            }
        }

        /** 删除标签：确认后调用接口并刷新列表。 */
        fun deleteTag(tag: String) {
            if (tagBusy) return
            tagBusy = true
            tagError = null
            scope.launch {
                val outcome = deps.authCall.call { deps.gateway.removeFavoriteTags(listOf(tag)) }
                tagBusy = false
                when (outcome) {
                    is SessionOutcome.Ok -> {
                        tagDeleteTarget = null
                        snackbarHostState.showSnackbar("已删除标签 #" + tag)
                        loadTags()
                    }

                    SessionOutcome.Expired -> {
                        snackbarHostState.showSnackbar(SessionOutcome.Expired.message)
                        onOpenLogin()
                    }

                    is SessionOutcome.Failed -> tagError = "删除标签失败，请稍后重试"
                }
            }
        }

        fun loadMore() {
            val current = feed ?: return
            val requested = current.beginLoadMore()
            if (requested === current) return
            val targetFolderId = folderId
            feed = requested
            scope.launch {
                val outcome = deps.authCall.call { deps.gateway.favorites(targetFolderId, requested.page + 1) }
                // 加载期间切换了收藏夹：这批结果属于旧收藏夹，丢弃以免串夹
                if (targetFolderId != folderId) return@launch
                when (outcome) {
                    is SessionOutcome.Ok -> {
                        val next = outcome.value
                        feed = requested.appendedBy(
                            SearchPage(next.currentPage, next.totalPages, next.items),
                        )
                    }

                    // 翻页撞上会话失效：已加载的列表不能顶掉，保留内容并把页脚置成可重试
                    SessionOutcome.Expired -> {
                        feed = requested.failLoadMore()
                        applyFailure(outcome, hasContent = true, failureMessage = "")
                    }

                    is SessionOutcome.Failed -> feed = requested.failLoadMore()
                }
            }
        }

        LaunchedEffect(Unit) { loadFirstPage() }

        PaginationModeReloadEffect(mode) { loadFirstPage() }

        // 首次切到标签页才加载；登录态变化时清空旧数据，避免登出后残留
        LaunchedEffect(tabIndex, user?.uid) {
            if (user == null) {
                tags = null
                tagsError = null
            } else if (tabIndex == 1 && tags == null && tagsError == null) {
                loadTags()
            }
        }

        val folderOptions = buildList {
            add(0 to "全部")
            folders.entries
                .mapNotNull { (key, name) -> key.toIntOrNull()?.let { it to name } }
                .filter { it.first != 0 }
                .sortedBy { it.first }
                .forEach { add(it) }
        }
        val currentFolderName = folderOptions.firstOrNull { it.first == folderId }?.second ?: "全部"

        Box(modifier = Modifier.fillMaxSize()) {
            CouiScreenScaffold(
                title = "我的收藏",
                onBack = onBack,
                actions = {
                    val addDesc = if (tabIndex == 0) "新建收藏夹" else "添加标签"
                    CouiTopBarAction(
                        icon = CouiIcons.Copy,
                        contentDescription = addDesc,
                        onClick = {
                            if (tabIndex == 0) {
                                folderNameInput = ""
                                folderError = null
                                showCreate = true
                            } else if (user == null) {
                                scope.launch { snackbarHostState.showSnackbar("请先登录后再添加标签") }
                            } else {
                                tagInput = ""
                                tagError = null
                                showAddTag = true
                            }
                        },
                    )
                },
            ) { innerPadding ->
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    state = gridState,
                    modifier = Modifier
                        .fillMaxSize()
                        .readableWidth()
                        .consumeWindowInsets(innerPadding),
                    contentPadding = innerPadding,
                    horizontalArrangement = Arrangement.spacedBy(CouiWorkCardTokens.GridSpacing),
                    verticalArrangement = Arrangement.spacedBy(CouiWorkCardTokens.GridSpacing),
                    overscrollEffect = rememberCouiOverscrollEffect(),
                ) {
                    item(key = "coui-favorites-tabs", span = { GridItemSpan(maxLineSpan) }) {
                        CouiSegmentedCapsules(
                            items = COUI_FAVORITE_TABS,
                            selectedIndex = tabIndex,
                            onSelect = { index -> if (index != tabIndex) tabIndex = index },
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    if (tabIndex == 0) {
                        val gridFeed = feed
                        val planned = failure
                        when {
                            planned != null -> item(
                                key = "coui-favorites-failed",
                                span = { GridItemSpan(maxLineSpan) },
                            ) {
                                CouiErrorGroup(
                                    message = if (planned.phase == ScreenLoadPhase.Expired) {
                                        SessionOutcome.Expired.message
                                    } else {
                                        planned.message.orEmpty()
                                    },
                                    onRetry = {
                                        if (planned.phase == ScreenLoadPhase.Expired) {
                                            onOpenLogin()
                                        } else {
                                            loadFirstPage()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                                )
                            }

                            gridFeed == null -> item(
                                key = "coui-favorites-loading",
                                span = { GridItemSpan(maxLineSpan) },
                            ) {
                                CouiLoadingGroup(modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                            }

                            else -> {
                                item(key = "coui-favorites-folders", span = { GridItemSpan(maxLineSpan) }) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(
                                            CouiWorkCardTokens.GridSpacing,
                                        ),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        CouiFavoritesChipButton(
                                            label = "收藏夹：" + currentFolderName,
                                            onClick = { folderPickerOpen = true },
                                            modifier = Modifier.weight(1f),
                                        )
                                        // 仅自建收藏夹可改名/删除；「全部」是聚合视图，不提供操作
                                        if (folderId != 0) {
                                            CouiFavoritesChipButton(
                                                label = "重命名",
                                                onClick = {
                                                    folderNameInput = currentFolderName
                                                    folderError = null
                                                    editTarget = folderId to currentFolderName
                                                },
                                            )
                                            CouiFavoritesChipButton(
                                                label = "删除",
                                                onClick = { deleteTarget = folderId to currentFolderName },
                                            )
                                        }
                                    }
                                }
                                if (gridFeed.items.isEmpty()) {
                                    item(
                                        key = "coui-favorites-empty",
                                        span = { GridItemSpan(maxLineSpan) },
                                    ) {
                                        CouiEmptyGroup(
                                            text = "在作品详情页点击收藏后会出现在这里",
                                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                                        )
                                    }
                                } else {
                                    items(items = gridFeed.items, key = { "coui-fav-" + it.id }) { result ->
                                        Box(
                                            modifier = Modifier.fillMaxWidth(),
                                            contentAlignment = Alignment.TopCenter,
                                        ) {
                                            CouiFavoritesAlbumCard(
                                                result = result,
                                                onClick = { onOpenAlbum(result.id) },
                                                onMove = {
                                                    moveError = null
                                                    moveTarget = result
                                                },
                                            )
                                        }
                                    }
                                }
                                item(key = "coui-favorites-footer", span = { GridItemSpan(maxLineSpan) }) {
                                    if (gridFeed.items.isEmpty()) {
                                        Unit
                                    } else if (clickPaging) {
                                        CouiPagingGroup(
                                            page = gridFeed.page,
                                            pageCount = gridFeed.pageCount,
                                            hasMore = gridFeed.hasMore,
                                            onJumpPage = { target -> jumpToPage(target) },
                                        )
                                    } else {
                                        CouiFavoritesAutoLoadEffect(feed = gridFeed, onLoadMore = { loadMore() })
                                        CouiLoadMoreFooter(
                                            feed = gridFeed,
                                            errorMessage = "没拿到下一页，可点击重试",
                                            onRetry = { loadMore() },
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        val tagList = tags
                        val tagsErr = tagsError
                        when {
                            user == null -> item(
                                key = "coui-tags-login",
                                span = { GridItemSpan(maxLineSpan) },
                            ) {
                                CouiEmptyGroup(
                                    text = "登录后查看标签收藏",
                                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                                )
                            }

                            tagsErr != null -> item(key = "coui-tags-failed", span = { GridItemSpan(maxLineSpan) }) {
                                CouiErrorGroup(
                                    message = tagsErr,
                                    onRetry = { loadTags() },
                                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                                )
                            }

                            tagList == null -> item(key = "coui-tags-loading", span = { GridItemSpan(maxLineSpan) }) {
                                CouiLoadingGroup(modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                            }

                            tagList.isEmpty() -> item(key = "coui-tags-empty", span = { GridItemSpan(maxLineSpan) }) {
                                CouiEmptyGroup(
                                    text = "暂无标签收藏，点击右上角添加",
                                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                                )
                            }

                            else -> {
                                item(key = "coui-tags-add", span = { GridItemSpan(maxLineSpan) }) {
                                    CouiFavoritesChipButton(
                                        label = "添加标签 · 共 " + tagList.size + " 个收藏标签",
                                        onClick = {
                                            tagInput = ""
                                            tagError = null
                                            showAddTag = true
                                        },
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    )
                                }
                                items(items = tagList, key = { "coui-tag-" + it.tag }) { item ->
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.TopCenter,
                                    ) {
                                        CouiWorkGridCard(
                                            title = "#" + item.tag,
                                            coverUrl = null,
                                            onClick = {
                                                tagError = null
                                                tagDeleteTarget = item.tag
                                            },
                                            subtitle = item.updatedAt?.let { "更新于 " + it } ?: "更新时间未知",
                                            badge = {
                                                CouiFavoritesDeleteBadge(
                                                    contentDescription = "删除标签",
                                                    onClick = {
                                                        tagError = null
                                                        tagDeleteTarget = item.tag
                                                    },
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .padding(CouiWorkCardTokens.BadgeInset),
                                                )
                                            },
                                        )
                                    }
                                }
                                item(key = "coui-tags-footer", span = { GridItemSpan(maxLineSpan) }) {
                                    // 标签收藏一次全量返回，没有下一页
                                    CouiLoadMoreFooter(
                                        loading = false,
                                        error = null,
                                        endReached = true,
                                        onRetry = {},
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (folderPickerOpen) {
                Dialog(
                    onDismissRequest = { folderPickerOpen = false },
                    confirmAction = CouiDialogAction(
                        text = "关闭",
                        onClick = { folderPickerOpen = false },
                        style = CouiDialogActionStyle.Secondary,
                    ),
                    title = "收藏夹",
                    placement = CouiDialogPlacement.Bottom,
                ) {
                    folderOptions.forEach { option ->
                        CouiFavoritesPickerRow(
                            label = option.second,
                            selected = option.first == folderId,
                            onClick = {
                                folderPickerOpen = false
                                switchFolder(option.first)
                            },
                        )
                    }
                }
            }

            if (showCreate || editTarget != null) {
                Dialog(
                    onDismissRequest = {
                        showCreate = false
                        editTarget = null
                        folderError = null
                    },
                    confirmAction = CouiDialogAction(
                        text = if (showCreate) "新建" else "保存",
                        onClick = {
                            val name = folderNameInput.trim()
                            if (name.isEmpty()) {
                                folderError = "名称不能为空"
                            } else if (showCreate) {
                                manageFolder(FavoriteFolderAction.ADD, targetId = "", name = name)
                            } else {
                                editTarget?.let { manageFolder(FavoriteFolderAction.EDIT, it.first.toString(), name) }
                            }
                        },
                        enabled = !folderBusy,
                        style = CouiDialogActionStyle.Primary,
                        colors = CouiDialogDefaults.primaryActionColors(scheme.accent),
                    ),
                    title = if (showCreate) "新建收藏夹" else "重命名收藏夹",
                    dismissAction = CouiDialogAction(
                        text = "取消",
                        onClick = {
                            showCreate = false
                            editTarget = null
                            folderError = null
                        },
                        style = CouiDialogActionStyle.Secondary,
                    ),
                    placement = CouiDialogPlacement.Bottom,
                ) {
                    CouiFavoritesTextField(
                        value = folderNameInput,
                        onValueChange = { folderNameInput = it.take(20) },
                        placeholder = "收藏夹名称（最多 20 字）",
                    )
                    CouiFavoritesInlineError(message = folderError, color = destructive)
                }
            }

            val deleting = deleteTarget
            if (deleting != null) {
                Dialog(
                    onDismissRequest = {
                        deleteTarget = null
                        folderError = null
                    },
                    confirmAction = couiDestructiveConfirmAction(
                        text = "确认删除",
                        enabled = !folderBusy,
                        onClick = {
                            manageFolder(FavoriteFolderAction.DELETE, deleting.first.toString())
                        },
                    ),
                    title = "删除收藏夹",
                    message = "将删除收藏夹「" + deleting.second + "」，夹内作品不会取消收藏。",
                    dismissAction = CouiDialogAction(
                        text = "取消",
                        onClick = {
                            deleteTarget = null
                            folderError = null
                        },
                        style = CouiDialogActionStyle.Secondary,
                    ),
                    placement = CouiDialogPlacement.Center,
                ) {
                    CouiFavoritesInlineError(message = folderError, color = destructive)
                }
            }

            val moving = moveTarget
            if (moving != null) {
                val movableFolders = folderOptions.filter { it.first != 0 }
                Dialog(
                    onDismissRequest = {
                        if (!moveBusy) {
                            moveTarget = null
                            moveError = null
                        }
                    },
                    confirmAction = CouiDialogAction(
                        text = "关闭",
                        onClick = {
                            if (!moveBusy) {
                                moveTarget = null
                                moveError = null
                            }
                        },
                        enabled = !moveBusy,
                        style = CouiDialogActionStyle.Secondary,
                    ),
                    title = "移动到收藏夹",
                    placement = CouiDialogPlacement.Bottom,
                ) {
                    CouiFavoritesInlineError(message = moveError, color = destructive)
                    if (movableFolders.isEmpty()) {
                        Text(
                            text = "暂无其他收藏夹，请先新建收藏夹",
                            kind = CouiTextKind.Description,
                            color = scheme.onSurfaceVariant,
                            maxLines = 2,
                            textAlign = TextAlign.Start,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    } else {
                        movableFolders.forEach { (id, name) ->
                            CouiFavoritesPickerRow(
                                label = name,
                                selected = false,
                                onClick = {
                                    if (!moveBusy) moveAlbum(moving.id, name, id)
                                },
                            )
                        }
                    }
                }
            }

            if (showAddTag) {
                Dialog(
                    onDismissRequest = {
                        if (!tagBusy) {
                            showAddTag = false
                            tagError = null
                        }
                    },
                    confirmAction = CouiDialogAction(
                        text = "添加",
                        onClick = { addTag() },
                        enabled = !tagBusy,
                        style = CouiDialogActionStyle.Primary,
                        colors = CouiDialogDefaults.primaryActionColors(scheme.accent),
                    ),
                    title = "添加标签",
                    dismissAction = CouiDialogAction(
                        text = "取消",
                        onClick = {
                            showAddTag = false
                            tagError = null
                        },
                        enabled = !tagBusy,
                        style = CouiDialogActionStyle.Secondary,
                    ),
                    placement = CouiDialogPlacement.Bottom,
                ) {
                    CouiFavoritesTextField(
                        value = tagInput,
                        onValueChange = { tagInput = it.take(20) },
                        placeholder = "标签名称（最多 20 字）",
                    )
                    CouiFavoritesInlineError(message = tagError, color = destructive)
                }
            }

            val deletingTag = tagDeleteTarget
            if (deletingTag != null) {
                Dialog(
                    onDismissRequest = {
                        if (!tagBusy) {
                            tagDeleteTarget = null
                            tagError = null
                        }
                    },
                    confirmAction = couiDestructiveConfirmAction(
                        text = "确认删除",
                        enabled = !tagBusy,
                        onClick = { deleteTag(deletingTag) },
                    ),
                    title = "删除标签",
                    message = "将取消收藏标签「#" + deletingTag + "」。",
                    dismissAction = CouiDialogAction(
                        text = "取消",
                        onClick = {
                            tagDeleteTarget = null
                            tagError = null
                        },
                        style = CouiDialogActionStyle.Secondary,
                    ),
                    placement = CouiDialogPlacement.Center,
                ) {
                    CouiFavoritesInlineError(message = tagError, color = destructive)
                }
            }

            GlassSnackbarHost(
                state = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/** 收藏作品栅格卡：分类 + 作者 + 「移动到收藏夹」文字键。 */
@Composable
private fun CouiFavoritesAlbumCard(
    result: SearchResult,
    onClick: () -> Unit,
    onMove: () -> Unit,
) {
    CouiWorkGridCard(
        title = result.title,
        coverUrl = result.coverUrl,
        onClick = onClick,
        albumId = result.id,
        coverContentDescription = result.title,
        subtitle = result.authors.authorNames(),
        category = result.category,
        footer = {
            val scheme = CouiKitTheme.colorScheme
            Text(
                text = "移动到收藏夹",
                kind = CouiTextKind.Description,
                color = scheme.accent,
                maxLines = 1,
                textAlign = TextAlign.Start,
                modifier = Modifier
                    .padding(top = CouiWorkCardTokens.TextGap)
                    .clip(CouiCapsuleShape)
                    .couiPressable(
                        shape = CouiCapsuleShape, spotlight = SpotlightPreset.ChipTab,
                        minTouchTarget = CouiMinTouchTarget,
                        onClick = onMove,
                    )
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        },
    )
}

/** 收藏夹/标签操作键：胶囊实底，尺寸与筛选键一致。 */
@Composable
private fun CouiFavoritesChipButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = CouiKitTheme.colorScheme
    Box(
        modifier = modifier
            .clip(CouiCapsuleShape)
            .background(scheme.container)
            .couiPressable(
                shape = CouiCapsuleShape, spotlight = SpotlightPreset.ChipTab,
                minTouchTarget = CouiMinTouchTarget,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            kind = CouiTextKind.PreferenceValue,
            color = scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** 卡片右上角的删除角标。 */
@Composable
private fun CouiFavoritesDeleteBadge(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = CouiKitTheme.colorScheme
    Box(
        modifier = modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(scheme.container)
            .couiPressable(
                shape = CircleShape,
                minTouchTarget = CouiMinTouchTarget,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = CouiIcons.Close,
            contentDescription = contentDescription,
            modifier = Modifier.size(14.dp),
            tint = scheme.onSurfaceVariant,
        )
    }
}

/** 弹窗里的一行选择项：选中项用强调色。 */
@Composable
private fun CouiFavoritesPickerRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = CouiKitTheme.colorScheme
    Text(
        text = label,
        kind = CouiTextKind.Body,
        color = if (selected) scheme.accent else scheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Start,
        modifier = Modifier
            .fillMaxWidth()
            .clip(CouiCapsuleShape)
            .couiPressable(
                shape = CouiCapsuleShape, spotlight = SpotlightPreset.ChipTab,
                minTouchTarget = CouiMinTouchTarget,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

/** 弹窗里的单行输入框：空值时显示占位文案。 */
@Composable
private fun CouiFavoritesTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    val scheme = CouiKitTheme.colorScheme
    val shape = RoundedCornerShape(LocalSkin.current.shapes.controlCorner)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(shape)
            .background(scheme.container)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                kind = CouiTextKind.Body,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = rememberCouiTextStyle(CouiTextKind.Body).copy(color = scheme.onSurface),
            cursorBrush = SolidColor(scheme.accent),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 弹窗内的失败文案：没有失败时不留空行。 */
@Composable
private fun CouiFavoritesInlineError(message: String?, color: androidx.compose.ui.graphics.Color) {
    if (message == null) return
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = message,
        kind = CouiTextKind.Description,
        color = color,
        maxLines = 3,
        textAlign = TextAlign.Start,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

/** 触底续页：滚动模式下列表末尾进入视口就拉下一页，失败时不自动重试。 */
@Composable
private fun CouiFavoritesAutoLoadEffect(feed: PagedFeed<*>, onLoadMore: () -> Unit) {
    LaunchedEffect(feed.page, feed.loadingMore, feed.loadMoreFailed) {
        if (!feed.canLoadMore || feed.loadingMore || feed.loadMoreFailed) return@LaunchedEffect
        onLoadMore()
    }
}
