package com.jm3.app.miuix.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.SnackbarHostState

/** 写入成功后的提示文案。 */
internal const val CLIPBOARD_COPY_SUCCESS = "复制成功！"

/** 拿不到系统剪贴板时的提示文案：没写进去就必须说失败，不能冒充成功。 */
internal const val CLIPBOARD_COPY_FAILED = "复制失败，请重试"

/** 写入结果翻成提示文案的唯一分叉点；[successMessage] 让调用方保留自己的成功文案。 */
internal fun clipboardCopyMessage(
    copied: Boolean,
    successMessage: String = CLIPBOARD_COPY_SUCCESS,
): String = if (copied) successMessage else CLIPBOARD_COPY_FAILED

/**
 * 把文本写入系统剪贴板，并按写入结果提示一次。
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
    successMessage: String = CLIPBOARD_COPY_SUCCESS,
) {
    val copied = writeClipboardText(context, label, value)
    scope.launch {
        snackbarHostState.showSnackbar(clipboardCopyMessage(copied, successMessage))
    }
}

/** 系统剪贴板不可用时返回 false，调用方据此报失败。 */
private fun writeClipboardText(context: Context, label: String, value: String): Boolean {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        ?: return false
    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
    return true
}
