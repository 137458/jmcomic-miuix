package com.jm3.app.miuix.ui.skin

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

/** 导航转场物理：null 表示沿用 Miuix 默认，HyperOS 保持现状。 */
internal fun SkinMotion.navMotionOrDefault(): NavMotion = navMotion ?: NavMotion.Default
