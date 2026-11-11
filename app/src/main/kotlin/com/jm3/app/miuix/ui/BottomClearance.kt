package com.jm3.app.miuix.ui

internal const val COUI_FAB_SIZE_DP = 56f
internal const val COUI_FAB_EDGE_MARGIN_DP = 16f
internal const val COUI_FAB_GAP_DP = 12f
internal const val FAB_SCROLL_BREATHING_DP = 16f
internal const val GLASS_FAB_HEIGHT_DP = 52f
internal const val GLASS_SCAFFOLD_BREATHING_DP = 20f
internal const val FLOATING_TOOLBAR_EXTRA_BOTTOM_DP = 72f
internal const val COUI_PAGE_BOTTOM_BREATHING_DP = 24f
internal const val COUI_SINGLE_FAB_EXTRA_BOTTOM_DP = 72f

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

internal fun couiHomeExtraBottomDp(fabCount: Int = 2): Float {
    if (fabCount <= 0) return 0f
    return COUI_FAB_EDGE_MARGIN_DP +
        fabCount * COUI_FAB_SIZE_DP +
        (fabCount - 1) * COUI_FAB_GAP_DP +
        FAB_SCROLL_BREATHING_DP
}

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

internal fun creatorWorkExtraBottomDp(hasRelated: Boolean): Float =
    if (hasRelated) FLOATING_TOOLBAR_EXTRA_BOTTOM_DP else 0f
