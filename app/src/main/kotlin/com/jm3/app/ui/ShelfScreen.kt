package com.jm3.app.ui

import androidx.compose.foundation.layout.fillMaxSize
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
import com.jm3.app.Jm3Graph
import com.jm3.core.download.DownloadRecord
import com.jm3.core.download.DownloadStatus
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar

/** 本地书架：下载进度、暂停/继续/重试、删除与离线阅读入口。 */
@Composable
fun ShelfScreen(onBack: () -> Unit, onOpenReader: (String) -> Unit) {
    var records by remember { mutableStateOf<List<DownloadRecord>>(emptyList()) }

    // 初次加载 + 队列进度驱动刷新
    LaunchedEffect(Unit) {
        Jm3Graph.downloadManager.ensureStarted()
        records = Jm3Graph.downloadManager.records()
        Jm3Graph.downloadManager.queue.updates.collect {
            records = Jm3Graph.downloadManager.records()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "本地书架",
                navigationIcon = { BackButton(onBack = onBack) },
            )
        },
    ) { innerPadding ->
        if (records.isEmpty()) {
            Text(
                text = "暂无下载，去详情页添加任务吧",
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = innerPadding,
            ) {
                items(records, key = { it.photoId }) { record ->
                    BasicComponent(
                        title = record.title.ifBlank { record.photoId },
                        summary = shelfSummary(record),
                        onClick = {
                            if (record.status == DownloadStatus.DONE) onOpenReader(record.photoId)
                        },
                        endActions = {
                            when (record.status) {
                                DownloadStatus.DONE -> TextButton(
                                    text = "阅读",
                                    onClick = { onOpenReader(record.photoId) },
                                )
                                DownloadStatus.RUNNING, DownloadStatus.QUEUED -> TextButton(
                                    text = "暂停",
                                    onClick = { Jm3Graph.downloadManager.pause(record.photoId) },
                                )
                                DownloadStatus.PAUSED -> TextButton(
                                    text = "继续",
                                    onClick = { Jm3Graph.downloadManager.resume(record.photoId) },
                                )
                                DownloadStatus.FAILED -> TextButton(
                                    text = "重试",
                                    onClick = {
                                        Jm3Graph.downloadManager.enqueueChapter(
                                            photoId = record.photoId,
                                            albumId = record.albumId,
                                            title = record.title,
                                        )
                                    },
                                )
                            }
                            TextButton(
                                text = "删除",
                                onClick = {
                                    Jm3Graph.downloadManager.remove(record.photoId)
                                    records = records.filterNot { it.photoId == record.photoId }
                                },
                            )
                        },
                    )
                }
            }
        }
    }
}

private fun shelfSummary(record: DownloadRecord): String = when (record.status) {
    DownloadStatus.DONE ->
        "已完成 " + record.donePages + "/" + record.total
    DownloadStatus.FAILED ->
        "失败 " + record.donePages + "/" + record.total
    else ->
        shelfStatusLabel(record.status) + " " + record.donePages + "/" + record.total
}

private fun shelfStatusLabel(status: DownloadStatus): String = when (status) {
    DownloadStatus.QUEUED -> "排队中"
    DownloadStatus.RUNNING -> "下载中"
    DownloadStatus.PAUSED -> "已暂停"
    DownloadStatus.DONE -> "已完成"
    DownloadStatus.FAILED -> "失败"
}
