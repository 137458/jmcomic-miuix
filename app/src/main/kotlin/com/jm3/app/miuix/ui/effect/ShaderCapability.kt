// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.effect

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast

/** 运行时着色器不可用的原因。目前只有一道门槛，留成结构是为了诊断时不必再猜。 */
internal enum class ShaderUnavailableReason {
    /** 系统版本低于 Android 13（API 33），平台没有 `RuntimeShader`。 */
    ApiLevel,
}

/** 一次能力判定的完整结果：布尔值给分支用，原因给日志与排查用。 */
internal data class ShaderCapability(
    val sdkInt: Int,
    val runtimeShaderSupported: Boolean,
    val unavailableReason: ShaderUnavailableReason? = null,
)

/** 纯判定：把版本号翻成能力结果。单测可以直接喂边界值，不必依赖真机版本。 */
internal fun resolveShaderCapability(sdkInt: Int): ShaderCapability =
    if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
        ShaderCapability(sdkInt = sdkInt, runtimeShaderSupported = true)
    } else {
        ShaderCapability(
            sdkInt = sdkInt,
            runtimeShaderSupported = false,
            unavailableReason = ShaderUnavailableReason.ApiLevel,
        )
    }

/**
 * 全仓唯一的运行时着色器（AGSL / `RuntimeShader`）能力判定入口。
 *
 * 取舍：`top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported`（miuix 0.9.4）经字节码核实就是
 * `SDK_INT >= 33`，与本函数语义完全一致。此前玻璃侧六个文件 import 库内同名函数、背景特效侧自带
 * 一个同名函数，等于同一次判定有两个入口；现在两侧都只认这里，库内函数不再被引用，门槛只写一处。
 *
 * `@ChecksSdkIntAtLeast` 让 lint 把本函数识别为版本门槛：函数体内的 API 33 平台符号调用因此
 * 不必再各写一遍字面量版本比较（[RuntimeShaderCompat] 的四处守卫据此收敛到本入口）。
 */
@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
fun isRuntimeShaderSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
