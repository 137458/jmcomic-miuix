// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.page

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.layout.Divider
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
import com.jm3.app.miuix.ui.LocalWideLayout
import com.jm3.app.miuix.ui.ScreenLoadPlan
import com.jm3.app.miuix.ui.ScreenLoadPhase
import com.jm3.app.miuix.ui.coui.component.CouiAppScaffold
import com.jm3.app.miuix.ui.coui.component.CouiEmptyGroup
import com.jm3.app.miuix.ui.coui.component.CouiErrorGroup
import com.jm3.app.miuix.ui.coui.component.CouiFab
import com.jm3.app.miuix.ui.coui.component.CouiGroupTitle
import com.jm3.app.miuix.ui.coui.component.CouiLoadingGroup
import com.jm3.app.miuix.ui.coui.component.CouiMinTouchTarget
import com.jm3.app.miuix.ui.coui.component.CouiTopBarBackButton
import com.jm3.app.miuix.ui.coui.component.CouiTopBarTitle
import com.jm3.app.miuix.ui.coui.component.CouiWorkRowCard
import com.jm3.app.miuix.ui.coui.component.couiPressable
import com.jm3.app.miuix.ui.coui.component.couiScreenContentPadding
import com.jm3.app.miuix.ui.coui.component.couiTopAppBarColors
import com.jm3.app.miuix.ui.coui.icon.CouiIcons
import com.jm3.app.miuix.ui.coui.theme.CouiStrings
import com.jm3.app.miuix.ui.coui.theme.CouiTheme
import com.jm3.app.miuix.ui.planLoad
import com.jm3.app.miuix.ui.readableWidth
import com.jm3.app.miuix.ui.skin.LocalSkin
import com.jm3.core.model.CreatorRelatedWork
import com.jm3.core.model.CreatorWork
import com.jm3.core.session.SessionOutcome
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon

/** 正文与图片正文区占位比例：作者作品多是长图配文，图片区保持 0.72 的画幅。 */
private const val COUI_CREATOR_WORK_IMAGE_ASPECT = 0.72f

private sealed interface CouiCreatorWorkState {
    data object Loading : CouiCreatorWorkState
    data class Failed(val plan: ScreenLoadPlan) : CouiCreatorWorkState
    data class Loaded(val work: CreatorWork) : CouiCreatorWorkState
}

/**
 * COUI 作者作品详情页。
 *
 * 大屏双栏：左栏放标题、正文与相关作品，右栏单独承载图片，读图时正文不会跟着滚走。
 * 窄屏两栏内容按序拼成一列，图片跟在正文后面。
 */
@Composable
internal fun CouiCreatorWorkScreen(
    deps: ScreenDeps,
    workId: String,
    onBack: () -> Unit,
    onOpenWork: (String) -> Unit,
    onOpenLogin: () -> Unit,
) {
    CouiTheme {
        CouiCreatorWorkBody(
            deps = deps,
            workId = workId,
            onBack = onBack,
            onOpenWork = onOpenWork,
            onOpenLogin = onOpenLogin,
        )
    }
}

@Composable
private fun CouiCreatorWorkBody(
    deps: ScreenDeps,
    workId: String,
    onBack: () -> Unit,
    onOpenWork: (String) -> Unit,
    onOpenLogin: () -> Unit,
) {
    val gateway = deps.gateway
    val authCall = deps.authCall
    val coroutineScope = rememberCoroutineScope()
    val wideLayout = LocalWideLayout.current
    val twoPane = wideLayout.twoPaneDetail
    val leftListState = rememberLazyListState()
    val rightListState = rememberLazyListState()
    val singleListState = rememberLazyListState()

    var state by remember(workId) { mutableStateOf<CouiCreatorWorkState>(CouiCreatorWorkState.Loading) }
    var requestToken by remember { mutableIntStateOf(0) }

    fun load() {
        requestToken++
        val token = requestToken
        state = CouiCreatorWorkState.Loading
        coroutineScope.launch {
            val outcome = authCall.call { gateway.creatorWork(workId) }
            if (token != requestToken) return@launch
            state = when (outcome) {
                is SessionOutcome.Ok -> CouiCreatorWorkState.Loaded(outcome.value)
                else -> CouiCreatorWorkState.Failed(
                    planLoad(outcome, hasContent = false, failureMessage = CouiStrings.LoadFailed),
                )
            }
        }
    }

    LaunchedEffect(workId) { load() }

    val work = (state as? CouiCreatorWorkState.Loaded)?.work
    val scrollBehavior = CouiTopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        canScroll = {
            if (twoPane) {
                leftListState.canScrollForward || rightListState.canScrollForward
            } else {
                singleListState.canScrollForward || singleListState.canScrollBackward
            }
        },
    )

    val metaItems: androidx.compose.foundation.lazy.LazyListScope.() -> Unit = {
        when (val current = state) {
            CouiCreatorWorkState.Loading -> item(key = "coui-work-loading") { CouiLoadingGroup() }

            is CouiCreatorWorkState.Failed -> item(key = "coui-work-error") {
                CouiCreatorWorkScreenFailure(plan = current.plan, onRetry = { load() }, onOpenLogin = onOpenLogin)
            }

            is CouiCreatorWorkState.Loaded -> {
                val loaded = current.work
                item(key = "coui-work-meta") { CouiCreatorWorkMetaCard(work = loaded) }
                if (loaded.contentText.isBlank()) {
                    item(key = "coui-work-content-empty") { CouiEmptyGroup(text = "暂无正文") }
                } else {
                    item(key = "coui-work-content") {
                        Group(title = "正文") {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            ) {
                                Text(
                                    text = loaded.contentText,
                                    kind = CouiTextKind.Body,
                                    color = CouiKitTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Start,
                                )
                            }
                        }
                    }
                }
                item(key = "coui-work-related-title") {
                    CouiGroupTitle(
                        title = if (loaded.related.isEmpty()) "相关作品" else "相关作品（${loaded.related.size}）",
                    )
                }
                if (loaded.related.isEmpty()) {
                    item(key = "coui-work-related-empty") { CouiEmptyGroup(text = "暂无相关作品") }
                } else {
                    itemsIndexed(
                        items = loaded.related,
                        key = { _, item -> "coui-work-related-${item.id}" },
                        contentType = { _, _ -> "coui-work-related" },
                    ) { _, item ->
                        CouiCreatorRelatedRow(item = item, onClick = { onOpenWork(item.id) })
                    }
                    item(key = "coui-work-related-footer") { Footer(text = "作者的其他作品") }
                }
            }
        }
    }

    val imageItems: androidx.compose.foundation.lazy.LazyListScope.() -> Unit = {
        when (val current = state) {
            CouiCreatorWorkState.Loading -> item(key = "coui-work-images-loading") { CouiLoadingGroup() }

            is CouiCreatorWorkState.Failed -> item(key = "coui-work-images-error") {
                CouiErrorGroup(message = current.plan.message.orEmpty(), onRetry = { load() })
            }

            is CouiCreatorWorkState.Loaded -> {
                val images = current.work.images
                if (images.isEmpty()) {
                    item(key = "coui-work-images-empty") {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            CouiEmptyGroup(text = "暂无图片")
                            Footer(text = "该作品还在补充中")
                        }
                    }
                } else {
                    itemsIndexed(
                        items = images,
                        key = { index, _ -> "coui-work-image-$index" },
                        contentType = { _, _ -> "coui-work-image" },
                    ) { index, url ->
                        CouiCreatorWorkImage(url = url, index = index)
                    }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CouiAppScaffold(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                LargeTopAppBar(
                    title = { CouiTopBarTitle(text = work?.title ?: "作品详情") },
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
            // 折叠位移留到测量期读取：组合期算成字面量会把首帧的顶栏高度冻结住
            val contentPadding = couiScreenContentPadding(
                scrollBehavior = scrollBehavior,
                innerPadding = innerPadding,
                horizontalPadding = null,
                extraBottom = 72.dp,
            )
            if (twoPane) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .readableWidth()
                        .consumeWindowInsets(innerPadding),
                ) {
                    LazyColumn(
                        state = leftListState,
                        modifier = Modifier.weight(0.38f).fillMaxHeight(),
                        contentPadding = contentPadding,
                        overscrollEffect = rememberCouiOverscrollEffect(),
                    ) {
                        metaItems()
                    }
                    LazyColumn(
                        state = rightListState,
                        modifier = Modifier.weight(0.62f).fillMaxHeight(),
                        contentPadding = contentPadding,
                        overscrollEffect = rememberCouiOverscrollEffect(),
                    ) {
                        imageItems()
                    }
                }
            } else {
                LazyColumn(
                    state = singleListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .readableWidth()
                        .consumeWindowInsets(innerPadding),
                    contentPadding = contentPadding,
                    overscrollEffect = rememberCouiOverscrollEffect(),
                ) {
                    metaItems()
                    imageItems()
                }
            }
        }
    }
}

/** 屏级失败块：会话失效给登录入口，其余给重试。 */
@Composable
private fun CouiCreatorWorkScreenFailure(plan: ScreenLoadPlan, onRetry: () -> Unit, onOpenLogin: () -> Unit) {
    val scheme = CouiKitTheme.colorScheme
    if (plan.phase == ScreenLoadPhase.Expired) {
        Group(title = "需要登录", footer = plan.message.orEmpty()) {
            Item(
                title = { Text(text = "请先登录后再查看，点此去登录", kind = CouiTextKind.Body) },
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

/** 标题卡：作品名 + 作者 + 创作时间，信息量小所以只留一张卡。 */
@Composable
private fun CouiCreatorWorkMetaCard(work: CreatorWork) {
    val scheme = CouiKitTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CouiPageDefaults.horizontalMargin)
            .padding(top = CouiPageDefaults.categoryTopSpacing, bottom = CouiPageDefaults.groupSpacing)
            .clip(CouiPageDefaults.shape)
            .background(scheme.card)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = work.title,
            kind = CouiTextKind.SectionTitle,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Start,
        )
        val author = work.authorName?.takeIf { it.isNotBlank() }
        if (author != null) {
            Text(
                text = author,
                kind = CouiTextKind.Description,
                color = scheme.accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start,
            )
        }
        val date = work.workDate?.takeIf { it.isNotBlank() }
        Text(
            text = date ?: "创作时间未知",
            kind = CouiTextKind.Description,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Start,
        )
    }
}

/** 正文图片：按 0.72 画幅留位，Fit 保证长图不裁切。 */
@Composable
private fun CouiCreatorWorkImage(url: String, index: Int) {
    val scheme = CouiKitTheme.colorScheme
    val shape = RoundedCornerShape(LocalSkin.current.shapes.cardCorner)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CouiPageDefaults.horizontalMargin)
            .padding(bottom = 12.dp)
            .aspectRatio(COUI_CREATOR_WORK_IMAGE_ASPECT)
            .clip(shape)
            .background(scheme.container),
    ) {
        AsyncImage(
            model = url,
            contentDescription = "第 ${index + 1} 张图",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** 相关作品行：复用作品行卡，副标题给时间，右上角标签给平台。 */
@Composable
private fun CouiCreatorRelatedRow(item: CreatorRelatedWork, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CouiPageDefaults.horizontalMargin, vertical = 4.dp),
    ) {
        CouiWorkRowCard(
            coverUrl = item.coverUrl,
            title = item.title,
            onClick = onClick,
            subtitle = item.workDate?.takeIf { it.isNotBlank() },
            category = item.platform?.takeIf { it.isNotBlank() },
            coverContentDescription = item.title,
        )
    }
}
