package com.jm3.app.miuix.ui

import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * 长图模式的页面比例与解码尺寸计算。
 *
 * 竖屏瀑布流按图片真实宽高比排布（ReaderScreen 的 dynamicAspectRatio 路径），但 Coil 的 FIT
 * 只负责把原图等比缩进请求尺寸里，请求尺寸本身必须跟随真实比例：写死 3:4 时 800x20000 的
 * 长条图会被解成 58x1440，再拉回整宽时放大约十九倍，肉眼就是糊的。
 *
 * 真实比例只能等首帧解码完成才知道（PageImage 没有宽高字段，Coil 也没有"只读尺寸"的接口），
 * 所以这里的规则必须能表达"比例还没拿到"这一态，并且给出有内存上界的兜底尺寸。
 */

/** 比例未知时的占位与兜底宽高比（宽/高）：3:4 竖版页，与旧逻辑的占位一致。 */
internal const val DEFAULT_PAGE_ASPECT_RATIO = 3f / 4f

/**
 * 单页解码的像素预算：4.0 MP，ARGB_8888 约 16 MB。
 * 跟随真实比例后超长条幅的高度会失控（1080 宽、1:25 的条幅约 27000 px，原图直解要 116 MB），
 * 这里按面积等比收敛，宁可牺牲长条图的细节也不让单页位图突破 16 MB。
 */
internal const val MAX_PAGE_DECODE_PIXELS = 4_000_000

/** 解码位图的单边上限：8192 已覆盖 4K 屏整页需求，再大在部分解码器与纹理上传上不安全。 */
internal const val MAX_PAGE_DECODE_EDGE_PX = 8_192

/**
 * 用解码后的像素尺寸算宽高比（宽/高）。
 *
 * 宽或高为 0、负数、NaN、Infinity 时返回 null：这些值如果写进页框比例，会让
 * Modifier.aspectRatio 抛异常或把页框塌成 0，此时保留上一次已知比例更安全。
 */
internal fun pageAspectRatio(widthPx: Float, heightPx: Float): Float? {
    if (widthPx <= 0f || heightPx <= 0f) return null
    return (widthPx / heightPx).takeIf { it.isUsableAspect() }
}

/**
 * 算一页的解码尺寸（请求侧按 FIT 等比缩进去，Coil 自己决定采样率）。
 *
 * - 比例未知或非法：退回 3:4 兜底盒，代价最低，真实比例等首帧解出来再收敛。
 * - 比例已知：宽铺满页宽、高按真实比例，再按 [MAX_PAGE_DECODE_PIXELS] 与
 *   [MAX_PAGE_DECODE_EDGE_PX] 等比收敛（只缩不放）。
 *
 * 预取与展示必须共用同一份结果，否则内存缓存键不一致，预取等于白发。
 */
internal fun readerPageDecodeSize(pageWidthPx: Int, aspectRatio: Float?): IntSize {
    val width = pageWidthPx.coerceAtLeast(1)
    val ratio = aspectRatio?.takeIf { it.isUsableAspect() }
        ?: return IntSize(
            width,
            (width / DEFAULT_PAGE_ASPECT_RATIO).roundToInt().coerceAtLeast(1),
        )
    return fitDecodeBudget(width, (width / ratio).roundToInt().coerceAtLeast(1))
}

/**
 * 首帧占位与首帧解码尺寸所用的比例。
 *
 * 本页已知比例 > 同一话里最近一次成功的比例 > 3:4 兜底。同一话的各页比例通常一致，
 * 借用前几页的比例能让后续页首帧就占到位，不必每页都从 3:4 跳一次。
 */
internal fun readerPagePlaceholderRatio(known: Float?, chapterSeed: Float?): Float =
    known?.takeIf { it.isUsableAspect() }
        ?: chapterSeed?.takeIf { it.isUsableAspect() }
        ?: DEFAULT_PAGE_ASPECT_RATIO

/** 只有有限的正数才是可用比例：0、负数、NaN、Infinity 都会让页框或解码尺寸失真。 */
private fun Float.isUsableAspect(): Boolean = isFinite() && this > 0f

/** 把 (width, height) 等比收敛到单边上限与像素预算内，返回的边长至少 1 px。 */
private fun fitDecodeBudget(width: Int, height: Int): IntSize {
    var w = width.toFloat()
    var h = height.toFloat()
    val edge = MAX_PAGE_DECODE_EDGE_PX.toFloat()
    if (w > edge || h > edge) {
        val scale = minOf(edge / w, edge / h)
        w *= scale
        h *= scale
    }
    val area = w * h
    if (area > MAX_PAGE_DECODE_PIXELS) {
        val scale = sqrt(MAX_PAGE_DECODE_PIXELS / area)
        w *= scale
        h *= scale
    }
    return IntSize(w.roundToInt().coerceAtLeast(1), h.roundToInt().coerceAtLeast(1))
}
