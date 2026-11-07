package com.jm3.app.miuix.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jm3.core.settings.PaginationMode
import com.jm3.core.settings.SettingsRepository

/**
 * 设置域：设置仓库与跨路由立即生效的界面态。
 *
 * 仓库的具体类型 [SettingsStore] 在本模块内可见：凭据密文与设置同库（jm3_settings），
 * 会话域要拿它当 CredentialBlobStore，所以不能让两条 seam 各自 new 一个 SharedPreferences 封装。
 */
class SettingsDomain(context: Context) {

    internal val store: SettingsStore = SettingsStore(context.applicationContext)

    val repository: SettingsRepository
        get() = store

    /** 分页模式的当前值：列表页经屏幕依赖容器读它。 */
    private var paginationModeState by mutableStateOf(PaginationMode.INFINITE)

    /**
     * 用户是否在本进程内显式改过分页模式。
     *
     * 启动读盘据此决定取盘上值还是内存值：用户在读盘返回前就改了设置时，
     * 盘上的旧值不该盖掉用户刚选的那个。
     */
    internal var paginationModeUserModified: Boolean = false
        private set

    /**
     * 列表分页模式：设置页写入后各列表页立即生效（跨 tab 无需重建）。
     *
     * 走这个属性就代表用户（或转接层）显式选了一次，所以同时把 [paginationModeUserModified] 置位；
     * 启动读盘落值不走这里，改用 [applyStartupPaginationMode]。
     */
    var paginationMode: PaginationMode
        get() = paginationModeState
        set(value) {
            paginationModeState = value
            paginationModeUserModified = true
        }

    /**
     * 启动读盘落值：写盘上值或启动时判出来的最终值。
     *
     * 这条路不是用户此刻的显式选择，所以不标记为用户修改（否则下次读盘会误以为用户改过）。
     * 调用方要先用 [com.jm3.core.paging.PaginationModePolicy.resolveStartupPaginationMode] 判好
     * 该用盘上值还是内存值。
     */
    internal fun applyStartupPaginationMode(mode: PaginationMode) {
        paginationModeState = mode
    }
}
