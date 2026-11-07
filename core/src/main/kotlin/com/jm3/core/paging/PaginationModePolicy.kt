package com.jm3.core.paging

import com.jm3.core.settings.PaginationMode

/**
 * 列表分页模式的策略判定：启动读盘取哪个值、模式变了要不要让列表页重载。
 *
 * 纯逻辑，不依赖平台：分页模式是设置项，判定规则只有两条，放在 core 才能被两边复用
 * （:app 启动读盘、列表页观察切换），也不会各写一份漂移。
 */
object PaginationModePolicy {

    /**
     * 启动读盘取哪个值：用户在本进程内显式改过就保留内存值，否则用盘上值。
     *
     * 用户在启动读盘返回前就改了设置时，盘上的旧值会盖掉用户刚选的那个；
     * [userModified] 就是这条判定的依据。
     */
    fun resolveStartupPaginationMode(
        persisted: PaginationMode,
        current: PaginationMode,
        userModified: Boolean,
    ): PaginationMode = if (userModified) current else persisted

    /** 模式是否真的变了：变了列表页要丢掉已加载的页，按新语义从第 1 页重来。 */
    fun paginationModeChanged(previous: PaginationMode, next: PaginationMode): Boolean =
        previous != next
}
