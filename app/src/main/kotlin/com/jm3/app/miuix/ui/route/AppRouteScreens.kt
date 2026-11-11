// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.route

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import com.itos.coui.menu.ProvideCouiMenuBackdrop
import com.jm3.core.settings.UiSkin

/**
 * 路由级皮肤分发：只决定这一路由走哪套页面树。
 *
 * 采用「两段 Composable 交给分发器」而不是把二十多个参数转发进分发函数：
 * 参数签名只在宿主处写一遍，分发器不持有任何业务形状，后续新增页面也只需把
 * COUI 版本挂到 colorOs 分支。COLOROS 以外的皮肤一律保持 HyperOS 原实现。
 *
 * 两套页面树互换时旧子树整体离场，页面里的 rememberSaveable（Tab、滚动位置、
 * 线路输入）会跟着丢，所以按皮肤名分组存一份可保存状态，切回来能续上。
 */
@Composable
fun SkinRoute(
    uiSkin: UiSkin,
    hyperOs: @Composable () -> Unit,
    colorOs: @Composable () -> Unit,
) {
    val stateHolder = rememberSaveableStateHolder()
    stateHolder.SaveableStateProvider(uiSkin.name) {
        if (uiSkin == UiSkin.COLOROS) colorOs() else hyperOs()
    }
}

/**
 * 窗口级弹窗背景源：把库的「菜单/材质弹窗采样源」挂到窗口内容根节点。
 *
 * 库对材质弹窗有两项硬前提——系统 Android 13 及以上、宿主已提供不含弹窗自身的背景源，
 * 缺任一项都在组合期抛异常。源必须挂在窗口根而不是单条路由：启动更新提示等弹窗在路由之外，
 * 挂在路由内它们拿不到源。HyperOS 皮肤不使用库弹窗，这里直接透传，不做额外离屏录制。
 */
@Composable
fun SkinWindowBackdrop(
    uiSkin: UiSkin,
    content: @Composable (Modifier) -> Unit,
) {
    if (uiSkin == UiSkin.COLOROS) {
        ProvideCouiMenuBackdrop(content = content)
    } else {
        content(Modifier)
    }
}
