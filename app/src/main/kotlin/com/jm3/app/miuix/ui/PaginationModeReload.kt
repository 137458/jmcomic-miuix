package com.jm3.app.miuix.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.jm3.core.paging.pagingModeReloadPage
import com.jm3.core.settings.PaginationMode

/**
 * 分页模式切换后按新形态重载第一页。
 *
 * 首帧只记录当前模式，不重复触发首屏加载；只有模式真的变化时才回调，
 * 判定复用 [pagingModeReloadPage]，避免同一份已追加内容混用两种翻页语义。
 */
@Composable
internal fun PaginationModeReloadEffect(mode: PaginationMode, onReload: (page: Int) -> Unit) {
    val reload by rememberUpdatedState(onReload)
    var previous by remember { mutableStateOf(mode) }
    LaunchedEffect(mode) {
        val target = pagingModeReloadPage(previous, mode)
        previous = mode
        if (target != null) reload(target)
    }
}
