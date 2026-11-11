package com.jm3.app.miuix.ui.skin

import com.jm3.core.settings.BottomBarPreferencePolicy
import com.jm3.core.settings.BottomBarSize
import com.jm3.core.settings.UiSkin
import top.yukonga.miuix.kmp.nav.transition.NavMotion

/**
 * 动态取色口径：只有 HyperOS 皮肤跟随系统壁纸配色；
 * ColorOS 固定自有色板，用户的动态取色开关不参与取色。
 */
internal object DynamicColorPolicy {
    fun supports(skin: UiSkin): Boolean = skin == UiSkin.HYPEROS

    /** 实际生效的动态取色：请求开启且当前皮肤支持才算开启。 */
    fun effective(skin: UiSkin, requested: Boolean): Boolean = requested && supports(skin)
}

/** 底栏大小口径：仅 HyperOS 开放标准/偏大切换；ColorOS 不论显式设置或屏幕宽度，始终锁定标准档位。 */
internal object BottomBarSizePolicy {
    fun supports(skin: UiSkin): Boolean = skin == UiSkin.HYPEROS

    fun effective(skin: UiSkin, requested: BottomBarSize?, widthDp: Int = 400): BottomBarSize =
        if (supports(skin)) BottomBarPreferencePolicy.resolveSize(requested, widthDp)
        else BottomBarSize.STANDARD
}

/** 导航转场物理：null 表示沿用 Miuix 默认，HyperOS 保持现状。 */
internal fun SkinMotion.navMotionOrDefault(): NavMotion = navMotion ?: NavMotion.Default
