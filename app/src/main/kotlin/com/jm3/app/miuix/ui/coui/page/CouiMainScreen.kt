// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.page

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.itos.coui.navigation.CouiNavigationItem
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.ui.BrowseMode
import com.jm3.app.miuix.ui.GlassSnackbarHost
import com.jm3.app.miuix.ui.coui.component.CouiNavigationBar
import com.jm3.app.miuix.ui.coui.component.LocalCouiBottomBar
import com.jm3.app.miuix.ui.coui.icon.CouiIcons
import com.jm3.app.miuix.ui.coui.theme.CouiMotion
import com.jm3.app.miuix.ui.coui.theme.CouiTheme
import com.jm3.app.miuix.ui.glass.LocalFloatingBarContentPadding
import com.jm3.core.session.UnreadBadge
import top.yukonga.miuix.kmp.basic.SnackbarHostState

private enum class CouiMainTab(val label: String) {
    HOME("首页"),
    CATEGORY("分类"),
    RANK("榜单"),
    MINE("我的"),
}

/** 底栏净空：COUI NavigationBar 实测 56dp 主体 + 上下 16dp 外边距。 */
private val CouiBottomBarClearance = 96.dp

/**
 * COUI 主框架：只负责皮肤、页签转场与底栏装配。
 *
 * 每个页签都是独立的 COUI 页面，各自持有 Scaffold（顶栏、正文、FAB），
 * 底栏由这里装配后经 [LocalCouiBottomBar] 下发，页面在自身 Scaffold 的 bottomBar 槽位取用，
 * 使得浮动材质始终采样当前页正文，而不是跨页共享一层固定的背景。
 *
 * 页签状态、滚动位置与搜索态全部留在各自页面内，切页只做淡入淡出，返回时不重建。
 */
@Composable
fun CouiMainScreen(
    deps: ScreenDeps,
    unreadBadge: UnreadBadge,
    snackbarHostState: SnackbarHostState,
    onOpenAlbum: (String) -> Unit,
    onOpenLogin: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenShelf: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenWeek: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenTracking: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenWatchHistory: () -> Unit,
    onOpenForum: () -> Unit,
    onOpenNovel: () -> Unit,
    onOpenLibrary: () -> Unit,
) {
    CouiTheme {
        var tab by rememberSaveable { mutableIntStateOf(0) }
        LaunchedEffect(Unit) { deps.downloadManager.ensureStarted() }
        val user by deps.account.user.collectAsState()
        LaunchedEffect(user?.uid) { unreadBadge.refresh(user != null) }
        unreadBadge.count.collectAsState()

        val navItems = CouiMainTab.entries.map { item ->
            CouiNavigationItem(
                title = item.label,
                icon = when (item) {
                    CouiMainTab.HOME -> CouiIcons.Home
                    CouiMainTab.CATEGORY -> CouiIcons.Category
                    CouiMainTab.RANK -> CouiIcons.Rank
                    CouiMainTab.MINE -> CouiIcons.Mine
                },
            )
        }
        val bottomBar: @Composable () -> Unit = {
            CouiNavigationBar(
                items = navItems,
                selectedIndex = tab,
                onSelect = { tab = it },
            )
        }
        val pageState = rememberSaveableStateHolder()

        CompositionLocalProvider(
            LocalCouiBottomBar provides bottomBar,
            LocalFloatingBarContentPadding provides CouiBottomBarClearance,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = {
                        fadeIn(tween(CouiMotion.TabCrossfadeMs)) togetherWith
                            fadeOut(tween(CouiMotion.TabCrossfadeMs))
                    },
                    label = "couiMainTab",
                    modifier = Modifier.fillMaxSize(),
                ) { currentTab ->
                    pageState.SaveableStateProvider(currentTab) {
                        when (currentTab) {
                            0 -> CouiHomeScreen(
                                deps = deps,
                                onOpenAlbum = onOpenAlbum,
                                onOpenWeek = onOpenWeek,
                            )
                            1 -> CouiBrowseScreen(
                                deps = deps,
                                mode = BrowseMode.CATEGORY,
                                onOpenAlbum = onOpenAlbum,
                            )
                            2 -> CouiBrowseScreen(
                                deps = deps,
                                mode = BrowseMode.RANK,
                                onOpenAlbum = onOpenAlbum,
                            )
                            else -> CouiMineScreen(
                                deps = deps,
                                unreadBadge = unreadBadge,
                                snackbarHostState = snackbarHostState,
                                onOpenLogin = onOpenLogin,
                                onOpenFavorites = onOpenFavorites,
                                onOpenHistory = onOpenHistory,
                                onOpenShelf = onOpenShelf,
                                onOpenSettings = onOpenSettings,
                                onOpenNotifications = onOpenNotifications,
                                onOpenTracking = onOpenTracking,
                                onOpenProfile = onOpenProfile,
                                onOpenTasks = onOpenTasks,
                                onOpenWatchHistory = onOpenWatchHistory,
                                onOpenForum = onOpenForum,
                                onOpenNovel = onOpenNovel,
                                onOpenLibrary = onOpenLibrary,
                            )
                        }
                    }
                }
                GlassSnackbarHost(
                    state = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = CouiBottomBarClearance),
                    backdrop = null,
                )
            }
        }
    }
}
