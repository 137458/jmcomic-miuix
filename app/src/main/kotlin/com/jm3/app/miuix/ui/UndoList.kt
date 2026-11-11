package com.jm3.app.miuix.ui

/** 从列表中按条件摘除元素的结果，保留摘除前的首个下标供撤销时回插原位。 */
internal data class RemovedSlice<T>(
    val removed: List<T>,
    val remaining: List<T>,
    val insertIndex: Int,
)

internal fun <T> List<T>.removeMatching(predicate: (T) -> Boolean): RemovedSlice<T> {
    val firstIndex = indexOfFirst(predicate)
    if (firstIndex < 0) {
        return RemovedSlice(removed = emptyList(), remaining = this, insertIndex = 0)
    }
    val (removed, remaining) = partition(predicate)
    return RemovedSlice(removed = removed, remaining = remaining, insertIndex = firstIndex)
}

internal fun <T, K> List<T>.restoreAt(
    items: List<T>,
    insertIndex: Int,
    keySelector: (T) -> K,
): List<T> {
    if (items.isEmpty()) return this
    val uniqueItems = items.distinctBy(keySelector)
    val restoredKeys = uniqueItems.mapTo(HashSet(uniqueItems.size), keySelector)
    val base = filterNot { keySelector(it) in restoredKeys }
    val clampedIndex = insertIndex.coerceIn(0, base.size)
    return buildList(base.size + uniqueItems.size) {
        addAll(base.subList(0, clampedIndex))
        addAll(uniqueItems)
        addAll(base.subList(clampedIndex, base.size))
    }
}
