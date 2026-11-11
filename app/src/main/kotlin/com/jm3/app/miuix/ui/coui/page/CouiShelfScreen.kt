// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.page

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.CouiDialogPlacement
import com.itos.coui.dialog.Dialog
import com.itos.coui.scroll.rememberCouiOverscrollEffect
import com.itos.coui.theme.CouiTheme as CouiKitTheme
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.data.download.contentDescriptionRes
import com.jm3.app.miuix.data.download.textRes
import com.jm3.app.miuix.ui.LocalWideLayout
import com.jm3.app.miuix.ui.coui.component.CouiEmptyGroup
import com.jm3.app.miuix.ui.coui.component.CouiLoadMoreFooter
import com.jm3.app.miuix.ui.coui.component.CouiLoadingGroup
import com.jm3.app.miuix.ui.coui.component.CouiMinTouchTarget
import com.jm3.app.miuix.ui.coui.component.CouiScreenScaffold
import com.jm3.app.miuix.ui.coui.component.CouiWorkCardTokens
import com.jm3.app.miuix.ui.coui.component.CouiWorkGridCard
import com.jm3.app.miuix.ui.coui.component.couiDestructiveConfirmAction
import com.jm3.app.miuix.ui.coui.component.couiDialogMaterial
import com.jm3.app.miuix.ui.coui.component.couiPressable
import com.jm3.app.miuix.ui.coui.component.rememberRetainedTarget
import com.jm3.app.miuix.ui.coui.icon.CouiIcons
import com.jm3.app.miuix.ui.coui.theme.CouiTheme
import com.jm3.app.miuix.ui.orDegrade
import com.jm3.app.miuix.ui.readableWidth
import com.jm3.core.CoverLocator
import com.jm3.core.download.DownloadAction
import com.jm3.core.download.DownloadRecord
import com.jm3.core.download.DownloadRow
import com.jm3.core.download.presentRow
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon

/**
 * 本地书架（COUI 栅格版）：参数与旧 Miuix 页一比一，数据完全来自本机下载管理器，
 * 队列更新事件驱动刷新，封面域名整屏只取一次。
 *
 * 下载列表是本机全量快照、没有分页，页脚固定为「已经到底了」，
 * 主操作按 presentRow 的判定收敛为整卡点击（打开 / 暂停 / 续传 / 重试）。
 */
@Composable
fun CouiShelfScreen(
    deps: ScreenDeps,
    onBack: () -> Unit,
    onOpenReader: (String) -> Unit,
) {
    CouiTheme {
        val scope = rememberCoroutineScope()
        val gridState = rememberLazyGridState()
        val columns = LocalWideLayout.current.gridColumns

        var records by remember { mutableStateOf<List<DownloadRecord>>(emptyList()) }
        var pendingDeleteRecord by remember { mutableStateOf<DownloadRecord?>(null) }
        var imageHost by remember { mutableStateOf("") }
        // 首帧还没拿到下载快照：先给加载态，避免空列表一闪
        var loaded by remember { mutableStateOf(false) }

        // 初次加载 + 队列进度驱动刷新
        LaunchedEffect(Unit) {
            deps.downloadManager.ensureStarted()
            records = deps.downloadManager.records()
            loaded = true
            deps.downloadManager.queue.updates.collect {
                records = deps.downloadManager.records()
            }
        }

        // 图片域名整个屏幕只取一次：网关取域名是阻塞方法，AuthCall 负责等待会话恢复并切到 IO；
        // 取不到或会话失效时留空串，封面由 CoverLocator.cover 返回 null 退化为占位，不影响卡片内容与操作
        LaunchedEffect(deps.gateway, deps.authCall) {
            imageHost = deps.authCall.call { deps.gateway.imageHost() }.orDegrade("")
        }

        fun performPrimaryAction(record: DownloadRecord, row: DownloadRow) {
            when (row.action) {
                DownloadAction.Open -> onOpenReader(record.photoId)
                DownloadAction.Pause -> deps.downloadManager.pause(record.photoId)
                DownloadAction.Resume, DownloadAction.Retry -> deps.downloadManager.resumeRecord(record)
            }
        }

        fun removeRecord(record: DownloadRecord) {
            scope.launch {
                deps.downloadManager.remove(record.photoId)
                records = records.filterNot { it.photoId == record.photoId }
            }
        }

        CouiScreenScaffold(
            title = "本地书架",
            onBack = onBack,
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
                    !loaded -> item(key = "coui-shelf-loading", span = { GridItemSpan(maxLineSpan) }) {
                        CouiLoadingGroup(modifier = Modifier.fillMaxWidth().padding(top = 24.dp))
                    }

                    records.isEmpty() -> item(key = "coui-shelf-empty", span = { GridItemSpan(maxLineSpan) }) {
                        CouiEmptyGroup(
                            text = "暂无下载，在详情页点下载按钮，章节会出现在这里",
                            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                        )
                    }

                    else -> {
                        items(items = records, key = { "coui-shelf-" + it.photoId }) { record ->
                            val albumId = couiShelfAlbumId(record)
                            val coverUrl = remember(albumId, imageHost) {
                                albumId?.let { CoverLocator.of(imageHost).cover(it, "_3x4") }
                            }
                            // 标签、进度条口径与主操作全部由 :core 的 presentRow 判定，这里只做渲染
                            val row = presentRow(record)
                            val label = stringResource(row.label.textRes())
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.TopCenter,
                            ) {
                                CouiShelfCard(
                                    title = record.title.ifBlank { record.photoId },
                                    coverUrl = coverUrl,
                                    albumId = albumId,
                                    row = row,
                                    subtitle = couiShelfSummary(record, row, label),
                                    onClick = { performPrimaryAction(record, row) },
                                    onDelete = { pendingDeleteRecord = record },
                                    actionLabel = stringResource(row.action.contentDescriptionRes()),
                                )
                            }
                        }
                        item(key = "coui-shelf-footer", span = { GridItemSpan(maxLineSpan) }) {
                            // 本机快照无分页，页脚只表达「已到底」这一态
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

        val toDelete = rememberRetainedTarget(pendingDeleteRecord)
        if (toDelete != null) {
            Dialog(
                onDismissRequest = { pendingDeleteRecord = null },
                visible = pendingDeleteRecord != null,
                material = couiDialogMaterial(),
                title = "删除下载",
                message = "确定要删除《" + toDelete.title.ifBlank { toDelete.photoId } + "》及已下载的本地文件吗？",
                placement = CouiDialogPlacement.Center,
                confirmAction = couiDestructiveConfirmAction(
                    text = "删除",
                    onClick = {
                        removeRecord(toDelete)
                        pendingDeleteRecord = null
                    },
                ),
                dismissAction = CouiDialogAction(
                    text = "取消",
                    onClick = { pendingDeleteRecord = null },
                ),
            )
        }
    }
}

/** 书架卡：整卡点击执行 presentRow 判定的主操作，右上角角标删除，未完成显示进度条。 */
@Composable
private fun CouiShelfCard(
    title: String,
    coverUrl: String?,
    albumId: String?,
    row: DownloadRow,
    subtitle: String,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    actionLabel: String,
) {
    val scheme = CouiKitTheme.colorScheme
    CouiWorkGridCard(
        title = title,
        coverUrl = coverUrl,
        onClick = onClick,
        albumId = albumId,
        coverContentDescription = title,
        subtitle = subtitle,
        badge = {
            CouiShelfDeleteBadge(
                contentDescription = "删除 " + actionLabel,
                onClick = onDelete,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(CouiWorkCardTokens.BadgeInset),
            )
        },
        footer = {
            if (row.showsProgressBar) {
                CouiShelfProgress(
                    donePages = row.donePages,
                    total = row.total,
                    container = scheme.container,
                    accent = scheme.accent,
                )
            }
        },
    )
}

/** 下载进度：卡宽只有 112dp，用 3dp 细条表达，比例按 presentRow 夹取过的页码算。 */
@Composable
private fun ColumnScope.CouiShelfProgress(
    donePages: Int,
    total: Int,
    container: androidx.compose.ui.graphics.Color,
    accent: androidx.compose.ui.graphics.Color,
) {
    val fraction = if (total > 0) (donePages.toFloat() / total).coerceIn(0f, 1f) else 0f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp)
            .height(3.dp)
            .clip(CircleShape)
            .background(container),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .fillMaxHeight()
                .background(accent),
        )
    }
}

/** 栅格格卡右上角的删除角标：落在封面点击区里，自己消费点击，不触发整卡主操作。 */
@Composable
private fun CouiShelfDeleteBadge(
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

/** 卡副标题：状态与进度，前面带禁漫车号；车号缺失时只显示状态，不输出孤立的 “#”。 */
private fun couiShelfSummary(record: DownloadRecord, row: DownloadRow, label: String): String {
    val progress = label + " " + row.donePages + "/" + row.total
    val albumId = couiShelfAlbumId(record) ?: return progress
    return "#" + albumId + " · " + progress
}

/** 车号口径与详情页一致（空 id 与 “-1” 都视为无车号），避免拼出无效封面地址。 */
private fun couiShelfAlbumId(record: DownloadRecord): String? =
    record.albumId.trim().takeIf { it.isNotEmpty() && it != "-1" }
