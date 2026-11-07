package com.jm3.app.miuix.ui

import com.jm3.core.settings.PaginationMode

internal data class PaginationModeSelection(val mode: PaginationMode, val changed: Boolean)

/** 下拉索引映射到全局模式；越界索引忽略，同值选择不重复落盘。 */
internal fun resolvePaginationSelection(current: PaginationMode, selectedIndex: Int): PaginationModeSelection? {
    val mode = PaginationMode.entries.getOrNull(selectedIndex) ?: return null
    return PaginationModeSelection(mode, changed = mode != current)
}
