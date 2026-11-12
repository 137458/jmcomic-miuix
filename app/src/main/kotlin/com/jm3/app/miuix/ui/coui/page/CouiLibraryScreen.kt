// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.page

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.CouiDialogActionStyle
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
import com.jm3.app.miuix.ui.coui.component.rememberCouiActionTextStyle
import com.jm3.app.miuix.ui.coui.component.CouiCustomStyledText
import com.jm3.app.miuix.ui.coui.component.CouiBodyText
import com.jm3.app.miuix.ui.coui.component.CouiAutoLoadMoreEffect
import com.jm3.app.miuix.ui.coui.component.CouiEmptyGroup
import com.jm3.app.miuix.ui.coui.component.CouiFab
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
import com.jm3.app.miuix.ui.coui.component.couiDialogMaterial
import com.jm3.app.miuix.ui.coui.component.couiPressable
import com.jm3.app.miuix.ui.coui.component.rememberRetainedTarget
import com.jm3.app.miuix.ui.coui.icon.CouiIcons
import com.jm3.app.miuix.ui.coui.theme.CouiStrings
import com.jm3.app.miuix.ui.coui.theme.CouiTheme
import com.jm3.app.miuix.ui.libraryPageCount
import com.jm3.app.miuix.ui.list.ListLoadState
import com.jm3.app.miuix.ui.list.SessionListLoader
import com.jm3.app.miuix.ui.readableWidth
import com.jm3.app.miuix.ui.rememberSyncedTabPagerState
import com.jm3.app.miuix.ui.route.AppRoutes
import com.jm3.core.model.CreatorFilters
import com.jm3.core.model.CreatorMeta
import com.jm3.core.model.CreatorPage
import com.jm3.core.model.CreatorWorkMeta
import com.jm3.core.model.CreatorWorkPage
import com.jm3.core.paging.PageSlice
import com.jm3.core.paging.PagedFeed
import com.jm3.core.paging.hasMorePages
import com.jm3.core.paging.pagerTargetPage
import com.jm3.core.session.SessionOutcome
import com.jm3.core.settings.PaginationMode
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.SnackbarHostState

/** 原版列表每页 30 条；接口只返回总数，总页数由 [libraryPageCount] 推算。 */
private const val COUI_LIBRARY_PAGE_SIZE = 30

private val COUI_LIBRARY_TABS = listOf("创作者", "作品")

/** 语言/来源筛选的取数弹窗：一次只开一个。 */
private enum class CouiLibraryPicker { Languages, Sources }

/**
 * 书库（COUI 栅格版）：创作者 / 作品双 Tab，参数与旧 Miuix 页一比一。
 *
 * 旧页的语言/来源是两个下拉胶囊，COUI 没有下拉件，改为点击弹出底部选择弹窗；
 * 旧页的下拉刷新在 COUI 没有对应件，改为右下角 FAB 刷新（与小说列表同一口径）。
 */
@Composable
internal fun CouiLibraryScreen(
    deps: ScreenDeps,
    onBack: () -> Unit,
    nav: AppRoutes,
    snackbarHostState: SnackbarHostState,
) {
    CouiTheme {
        val scope = rememberCoroutineScope()
        val creatorGridState = rememberLazyGridState()
        val workGridState = rememberLazyGridState()
        val columns = LocalWideLayout.current.gridColumns
        val paginationMode = deps.paging.paginationMode

        var tab by rememberSaveable { mutableIntStateOf(0) }
        var query by rememberSaveable { mutableStateOf("") }
        var appliedQuery by rememberSaveable { mutableStateOf("") }
        var lang by rememberSaveable { mutableStateOf("") }
        var source by rememberSaveable { mutableStateOf("") }
        var filters by rememberSaveable { mutableStateOf(CreatorFilters()) }
        var picker by remember { mutableStateOf<CouiLibraryPicker?>(null) }

        // 两个 Tab 各一个 loader：作者与作品条目类型不同，各自持有 feed 与在途请求也不会互相打断。
        // 查询条件与翻页模式都进 remember 键，条件一变就换实例，等于「丢掉已加载的页从第 1 页重来」。
        val creatorLoader = remember(appliedQuery, paginationMode, snackbarHostState) {
            SessionListLoader(
                scope = scope,
                idOf = { item: CreatorMeta -> item.id },
                failureMessage = CouiStrings.LoadFailed,
                fetchPage = { page ->
                    when (val outcome = deps.authCall.call { deps.gateway.creatorAuthors(page, appliedQuery) }) {
                        is SessionOutcome.Ok -> SessionOutcome.Ok(
                            couiLibrarySlice(outcome.value.items, outcome.value.total, page),
                        )

                        SessionOutcome.Expired -> SessionOutcome.Expired
                        is SessionOutcome.Failed -> outcome
                    }
                },
                notice = { message -> scope.launch { snackbarHostState.showSnackbar(message) } },
            )
        }
        val workLoader = remember(appliedQuery, lang, source, paginationMode, snackbarHostState) {
            SessionListLoader(
                scope = scope,
                idOf = { item: CreatorWorkMeta -> item.id },
                failureMessage = CouiStrings.LoadFailed,
                fetchPage = { page ->
                    when (
                        val outcome = deps.authCall.call {
                            deps.gateway.creatorWorks(page, appliedQuery, lang, source)
                        }
                    ) {
                        is SessionOutcome.Ok -> {
                            // 语言/来源选项随作品页返回，顺手更新筛选弹窗的候选
                            filters = outcome.value.filters
                            SessionOutcome.Ok(
                                couiLibrarySlice(outcome.value.items, outcome.value.total, page),
                            )
                        }

                        SessionOutcome.Expired -> SessionOutcome.Expired
                        is SessionOutcome.Failed -> outcome
                    }
                },
                notice = { message -> scope.launch { snackbarHostState.showSnackbar(message) } },
            )
        }
        val creatorSnapshot by creatorLoader.snapshot.collectAsState()
        val workSnapshot by workLoader.snapshot.collectAsState()
        val creatorFeed = creatorSnapshot.feed
        val workFeed = workSnapshot.feed
        val activeLoader = if (tab == 0) creatorLoader else workLoader
        val activeRefreshing = if (tab == 0) creatorSnapshot.refreshing else workSnapshot.refreshing

        // 每个 loader 只在第一次被切到时拉首屏：切回已加载的 Tab 不该重复请求
        var creatorStarted by remember(creatorLoader) { mutableStateOf(false) }
        var workStarted by remember(workLoader) { mutableStateOf(false) }

        LaunchedEffect(tab, creatorLoader, workLoader) {
            if (tab == 0) {
                if (!creatorStarted) {
                    creatorStarted = true
                    creatorLoader.loadFirstPage()
                }
            } else if (!workStarted) {
                workStarted = true
                workLoader.loadFirstPage()
            }
        }

        val scheme = CouiKitTheme.colorScheme
        val pagerState = rememberSyncedTabPagerState(
            selectedTab = tab,
            pageCount = COUI_LIBRARY_TABS.size,
            onTabSelected = { index -> if (index != tab) tab = index },
        )
        val scrollBehavior = rememberCouiScreenScrollBehavior {
            val activeState = if (tab == 0) creatorGridState else workGridState
            activeState.canScrollForward || activeState.canScrollBackward
        }

        Box(modifier = Modifier.fillMaxSize()) {
            CouiScreenScaffold(
                title = "书库",
                onBack = onBack,
                scrollBehavior = scrollBehavior,
                topBarBottom = {
                    CouiTopBarSegmentedTabs(
                        items = COUI_LIBRARY_TABS,
                        selectedIndex = tab,
                        onSelect = { index -> if (index != tab) tab = index },
                    )
                },
                actions = {
                    if (appliedQuery.isNotBlank() || query.isNotBlank()) {
                        CouiTopBarAction(
                            icon = CouiIcons.Close,
                            contentDescription = "清空搜索",
                            onClick = {
                                query = ""
                                appliedQuery = ""
                            },
                        )
                    }
                },
                floatingActionButton = {
                    CouiFab(
                        icon = CouiIcons.Refresh,
                        contentDescription = "刷新",
                        onClick = activeLoader::refresh,
                        rotationDegrees = if (activeRefreshing) 180f else 0f,
                    )
                },
            ) { innerPadding ->
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    val pageFeed: PagedFeed<*> = if (page == 0) creatorFeed else workFeed
                    val pageItemCount = pageFeed.items.size
                    val pageTotal = pageFeed.total ?: 0
                    val pageTotalPages = libraryPageCount(pageTotal)
                    val pageState = if (page == 0) creatorSnapshot.state else workSnapshot.state
                    val pageLoader = if (page == 0) creatorLoader else workLoader
                    val pageGridState = if (page == 0) creatorGridState else workGridState

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        state = pageGridState,
                        modifier = Modifier
                            .fillMaxSize()
                            .readableWidth()
                            .consumeWindowInsets(innerPadding),
                        contentPadding = innerPadding,
                        horizontalArrangement = Arrangement.spacedBy(CouiWorkCardTokens.GridSpacing),
                        verticalArrangement = Arrangement.spacedBy(CouiWorkCardTokens.GridSpacing),
                        overscrollEffect = rememberCouiOverscrollEffect(),
                    ) {
                        when {
                            pageState is ListLoadState.Loading -> item(
                                key = "coui-library-loading",
                                span = { GridItemSpan(maxLineSpan) },
                            ) {
                                CouiLoadingGroup(modifier = Modifier.fillMaxWidth().padding(top = 24.dp))
                            }

                            pageState is ListLoadState.Failed -> item(
                                key = "coui-library-failed",
                                span = { GridItemSpan(maxLineSpan) },
                            ) {
                                CouiScreenFailureGroup(
                                    message = pageState.message,
                                    expired = pageState.sessionExpired,
                                    // 失败时 feed 还停在上一次成功的页码，重试就打这一页
                                    onRetry = { pageLoader.goToPage(pageFeed.page.coerceAtLeast(1)) },
                                    onOpenLogin = nav::login,
                                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                                )
                            }

                            else -> {
                                item(key = "coui-library-search", span = { GridItemSpan(maxLineSpan) }) {
                                    CouiLibrarySearchField(
                                        value = query,
                                        onValueChange = { query = it },
                                        onSubmit = { appliedQuery = query.trim() },
                                        placeholder = if (page == 0) "搜索创作者" else "搜索作品",
                                    )
                                }
                                if (page == 1 && !filters.isEmpty) {
                                    item(key = "coui-library-filters", span = { GridItemSpan(maxLineSpan) }) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = CouiPageDefaults.horizontalMargin, vertical = 2.dp),
                                            horizontalArrangement = Arrangement.spacedBy(
                                                CouiWorkCardTokens.GridSpacing,
                                            ),
                                        ) {
                                            if (filters.languages.isNotEmpty()) {
                                                CouiLibraryFilterChip(
                                                    label = "语言：" + lang.ifBlank { "全部" },
                                                    onClick = { picker = CouiLibraryPicker.Languages },
                                                    modifier = Modifier.weight(1f),
                                                )
                                            }
                                            if (filters.sources.isNotEmpty()) {
                                                CouiLibraryFilterChip(
                                                    label = "来源：" + source.ifBlank { "全部" },
                                                    onClick = { picker = CouiLibraryPicker.Sources },
                                                    modifier = Modifier.weight(1f),
                                                )
                                            }
                                        }
                                    }
                                }
                                if (pageItemCount == 0) {
                                    item(key = "coui-library-empty", span = { GridItemSpan(maxLineSpan) }) {
                                        CouiEmptyGroup(
                                            text = if (appliedQuery.isBlank()) {
                                                "换个筛选条件再试试"
                                            } else {
                                                "没有找到相关内容"
                                            },
                                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                                        )
                                    }
                                } else {
                                    item(key = "coui-library-total", span = { GridItemSpan(maxLineSpan) }) {
                                        if (pageTotal > 0) {
                                            Text(
                                                text = "共 " + pageTotal + " 项",
                                                kind = CouiTextKind.GroupFooter,
                                                color = scheme.onSurfaceVariant,
                                                maxLines = 1,
                                                textAlign = TextAlign.Start,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = CouiPageDefaults.horizontalMargin)
                                                    .padding(top = 4.dp),
                                            )
                                        }
                                    }
                                    if (page == 0) {
                                        items(items = creatorFeed.items, key = { "coui-creator-" + it.id }) { creator ->
                                            Box(
                                                modifier = Modifier.fillMaxWidth(),
                                                contentAlignment = Alignment.TopCenter,
                                            ) {
                                                CouiWorkGridCard(
                                                    title = creator.name.ifBlank { creator.id },
                                                    coverUrl = creator.avatarUrl ?: creator.backgroundUrl,
                                                    onClick = { nav.creator(creator.id) },
                                                    coverContentDescription = creator.name,
                                                    subtitle = creator.updateText?.takeIf { it.isNotBlank() }?.let {
                                                        "更新于 " + it
                                                    },
                                                )
                                            }
                                        }
                                    } else {
                                        items(items = workFeed.items, key = { "coui-work-" + it.id }) { work ->
                                            Box(
                                                modifier = Modifier.fillMaxWidth(),
                                                contentAlignment = Alignment.TopCenter,
                                            ) {
                                                CouiWorkGridCard(
                                                    title = work.title.ifBlank { "无题" },
                                                    coverUrl = work.coverUrl,
                                                    onClick = { nav.creatorWork(work.id) },
                                                    coverContentDescription = work.title,
                                                    subtitle = work.authorName?.takeIf { it.isNotBlank() },
                                                    category = work.platform?.takeIf { it.isNotBlank() },
                                                    footer = {
                                                        CouiLibraryWorkDate(workDate = work.workDate)
                                                    },
                                                )
                                            }
                                        }
                                    }
                                    item(key = "coui-library-footer", span = { GridItemSpan(maxLineSpan) }) {
                                        if (paginationMode == PaginationMode.CLICK) {
                                            CouiPagingGroup(
                                                page = pageFeed.page,
                                                pageCount = pageTotalPages,
                                                hasMore = pageFeed.hasMore,
                                                onJumpPage = { target ->
                                                    val resolved = pagerTargetPage(
                                                        pageFeed.page,
                                                        target,
                                                        pageTotalPages,
                                                    )
                                                    if (resolved != pageFeed.page) {
                                                        scope.launch {
                                                            pageGridState.scrollToItem(0)
                                                            pageLoader.goToPage(resolved)
                                                        }
                                                    }
                                                },
                                            )
                                        } else {
                                            CouiAutoLoadMoreEffect(
                                                feed = pageFeed,
                                                onLoadMore = pageLoader::loadMore,
                                            )
                                            CouiLoadMoreFooter(
                                                feed = pageFeed,
                                                onRetry = pageLoader::loadMore,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            val openPicker = rememberRetainedTarget(picker)
            if (openPicker != null) {
                val options = if (openPicker == CouiLibraryPicker.Languages) {
                    filters.languages
                } else {
                    filters.sources
                }
                val selected = if (openPicker == CouiLibraryPicker.Languages) lang else source
                val all = listOf("全部") + options.distinct()
                Dialog(
                    onDismissRequest = { picker = null },
                    visible = picker != null,
                    material = couiDialogMaterial(),
                    confirmAction = CouiDialogAction(
                        text = "关闭",
                        onClick = { picker = null },
                        style = CouiDialogActionStyle.Secondary,
                    ),
                    title = if (openPicker == CouiLibraryPicker.Languages) "语言" else "来源",
                    placement = CouiDialogPlacement.Bottom,
                ) {
                    all.forEach { option ->
                        val value = if (option == "全部") "" else option
                        CouiLibraryPickerRow(
                            label = option,
                            selected = value == selected,
                            onClick = {
                                if (openPicker == CouiLibraryPicker.Languages) lang = value else source = value
                                picker = null
                            },
                        )
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

/** 作品卡额外行：作品日期；缺失时不留空行。 */
@Composable
private fun ColumnScope.CouiLibraryWorkDate(workDate: String?) {
    val date = workDate?.takeIf { it.isNotBlank() } ?: return
    Text(
        text = date,
        kind = CouiTextKind.GroupFooter,
        color = CouiKitTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        textAlign = TextAlign.Start,
        modifier = Modifier.padding(top = CouiWorkCardTokens.TextGap),
    )
}

/** 筛选取数按钮：显示当前值，点击打开底部选择弹窗。 */
@Composable
private fun CouiLibraryFilterChip(
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

/** 筛选弹窗里的一行：选中项用强调色。 */
@Composable
private fun CouiLibraryPickerRow(
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

/** 正文内胶囊搜索行：IME 搜索键或右侧「搜索」提交。 */
@Composable
private fun CouiLibrarySearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    placeholder: String,
) {
    val scheme = CouiKitTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CouiPageDefaults.horizontalMargin)
            .clip(CouiCapsuleShape)
            .background(scheme.container)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    kind = CouiTextKind.Search,
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
                textStyle = rememberCouiTextStyle(CouiTextKind.Search).copy(color = scheme.onSurface),
                cursorBrush = SolidColor(scheme.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            CouiCustomStyledText(
                text = "搜索",
                style = rememberCouiActionTextStyle(),
                color = scheme.accent,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier
                    .clip(CouiCapsuleShape)
                    .couiPressable(
                        shape = CouiCapsuleShape, spotlight = SpotlightPreset.ChipTab,
                        minTouchTarget = CouiMinTouchTarget,
                        onClick = onSubmit,
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

/** 把书库接口的一页折算成 PagedFeed 切片：末页判据用页码，条目数不参与。 */
private fun <T> couiLibrarySlice(items: List<T>, total: Int, page: Int): PageSlice<T> {
    val pageCount = libraryPageCount(total)
    return PageSlice(
        items = items,
        hasMore = hasMorePages(page, pageCount),
        total = total,
        page = page,
        pageCount = pageCount,
    )
}
