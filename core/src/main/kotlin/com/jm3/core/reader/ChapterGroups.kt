package com.jm3.core.reader

/**
 * 目录里的一个章节组：身份、展示顺序与它在原始目录中的起始位置。
 *
 * @param id 稳定身份。取组内首章（原始目录顺序）的 key——组身份必须能直接做 LazyColumn 的 key
 *   与展开态的集合成员，所以只能是单值；用"组内首章"而不是"引用组内任意章节"，
 *   是因为它同时编码了"这一组从哪一章开始"，反查所属组时无需再扫一遍成员。
 * @param items 组内章节，已按展示方向排列（[ChapterGroups.of] 的 descending 为真时最新在前）。
 * @param startPosition 组内首章在原始目录中的下标。与展示方向无关，供章节序号一类换算使用。
 */
data class ChapterGroup<T>(
    val id: String,
    val items: List<T>,
    val startPosition: Int,
)

/**
 * 章节分组的唯一实现：相册章节、小说目录共用同一套边界与身份规则。
 *
 * 约定输入是接口下发的原始目录顺序（旧到新），分组边界只在这套顺序上确定：
 * 每 [SIZE] 章一组、末组不补齐。展示方向（[of] 的 descending）只反转组序列与组内条目，
 * 不重新切分——否则同一个十章分组会随排序在两套分区之间跳变，展开态随之落到别的组。
 *
 * 纯函数、无 Android 依赖，可在 :core 内直接测。
 */
object ChapterGroups {

    /** 每组章节数。目录十话一卡，相册与小说同一节奏。 */
    const val SIZE: Int = 10

    /**
     * 按原始目录顺序切组。
     *
     * @param descending 展示时最新在前：组序列与组内条目一起反转，[ChapterGroup.id] 不变。
     */
    fun <T> of(items: List<T>, keyOf: (T) -> String, descending: Boolean = false): List<ChapterGroup<T>> {
        if (items.isEmpty()) return emptyList()
        val groups = ArrayList<ChapterGroup<T>>((items.size + SIZE - 1) / SIZE)
        var position = 0
        while (position < items.size) {
            val end = minOf(position + SIZE, items.size)
            val members = items.subList(position, end).toList()
            groups += ChapterGroup(
                id = keyOf(members.first()),
                items = if (descending) members.reversed() else members,
                startPosition = position,
            )
            position = end
        }
        return if (descending) groups.reversed() else groups
    }

    /** 章节 [key] 所属组的身份；不在目录里时返回 null。 */
    fun <T> groupIdOf(items: List<T>, keyOf: (T) -> String, key: String): String? {
        val position = items.indexOfFirst { keyOf(it) == key }
        if (position < 0) return null
        return keyOf(items[position / SIZE * SIZE])
    }
}
