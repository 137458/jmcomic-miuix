package com.jm3.app.miuix.ui.coui.page

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import com.jm3.app.miuix.ui.COMMENT_REPLY_MAX_LENGTH
import com.jm3.core.model.Chapter
import com.jm3.core.model.NovelChapterMeta
import com.jm3.core.model.PhotoPages
import kotlin.math.roundToInt
import kotlin.math.sign

internal const val READER_VOLUME_THROTTLE_MS = 150L

/**
 * COUI 漫画与小说阅读器共用的弹窗互斥槽位。
 *
 * 任意时刻主屏幕只允许至多一个顶层弹窗或回复弹窗处于可见状态；
 * 回复目标存在时主弹窗退让并在离开回复后决定是否回退评论列表。
 */
internal enum class CouiReaderModal {
    NONE,
    PAGE_JUMP,
    CHAPTERS,
    FONT_SIZE,
    COMMENTS,
    TRACK_PROMPT,
}

/**
 * 阅读器单层弹窗状态：保证回复窗、主弹窗与跳转登录不会互相压住。
 */
internal data class CouiReaderModalState(
    val activeModal: CouiReaderModal = CouiReaderModal.NONE,
    val reopenCommentsAfterReply: Boolean = false,
) {
    fun open(modal: CouiReaderModal): CouiReaderModalState =
        CouiReaderModalState(activeModal = modal, reopenCommentsAfterReply = false)

    fun dismiss(): CouiReaderModalState =
        if (activeModal == CouiReaderModal.NONE) this
        else copy(activeModal = CouiReaderModal.NONE)

    fun enterReply(): CouiReaderModalState =
        CouiReaderModalState(
            activeModal = CouiReaderModal.NONE,
            reopenCommentsAfterReply = (activeModal == CouiReaderModal.COMMENTS) || reopenCommentsAfterReply,
        )

    fun exitReply(): CouiReaderModalState =
        CouiReaderModalState(
            activeModal = if (reopenCommentsAfterReply) CouiReaderModal.COMMENTS else CouiReaderModal.NONE,
            reopenCommentsAfterReply = false,
        )

    fun dismissAllForNavigation(): CouiReaderModalState = CouiReaderModalState()
}

internal val CouiReaderModalStateSaver: Saver<CouiReaderModalState, Any> = listSaver(
    save = { listOf(it.activeModal.name, it.reopenCommentsAfterReply) },
    restore = { saved ->
        val name = saved.getOrNull(0) as? String
        val modal = CouiReaderModal.entries.firstOrNull { it.name == name } ?: CouiReaderModal.NONE
        val reopen = saved.getOrNull(1) as? Boolean ?: false
        CouiReaderModalState(activeModal = modal, reopenCommentsAfterReply = reopen)
    },
)

/** 同一时刻至多展示一个主弹窗；正在回复某条评论时主弹窗退让。 */
internal fun couiVisibleReaderModal(
    state: CouiReaderModalState,
    hasReplyTarget: Boolean,
): CouiReaderModal? = when {
    hasReplyTarget -> null
    state.activeModal == CouiReaderModal.NONE -> null
    else -> state.activeModal
}

/** 当前是否存在任意可见弹窗（主弹窗或回复窗），用于音量键与返回拦截让渡。 */
internal fun couiHasOpenReaderModal(
    state: CouiReaderModalState,
    hasReplyTarget: Boolean,
): Boolean = hasReplyTarget || state.activeModal != CouiReaderModal.NONE

/** 竖向阅读按列表实际内容宽度（扣减宽屏可读区左右留白）推算页宽与解码尺寸。 */
internal fun couiReaderVerticalPageWidthPx(
    windowWidthPx: Int,
    readableHorizontalPaddingPx: Int,
): Int = (windowWidthPx - readableHorizontalPaddingPx.coerceAtLeast(0) * 2).coerceAtLeast(1)

/** 提示条底部额外让位高度（dp）：HUD 可见时避开底栏与上下边距，隐藏时归零。 */
internal fun couiReaderSnackbarBottomPaddingDp(
    hudVisible: Boolean,
    bottomBarHeightDp: Int,
    barVerticalMarginDp: Int,
): Int = if (hudVisible) {
    bottomBarHeightDp.coerceAtLeast(0) + barVerticalMarginDp.coerceAtLeast(0) * 2
} else {
    0
}

/** 评论与回复输入统一按字符上限截断，避免整段粘贴超长文本被直接吞掉。 */
internal fun couiClampCommentInput(
    raw: String,
    maxLength: Int = COMMENT_REPLY_MAX_LENGTH,
): String = raw.take(maxLength.coerceAtLeast(0))

/** 跳页器加减步进，目标页码始终夹在 1..totalPages 范围内。 */
internal fun couiStepJumpPage(
    currentPage: Int,
    delta: Int,
    totalPages: Int,
): Int = (currentPage + delta).coerceIn(1, totalPages.coerceAtLeast(1))

/** 只在模式初始化、初始进度恢复都已落地且没有跳页动画在途时同步横向 pager 索引。 */
internal fun couiShouldSyncPagerOnModeChange(
    horizontalReady: Boolean,
    progressRestored: Boolean,
    horizontal: Boolean,
    pagerPageCount: Int,
    pendingJumpTarget: Int?,
): Boolean = horizontalReady &&
    progressRestored &&
    horizontal &&
    pagerPageCount > 0 &&
    pendingJumpTarget == null

/** 计算当前小说章节的上一章/下一章；未找到当前章时两侧均返回 null。 */
internal fun couiAdjacentNovelChapters(
    chapters: List<NovelChapterMeta>,
    currentId: String,
): Pair<NovelChapterMeta?, NovelChapterMeta?> {
    val index = chapters.indexOfFirst { it.ncid == currentId }
    if (index < 0) return null to null
    return chapters.getOrNull(index - 1) to chapters.getOrNull(index + 1)
}

/** 小说阅读器音量键按阅读视口高度的 80% 翻滚，并安全夹取至有效滚动范围。 */
internal fun couiNovelVolumeScrollTarget(
    currentScrollPx: Int,
    maxScrollPx: Int,
    viewportHeightPx: Int,
    direction: Int,
): Int {
    val ceiling = maxScrollPx.coerceAtLeast(0)
    val clampedCurrent = currentScrollPx.coerceIn(0, ceiling)
    if (direction == 0 || viewportHeightPx <= 0 || ceiling == 0) return clampedCurrent
    val step = (viewportHeightPx * 0.8f).roundToInt().coerceAtLeast(1)
    return (clampedCurrent + step * direction.sign).coerceIn(0, ceiling)
}

/** 回填离线页缺失的 albumId 与标题，确保阅读页与历史记录不写空值。 */
internal fun resolveCouiLoadedPhoto(
    photo: PhotoPages,
    savedAlbumId: String?,
    savedTitle: String?,
    detailTitle: String?,
): PhotoPages {
    val resolvedAlbumId = photo.albumId.ifBlank { savedAlbumId.orEmpty() }
    val resolvedTitle = photo.title.ifBlank {
        savedTitle?.takeIf { it.isNotBlank() } ?: detailTitle.orEmpty()
    }
    return photo.copy(albumId = resolvedAlbumId, title = resolvedTitle)
}

/** 章节列表按 sortOrder 升序归一，确保上下话与目录顺序稳定。 */
internal fun sortCouiReaderChapters(chapters: List<Chapter>): List<Chapter> =
    chapters.sortedBy { it.sortOrder }

/** 仅当存在翻页方向且未被缩放浮层或模态弹窗遮挡时消费音量键。 */
internal fun couiShouldHandleReaderVolumeKey(
    delta: Int,
    hasZoomOverlay: Boolean,
    hasOpenModal: Boolean,
): Boolean = delta != 0 && !hasZoomOverlay && !hasOpenModal

internal enum class CouiReaderHudPlacement {
    TOP,
    BOTTOM,
}

internal data class CouiReaderHudShadowSpec(
    val elevationDp: Float,
    val shadowAlpha: Float,
    val lightYDp: Float,
    val lightZDp: Float,
    val lightRadiusDp: Float,
    val causticBottomDp: Float,
    val causticSdfDp: Float,
    val causticAlpha: Float,
    val smoothCorner: Boolean,
)

/** 依据顶/底浮栏位置与明暗/凝光开关计算浮栏阴影与焦散几何，避免顶栏底缘出现异常白光晕。 */
internal fun couiReaderHudShadowSpec(
    placement: CouiReaderHudPlacement,
    dark: Boolean,
    contourGlowEnabled: Boolean,
): CouiReaderHudShadowSpec = when (placement) {
    CouiReaderHudPlacement.TOP -> CouiReaderHudShadowSpec(
        elevationDp = if (contourGlowEnabled) 16f else 100f,
        shadowAlpha = (if (contourGlowEnabled) 30f else if (dark) 68f else 34f) / 255f * 0.75f,
        lightYDp = if (contourGlowEnabled) -160f else if (dark) -240f else -200f,
        lightZDp = if (contourGlowEnabled) 0f else 10000f,
        lightRadiusDp = if (contourGlowEnabled) 800f else 2000f,
        causticBottomDp = 0f,
        causticSdfDp = 0f,
        causticAlpha = 0f,
        smoothCorner = true,
    )

    CouiReaderHudPlacement.BOTTOM -> CouiReaderHudShadowSpec(
        elevationDp = if (contourGlowEnabled) 24f else 350f,
        shadowAlpha = when {
            contourGlowEnabled -> 0x23 / 255f
            dark -> 0x78 / 255f
            else -> 0x28 / 255f
        },
        lightYDp = if (contourGlowEnabled) -320f else -160f,
        lightZDp = if (contourGlowEnabled) 0f else 10000f,
        lightRadiusDp = if (contourGlowEnabled) 1600f else 2000f,
        causticBottomDp = 4.9f,
        causticSdfDp = 10.4f,
        causticAlpha = 0x42 / 255f,
        smoothCorner = true,
    )
}
