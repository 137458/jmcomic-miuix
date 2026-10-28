// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.glass

import top.yukonga.miuix.kmp.blur.BackdropEffectScope
import top.yukonga.miuix.kmp.blur.colorControls

/** 液态玻璃透镜前的轻量饱和度增强。 */
fun BackdropEffectScope.vibrancy() {
    colorControls(
        brightness = 0f,
        contrast = 1f,
        saturation = 1.5f,
    )
}
