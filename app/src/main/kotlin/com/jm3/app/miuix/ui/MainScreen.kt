package com.jm3.app.miuix.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.jm3.app.miuix.Jm3Graph
import com.jm3.app.miuix.data.AccountManager
import com.jm3.app.miuix.ui.glass.IosLiquidGlassNavigationBar
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
import top.yukonga.miuix.kmp.basic.SnackbarHost
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

/** 悬浮底栏占位高度：让列表内容可滚动到胶囊上方，不被玻璃层遮挡。 */
private val FLOATING_BAR_CONTENT_PADDING = 88.dp

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

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // 宽屏改用 NavigationRail：底栏让位给侧栏，列表获得更宽的横向空间
        val wideScreen = maxWidth >= WIDE_SCREEN_WIDTH
        // 底栏玻璃采样源：设备不支持 RuntimeShader 时返回 null，底栏自动降级为实色
        val backdrop = rememberBlurBackdrop()
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { innerPadding ->
            // 消费父 Scaffold 的内边距，避免各 tab 自己的 Scaffold 重复应用系统栏 insets。
            Row(
                modifier = Modifier.fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding),
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
                    // 内容层同时是玻璃采样源；底部留白让内容滚到胶囊上方
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .layerBackdropSource(backdrop)
                            .padding(bottom = if (wideScreen) 0.dp else FLOATING_BAR_CONTENT_PADDING),
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
        // 液态玻璃胶囊底栏：浮在内容之上以采样页面背景，宽度由 24dp 外边距决定
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
