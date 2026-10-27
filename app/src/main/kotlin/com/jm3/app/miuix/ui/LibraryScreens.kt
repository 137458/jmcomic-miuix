package com.jm3.app.miuix.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.jm3.app.miuix.Jm3Graph
import com.jm3.core.model.CreatorAuthorWorks
import com.jm3.core.model.CreatorFilters
import com.jm3.core.model.CreatorMeta
import com.jm3.core.model.CreatorPage
import com.jm3.core.model.CreatorRelatedWork
import com.jm3.core.model.CreatorWork
import com.jm3.core.model.CreatorWorkMeta
import com.jm3.core.model.CreatorWorkPage
import com.jm3.core.settings.PaginationMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.BreadcrumbBar
import top.yukonga.miuix.kmp.basic.BreadcrumbItem
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.FloatingToolbar
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 原版列表每页 30 条；接口只返回总数，总页数由它推算。 */
private const val LIBRARY_PAGE_SIZE = 30

private val LIBRARY_TABS = listOf("创作者", "作品")

/** 书库首页：创作者/作品双 Tab + 关键词搜索 + 语言/来源筛选 + 两种分页模式。 */
@Composable
fun LibraryScreen(
    onBack: () -> Unit,
    onOpenCreator: (String) -> Unit,
    onOpenWork: (String) -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var appliedQuery by rememberSaveable { mutableStateOf("") }
    var lang by rememberSaveable { mutableStateOf("") }
    var source by rememberSaveable { mutableStateOf("") }
    var creators by remember { mutableStateOf<List<CreatorMeta>>(emptyList()) }
    var works by remember { mutableStateOf<List<CreatorWorkMeta>>(emptyList()) }
    var creatorTotal by remember { mutableIntStateOf(0) }
    var workTotal by remember { mutableIntStateOf(0) }
    var filters by remember { mutableStateOf(CreatorFilters()) }
    var page by remember { mutableIntStateOf(1) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // 切换 Tab/筛选/搜索时旧响应不能覆盖新结果
    var loadToken by remember { mutableIntStateOf(0) }
    val paginationMode = Jm3Graph.paginationMode
    // 阻尼阈值略低：下拉更轻快即触发刷新，回弹仍由库的弹簧曲线负责
    val refreshState = rememberPullToRefreshState(refreshThreshold = 0.18f)
    val scope = rememberCoroutineScope()

    fun load(targetPage: Int, append: Boolean) {
        loadToken++
        val token = loadToken
        val tabIndex = tab
        val keyword = appliedQuery
        if (append) {
            loadingMore = true
        } else if (!refreshing) {
            loading = true
        }
        error = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    if (tabIndex == 0) {
                        Jm3Graph.gateway.creatorAuthors(targetPage, keyword)
                    } else {
                        Jm3Graph.gateway.creatorWorks(targetPage, keyword, lang, source)
                    }
                }
            }
            if (token != loadToken) return@launch
            loading = false
            loadingMore = false
            refreshing = false
            result
                .onSuccess { value ->
                    when (value) {
                        is CreatorPage -> {
                            creators = if (append) creators + value.items else value.items
                            creatorTotal = value.total
                            page = targetPage
                        }
                        is CreatorWorkPage -> {
                            works = if (append) works + value.items else value.items
                            workTotal = value.total
                            filters = value.filters
                            page = targetPage
                        }
                        else -> Unit
                    }
                }
                .onFailure { error = it.message ?: "加载失败" }
        }
    }

    LaunchedEffect(tab, appliedQuery, lang, source) {
        load(1, append = false)
    }

    val itemCount = if (tab == 0) creators.size else works.size
    val total = if (tab == 0) creatorTotal else workTotal
    val totalPages = ((total + LIBRARY_PAGE_SIZE - 1) / LIBRARY_PAGE_SIZE).coerceAtLeast(1)

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = "书库",
                    subtitle = if (total > 0) "共 $total 项" else "",
                    navigationIcon = { BackButton(onBack = onBack) },
                )
                // 胶囊搜索框：展开态输入，提交即按当前 Tab 检索
                SearchBar(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    expanded = searchExpanded,
                    onExpandedChange = { searchExpanded = it },
                    inputField = {
                        InputField(
                            query = query,
                            onQueryChange = { query = it },
                            onSearch = {
                                appliedQuery = query.trim()
                                searchExpanded = false
                            },
                            expanded = searchExpanded,
                            onExpandedChange = { searchExpanded = it },
                            label = if (tab == 0) "搜索创作者" else "搜索作品",
                        )
                    },
                ) {}
                TabRow(
                    tabs = LIBRARY_TABS,
                    selectedTabIndex = tab,
                    onTabSelected = { tab = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
        },
        snackbarHost = { top.yukonga.miuix.kmp.basic.SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (tab == 1 && !filters.isEmpty) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (filters.languages.isNotEmpty()) {
                        FilterSpinner(
                            title = "语言",
                            options = filters.languages,
                            selected = lang,
                            onSelect = { lang = it },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (filters.sources.isNotEmpty()) {
                        FilterSpinner(
                            title = "来源",
                            options = filters.sources,
                            selected = source,
                            onSelect = { source = it },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            when {
                loading -> LoadingScreen(modifier = Modifier.fillMaxWidth().padding(24.dp))
                error != null -> ErrorScreen(
                    message = "加载失败：$error",
                    onRetry = { load(page.coerceAtLeast(1), append = false) },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
                itemCount == 0 -> EmptyState(
                    title = if (appliedQuery.isBlank()) {
                        if (tab == 0) "暂无创作者" else "暂无作品"
                    } else {
                        "没有找到相关内容"
                    },
                    hint = if (appliedQuery.isBlank()) "换个筛选条件再试试" else "换个关键词再试试",
                )
                else -> PullToRefresh(
                    isRefreshing = refreshing,
                    onRefresh = {
                        refreshing = true
                        load(1, append = false)
                    },
                    modifier = Modifier.fillMaxSize(),
                    pullToRefreshState = refreshState,
                ) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        if (tab == 0) {
                            items(creators, key = { it.id }) { creator ->
                                CreatorRow(creator = creator, onClick = { onOpenCreator(creator.id) })
                            }
                        } else {
                            items(
                                items = works.chunked(2),
                                key = { row -> row.joinToString(",") { it.id } },
                            ) { row ->
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    row.forEach { work ->
                                        WorkCard(
                                            title = work.title,
                                            coverUrl = work.coverUrl,
                                            platform = work.platform,
                                            authorName = work.authorName,
                                            workDate = work.workDate,
                                            modifier = Modifier.weight(1f),
                                            onClick = { onOpenWork(work.id) },
                                            onOpenAuthor = work.authorId?.let { id ->
                                                { onOpenCreator(id) }
                                            },
                                        )
                                    }
                                    if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                        item(key = "footer") {
                            LibraryFooter(
                                itemCount = itemCount,
                                total = total,
                                mode = paginationMode,
                                page = page,
                                totalPages = totalPages,
                                loadingMore = loadingMore,
                                onLoadMore = { load(page + 1, append = true) },
                                onPageChange = { target -> load(target, append = false) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 作者主页：背景/头像/赞助入口 + 语言来源筛选 + 作品网格。 */
@Composable
fun CreatorWorksScreen(
    creatorId: String,
    onBack: () -> Unit,
    onOpenCreator: (String) -> Unit,
    onOpenWork: (String) -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    var data by remember { mutableStateOf<CreatorAuthorWorks?>(null) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var lang by rememberSaveable { mutableStateOf("") }
    var source by rememberSaveable { mutableStateOf("") }
    var loadToken by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val refreshState = rememberPullToRefreshState(refreshThreshold = 0.18f)

    fun load(showLoading: Boolean) {
        loadToken++
        val token = loadToken
        if (showLoading) loading = true
        error = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { Jm3Graph.gateway.creatorAuthorWorks(creatorId, lang, source, 1) }
            }
            if (token != loadToken) return@launch
            loading = false
            refreshing = false
            result
                .onSuccess { data = it }
                .onFailure { error = it.message ?: "加载失败" }
        }
    }

    LaunchedEffect(creatorId, lang, source) {
        load(showLoading = data == null)
    }

    /** 赞助平台：绝对地址直接打开，缺失时提示；失败不崩溃。 */
    fun openSponsor(url: String?) {
        if (url.isNullOrBlank()) {
            scope.launch { snackbarHostState.showSnackbar("该平台暂无链接") }
            return
        }
        openExternal(context, url) {
            scope.launch { snackbarHostState.showSnackbar("无法打开链接") }
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = data?.authorName?.takeIf { it.isNotBlank() } ?: "创作者",
                    subtitle = data?.works?.size?.takeIf { it > 0 }?.let { "$it 部作品" } ?: "",
                    navigationIcon = { BackButton(onBack = onBack) },
                )
                // 多层级目录：书库 → 创作者，点击「书库」回到上一层
                BreadcrumbBar(
                    items = listOf(
                        BreadcrumbItem(path = "library", text = "书库"),
                        BreadcrumbItem(
                            path = creatorId,
                            text = data?.authorName?.takeIf { it.isNotBlank() } ?: "创作者",
                        ),
                    ),
                    onItemClick = { index -> if (index == 0) onBack() },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                )
            }
        },
        snackbarHost = { top.yukonga.miuix.kmp.basic.SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when {
            loading -> LoadingScreen(modifier = Modifier.padding(innerPadding))
            error != null && data == null -> ErrorScreen(
                message = "加载失败：$error",
                onRetry = { load(showLoading = true) },
                modifier = Modifier.padding(innerPadding),
            )
            else -> PullToRefresh(
                isRefreshing = refreshing,
                onRefresh = {
                    refreshing = true
                    load(showLoading = false)
                },
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                pullToRefreshState = refreshState,
            ) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item(key = "hero") {
                        val current = data
                        if (current != null) {
                            CreatorHero(author = current)
                        }
                    }
                    items(data?.sponsors.orEmpty(), key = { "sponsor-${it.name}-${it.url}" }) { sponsor ->
                        Button(
                            onClick = { openSponsor(sponsor.url) },
                            colors = ButtonDefaults.buttonColorsPrimary(),
                            modifier = Modifier.fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                        ) {
                            Text(text = "支持作者 → ${sponsor.name}")
                        }
                    }
                    val filterSource = data?.filters
                    if (filterSource != null && !filterSource.isEmpty) {
                        item(key = "filters") {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if (filterSource.languages.isNotEmpty()) {
                                    FilterSpinner(
                                        title = "语言",
                                        options = filterSource.languages,
                                        selected = lang,
                                        onSelect = { lang = it },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                if (filterSource.sources.isNotEmpty()) {
                                    FilterSpinner(
                                        title = "来源",
                                        options = filterSource.sources,
                                        selected = source,
                                        onSelect = { source = it },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                    val works = data?.works.orEmpty()
                    if (works.isEmpty()) {
                        item(key = "empty") {
                            EmptyState(title = "暂无作品", hint = "换个语言或来源再试试")
                        }
                    } else {
                        items(
                            items = works.chunked(2),
                            key = { row -> row.joinToString(",") { it.id } },
                        ) { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                row.forEach { work ->
                                    WorkCard(
                                        title = work.title,
                                        coverUrl = work.coverUrl,
                                        platform = work.platform,
                                        authorName = null,
                                        workDate = work.workDate,
                                        modifier = Modifier.weight(1f),
                                        onClick = { onOpenWork(work.id) },
                                        onOpenAuthor = null,
                                    )
                                }
                                if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    item(key = "bottom-space") { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        }
    }
}

/** 作品详情：描述 + 整页图片 + 底部「相关作品」悬浮入口。 */
@Composable
fun CreatorWorkDetailScreen(
    workId: String,
    onBack: () -> Unit,
    onOpenWork: (String) -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    var work by remember { mutableStateOf<CreatorWork?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var relatedOpen by remember { mutableStateOf(false) }
    var loadToken by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    fun load() {
        loadToken++
        val token = loadToken
        loading = true
        error = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { Jm3Graph.gateway.creatorWork(workId) }
            }
            if (token != loadToken) return@launch
            loading = false
            result
                .onSuccess { work = it }
                .onFailure { error = it.message ?: "加载失败" }
        }
    }

    LaunchedEffect(workId) { load() }

    val related = work?.related.orEmpty()

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = work?.title?.takeIf { it.isNotBlank() } ?: "作品",
                    subtitle = work?.authorName.orEmpty(),
                    navigationIcon = { BackButton(onBack = onBack) },
                )
                // 多层级目录：书库 → 作品，点击「书库」回到上一层
                BreadcrumbBar(
                    items = listOf(
                        BreadcrumbItem(path = "library", text = "书库"),
                        BreadcrumbItem(
                            path = workId,
                            text = work?.title?.takeIf { it.isNotBlank() } ?: "作品",
                        ),
                    ),
                    onItemClick = { index -> if (index == 0) onBack() },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                )
            }
        },
        floatingToolbar = {
            if (related.isNotEmpty()) {
                FloatingToolbar {
                    TextButton(
                        text = "相关作品 ${related.size}",
                        onClick = { relatedOpen = true },
                        minWidth = 0.dp,
                        minHeight = 44.dp,
                    )
                }
            }
        },
        snackbarHost = { top.yukonga.miuix.kmp.basic.SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when {
            loading -> LoadingScreen(modifier = Modifier.padding(innerPadding))
            error != null -> ErrorScreen(
                message = "加载失败：$error",
                onRetry = ::load,
                modifier = Modifier.padding(innerPadding),
            )
            work == null -> CenteredEmptyState(
                title = "作品不存在",
                hint = "可能已下架",
                modifier = Modifier.padding(innerPadding),
            )
            else -> {
                val body = work!!
                LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    if (body.contentText.isNotBlank()) {
                        item(key = "content") {
                            Text(
                                text = body.contentText,
                                modifier = Modifier.fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                fontSize = MiuixTheme.textStyles.body2.fontSize,
                            )
                        }
                    }
                    items(body.images.withIndex().toList(), key = { it.index }) { entry ->
                        LibraryCover(
                            url = entry.value,
                            modifier = Modifier.fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                                .squircleClip(10.dp)
                                .aspectRatio(0.72f),
                            contentScale = ContentScale.Fit,
                        )
                    }
                    if (body.images.isEmpty() && body.contentText.isBlank()) {
                        item(key = "empty") {
                            EmptyState(title = "暂无内容", hint = "该作品还在补充中")
                        }
                    }
                    item(key = "bottom-space") { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }

        OverlayBottomSheet(
            show = relatedOpen,
            title = "相关作品",
            onDismissRequest = { relatedOpen = false },
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(related, key = { it.id }) { item ->
                        RelatedWorkCard(
                            work = item,
                            onClick = {
                                relatedOpen = false
                                onOpenWork(item.id)
                            },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

/** 创作者行：背景图 + 暗色蒙版 + 头像 + 名称/更新时间。 */
@Composable
private fun CreatorRow(creator: CreatorMeta, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .pressScale(),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(104.dp).clickable(onClick = onClick),
        ) {
            LibraryCover(
                url = creator.backgroundUrl,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LibraryCover(
                    url = creator.avatarUrl,
                    modifier = Modifier.size(64.dp).squircleClip(14.dp),
                    contentScale = ContentScale.Crop,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = creator.name.ifBlank { creator.id },
                        fontSize = MiuixTheme.textStyles.title4.fontSize,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    creator.updateText?.let { update ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "更新于 $update",
                            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                            color = Color.White.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** 作品卡：封面 + 平台角标 + 标题 + 作者（可跳作者主页）/发布日期。 */
@Composable
private fun WorkCard(
    title: String,
    coverUrl: String?,
    platform: String?,
    authorName: String?,
    workDate: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onOpenAuthor: (() -> Unit)?,
) {
    Column(modifier = modifier.pressScale().clickable(onClick = onClick)) {
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(0.72f).squircleClip(10.dp),
        ) {
            LibraryCover(
                url = coverUrl,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            platform?.takeIf { it.isNotBlank() }?.let { label ->
                PlatformBadge(
                    text = label,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = title.ifBlank { "无题" },
            fontSize = MiuixTheme.textStyles.body2.fontSize,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (!authorName.isNullOrBlank() && onOpenAuthor != null) {
            Text(
                text = authorName,
                fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                color = MiuixTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp).clickable(onClick = onOpenAuthor),
            )
        }
        workDate?.takeIf { it.isNotBlank() }?.let { date ->
            Text(
                text = date,
                fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** 作者主页头部：背景（底部渐进模糊）+ 头像 + 名称。 */
@Composable
private fun CreatorHero(author: CreatorAuthorWorks) {
    // 背景层作为 backdrop 供底部渐进模糊采样；API 33 以下退回纯色蒙版
    val backdrop = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberLayerBackdrop()
    } else {
        null
    }
    Box(modifier = Modifier.fillMaxWidth().height(150.dp)) {
        LibraryCover(
            url = author.backgroundUrl,
            modifier = Modifier.fillMaxSize().then(
                if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier,
            ),
            contentScale = ContentScale.Crop,
        )
        if (backdrop != null) {
            // 从下往上由实到虚：底部文字压在模糊带上，比整块暗色蒙版更通透
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .progressiveTextureBlur(
                        backdrop = backdrop,
                        shape = RectangleShape,
                        blurRadius = 26f,
                        gradient = ProgressiveBlur.Bottom,
                    )
                    .background(Color.Black.copy(alpha = 0.28f)),
            )
        } else {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))
        }
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LibraryCover(
                url = author.avatarUrl,
                modifier = Modifier.size(76.dp).squircleClip(18.dp),
                contentScale = ContentScale.Crop,
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = author.authorName.ifBlank { "未知创作者" },
                    fontSize = MiuixTheme.textStyles.title3.fontSize,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (author.works.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${author.works.size} 部作品",
                        fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                }
            }
        }
    }
}

/** 相关作品卡：窄版封面 + 标题 + 平台角标。 */
@Composable
private fun RelatedWorkCard(work: CreatorRelatedWork, onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(120.dp).pressScale().clickable(onClick = onClick),
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(0.72f).squircleClip(8.dp)) {
            LibraryCover(
                url = work.coverUrl,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            work.platform?.takeIf { it.isNotBlank() }?.let { label ->
                PlatformBadge(
                    text = label,
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = work.title.ifBlank { "无题" },
            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 语言/来源筛选：接口下发的选项 + 首项「全部」。 */
@Composable
private fun FilterSpinner(
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val all = remember(options) { listOf("全部") + options.distinct() }
    val selectedIndex = all.indexOf(selected).coerceAtLeast(0)
    Card(modifier = modifier) {
        OverlaySpinnerPreference(
            items = all.mapIndexed { index, text ->
                DropdownItem(text = text, selected = index == selectedIndex)
            },
            selectedIndex = selectedIndex,
            title = title,
            summary = all.getOrNull(selectedIndex) ?: "全部",
            showValue = false,
            onSelectedIndexChange = { index ->
                onSelect(if (index == 0) "" else all.getOrNull(index).orEmpty())
            },
        )
    }
}

/** 列表底部：加载更多/没有更多/点击翻页。 */
@Composable
private fun LibraryFooter(
    itemCount: Int,
    total: Int,
    mode: PaginationMode,
    page: Int,
    totalPages: Int,
    loadingMore: Boolean,
    onLoadMore: () -> Unit,
    onPageChange: (Int) -> Unit,
) {
    if (itemCount == 0) return
    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        when {
            loadingMore -> CircularProgressIndicator(size = 20.dp)
            mode == PaginationMode.CLICK -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    text = "上一页",
                    onClick = { onPageChange(page - 1) },
                    enabled = page > 1,
                    minWidth = 0.dp,
                    minHeight = 36.dp,
                )
                Text(
                    text = "$page / $totalPages",
                    fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                TextButton(
                    text = "下一页",
                    onClick = { onPageChange(page + 1) },
                    enabled = page < totalPages,
                    minWidth = 0.dp,
                    minHeight = 36.dp,
                )
            }
            itemCount < total -> TextButton(text = "加载更多", onClick = onLoadMore)
            else -> Text(
                text = "没有更多了",
                fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

/** 平台角标：半透明主色胶囊 + 白字，与 HyperOS 标签观感一致。 */
@Composable
private fun PlatformBadge(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = MiuixTheme.textStyles.footnote2.fontSize,
        color = Color.White,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.9f), RoundedCornerShape(6.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp),
    )
}

/** 统一封面占位：缺失时保留同尺寸色块，避免网格跳动。 */
@Composable
private fun LibraryCover(
    url: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    Box(
        modifier = modifier.background(MiuixTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** 外链打开：无匹配 Activity 时回调提示，不抛异常。 */
private fun openExternal(context: Context, url: String, onFailure: () -> Unit) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    runCatching { context.startActivity(intent) }.onFailure { onFailure() }
}
