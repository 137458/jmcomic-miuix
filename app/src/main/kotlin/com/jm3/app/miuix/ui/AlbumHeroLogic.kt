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
    val id: Int = 0,
)

/** 位移小于 2px 说明两端几乎重合，飞一次只会露出重影。 */
const val HERO_MIN_TRAVEL_PX: Float = 2f

/** 详情页头图迟迟不出现（网络慢、加载失败）时放弃飞行，让两端封面恢复可见。 */
const val HERO_PENDING_TIMEOUT_MS: Long = 1200L

/** 飞行时长：与 miuix-nav 默认的 500ms 页转场错开，先落位再让页面淡入结束。 */
const val HERO_FLIGHT_DURATION_MS: Int = 380

/**
 * 飞行落位后等详情头图画出首帧的上限。
 *
 * 覆盖层刚落位时头图可能还在解内存缓存（异步、要等一两帧），这时候收起覆盖层就会露出空白封面，
 * 也就是用户说的"进详情页封面闪一下"。超时只是兜底，正常命中缓存远小于这个值。
 */
const val HERO_HEADER_READY_TIMEOUT_MS: Long = 200L

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

/** 退出进度小于此值即视为「回到静息」：手势已取消，且页面仍是顶层。 */
internal const val HERO_EXIT_EPSILON: Float = 0.01f

/** 反向飞行编排的一次决策；[AlbumDetailHeroFlightEffect] 只负责把命令执行到 host 上。 */
internal sealed interface HeroBackwardCommand {
    /** 自己的反向飞行要收场：结束飞行，并把列表封面放回可见。 */
    data class FinishOwnFlight(val flightId: Int) : HeroBackwardCommand

    /** 自己的反向飞行继续跟着退出进度走。 */
    data class AdvanceOwnFlight(val flightId: Int, val progress: Float) : HeroBackwardCommand

    /** 退出信号成立且没有别的飞行占位：去 host.startBackward 起飞。 */
    data class StartFlight(val progress: Float) : HeroBackwardCommand

    /** 回到静息：清掉上一轮的起飞失败标记，下一次返回仍能尝试。 */
    data object ResetAttempt : HeroBackwardCommand

    /** 保持现状：没有明确退出信号、已有别的飞行在飞，或本轮已经失败过。 */
    data object Idle : HeroBackwardCommand
}

/**
 * 反向飞行的纯判定：导航层的退出进度、本页是否在出栈、host 是否有飞行在飞，三者决定下一步。
 *
 * @param exit 退出进度 0..1（`-relativeDepth`，与页面 alpha 同源）
 * @param popping 是否已有明确退出信号（返回手势进行中，或页面已被标记出栈）
 * @param startFailed 本轮退出是否已经尝试起飞并失败过
 * @param ownFlightId 本页自己的反向飞行 id；非空表示它已经在飞
 * @param hostBusy 是否已有正向/反向飞行或待飞记录占着 host
 */
internal fun planHeroBackwardCommand(
    exit: Float,
    popping: Boolean,
    startFailed: Boolean,
    ownFlightId: Int?,
    hostBusy: Boolean,
): HeroBackwardCommand {
    val progress = exit.coerceIn(0f, 1f)
    if (ownFlightId != null) {
        return if (progress <= HERO_EXIT_EPSILON && !popping) {
            HeroBackwardCommand.FinishOwnFlight(ownFlightId)
        } else {
            HeroBackwardCommand.AdvanceOwnFlight(ownFlightId, progress)
        }
    }
    if (progress <= HERO_EXIT_EPSILON) return HeroBackwardCommand.ResetAttempt
    if (!popping || startFailed) return HeroBackwardCommand.Idle
    if (hostBusy) return HeroBackwardCommand.Idle
    return HeroBackwardCommand.StartFlight(progress)
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
