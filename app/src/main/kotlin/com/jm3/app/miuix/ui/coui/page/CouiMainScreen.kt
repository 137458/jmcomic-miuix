// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.page

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.itos.coui.navigation.CouiNavigationItem
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.ui.BrowseMode
import com.jm3.app.miuix.ui.GlassSnackbarHost
import com.jm3.app.miuix.ui.coui.component.CouiNavigationBar
import com.jm3.app.miuix.ui.coui.component.CouiNavigationRail
import com.jm3.app.miuix.ui.coui.component.LocalCouiBottomBar
import com.jm3.app.miuix.ui.coui.icon.CouiIcons
import com.jm3.app.miuix.ui.coui.theme.CouiMotion
import com.jm3.app.miuix.ui.coui.theme.CouiTheme
import com.jm3.app.miuix.ui.glass.GlassBottomBarTokens
import com.jm3.app.miuix.ui.glass.LocalFloatingBarContentPadding
import com.jm3.app.miuix.ui.glass.resolveBottomBarTokens
import com.jm3.app.miuix.ui.rememberWindowDpSize
import com.jm3.app.miuix.ui.toLayoutDpInt
import com.jm3.core.session.UnreadBadge
import com.jm3.core.settings.BottomBarPreferencePolicy
import com.jm3.core.settings.WideScreenNavigation
import top.yukonga.miuix.kmp.basic.SnackbarHostState

private enum class CouiMainTab(val label: String) {
    HOME("首页"),
    CATEGORY("分类"),
    RANK("榜单"),
    MINE("我的"),
}

/**
 * 底栏净空：底栏模式按 token 的净空值让出底部空间，侧栏模式不占底部空间。
 *
 * 这是 [LocalFloatingBarContentPadding] 的唯一来源；玻璃提示条宿主内部会再扣掉自身底边距，
 * 因此这里不能再叠加任何经验常量，否则提示条会被抬两次。
 */
internal fun couiBottomBarClearance(useRail: Boolean, tokens: GlassBottomBarTokens): Dp =
    if (useRail) 0.dp else tokens.contentPadding

/**
 * COUI 主框架：只负责皮肤、页签转场与导航容器装配。
 *
 * 每个页签都是独立的 COUI 页面，各自持有 Scaffold（顶栏、正文、FAB），
 * 底栏由这里装配后经 [LocalCouiBottomBar] 下发，页面在自身 Scaffold 的 bottomBar 槽位取用，
 * 使得浮动材质始终采样当前页正文，而不是跨页共享一层固定的背景。
 *
 * 宽屏选侧栏时改为左侧 88dp 侧栏 + 右侧正文：正文横向由 Row 的剩余空间接管，
 * 且不下发底栏槽位，页面自然不再渲染底栏。
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
        // 登录态变化后重算未读；登出时 refresh 内部会归零
        LaunchedEffect(user?.uid) { unreadBadge.refresh(user != null) }

        // 与全 app 断点同一取整方式：599.6dp 不能被四舍五入成 600 提前进入平板布局
        val widthDp = rememberWindowDpSize().width.toLayoutDpInt()
        // 宽屏导航偏好与底栏 token 同源：600dp 以下设置项不可见，布局一律按底栏渲染；
        // 大屏未设置时默认底栏，用户显式选侧栏才切成侧栏。
        val wideScreenNavigation = remember(deps.preferences.wideScreenNavigation, widthDp) {
            BottomBarPreferencePolicy.resolveActiveWideScreenNavigation(
                deps.preferences.wideScreenNavigation,
                widthDp,
            )
        }
        // 尺寸档与形态档在组合期一次性解析成 token，切换偏好只重建这份数值，不进入测量热路径
        val tokens = remember(deps.preferences.bottomBarSize, deps.preferences.bottomBarForm, widthDp) {
            resolveBottomBarTokens(
                explicitSize = deps.preferences.bottomBarSize,
                explicitForm = deps.preferences.bottomBarForm,
                widthDp = widthDp,
            )
        }
        val useRail = wideScreenNavigation == WideScreenNavigation.RAIL

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
                tokens = tokens,
            )
        }
        // 侧栏模式不向页面下发底栏槽位：页面的 Scaffold 取到 null，bottomBar 槽位自然留空
        val barSlot: (@Composable () -> Unit)? = if (useRail) null else bottomBar
        val pageState = rememberSaveableStateHolder()

        CompositionLocalProvider(
            LocalCouiBottomBar provides barSlot,
            LocalFloatingBarContentPadding provides couiBottomBarClearance(useRail, tokens),
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                if (useRail) {
                    // 侧栏固定 88dp 贴左，正文由 Row 的剩余空间接管，不再叠加 start padding
                    CouiNavigationRail(
                        items = navItems,
                        selectedIndex = tab,
                        onSelect = { tab = it },
                        tokens = tokens,
                    )
                }
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
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
                    // 提示条宿主按 LocalFloatingBarContentPadding 自行上抬，这里只定位到底部居中
                    GlassSnackbarHost(
                        state = snackbarHostState,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
    }
}
