package com.jm3.app.miuix.ui.coui.page

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.jm3.app.miuix.ui.ReaderWindowLeases
import com.jm3.core.settings.ReaderPreferences

/** 从 Compose 的 Context 链上找宿主 Activity，窗口亮度与常亮标记都要打在它上面。 */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** 绑定阅读器窗口级亮度与常亮租约：由宿主窗口消费，按引用计数安全释放。 */
@Composable
internal fun BindCouiReaderWindowEffects(
    keepScreenOn: Boolean,
    readerBrightness: Int,
) {
    val activityContext = LocalContext.current
    val activity = remember(activityContext) { activityContext.findActivity() }
    val readerWindow = activity?.window
    DisposableEffect(readerWindow) {
        ReaderWindowLeases.enterBrightness(readerWindow?.attributes?.screenBrightness)
        onDispose {
            val restore = ReaderWindowLeases.originalBrightness
            if (ReaderWindowLeases.exitBrightness() && readerWindow != null && restore != null) {
                readerWindow.attributes = readerWindow.attributes.apply {
                    screenBrightness = restore
                }
            }
        }
    }
    LaunchedEffect(readerWindow, readerBrightness) {
        if (readerWindow == null) return@LaunchedEffect
        readerWindow.attributes = readerWindow.attributes.apply {
            screenBrightness = if (readerBrightness == ReaderPreferences.FOLLOW_SYSTEM_BRIGHTNESS) {
                WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            } else {
                readerBrightness / 100f
            }
        }
    }
    DisposableEffect(readerWindow, keepScreenOn) {
        if (readerWindow == null || !keepScreenOn) return@DisposableEffect onDispose {}
        ReaderWindowLeases.enterKeepScreenOn()
        readerWindow.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            if (ReaderWindowLeases.exitKeepScreenOn()) {
                readerWindow.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }
}
