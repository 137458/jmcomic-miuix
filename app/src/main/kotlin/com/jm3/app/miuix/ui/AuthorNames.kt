package com.jm3.app.miuix.ui

/**
 * 作者列表的展示口径：分隔符与空值兜底只在这里定义。
 *
 * 之前各屏各写一遍，还出现了「空列表给 null」与「空列表给未知作者」两种兜底；
 * 需要占位文案的调用方传 [fallback]，不需要副标题的传 null。
 */
internal fun List<String>.authorNames(fallback: String? = null): String? =
    map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString(" / ")
        .ifBlank { fallback }
