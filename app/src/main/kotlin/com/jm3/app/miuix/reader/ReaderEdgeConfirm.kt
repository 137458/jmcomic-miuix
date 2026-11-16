package com.jm3.app.miuix.reader

import com.jm3.app.miuix.ui.EDGE_CONFIRM_MOVE_PX
import kotlin.math.abs

/**
 * 边缘空拖的松手复核：松手后等惯性起步，再看内容是否还在被带走。
 *
 * 快甩时 content 在松手后才由惯性推开，拖动期间几乎不动，整段位移会被误算成空拖；
 * 因此窗口结束时内容位移小于 [EDGE_CONFIRM_MOVE_PX] 才算「内容仍停在边界」，空拖成立。
 * 计时（EDGE_CONFIRM_DELAY_MS）留在 Compose 协程里，这里只判位移。
 */
internal fun edgeDragStillPinned(
    offsetAtRelease: Float,
    offsetNow: Float,
    moveTolerancePx: Float = EDGE_CONFIRM_MOVE_PX,
): Boolean = abs(offsetNow - offsetAtRelease) < moveTolerancePx
