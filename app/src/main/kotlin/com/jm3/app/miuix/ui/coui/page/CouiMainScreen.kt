// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.page

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.ui.BrowseMode
import com.jm3.app.miuix.ui.BrowseScreen
import com.jm3.app.miuix.ui.GlassSnackbarHost
import com.jm3.app.miuix.ui.HomeScreen
import com.jm3.app.miuix.ui.MineScreen
import com.jm3.app.miuix.ui.coui.component.CouiBadge
import com.jm3.app.miuix.ui.coui.component.CouiBottomBar
import com.jm3.app.miuix.ui.coui.component.CouiNavItem
import com.jm3.app.miuix.ui.coui.component.CouiSurfaceBackground
import com.jm3.app.miuix.ui.coui.component.couiBackdropSource
import com.jm3.app.miuix.ui.coui.component.rememberCouiBlurBackdrop
import com.jm3.app.miuix.ui.coui.theme.CouiMotion
import com.jm3.app.miuix.ui.coui.theme.CouiTheme
import com.jm3.app.miuix.ui.glass.LocalFloatingBarContentPadding
import com.jm3.app.miuix.ui.glass.LocalGlassBottomBarTokens
import com.jm3.app.miuix.ui.glass.resolveBottomBarTokens
import com.jm3.core.download.activeDownloadCount
import com.jm3.core.session.UnreadBadge
import com.jm3.core.settings.BottomBarPreferencePolicy
import com.jm3.core.settings.WideScreenNavigation
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Badge
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.NavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.rememberNavigationRailState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Playlist

private enum class CouiMainTab(val label: String) {
    HOME("首页"),
    CATEGORY("分类"),
    RANK("榜单"),
    MINE("我的"),
}

private fun couiTabIcon(tab: CouiMainTab): ImageVector = when (tab) {
    CouiMainTab.HOME -> MiuixIcons.Home
    CouiMainTab.CATEGORY -> MiuixIcons.GridView
    CouiMainTab.RANK -> MiuixIcons.Playlist
    CouiMainTab.MINE -> MiuixIcons.Contacts
}

/**
 * COUI 主框架：背景、底栏、页签转场与宽屏侧栏。
 *
 * 过渡期只有首页是 COUI 页；分类、榜单、我的继续挂现有 HyperOS 页，
 * 因此这里提供 [LocalFloatingBarContentPadding]，让下层页知道底栏占位，内容不会被底栏压住。
 * 宽屏侧栏仍用 Miuix NavigationRail，属于过渡期混合，不在本轮样板页范围内。
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
        val downloads by deps.downloadManager.queue.updates.collectAsState()
        val activeDownloads = downloads.activeDownloadCount()
        val user by deps.account.user.collectAsState()
        val unread by unreadBadge.count.collectAsState()
        LaunchedEffect(user?.uid) { unreadBadge.refresh(user != null) }
        val backdrop = rememberCouiBlurBackdrop()
        val navBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

        fun couiBadgeFor(index: Int): CouiBadge? = when {
            index == CouiMainTab.MINE.ordinal && unread > 0 -> CouiBadge(count = unread)
            index == CouiMainTab.MINE.ordinal && activeDownloads > 0 -> CouiBadge(dot = true)
            else -> null
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { GlassSnackbarHost(state = snackbarHostState, backdrop = backdrop) },
        ) { _ ->
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val widthDp = maxWidth.value.roundToInt()
                val wideScreenNavigation = remember(deps.preferences.wideScreenNavigation, widthDp) {
                    BottomBarPreferencePolicy.resolveActiveWideScreenNavigation(
                        deps.preferences.wideScreenNavigation,
                        widthDp,
                    )
                }
                val useRail = wideScreenNavigation == WideScreenNavigation.RAIL
                // 与 HyperOS 主框架同源解析尺寸/形态档：切换偏好只重建 token，底栏几何全部由它给出
                val barTokens = remember(
                    deps.preferences.bottomBarSize,
                    deps.preferences.bottomBarForm,
                    widthDp,
                ) {
                    resolveBottomBarTokens(
                        explicitSize = deps.preferences.bottomBarSize,
                        explicitForm = deps.preferences.bottomBarForm,
                        widthDp = widthDp,
                    )
                }
                // 净空按 token 推导：条高 + 悬浮档与导航条之间的 8dp 间隙 + 4dp 呼吸位 + 导航条 inset
                val floatingBarClearance = barTokens.height +
                    (if (barTokens.floating) 8.dp else 0.dp) +
                    navBarHeight +
                    4.dp
                CompositionLocalProvider(
                    LocalFloatingBarContentPadding provides if (useRail) 0.dp else floatingBarClearance,
                    LocalGlassBottomBarTokens provides barTokens,
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .couiBackdropSource(backdrop),
                        ) {
                            CouiSurfaceBackground(modifier = Modifier.fillMaxSize())
                            val saveableStateHolder = rememberSaveableStateHolder()
                            val tabContent: @Composable (Modifier) -> Unit = { contentModifier ->
                                AnimatedContent(
                                    targetState = tab,
                                    transitionSpec = {
                                        fadeIn(tween(CouiMotion.TabCrossfadeMs)) togetherWith
                                            fadeOut(tween(CouiMotion.TabCrossfadeMs))
                                    },
                                    label = "couiMainTab",
                                    modifier = contentModifier,
                                ) { currentTab ->
                                    saveableStateHolder.SaveableStateProvider(currentTab) {
                                        when (currentTab) {
                                            0 -> CouiHomeScreen(
                                                deps = deps,
                                                onOpenAlbum = onOpenAlbum,
                                                onOpenWeek = onOpenWeek,
                                            )
                                            1 -> BrowseScreen(
                                                deps = deps,
                                                mode = BrowseMode.CATEGORY,
                                                onOpenAlbum = onOpenAlbum,
                                            )
                                            2 -> BrowseScreen(
                                                deps = deps,
                                                mode = BrowseMode.RANK,
                                                onOpenAlbum = onOpenAlbum,
                                            )
                                            else -> MineScreen(
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
                            }
                            if (useRail) {
                                Row(modifier = Modifier.fillMaxSize()) {
                                    val railState = rememberNavigationRailState()
                                    NavigationRail(
                                        state = railState,
                                        header = {
                                            IconButton(
                                                onClick = { railState.toggle() },
                                                minWidth = 48.dp,
                                                minHeight = 48.dp,
                                            ) {
                                                Icon(
                                                    imageVector = if (railState.isExpanded) {
                                                        MiuixIcons.ChevronBackward
                                                    } else {
                                                        MiuixIcons.ChevronForward
                                                    },
                                                    contentDescription = if (railState.isExpanded) "收起侧栏" else "展开侧栏",
                                                )
                                            }
                                        },
                                    ) {
                                        CouiMainTab.entries.forEachIndexed { index, item ->
                                            NavigationRailItem(
                                                selected = tab == index,
                                                onClick = { tab = index },
                                                icon = couiTabIcon(item),
                                                label = item.label,
                                                badge = if (index == CouiMainTab.MINE.ordinal && unread > 0) {
                                                    { Badge { Text(text = if (unread > 99) "99+" else unread.toString()) } }
                                                } else if (index == CouiMainTab.MINE.ordinal && activeDownloads > 0) {
                                                    { Badge() }
                                                } else {
                                                    null
                                                },
                                            )
                                        }
                                    }
                                    tabContent(Modifier.weight(1f).fillMaxHeight())
                                }
                            } else {
                                tabContent(Modifier.fillMaxSize())
                            }
                        }
                        if (!useRail) {
                            CouiBottomBar(
                                items = CouiMainTab.entries.map { CouiNavItem(label = it.label, icon = couiTabIcon(it)) },
                                selectedIndex = tab,
                                onItemClick = { tab = it },
                                modifier = Modifier.align(Alignment.BottomCenter),
                                backdrop = backdrop,
                                badge = { index -> couiBadgeFor(index) },
                            )
                        }
                    }
                }
            }
        }
    }
}
