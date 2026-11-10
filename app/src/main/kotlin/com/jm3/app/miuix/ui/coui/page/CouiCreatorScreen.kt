// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.page

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.layout.Footer
import com.itos.coui.layout.Group
import com.itos.coui.layout.Item
import com.itos.coui.scroll.rememberCouiOverscrollEffect
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme as CouiKitTheme
import com.itos.coui.topappbar.CouiTopAppBarDefaults
import com.itos.coui.topappbar.LargeTopAppBar
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.ui.GlassSnackbarHost
import com.jm3.app.miuix.ui.LocalWideLayout
import com.jm3.app.miuix.ui.ScreenLoadPlan
import com.jm3.app.miuix.ui.ScreenLoadPhase
import com.jm3.app.miuix.ui.coui.component.CouiAppScaffold
import com.jm3.app.miuix.ui.coui.component.CouiEmptyGroup
import com.jm3.app.miuix.ui.coui.component.CouiErrorGroup
import com.jm3.app.miuix.ui.coui.component.CouiFab
import com.jm3.app.miuix.ui.coui.component.CouiGroupTitle
import com.jm3.app.miuix.ui.coui.component.CouiLoadMoreFooter
import com.jm3.app.miuix.ui.coui.component.CouiLoadingGroup
import com.jm3.app.miuix.ui.coui.component.CouiMinTouchTarget
import com.jm3.app.miuix.ui.coui.component.CouiTopBarBackButton
import com.jm3.app.miuix.ui.coui.component.CouiTopBarTitle
import com.jm3.app.miuix.ui.coui.component.CouiWorkCardTokens
import com.jm3.app.miuix.ui.coui.component.CouiWorkGridCard
import com.jm3.app.miuix.ui.coui.component.couiPressable
import com.jm3.app.miuix.ui.coui.component.couiTopAppBarColors
import com.jm3.app.miuix.ui.coui.icon.CouiIcons
import com.jm3.app.miuix.ui.coui.theme.CouiTheme
import com.jm3.app.miuix.ui.planLoad
import com.jm3.app.miuix.ui.readableWidth
import com.jm3.app.miuix.ui.skin.LocalSkin
import com.jm3.core.model.CreatorAuthorWorks
import com.jm3.core.model.CreatorFilters
import com.jm3.core.model.CreatorRelatedWork
import com.jm3.core.model.CreatorSponsor
import com.jm3.core.session.SessionOutcome
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SnackbarHostState

private const val COUI_CREATOR_LOAD_FAILED = "加载失败"
private const val COUI_CREATOR_LOAD_MORE_FAILED = "没拿到下一页，可点击重试"
private const val COUI_CREATOR_EMPTY = "暂无作品"
private const val COUI_CREATOR_EMPTY_HINT = "换个语言或来源再试试"
private const val COUI_CREATOR_NO_SPONSOR_URL = "该平台暂无链接"
private const val COUI_CREATOR_OPEN_FAILED = "无法打开链接"
private const val COUI_CREATOR_ALL_FILTER = "全部"
private const val COUI_CREATOR_BANNER_HEIGHT_DP = 150

private data class CouiCreatorHeader(
    val authorName: String,
    val avatarUrl: String?,
    val backgroundUrl: String?,
    val sponsors: List<CreatorSponsor>,
    val filters: CreatorFilters,
)

private data class CouiCreatorContent(
    val header: CouiCreatorHeader,
    val works: List<CreatorRelatedWork>,
)

private sealed interface CouiCreatorState {
    data object Loading : CouiCreatorState
    data class Failed(val plan: ScreenLoadPlan) : CouiCreatorState
    data class Loaded(
        val content: CouiCreatorContent,
        val page: Int,
        val hasMore: Boolean,
        val loadingMore: Boolean = false,
        val loadMoreFailed: Boolean = false,
    ) : CouiCreatorState
}

/**
 * COUI 作者主页。
 *
 * 头图 + 头像 + 赞助平台构成头部卡片，作品按 [LocalWideLayout] 的列数铺成栅格；
 * 语言/来源筛选走服务端重查，选中即回到第 1 页。接口不返回 total/hasMore，
 * 所以续页是否到底只能按「本次拿回的作品是否还有新条目」推断（见 loadMore）。
 */
@Composable
internal fun CouiCreatorScreen(
    deps: ScreenDeps,
    creatorId: String,
    onBack: () -> Unit,
    onOpenWork: (String) -> Unit,
    snackbarHostState: SnackbarHostState,
    onOpenLogin: () -> Unit,
) {
    CouiTheme {
        CouiCreatorBody(
            deps = deps,
            creatorId = creatorId,
            onBack = onBack,
            onOpenWork = onOpenWork,
            snackbarHostState = snackbarHostState,
            onOpenLogin = onOpenLogin,
        )
    }
}

@Composable
private fun CouiCreatorBody(
    deps: ScreenDeps,
    creatorId: String,
    onBack: () -> Unit,
    onOpenWork: (String) -> Unit,
    snackbarHostState: SnackbarHostState,
    onOpenLogin: () -> Unit,
) {
    val gateway = deps.gateway
    val authCall = deps.authCall
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val wideLayout = LocalWideLayout.current
    val columns = wideLayout.gridColumns.coerceAtLeast(1)
    val lazyState = rememberLazyListState()

    var state by remember(creatorId) { mutableStateOf<CouiCreatorState>(CouiCreatorState.Loading) }
    var requestToken by remember { mutableIntStateOf(0) }
    var language by rememberSaveable(creatorId) { mutableStateOf("") }
    var source by rememberSaveable(creatorId) { mutableStateOf("") }

    fun load() {
        requestToken++
        val token = requestToken
        state = CouiCreatorState.Loading
        coroutineScope.launch {
            val outcome = authCall.call { gateway.creatorAuthorWorks(creatorId, language, source, 1) }
            if (token != requestToken) return@launch
            state = when (outcome) {
                is SessionOutcome.Ok -> {
                    val value: CreatorAuthorWorks = outcome.value
                    val content = CouiCreatorContent(
                        header = CouiCreatorHeader(
                            authorName = value.authorName.ifBlank { "创作者" },
                            avatarUrl = value.avatarUrl,
                            backgroundUrl = value.backgroundUrl,
                            sponsors = value.sponsors,
                            filters = value.filters,
                        ),
                        works = value.works,
                    )
                    CouiCreatorState.Loaded(
                        content = content,
                        page = 1,
                        hasMore = value.works.isNotEmpty(),
                    )
                }

                else -> CouiCreatorState.Failed(
                    planLoad(outcome, hasContent = false, failureMessage = COUI_CREATOR_LOAD_FAILED),
                )
            }
        }
    }

    fun loadMore() {
        val current = state as? CouiCreatorState.Loaded ?: return
        if (!current.hasMore || current.loadingMore) return
        val token = requestToken
        state = current.copy(loadingMore = true, loadMoreFailed = false)
        coroutineScope.launch {
            val outcome = authCall.call { gateway.creatorAuthorWorks(creatorId, language, source, current.page + 1) }
            if (token != requestToken) return@launch
            val latest = state as? CouiCreatorState.Loaded ?: return@launch
            state = when (outcome) {
                is SessionOutcome.Ok -> {
                    val known = latest.content.works.mapTo(HashSet()) { it.id }
                    val fresh = outcome.value.works.filter { it.id !in known }
                    latest.copy(
                        content = latest.content.copy(works = latest.content.works + fresh),
                        page = current.page + 1,
                        hasMore = fresh.isNotEmpty(),
                        loadingMore = false,
                        loadMoreFailed = false,
                    )
                }

                else -> latest.copy(loadingMore = false, loadMoreFailed = true)
            }
        }
    }

    LaunchedEffect(creatorId, language, source) { load() }

    CouiPrefetchEffect(
        listState = lazyState,
        restartKey = Triple(creatorId, language, source),
    ) { lastIndex, totalItemsCount, prefetchRows ->
        val loaded = state as? CouiCreatorState.Loaded ?: return@CouiPrefetchEffect
        if (!loaded.hasMore || loaded.loadingMore || loaded.loadMoreFailed) return@CouiPrefetchEffect
        if (!shouldPrefetch(lastIndex, totalItemsCount, prefetchRows)) return@CouiPrefetchEffect
        loadMore()
    }

    val header = (state as? CouiCreatorState.Loaded)?.content?.header
    val scrollBehavior = CouiTopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        canScroll = { lazyState.canScrollForward || lazyState.canScrollBackward },
    )

    Box(modifier = Modifier.fillMaxSize()) {
        CouiAppScaffold(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                LargeTopAppBar(
                    title = { CouiTopBarTitle(text = header?.authorName ?: "创作者") },
                    navigationIcon = { CouiTopBarBackButton(onClick = onBack) },
                    scrollBehavior = scrollBehavior,
                    colors = couiTopAppBarColors(),
                )
            },
            floatingActionButton = {
                CouiFab(
                    icon = CouiIcons.Refresh,
                    contentDescription = "刷新",
                    onClick = { load() },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                )
            },
        ) { innerPadding ->
            val corrected = scrollBehavior.state.contentPadding(innerPadding, density)
            val contentPadding = remember(corrected, layoutDirection) {
                PaddingValues(
                    start = corrected.calculateLeftPadding(layoutDirection),
                    top = corrected.calculateTopPadding(),
                    end = corrected.calculateRightPadding(layoutDirection),
                    bottom = corrected.calculateBottomPadding() + 24.dp,
                )
            }
            LazyColumn(
                state = lazyState,
                modifier = Modifier
                    .fillMaxSize()
                    .readableWidth()
                    .consumeWindowInsets(innerPadding),
                contentPadding = contentPadding,
                overscrollEffect = rememberCouiOverscrollEffect(),
            ) {
                when (val current = state) {
                    CouiCreatorState.Loading -> item(key = "coui-creator-loading") { CouiLoadingGroup() }

                    is CouiCreatorState.Failed -> item(key = "coui-creator-error") {
                        CouiCreatorFailure(plan = current.plan, onRetry = { load() }, onOpenLogin = onOpenLogin)
                    }

                    is CouiCreatorState.Loaded -> {
                        val content = current.content
                        item(key = "coui-creator-header") {
                            CouiCreatorHeaderCard(
                                header = content.header,
                                worksCount = content.works.size,
                                onOpenSponsor = { sponsor ->
                                    val url = sponsor.url?.takeIf { it.isNotBlank() }
                                    if (url == null) {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(COUI_CREATOR_NO_SPONSOR_URL)
                                        }
                                    } else {
                                        openCouiExternal(context, url) {
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar(COUI_CREATOR_OPEN_FAILED)
                                            }
                                        }
                                    }
                                },
                            )
                        }
                        if (content.header.filters.languages.isNotEmpty() || content.header.filters.sources.isNotEmpty()) {
                            item(key = "coui-creator-filters") {
                                CouiCreatorFilterSection(
                                    filters = content.header.filters,
                                    language = language,
                                    source = source,
                                    onLanguage = { language = it },
                                    onSource = { source = it },
                                )
                            }
                        }
                        if (content.works.isEmpty()) {
                            item(key = "coui-creator-empty") {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    CouiEmptyGroup(text = COUI_CREATOR_EMPTY)
                                    Footer(text = COUI_CREATOR_EMPTY_HINT)
                                }
                            }
                        } else {
                            item(key = "coui-creator-works-title") {
                                CouiGroupTitle(
                                    title = "作品（${content.works.size}）",
                                )
                            }
                            val rows = content.works.chunked(columns)
                            itemsIndexed(
                                items = rows,
                                key = { _, row -> "coui-creator-row-${row.firstOrNull()?.id.orEmpty()}" },
                                contentType = { _, _ -> "coui-creator-row" },
                            ) { _, row ->
                                CouiCreatorWorksRow(
                                    works = row,
                                    columns = columns,
                                    onOpenWork = onOpenWork,
                                )
                            }
                            item(key = "coui-creator-footer") {
                                CouiLoadMoreFooter(
                                    loading = current.loadingMore,
                                    error = if (current.loadMoreFailed) COUI_CREATOR_LOAD_MORE_FAILED else null,
                                    endReached = !current.hasMore,
                                    onRetry = { loadMore() },
                                )
                            }
                        }
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

/** 屏级失败块：会话失效换成登录提示，其余给重试。 */
@Composable
private fun CouiCreatorFailure(plan: ScreenLoadPlan, onRetry: () -> Unit, onOpenLogin: () -> Unit) {
    val scheme = CouiKitTheme.colorScheme
    if (plan.phase == ScreenLoadPhase.Expired) {
        Group(title = "需要登录", footer = plan.message.orEmpty()) {
            Item(
                title = { Text(text = "登录后可查看创作者主页，点此去登录", kind = CouiTextKind.Body) },
                onClick = onOpenLogin,
                trailing = {
                    Icon(
                        imageVector = CouiIcons.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = scheme.onSurfaceVariant,
                    )
                },
            )
        }
    } else {
        CouiErrorGroup(message = plan.message.orEmpty(), onRetry = onRetry)
    }
}

/** 头部卡片：头图 + 圆形头像 + 名字作品数 + 赞助平台胶囊。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CouiCreatorHeaderCard(
    header: CouiCreatorHeader,
    worksCount: Int,
    onOpenSponsor: (CreatorSponsor) -> Unit,
) {
    val scheme = CouiKitTheme.colorScheme
    val corner = LocalSkin.current.shapes.cardCorner
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CouiPageDefaults.horizontalMargin)
            .padding(top = CouiPageDefaults.categoryTopSpacing, bottom = CouiPageDefaults.groupSpacing)
            .clip(CouiPageDefaults.shape)
            .background(scheme.card),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(COUI_CREATOR_BANNER_HEIGHT_DP.dp)
                .background(scheme.container),
        ) {
            if (header.backgroundUrl != null) {
                AsyncImage(
                    model = header.backgroundUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(scheme.container),
            ) {
                if (header.avatarUrl != null) {
                    AsyncImage(
                        model = header.avatarUrl,
                        contentDescription = header.authorName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = header.authorName,
                    kind = CouiTextKind.SectionTitle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Start,
                )
                Text(
                    text = "$worksCount 部作品",
                    kind = CouiTextKind.Description,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Start,
                )
            }
        }
        if (header.sponsors.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                header.sponsors.forEach { sponsor ->
                    val shape = CouiCapsuleShape
                    Box(
                        modifier = Modifier
                            .height(28.dp)
                            .clip(shape)
                            .background(scheme.accent.copy(alpha = 0.12f))
                            .couiPressable(
                                shape = shape,
                                minTouchTarget = CouiMinTouchTarget,
                                onClick = { onOpenSponsor(sponsor) },
                            )
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = sponsor.name,
                            kind = CouiTextKind.Description,
                            color = scheme.accent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** 语言/来源两排胶囊：值来自接口返回的可选集合，空串代表不过滤。 */
@Composable
private fun CouiCreatorFilterSection(
    filters: CreatorFilters,
    language: String,
    source: String,
    onLanguage: (String) -> Unit,
    onSource: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CouiCreatorFilterRow(
            title = "语言",
            options = filters.languages,
            selected = language,
            onSelect = onLanguage,
        )
        CouiCreatorFilterRow(
            title = "来源",
            options = filters.sources,
            selected = source,
            onSelect = onSource,
        )
    }
}

@Composable
private fun CouiCreatorFilterRow(
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    if (options.isEmpty()) return
    val scheme = CouiKitTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            kind = CouiTextKind.Description,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(start = CouiPageDefaults.horizontalMargin),
        )
        Spacer(modifier = Modifier.width(8.dp))
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(end = CouiPageDefaults.horizontalMargin),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items = listOf("") + options, key = { it.ifBlank { "coui-creator-all" } }) { option ->
                val shape = RoundedCornerShape(16.dp)
                Box(
                    modifier = Modifier
                        .clip(shape)
                        .background(if (option == selected) scheme.accent.copy(alpha = 0.15f) else scheme.card)
                        .couiPressable(
                            shape = shape,
                            minTouchTarget = CouiMinTouchTarget,
                            onClick = { onSelect(option) },
                        )
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = option.ifBlank { COUI_CREATOR_ALL_FILTER },
                        kind = CouiTextKind.Body,
                        color = if (option == selected) scheme.accent else scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** 栅格一行：固定卡宽，列数由 [LocalWideLayout] 决定，尾部用占位保证左对齐。 */
@Composable
private fun CouiCreatorWorksRow(
    works: List<CreatorRelatedWork>,
    columns: Int,
    onOpenWork: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CouiPageDefaults.horizontalMargin)
            .padding(bottom = CouiWorkCardTokens.GridSpacing),
        horizontalArrangement = Arrangement.spacedBy(CouiWorkCardTokens.GridSpacing),
    ) {
        works.forEach { work ->
            CouiWorkGridCard(
                title = work.title,
                coverUrl = work.coverUrl,
                onClick = { onOpenWork(work.id) },
                modifier = Modifier.width(CouiWorkCardTokens.GridWidth),
                coverContentDescription = work.title,
                subtitle = work.workDate?.takeIf { it.isNotBlank() },
                category = work.platform?.takeIf { it.isNotBlank() },
            )
        }
        repeat(columns - works.size) {
            Spacer(modifier = Modifier.width(CouiWorkCardTokens.GridWidth))
        }
    }
}

/** 赞助外链：起不来就回调失败，由调用方提示，不静默吞掉。 */
private fun openCouiExternal(context: Context, url: String, onFailure: () -> Unit) {
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }.onFailure { onFailure() }
}
