package com.jm3.app.miuix

import com.jm3.app.miuix.data.ApiDomain
import com.jm3.app.miuix.data.DownloadDomain
import com.jm3.app.miuix.data.LibraryDomain
import com.jm3.app.miuix.data.SessionDomain
import com.jm3.app.miuix.data.SettingsDomain
import com.jm3.app.miuix.data.UpdateDomain
import com.jm3.app.miuix.data.download.DownloadManager
import com.jm3.app.miuix.ui.SessionCache
import com.jm3.app.miuix.update.UpdateDownloader
import com.jm3.core.Jm3Gateway
import com.jm3.core.download.DownloadStore
import com.jm3.core.history.HistoryRepository
import com.jm3.core.session.AccountManager
import com.jm3.core.session.AuthCall
import com.jm3.core.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope

/**
 * 屏幕依赖：由组装根一次性注入，屏幕内部不再直读 [Jm3Graph]。
 *
 * 只持有各域实例与进程级资源，全部成员按域转发，不在这里写业务判定或编排；
 * 这样屏幕签名只需一个 deps，新增依赖只改容器对应的一行。
 */
class ScreenDeps internal constructor(
    val api: ApiDomain,
    val preferences: SettingsDomain,
    val session: SessionDomain,
    val library: LibraryDomain,
    val downloads: DownloadDomain,
    val updates: UpdateDomain,
    sessionCache: SessionCache,
    val appScope: CoroutineScope,
) {
    internal val sessionCache: SessionCache = sessionCache

    val gateway: Jm3Gateway
        get() = api.gateway

    val settings: SettingsRepository
        get() = preferences.repository

    val account: AccountManager
        get() = session.account

    val authCall: AuthCall
        get() = session.authCall

    val history: HistoryRepository
        get() = library.history

    val downloadFiles: DownloadStore
        get() = downloads.files

    val downloadManager: DownloadManager
        get() = downloads.manager

    val updateDownloader: UpdateDownloader
        get() = updates.downloader

    suspend fun applyDomains(apiDomains: List<String>) = session.applyDomains(apiDomains)
}
