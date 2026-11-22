// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.page

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.ui.BrowseMode
import com.jm3.app.miuix.ui.GlassSnackbarHost
import com.jm3.app.miuix.ui.coui.component.Coui2NavItem
import com.jm3.app.miuix.ui.coui.component.Coui2NavigationBar
import com.jm3.app.miuix.ui.coui.component.Coui2NavigationRail
import com.jm3.app.miuix.ui.coui.component.CouiRailWidth
import com.jm3.app.miuix.ui.coui.component.rememberPageBackdrop
import com.jm3.app.miuix.ui.coui.icon.CouiIcons
import com.jm3.app.miuix.ui.coui.theme.CouiMotion
import com.jm3.app.miuix.ui.coui.theme.CouiTheme
import com.jm3.app.miuix.ui.glass.GlassBottomBarTokens
import com.jm3.app.miuix.ui.glass.LocalFloatingBarContentPadding
import com.jm3.app.miuix.ui.LocalWideLayout
import com.jm3.app.miuix.ui.mainBottomBarClearanceDp
import com.jm3.app.miuix.ui.mainscreen.MainTab
import com.jm3.app.miuix.ui.mainscreen.mainContentWidthDp
import com.jm3.app.miuix.ui.mainscreen.rememberMainScaffoldState
import com.jm3.app.miuix.ui.mainscreen.resolveMainBarLayout
import com.jm3.app.miuix.ui.railAwareWideLayout
import com.jm3.app.miuix.ui.rememberWindowDpSize
import com.jm3.app.miuix.ui.route.AppRoutes
import com.jm3.app.miuix.ui.toLayoutDpInt
import com.jm3.core.settings.UiSkin
import com.jm3.core.session.UnreadBadge
import io.github.suqi8.coui.kmp.blur.layerBackdrop
import io.github.suqi8.coui.kmp.theme.COUITheme
import top.yukonga.miuix.kmp.basic.SnackbarHostState

/**
 * 底栏净空：底栏模式按 token 的净空值让出底部空间，侧栏模式不占底部空间。
 *
 * 判定收敛在 [mainBottomBarClearanceDp]，与玻璃皮肤的主框架共用同一推导；玻璃提示条宿主内部
 * 会再扣掉自身底边距，因此这里不能再叠加任何经验常量，否则提示条会被抬两次。
 */
internal fun couiBottomBarClearance(useRail: Boolean, tokens: GlassBottomBarTokens): Dp =
    mainBottomBarClearanceDp(useRail, tokens.contentPadding.value).dp

/**
 * COUI 主框架：只负责皮肤、页签转场与导航容器装配。
 *
 * 每个页签都是独立的 COUI 页面，各自持有 Scaffold（顶栏、正文、FAB）；
 * 底栏只在这里装配一份，画在页签转场之外，转场期不会随淡入淡出被组合两次或淡成半透明。
 * 页面按 [LocalFloatingBarContentPadding] 在自身 Scaffold 里留出等高净空，FAB 随 innerPadding 上抬。
 *
 * 宽屏选侧栏时改为左侧 88dp 侧栏 + 右侧正文：正文横向由 Row 的剩余空间接管，且不绘制底栏。
 *
 * 页签状态、滚动位置与搜索态全部留在各自页面内，切页只做淡入淡出，返回时不重建。
 */
@Composable
internal fun CouiMainScreen(
    deps: ScreenDeps,
    unreadBadge: UnreadBadge,
    snackbarHostState: SnackbarHostState,
    nav: AppRoutes,
) {
    CouiTheme {
        // 选页与启动接线（拉起下载队列、登录态变化刷未读）都在共享骨架里
        val state = rememberMainScaffoldState(deps = deps, unreadBadge = unreadBadge)
        val tab = state.tab
        // 与全 app 断点同一取整方式：599.6dp 不能被四舍五入成 600 提前进入平板布局
        val windowSize = rememberWindowDpSize()
        val widthDp = windowSize.width.toLayoutDpInt()
        val heightDp = windowSize.height.toLayoutDpInt()
        // 宽屏导航偏好与底栏 token 同源：600dp 以下设置项不可见，布局一律按底栏渲染；
        // 够宽够高时未设置默认侧栏，矮横屏一律回落底栏。ColorOS 的尺寸档恒为标准档。
        val barLayout = remember(
            deps.wideScreenNavigation,
            deps.bottomBarSize,
            deps.bottomBarForm,
            widthDp,
            heightDp,
        ) {
            resolveMainBarLayout(
                wideScreenNavigation = deps.wideScreenNavigation,
                explicitSize = deps.bottomBarSize,
                explicitForm = deps.bottomBarForm,
                widthDp = widthDp,
                heightDp = heightDp,
                skin = UiSkin.COLOROS,
            )
        }
        val tokens = barLayout.tokens
        val useRail = barLayout.useRail
        // 侧栏占掉 88dp 定宽，正文的限宽留白要按剩下的宽度算，否则内容列整体右偏半个侧栏
        val contentLayout = railAwareWideLayout(
            availableWidthDp = mainContentWidthDp(
                widthDp = widthDp,
                useRail = useRail,
                railWidthDp = CouiRailWidth.value.toInt(),
            ),
        )

        // 导航项只随枚举变化：不 remember 会让主框架每次重组都新建列表，连带四页整树重组
        val navItems = remember {
            MainTab.entries.map { item ->
                Coui2NavItem(
                    label = item.label,
                    // 分页图标属于皮肤视觉，不进共享骨架
                    icon = when (item) {
                        MainTab.HOME -> CouiIcons.Home
                        MainTab.CATEGORY -> CouiIcons.Category
                        MainTab.RANK -> CouiIcons.Rank
                        MainTab.MINE -> CouiIcons.Mine
                    },
                )
            }
        }
        // 底栏只画一份：转场期不再随两个页面各组合一次，浮动材质由这一份 backdrop 采样正文
        val backdrop = rememberPageBackdrop()
        val pageState = rememberSaveableStateHolder()

        CompositionLocalProvider(
            LocalFloatingBarContentPadding provides couiBottomBarClearance(useRail, tokens),
            LocalWideLayout provides contentLayout,
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                if (useRail) {
                    // 侧栏固定 88dp 贴左，正文由 Row 的剩余空间接管，不再叠加 start padding
                    Coui2NavigationRail(
                        items = navItems,
                        selectedIndex = tab,
                        onSelect = state::selectTab,
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
                        // 只录制页签正文：底栏在转场之外绘制，底色垫在采样层保证 Backdrop 不录制透明像素
                        modifier = Modifier
                            .fillMaxSize()
                            .background(COUITheme.colorScheme.backgroundWithCard)
                            .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier),
                    ) { currentTab ->
                        pageState.SaveableStateProvider(currentTab) {
                            when (currentTab) {
                                0 -> CouiHomeScreen(
                                    deps = deps,
                                    onOpenAlbum = nav::album,
                                    onOpenWeek = nav::week,
                                    onOpenRanking = { state.selectTab(MainTab.RANK.ordinal) },
                                    onOpenLogin = nav::login,
                                )
                                1 -> CouiBrowseScreen(
                                    deps = deps,
                                    mode = BrowseMode.CATEGORY,
                                    onOpenAlbum = nav::album,
                                    onOpenLogin = nav::login,
                                )
                                2 -> CouiBrowseScreen(
                                    deps = deps,
                                    mode = BrowseMode.RANK,
                                    onOpenAlbum = nav::album,
                                    onOpenLogin = nav::login,
                                )
                                else -> CouiMineScreen(
                                    deps = deps,
                                    unreadBadge = unreadBadge,
                                    snackbarHostState = snackbarHostState,
                                    onOpenLogin = nav::login,
                                    onOpenFavorites = nav::favorites,
                                    onOpenHistory = nav::history,
                                    onOpenShelf = nav::shelf,
                                    onOpenSettings = nav::settings,
                                    onOpenNotifications = nav::notifications,
                                    onOpenTracking = nav::tracking,
                                    onOpenProfile = nav::profile,
                                    onOpenTasks = nav::tasks,
                                    onOpenWatchHistory = nav::watchHistory,
                                    onOpenForum = nav::forum,
                                    onOpenNovel = nav::novelList,
                                    onOpenLibrary = nav::library,
                                )
                            }
                        }
                    }
                    // 底栏画在页签转场之外：只此一份，选中态在内部读 tab，切页只重组底栏自身
                    if (!useRail) {
                        Coui2NavigationBar(
                            items = navItems,
                            selectedIndex = tab,
                            onSelect = state::selectTab,
                            tokens = tokens,
                            backdrop = backdrop,
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
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
