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

    /** 列表分页模式：设置页写入后各列表页立即生效（跨 tab 无需重建）。 */
    var paginationMode by mutableStateOf(PaginationMode.INFINITE)
}
