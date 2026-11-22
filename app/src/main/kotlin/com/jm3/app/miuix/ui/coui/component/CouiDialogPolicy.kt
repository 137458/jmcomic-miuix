// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.component

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.itos.coui.dialog.CouiDialogDefaults
import com.itos.coui.dialog.CouiDialogMaterial
import com.itos.coui.menu.rememberCouiWindowBackdrop
import com.itos.coui.theme.CouiTheme as CouiKitTheme
import io.github.suqi8.coui.kmp.blur.Backdrop
import io.github.suqi8.coui.kmp.blur.LayerBackdrop
import io.github.suqi8.coui.kmp.blur.MaterialBlendMode
import io.github.suqi8.coui.kmp.blur.MaterialBlurStyle
import io.github.suqi8.coui.kmp.blur.ViewBlurStyle
import io.github.suqi8.coui.kmp.blur.couiMaterialBlur
import io.github.suqi8.coui.kmp.blur.couiViewBlur
import io.github.suqi8.coui.kmp.blur.isRuntimeShaderSupported
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import io.github.suqi8.coui.kmp.effect.MaterialBackdropProvider
import io.github.suqi8.coui.kmp.effect.MaterialBackdropSpec
import io.github.suqi8.coui.kmp.effect.MaterialBlurBlendMode
import io.github.suqi8.coui.kmp.effect.MaterialBlurProvider
import io.github.suqi8.coui.kmp.effect.MaterialBlurSpec
import io.github.suqi8.coui.kmp.theme.COUITheme

/** 全局开关：是否启用 COUI 17 实时材质模糊（可接入用户设置关闭）。 */
val LocalCouiBlurEnabled = staticCompositionLocalOf { true }

/** 页面内容录制源：供页内浮层弹窗、底栏与菜单对该页背景做材质模糊。 */
val LocalPageBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

internal fun shouldUsePageBackdrop(
    blurEnabled: Boolean,
    runtimeShaderSupported: Boolean,
): Boolean = blurEnabled && runtimeShaderSupported

internal fun shouldUseCouiBlur(
    blurEnabled: Boolean,
    hasBackdrop: Boolean,
    runtimeShaderSupported: Boolean,
): Boolean = blurEnabled && hasBackdrop && runtimeShaderSupported

/** 创建带窗口底色的页面背景录制源；关闭模糊或缺少 RuntimeShader 支持时返回 null。 */
@Composable
fun rememberPageBackdrop(): LayerBackdrop? {
    if (!shouldUsePageBackdrop(
            blurEnabled = LocalCouiBlurEnabled.current,
            runtimeShaderSupported = isRuntimeShaderSupported(),
        )
    ) {
        return null
    }
    val background = COUITheme.colorScheme.backgroundWithCard
    return rememberLayerBackdrop {
        drawRect(background)
        drawContent()
    }
}

/**
 * 将页面录制源桥接至 coui-ui 的 `LocalMaterialBackdrop`（按钮、底栏、FAB、工具栏）。
 * 调用方须保证采样源仅记录这些控件背后的内容，避免控件采样自身。
 */
@Composable
fun rememberMaterialBackdropProvider(backdrop: Backdrop?): MaterialBackdropProvider? {
    val active = shouldUseCouiBlur(
        blurEnabled = LocalCouiBlurEnabled.current,
        hasBackdrop = backdrop != null,
        runtimeShaderSupported = isRuntimeShaderSupported(),
    )
    return remember(backdrop, active) {
        if (active && backdrop != null) {
            MaterialBackdropProvider { spec, shape ->
                Modifier.couiViewBlur(backdrop = backdrop, style = spec.toViewBlurStyle(), shape = shape)
            }
        } else {
            null
        }
    }
}

/** 将页面录制源桥接至 coui-ui 的 `LocalPopupMaterialBlur`（下拉列表、弹出菜单）。 */
@Composable
fun rememberPopupMaterialBlur(backdrop: Backdrop?): MaterialBlurProvider? {
    val active = shouldUseCouiBlur(
        blurEnabled = LocalCouiBlurEnabled.current,
        hasBackdrop = backdrop != null,
        runtimeShaderSupported = isRuntimeShaderSupported(),
    )
    return remember(backdrop, active) {
        if (active && backdrop != null) {
            MaterialBlurProvider { spec, shape ->
                Modifier.couiMaterialBlur(backdrop = backdrop, style = spec.toMaterialBlurStyle(), shape = shape)
            }
        } else {
            null
        }
    }
}

/** 弹窗或底抽屉的模糊面板配置：传给组件的 `backgroundModifier` 与 `backgroundColor`。 */
@Immutable
class PanelBlur(val backgroundModifier: Modifier, val backgroundColor: Color)

/**
 * 为页内弹窗/底抽屉生成对当前页面的材质模糊配置；未启用或无采样源时返回 null（保留不透明表面）。
 *
 * @param style 例如 `MaterialBlurDefaults::dialog` 或 `MaterialBlurDefaults::bottomSheet`。
 */
@Composable
fun panelBlur(style: (isDark: Boolean) -> MaterialBlurStyle): PanelBlur? {
    val backdrop = LocalPageBackdrop.current ?: return null
    if (!shouldUseCouiBlur(
            blurEnabled = LocalCouiBlurEnabled.current,
            hasBackdrop = true,
            runtimeShaderSupported = isRuntimeShaderSupported(),
        )
    ) {
        return null
    }
    return PanelBlur(
        backgroundModifier = Modifier.couiMaterialBlur(
            backdrop = backdrop,
            style = style(COUITheme.isDark),
        ),
        backgroundColor = Color.Transparent,
    )
}

private fun MaterialBackdropSpec.toViewBlurStyle(): ViewBlurStyle = ViewBlurStyle(
    blurRadiusPx = blurRadiusPx,
    layer1 = layer1,
    mode1 = mode1,
    layer2 = layer2,
    mode2 = mode2,
)

private fun MaterialBlurSpec.toMaterialBlurStyle(): MaterialBlurStyle = MaterialBlurStyle(
    blurRadiusPx = blurRadiusPx,
    mixColor = mixColor,
    blendColor = blendColor,
    blendMode = when (blendMode) {
        MaterialBlurBlendMode.Overlay -> MaterialBlendMode.Overlay
        MaterialBlurBlendMode.ColorDodge -> MaterialBlendMode.ColorDodge
        MaterialBlurBlendMode.GlowOverlay -> MaterialBlendMode.GlowOverlay
        MaterialBlurBlendMode.GlowColorDodge -> MaterialBlendMode.GlowColorDodge
    },
)

/**
 * 弹窗材质的可用性判定（Wave 1-2 双版本共存过渡垫片，Wave 3 移除）。
 *
 * 库侧对材质的契约是两项硬前提：系统 Android 13 及以上，且宿主窗口已提供不含弹窗自身的采样源。
 * 任一缺失都会在组合期直接抛异常，因此这里先判再用，缺源时退回不透明弹窗。
 */
@Deprecated("Remove in Wave 3")
internal object CouiDialogPolicy {

    fun shouldUseMaterial(sdkInt: Int, hasBackdrop: Boolean): Boolean = sdkInt >= 33 && hasBackdrop
}

/** 当前窗口是否满足材质前提；满足时返回材质，否则返回 null 让库走不透明弹窗。 */
@Deprecated("Remove in Wave 3")
@Suppress("DEPRECATION")
@Composable
internal fun couiDialogMaterial(): CouiDialogMaterial? =
    if (CouiDialogPolicy.shouldUseMaterial(sdkInt = Build.VERSION.SDK_INT, hasBackdrop = rememberCouiWindowBackdrop() != null)) {
        CouiDialogDefaults.material(CouiKitTheme.isDark)
    } else {
        null
    }

/** 玻璃弹窗内输入区/列表行使用的半透明表面底色，直接承接弹窗玻璃而不叠不透明卡片。 */
@Composable
internal fun couiDialogSurfaceColor(elevated: Boolean = false): Color {
    val dark = COUITheme.isDark
    val alpha = when {
        elevated && dark -> 0.13f
        elevated -> 0.08f
        dark -> 0.09f
        else -> 0.055f
    }
    return COUITheme.colorScheme.labelPrimary.copy(alpha = alpha)
}

/** 玻璃弹窗内部控件的细腻轮廓边线，保证浅/深色下输入区与标签边界清晰。 */
@Composable
internal fun couiDialogSurfaceBorder(): BorderStroke {
    val dark = COUITheme.isDark
    return BorderStroke(
        width = 0.5.dp,
        color = COUITheme.colorScheme.labelPrimary.copy(alpha = if (dark) 0.14f else 0.08f),
    )
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
