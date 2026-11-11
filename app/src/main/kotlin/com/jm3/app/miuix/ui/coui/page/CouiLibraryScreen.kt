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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.jm3.app.miuix.ui.coui.component.CouiAutoLoadMoreEffect
import com.jm3.app.miuix.ui.coui.component.CouiEmptyGroup
import com.jm3.app.miuix.ui.coui.component.CouiErrorGroup
import com.jm3.app.miuix.ui.coui.component.CouiFab
import com.jm3.app.miuix.ui.coui.component.CouiLoadMoreFooter
import com.jm3.app.miuix.ui.coui.component.CouiLoadingGroup
import com.jm3.app.miuix.ui.coui.component.CouiMinTouchTarget
import com.jm3.app.miuix.ui.coui.component.CouiPagingGroup
import com.jm3.app.miuix.ui.coui.component.CouiScreenScaffold
import com.jm3.app.miuix.ui.coui.component.CouiSegmentedCapsules
import com.jm3.app.miuix.ui.coui.component.CouiTopBarAction
import com.jm3.app.miuix.ui.coui.component.CouiWorkCardTokens
import com.jm3.app.miuix.ui.coui.component.CouiWorkGridCard
import com.jm3.app.miuix.ui.coui.component.couiPressable
import com.jm3.app.miuix.ui.coui.icon.CouiIcons
import com.jm3.app.miuix.ui.coui.theme.CouiTheme
import com.jm3.app.miuix.ui.libraryPageCount
import com.jm3.app.miuix.ui.planLoad
import com.jm3.app.miuix.ui.readableWidth
import com.jm3.core.model.CreatorFilters
import com.jm3.core.model.CreatorMeta
import com.jm3.core.model.CreatorPage
import com.jm3.core.model.CreatorWorkMeta
import com.jm3.core.model.CreatorWorkPage
import com.jm3.core.paging.PageSlice
import com.jm3.core.paging.PagedFeed
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
fun CouiLibraryScreen(
    deps: ScreenDeps,
    onBack: () -> Unit,
    onOpenCreator: (String) -> Unit,
    onOpenWork: (String) -> Unit,
    snackbarHostState: SnackbarHostState,
    onOpenLogin: () -> Unit = {},
) {
    CouiTheme {
        val scope = rememberCoroutineScope()
        val gridState = rememberLazyGridState()
        val columns = LocalWideLayout.current.gridColumns
        val paginationMode = deps.preferences.paginationMode

        var tab by rememberSaveable { mutableIntStateOf(0) }
        var query by rememberSaveable { mutableStateOf("") }
        var appliedQuery by rememberSaveable { mutableStateOf("") }
        var lang by rememberSaveable { mutableStateOf("") }
        var source by rememberSaveable { mutableStateOf("") }
        // 两个 Tab 各一份分页状态：作者与作品的条目类型不同，不能共用一份 feed
        var creatorFeed by remember { mutableStateOf(PagedFeed<CreatorMeta>()) }
        var workFeed by remember { mutableStateOf(PagedFeed<CreatorWorkMeta>()) }
        var filters by rememberSaveable { mutableStateOf(CreatorFilters()) }
        var loading by remember { mutableStateOf(true) }
        var refreshing by remember { mutableStateOf(false) }
        var failure by remember { mutableStateOf<ScreenLoadPlan?>(null) }
        // 切 Tab/筛选/搜索时旧响应不能覆盖新结果
        var loadToken by remember { mutableIntStateOf(0) }
        var picker by remember { mutableStateOf<CouiLibraryPicker?>(null) }

        fun applyFailure(outcome: SessionOutcome<*>, hasContent: Boolean) {
            val plan = planLoad(outcome, hasContent = hasContent, failureMessage = "加载失败")
            if (plan.keepContent) {
                scope.launch { snackbarHostState.showSnackbar(plan.notice.orEmpty()) }
            } else {
                failure = plan
            }
        }

        fun load(targetPage: Int, append: Boolean) {
            val tabIndex = tab
            val keyword = appliedQuery
            // 在不在拉、还能不能拉都交给 feed：重复触底不会再并发打同一页
            val creatorRequest = if (append && tabIndex == 0) creatorFeed.beginLoadMore() else null
            val workRequest = if (append && tabIndex != 0) workFeed.beginLoadMore() else null
            // 拦截必须在占用 token 之前：被拦下的重复触底若先自增 token，在途请求的结果会被丢弃，
            // feed 就永远停在 loadingMore，页脚一直转圈且再也拉不动。
            if (creatorRequest === creatorFeed || workRequest === workFeed) return
            loadToken++
            val token = loadToken
            if (creatorRequest != null) creatorFeed = creatorRequest
            if (workRequest != null) workFeed = workRequest
            if (!append) loading = true
            failure = null
            val hasContent = append || if (tabIndex == 0) {
                creatorFeed.items.isNotEmpty()
            } else {
                workFeed.items.isNotEmpty()
            }
            scope.launch {
                val outcome = deps.authCall.call {
                    if (tabIndex == 0) {
                        deps.gateway.creatorAuthors(targetPage, keyword)
                    } else {
                        deps.gateway.creatorWorks(targetPage, keyword, lang, source)
                    }
                }
                if (token != loadToken) return@launch
                loading = false
                refreshing = false
                when (outcome) {
                    is SessionOutcome.Ok -> when (val value = outcome.value) {
                        is CreatorPage -> {
                            val slice = couiLibrarySlice(value.items, value.total, targetPage)
                            creatorFeed = creatorRequest?.append(slice) { it.id }
                                ?: PagedFeed<CreatorMeta>().replace(slice) { it.id }
                        }

                        is CreatorWorkPage -> {
                            filters = value.filters
                            val slice = couiLibrarySlice(value.items, value.total, targetPage)
                            workFeed = workRequest?.append(slice) { it.id }
                                ?: PagedFeed<CreatorWorkMeta>().replace(slice) { it.id }
                        }

                        else -> Unit
                    }

                    SessionOutcome.Expired -> applyFailure(outcome, hasContent)

                    is SessionOutcome.Failed -> {
                        applyFailure(outcome, hasContent)
                        // 附加页失败只影响页脚，重试入口交给页脚的「重新加载」
                        creatorRequest?.let { creatorFeed = it.failLoadMore() }
                        workRequest?.let { workFeed = it.failLoadMore() }
                    }
                }
            }
        }

        LaunchedEffect(tab, appliedQuery, lang, source) {
            load(1, append = false)
        }
        PaginationModeReloadEffect(paginationMode) { target -> load(target, append = false) }

        val activeFeed: PagedFeed<*> = if (tab == 0) creatorFeed else workFeed
        val itemCount = activeFeed.items.size
        val total = activeFeed.total ?: 0
        val totalPages = libraryPageCount(total)
        val scheme = CouiKitTheme.colorScheme

        Box(modifier = Modifier.fillMaxSize()) {
            CouiScreenScaffold(
                title = "书库",
                onBack = onBack,
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
                        onClick = {
                            refreshing = true
                            load(1, append = false)
                        },
                        rotationDegrees = if (refreshing) 180f else 0f,
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
                    when {
                        loading -> item(key = "coui-library-loading", span = { GridItemSpan(maxLineSpan) }) {
                            CouiLoadingGroup(modifier = Modifier.fillMaxWidth().padding(top = 24.dp))
                        }

                        failure != null -> item(key = "coui-library-failed", span = { GridItemSpan(maxLineSpan) }) {
                            val planned = failure
                            CouiErrorGroup(
                                message = if (planned?.phase == ScreenLoadPhase.Expired) {
                                    SessionOutcome.Expired.message
                                } else {
                                    planned?.message.orEmpty()
                                },
                                onRetry = {
                                    if (planned?.phase == ScreenLoadPhase.Expired) {
                                        onOpenLogin()
                                    } else {
                                        load(activeFeed.page.coerceAtLeast(1), append = false)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                            )
                        }

                        else -> {
                            item(key = "coui-library-tabs", span = { GridItemSpan(maxLineSpan) }) {
                                CouiSegmentedCapsules(
                                    items = COUI_LIBRARY_TABS,
                                    selectedIndex = tab,
                                    onSelect = { index -> if (index != tab) tab = index },
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                            item(key = "coui-library-search", span = { GridItemSpan(maxLineSpan) }) {
                                CouiLibrarySearchField(
                                    value = query,
                                    onValueChange = { query = it },
                                    onSubmit = { appliedQuery = query.trim() },
                                    placeholder = if (tab == 0) "搜索创作者" else "搜索作品",
                                )
                            }
                            if (tab == 1 && !filters.isEmpty) {
                                item(key = "coui-library-filters", span = { GridItemSpan(maxLineSpan) }) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
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
                            if (itemCount == 0) {
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
                                    if (total > 0) {
                                        Text(
                                            text = "共 " + total + " 项",
                                            kind = CouiTextKind.Description,
                                            color = scheme.onSurfaceVariant,
                                            maxLines = 1,
                                            textAlign = TextAlign.Start,
                                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                        )
                                    }
                                }
                                if (tab == 0) {
                                    items(items = creatorFeed.items, key = { "coui-creator-" + it.id }) { creator ->
                                        Box(
                                            modifier = Modifier.fillMaxWidth(),
                                            contentAlignment = Alignment.TopCenter,
                                        ) {
                                            CouiWorkGridCard(
                                                title = creator.name.ifBlank { creator.id },
                                                coverUrl = creator.avatarUrl ?: creator.backgroundUrl,
                                                onClick = { onOpenCreator(creator.id) },
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
                                                onClick = { onOpenWork(work.id) },
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
                                            page = activeFeed.page,
                                            pageCount = totalPages,
                                            hasMore = activeFeed.hasMore,
                                            onJumpPage = { target -> load(target, append = false) },
                                        )
                                    } else {
                                        CouiAutoLoadMoreEffect(
                                            feed = activeFeed,
                                            onLoadMore = { load(activeFeed.page + 1, append = true) },
                                        )
                                        CouiLoadMoreFooter(
                                            feed = activeFeed,
                                            errorMessage = "没拿到下一页，可点击重试",
                                            onRetry = { load(activeFeed.page + 1, append = true) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            val openPicker = picker
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
        kind = CouiTextKind.Description,
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

/** 筛选弹窗里的一行：选中项用强调色。 */
@Composable
private fun CouiLibraryPickerRow(
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
            Text(
                text = "搜索",
                kind = CouiTextKind.SearchCancel,
                color = scheme.accent,
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
        hasMore = page < pageCount,
        total = total,
        page = page,
        pageCount = pageCount,
    )
}
