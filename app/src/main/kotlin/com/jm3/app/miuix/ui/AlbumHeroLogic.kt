package com.jm3.app.miuix.ui

/**
 * 「列表卡片 → 作品详情头图」共享元素飞行的纯 Kotlin 几何与判定。
 *
 * 拆出来是为了可测：真实的飞行由 [AlbumHero] 里的覆盖层画，但起止矩形、插值与
 * 「值不值得飞」的判断都不依赖 Android，单元测试可以直接盯住这些边界。
 */

/** 根坐标系（px）下的矩形；不用 Dp 是为了让列表与详情两侧的测量结果能直接比较。 */
data class HeroRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
) {
    val centerX: Float get() = left + width / 2f
    val centerY: Float get() = top + height / 2f
}

/** 一次飞行的完整参数快照；飞行期间不可变，避免动画中途被新的测量结果改掉终点。 */
data class HeroPlan(
    val id: Int,
    val albumId: String,
    val url: String?,
    val from: HeroRect,
    val to: HeroRect,
    val cornerFromDp: Float,
    val cornerToDp: Float,
)

/** 已点开详情、但详情页头图还没测量出来时的待飞记录。 */
data class HeroPending(
    val albumId: String,
    val url: String?,
    val from: HeroRect,
    val cornerFromDp: Float,
)

/** 位移小于 2px 说明两端几乎重合，飞一次只会露出重影。 */
const val HERO_MIN_TRAVEL_PX: Float = 2f

/** 详情页头图迟迟不出现（网络慢、加载失败）时放弃飞行，让两端封面恢复可见。 */
const val HERO_PENDING_TIMEOUT_MS: Long = 1200L

/** 飞行时长：与 miuix-nav 默认的 500ms 页转场错开，先落位再让页面淡入结束。 */
const val HERO_FLIGHT_DURATION_MS: Int = 380

/** 未测量或尺寸为零的矩形不能作为飞行端点。 */
fun isFiniteHeroRect(rect: HeroRect): Boolean =
    rect.left.isFinite() &&
        rect.top.isFinite() &&
        rect.width.isFinite() &&
        rect.height.isFinite() &&
        rect.width > 0f &&
        rect.height > 0f

/** 按进度线性插值两端矩形；进度先夹到 0..1，动画回调偶发的越界值不会把图形甩到屏幕外。 */
fun lerpHeroRect(from: HeroRect, to: HeroRect, t: Float): HeroRect {
    val p = t.coerceIn(0f, 1f)
    return HeroRect(
        left = from.left + (to.left - from.left) * p,
        top = from.top + (to.top - from.top) * p,
        width = from.width + (to.width - from.width) * p,
        height = from.height + (to.height - from.height) * p,
    )
}

/** 圆角同步插值：起点是列表封面圆角，终点是详情卡圆角。 */
fun lerpHeroCorner(fromDp: Float, toDp: Float, t: Float): Float =
    fromDp + (toDp - fromDp) * t.coerceIn(0f, 1f)

/** 两端都可用且确有位移时才飞；否则交给普通页转场，不额外画一层覆盖物。 */
fun shouldRunHero(from: HeroRect?, to: HeroRect?): Boolean {
    if (from == null || to == null) return false
    if (!isFiniteHeroRect(from) || !isFiniteHeroRect(to)) return false
    val dx = to.centerX - from.centerX
    val dy = to.centerY - from.centerY
    return dx * dx + dy * dy > HERO_MIN_TRAVEL_PX * HERO_MIN_TRAVEL_PX
}

/** 正向飞行：起点已由点击时的待飞记录固定，终点等详情页头图测量出来。 */
fun planForwardFlight(
    id: Int,
    pending: HeroPending?,
    headerRect: HeroRect?,
    headerCornerDp: Float,
): HeroPlan? {
    if (pending == null || headerRect == null) return null
    if (!shouldRunHero(pending.from, headerRect)) return null
    return HeroPlan(
        id = id,
        albumId = pending.albumId,
        url = pending.url,
        from = pending.from,
        to = headerRect,
        cornerFromDp = pending.cornerFromDp,
        cornerToDp = headerCornerDp,
    )
}

/** 反向飞行：起点是详情页头图当前矩形，终点是列表卡片仍登记的矩形。 */
fun planBackwardFlight(
    id: Int,
    albumId: String,
    url: String?,
    headerRect: HeroRect?,
    headerCornerDp: Float,
    coverRect: HeroRect?,
    coverCornerDp: Float,
): HeroPlan? {
    if (headerRect == null || coverRect == null) return null
    if (!shouldRunHero(headerRect, coverRect)) return null
    return HeroPlan(
        id = id,
        albumId = albumId,
        url = url,
        from = headerRect,
        to = coverRect,
        cornerFromDp = headerCornerDp,
        cornerToDp = coverCornerDp,
    )
}
