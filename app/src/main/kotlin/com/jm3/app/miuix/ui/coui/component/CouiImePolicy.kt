package com.jm3.app.miuix.ui.coui.component

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalWindowInfo

/**
 * 软键盘是否真的在占用本页视口。
 *
 * 系统会给每个窗口上报 IME 尺寸，弹窗持有输入焦点时宿主窗口同样能读到一个非零的 IME inset；
 * 直接拿它收缩页面，会让宿主在输入弹窗升起前就空出整块区域，露出窗口底色。
 * 库的对话框文档把这条约束点名给宿主：只有宿主窗口自己持有焦点时，才算键盘占用了本页。
 */
internal fun couiImeOccupiesPage(imeVisible: Boolean, windowFocused: Boolean): Boolean =
    imeVisible && windowFocused

/** 组合期读取的 [couiImeOccupiesPage]：焦点与 IME 尺寸变化都会触发重组。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun rememberCouiImeOccupiesPage(): Boolean = couiImeOccupiesPage(
    imeVisible = WindowInsets.isImeVisible,
    windowFocused = LocalWindowInfo.current.isWindowFocused,
)
