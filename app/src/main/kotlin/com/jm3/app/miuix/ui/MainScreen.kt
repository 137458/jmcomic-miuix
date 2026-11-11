package com.jm3.app.miuix.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.ui.glass.IosLiquidGlassNavigationBar
import com.jm3.app.miuix.ui.glass.LocalFloatingBarContentPadding
import com.jm3.app.miuix.ui.glass.LocalGlassBottomBarTokens
import com.jm3.app.miuix.ui.glass.resolveBottomBarTokens
import com.jm3.app.miuix.ui.glass.layerBackdropSource
import com.jm3.app.miuix.ui.glass.rememberBlurBackdrop
import com.jm3.core.settings.BottomBarForm
import com.jm3.core.settings.BottomBarPreferencePolicy
import com.jm3.core.settings.WideScreenNavigation

import com.jm3.core.download.activeDownloadCount
import com.jm3.core.session.UnreadBadge
import top.yukonga.miuix.kmp.basic.Badge
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.NavigationItem
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

private enum class MainTab(val label: String) {
    HOME("首页"),
    CATEGORY("分类"),
    RANK("榜单"),
    MINE("我的"),
}

private fun tabIcon(tab: MainTab): ImageVector = when (tab) {
    MainTab.HOME -> MiuixIcons.Home
    MainTab.CATEGORY -> MiuixIcons.GridView
    MainTab.RANK -> MiuixIcons.Playlist
    MainTab.MINE -> MiuixIcons.Contacts
}

@Composable
fun MainScreen(
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
    var tab by rememberSaveable { mutableIntStateOf(0) }
    // 启动即恢复下载队列（中断任务收敛为暂停态），底栏角标才能反映在途任务
    LaunchedEffect(Unit) { deps.downloadManager.ensureStarted() }
    // 下载队列进度在“我的”页可见，底栏用角标提示在途任务；在途判定只由 :core 的状态语义给出
    val downloads by deps.downloadManager.queue.updates.collectAsState()
    val activeDownloads = downloads.activeDownloadCount()
    val user by deps.account.user.collectAsState()
    val unread by unreadBadge.count.collectAsState()
    // 登录态变化后重算未读；登出时 refresh 内部会归零
    LaunchedEffect(user?.uid) { unreadBadge.refresh(user != null) }

    // 未读优先显示数字；无未读时用圆点提示在途下载
    fun badgeFor(item: MainTab): (@Composable () -> Unit)? = when {
        item == MainTab.MINE && unread > 0 -> {
            { Badge { Text(text = if (unread > 99) "99+" else unread.toString()) } }
        }
        item == MainTab.MINE && activeDownloads > 0 -> {
            { Badge() }
        }
        else -> null
    }

    // 弹窗层必须压在悬浮底栏之上：Miuix 的 Overlay* 默认注册到最外层 Scaffold 的弹窗层，
    // 而底栏画在 Scaffold 之后。这里套一层只承载弹窗层的 Scaffold，容器与内边距都不参与布局。
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { _ ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val widthDp = maxWidth.toLayoutDpInt()
            // 宽屏导航偏好与底栏 token 同源：600dp 以下设置项不可见，布局一律按底栏渲染；
            // 大屏未设置时默认底栏，用户显式选侧栏才切成 NavigationRail。
            val wideScreenNavigation = remember(deps.preferences.wideScreenNavigation, widthDp) {
                BottomBarPreferencePolicy.resolveActiveWideScreenNavigation(
                    deps.preferences.wideScreenNavigation,
                    widthDp,
                )
            }
            // 底栏尺寸/形态档在组合期一次性解析：未显式设置时按窗口宽度断点取默认值，
            // 切换偏好只会重建这份 token，不进入滚动或测量热路径。
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
            // 侧栏只占横向空间，底部净空留给系统栏；底栏（含宽屏）按 token 扣除
            val useRail = wideScreenNavigation == WideScreenNavigation.RAIL
            // 底栏玻璃采样源：设备不支持 RuntimeShader 时返回 null，底栏自动降级为实色
            val backdrop = rememberBlurBackdrop()
            // 主框架展示底栏时给列表提供底部净空，内容其余部分穿透底栏持续被采样
            CompositionLocalProvider(
                LocalFloatingBarContentPadding provides if (useRail) 0.dp else barTokens.contentPadding,
                LocalGlassBottomBarTokens provides barTokens,
            ) {
                Scaffold(
                    snackbarHost = { GlassSnackbarHost(state = snackbarHostState, backdrop = backdrop) },
                ) { innerPadding ->
                    // 顶栏玻璃要采样状态栏背后的内容：这里只让出手势条与侧边安全区，状态栏高度
                    // 不由外层让位，改由各页 TopAppBar 自己补（对应的 inset 也不能消费）。
                    // 否则内容整体下移一个状态栏高度，状态栏那条只剩外层 Surface 实色，玻璃会出现硬边。
                    val layoutDirection = LocalLayoutDirection.current
                    val tabContentPadding = PaddingValues(
                        start = innerPadding.calculateStartPadding(layoutDirection),
                        end = innerPadding.calculateEndPadding(layoutDirection),
                        bottom = innerPadding.calculateBottomPadding(),
                    )
                    // 消费父 Scaffold 的非顶部内边距，避免各 tab 自己的 Scaffold 重复应用系统栏 insets。
                    Row(
                        modifier = Modifier.fillMaxSize()
                            .padding(tabContentPadding)
                            .consumeWindowInsets(tabContentPadding),
                    ) {
                        if (useRail) {
                            // 可展开/收起侧栏：展开宽度由库的连续动画驱动，收起时只留图标
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
                                MainTab.entries.forEachIndexed { index, item ->
                                    NavigationRailItem(
                                        selected = tab == index,
                                        onClick = { tab = index },
                                        icon = tabIcon(item),
                                        label = item.label,
                                        badge = badgeFor(item),
                                    )
                                }
                            }
                        }
                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            // 内容层同时是玻璃采样源；列表经 contentPadding 让出底栏，其余内容可滚到胶囊下方
                            // 各页的 rememberSaveable 状态（例如列表滚动位置）会随组合销毁在切页时丢失，
                            // 先按 tab 交给 state holder 保管，切回来时再还原。
                            val saveableStateHolder = rememberSaveableStateHolder()
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .layerBackdropSource(backdrop),
                            ) {
                                AnimatedContent(
                                    targetState = tab,
                                    transitionSpec = {
                                        fadeIn(animationSpec = tween(180, easing = LinearEasing)) +
                                            scaleIn(initialScale = 0.98f, animationSpec = tween(220, easing = FastOutSlowInEasing)) togetherWith
                                            fadeOut(animationSpec = tween(140, easing = LinearEasing))
                                    },
                                    label = "MainTabTransition",
                                    modifier = Modifier.fillMaxSize(),
                                ) { currentTab ->
                                    // 键直接取 targetState 的 tab 索引，两处同源；转场期间新旧两页并存时键也唯一
                                    saveableStateHolder.SaveableStateProvider(currentTab) {
                                        when (currentTab) {
                                            0 -> HomeScreen(
                                                deps = deps,
                                                onOpenAlbum = onOpenAlbum,
                                                onOpenWeek = onOpenWeek,
                                                onOpenLogin = onOpenLogin,
                                            )
                                            1 -> BrowseScreen(
                                                deps = deps,
                                                mode = BrowseMode.CATEGORY,
                                                onOpenAlbum = onOpenAlbum,
                                                onOpenLogin = onOpenLogin,
                                            )
                                            2 -> BrowseScreen(
                                                deps = deps,
                                                mode = BrowseMode.RANK,
                                                onOpenAlbum = onOpenAlbum,
                                                onOpenLogin = onOpenLogin,
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
                        }
                    }
                }
                if (!useRail) {
                    if (barTokens.form == BottomBarForm.DOCKED) {
                        MiuixDockedNavigationBar(
                            items = MainTab.entries.map { MiuixDockedNavItem(label = it.label, icon = tabIcon(it)) },
                            selectedIndex = tab,
                            onSelect = { tab = it },
                            badge = { index -> MainTab.entries.getOrNull(index)?.let(::badgeFor) },
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    } else {
                        // 液态玻璃胶囊底栏：浮在内容之上以采样页面背景，宽度由内容与官方规范上限决定。
                        // 必须与内容同处 token provider：三层渲染与图标字号都读 LocalGlassBottomBarTokens，
                        // 放到 provider 之外只会永远拿到默认 StandardFloating，尺寸/形态档全部失效。
                        IosLiquidGlassNavigationBar(
                            items = MainTab.entries.map { NavigationItem(label = it.label, icon = tabIcon(it)) },
                            selectedIndex = tab,
                            onItemClick = { tab = it },
                            backdrop = backdrop,
                            modifier = Modifier.align(Alignment.BottomCenter),
                            badge = { index -> MainTab.entries.getOrNull(index)?.let(::badgeFor) },
                        )
                    }
                }
            }
        }
    }
}
