package com.jm3.app.miuix.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.data.download.contentDescriptionRes
import com.jm3.app.miuix.data.download.textRes
import com.jm3.app.miuix.ui.components.BackButton
import com.jm3.app.miuix.ui.components.CenteredEmptyState
import com.jm3.app.miuix.ui.components.TrackedLazyColumn
import com.jm3.app.miuix.ui.components.overlayDialogMaxWidth
import com.jm3.app.miuix.ui.glass.GlassScaffold
import com.jm3.app.miuix.ui.glass.GlassTopAppBar
import com.jm3.app.miuix.ui.route.AppRoutes
import com.jm3.app.miuix.ui.shelf.SHELF_DELETE_CONFIRM
import com.jm3.app.miuix.ui.shelf.SHELF_DELETE_DISMISS
import com.jm3.app.miuix.ui.shelf.SHELF_DELETE_TITLE
import com.jm3.app.miuix.ui.shelf.SHELF_TITLE
import com.jm3.app.miuix.ui.shelf.rememberShelfScaffoldState
import com.jm3.app.miuix.ui.shelf.shelfAlbumId
import com.jm3.app.miuix.ui.shelf.shelfDeleteMessage
import com.jm3.app.miuix.ui.shelf.shelfSummary
import com.jm3.app.miuix.ui.skin.LocalSkin
import com.jm3.core.CoverLocator
import com.jm3.core.download.DownloadAction
import com.jm3.core.download.presentRow
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Pause
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 本地书架：下载进度、暂停/继续/重试、删除与离线阅读入口。 */
@Composable
internal fun ShelfScreen(
    deps: ScreenDeps,
    onBack: () -> Unit,
    nav: AppRoutes,
) {
    val skin = LocalSkin.current
    // 账本订阅、封面域名、主操作分派与删除确认都在共享骨架上；这里只读状态、只画行
    val shelf = rememberShelfScaffoldState(deps)
    val records = shelf.records
    val pendingDeleteRecord = shelf.pendingDeleteRecord
    val imageHost = shelf.imageHost

    GlassScaffold(
        topBar = {
            GlassTopAppBar(
                title = SHELF_TITLE,
                // 透明底色：玻璃底色与模糊由 GlassScaffold 统一绘制
                color = Color.Transparent,
                navigationIcon = { BackButton(onBack = onBack) },
            )
        },
    ) { innerPadding ->
        if (records.isEmpty()) {
            CenteredEmptyState(
                title = "暂无下载",
                hint = "在详情页点下载按钮，章节会出现在这里",
                modifier = Modifier.padding(innerPadding).readableWidth(),
            )
        } else {
            val listState = rememberLazyListState()
            TrackedLazyColumn(
                state = listState,
                // 列表内容滚到玻璃顶栏下方，顶栏才能采样到真实背景
                contentPadding = innerPadding,
                modifier = Modifier.fillMaxSize().readableWidth(),
            ) {
                items(records, key = { it.photoId }) { record ->
                    val albumId = shelfAlbumId(record)
                    // 3:4 封面地址由车号与图片域名纯拼：域名未就绪或车号缺失时为 null，
                    // WorkThumbnail 用同尺寸占位撑住行高，行不会塌陷
                    val coverUrl = remember(albumId, imageHost) {
                        albumId?.let { CoverLocator.of(imageHost).cover(it, "_3x4") }
                    }
                    // 标签、进度条口径与主操作全部由 :core 的 presentRow 判定，本行只做渲染
                    val row = presentRow(record)
                    val label = stringResource(row.label.textRes())
                    BasicComponent(
                        title = record.title.ifBlank { record.photoId },
                        summary = shelfSummary(record, row, label),
                        // 左侧 3:4 缩略封面：与追踪、历史、收藏行同一套尺寸与圆角
                        startAction = {
                            WorkThumbnail(
                                url = coverUrl,
                                albumId = albumId,
                                contentDescription = record.title,
                            )
                        },
                        // 未完成任务用进度条替代纯文本比例，进度一眼可见
                        bottomAction = if (row.showsProgressBar) {
                            {
                                LinearProgressIndicator(
                                    progress = row.donePages.toFloat() / row.total,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                                )
                            }
                        } else {
                            null
                        },
                        onClick = {
                            if (row.action == DownloadAction.Open) nav.reader(record.photoId)
                        },
                        endActions = {
                            // 状态主操作与删除收敛为图标按钮，避免双 TextButton 挤压标题区
                            val actionLabel = stringResource(row.action.contentDescriptionRes())
                            when (row.action) {
                                DownloadAction.Open -> IconButton(
                                    onClick = { nav.reader(record.photoId) },
                                ) {
                                    Icon(MiuixIcons.Play, contentDescription = actionLabel)
                                }
                                DownloadAction.Pause -> IconButton(
                                    onClick = { shelf.performPrimaryAction(record, row) },
                                ) {
                                    Icon(MiuixIcons.Pause, contentDescription = actionLabel)
                                }
                                DownloadAction.Resume, DownloadAction.Retry -> IconButton(
                                    onClick = { shelf.performPrimaryAction(record, row) },
                                ) {
                                    Icon(MiuixIcons.Refresh, contentDescription = actionLabel)
                                }
                            }
                            IconButton(
                                onClick = { shelf.requestDelete(record) },
                            ) {
                                Icon(
                                    MiuixIcons.Delete,
                                    contentDescription = "删除",
                                    tint = MiuixTheme.colorScheme.error,
                                )
                            }
                        },
                    )
                }
            }
        }

        val toDelete = pendingDeleteRecord
        if (toDelete != null) {
            OverlayDialog(
                show = true,
                maxWidth = overlayDialogMaxWidth(),
                title = SHELF_DELETE_TITLE,
                cornerRadius = skin.shapes.dialogCorner,
                onDismissRequest = { shelf.dismissDelete() },
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(
                        text = shelfDeleteMessage(toDelete),
                        style = MiuixTheme.textStyles.body2,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(
                        text = SHELF_DELETE_DISMISS,
                        onClick = { shelf.dismissDelete() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(
                        text = SHELF_DELETE_CONFIRM,
                        colors = ButtonDefaults.textButtonColors(textColor = MiuixTheme.colorScheme.error),
                        onClick = { shelf.confirmDelete() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
