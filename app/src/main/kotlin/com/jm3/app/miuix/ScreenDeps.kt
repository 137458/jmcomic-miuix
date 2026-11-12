package com.jm3.app.miuix

import com.jm3.app.miuix.data.ApiDomain
import com.jm3.app.miuix.data.DownloadDomain
import com.jm3.app.miuix.data.LibraryDomain
import com.jm3.app.miuix.data.SessionDomain
import com.jm3.app.miuix.data.SettingsDomain
import com.jm3.app.miuix.data.UpdateDomain
import com.jm3.app.miuix.data.download.DownloadManager
import com.jm3.app.miuix.ui.SessionCache
import com.jm3.app.miuix.update.ApkInstaller
import com.jm3.app.miuix.update.UpdateDownloader
import com.jm3.core.DomainRoutes
import com.jm3.core.Jm3Gateway
import com.jm3.core.Jm3Log
import com.jm3.core.download.DownloadStore
import com.jm3.core.history.HistoryRepository
import com.jm3.core.runCatchingSuspending
import com.jm3.core.session.AccountManager
import com.jm3.core.session.AuthCall
import com.jm3.core.session.UnreadBadge
import com.jm3.core.settings.BottomBarForm
import com.jm3.core.settings.BottomBarSize
import com.jm3.core.settings.PaginationMode
import com.jm3.core.settings.SettingsRepository
import com.jm3.core.settings.WideScreenNavigation
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 列表页读取的分页开关。
 *
 * 只暴露读面：写入要连着作废分页缓存与落盘，走 [ScreenDeps.selectPaginationMode]，
 * 免得每个设置页各抄一遍写序。
 */
internal interface PagingPreference {
    val paginationMode: PaginationMode
}

/**
 * 屏幕读取的底栏外观选项。
 *
 * null 表示用户没有显式设置过，由窗口宽度断点在组合期解析默认值；
 * 写入同分页开关，统一走 [ScreenDeps.selectBottomBarForm] 一类的入口。
 */
internal interface BottomBarPreference {
    val bottomBarSize: BottomBarSize?
    val bottomBarForm: BottomBarForm?
    val wideScreenNavigation: WideScreenNavigation?
}

/**
 * 屏幕依赖：由组装根一次性注入，屏幕内部不再直读 [Jm3Graph]。
 *
 * 只持有各域实例与进程级资源，全部成员按域转发，不在这里写业务判定或编排；
 * 这样屏幕签名只需一个 deps，新增依赖只改容器对应的一行。
 *
 * 域对象本身不出现在屏幕可见面上：屏幕要读的界面态经 [PagingPreference]、
 * [BottomBarPreference] 这类窄接口给出，要写的走本类的一次性动作，
 * 两条路径都不需要屏幕知道设置域长什么样。
 */
class ScreenDeps internal constructor(
    internal val api: ApiDomain,
    internal val preferences: SettingsDomain,
    internal val session: SessionDomain,
    internal val library: LibraryDomain,
    internal val downloads: DownloadDomain,
    internal val updates: UpdateDomain,
    sessionCache: SessionCache,
    val appScope: CoroutineScope,
) : PagingPreference, BottomBarPreference {

    internal val sessionCache: SessionCache = sessionCache

    val gateway: Jm3Gateway
        get() = api.gateway

    /** 线路可观测面：只想知道「现在走哪条线路、哪条快」的屏幕用这个面，不必认识内容方法。 */
    val routes: DomainRoutes
        get() = gateway

    val settings: SettingsRepository
        get() = preferences.repository

    val account: AccountManager
        get() = session.account

    /** 信箱未读数：底栏角标与「我的」页入口共用同一个刷新载体。 */
    val unread: UnreadBadge
        get() = session.unread

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

    /** 本次进程内已经弹过更新窗的版本号；只在内存里，冷启动会清空。 */
    var promptedVersion: String?
        get() = updates.promptedVersion
        set(value) {
            updates.promptedVersion = value
        }

    /** 认领进程内唯一的启动期自动更新检查名额，仅首次返回 true。 */
    fun claimStartupUpdateCheck(): Boolean = updates.claimStartupUpdateCheck()

    /** 安装已下载的包；结果由调用方决定提示文案。 */
    fun installUpdate(file: File): ApkInstaller.Result = updates.install(file)

    /** 分页开关的窄读面：列表页只依赖这两个成员。 */
    internal val paging: PagingPreference
        get() = this

    /** 底栏外观的窄读面：底栏与设置页只依赖这三个成员。 */
    internal val bottomBar: BottomBarPreference
        get() = this

    override val paginationMode: PaginationMode
        get() = preferences.paginationMode

    override val bottomBarSize: BottomBarSize?
        get() = preferences.bottomBarSize

    override val bottomBarForm: BottomBarForm?
        get() = preferences.bottomBarForm

    override val wideScreenNavigation: WideScreenNavigation?
        get() = preferences.wideScreenNavigation

    /**
     * 用户改分页模式：内存态立即生效（各列表页跨 tab 立刻跟着变），旧模式的分页缓存作废。
     *
     * 写盘挂在进程作用域而非页面作用域：用户点选后常立刻返回上一页，
     * 随页面销毁取消的协程会让内存态已改而盘上没落。
     */
    fun selectPaginationMode(mode: PaginationMode) {
        if (mode == preferences.paginationMode) return
        preferences.paginationMode = mode
        // 列表页此时已被设置路由移出组合；作废旧模式的缓存，回来时才会按新语义从第 1 页加载。
        sessionCache.forgetPages()
        persist("分页模式") { settings.setPaginationMode(mode) }
    }

    /**
     * 用户选底栏尺寸：「普通」表示自适应，清空显式值回到断点解析；
     * 只有「偏大」是需要落盘的强制选择。
     */
    fun selectBottomBarSize(size: BottomBarSize) {
        val target = if (size == BottomBarSize.GENEROUS) BottomBarSize.GENEROUS else null
        if (target == preferences.bottomBarSize) return
        preferences.bottomBarSize = target
        persist("底栏尺寸") {
            if (target == null) settings.clearBottomBarSize() else settings.setBottomBarSize(target)
        }
    }

    fun selectBottomBarForm(form: BottomBarForm) {
        if (form == preferences.bottomBarForm) return
        preferences.bottomBarForm = form
        persist("底栏形态") { settings.setBottomBarForm(form) }
    }

    fun selectWideScreenNavigation(navigation: WideScreenNavigation) {
        if (navigation == preferences.wideScreenNavigation) return
        preferences.wideScreenNavigation = navigation
        persist("宽屏导航") { settings.setWideScreenNavigation(navigation) }
    }

    suspend fun applyDomains(apiDomains: List<String>) = session.applyDomains(apiDomains)

    private fun persist(label: String, write: suspend () -> Unit) {
        appScope.launch {
            runCatchingSuspending { write() }.onFailure { error ->
                Jm3Log.warn("Settings", "保存设置「$label」失败：${error.javaClass.simpleName}", error)
            }
        }
    }
}
