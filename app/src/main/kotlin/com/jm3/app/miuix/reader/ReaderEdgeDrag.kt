package com.jm3.app.miuix.reader

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChange
import com.jm3.app.miuix.ui.EDGE_CONFIRM_DELAY_MS
import com.jm3.app.miuix.ui.EDGE_CONFIRM_MOVE_PX
import com.jm3.app.miuix.ui.EdgeDragTracker
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * 边界空拖采集：telephoto 越过触摸阈值后会无条件消费全部位移，内容到边界之后也不放手，
 * 父节点在 Main pass 只能看到「已消费」。这里在 Initial pass 抢读原始位移，
 * 再用内容偏移判断内容是否还在跟随手势：内容停下后累计的位移才算边界空拖。
 * 双页与单页两条缩放路径共用这一段，判定与复核时机不会各写一份而漂移。
 */
internal suspend fun PointerInputScope.collectHorizontalEdgeDrag(
    contentOffsetX: () -> Float,
    onEdgeDrag: (dragX: Float, stillPinned: suspend () -> Boolean) -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val edgeTracker = EdgeDragTracker()
            .apply { start(contentOffsetX()) }
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            // 多指是捏合缩放，不参与翻页判定
            if (event.changes.size > 1) break
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            edgeTracker.onDrag(
                deltaX = change.positionChange().x,
                offsetX = contentOffsetX(),
            )
            if (!change.pressed) {
                val pinnedDragX = edgeTracker.pinnedDragX()
                if (pinnedDragX != 0f) {
                    // 快甩时 telephoto 要等松手后才用惯性把内容带走，拖动期间内容几乎不动，
                    // 会把整段位移误算成空拖：把复核推迟到惯性起步之后。
                    val offsetAtRelease = contentOffsetX()
                    onEdgeDrag(pinnedDragX) {
                        delay(EDGE_CONFIRM_DELAY_MS)
                        abs(contentOffsetX() - offsetAtRelease) < EDGE_CONFIRM_MOVE_PX
                    }
                }
                break
            }
        }
    }
}
