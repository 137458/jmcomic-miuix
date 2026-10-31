package com.jm3.app.miuix.ui.glass

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.TabRowDefaults

/**
 * 玻璃顶栏里的页签条。[TabRow] 默认自带不透明的 surface 底色，铺在渐变模糊之上会把模糊整块盖掉，
 * 页签之间只剩一片纯色；这里只把底色置空，让模糊层透上来，选中胶囊仍用主题的 surfaceContainer。
 */
@Composable
fun GlassTabRow(
    tabs: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    TabRow(
        tabs = tabs,
        selectedTabIndex = selectedTabIndex,
        onTabSelected = onTabSelected,
        modifier = modifier,
        colors = TabRowDefaults.tabRowColors(backgroundColor = Color.Transparent),
    )
}
