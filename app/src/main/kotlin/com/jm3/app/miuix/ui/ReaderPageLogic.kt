package com.jm3.app.miuix.ui

import androidx.compose.ui.unit.IntSize
import kotlin.math.floor
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
 * 能当章节种子的最小宽高比（宽/高），0.5 即 1:2。
 * 更窄的条幅只记本页比例：一张 1:25 的条幅会把种子压到 0.04，同章后续未解码页的占位框
 * 被撑到上万 px，解码请求还会被单边上限截成 328x8192，等真图塌回来时滚动位置剧烈跳动。
 */
internal const val MIN_SEED_ASPECT_RATIO = 0.5f

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
 * 判断一次解码出的比例能否当章节种子。
 *
 * 条幅与非法比例返回 null：它们只代表本页，拿去做同章其余页的占位与解码基准会把整章带偏。
 */
internal fun chapterSeedCandidate(detected: Float): Float? =
    detected.takeIf { it.isUsableAspect() && it >= MIN_SEED_ASPECT_RATIO }

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

/**
 * 一页的布局比例与请求尺寸。
 *
 * [layoutRatio] 跟随本页已知比例或章节种子，[decodeSize] 只跟随解码比例：一次比例回填若同时
 * 改掉请求尺寸，coil3 默认 Precision.EXACT 会判内存缓存失效并重解一遍，而这一遍多半拿到的
 * 还是同一张位图。两份入参分开，布局与请求因此各自独立演进。
 */
internal data class PageFrame(val layoutRatio: Float, val decodeSize: IntSize)

/**
 * 展示与预取共用的唯一换算入口：两处传同一组入参，同一页的请求尺寸必然一致。
 * 尺寸不一致时内存缓存键不同，预取等于白解。
 */
internal fun readerPageFrame(
    pageWidthPx: Int,
    knownRatio: Float?,
    chapterSeed: Float?,
    decodeRatio: Float?,
): PageFrame = PageFrame(
    layoutRatio = readerPagePlaceholderRatio(knownRatio, chapterSeed),
    decodeSize = readerPageDecodeSize(pageWidthPx, decodeRatio),
)

/**
 * 解码结果是否顶满请求框的某一维。
 *
 * 顶满说明画面被请求框限制住，按真实比例重算请求才能换到更贴合的尺寸（1080x1440 的框解出
 * 77x1440，重算后是 328x8192）；两维都没顶满说明源码本身就小于请求框，重算只会再解一次
 * 同一张位图，纯属浪费。
 */
internal fun isBoxLimited(
    requestedWidthPx: Int,
    requestedHeightPx: Int,
    decodedWidthPx: Float,
    decodedHeightPx: Float,
): Boolean = decodedWidthPx >= requestedWidthPx - 1 || decodedHeightPx >= requestedHeightPx - 1

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
        // 面积分支向下取整：四舍五入会把乘积重新顶出预算（1000x4167 收敛成 980x4083 就超了 4 MP）
        return IntSize(
            floor(w * scale).toInt().coerceAtLeast(1),
            floor(h * scale).toInt().coerceAtLeast(1),
        )
    }
    return IntSize(w.roundToInt().coerceAtLeast(1), h.roundToInt().coerceAtLeast(1))
}
