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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.scroll.rememberCouiOverscrollEffect
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.text.rememberCouiTextStyle
import com.itos.coui.theme.CouiTheme as CouiKitTheme
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.ui.GlassSnackbarHost
import com.jm3.app.miuix.ui.LocalWideLayout
import com.jm3.app.miuix.ui.authorNames
import com.jm3.app.miuix.ui.coui.component.CouiBodyText
import com.jm3.app.miuix.ui.coui.component.rememberCouiBodyTextStyle
import com.jm3.app.miuix.ui.coui.component.CouiAutoLoadMoreEffect
import com.jm3.app.miuix.ui.coui.component.CouiEmptyGroup
import com.jm3.app.miuix.ui.coui.component.CouiErrorGroup
import com.jm3.app.miuix.ui.coui.component.CouiFeedLoginGroup
import com.jm3.app.miuix.ui.coui.component.CouiLoadMoreFooter
import com.jm3.app.miuix.ui.coui.component.CouiLoadingGroup
import com.jm3.app.miuix.ui.coui.component.CouiMinTouchTarget
import com.jm3.app.miuix.ui.coui.component.CouiPagingGroup
import com.jm3.app.miuix.ui.coui.component.CouiScreenFailureGroup
import com.jm3.app.miuix.ui.coui.component.CouiScreenScaffold
import com.jm3.app.miuix.ui.coui.component.CouiTopBarAction
import com.jm3.app.miuix.ui.coui.component.CouiTopBarSegmentedTabs
import com.jm3.app.miuix.ui.coui.component.rememberCouiScreenScrollBehavior
import com.jm3.app.miuix.ui.coui.component.CouiWorkCardTokens
import com.jm3.app.miuix.ui.coui.component.CouiWorkGridCard
import com.jm3.app.miuix.ui.coui.component.couiDestructiveConfirmAction
import com.jm3.app.miuix.ui.coui.component.couiDialogMaterial
import com.jm3.app.miuix.ui.coui.component.couiPressable
import com.jm3.app.miuix.ui.coui.component.rememberRetainedTarget
import com.jm3.app.miuix.ui.coui.icon.CouiIcons
import com.jm3.app.miuix.ui.coui.theme.CouiTheme
import com.jm3.app.miuix.ui.favorites.FAVORITE_ADD_CONFIRM
import com.jm3.app.miuix.ui.favorites.FAVORITE_ADD_FOLDER_TITLE
import com.jm3.app.miuix.ui.favorites.FAVORITE_ADD_TAG_TITLE
import com.jm3.app.miuix.ui.favorites.FAVORITE_CANCEL_LABEL
import com.jm3.app.miuix.ui.favorites.FAVORITE_CLOSE_LABEL
import com.jm3.app.miuix.ui.favorites.FAVORITE_CREATE_CONFIRM
import com.jm3.app.miuix.ui.favorites.FAVORITE_DELETE_CONFIRM
import com.jm3.app.miuix.ui.favorites.FAVORITE_DELETE_FOLDER_TITLE
import com.jm3.app.miuix.ui.favorites.FAVORITE_DELETE_TAG_TITLE
import com.jm3.app.miuix.ui.favorites.FAVORITE_EDIT_CONFIRM
import com.jm3.app.miuix.ui.favorites.FAVORITE_EDIT_FOLDER_TITLE
import com.jm3.app.miuix.ui.favorites.FAVORITE_FOLDER_ALL_ID
import com.jm3.app.miuix.ui.favorites.FAVORITE_FOLDER_NAME_LABEL
import com.jm3.app.miuix.ui.favorites.FAVORITE_MOVE_EMPTY_HINT
import com.jm3.app.miuix.ui.favorites.FAVORITE_MOVE_TITLE
import com.jm3.app.miuix.ui.favorites.FAVORITE_TAB_FOLDERS
import com.jm3.app.miuix.ui.favorites.FAVORITE_TAB_TAGS
import com.jm3.app.miuix.ui.favorites.FAVORITE_TAG_NAME_LABEL
import com.jm3.app.miuix.ui.favorites.favoriteDeleteFolderMessage
import com.jm3.app.miuix.ui.favorites.favoriteDeleteTagMessage
import com.jm3.app.miuix.ui.favorites.favoriteFixedFailure
import com.jm3.app.miuix.ui.favorites.favoritePageSliceKeepingPageCount
import com.jm3.app.miuix.ui.favorites.rememberFavoriteScaffoldState
import com.jm3.app.miuix.ui.list.ListLoadState
import com.jm3.app.miuix.ui.readableWidth
import com.jm3.app.miuix.ui.rememberSyncedTabPagerState
import com.jm3.app.miuix.ui.route.AppRoutes
import com.jm3.app.miuix.ui.skin.LocalSkin
import com.jm3.core.model.SearchResult
import com.jm3.core.model.TagFavorite
import com.jm3.core.paging.pagerTargetPage
import com.jm3.core.session.SessionOutcome
import com.jm3.core.settings.PaginationMode
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SnackbarHostState

/**
 * 我的收藏（COUI 栅格版）：收藏夹与标签收藏两个分段，参数与旧 Miuix 页一比一。
 *
 * 旧页的收藏夹下拉、移动选择层都是 Overlay 件，COUI 改走底部弹窗；旧页的行卡在这里统一成作品栅格卡，
 * 「移动到收藏夹」从行尾图标改成卡片正文里的文字键（COUI 图标集没有移动图标）。
 */
@Composable
internal fun CouiFavoritesScreen(
    deps: ScreenDeps,
    onBack: () -> Unit,
    nav: AppRoutes,
) {
    CouiTheme {
        val user by deps.account.user.collectAsState()
        // 与旧页一致：收藏页自己持有 Snackbar，不占路由槽
        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val gridState = rememberLazyGridState()
        val tagsGridState = rememberLazyGridState()
        val columns = LocalWideLayout.current.gridColumns
        val mode = deps.paginationMode
        val clickPaging = mode == PaginationMode.CLICK
        val scheme = CouiKitTheme.colorScheme
        val destructive = CouiDialogDefaults.themeColors(CouiKitTheme.isDark).destructive

        // COUI 独有：收藏夹选择走底部弹窗，开关自己留在这里
        var folderPickerOpen by rememberSaveable { mutableStateOf(false) }

        // 标签收藏：null 表示加载中，空列表表示无数据；COUI 页手写这份加载，语义与旧页一致（会话失效静默）
        var tags by remember { mutableStateOf<List<TagFavorite>?>(null) }
        var tagsError by remember { mutableStateOf<String?>(null) }

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

        // 分段、收藏夹名册、四个弹窗与三条网络动作的编排都在骨架里，本屏只留栅格与弹窗外观；
        // 旧页的固定失败文案、页码切片口径与「换号不回落收藏夹」按参数注入
        val state = rememberFavoriteScaffoldState(
            deps = deps,
            onLoginRequired = nav::login,
            onNotice = { message -> snackbarHostState.showSnackbar(message) },
            favoriteTags = { tags.orEmpty() },
            onTagsChanged = { loadTags() },
            failureText = ::favoriteFixedFailure,
            pageSlice = ::favoritePageSliceKeepingPageCount,
            resetSelectionOnAccountChange = false,
        )
        val snapshot by state.loader.snapshot.collectAsState()
        val feed = snapshot.feed

        // 首次切到标签页才加载；登录态变化时清空旧数据，避免登出后残留
        LaunchedEffect(state.tabIndex, user?.uid) {
            if (user == null) {
                tags = null
                tagsError = null
            } else if (state.tabIndex == FAVORITE_TAB_TAGS && tags == null && tagsError == null) {
                loadTags()
            }
        }

        val folderOptions = state.folderOptions
        val currentFolderName = state.currentFolderName
        val pagerState = rememberSyncedTabPagerState(
            selectedTab = state.tabIndex,
            pageCount = state.tabs.size,
            onTabSelected = { index -> state.selectTab(index) },
        )
        val scrollBehavior = rememberCouiScreenScrollBehavior {
            val activeState = if (state.tabIndex == FAVORITE_TAB_FOLDERS) gridState else tagsGridState
            activeState.canScrollForward || activeState.canScrollBackward
        }

        Box(modifier = Modifier.fillMaxSize()) {
            CouiScreenScaffold(
                title = "我的收藏",
                onBack = onBack,
                scrollBehavior = scrollBehavior,
                topBarBottom = {
                    CouiTopBarSegmentedTabs(
                        items = state.tabs,
                        selectedIndex = state.tabIndex,
                        onSelect = { index -> state.selectTab(index) },
                    )
                },
                actions = {
                    CouiTopBarAction(
                        icon = CouiIcons.Copy,
                        contentDescription = state.addActionLabel,
                        onClick = {
                            if (state.onFoldersTab) {
                                state.openFolderCreate()
                            } else {
                                state.openAddTag()
                            }
                        },
                    )
                },
            ) { innerPadding ->
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        state = if (page == 0) gridState else tagsGridState,
                        modifier = Modifier
                            .fillMaxSize()
                            .readableWidth()
                            .consumeWindowInsets(innerPadding),
                        contentPadding = innerPadding,
                        horizontalArrangement = Arrangement.spacedBy(CouiWorkCardTokens.GridSpacing),
                        verticalArrangement = Arrangement.spacedBy(CouiWorkCardTokens.GridSpacing),
                        overscrollEffect = rememberCouiOverscrollEffect(),
                    ) {
                        if (page == 0) {
                        val gridFeed = feed
                        when (val currentState = snapshot.state) {
                            ListLoadState.Loading -> item(
                                key = "coui-favorites-loading",
                                span = { GridItemSpan(maxLineSpan) },
                            ) {
                                CouiLoadingGroup(modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                            }

                            is ListLoadState.Failed -> item(
                                key = "coui-favorites-failed",
                                span = { GridItemSpan(maxLineSpan) },
                            ) {
                                CouiScreenFailureGroup(
                                    message = currentState.message,
                                    expired = currentState.sessionExpired,
                                    onRetry = state.loader::loadFirstPage,
                                    onOpenLogin = nav::login,
                                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                                )
                            }

                            ListLoadState.Loaded -> {
                                item(key = "coui-favorites-folders", span = { GridItemSpan(maxLineSpan) }) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = CouiPageDefaults.horizontalMargin, vertical = 4.dp),
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
                                        if (state.folderId != FAVORITE_FOLDER_ALL_ID) {
                                            CouiFavoritesChipButton(
                                                label = "重命名",
                                                onClick = { state.openFolderRename() },
                                            )
                                            CouiFavoritesChipButton(
                                                label = "删除",
                                                onClick = { state.openFolderDelete() },
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
                                                onClick = { nav.album(result.id) },
                                                onMove = { state.openMove(result) },
                                            )
                                        }
                                    }
                                }
                                item(key = "coui-favorites-footer", span = { GridItemSpan(maxLineSpan) }) {
                                    if (gridFeed.items.isNotEmpty()) {
                                        if (clickPaging) {
                                            CouiPagingGroup(
                                                page = gridFeed.page,
                                                pageCount = gridFeed.pageCount,
                                                hasMore = gridFeed.hasMore,
                                                onJumpPage = { target ->
                                                    val resolved = pagerTargetPage(
                                                        gridFeed.page,
                                                        target,
                                                        gridFeed.pageCount,
                                                    )
                                                    if (resolved != gridFeed.page) {
                                                        scope.launch {
                                                            gridState.scrollToItem(0)
                                                            state.loader.goToPage(resolved)
                                                        }
                                                    }
                                                },
                                            )
                                        } else {
                                            CouiAutoLoadMoreEffect(feed = gridFeed, onLoadMore = state.loader::loadMore)
                                            CouiLoadMoreFooter(
                                                feed = gridFeed,
                                                onRetry = state.loader::loadMore,
                                            )
                                        }
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
                                CouiFeedLoginGroup(
                                    message = "登录后查看标签收藏",
                                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                                    onOpenLogin = nav::login,
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
                                        onClick = { state.openAddTag() },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = CouiPageDefaults.horizontalMargin, vertical = 4.dp),
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
                                            onClick = { state.openTagDelete(item.tag) },
                                            subtitle = item.updatedAt?.let { "更新于 " + it } ?: "更新时间未知",
                                            badge = {
                                                CouiFavoritesDeleteBadge(
                                                    contentDescription = "删除标签",
                                                    onClick = { state.openTagDelete(item.tag) },
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
            }

            Dialog(
                onDismissRequest = { folderPickerOpen = false },
                visible = folderPickerOpen,
                material = couiDialogMaterial(),
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
                        label = option.name,
                        selected = option.id == state.folderId,
                        onClick = {
                            folderPickerOpen = false
                            state.switchFolder(option.id)
                        },
                    )
                }
            }

            val folderOps = state.folderOps
            // 弹窗内容按「最近一次非空的目标」留在屏上，关闭动画里不跳字
            val retainedFolderMode = rememberRetainedTarget(folderOps.dialogMode)
            if (retainedFolderMode != null) {
                val creating = retainedFolderMode.first
                Dialog(
                    onDismissRequest = { state.dismissFolderDialogs() },
                    visible = folderOps.dialogVisible,
                    material = couiDialogMaterial(),
                    confirmAction = CouiDialogAction(
                        text = if (creating) FAVORITE_CREATE_CONFIRM else FAVORITE_EDIT_CONFIRM,
                        onClick = { state.submitFolderDialog() },
                        enabled = !folderOps.busy,
                        style = CouiDialogActionStyle.Primary,
                        colors = CouiDialogDefaults.primaryActionColors(scheme.accent),
                    ),
                    title = if (creating) FAVORITE_ADD_FOLDER_TITLE else FAVORITE_EDIT_FOLDER_TITLE,
                    dismissAction = CouiDialogAction(
                        text = FAVORITE_CANCEL_LABEL,
                        onClick = { state.dismissFolderDialogs() },
                        style = CouiDialogActionStyle.Secondary,
                    ),
                    placement = CouiDialogPlacement.Bottom,
                ) {
                    CouiFavoritesTextField(
                        value = folderOps.nameInput,
                        onValueChange = state::setFolderName,
                        placeholder = FAVORITE_FOLDER_NAME_LABEL,
                    )
                    CouiFavoritesInlineError(message = folderOps.errorMessage, color = destructive)
                }
            }

            val deleting = rememberRetainedTarget(folderOps.deleteTarget)
            if (deleting != null) {
                Dialog(
                    onDismissRequest = { state.dismissFolderDialogs() },
                    visible = folderOps.deleteVisible,
                    material = couiDialogMaterial(),
                    confirmAction = couiDestructiveConfirmAction(
                        text = FAVORITE_DELETE_CONFIRM,
                        enabled = !folderOps.busy,
                        onClick = { state.confirmFolderDelete() },
                    ),
                    title = FAVORITE_DELETE_FOLDER_TITLE,
                    message = favoriteDeleteFolderMessage(deleting.second),
                    dismissAction = CouiDialogAction(
                        text = FAVORITE_CANCEL_LABEL,
                        onClick = { state.dismissFolderDialogs() },
                        style = CouiDialogActionStyle.Secondary,
                    ),
                    placement = CouiDialogPlacement.Center,
                ) {
                    CouiFavoritesInlineError(message = folderOps.errorMessage, color = destructive)
                }
            }

            val moveOps = state.moveOps
            val moving = rememberRetainedTarget(moveOps.target)
            if (moving != null) {
                val movableFolders = state.movableFolders
                Dialog(
                    onDismissRequest = { state.dismissMove() },
                    visible = moveOps.visible,
                    material = couiDialogMaterial(),
                    confirmAction = CouiDialogAction(
                        text = FAVORITE_CLOSE_LABEL,
                        onClick = { state.dismissMove() },
                        enabled = !moveOps.busy,
                        style = CouiDialogActionStyle.Secondary,
                    ),
                    title = FAVORITE_MOVE_TITLE,
                    placement = CouiDialogPlacement.Bottom,
                ) {
                    CouiFavoritesInlineError(message = moveOps.errorMessage, color = destructive)
                    if (movableFolders.isEmpty()) {
                        Text(
                            text = FAVORITE_MOVE_EMPTY_HINT,
                            kind = CouiTextKind.GroupFooter,
                            color = scheme.onSurfaceVariant,
                            maxLines = 2,
                            textAlign = TextAlign.Start,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    } else {
                        movableFolders.forEach { folder ->
                            CouiFavoritesPickerRow(
                                label = folder.name,
                                selected = false,
                                onClick = {
                                    if (!moveOps.busy) state.moveToFolder(folder.id, folder.name)
                                },
                            )
                        }
                    }
                }
            }

            val tagOps = state.tagOps
            Dialog(
                onDismissRequest = { state.dismissTagAdd() },
                visible = tagOps.addVisible,
                material = couiDialogMaterial(),
                confirmAction = CouiDialogAction(
                    text = FAVORITE_ADD_CONFIRM,
                    onClick = { state.submitTag() },
                    enabled = !tagOps.busy,
                    style = CouiDialogActionStyle.Primary,
                    colors = CouiDialogDefaults.primaryActionColors(scheme.accent),
                ),
                title = FAVORITE_ADD_TAG_TITLE,
                dismissAction = CouiDialogAction(
                    text = FAVORITE_CANCEL_LABEL,
                    onClick = { state.dismissTagAdd() },
                    enabled = !tagOps.busy,
                    style = CouiDialogActionStyle.Secondary,
                ),
                placement = CouiDialogPlacement.Bottom,
            ) {
                CouiFavoritesTextField(
                    value = tagOps.input,
                    onValueChange = state::setTagInput,
                    placeholder = FAVORITE_TAG_NAME_LABEL,
                )
                CouiFavoritesInlineError(message = tagOps.errorMessage, color = destructive)
            }

            val deletingTag = rememberRetainedTarget(tagOps.deleteTarget)
            if (deletingTag != null) {
                Dialog(
                    onDismissRequest = { state.dismissTagDelete() },
                    visible = tagOps.deleteVisible,
                    material = couiDialogMaterial(),
                    confirmAction = couiDestructiveConfirmAction(
                        text = FAVORITE_DELETE_CONFIRM,
                        enabled = !tagOps.busy,
                        onClick = { state.confirmTagDelete() },
                    ),
                    title = FAVORITE_DELETE_TAG_TITLE,
                    message = favoriteDeleteTagMessage(deletingTag),
                    dismissAction = CouiDialogAction(
                        text = FAVORITE_CANCEL_LABEL,
                        onClick = { state.dismissTagDelete() },
                        style = CouiDialogActionStyle.Secondary,
                    ),
                    placement = CouiDialogPlacement.Center,
                ) {
                    CouiFavoritesInlineError(message = tagOps.errorMessage, color = destructive)
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
                kind = CouiTextKind.GroupFooter,
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
            .couiPressable(
                shape = CouiCapsuleShape,
                backgroundColor = scheme.container,
                spotlight = SpotlightPreset.ChipTab,
                minTouchTarget = CouiMinTouchTarget,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            kind = CouiTextKind.ItemValue,
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
            .couiPressable(
                shape = CircleShape,
                backgroundColor = scheme.container,
                minTouchTarget = CouiMinTouchTarget,
                onClick = onClick,
            )
            .size(28.dp),
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
    CouiBodyText(
        text = label,
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
            CouiBodyText(
                text = placeholder,
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
            textStyle = rememberCouiBodyTextStyle().copy(color = scheme.onSurface),
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
        kind = CouiTextKind.GroupFooter,
        color = color,
        maxLines = 3,
        textAlign = TextAlign.Start,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    )
}
