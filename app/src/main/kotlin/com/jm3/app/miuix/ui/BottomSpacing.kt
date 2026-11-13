// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui

import com.jm3.core.settings.BottomBarForm
import com.jm3.core.settings.BottomBarSize

/*
 * 底部净空的唯一推导处。
 *
 * 此前是两套互不知情的推导：ui/BottomClearance.kt 只算 FAB、创作者浮动工具条与 COUI 页面底部呼吸；
 * 玻璃底栏的 contentPadding（80dp / 108dp / 64dp）写在 glass/BottomBarTokens.kt 的注释里，公式没沉淀。
 * 两者描述的是同一件事——「屏幕底到内容可停在的最低处」，改一侧推不出另一侧是否要跟。
 *
 * 现在底栏净空与 FAB 净空都从这里派生：底栏 = 条高 + 底距 + 呼吸，FAB = 边距 + 尺寸 + 间隙 + 呼吸。
 * BottomBarTokens 只保留视觉度量，净空向本文件取值。
 */

// ── 底栏净空：条高 + 底距 + 呼吸 ──

/** 悬浮底栏（普通档）的底距：胶囊底边到屏幕可用底边的距离。 */
internal const val BOTTOM_BAR_STANDARD_BOTTOM_MARGIN_DP = 16f

/** 悬浮底栏（普通档）的呼吸：底距之外留给列表末项的下沉余量。 */
internal const val BOTTOM_BAR_STANDARD_BREATHING_DP = 8f

/** 悬浮底栏（偏大档）的底距：与呼吸一同比普通档放大 1.5 倍，避免大条配小间距。 */
internal const val BOTTOM_BAR_GENEROUS_BOTTOM_MARGIN_DP = 24f

/** 悬浮底栏（偏大档）的呼吸：1.5 倍放大的另一半。 */
internal const val BOTTOM_BAR_GENEROUS_BREATHING_DP = 12f

/**
 * 底栏占用的底部净空：屏幕底到内容最低可停留处的距离，交给 `LocalFloatingBarContentPadding`。
 *
 * - 悬浮：条高 + 底距 + 呼吸。普通档 56 + 16 + 8 = 80；偏大档 72 + 24 + 12 = 108。
 * - 贴底通栏：底栏本身贴到屏幕底，没有底距与呼吸，净空就是条高（Miuix NavigationBar 64dp）。
 *
 * [heightDp] 由调用方传入实际绘制高度，净空与条高因此只有一处来源，不会各写一个数而漂移。
 */
internal fun bottomBarContentPaddingDp(
    heightDp: Float,
    size: BottomBarSize,
    form: BottomBarForm,
): Float = when (form) {
    BottomBarForm.DOCKED -> heightDp
    BottomBarForm.FLOATING -> {
        val bottomMarginDp = when (size) {
            BottomBarSize.STANDARD -> BOTTOM_BAR_STANDARD_BOTTOM_MARGIN_DP
            BottomBarSize.GENEROUS -> BOTTOM_BAR_GENEROUS_BOTTOM_MARGIN_DP
        }
        val breathingDp = when (size) {
            BottomBarSize.STANDARD -> BOTTOM_BAR_STANDARD_BREATHING_DP
            BottomBarSize.GENEROUS -> BOTTOM_BAR_GENEROUS_BREATHING_DP
        }
        heightDp + bottomMarginDp + breathingDp
    }
}

// ── COUI FAB 净空 ──

internal const val COUI_FAB_SIZE_DP = 56f
internal const val COUI_FAB_EDGE_MARGIN_DP = 16f
internal const val COUI_FAB_GAP_DP = 12f
internal const val FAB_SCROLL_BREATHING_DP = 16f
internal const val COUI_PAGE_BOTTOM_BREATHING_DP = 24f
internal const val COUI_SINGLE_FAB_EXTRA_BOTTOM_DP = 72f

/**
 * COUI 页面底部额外净空：按 FAB 数量与软键盘态收敛。
 *
 * 双 FAB 走首页公式，单 FAB 固定 72dp；软键盘弹出且没有 FAB 时归零（键盘自己已经让位），
 * 其余情况给页面呼吸 24dp。
 */
internal fun couiPageExtraBottomDp(
    hasFab: Boolean = false,
    fabCount: Int = if (hasFab) 1 else 0,
    isImeVisible: Boolean = false,
): Float = when {
    fabCount >= 2 -> couiHomeExtraBottomDp(fabCount)
    fabCount == 1 -> COUI_SINGLE_FAB_EXTRA_BOTTOM_DP
    isImeVisible -> 0f
    else -> COUI_PAGE_BOTTOM_BREATHING_DP
}

/** COUI 首页双 FAB：下边距 + 每个 FAB + 之间的间隙 + 滚动呼吸。 */
internal fun couiHomeExtraBottomDp(fabCount: Int = 2): Float {
    if (fabCount <= 0) return 0f
    return COUI_FAB_EDGE_MARGIN_DP +
        fabCount * COUI_FAB_SIZE_DP +
        (fabCount - 1) * COUI_FAB_GAP_DP +
        FAB_SCROLL_BREATHING_DP
}

// ── 玻璃悬浮动作净空 ──

internal const val GLASS_FAB_HEIGHT_DP = 52f
internal const val GLASS_SCAFFOLD_BREATHING_DP = 20f

/**
 * HyperOS 首页 FAB 净空：悬浮动作条顶端 + 呼吸，减掉脚手架已给的底栏净空与自身呼吸，不足补零。
 *
 * 宽屏侧栏（无底栏）时 [floatingBarContentPaddingDp] 为 0，补足量等于 FAB 顶端加呼吸减脚手架呼吸。
 */
internal fun glassHomeExtraBottomDp(
    hasActions: Boolean,
    floatingBarContentPaddingDp: Float,
    floatingActionsBottomDp: Float,
    fabHeightDp: Float = GLASS_FAB_HEIGHT_DP,
    scaffoldBreathingDp: Float = GLASS_SCAFFOLD_BREATHING_DP,
    extraBreathingDp: Float = FAB_SCROLL_BREATHING_DP,
): Float {
    if (!hasActions) return 0f
    val fabTop = floatingActionsBottomDp + fabHeightDp
    val requiredBottom = fabTop + extraBreathingDp
    val scaffoldBottom = floatingBarContentPaddingDp + scaffoldBreathingDp
    return (requiredBottom - scaffoldBottom).coerceAtLeast(0f)
}

// ── 创作者页浮动工具条 ──

internal const val FLOATING_TOOLBAR_EXTRA_BOTTOM_DP = 72f

/** 作者作品详情：只在展示底部 FloatingToolbar 时增加净空。 */
internal fun creatorWorkExtraBottomDp(hasRelated: Boolean): Float =
    if (hasRelated) FLOATING_TOOLBAR_EXTRA_BOTTOM_DP else 0f
