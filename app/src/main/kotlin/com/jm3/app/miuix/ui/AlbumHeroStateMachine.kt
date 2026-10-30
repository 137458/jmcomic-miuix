package com.jm3.app.miuix.ui

/**
 * 共享元素飞行状态机的纯数据模型与转移函数。
 * 不依赖 Compose 或 Android 视图层，保证完整的单元测试覆盖。
 */
data class HeroState(
    val pending: HeroPending? = null,
    val flight: HeroPlan? = null,
    val heroOrigins: Set<String> = emptySet(),
)

/**
 * 列表卡片点击时调用：
 * 矩形非法则状态不变；
 * 否则写入 pending 并将 albumId 记入 heroOrigins。
 */
fun HeroState.startForward(
    id: Int,
    albumId: String,
    url: String?,
    from: HeroRect,
    cornerDp: Float,
): HeroState {
    if (!isFiniteHeroRect(from)) return this
    val newPending = HeroPending(
        albumId = albumId,
        url = url,
        from = from,
        cornerFromDp = cornerDp,
        id = id,
    )
    return copy(
        pending = newPending,
        heroOrigins = heroOrigins + albumId,
    )
}

/**
 * 详情头图测量完成后补上终点：
 * 无论成功与否都清空 pending（彻底杜绝幽灵飞行残留）；
 * 仅当能生成合法飞行计划时生成 flight。
 */
fun HeroState.resolveForward(
    headerRect: HeroRect?,
    headerCornerDp: Float,
): HeroState {
    val currentPending = pending ?: return this
    val plan = planForwardFlight(
        id = currentPending.id,
        pending = currentPending,
        headerRect = headerRect,
        headerCornerDp = headerCornerDp,
    )
    return copy(
        pending = null,
        flight = plan,
    )
}

/**
 * 等待超时或被中断时取消待飞记录：
 * 只清空 pending，保留 origin 集合以便后续追踪。
 */
fun HeroState.cancelPending(): HeroState = copy(pending = null)

/**
 * 退栈时调用：
 * 仅当 albumId 在 heroOrigins 中才可能触发反向飞行；
 * 无论是否飞成，都将该 albumId 从 heroOrigins 移除，并清空 pending。
 */
fun HeroState.startBackward(
    id: Int,
    albumId: String,
    url: String?,
    headerRect: HeroRect?,
    headerCornerDp: Float,
    coverRect: HeroRect?,
    coverCornerDp: Float,
): HeroState {
    if (albumId !in heroOrigins) {
        return copy(pending = null)
    }
    val plan = planBackwardFlight(
        id = id,
        albumId = albumId,
        url = url,
        headerRect = headerRect,
        headerCornerDp = headerCornerDp,
        coverRect = coverRect,
        coverCornerDp = coverCornerDp,
    )
    return copy(
        pending = null,
        flight = plan,
        heroOrigins = heroOrigins - albumId,
    )
}

/**
 * 飞行完成时调用：
 * 仅当 flight.id 与传入的 id 匹配时清空 flight。
 */
fun HeroState.finishFlight(id: Int): HeroState {
    return if (flight?.id == id) copy(flight = null) else this
}
