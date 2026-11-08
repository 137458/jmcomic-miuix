// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.route

import androidx.compose.runtime.Composable
import com.jm3.core.settings.UiSkin

/**
 * 路由级皮肤分发：只决定这一路由走哪套页面树。
 *
 * 采用「两段 Composable 交给分发器」而不是把二十多个参数转发进分发函数：
 * 参数签名只在宿主处写一遍，分发器不持有任何业务形状，后续新增页面也只需把
 * COUI 版本挂到 colorOs 分支。COLOROS 以外的皮肤一律保持 HyperOS 原实现。
 */
@Composable
fun SkinRoute(
    uiSkin: UiSkin,
    hyperOs: @Composable () -> Unit,
    colorOs: @Composable () -> Unit,
) {
    if (uiSkin == UiSkin.COLOROS) colorOs() else hyperOs()
}
