// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarDefaults
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationBarItemColors
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Immutable
internal data class MiuixDockedNavItem(
    val label: String,
    val icon: ImageVector,
)

/**
 * 双端非悬浮（[com.jm3.core.settings.BottomBarForm.DOCKED]）共享底栏：
 * 直接复用 Miuix 官方 [NavigationBar] 与 [NavigationBarItem]，不再手写贴底横条。
 */
@Composable
internal fun MiuixDockedNavigationBar(
    items: List<MiuixDockedNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MiuixTheme.colorScheme.surface,
    itemColors: NavigationBarItemColors = NavigationBarDefaults.navigationBarItemColors(),
    badge: ((Int) -> (@Composable () -> Unit)?)? = null,
    showDivider: Boolean = true,
    defaultWindowInsetsPadding: Boolean = true,
) {
    NavigationBar(
        modifier = modifier,
        color = color,
        showDivider = showDivider,
        defaultWindowInsetsPadding = defaultWindowInsetsPadding,
    ) {
        items.forEachIndexed { index, item ->
            NavigationBarItem(
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                icon = item.icon,
                label = item.label,
                colors = itemColors,
                badge = badge?.invoke(index),
            )
        }
    }
}
