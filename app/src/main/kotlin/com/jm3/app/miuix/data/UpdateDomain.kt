package com.jm3.app.miuix.data

import android.content.Context
import com.jm3.app.miuix.update.ApkInstaller
import com.jm3.app.miuix.update.UpdateDownloader
import java.io.File
import kotlinx.coroutines.CoroutineScope

/**
 * 更新域：应用内更新的下载器与安装动作，一趟更新的两步都在这里。
 *
 * 下载器状态跟随进程（协程跑在进程域上，离开更新页不中断），安装是它的下一步而不是独立能力；
 * 两者一起出口之后，调用方不必知道安装要的是 application context、也不必自己持有下载器实例。
 */
class UpdateDomain(context: Context, appScope: CoroutineScope) {

    private val appContext: Context = context.applicationContext

    /** 更新下载器：同一进程只允许一个实例，否则两个入口各自维护进度。 */
    val downloader: UpdateDownloader by lazy { UpdateDownloader(appScope) }

    /**
     * 安装已下载的包，结果由调用方决定提示文案。
     *
     * 内部会拉起系统界面，必须在主线程调用；失败原因走返回值，不抛异常。
     */
    fun install(file: File): ApkInstaller.Result = ApkInstaller.install(appContext, file)
}
