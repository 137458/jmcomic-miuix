package com.jm3.core.download

/**
 * 页号与页列表下标的唯一换算点。
 *
 * - 队列/落盘页号（[pageNo]）从 1 起：DownloadTask.index 为已完成页数游标，页号 = 游标 + 1，
 *   DownloadStore 实现按 1 起页号落盘（如 %05d.jpg）。
 * - 页列表下标（[imageIndex]）从 0 起：Mappers.toPhotoPages 以 mapIndexed 赋值 PageImage.index。
 *
 * 两侧契约不同，调用点不得自行加减，统一经本对象换算，避免错位一页或末页越界。
 */
object PageNumbers {

    /** 1 起页号 -> 0 起页列表下标。 */
    fun imageIndex(pageNo: Int): Int = pageNo - 1

    /** 0 起页列表下标 -> 1 起页号。 */
    fun pageNo(imageIndex: Int): Int = imageIndex + 1
}
