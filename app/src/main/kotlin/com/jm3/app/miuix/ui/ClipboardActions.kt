package com.jm3.app.miuix.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.SnackbarHostState

/**
 * 把文本写入系统剪贴板并提示一次「复制成功！」。
 *
 * 两套皮肤的相册详情页原本各有一份逐字相同的实现；这里显式接收 context、scope 与
 * snackbarHostState，调用方保留页面级的两参包装，避免五个参数铺到每个 lambda 调用点。
 */
internal fun copyTextToClipboard(
    context: Context,
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    label: String,
    value: String,
) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText(label, value))
    scope.launch { snackbarHostState.showSnackbar("复制成功！") }
}
