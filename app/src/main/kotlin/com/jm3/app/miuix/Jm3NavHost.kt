package com.jm3.app.miuix

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.jm3.app.miuix.ui.AlbumDetailScreen
import com.jm3.app.miuix.ui.CreatorWorkDetailScreen
import com.jm3.app.miuix.ui.CreatorWorksScreen
import com.jm3.app.miuix.ui.FavoritesScreen
import com.jm3.app.miuix.ui.ForumScreen
import com.jm3.app.miuix.ui.HistoryScreen
import com.jm3.app.miuix.ui.LibraryScreen
import com.jm3.app.miuix.ui.LoginScreen
import com.jm3.app.miuix.ui.MainScreen
import com.jm3.app.miuix.ui.NovelDetailScreen
import com.jm3.app.miuix.ui.NovelListScreen
import com.jm3.app.miuix.ui.NovelReaderScreen
import com.jm3.app.miuix.ui.ProfileScreen
import com.jm3.app.miuix.ui.ReaderScreen
import com.jm3.app.miuix.ui.SearchScreen
import com.jm3.app.miuix.ui.SettingsScreen
import com.jm3.app.miuix.ui.ShelfScreen
import com.jm3.app.miuix.ui.TasksScreen
import com.jm3.app.miuix.ui.UpdateScreen
import com.jm3.app.miuix.ui.TrackingScreen
import com.jm3.app.miuix.ui.WatchHistoryScreen
import com.jm3.app.miuix.ui.WeekScreen
import com.jm3.app.miuix.ui.NotificationsScreen
import com.jm3.app.miuix.ui.coui.page.CouiAlbumDetailScreen
import com.jm3.app.miuix.ui.coui.page.CouiCreatorScreen
import com.jm3.app.miuix.ui.coui.page.CouiCreatorWorkScreen
import com.jm3.app.miuix.ui.coui.page.CouiFavoritesScreen
import com.jm3.app.miuix.ui.coui.page.CouiForumScreen
import com.jm3.app.miuix.ui.coui.page.CouiHistoryScreen
import com.jm3.app.miuix.ui.coui.page.CouiLibraryScreen
import com.jm3.app.miuix.ui.coui.page.CouiLoginScreen
import com.jm3.app.miuix.ui.coui.page.CouiMainScreen
import com.jm3.app.miuix.ui.coui.page.CouiNotificationsScreen
import com.jm3.app.miuix.ui.coui.page.CouiNovelDetailScreen
import com.jm3.app.miuix.ui.coui.page.CouiNovelListScreen
import com.jm3.app.miuix.ui.coui.page.CouiNovelReaderScreen
import com.jm3.app.miuix.ui.coui.page.CouiProfileScreen
import com.jm3.app.miuix.ui.coui.page.CouiReaderScreen
import com.jm3.app.miuix.ui.coui.page.CouiSearchScreen
import com.jm3.app.miuix.ui.coui.page.CouiSettingsScreen
import com.jm3.app.miuix.ui.coui.page.CouiShelfScreen
import com.jm3.app.miuix.ui.coui.page.CouiTasksScreen
import com.jm3.app.miuix.ui.coui.page.CouiTrackingScreen
import com.jm3.app.miuix.ui.coui.page.CouiUpdateScreen
import com.jm3.app.miuix.ui.coui.page.CouiWatchHistoryScreen
import com.jm3.app.miuix.ui.coui.page.CouiWeekScreen
import com.jm3.app.miuix.ui.route.AppRoutes
import com.jm3.app.miuix.ui.route.SkinRoute
import com.jm3.app.miuix.ui.skin.DynamicColorPolicy
import com.jm3.core.settings.ThemeMode
import com.jm3.core.settings.UiSkin
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import top.yukonga.miuix.kmp.nav.transition.NavTransition
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun Jm3NavHost(
    backStack: NavBackStack,
    onBack: () -> Unit,
    effects: NavDisplayEffects,
    deps: ScreenDeps,
    nav: AppRoutes,
    uiSkin: UiSkin,
    snackbarHostState: SnackbarHostState,
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onUiSkinChange: (UiSkin) -> Unit,
    subPageTransition: NavTransition?,
    albumDetailTransition: NavTransition,
) {
    NavDisplay(
        backStack = backStack,
        onBack = onBack,
        effects = effects,
    ) {
        entry<Route.Main> {
            SkinRoute(
                uiSkin = uiSkin,
                hyperOs = {
                    MainScreen(
                        deps = deps,
                        unreadBadge = deps.unread,
                        snackbarHostState = snackbarHostState,
                        nav = nav,
                    )
                },
                colorOs = {
                    CouiMainScreen(
                        deps = deps,
                        unreadBadge = deps.unread,
                        snackbarHostState = snackbarHostState,
                        nav = nav,
                    )
                },
            )
        }
        entry<Route.Login>(
            transition = subPageTransition,
            swipeDismiss = NavSwipeDirection.LeftToRight,
        ) {
            val back: () -> Unit = { backStack.removeLastOrNull() }
            SkinRoute(
                uiSkin = uiSkin,
                hyperOs = { LoginScreen(account = deps.account, onBack = back) },
                colorOs = { CouiLoginScreen(account = deps.account, onBack = back) },
            )
        }
        entry<Route.Favorites>(
            transition = subPageTransition,
            swipeDismiss = NavSwipeDirection.LeftToRight,
        ) {
            val back: () -> Unit = { backStack.removeLastOrNull() }
            SkinRoute(
                uiSkin = uiSkin,
                hyperOs = {
                    FavoritesScreen(
                        deps = deps,
                        onBack = back,
                        nav = nav,
                    )
                },
                colorOs = {
                    CouiFavoritesScreen(
                        deps = deps,
                        onBack = back,
                        nav = nav,
                    )
                },
            )
        }
        entry<Route.History>(
            transition = subPageTransition,
            swipeDismiss = NavSwipeDirection.LeftToRight,
        ) {
            val back: () -> Unit = { backStack.removeLastOrNull() }
            SkinRoute(
                uiSkin = uiSkin,
                hyperOs = {
                    HistoryScreen(
                        deps = deps,
                        onBack = back,
                        nav = nav,
                    )
                },
                colorOs = {
                    CouiHistoryScreen(
                        deps = deps,
                        onBack = back,
                        nav = nav,
                    )
                },
            )
        }
        entry<Route.Shelf>(
            transition = subPageTransition,
            swipeDismiss = NavSwipeDirection.LeftToRight,
        ) {
            val back: () -> Unit = { backStack.removeLastOrNull() }
            SkinRoute(
                uiSkin = uiSkin,
                hyperOs = {
                    ShelfScreen(deps = deps, onBack = back, nav = nav)
                },
                colorOs = {
                    CouiShelfScreen(deps = deps, onBack = back, nav = nav)
                },
            )
        }
        entry<Route.Settings>(
            transition = subPageTransition,
            swipeDismiss = NavSwipeDirection.LeftToRight,
        ) {
            val changeThemeMode: (ThemeMode) -> Unit = { mode ->
                onThemeModeChange(mode)
            }
            val changeDynamicColor: (Boolean) -> Unit = { enabled ->
                onDynamicColorChange(enabled)
            }
            val changeSkin: (UiSkin) -> Unit = { nextSkin ->
                onUiSkinChange(nextSkin)
            }
            val back: () -> Unit = { backStack.removeLastOrNull() }
            SkinRoute(
                uiSkin = uiSkin,
                hyperOs = {
                    SettingsScreen(
                        deps = deps,
                        themeMode = themeMode,
                        onThemeModeChange = changeThemeMode,
                        dynamicColor = dynamicColor,
                        onDynamicColorChange = changeDynamicColor,
                        dynamicColorSupported = DynamicColorPolicy.supports(uiSkin),
                        uiSkin = uiSkin,
                        onUiSkinChange = changeSkin,
                        onBack = back,
                        snackbarHostState = snackbarHostState,
                        nav = nav,
                    )
                },
                colorOs = {
                    CouiSettingsScreen(
                        deps = deps,
                        themeMode = themeMode,
                        onThemeModeChange = changeThemeMode,
                        dynamicColor = dynamicColor,
                        onDynamicColorChange = changeDynamicColor,
                        dynamicColorSupported = DynamicColorPolicy.supports(uiSkin),
                        uiSkin = uiSkin,
                        onUiSkinChange = changeSkin,
                        onBack = back,
                        snackbarHostState = snackbarHostState,
                        nav = nav,
                    )
                },
            )
        }
        entry<Route.Update>(
            transition = subPageTransition,
            swipeDismiss = NavSwipeDirection.LeftToRight,
        ) {
            val back: () -> Unit = { backStack.removeLastOrNull() }
            SkinRoute(
                uiSkin = uiSkin,
                hyperOs = {
                    UpdateScreen(deps = deps, onBack = back)
                },
                colorOs = {
                    CouiUpdateScreen(deps = deps, onBack = back, snackbarHostState = snackbarHostState)
                },
            )
        }
        entry<Route.Week>(
            transition = subPageTransition,
            swipeDismiss = NavSwipeDirection.LeftToRight,
        ) {
            val back: () -> Unit = { backStack.removeLastOrNull() }
            SkinRoute(
                uiSkin = uiSkin,
                hyperOs = {
                    WeekScreen(
                        deps = deps,
                        onBack = back,
                        nav = nav,
                    )
                },
                colorOs = {
                    CouiWeekScreen(
                        deps = deps,
                        onBack = back,
                        nav = nav,
                    )
                },
            )
        }
        entry<Route.Notifications>(
            transition = subPageTransition,
            swipeDismiss = NavSwipeDirection.LeftToRight,
        ) {
            val back: () -> Unit = { backStack.removeLastOrNull() }
            SkinRoute(
                uiSkin = uiSkin,
                hyperOs = {
                    NotificationsScreen(
                        deps = deps,
                        onBack = back,
                        snackbarHostState = snackbarHostState,
                        unreadBadge = deps.unread,
                        nav = nav,
                    )
                },
                colorOs = {
                    CouiNotificationsScreen(
                        deps = deps,
                        onBack = back,
                        snackbarHostState = snackbarHostState,
                        unreadBadge = deps.unread,
                        nav = nav,
                    )
                },
            )
        }
        entry<Route.Tracking>(
            transition = subPageTransition,
            swipeDismiss = NavSwipeDirection.LeftToRight,
        ) {
            val back: () -> Unit = { backStack.removeLastOrNull() }
            SkinRoute(
                uiSkin = uiSkin,
                hyperOs = {
                    TrackingScreen(
                        deps = deps,
                        onBack = back,
                        snackbarHostState = snackbarHostState,
                        nav = nav,
                    )
                },
                colorOs = {
                    CouiTrackingScreen(
                        deps = deps,
                        onBack = back,
                        snackbarHostState = snackbarHostState,
                        nav = nav,
                    )
                },
            )
        }
        entry<Route.Tasks>(
            transition = subPageTransition,
            swipeDismiss = NavSwipeDirection.LeftToRight,
        ) {
            val back: () -> Unit = { backStack.removeLastOrNull() }
            SkinRoute(
                uiSkin = uiSkin,
                hyperOs = {
                    TasksScreen(
                        deps = deps,
                        onBack = back,
                        snackbarHostState = snackbarHostState,
                        nav = nav,
                    )
                },
                colorOs = {
                    CouiTasksScreen(
                        deps = deps,
                        onBack = back,
                        snackbarHostState = snackbarHostState,
                        nav = nav,
                    )
                },
            )
        }
        entry<Route.Profile>(
            transition = subPageTransition,
            swipeDismiss = NavSwipeDirection.LeftToRight,
        ) {
            val back: () -> Unit = { backStack.removeLastOrNull() }
            SkinRoute(
                uiSkin = uiSkin,
                hyperOs = {
                    ProfileScreen(
                        deps = deps,
                        onBack = back,
                        snackbarHostState = snackbarHostState,
                        nav = nav,
                    )
                },
                colorOs = {
                    CouiProfileScreen(
                        deps = deps,
                        onBack = back,
                        snackbarHostState = snackbarHostState,
                        nav = nav,
                    )
                },
            )
        }
        entry<Route.WatchHistory>(
            transition = subPageTransition,
            swipeDismiss = NavSwipeDirection.LeftToRight,
        ) {
            val back: () -> Unit = { backStack.removeLastOrNull() }
            SkinRoute(
                uiSkin = uiSkin,
                hyperOs = {
                    WatchHistoryScreen(
                        deps = deps,
                        onBack = back,
                        snackbarHostState = snackbarHostState,
                        nav = nav,
                    )
                },
                colorOs = {
                    CouiWatchHistoryScreen(
                        deps = deps,
                        onBack = back,
                        snackbarHostState = snackbarHostState,
                        nav = nav,
                    )
                },
            )
        }
        entry<Route.Forum>(
            transition = subPageTransition,
            swipeDismiss = NavSwipeDirection.LeftToRight,
        ) {
            val back: () -> Unit = { backStack.removeLastOrNull() }
            SkinRoute(
                uiSkin = uiSkin,
                hyperOs = {
                    ForumScreen(
                        deps = deps,
                        onBack = back,
                        snackbarHostState = snackbarHostState,
                        nav = nav,
                    )
                },
                colorOs = {
                    CouiForumScreen(
                        deps = deps,
                        onBack = back,
                        snackbarHostState = snackbarHostState,
                        nav = nav,
                    )
                },
            )
        }
        entry<Route.AlbumDetail>(
            transition = albumDetailTransition,
            swipeDismiss = NavSwipeDirection.LeftToRight,
        ) { route ->
            SkinRoute(
                uiSkin = uiSkin,
                hyperOs = {
                    AlbumDetailScreen(
                        albumId = route.albumId,
                        initialTitle = route.initialTitle,
                        initialCoverUrl = route.initialCoverUrl,
                        onBack = onBack,
                        snackbarHostState = snackbarHostState,
                        gateway = deps.gateway,
                        authCall = deps.authCall,
                        history = deps.history,
                        downloadManager = deps.downloadManager,
                        downloadFiles = deps.downloadFiles,
                        account = deps.account,
                        sessionCache = deps.sessionCache,
                        nav = nav,
                    )
                },
                colorOs = {
                    CouiAlbumDetailScreen(
                        albumId = route.albumId,
                        initialTitle = route.initialTitle,
                        initialCoverUrl = route.initialCoverUrl,
                        onBack = onBack,
                        snackbarHostState = snackbarHostState,
                        gateway = deps.gateway,
                        authCall = deps.authCall,
                        history = deps.history,
                        downloadManager = deps.downloadManager,
                        downloadFiles = deps.downloadFiles,
                        account = deps.account,
                        sessionCache = deps.sessionCache,
                        nav = nav,
                    )
                },
            )
        }
        entry<Route.Reader>(
            transition = subPageTransition,
            swipeDismiss = NavSwipeDirection.LeftToRight,
        ) { route ->
            val back: () -> Unit = { backStack.removeLastOrNull() }
            SkinRoute(
                uiSkin = uiSkin,
                hyperOs = {
                    ReaderScreen(
                        deps = deps,
                        photoId = route.photoId,
                        startPage = route.startPage,
                        settings = deps.settings,
                        onBack = back,
                        snackbarHostState = snackbarHostState,
                        nav = nav,
                    )
                },
                colorOs = {
                    CouiReaderScreen(
                        deps = deps,
                        photoId = route.photoId,
                        startPage = route.startPage,
                        settings = deps.settings,
                        onBack = back,
                        snackbarHostState = snackbarHostState,
                        nav = nav,
                    )
                },
            )
        }
        entry<Route.Novel>(
            transition = subPageTransition,
            swipeDismiss = NavSwipeDirection.LeftToRight,
        ) {
            val back: () -> Unit = { backStack.removeLastOrNull() }
            SkinRoute(
                uiSkin = uiSkin,
                hyperOs = {
                    NovelListScreen(
                        deps = deps,
                        onBack = back,
                        snackbarHostState = snackbarHostState,
                        nav = nav,
                    )
                },
                colorOs = {
                    CouiNovelListScreen(
                        deps = deps,
                        onBack = back,
                        snackbarHostState = snackbarHostState,
                        nav = nav,
                    )
                },
            )
        }
        entry<Route.NovelDetail>(
            transition = subPageTransition,
            swipeDismiss = NavSwipeDirection.LeftToRight,
        ) { route ->
            val back: () -> Unit = { backStack.removeLastOrNull() }
            SkinRoute(
                uiSkin = uiSkin,
                hyperOs = {
                    NovelDetailScreen(
                        deps = deps,
                        novelId = route.novelId,
                        onBack = back,
                        snackbarHostState = snackbarHostState,
                        nav = nav,
                    )
                },
                colorOs = {
                    CouiNovelDetailScreen(
                        deps = deps,
                        novelId = route.novelId,
                        onBack = back,
                        snackbarHostState = snackbarHostState,
                        nav = nav,
                    )
                },
            )
        }
        entry<Route.NovelReader>(
            transition = subPageTransition,
            swipeDismiss = NavSwipeDirection.LeftToRight,
        ) { route ->
            val back: () -> Unit = { backStack.removeLastOrNull() }
            SkinRoute(
                uiSkin = uiSkin,
                hyperOs = {
                    NovelReaderScreen(
                        deps = deps,
                        novelId = route.novelId,
                        initialChapterId = route.chapterId,
                        onBack = back,
                        snackbarHostState = snackbarHostState,
                        nav = nav,
                    )
                },
                colorOs = {
                    CouiNovelReaderScreen(
                        deps = deps,
                        novelId = route.novelId,
                        initialChapterId = route.chapterId,
                        onBack = back,
                        snackbarHostState = snackbarHostState,
                        nav = nav,
                    )
                },
            )
        }
        entry<Route.Library>(
            transition = subPageTransition,
            swipeDismiss = NavSwipeDirection.LeftToRight,
        ) {
            val back: () -> Unit = { backStack.removeLastOrNull() }
            SkinRoute(
                uiSkin = uiSkin,
                hyperOs = {
                    LibraryScreen(
                        deps = deps,
                        onBack = back,
                        snackbarHostState = snackbarHostState,
                        nav = nav,
                    )
                },
                colorOs = {
                    CouiLibraryScreen(
                        deps = deps,
                        onBack = back,
                        snackbarHostState = snackbarHostState,
                        nav = nav,
                    )
                },
            )
        }
        entry<Route.Creator>(
            transition = subPageTransition,
            swipeDismiss = NavSwipeDirection.LeftToRight,
        ) { route ->
            val back: () -> Unit = { backStack.removeLastOrNull() }
            SkinRoute(
                uiSkin = uiSkin,
                hyperOs = {
                    CreatorWorksScreen(
                        deps = deps,
                        creatorId = route.creatorId,
                        onBack = back,
                        snackbarHostState = snackbarHostState,
                        nav = nav,
                    )
                },
                colorOs = {
                    CouiCreatorScreen(
                        deps = deps,
                        creatorId = route.creatorId,
                        onBack = back,
                        snackbarHostState = snackbarHostState,
                        nav = nav,
                    )
                },
            )
        }
        entry<Route.CreatorWork>(
            transition = subPageTransition,
            swipeDismiss = NavSwipeDirection.LeftToRight,
        ) { route ->
            val back: () -> Unit = { backStack.removeLastOrNull() }
            SkinRoute(
                uiSkin = uiSkin,
                hyperOs = {
                    CreatorWorkDetailScreen(
                        deps = deps,
                        workId = route.workId,
                        onBack = back,
                        snackbarHostState = snackbarHostState,
                        nav = nav,
                    )
                },
                colorOs = {
                    CouiCreatorWorkScreen(
                        deps = deps,
                        workId = route.workId,
                        onBack = back,
                        nav = nav,
                    )
                },
            )
        }
        entry<Route.Search>(
            transition = subPageTransition,
            swipeDismiss = NavSwipeDirection.LeftToRight,
        ) { route ->
            val back: () -> Unit = { backStack.removeLastOrNull() }
            SkinRoute(
                uiSkin = uiSkin,
                hyperOs = {
                    SearchScreen(
                        deps = deps,
                        query = route.query,
                        scope = route.scope,
                        onBack = back,
                        nav = nav,
                    )
                },
                colorOs = {
                    CouiSearchScreen(
                        deps = deps,
                        query = route.query,
                        scope = route.scope,
                        onBack = back,
                        nav = nav,
                    )
                },
            )
    }
}
}