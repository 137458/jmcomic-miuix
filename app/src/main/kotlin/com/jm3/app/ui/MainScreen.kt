package com.jm3.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.jm3.core.Jm3Gateway
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Playlist

private enum class MainTab(val label: String) {
    HOME("首页"),
    CATEGORY("分类"),
    RANK("榜单"),
}

@Composable
fun MainScreen(gateway: Jm3Gateway, onOpenAlbum: (String) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEachIndexed { index, t ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = when (t) {
                            MainTab.HOME -> MiuixIcons.Home
                            MainTab.CATEGORY -> MiuixIcons.GridView
                            MainTab.RANK -> MiuixIcons.Playlist
                        },
                        label = t.label,
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (tab) {
                0 -> HomeScreen(gateway = gateway, onOpenAlbum = onOpenAlbum)
                1 -> BrowseScreen(mode = BrowseMode.CATEGORY, gateway = gateway, onOpenAlbum = onOpenAlbum)
                else -> BrowseScreen(mode = BrowseMode.RANK, gateway = gateway, onOpenAlbum = onOpenAlbum)
            }
        }
    }
}
