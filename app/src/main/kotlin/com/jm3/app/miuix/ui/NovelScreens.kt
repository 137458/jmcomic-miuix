package com.jm3.app.miuix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.jm3.app.miuix.Jm3Graph
import com.jm3.app.miuix.ui.glass.GlassPullToRefresh
import com.jm3.app.miuix.ui.glass.GlassScaffold
import com.jm3.app.miuix.ui.glass.GlassTopAppBar
import com.jm3.app.miuix.ui.glass.withSpacing
import com.jm3.core.model.CommentItem
import com.jm3.core.model.NovelChapter
import com.jm3.core.model.NovelChapterMeta
import com.jm3.core.model.NovelDetail
import com.jm3.core.model.NovelMeta
import com.jm3.core.model.NovelOrder
import com.jm3.core.model.SearchScope
import com.jm3.core.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.FloatingToolbar
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.FavoritesFill
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.FolderFill
import top.yukonga.miuix.kmp.icon.extended.Messages
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 小说列表页：排序 + 关键词搜索 + 点赞/收藏。 */
@Composable
fun NovelListScreen(
    onBack: () -> Unit,
    onOpenNovel: (String) -> Unit,
    onOpenLogin: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val user by Jm3Graph.account.user.collectAsState()
    var orderName by rememberSaveable { mutableStateOf(NovelOrder.DEFAULT.name) }
    val order = NovelOrder.entries.firstOrNull { it.name == orderName } ?: NovelOrder.DEFAULT
    var query by rememberSaveable { mutableStateOf("") }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var appliedQuery by rememberSaveable { mutableStateOf("") }
    var items by remember { mutableStateOf<List<NovelMeta>>(emptyList()) }
    var total by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    // 快速切换排序/搜索时旧响应不能覆盖新结果
    var loadToken by remember { mutableIntStateOf(0) }
    var busyId by remember { mutableStateOf<String?>(null) }
    val refreshState = rememberPullToRefreshState(refreshThreshold = 0.18f)
    val scope = rememberCoroutineScope()

    fun load(orderValue: String, keyword: String, showLoading: Boolean) {
        loadToken++
        val token = loadToken
        if (showLoading) loading = true
        error = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    if (keyword.isBlank()) {
                        Jm3Graph.gateway.novelList(orderValue, 1)
                    } else {
                        Jm3Graph.gateway.searchNovels(keyword)
                    }
                }
            }
            if (token != loadToken) return@launch
            loading = false
            refreshing = false
            result
                .onSuccess {
                    items = it.items
                    total = it.total
                }
                .onFailure { error = it.message ?: "加载失败" }
        }
    }

    LaunchedEffect(orderName, appliedQuery) {
        load(order.apiValue, appliedQuery, showLoading = items.isEmpty())
    }

    /** 点赞：平台不支持取消，已赞时只提示。 */
    fun like(item: NovelMeta) {
        if (item.liked) {
            scope.launch { snackbarHostState.showSnackbar("已经点过赞了") }
            return
        }
        if (busyId != null) return
        busyId = item.id
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { Jm3Graph.gateway.toggleNovelLike(item.id) }
            }
            busyId = null
            result
                .onSuccess {
                    items = items.map { if (it.id == item.id) it.copy(liked = true) else it }
                    snackbarHostState.showSnackbar("点赞成功")
                }
                .onFailure {
                    snackbarHostState.showSnackbar("点赞失败：" + (it.message ?: "请稍后重试"))
                }
        }
    }

    /** 收藏：未登录先引导登录，成功后本地回显收藏态。 */
    fun favorite(item: NovelMeta) {
        if (user == null) {
            onOpenLogin()
            return
        }
        if (busyId != null) return
        busyId = item.id
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { Jm3Graph.gateway.toggleNovelFavorite(item.id) }
            }
            busyId = null
            result
                .onSuccess {
                    items = items.map { if (it.id == item.id) it.copy(favorite = !it.favorite) else it }
                    snackbarHostState.showSnackbar(if (item.favorite) "已取消收藏" else "已加入收藏")
                }
                .onFailure {
                    snackbarHostState.showSnackbar("收藏失败：" + (it.message ?: "请稍后重试"))
                }
        }
    }

    GlassScaffold(
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                GlassTopAppBar(
                    title = if (appliedQuery.isBlank()) "小说" else "小说搜索",
                    subtitle = if (total > 0) "共 $total 本" else "",
                    navigationIcon = { BackButton(onBack = onBack) },
                    // 透明底色：玻璃底色与模糊由 GlassScaffold 统一绘制
                    color = Color.Transparent,
                )
                // 胶囊搜索框：展开态输入，提交即检索
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
                            label = "搜索小说",
                        )
                    },
                ) {}
                // 排序控件固定在列表之外：与顶栏同处玻璃层，列表从其下方穿过
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                    OverlaySpinnerPreference(
                        items = NovelOrder.entries.map { DropdownItem(text = it.label, selected = it == order) },
                        selectedIndex = NovelOrder.entries.indexOf(order),
                        title = "排序",
                        summary = order.label,
                        showValue = false,
                        onSelectedIndexChange = { index ->
                            val next = NovelOrder.entries.getOrNull(index) ?: return@OverlaySpinnerPreference
                            if (next == order) return@OverlaySpinnerPreference
                            orderName = next.name
                        },
                    )
                }
            }
        },
        snackbarHost = { GlassSnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when {
            loading -> LoadingScreen(modifier = Modifier.padding(innerPadding))
            error != null -> ErrorScreen(
                message = "加载失败：$error",
                onRetry = { load(order.apiValue, appliedQuery, showLoading = true) },
                modifier = Modifier.padding(innerPadding),
            )
            items.isEmpty() -> EmptyState(
                title = if (appliedQuery.isBlank()) "暂无小说" else "没有找到相关小说",
                hint = if (appliedQuery.isBlank()) "稍后再来看看" else "换个关键词再试试",
                modifier = Modifier.padding(innerPadding),
            )
            else -> GlassPullToRefresh(
                isRefreshing = refreshing,
                onRefresh = {
                    refreshing = true
                    load(order.apiValue, appliedQuery, showLoading = false)
                },
                modifier = Modifier.fillMaxSize(),
                pullToRefreshState = refreshState,
                // 刷新指示器下移到玻璃顶栏之下，避免被顶栏遮住
                contentPadding = innerPadding,
            ) {
                // 首项下移让出顶栏高度，滚动时内容从玻璃下方穿过
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = innerPadding,
                ) {
                    items(items, key = { it.id }) { item ->
                        NovelListRow(
                            item = item,
                            busy = busyId == item.id,
                            onClick = { onOpenNovel(item.id) },
                            onLike = { like(item) },
                            onFavorite = { favorite(item) },
                        )
                    }
                }
            }
        }
    }
}

/** 小说列表行：封面 + 标题/作者/更新 + 点赞与收藏两个可点标记。 */
@Composable
private fun NovelListRow(
    item: NovelMeta,
    busy: Boolean,
    onClick: () -> Unit,
    onLike: () -> Unit,
    onFavorite: () -> Unit,
) {
    WorkRowCard(
        coverUrl = item.coverUrl,
        title = item.name,
        onClick = onClick,
        coverContentDescription = item.name,
        subtitle = item.author.ifBlank { "佚名" },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        textEndPadding = 4.dp,
        trailing = {
            if (busy) {
                CircularProgressIndicator(size = 20.dp)
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = onLike) {
                        Icon(
                            imageVector = if (item.liked) MiuixIcons.FavoritesFill else MiuixIcons.Favorites,
                            contentDescription = "点赞",
                            tint = if (item.liked) {
                                MiuixTheme.colorScheme.primary
                            } else {
                                MiuixTheme.colorScheme.onSurfaceVariantSummary
                            },
                        )
                    }
                    IconButton(onClick = onFavorite) {
                        Icon(
                            imageVector = if (item.favorite) MiuixIcons.FolderFill else MiuixIcons.Folder,
                            contentDescription = "收藏",
                            tint = if (item.favorite) {
                                MiuixTheme.colorScheme.primary
                            } else {
                                MiuixTheme.colorScheme.onSurfaceVariantSummary
                            },
                        )
                    }
                }
            }
        },
    ) {
        item.lastChapterTitle?.let { chapter ->
            Text(
                text = "更新至 $chapter",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Row(
            modifier = Modifier.padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item.likes?.let { likes ->
                Text(
                    text = "♥ $likes",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Text(
                text = formatUnixSeconds(item.updateAt),
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

/** 小说详情：头部信息 + 目录/相关/评论三档 + 阅读入口。 */
@Composable
fun NovelDetailScreen(
    novelId: String,
    onBack: () -> Unit,
    onOpenNovel: (String) -> Unit,
    onOpenReader: (String, String) -> Unit,
    onOpenSearch: (String, SearchScope) -> Unit,
    onOpenLogin: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val user by Jm3Graph.account.user.collectAsState()
    var detail by remember { mutableStateOf<NovelDetail?>(null) }
    var savedChapter by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val selectionHaptic = rememberSelectionHaptic()
    var ascending by rememberSaveable { mutableStateOf(true) }
    var descriptionExpanded by rememberSaveable { mutableStateOf(false) }
    var actionBusy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun load() {
        loading = true
        error = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { Jm3Graph.gateway.novelDetail(novelId) }
            }
            loading = false
            result
                .onSuccess { detail = it }
                .onFailure { error = it.message ?: "加载失败" }
        }
    }

    LaunchedEffect(novelId) {
        savedChapter = Jm3Graph.settings.novelLastChapter(novelId)
        load()
    }

    fun openChapter(chapter: NovelChapterMeta) {
        if (chapter.needBuy) {
            scope.launch {
                snackbarHostState.showSnackbar("该章节需付费购买，当前版本暂不支持购买")
            }
            return
        }
        onOpenReader(novelId, chapter.ncid)
    }

    /** 继续阅读：优先上次章节，其次第一章。 */
    fun startReading(current: NovelDetail) {
        val target = current.chapters.firstOrNull { it.ncid == savedChapter }
            ?: current.chapters.firstOrNull()
        if (target == null) {
            scope.launch { snackbarHostState.showSnackbar("暂无章节") }
            return
        }
        openChapter(target)
    }

    fun toggleLike(current: NovelDetail) {
        if (current.liked) {
            scope.launch { snackbarHostState.showSnackbar("已经点过赞了") }
            return
        }
        if (actionBusy) return
        actionBusy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { Jm3Graph.gateway.toggleNovelLike(novelId) }
            }
            actionBusy = false
            result
                .onSuccess {
                    detail = current.copy(liked = true)
                    snackbarHostState.showSnackbar("点赞成功")
                }
                .onFailure { snackbarHostState.showSnackbar("点赞失败：" + (it.message ?: "请稍后重试")) }
        }
    }

    fun toggleFavorite(current: NovelDetail) {
        if (user == null) {
            onOpenLogin()
            return
        }
        if (actionBusy) return
        actionBusy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { Jm3Graph.gateway.toggleNovelFavorite(novelId) }
            }
            actionBusy = false
            result
                .onSuccess {
                    detail = current.copy(favorite = !current.favorite)
                    snackbarHostState.showSnackbar(if (current.favorite) "已取消收藏" else "已加入收藏")
                }
                .onFailure { snackbarHostState.showSnackbar("收藏失败：" + (it.message ?: "请稍后重试")) }
        }
    }

    GlassScaffold(
        topBar = {
            GlassTopAppBar(
                title = detail?.name ?: "小说详情",
                subtitle = detail?.author.orEmpty(),
                navigationIcon = { BackButton(onBack = onBack) },
                // 透明底色：玻璃底色与模糊由 GlassScaffold 统一绘制
                color = Color.Transparent,
            )
        },
        snackbarHost = { GlassSnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when {
            loading -> LoadingScreen(modifier = Modifier.padding(innerPadding))
            error != null -> ErrorScreen(
                message = "加载失败：$error",
                onRetry = ::load,
                modifier = Modifier.padding(innerPadding),
            )
            else -> {
                val current = detail
                if (current == null) {
                    CenteredEmptyState(
                        title = "小说不存在",
                        hint = "可能已下架",
                        modifier = Modifier.padding(innerPadding),
                    )
                } else {
                    // 首项下移让出顶栏高度，滚动时内容从玻璃下方穿过
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = innerPadding,
                    ) {
                        item(key = "header") {
                            NovelDetailHeader(
                                detail = current,
                                savedChapter = savedChapter,
                                descriptionExpanded = descriptionExpanded,
                                onToggleDescription = { descriptionExpanded = !descriptionExpanded },
                                onTagClick = { tag -> onOpenSearch(tag, SearchScope.TAG) },
                                onStartReading = { startReading(current) },
                                onToggleLike = { toggleLike(current) },
                                onToggleFavorite = { toggleFavorite(current) },
                                onComments = { tab = 2 },
                            )
                        }
                        item(key = "tabs") {
                            GlassChipRow(
                                tabs = listOf("目录", "相关", "评论"),
                                selectedTabIndex = tab,
                                onTabSelected = {
                                    selectionHaptic()
                                    tab = it
                                },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            )
                        }
                        when (tab) {
                            0 -> {
                                item(key = "sort") {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            text = "共 ${current.chapters.size} 章",
                                            style = MiuixTheme.textStyles.footnote1,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        )
                                        TextButton(
                                            text = if (ascending) "正序" else "倒序",
                                            onClick = { ascending = !ascending },
                                            minWidth = 0.dp,
                                            minHeight = 32.dp,
                                        )
                                    }
                                }
                                val chapters = if (ascending) current.chapters else current.chapters.reversed()
                                items(chapters, key = { it.ncid }) { chapter ->
                                    NovelChapterRow(
                                        chapter = chapter,
                                        read = chapter.ncid == savedChapter,
                                        onClick = { openChapter(chapter) },
                                    )
                                }
                            }
                            1 -> {
                                if (current.related.isEmpty()) {
                                    item(key = "related-empty") { EmptyState(title = "暂无相关作品") }
                                } else {
                                    items(current.related, key = { it.id }) { related ->
                                        NovelListRow(
                                            item = related,
                                            busy = false,
                                            onClick = { onOpenNovel(related.id) },
                                            onLike = {},
                                            onFavorite = {},
                                        )
                                    }
                                }
                            }
                            else -> item(key = "comments") {
                                NovelCommentsPanel(
                                    novelId = novelId,
                                    onOpenLogin = onOpenLogin,
                                    snackbarHostState = snackbarHostState,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 详情头部：封面 + 元信息 + 标签 + 三宫格操作 + 阅读按钮 + 简介。 */
@Composable
private fun NovelDetailHeader(
    detail: NovelDetail,
    savedChapter: String?,
    descriptionExpanded: Boolean,
    onToggleDescription: () -> Unit,
    onTagClick: (String) -> Unit,
    onStartReading: () -> Unit,
    onToggleLike: () -> Unit,
    onToggleFavorite: () -> Unit,
    onComments: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            NovelCover(url = detail.coverUrl, width = 100.dp, height = 136.dp)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = detail.name,
                    style = MiuixTheme.textStyles.title4,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "NVL${detail.id} · ${detail.author.ifBlank { "佚名" }}",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = buildString {
                        append(if (detail.isEnd) "已完结" else "连载中")
                        if (detail.serialStatus.isNotBlank()) append(" · ${detail.serialStatus}")
                        append(" · ${detail.chapters.size} 章")
                    },
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = buildString {
                        val added = formatUnixSeconds(detail.addtime)
                        if (added.isNotBlank()) append("上架 $added")
                        detail.totalViews?.let { append(" · 阅 $it") }
                        detail.likes?.let { append(" · ♥ $it") }
                    },
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        if (detail.tags.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                detail.tags.forEach { tag ->
                    TextButton(
                        text = tag,
                        onClick = { onTagClick(tag) },
                        minWidth = 0.dp,
                        minHeight = 30.dp,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Button(
            onClick = onStartReading,
            colors = ButtonDefaults.buttonColorsPrimary(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = if (savedChapter == null) "开始阅读" else "继续阅读")
        }
        Card(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                ActionCell(
                    icon = if (detail.favorite) MiuixIcons.FolderFill else MiuixIcons.Folder,
                    label = "收藏",
                    active = detail.favorite,
                    modifier = Modifier.weight(1f),
                    onClick = onToggleFavorite,
                )
                ActionCell(
                    icon = if (detail.liked) MiuixIcons.FavoritesFill else MiuixIcons.Favorites,
                    label = "喜欢",
                    active = detail.liked,
                    modifier = Modifier.weight(1f),
                    onClick = onToggleLike,
                )
                ActionCell(
                    icon = MiuixIcons.Messages,
                    label = "评论",
                    active = false,
                    modifier = Modifier.weight(1f),
                    onClick = onComments,
                )
            }
        }
        if (detail.description.isNotBlank()) {
            Card(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onToggleDescription).padding(14.dp)) {
                    Text(
                        text = detail.description,
                        style = MiuixTheme.textStyles.body2,
                        maxLines = if (descriptionExpanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (descriptionExpanded) "收起" else "展开简介",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.primary,
                    )
                }
            }
        }
        // ascending 在章节栏使用；此处读取一次保证参数被使用且切换排序时整块重组
        if (!descriptionExpanded) Spacer(modifier = Modifier.height(2.dp))
    }
}


/** 单个章节行：标题 + 已读/new/购买标记。 */
@Composable
private fun NovelChapterRow(
    chapter: NovelChapterMeta,
    read: Boolean,
    onClick: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = chapter.sort?.let { "#$it" } ?: "·",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.width(38.dp),
            )
            Text(
                text = chapter.title.ifBlank { "未命名章节" },
                style = MiuixTheme.textStyles.body2,
                color = if (read) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (chapter.isNew) {
                Text(
                    text = "new",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            if (chapter.needBuy) {
                Text(
                    text = "需购买",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            } else if (read) {
                Text(
                    text = "已读",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

/** 小说阅读页：正文 + 字号 + 繁简 + 章节切换 + 评论/收藏。 */
@Composable
fun NovelReaderScreen(
    novelId: String,
    initialChapterId: String,
    onBack: () -> Unit,
    onOpenLogin: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val user by Jm3Graph.account.user.collectAsState()
    var detail by remember { mutableStateOf<NovelDetail?>(null) }
    var chapter by remember { mutableStateOf<NovelChapter?>(null) }
    var currentId by rememberSaveable { mutableStateOf(initialChapterId) }
    var fontSize by remember { mutableIntStateOf(SettingsRepository.DEFAULT_NOVEL_FONT_SIZE) }
    var lang by rememberSaveable { mutableStateOf(SettingsRepository.DEFAULT_NOVEL_LANG) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var favorited by remember { mutableStateOf(false) }
    var commentsOpen by remember { mutableStateOf(false) }
    var fontSheetOpen by remember { mutableStateOf(false) }
    var loadToken by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    fun loadChapter(chapterId: String, language: String, showLoading: Boolean) {
        loadToken++
        val token = loadToken
        if (showLoading) loading = true
        error = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { Jm3Graph.gateway.novelChapter(chapterId, language) }
            }
            if (token != loadToken) return@launch
            loading = false
            result
                .onSuccess {
                    chapter = it
                    currentId = chapterId
                    favorited = it.favorite
                    scope.launch { Jm3Graph.settings.setNovelLastChapter(novelId, chapterId) }
                }
                .onFailure { error = it.message ?: "章节加载失败" }
        }
    }

    LaunchedEffect(novelId) {
        fontSize = Jm3Graph.settings.novelFontSize()
        lang = Jm3Graph.settings.novelLang()
        val detailResult = withContext(Dispatchers.IO) {
            runCatching { Jm3Graph.gateway.novelDetail(novelId) }
        }
        detailResult.onSuccess {
            detail = it
            favorited = it.favorite
        }
        loadChapter(currentId, lang, showLoading = true)
    }

    val chapters = detail?.chapters.orEmpty()
    val currentIndex = chapters.indexOfFirst { it.ncid == currentId }
    val prevChapter = chapters.getOrNull(currentIndex - 1)
    val nextChapter = chapters.getOrNull(currentIndex + 1)

    fun goTo(target: NovelChapterMeta?) {
        if (target == null) {
            scope.launch { snackbarHostState.showSnackbar("已到尽头") }
            return
        }
        if (target.needBuy) {
            scope.launch { snackbarHostState.showSnackbar("该章节需付费购买，当前版本暂不支持购买") }
            return
        }
        loadChapter(target.ncid, lang, showLoading = true)
    }

    fun toggleFavorite() {
        if (user == null) {
            onOpenLogin()
            return
        }
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { Jm3Graph.gateway.toggleNovelFavorite(novelId) }
            }
            result
                .onSuccess {
                    favorited = !favorited
                    snackbarHostState.showSnackbar(if (favorited) "已加入收藏" else "已取消收藏")
                }
                .onFailure { snackbarHostState.showSnackbar("收藏失败：" + (it.message ?: "请稍后重试")) }
        }
    }

    fun changeLang(next: String) {
        lang = next
        scope.launch { Jm3Graph.settings.setNovelLang(next) }
        loadChapter(currentId, next, showLoading = false)
    }

    fun changeFontSize(delta: Int) {
        val next = (fontSize + delta).coerceIn(
            SettingsRepository.NOVEL_FONT_SIZE_MIN,
            SettingsRepository.NOVEL_FONT_SIZE_MAX,
        )
        if (next == fontSize) return
        fontSize = next
        scope.launch { Jm3Graph.settings.setNovelFontSize(next) }
    }

    GlassScaffold(
        topBar = {
            GlassTopAppBar(
                title = chapter?.title ?: "阅读",
                subtitle = detail?.name.orEmpty(),
                navigationIcon = { BackButton(onBack = onBack) },
                // 透明底色：玻璃底色与模糊由 GlassScaffold 统一绘制
                color = Color.Transparent,
                actions = {
                    TextButton(
                        text = if (lang == "tw") "繁" else "简",
                        onClick = { changeLang(if (lang == "tw") "cn" else "tw") },
                        minWidth = 0.dp,
                        minHeight = 36.dp,
                    )
                },
            )
        },
        snackbarHost = { GlassSnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when {
            loading -> LoadingScreen(modifier = Modifier.padding(innerPadding))
            error != null -> ErrorScreen(
                message = "加载失败：$error",
                onRetry = { loadChapter(currentId, lang, showLoading = true) },
                modifier = Modifier.padding(innerPadding),
            )
            chapter == null -> CenteredEmptyState(
                title = "章节不存在",
                hint = "可能已下架",
                modifier = Modifier.padding(innerPadding),
            )
            else -> chapter?.let { body ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        // 内边距放在滚动容器内部，正文才能从玻璃顶栏下方穿过
                        .padding(
                            innerPadding.withSpacing(horizontal = 20.dp, extraTop = 12.dp, bottom = 12.dp),
                        ),
                ) {
                    Text(
                        text = body.title,
                        style = MiuixTheme.textStyles.title4,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = body.contentText,
                        fontSize = fontSize.sp,
                        lineHeight = (fontSize * 1.75).sp,
                    )
                    Spacer(modifier = Modifier.height(96.dp))
                }
            }
        }

        // 底栏原由 Scaffold 的 floatingToolbar 槽承载；GlassScaffold 无该槽，
        // 改为内容层底部居中的覆盖层，位置与原先一致
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding() + 4.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            NovelReaderToolbar(
                favorited = favorited,
                canPrev = prevChapter != null,
                canNext = nextChapter != null,
                onFont = { fontSheetOpen = true },
                onPrev = { goTo(prevChapter) },
                onNext = { goTo(nextChapter) },
                onComments = { commentsOpen = true },
                onFavorite = ::toggleFavorite,
            )
        }

        OverlayBottomSheet(
            show = fontSheetOpen,
            title = "字号",
            onDismissRequest = { fontSheetOpen = false },
        ) {
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        text = "当前字号 ${fontSize}sp",
                        style = MiuixTheme.textStyles.body2,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        TextButton(
                            text = "A-",
                            onClick = { changeFontSize(-1) },
                            enabled = fontSize > SettingsRepository.NOVEL_FONT_SIZE_MIN,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            text = "A+",
                            onClick = { changeFontSize(1) },
                            enabled = fontSize < SettingsRepository.NOVEL_FONT_SIZE_MAX,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        OverlayBottomSheet(
            show = commentsOpen,
            title = "评论",
            onDismissRequest = { commentsOpen = false },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                NovelCommentsPanel(
                    novelId = novelId,
                    onOpenLogin = onOpenLogin,
                    snackbarHostState = snackbarHostState,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

/** 阅读器底部工具栏：字号/上一章/下一章/评论/收藏。 */
@Composable
private fun NovelReaderToolbar(
    favorited: Boolean,
    canPrev: Boolean,
    canNext: Boolean,
    onFont: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onComments: () -> Unit,
    onFavorite: () -> Unit,
) {
    FloatingToolbar {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ActionCell(glyph = "A", label = "字号", onClick = onFont, modifier = Modifier.width(66.dp))
            ActionCell(
                icon = MiuixIcons.ChevronBackward,
                label = "上一章",
                enabled = canPrev,
                onClick = onPrev,
                modifier = Modifier.width(66.dp),
            )
            ActionCell(
                icon = MiuixIcons.ChevronForward,
                label = "下一章",
                enabled = canNext,
                onClick = onNext,
                modifier = Modifier.width(66.dp),
            )
            ActionCell(icon = MiuixIcons.Messages, label = "评论", onClick = onComments, modifier = Modifier.width(66.dp))
            ActionCell(
                icon = if (favorited) MiuixIcons.FolderFill else MiuixIcons.Folder,
                label = "收藏",
                active = favorited,
                onClick = onFavorite,
                modifier = Modifier.width(66.dp),
            )
        }
    }
}


/** 小说评论区：列表 + 加载更多 + 发评论 + 回复。详情页与阅读页共用。 */
@Composable
private fun NovelCommentsPanel(
    novelId: String,
    onOpenLogin: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val user by Jm3Graph.account.user.collectAsState()
    var items by remember { mutableStateOf<List<CommentItem>>(emptyList()) }
    var total by remember { mutableIntStateOf(0) }
    var page by remember { mutableIntStateOf(1) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var loadingMore by remember { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var replyTarget by remember { mutableStateOf<CommentItem?>(null) }
    var replyBusy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun load(targetPage: Int, replace: Boolean, showLoading: Boolean) {
        if (showLoading) loading = true
        error = null
        if (!replace) loadingMore = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { Jm3Graph.gateway.novelComments(novelId, targetPage) }
            }
            loading = false
            loadingMore = false
            result
                .onSuccess {
                    items = if (replace) it.items else (items + it.items).distinctBy { comment -> comment.id }
                    total = it.total
                    page = targetPage
                }
                .onFailure { error = it.message ?: "评论加载失败" }
        }
    }

    LaunchedEffect(novelId) { load(1, replace = true, showLoading = true) }

    fun send() {
        val text = draft.trim()
        if (text.isEmpty() || sending) return
        if (user == null) {
            onOpenLogin()
            return
        }
        sending = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { Jm3Graph.gateway.postNovelComment(novelId, text, "") }
            }
            sending = false
            result
                .onSuccess {
                    draft = ""
                    snackbarHostState.showSnackbar("评论已发布")
                    load(1, replace = true, showLoading = false)
                }
                .onFailure { snackbarHostState.showSnackbar("发布失败：" + (it.message ?: "请稍后重试")) }
        }
    }

    fun sendReply(text: String) {
        val target = replyTarget ?: return
        if (text.isBlank() || replyBusy) return
        replyBusy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { Jm3Graph.gateway.replyNovelComment(novelId, text, target.id, "") }
            }
            replyBusy = false
            result
                .onSuccess {
                    replyTarget = null
                    snackbarHostState.showSnackbar("回复已发送")
                    load(1, replace = true, showLoading = false)
                }
                .onFailure { snackbarHostState.showSnackbar("回复失败：" + (it.message ?: "请稍后重试")) }
        }
    }

    Column(modifier = modifier) {
        when {
            loading -> LoadingScreen(modifier = Modifier.fillMaxWidth().padding(24.dp))
            error != null -> ErrorScreen(
                message = "评论加载失败：$error",
                onRetry = { load(1, replace = true, showLoading = true) },
                modifier = Modifier.fillMaxWidth().padding(8.dp),
            )
            items.isEmpty() -> EmptyState(title = "暂无评论", hint = "来说点什么吧")
            else -> {
                items.forEach { item ->
                    CommentRow(item = item, onReply = { replyTarget = it })
                }
                val hasMore = items.size < total
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        loadingMore -> InfiniteProgressIndicator(modifier = Modifier.size(20.dp))
                        hasMore -> TextButton(
                            text = "加载更多",
                            onClick = { load(page + 1, replace = false, showLoading = false) },
                        )
                        else -> Text(
                            text = "已经到底了",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                TextField(
                    value = draft,
                    onValueChange = { draft = it.take(COMMENT_REPLY_MAX_LENGTH) },
                    label = if (user == null) "登录后参与评论" else "写下你的评论",
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "${draft.length}/$COMMENT_REPLY_MAX_LENGTH",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Button(
                        onClick = ::send,
                        enabled = !sending && draft.isNotBlank(),
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) {
                        Text(text = if (sending) "发布中…" else "发布")
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        // 未登录时也给出明确入口，避免输入框静默失效
        if (user == null) {
            BasicComponent(
                title = "未登录",
                summary = "点击登录后参与评论",
                onClick = onOpenLogin,
            )
        }
    }

    CommentReplySheet(
        show = replyTarget != null,
        targetAuthor = replyTarget?.author,
        busy = replyBusy,
        onDismiss = { replyTarget = null },
        onSend = ::sendReply,
    )
}

/** 统一封面：超椭圆裁切 + 缺图占位，保持行高稳定。 */
@Composable
private fun NovelCover(url: String?, width: Dp, height: Dp) {
    Box(
        modifier = Modifier
            .size(width = width, height = height)
            .squircleClip(8.dp)
            .background(MiuixTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = "无封面",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

/** 秒级时间戳 → yyyy-MM-dd；0/负值按空串（不展示）。 */
private fun formatUnixSeconds(seconds: Long): String {
    if (seconds <= 0L) return ""
    return runCatching {
        java.time.Instant.ofEpochSecond(seconds)
            .atZone(java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    }.getOrDefault("")
}
