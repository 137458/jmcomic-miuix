package com.jm3.app.miuix.data

import android.content.Context
import com.jm3.app.miuix.data.download.DownloadManager
import com.jm3.app.miuix.data.download.FileDownloadStore
import com.jm3.app.miuix.data.download.GatewayPageFetcher
import com.jm3.app.miuix.data.download.GatewayTaskResolver
import com.jm3.app.miuix.data.download.QueueGuardedDownloadStore
import com.jm3.core.download.DownloadLedger
import com.jm3.core.download.DownloadQueue
import com.jm3.core.download.DownloadStore
import java.io.File
import kotlinx.coroutines.CoroutineScope

/**
 * 下载域：页文件存储、任务队列、落库台账与门面。
 *
 * 页源与任务解析共用同一个网关实例（都读 [ApiDomain] 的当前网关），线路切换后自动跟随；
 * 台账落在 [LibraryDomain] 的库里，文件落在 filesDir/downloads。
 */
class DownloadDomain(
    context: Context,
    private val api: ApiDomain,
    library: LibraryDomain,
    appScope: CoroutineScope,
) {

    /** 下载页文件存储：filesDir/downloads/{photoId}/{index:05}.jpg。 */
    val files: DownloadStore by lazy {
        FileDownloadStore(File(context.applicationContext.filesDir, "downloads"))
    }

    private val pageSource by lazy { GatewayPageFetcher { api.gateway } }

    val manager: DownloadManager by lazy {
        lateinit var queue: DownloadQueue
        val queueStore = QueueGuardedDownloadStore(
            delegate = files,
            isActiveInQueue = { photoId -> photoId in queue.updates.value },
        )
        queue = DownloadQueue(
            fetcher = pageSource,
            store = queueStore,
            resolver = GatewayTaskResolver { api.gateway },
        )
        DownloadManager(
            queue = queue,
            fileStore = files,
            ledger = DownloadLedger(store = library.ledgerStore, pagesJsonOf = pageSource::pagesJsonOrNull),
            appScope = appScope,
            onEvictPages = pageSource::evict,
            onClearPages = pageSource::clearCache,
        )
    }
}
