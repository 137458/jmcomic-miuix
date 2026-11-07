package com.jm3.app.miuix.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.data.download.contentDescriptionRes
import com.jm3.app.miuix.data.download.textRes
import com.jm3.app.miuix.ui.glass.GlassScaffold
import com.jm3.app.miuix.ui.glass.GlassTopAppBar
import com.jm3.core.CoverLocator
import com.jm3.core.Jm3Gateway
import com.jm3.core.download.DownloadAction
import com.jm3.core.download.DownloadRecord
import com.jm3.core.download.DownloadRow
import com.jm3.core.download.presentRow
import com.jm3.core.session.AuthCall
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Pause
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 本地书架：下载进度、暂停/继续/重试、删除与离线阅读入口。 */
@Composable
fun ShelfScreen(
    deps: ScreenDeps,
    gateway: Jm3Gateway,
    authCall: AuthCall,
    onBack: () -> Unit,
    onOpenReader: (String) -> Unit,
) {
    var records by remember { mutableStateOf<List<DownloadRecord>>(emptyList()) }
    var imageHost by remember { mutableStateOf("") }

    // 初次加载 + 队列进度驱动刷新
    LaunchedEffect(Unit) {
        deps.downloadManager.ensureStarted()
        records = deps.downloadManager.records()
        deps.downloadManager.queue.updates.collect {
            records = deps.downloadManager.records()
        }
    }

    // 图片域名整个屏幕只取一次：网关取域名是阻塞方法，AuthCall 负责等待会话恢复并切到 IO；
    // 取不到或会话失效时留空串，封面由 CoverLocator.cover 返回 null 退化为占位，不影响行内容与按钮
    LaunchedEffect(gateway, authCall) {
        imageHost = authCall.call { gateway.imageHost() }.orDegrade("")
    }

    GlassScaffold(
        topBar = {
            GlassTopAppBar(
                title = "本地书架",
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
                modifier = Modifier.padding(innerPadding),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                // 列表内容滚到玻璃顶栏下方，顶栏才能采样到真实背景
                contentPadding = innerPadding,
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
                            if (row.action == DownloadAction.Open) onOpenReader(record.photoId)
                        },
                        endActions = {
                            // 状态主操作与删除收敛为图标按钮，避免双 TextButton 挤压标题区
                            val actionLabel = stringResource(row.action.contentDescriptionRes())
                            when (row.action) {
                                DownloadAction.Open -> IconButton(
                                    onClick = { onOpenReader(record.photoId) },
                                ) {
                                    Icon(MiuixIcons.Play, contentDescription = actionLabel)
                                }
                                DownloadAction.Pause -> IconButton(
                                    onClick = { deps.downloadManager.pause(record.photoId) },
                                ) {
                                    Icon(MiuixIcons.Pause, contentDescription = actionLabel)
                                }
                                DownloadAction.Resume, DownloadAction.Retry -> IconButton(
                                    onClick = { deps.downloadManager.resumeRecord(record) },
                                ) {
                                    Icon(MiuixIcons.Refresh, contentDescription = actionLabel)
                                }
                            }
                            IconButton(
                                onClick = {
                                    deps.downloadManager.remove(record.photoId)
                                    records = records.filterNot { it.photoId == record.photoId }
                                },
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
    }
}

/** 行摘要：状态与进度，前面带禁漫车号；车号缺失时只显示状态，不输出孤立的 “#”。 */
private fun shelfSummary(record: DownloadRecord, row: DownloadRow, label: String): String {
    val progress = label + " " + row.donePages + "/" + row.total
    val albumId = shelfAlbumId(record) ?: return progress
    return "#" + albumId + " · " + progress
}

/** 车号口径与详情页一致（AlbumDetailScreen 把空 id 与 “-1” 都视为无车号），避免拼出无效封面地址。 */
private fun shelfAlbumId(record: DownloadRecord): String? =
    record.albumId.trim().takeIf { it.isNotEmpty() && it != "-1" }
