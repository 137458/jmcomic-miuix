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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
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
import com.jm3.app.miuix.ui.ScreenLoadPhase
import com.jm3.app.miuix.ui.coui.component.CouiEmptyGroup
import com.jm3.app.miuix.ui.coui.component.CouiErrorGroup
import com.jm3.app.miuix.ui.coui.component.CouiFab
import com.jm3.app.miuix.ui.coui.component.CouiLoadMoreFooter
import com.jm3.app.miuix.ui.coui.component.CouiLoadingGroup
import com.jm3.app.miuix.ui.coui.component.CouiMinTouchTarget
import com.jm3.app.miuix.ui.coui.component.CouiScreenScaffold
import com.jm3.app.miuix.ui.coui.component.CouiSegmentedCapsules
import com.jm3.app.miuix.ui.coui.component.CouiTopBarAction
import com.jm3.app.miuix.ui.coui.component.CouiWorkCardTokens
import com.jm3.app.miuix.ui.coui.component.CouiWorkGridCard
import com.jm3.app.miuix.ui.coui.component.couiPressable
import com.jm3.app.miuix.ui.coui.icon.CouiIcons
import com.jm3.app.miuix.ui.coui.theme.CouiTheme
import com.jm3.app.miuix.ui.formatEpochSeconds
import com.jm3.app.miuix.ui.novelActionNotice
import com.jm3.app.miuix.ui.planLoad
import com.jm3.app.miuix.ui.readableWidth
import com.jm3.app.miuix.ui.rememberDateFormatter
import com.jm3.core.model.NovelMeta
import com.jm3.core.model.NovelOrder
import com.jm3.core.session.SessionOutcome
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SnackbarHostState

/**
 * 小说列表（COUI 栅格版）：参数与旧 Miuix 页一比一，排序 / 关键词搜索 / 点赞 / 收藏全部沿用旧页口径。
 *
 * 旧页的胶囊搜索是玻璃浮层件，COUI 没有对应浮层槽位，改为正文内的胶囊输入行（IME 搜索键或右侧「搜索」提交）；
 * 网关按页返回 NovelPage 且旧页只取第 1 页，所以页脚固定为「已经到底了」，刷新走右下角 FAB。
 */
@Composable
fun CouiNovelListScreen(
    deps: ScreenDeps,
    onBack: () -> Unit,
    onOpenNovel: (String) -> Unit,
    onOpenLogin: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    CouiTheme {
        val authCall = deps.authCall
        val scope = rememberCoroutineScope()
        val gridState = rememberLazyGridState()
        val columns = LocalWideLayout.current.gridColumns
        val user by deps.account.user.collectAsState()

        var orderName by rememberSaveable { mutableStateOf(NovelOrder.DEFAULT.name) }
        val order = NovelOrder.entries.firstOrNull { it.name == orderName } ?: NovelOrder.DEFAULT
        var query by rememberSaveable { mutableStateOf("") }
        var appliedQuery by rememberSaveable { mutableStateOf("") }
        var items by remember { mutableStateOf<List<NovelMeta>>(emptyList()) }
        var total by remember { mutableIntStateOf(0) }
        var loading by remember { mutableStateOf(true) }
        var failure by remember { mutableStateOf<Plan?>(null) }
        var refreshing by remember { mutableStateOf(false) }
        // 快速切换排序/搜索时旧响应不能覆盖新结果
        var loadToken by remember { mutableIntStateOf(0) }
        var busyId by remember { mutableStateOf<String?>(null) }
        // 列表层只建一份格式化器，卡内不再各自持有
        val dateFormat = rememberDateFormatter("yyyy-MM-dd")

        fun load(orderValue: String, keyword: String, showLoading: Boolean) {
            loadToken++
            val token = loadToken
            if (showLoading) loading = true
            failure = null
            scope.launch {
                val outcome = authCall.call {
                    if (keyword.isBlank()) {
                        deps.gateway.novelList(orderValue, 1)
                    } else {
                        deps.gateway.searchNovels(keyword)
                    }
                }
                if (token != loadToken) return@launch
                loading = false
                refreshing = false
                if (outcome is SessionOutcome.Ok) {
                    items = outcome.value.items
                    total = outcome.value.total
                } else {
                    // 已有列表就只提示并保留内容；没有内容才换整屏（失效换成「去登录」）
                    val plan = planLoad(
                        outcome = outcome,
                        hasContent = items.isNotEmpty(),
                        failureMessage = "加载失败",
                    )
                    if (plan.keepContent) {
                        snackbarHostState.showSnackbar(plan.notice.orEmpty())
                    } else {
                        failure = Plan(plan.message.orEmpty(), plan.phase == ScreenLoadPhase.Expired)
                    }
                }
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
                val outcome = authCall.call { deps.gateway.toggleNovelLike(item.id) }
                busyId = null
                val notice = novelActionNotice(outcome, failurePrefix = "点赞失败：")
                if (notice == null) {
                    items = items.map { if (it.id == item.id) it.copy(liked = true) else it }
                    snackbarHostState.showSnackbar("点赞成功")
                } else {
                    snackbarHostState.showSnackbar(notice.message)
                    if (notice.openLogin) onOpenLogin()
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
                val outcome = authCall.call { deps.gateway.toggleNovelFavorite(item.id) }
                busyId = null
                val notice = novelActionNotice(outcome, failurePrefix = "收藏失败：")
                if (notice == null) {
                    items = items.map { if (it.id == item.id) it.copy(favorite = !it.favorite) else it }
                    snackbarHostState.showSnackbar(if (item.favorite) "已取消收藏" else "已加入收藏")
                } else {
                    snackbarHostState.showSnackbar(notice.message)
                    if (notice.openLogin) onOpenLogin()
                }
            }
        }

        val scheme = CouiKitTheme.colorScheme

        Box(modifier = Modifier.fillMaxSize()) {
            CouiScreenScaffold(
                title = if (appliedQuery.isBlank()) "小说" else "小说搜索",
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
                            load(order.apiValue, appliedQuery, showLoading = items.isEmpty())
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
                        loading -> item(key = "coui-novel-loading", span = { GridItemSpan(maxLineSpan) }) {
                            CouiLoadingGroup(modifier = Modifier.fillMaxWidth().padding(top = 24.dp))
                        }

                        failure != null -> item(key = "coui-novel-failed", span = { GridItemSpan(maxLineSpan) }) {
                            val planned = failure
                            CouiErrorGroup(
                                message = if (planned?.sessionExpired == true) {
                                    SessionOutcome.Expired.message
                                } else {
                                    planned?.message.orEmpty()
                                },
                                onRetry = {
                                    if (planned?.sessionExpired == true) {
                                        onOpenLogin()
                                    } else {
                                        load(order.apiValue, appliedQuery, showLoading = true)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                            )
                        }

                        else -> {
                            item(key = "coui-novel-order", span = { GridItemSpan(maxLineSpan) }) {
                                CouiSegmentedCapsules(
                                    items = NovelOrder.entries.map { it.label },
                                    selectedIndex = NovelOrder.entries.indexOf(order).coerceAtLeast(0),
                                    onSelect = { index ->
                                        val next = NovelOrder.entries.getOrNull(index) ?: return@CouiSegmentedCapsules
                                        if (next != order) orderName = next.name
                                    },
                                    modifier = Modifier.padding(vertical = 8.dp),
                                )
                            }
                            item(key = "coui-novel-search", span = { GridItemSpan(maxLineSpan) }) {
                                CouiNovelSearchField(
                                    value = query,
                                    onValueChange = { query = it },
                                    onSubmit = { appliedQuery = query.trim() },
                                    placeholder = "搜索小说",
                                )
                            }
                            if (items.isEmpty()) {
                                item(key = "coui-novel-empty", span = { GridItemSpan(maxLineSpan) }) {
                                    CouiEmptyGroup(
                                        text = if (appliedQuery.isBlank()) "暂无小说" else "没有找到相关小说",
                                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                                    )
                                }
                            } else {
                                items(items = items, key = { "coui-novel-" + it.id }) { item ->
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.TopCenter,
                                    ) {
                                        CouiNovelCard(
                                            item = item,
                                            dateFormat = dateFormat,
                                            busy = busyId == item.id,
                                            onClick = { onOpenNovel(item.id) },
                                            onLike = { like(item) },
                                            onFavorite = { favorite(item) },
                                            accent = scheme.accent,
                                            onSurfaceVariant = scheme.onSurfaceVariant,
                                            container = scheme.container,
                                        )
                                    }
                                }
                                item(key = "coui-novel-total", span = { GridItemSpan(maxLineSpan) }) {
                                    if (total > 0) {
                                        Text(
                                            text = "共 " + total + " 本",
                                            kind = CouiTextKind.Description,
                                            color = scheme.onSurfaceVariant,
                                            maxLines = 1,
                                            textAlign = TextAlign.Start,
                                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                        )
                                    }
                                }
                                item(key = "coui-novel-footer", span = { GridItemSpan(maxLineSpan) }) {
                                    // 网关按页返回、旧页只取第 1 页：本页没有下一页可拉
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
            GlassSnackbarHost(
                state = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/** 本页失败态：与 ScreenLoadPlan 同义，只留渲染要用的两项。 */
private data class Plan(val message: String, val sessionExpired: Boolean)

/** 小说栅格卡：封面 + 名称/作者/更新章节 + 点赞与收藏。 */
@Composable
private fun CouiNovelCard(
    item: NovelMeta,
    dateFormat: java.time.format.DateTimeFormatter,
    busy: Boolean,
    onClick: () -> Unit,
    onLike: () -> Unit,
    onFavorite: () -> Unit,
    accent: androidx.compose.ui.graphics.Color,
    onSurfaceVariant: androidx.compose.ui.graphics.Color,
    container: androidx.compose.ui.graphics.Color,
) {
    CouiWorkGridCard(
        title = item.name,
        coverUrl = item.coverUrl,
        onClick = onClick,
        coverContentDescription = item.name,
        subtitle = item.author.ifBlank { "佚名" },
        category = item.lastChapterTitle?.takeIf { it.isNotBlank() }?.let { "更新至 " + it },
        badge = {
            CouiNovelFavoriteBadge(
                favorite = item.favorite,
                enabled = !busy,
                accent = accent,
                onSurfaceVariant = onSurfaceVariant,
                container = container,
                onClick = onFavorite,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(CouiWorkCardTokens.BadgeInset),
            )
        },
        footer = {
            CouiNovelActions(
                liked = item.liked,
                likes = item.likes,
                enabled = !busy,
                accent = accent,
                onSurfaceVariant = onSurfaceVariant,
                onLike = onLike,
            )
            Text(
                text = formatEpochSeconds(item.updateAt, dateFormat),
                kind = CouiTextKind.Description,
                color = onSurfaceVariant,
                maxLines = 1,
                textAlign = TextAlign.Start,
                modifier = Modifier.padding(top = 2.dp),
            )
        },
    )
}

/** 卡 footer 的点赞动作：点赞不可取消，已赞只显示状态。 */
@Composable
private fun ColumnScope.CouiNovelActions(
    liked: Boolean,
    likes: String?,
    enabled: Boolean,
    accent: androidx.compose.ui.graphics.Color,
    onSurfaceVariant: androidx.compose.ui.graphics.Color,
    onLike: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (liked) "已赞" else "点赞",
            kind = CouiTextKind.Description,
            color = if (liked) onSurfaceVariant else accent,
            maxLines = 1,
            textAlign = TextAlign.Start,
            modifier = Modifier
                .clip(CouiCapsuleShape)
                .couiPressable(shape = CouiCapsuleShape, spotlight = SpotlightPreset.ChipTab, enabled = enabled, minTouchTarget = CouiMinTouchTarget, onClick = onLike)
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
        if (!likes.isNullOrBlank()) {
            Spacer(modifier = Modifier.size(4.dp))
            Text(
                text = "♥ " + likes,
                kind = CouiTextKind.Description,
                color = onSurfaceVariant,
                maxLines = 1,
                textAlign = TextAlign.Start,
            )
        }
    }
}

/** 封面右上角的收藏角标：收藏后主色实底，未收藏走容器色。 */
@Composable
private fun CouiNovelFavoriteBadge(
    favorite: Boolean,
    enabled: Boolean,
    accent: androidx.compose.ui.graphics.Color,
    onSurfaceVariant: androidx.compose.ui.graphics.Color,
    container: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = if (favorite) accent else container
    val tint = if (favorite) CouiKitTheme.colorScheme.onAccent else onSurfaceVariant
    Box(
        modifier = modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(background)
            .couiPressable(
                shape = CircleShape,
                enabled = enabled,
                minTouchTarget = CouiMinTouchTarget,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = CouiIcons.Star,
            contentDescription = if (favorite) "取消收藏" else "收藏",
            modifier = Modifier.size(14.dp),
            tint = tint,
        )
    }
}

/** 正文内胶囊搜索行：IME 搜索键或右侧「搜索」提交，值与已提交关键词分开。 */
@Composable
private fun CouiNovelSearchField(
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
                    .couiPressable(shape = CouiCapsuleShape, spotlight = SpotlightPreset.ChipTab, minTouchTarget = CouiMinTouchTarget, onClick = onSubmit)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}
