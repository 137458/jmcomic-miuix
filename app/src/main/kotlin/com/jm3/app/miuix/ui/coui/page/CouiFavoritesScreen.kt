// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.page

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.ui.GlassSnackbarHost
import com.jm3.app.miuix.ui.LocalWideLayout
import com.jm3.app.miuix.ui.authorNames
import com.jm3.app.miuix.ui.coui.component.CouiAutoLoadMoreEffect
import com.jm3.app.miuix.ui.coui.component.CouiBodyText
import com.jm3.app.miuix.ui.coui.component.CouiEmptyGroup
import com.jm3.app.miuix.ui.coui.component.CouiErrorGroup
import com.jm3.app.miuix.ui.coui.component.CouiExpandTopAppBarEffect
import com.jm3.app.miuix.ui.coui.component.CouiFeedLoginGroup
import com.jm3.app.miuix.ui.coui.component.CouiLayoutTokens
import com.jm3.app.miuix.ui.coui.component.CouiLoadMoreFooter
import com.jm3.app.miuix.ui.coui.component.CouiLoadingGroup
import com.jm3.app.miuix.ui.coui.component.CouiMinTouchTarget
import com.jm3.app.miuix.ui.coui.component.CouiPageScaffold
import com.jm3.app.miuix.ui.coui.component.CouiPageTopBarAction
import com.jm3.app.miuix.ui.coui.component.CouiPageTopBarSegmentedTabs
import com.jm3.app.miuix.ui.coui.component.CouiPagingGroup
import com.jm3.app.miuix.ui.coui.component.CouiPullToRefresh
import com.jm3.app.miuix.ui.coui.component.CouiScreenFailureGroup
import com.jm3.app.miuix.ui.coui.component.CouiWorkCardTokens
import com.jm3.app.miuix.ui.coui.component.CouiWorkGridCard
import com.jm3.app.miuix.ui.coui.component.couiDestructiveDialogAction
import com.jm3.app.miuix.ui.coui.component.couiDialogSurfaceColor
import com.jm3.app.miuix.ui.coui.component.couiPageScroll
import com.jm3.app.miuix.ui.coui.component.couiPressable
import com.jm3.app.miuix.ui.coui.component.isCouiWideWindow
import com.jm3.app.miuix.ui.coui.component.panelBlur
import com.jm3.app.miuix.ui.coui.component.rememberCouiBodyTextStyle
import com.jm3.app.miuix.ui.coui.component.rememberCouiPageScrollBehavior
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
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.blur.MaterialBlurDefaults
import io.github.suqi8.coui.kmp.layout.DialogButtonBar
import io.github.suqi8.coui.kmp.layout.DialogButtonBarAction
import io.github.suqi8.coui.kmp.layout.DialogDefaults
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.rememberCOUIFlingBehavior
import kotlinx.coroutines.launch
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
        val scheme = COUITheme.colorScheme
        val destructive = scheme.error

        // COUI 独有：收藏夹选择走弹窗，开关自己留在这里
        var folderPickerOpen by rememberSaveable { mutableStateOf(false) }

        // 标签收藏：null 表示加载中，空列表表示无数据；COUI 页手写这份加载，语义与旧页一致（会话失效静默）
        var tags by remember { mutableStateOf<List<TagFavorite>?>(null) }
        var tagsError by remember { mutableStateOf<String?>(null) }
        var tagsRefreshing by remember { mutableStateOf(false) }

        /** 标签收藏列表；未登录不发请求，由界面直接展示登录提示。 */
        fun loadTags(refreshing: Boolean = false) {
            // 绑定发起请求时的用户，避免登出/换号后把旧结果写回界面
            val uid = user?.uid ?: return
            scope.launch {
                if (refreshing && tags != null) {
                    tagsRefreshing = true
                } else {
                    tagsError = null
                    tags = null
                }
                val outcome = deps.authCall.call { deps.gateway.tagsFavorite() }
                tagsRefreshing = false
                if (user?.uid != uid) return@launch
                when (outcome) {
                    is SessionOutcome.Ok -> {
                        tags = outcome.value
                        tagsError = null
                    }
                    // 会话失效后用户会置空，标签页自动回落到未登录引导，不必再留错误文案
                    SessionOutcome.Expired -> Unit
                    is SessionOutcome.Failed -> {
                        if (refreshing && tags != null) {
                            snackbarHostState.showSnackbar("标签收藏加载失败，请稍后重试")
                        } else {
                            tagsError = "标签收藏加载失败，请稍后重试"
                        }
                    }
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
                tagsRefreshing = false
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
        val activeCanScroll by remember(pagerState, gridState, tagsGridState) {
            derivedStateOf {
                val activeState = if (pagerState.currentPage == FAVORITE_TAB_FOLDERS) gridState else tagsGridState
                activeState.canScrollForward || activeState.canScrollBackward
            }
        }
        val scrollBehavior = rememberCouiPageScrollBehavior { activeCanScroll }
        CouiExpandTopAppBarEffect(
            scrollBehavior = scrollBehavior,
            activeTab = pagerState.currentPage,
            canScroll = activeCanScroll,
        )

        Box(modifier = Modifier.fillMaxSize()) {
            CouiPageScaffold(
                title = "我的收藏",
                onBack = onBack,
                wide = isCouiWideWindow(),
                scrollBehavior = scrollBehavior,
                attachScrollNestedScroll = false,
                topBarBottom = {
                    CouiPageTopBarSegmentedTabs(
                        items = state.tabs,
                        selectedIndex = state.tabIndex,
                        onSelect = { index -> state.selectTab(index) },
                    )
                },
                actions = {
                    CouiPageTopBarAction(
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
                Box(modifier = Modifier.fillMaxSize()) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                    ) { page ->
                        CouiPullToRefresh(
                            isRefreshing = if (page == FAVORITE_TAB_FOLDERS) snapshot.refreshing else tagsRefreshing,
                            onRefresh = {
                                if (page == FAVORITE_TAB_FOLDERS) {
                                    state.loader.refresh()
                                } else {
                                    loadTags(refreshing = true)
                                }
                            },
                            scrollBehavior = scrollBehavior,
                            contentPadding = innerPadding,
                        ) {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(columns),
                                state = if (page == 0) gridState else tagsGridState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .readableWidth()
                                    .couiPageScroll()
                                    .consumeWindowInsets(innerPadding),
                                contentPadding = innerPadding,
                                horizontalArrangement = Arrangement.spacedBy(CouiWorkCardTokens.GridSpacing),
                                verticalArrangement = Arrangement.spacedBy(CouiWorkCardTokens.GridSpacing),
                                flingBehavior = rememberCOUIFlingBehavior(),
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
                                                        .padding(
                                                            horizontal = CouiLayoutTokens.HorizontalMargin,
                                                            vertical = 4.dp,
                                                        ),
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
                                                        .padding(
                                                            horizontal = CouiLayoutTokens.HorizontalMargin,
                                                            vertical = 4.dp,
                                                        ),
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

                    val dialogBlur = panelBlur(MaterialBlurDefaults::dialog)

                    OverlayDialog(
                        show = folderPickerOpen,
                        title = "收藏夹",
                        backgroundModifier = dialogBlur?.backgroundModifier ?: Modifier,
                        backgroundColor = dialogBlur?.backgroundColor ?: DialogDefaults.backgroundColor(),
                        materialStroke = dialogBlur != null,
                        onDismissRequest = { folderPickerOpen = false },
                        customContent = {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 24.dp),
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
                        },
                    ) {
                        DialogButtonBar(
                            negative = DialogButtonBarAction(
                                text = "关闭",
                                onClick = { folderPickerOpen = false },
                            ),
                            positive = null,
                        )
                    }

                    val folderOps = state.folderOps
                    // 弹窗内容按「最近一次非空的目标」留在屏上，关闭动画里不跳字
                    val retainedFolderMode = rememberRetainedTarget(folderOps.dialogMode)
                    if (retainedFolderMode != null) {
                        val creating = retainedFolderMode.first
                        OverlayDialog(
                            show = folderOps.dialogVisible,
                            title = if (creating) FAVORITE_ADD_FOLDER_TITLE else FAVORITE_EDIT_FOLDER_TITLE,
                            backgroundModifier = dialogBlur?.backgroundModifier ?: Modifier,
                            backgroundColor = dialogBlur?.backgroundColor ?: DialogDefaults.backgroundColor(),
                            materialStroke = dialogBlur != null,
                            onDismissRequest = { state.dismissFolderDialogs() },
                            customContent = {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp),
                                ) {
                                    CouiFavoritesTextField(
                                        value = folderOps.nameInput,
                                        onValueChange = state::setFolderName,
                                        placeholder = FAVORITE_FOLDER_NAME_LABEL,
                                    )
                                    CouiFavoritesInlineError(message = folderOps.errorMessage, color = destructive)
                                }
                            },
                        ) {
                            DialogButtonBar(
                                negative = DialogButtonBarAction(
                                    text = FAVORITE_CANCEL_LABEL,
                                    onClick = { state.dismissFolderDialogs() },
                                ),
                                positive = DialogButtonBarAction(
                                    text = if (creating) FAVORITE_CREATE_CONFIRM else FAVORITE_EDIT_CONFIRM,
                                    enabled = !folderOps.busy,
                                    recommended = true,
                                    onClick = { state.submitFolderDialog() },
                                ),
                            )
                        }
                    }

                    val deleting = rememberRetainedTarget(folderOps.deleteTarget)
                    if (deleting != null) {
                        OverlayDialog(
                            show = folderOps.deleteVisible,
                            title = FAVORITE_DELETE_FOLDER_TITLE,
                            summary = favoriteDeleteFolderMessage(deleting.second),
                            backgroundModifier = dialogBlur?.backgroundModifier ?: Modifier,
                            backgroundColor = dialogBlur?.backgroundColor ?: DialogDefaults.backgroundColor(),
                            materialStroke = dialogBlur != null,
                            onDismissRequest = { state.dismissFolderDialogs() },
                            customContent = folderOps.errorMessage?.let { errMsg ->
                                {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 24.dp),
                                    ) {
                                        CouiFavoritesInlineError(message = errMsg, color = destructive)
                                    }
                                }
                            },
                        ) {
                            DialogButtonBar(
                                negative = DialogButtonBarAction(
                                    text = FAVORITE_CANCEL_LABEL,
                                    onClick = { state.dismissFolderDialogs() },
                                ),
                                positive = null,
                                neutral = couiDestructiveDialogAction(
                                    text = FAVORITE_DELETE_CONFIRM,
                                    enabled = !folderOps.busy,
                                    onClick = { state.confirmFolderDelete() },
                                ),
                            )
                        }
                    }

                    val moveOps = state.moveOps
                    val moving = rememberRetainedTarget(moveOps.target)
                    if (moving != null) {
                        val movableFolders = state.movableFolders
                        OverlayDialog(
                            show = moveOps.visible,
                            title = FAVORITE_MOVE_TITLE,
                            backgroundModifier = dialogBlur?.backgroundModifier ?: Modifier,
                            backgroundColor = dialogBlur?.backgroundColor ?: DialogDefaults.backgroundColor(),
                            materialStroke = dialogBlur != null,
                            onDismissRequest = { state.dismissMove() },
                            customContent = {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .verticalScroll(rememberScrollState())
                                        .padding(horizontal = 24.dp),
                                ) {
                                    CouiFavoritesInlineError(message = moveOps.errorMessage, color = destructive)
                                    if (movableFolders.isEmpty()) {
                                        Text(
                                            text = FAVORITE_MOVE_EMPTY_HINT,
                                            style = COUITheme.textStyles.bodyS,
                                            color = scheme.labelSecondary,
                                            maxLines = 2,
                                            textAlign = TextAlign.Start,
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
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
                            },
                        ) {
                            DialogButtonBar(
                                negative = DialogButtonBarAction(
                                    text = FAVORITE_CLOSE_LABEL,
                                    enabled = !moveOps.busy,
                                    onClick = { state.dismissMove() },
                                ),
                                positive = null,
                            )
                        }
                    }

                    val tagOps = state.tagOps
                    OverlayDialog(
                        show = tagOps.addVisible,
                        title = FAVORITE_ADD_TAG_TITLE,
                        backgroundModifier = dialogBlur?.backgroundModifier ?: Modifier,
                        backgroundColor = dialogBlur?.backgroundColor ?: DialogDefaults.backgroundColor(),
                        materialStroke = dialogBlur != null,
                        onDismissRequest = { state.dismissTagAdd() },
                        customContent = {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp),
                            ) {
                                CouiFavoritesTextField(
                                    value = tagOps.input,
                                    onValueChange = state::setTagInput,
                                    placeholder = FAVORITE_TAG_NAME_LABEL,
                                )
                                CouiFavoritesInlineError(message = tagOps.errorMessage, color = destructive)
                            }
                        },
                    ) {
                        DialogButtonBar(
                            negative = DialogButtonBarAction(
                                text = FAVORITE_CANCEL_LABEL,
                                enabled = !tagOps.busy,
                                onClick = { state.dismissTagAdd() },
                            ),
                            positive = DialogButtonBarAction(
                                text = FAVORITE_ADD_CONFIRM,
                                enabled = !tagOps.busy,
                                recommended = true,
                                onClick = { state.submitTag() },
                            ),
                        )
                    }

                    val deletingTag = rememberRetainedTarget(tagOps.deleteTarget)
                    if (deletingTag != null) {
                        OverlayDialog(
                            show = tagOps.deleteVisible,
                            title = FAVORITE_DELETE_TAG_TITLE,
                            summary = favoriteDeleteTagMessage(deletingTag),
                            backgroundModifier = dialogBlur?.backgroundModifier ?: Modifier,
                            backgroundColor = dialogBlur?.backgroundColor ?: DialogDefaults.backgroundColor(),
                            materialStroke = dialogBlur != null,
                            onDismissRequest = { state.dismissTagDelete() },
                            customContent = tagOps.errorMessage?.let { errMsg ->
                                {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 24.dp),
                                    ) {
                                        CouiFavoritesInlineError(message = errMsg, color = destructive)
                                    }
                                }
                            },
                        ) {
                            DialogButtonBar(
                                negative = DialogButtonBarAction(
                                    text = FAVORITE_CANCEL_LABEL,
                                    onClick = { state.dismissTagDelete() },
                                ),
                                positive = null,
                                neutral = couiDestructiveDialogAction(
                                    text = FAVORITE_DELETE_CONFIRM,
                                    enabled = !tagOps.busy,
                                    onClick = { state.confirmTagDelete() },
                                ),
                            )
                        }
                    }
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
            val scheme = COUITheme.colorScheme
            Text(
                text = "移动到收藏夹",
                style = COUITheme.textStyles.bodyS,
                color = scheme.primaryText,
                maxLines = 1,
                textAlign = TextAlign.Start,
                modifier = Modifier
                    .padding(top = CouiWorkCardTokens.TextGap)
                    .clip(CircleShape)
                    .couiPressable(
                        shape = CircleShape,
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
    val scheme = COUITheme.colorScheme
    Box(
        modifier = modifier
            .couiPressable(
                shape = CircleShape,
                backgroundColor = scheme.cardBackground,
                minTouchTarget = CouiMinTouchTarget,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = COUITheme.textStyles.bodyM,
            color = scheme.labelPrimary,
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
    val scheme = COUITheme.colorScheme
    Box(
        modifier = modifier
            .couiPressable(
                shape = CircleShape,
                backgroundColor = scheme.cardBackground,
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
            tint = scheme.labelSecondary,
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
    val scheme = COUITheme.colorScheme
    CouiBodyText(
        text = label,
        color = if (selected) scheme.primaryText else scheme.labelPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Start,
        modifier = Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .couiPressable(
                shape = CircleShape,
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
    val scheme = COUITheme.colorScheme
    val shape = RoundedCornerShape(LocalSkin.current.shapes.controlCorner)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(shape)
            .background(couiDialogSurfaceColor())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            CouiBodyText(
                text = placeholder,
                color = scheme.labelSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = rememberCouiBodyTextStyle().copy(color = scheme.labelPrimary),
            cursorBrush = SolidColor(scheme.primary),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 弹窗内的失败文案：没有失败时不留空行。 */
@Composable
private fun CouiFavoritesInlineError(message: String?, color: Color) {
    if (message == null) return
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = message,
        style = COUITheme.textStyles.bodyS,
        color = color,
        maxLines = 3,
        textAlign = TextAlign.Start,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}
