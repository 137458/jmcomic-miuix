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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.data.download.contentDescriptionRes
import com.jm3.app.miuix.data.download.textRes
import com.jm3.app.miuix.ui.LocalWideLayout
import com.jm3.app.miuix.ui.coui.component.CouiEmptyGroup
import com.jm3.app.miuix.ui.coui.component.CouiLoadMoreFooter
import com.jm3.app.miuix.ui.coui.component.CouiLoadingGroup
import com.jm3.app.miuix.ui.coui.component.CouiMinTouchTarget
import com.jm3.app.miuix.ui.coui.component.CouiPageScaffold
import com.jm3.app.miuix.ui.coui.component.CouiWorkCardTokens
import com.jm3.app.miuix.ui.coui.component.CouiWorkGridCard
import com.jm3.app.miuix.ui.coui.component.couiDestructiveDialogAction
import com.jm3.app.miuix.ui.coui.component.couiPageScroll
import com.jm3.app.miuix.ui.coui.component.couiPressable
import com.jm3.app.miuix.ui.coui.component.isCouiWideWindow
import com.jm3.app.miuix.ui.coui.component.panelBlur
import com.jm3.app.miuix.ui.coui.component.rememberRetainedTarget
import com.jm3.app.miuix.ui.coui.icon.CouiIcons
import com.jm3.app.miuix.ui.coui.theme.CouiTheme
import com.jm3.app.miuix.ui.readableWidth
import com.jm3.app.miuix.ui.route.AppRoutes
import com.jm3.app.miuix.ui.shelf.SHELF_DELETE_CONFIRM
import com.jm3.app.miuix.ui.shelf.SHELF_DELETE_DISMISS
import com.jm3.app.miuix.ui.shelf.SHELF_DELETE_TITLE
import com.jm3.app.miuix.ui.shelf.SHELF_TITLE
import com.jm3.app.miuix.ui.shelf.rememberShelfScaffoldState
import com.jm3.app.miuix.ui.shelf.shelfAlbumId
import com.jm3.app.miuix.ui.shelf.shelfDeleteMessage
import com.jm3.app.miuix.ui.shelf.shelfSummary
import com.jm3.core.CoverLocator
import com.jm3.core.download.DownloadRow
import com.jm3.core.download.presentRow
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.blur.MaterialBlurDefaults
import io.github.suqi8.coui.kmp.layout.DialogButtonBar
import io.github.suqi8.coui.kmp.layout.DialogButtonBarAction
import io.github.suqi8.coui.kmp.layout.DialogDefaults
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.rememberCOUIFlingBehavior

/**
 * 本地书架（COUI 栅格版）：参数与旧 Miuix 页一比一，数据完全来自本机下载管理器，
 * 队列更新事件驱动刷新，封面域名整屏只取一次。
 *
 * 下载列表是本机全量快照、没有分页，页脚固定为「已经到底了」，
 * 主操作按 presentRow 的判定收敛为整卡点击（打开 / 暂停 / 续传 / 重试）。
 */
@Composable
internal fun CouiShelfScreen(
    deps: ScreenDeps,
    onBack: () -> Unit,
    nav: AppRoutes,
) {
    CouiTheme {
        val gridState = rememberLazyGridState()
        val columns = LocalWideLayout.current.gridColumns

        val shelf = rememberShelfScaffoldState(deps)
        val records = shelf.records
        val pendingDeleteRecord = shelf.pendingDeleteRecord
        val imageHost = shelf.imageHost
        val loaded = shelf.loaded

        CouiPageScaffold(
            title = SHELF_TITLE,
            onBack = onBack,
            wide = isCouiWideWindow(),
        ) { innerPadding ->
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                state = gridState,
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
                            val albumId = shelfAlbumId(record)
                            val coverUrl = remember(albumId, imageHost) {
                                albumId?.let { CoverLocator.of(imageHost).cover(it, "_3x4") }
                            }
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
                                    subtitle = shelfSummary(record, row, label),
                                    onClick = { if (shelf.performPrimaryAction(record, row)) nav.reader(record.photoId) },
                                    onDelete = { shelf.requestDelete(record) },
                                    actionLabel = stringResource(row.action.contentDescriptionRes()),
                                )
                            }
                        }
                        item(key = "coui-shelf-footer", span = { GridItemSpan(maxLineSpan) }) {
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

            val toDelete = rememberRetainedTarget(pendingDeleteRecord)
            if (toDelete != null) {
                val deleteBlur = panelBlur(MaterialBlurDefaults::dialog)
                OverlayDialog(
                    show = pendingDeleteRecord != null,
                    title = SHELF_DELETE_TITLE,
                    summary = shelfDeleteMessage(toDelete),
                    backgroundModifier = deleteBlur?.backgroundModifier ?: Modifier,
                    backgroundColor = deleteBlur?.backgroundColor ?: DialogDefaults.backgroundColor(),
                    materialStroke = deleteBlur != null,
                    onDismissRequest = { shelf.dismissDelete() },
                ) {
                    DialogButtonBar(
                        negative = DialogButtonBarAction(
                            text = SHELF_DELETE_DISMISS,
                            onClick = { shelf.dismissDelete() },
                        ),
                        positive = null,
                        neutral = couiDestructiveDialogAction(
                            text = SHELF_DELETE_CONFIRM,
                            onClick = { shelf.confirmDelete() },
                        ),
                    )
                }
            }
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
    val scheme = COUITheme.colorScheme
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
                    container = scheme.container4,
                    accent = scheme.primary,
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
    container: Color,
    accent: Color,
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
    val scheme = COUITheme.colorScheme
    Box(
        modifier = modifier
            .couiPressable(
                shape = CircleShape,
                backgroundColor = scheme.container4,
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
