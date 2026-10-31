package com.jm3.app.miuix.ui

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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import com.jm3.app.miuix.Jm3Graph
import com.jm3.app.miuix.data.AccountManager
import com.jm3.app.miuix.ui.glass.IosLiquidGlassNavigationBar
import com.jm3.app.miuix.ui.glass.LocalFloatingBarContentPadding
import com.jm3.app.miuix.ui.glass.layerBackdropSource
import com.jm3.app.miuix.ui.glass.rememberBlurBackdrop
import com.jm3.core.Jm3Gateway
import com.jm3.core.download.DownloadStatus
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

/** 宽屏阈值：≥600dp 视为平板/折叠展开/桌面，主框架切为可折叠侧栏。 */
private val WIDE_SCREEN_WIDTH = 600.dp

/**
 * 底栏内容净空：列表末项需让出的高度（胶囊 64dp + 底边距 + 呼吸空间），
 * 经 GlassScaffold 叠加进 bottom contentPadding；内容其余部分穿透底栏滚动。
 */
private val FLOATING_BAR_CONTENT_PADDING = 100.dp

private fun tabIcon(tab: MainTab): ImageVector = when (tab) {
    MainTab.HOME -> MiuixIcons.Home
    MainTab.CATEGORY -> MiuixIcons.GridView
    MainTab.RANK -> MiuixIcons.Playlist
    MainTab.MINE -> MiuixIcons.Contacts
}

@Composable
fun MainScreen(
    gateway: Jm3Gateway,
    account: AccountManager,
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
    LaunchedEffect(Unit) { Jm3Graph.downloadManager.ensureStarted() }
    // 下载队列进度在“我的”页可见，底栏用角标提示在途任务
    val downloads by Jm3Graph.downloadManager.queue.updates.collectAsState()
    val activeDownloads = downloads.values.count {
        it.status == DownloadStatus.RUNNING || it.status == DownloadStatus.QUEUED
    }
    val user by account.user.collectAsState()
    val unread by Jm3Graph.unreadNotifications.collectAsState()
    // 登录态变化后重算未读；登出时 refresh 内部会归零
    LaunchedEffect(user?.uid) { Jm3Graph.refreshUnreadNotifications() }

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
            // 宽屏改用 NavigationRail：底栏让位给侧栏，列表获得更宽的横向空间
            val wideScreen = maxWidth >= WIDE_SCREEN_WIDTH
            // 底栏玻璃采样源：设备不支持 RuntimeShader 时返回 null，底栏自动降级为实色
            val backdrop = rememberBlurBackdrop()
            // 主框架展示悬浮底栏时给列表提供底部净空，内容其余部分穿透底栏持续被采样
            CompositionLocalProvider(
                LocalFloatingBarContentPadding provides if (wideScreen) 0.dp else FLOATING_BAR_CONTENT_PADDING,
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
                        if (wideScreen) {
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
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .layerBackdropSource(backdrop),
                            ) {
                                when (tab) {
                                    0 -> HomeScreen(
                                        gateway = gateway,
                                        onOpenAlbum = onOpenAlbum,
                                        onOpenWeek = onOpenWeek,
                                    )
                                    1 -> BrowseScreen(
                                        mode = BrowseMode.CATEGORY,
                                        gateway = gateway,
                                        onOpenAlbum = onOpenAlbum,
                                    )
                                    2 -> BrowseScreen(
                                        mode = BrowseMode.RANK,
                                        gateway = gateway,
                                        onOpenAlbum = onOpenAlbum,
                                    )
                                    else -> MineScreen(
                                        account = account,
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
            // 液态玻璃胶囊底栏：浮在内容之上以采样页面背景，宽度由内容与官方规范上限决定
            if (!wideScreen) {
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
