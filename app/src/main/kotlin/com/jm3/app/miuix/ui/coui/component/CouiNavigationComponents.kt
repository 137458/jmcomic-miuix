// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.component

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jm3.app.miuix.ui.glass.GlassBottomBarTokens
import com.jm3.core.settings.BottomBarForm
import com.jm3.core.settings.BottomBarSize
import io.github.suqi8.coui.kmp.basic.FloatingActionButton
import io.github.suqi8.coui.kmp.basic.FloatingNavigationBar
import io.github.suqi8.coui.kmp.basic.FloatingNavigationBarItem
import io.github.suqi8.coui.kmp.basic.Icon as Coui2Icon
import io.github.suqi8.coui.kmp.basic.NavigationBar as Coui2LibNavigationBar
import io.github.suqi8.coui.kmp.basic.NavigationBarItem as Coui2LibNavigationBarItem
import io.github.suqi8.coui.kmp.basic.NavigationRail as Coui2LibNavigationRail
import io.github.suqi8.coui.kmp.basic.NavigationRailItem as Coui2LibNavigationRailItem
import io.github.suqi8.coui.kmp.blur.Backdrop
import io.github.suqi8.coui.kmp.effect.LocalMaterialBackdrop
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.LocalContentColor

/** 库浮动材质导航栏需要 API 34：库内实时浮动材质依赖 34 起的模糊与渲染能力。 */
internal const val CouiLibraryNavBarMinSdk = 34

/** 菜单项宽度下限与间隔，用于复算最大菜单宽度：24 + 70*n + 8*(n-1)。 */
private val CouiNavItemWidth = 70.dp
private val CouiNavItemSpacing = 8.dp
private val CouiNavSideSurfaceWidth = 136.dp

/** 选中容器透明度：底栏、侧栏与分段胶囊共用，保证所有选中观感一致。 */
internal const val CouiSelectedContainerAlpha = 0.14f

/** 侧栏宽度：只放图标列，横向固定不参与伸缩。 */
internal val CouiRailWidth = 88.dp

/**
 * suqi8/coui 导航项数据模型，供 [Coui2NavigationBar] 与 [Coui2NavigationRail] 共用。
 */
@Immutable
internal data class Coui2NavItem(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon,
    val badge: Int? = null,
)

/**
 * 新版 COUI 底栏（suqi8/coui 2.1.0）：
 * - [BottomBarForm.DOCKED]：使用 [io.github.suqi8.coui.kmp.basic.NavigationBar]；
 * - [BottomBarForm.FLOATING]：通过 [rememberMaterialBackdropProvider] 桥接 [LocalMaterialBackdrop]
 *   后交给 [FloatingNavigationBar]，在支持的设备上自动呈现实时材质模糊，否则回落不透明胶囊。
 */
@Composable
internal fun Coui2NavigationBar(
    items: List<Coui2NavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    tokens: GlassBottomBarTokens,
    backdrop: Backdrop? = null,
    modifier: Modifier = Modifier,
) {
    when (tokens.form) {
        BottomBarForm.DOCKED -> {
            Coui2LibNavigationBar(modifier = modifier) {
                items.forEachIndexed { index, item ->
                    val selected = index == selectedIndex
                    Coui2LibNavigationBarItem(
                        selected = selected,
                        onClick = { onSelect(index) },
                        icon = if (selected) item.selectedIcon else item.icon,
                        label = item.label,
                        badge = item.badge,
                    )
                }
            }
        }

        BottomBarForm.FLOATING -> {
            val backdropProvider = rememberMaterialBackdropProvider(backdrop)
            CompositionLocalProvider(LocalMaterialBackdrop provides backdropProvider) {
                FloatingNavigationBar(modifier = modifier) {
                    items.forEachIndexed { index, item ->
                        val selected = index == selectedIndex
                        FloatingNavigationBarItem(
                            selected = selected,
                            onClick = { onSelect(index) },
                            icon = if (selected) item.selectedIcon else item.icon,
                            label = item.label,
                            badge = item.badge,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 新版 COUI 宽屏侧栏（suqi8/coui 2.1.0）：固定 [CouiRailWidth] 贴左，接入系统栏与刘海避让。
 */
@Composable
internal fun Coui2NavigationRail(
    items: List<Coui2NavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    header: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Coui2LibNavigationRail(
        modifier = modifier,
        header = header,
        minWidth = CouiRailWidth,
    ) {
        items.forEachIndexed { index, item ->
            val selected = index == selectedIndex
            Coui2LibNavigationRailItem(
                selected = selected,
                onClick = { onSelect(index) },
                icon = if (selected) item.selectedIcon else item.icon,
                label = item.label,
                badge = item.badge,
            )
        }
    }
}

/**
 * 是否交给库的浮动材质导航栏渲染。
 *
 * 只有「普通档 + 悬浮形态 + API 34 及以上」这一种组合保持库渲染：偏大档要靠 token 放大条高、
 * 图标与字号，贴底档要通栏与上圆角，低版本没有原厂浮动材质，三者都只能按 token 自绘。
 * sdkInt 由调用方传入，纯函数在 JVM 单测里可覆盖全部组合。
 */
internal enum class CouiNavBarMode {
    LIBRARY_FLOATING,
    LEGACY_FLOATING,
    MIUIX_DOCKED,
}

internal fun couiNavBarMode(tokens: GlassBottomBarTokens, sdkInt: Int): CouiNavBarMode = when {
    tokens.form == BottomBarForm.DOCKED -> CouiNavBarMode.MIUIX_DOCKED
    sdkInt >= CouiLibraryNavBarMinSdk && tokens.size == BottomBarSize.STANDARD -> CouiNavBarMode.LIBRARY_FLOATING
    else -> CouiNavBarMode.LEGACY_FLOATING
}

internal fun couiUsesLibraryNavBar(tokens: GlassBottomBarTokens, sdkInt: Int): Boolean =
    couiNavBarMode(tokens, sdkInt) == CouiNavBarMode.LIBRARY_FLOATING

/** 自绘底栏的宽度上限：复算库的 maximumMenuWidth + 两侧圆键留白，换实现时条宽不跳变。 */
internal fun couiPlainNavMaxWidth(itemCount: Int): Dp =
    24.dp + CouiNavItemWidth * itemCount +
        CouiNavItemSpacing * (itemCount - 1).coerceAtLeast(0) + CouiNavSideSurfaceWidth

/**
 * 拖动预选的释放判定，语义与库 NavigationBar 的 UP 分发一致：
 * 释放点既不是按下项也不是预览项时切到释放点；否则预览项与待定项一致即切过去；
 * 按下即抬起的普通点击再补一次同点判定。被取消的手势不产生切换。
 */
internal fun couiPlainNavReleaseTarget(
    preview: Int,
    pending: Int?,
    down: Int?,
    up: Int?,
    cancelled: Boolean,
): Int? {
    if (!cancelled && up != null && up != down && up != preview) return up
    if (pending != null && pending == preview) return pending
    if (!cancelled && up != null && up == down && up != preview) return up
    return null
}

/** COUI FAB：基于 suqi8/coui [FloatingActionButton]，自动读取 [LocalMaterialBackdrop]。 */
@Composable
fun CouiFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scheme = COUITheme.colorScheme
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = scheme.primary,
        contentColor = scheme.onPrimary,
    ) {
        CompositionLocalProvider(LocalCouiFallbackContentColor provides LocalContentColor.current) {
            content()
        }
    }
}

/**
 * FAB 的图标重载：内容只有一枚图标时直接传图标，页面不必再包一层 Box。
 * [rotationDegrees] 供刷新、随机等需要状态感的动作使用。
 */
@Composable
fun CouiFab(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    rotationDegrees: Float = 0f,
) {
    CouiFab(onClick = onClick, modifier = modifier) {
        Coui2Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier
                .size(22.dp)
                .graphicsLayer { rotationZ = rotationDegrees },
        )
    }
}
