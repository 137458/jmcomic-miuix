package com.jm3.app.miuix

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.jm3.app.miuix.data.download.DownloadService
import com.jm3.app.miuix.ui.ReaderVolumeKeys
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /** 通知权限结果不影响下载：拒绝后服务照常运行，只是没有可见进度通知。 */
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* 结果无需处理 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Jm3Graph.init(applicationContext)
        // 进程级注册：此后「是否有活动下载任务」自动决定前台服务的启停，与界面生命周期无关。
        DownloadService.attach(applicationContext)
        requestNotificationPermissionOnce()
        setContent {
            Jm3App()
        }
    }

    /**
     * Android 13+ 申请 POST_NOTIFICATIONS。
     *
     * 只在首次进入时询问一次（标记与其它设置项一样存 DataStore）：系统弹窗被拒后不再骚扰，
     * 用户可在系统设置里补开，前台服务与下载本身都不依赖该权限。
     */
    private fun requestNotificationPermissionOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) return
        // lifecycleScope 默认在主线程派发：读标记是挂起调用不阻塞界面，弹窗 launch 也必须在主线程。
        lifecycleScope.launch {
            if (Jm3Graph.settings.notificationPermissionAsked()) return@launch
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            // 先弹窗再写标记：弹窗期间进程被杀时标记不落地，下次进入还会询问；
            // 反之先写标记会让用户永远失去补授权的一次引导。
            Jm3Graph.settings.markNotificationPermissionAsked()
        }
    }

    /** 阅读器挂载时把音量键交给翻页，其余场景保持系统音量行为。 */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (ReaderVolumeKeys.dispatch(keyCode)) return true
        return super.onKeyDown(keyCode, event)
    }
}
