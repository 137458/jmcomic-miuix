package com.jm3.core.reader

import com.jm3.core.settings.ReaderProgressSettings

/** 阅读进度：当前页与该章节的总页数。 */
data class ReaderPageProgress(val page: Int, val total: Int)

/** 进度编码为 "page:total"。 */
fun encodeReaderProgress(progress: ReaderPageProgress): String =
    progress.page.toString() + ":" + progress.total.toString()

/**
 * 进度解码。
 *
 * 任何非法内容（空白、缺分隔符、非数字、页码或总页数小于 1）都当作没有进度，
 * 避免损坏的旧数据把用户恢复到某一页。
 */
fun decodeReaderProgress(raw: String?): ReaderPageProgress? {
    val text = raw?.trim().orEmpty()
    if (text.isEmpty()) return null
    val parts = text.split(":")
    if (parts.size != 2) return null
    val page = parts[0].trim().toIntOrNull() ?: return null
    val total = parts[1].trim().toIntOrNull() ?: return null
    if (page < 1 || total < 1) return null
    return ReaderPageProgress(page = page, total = total)
}

/**
 * 恢复页码判定。
 *
 * 没有历史进度、当前章节没有图片（总页数为 0）、历史页码或历史总页数非法时都不恢复；
 * 历史页码超出当前总页数时夹到最后一页 —— 本地下载页数与网络不一致时按实际总页数夹取。
 */
fun resolveRestoredPage(saved: ReaderPageProgress?, totalPages: Int): Int? {
    if (saved == null) return null
    if (totalPages <= 0) return null
    if (saved.page < 1) return null
    if (saved.total < 1) return null
    return if (saved.page > totalPages) totalPages else saved.page
}

/**
 * 首载页码：详情页预览点第 i 张时优先直达该页，起始页缺失或越界才回退历史进度。
 */
fun resolveInitialPage(startPage: Int?, saved: ReaderPageProgress?, totalPages: Int): Int? {
    if (totalPages <= 0) return null
    if (startPage != null && startPage in 1..totalPages) return startPage
    return resolveRestoredPage(saved, totalPages)
}

/**
 * 离开阅读器时补记的进度。
 *
 * 进度恢复流程完成前不写：此时 [page] 还停在首帧的第 1 页，写盘会把尚未读取的旧进度覆盖掉。
 * 页码按 [total] 夹取，避免长图列表末尾的留白项把页码推到越界值。
 */
fun readerProgressOnExit(restored: Boolean, page: Int, total: Int): ReaderPageProgress? {
    if (!restored || total <= 0 || page < 1) return null
    return ReaderPageProgress(page = page.coerceAtMost(total), total = total)
}

/**
 * 是否该清掉进度键。
 *
 * 读到末页（[ReaderPageProgress.page] 达到 [ReaderPageProgress.total]）说明本章已读完，
 * 自然退出时改为清键，否则每读一本书就会永久多留一个进度键；
 * 总页数无效（0 或负数）时没有「末页」概念，按未读完处理，保留原进度。
 */
fun shouldClearReaderProgress(progress: ReaderPageProgress): Boolean =
    progress.total > 0 && progress.page >= progress.total

/**
 * 记进度：读完末页改为清键，其余写键。
 *
 * 收在这里而不是留在 UI 编排里，是让「末页清键」这一条规则只有一处实现，
 * 两套皮肤与阅读器的多个退出口都走同一个判定。
 */
suspend fun persistReaderProgress(
    settings: ReaderProgressSettings,
    photoId: String,
    progress: ReaderPageProgress,
) {
    if (shouldClearReaderProgress(progress)) {
        settings.clearReaderLastPage(photoId)
    } else {
        settings.setReaderLastPage(photoId, progress)
    }
}
