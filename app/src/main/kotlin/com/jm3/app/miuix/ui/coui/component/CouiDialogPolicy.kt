// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.component

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.itos.coui.dialog.CouiDialogDefaults
import com.itos.coui.dialog.CouiDialogMaterial
import com.itos.coui.menu.rememberCouiWindowBackdrop
import com.itos.coui.theme.CouiTheme as CouiKitTheme

/**
 * 弹窗材质的可用性判定。
 *
 * 库侧对材质的契约是两项硬前提：系统 Android 13 及以上，且宿主窗口已提供不含弹窗自身的采样源。
 * 任一缺失都会在组合期直接抛异常，因此这里先判再用，缺源时退回不透明弹窗。
 */
internal object CouiDialogPolicy {

    fun shouldUseMaterial(sdkInt: Int, hasBackdrop: Boolean): Boolean = sdkInt >= 33 && hasBackdrop
}

/** 当前窗口是否满足材质前提；满足时返回材质，否则返回 null 让库走不透明弹窗。 */
@Composable
internal fun couiDialogMaterial(): CouiDialogMaterial? =
    if (CouiDialogPolicy.shouldUseMaterial(sdkInt = Build.VERSION.SDK_INT, hasBackdrop = rememberCouiWindowBackdrop() != null)) {
        CouiDialogDefaults.material(CouiKitTheme.isDark)
    } else {
        null
    }

/**
 * 保留弹窗最近一次非空目标：目标置空后，让弹窗在 visible = false 退场期间继续拿到渲染数据，
 * 避免退场动画被提前 return 抽走或标题突然退化成空白。
 */
@Composable
internal fun <T : Any> rememberRetainedTarget(target: T?): T? {
    var retained by remember { mutableStateOf<T?>(null) }
    SideEffect { if (target != null) retained = target }
    return target ?: retained
}
