package com.jm3.app.miuix

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.request.crossfade
import com.jm3.app.miuix.data.ApiDomain
import com.jm3.app.miuix.data.DownloadDomain
import com.jm3.app.miuix.data.LibraryDomain
import com.jm3.app.miuix.data.SessionDomain
import com.jm3.app.miuix.data.SettingsDomain
import com.jm3.app.miuix.data.UpdateDomain
import com.jm3.app.miuix.data.download.DownloadManager
import com.jm3.app.miuix.ui.AlbumDetailScreen
import com.jm3.app.miuix.ui.AlbumHeroHost
import com.jm3.app.miuix.ui.AlbumHeroOverlay
import com.jm3.app.miuix.ui.CreatorWorkDetailScreen
import com.jm3.app.miuix.ui.CreatorWorksScreen
import com.jm3.app.miuix.ui.FavoritesScreen
import com.jm3.app.miuix.ui.ForumScreen
import com.jm3.app.miuix.ui.HistoryScreen
import com.jm3.app.miuix.ui.LibraryScreen
import com.jm3.app.miuix.ui.LocalAlbumHeroHost
import com.jm3.app.miuix.ui.LoginScreen
import com.jm3.app.miuix.ui.MainScreen
import com.jm3.app.miuix.ui.NovelDetailScreen
import com.jm3.app.miuix.ui.NovelListScreen
import com.jm3.app.miuix.ui.NovelReaderScreen
import com.jm3.app.miuix.ui.ProfileScreen
import com.jm3.app.miuix.ui.ReaderScreen
import com.jm3.app.miuix.ui.SearchScreen
import com.jm3.app.miuix.ui.SessionCache
import com.jm3.app.miuix.ui.SettingsScreen
import com.jm3.app.miuix.ui.ShelfScreen
import com.jm3.app.miuix.ui.TasksScreen
import com.jm3.app.miuix.ui.UpdateScreen
import com.jm3.app.miuix.ui.UpdateStartupPrompt
import com.jm3.app.miuix.ui.TrackingScreen
import com.jm3.app.miuix.ui.WatchHistoryScreen
import com.jm3.app.miuix.ui.WeekScreen
import com.jm3.app.miuix.ui.NotificationsScreen
import com.jm3.app.miuix.update.UpdateDownloader
import com.jm3.core.paging.PaginationModePolicy
import com.jm3.core.Jm3Gateway
import com.jm3.core.Jm3Log
import com.jm3.core.runCatchingSuspending
import com.jm3.core.download.DownloadStore
import com.jm3.core.history.HistoryRepository
import com.jm3.core.session.AccountManager
import com.jm3.core.session.AuthCall
import com.jm3.core.settings.SettingsRepository
import com.jm3.core.settings.PaginationMode
import com.jm3.core.settings.ThemeMode
import com.jm3.core.model.SearchScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack
import top.yukonga.miuix.kmp.nav.core.rememberNavSystemCornerRadius
import androidx.compose.ui.graphics.GraphicsLayerScope
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import top.yukonga.miuix.kmp.nav.transition.NavTransitionScope
import top.yukonga.miuix.kmp.nav.transition.navDirectionalTransition
import top.yukonga.miuix.kmp.nav.transition.navGraphicsTransition
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/**
 * 应用组合根：只做装配与进程级作用域，业务依赖按域收在 holder 里。
 *
 * 深化之前这里是 14 个成员各写一遍构造的上帝对象：改一条依赖要在大文件里找对位置，
 * 读一个概念要在一屏内的十几处构造之间跳。现在新增一个依赖只改它所属 domain 的一行，
 * 新增一个域只在 [init] 里多一行装配；组合根自己只剩「谁先于谁装配」这一条不变量。
 */
object Jm3Graph {

    /** 进程级作用域：随进程存活，界面与服务都往这里挂长期协程。 */
    val appScope: CoroutineScope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.IO) }

    /** 线路域：当前网关与线路切换。 */
    lateinit var api: ApiDomain
        private set

    /** 设置域：设置仓库与界面态。 */
    lateinit var preferences: SettingsDomain
        private set

    /** 会话域：账号、带闸门调用入口、未读角标。 */
    lateinit var session: SessionDomain
        private set

    /** 本地库域：阅读历史与下载台账共用的 Room 库。 */
    lateinit var library: LibraryDomain
        private set

    /** 下载域：页文件存储、队列与台账门面。 */
    lateinit var downloads: DownloadDomain
        private set

    /** 更新域：应用内更新的下载器与安装动作（状态跟随进程，离开更新页后下载继续）。 */
    lateinit var updates: UpdateDomain
        private set

    /** 会话级列表缓存：首页发现流/搜索结果与浏览页结果跨路由保存，避免回来时列表被清空、滚动位置落不回去。 */
    internal val sessionCache: SessionCache by lazy { SessionCache() }

    @Volatile
    private var initialized = false

    /**
     * 装配顺序即启动顺序：线路池先落进 [ApiDomain]（网关构造时读它），
     * 会话域拿设置存储当凭据密文库，下载域拿线路域当页源、拿本地库当台账。
     *
     * 幂等：Activity 重建会再调一次，重复装配会换掉正在被 collect 的实例。
     */
    fun init(context: Context) {
        if (initialized) return
        initialized = true
        val app = context.applicationContext
        // :core 不依赖 Android，事实日志出口只能由这里接上平台日志；没接时 :core 侧静默丢弃。
        Jm3Log.sink = { tag, message, error ->
            if (error == null) Log.w(tag, message) else Log.w(tag, message, error)
        }
        api = ApiDomain(app, appScope)
        // 网关装配是同步的且早于启动协程，线路池必须在这里就读出来（纯文本，一次几十字节）。
        api.warmUp()
        preferences = SettingsDomain(app)
        session = SessionDomain(app, preferences.store, api)
        library = LibraryDomain(app)
        downloads = DownloadDomain(app, api, library, appScope)
        updates = UpdateDomain(app, appScope)
    }

    // ---- 迁移期转接：屏幕/服务还按旧名字读，迁到 holder 后整块删除（task-8 / task-9）。----

    /** 转接：[ApiDomain.gateway]。 */
    @Deprecated("改用 api.gateway")
    val gateway: Jm3Gateway
        get() = api.gateway

    /** 转接：[SettingsDomain.repository]。 */
    @Deprecated("改用 preferences.repository")
    val settings: SettingsRepository
        get() = preferences.repository

    /** 转接：[SessionDomain.account]。 */
    @Deprecated("改用 session.account")
    val account: AccountManager
        get() = session.account

    /** 转接：[SessionDomain.authCall]。 */
    @Deprecated("改用 session.authCall")
    val authCall: AuthCall
        get() = session.authCall

    /** 转接：[SettingsDomain.paginationMode]。 */
    @Deprecated("改用 preferences.paginationMode")
    var paginationMode: PaginationMode
        get() = preferences.paginationMode
        set(value) {
            preferences.paginationMode = value
        }

    /** 转接：[LibraryDomain.history]。 */
    @Deprecated("改用 library.history")
    val history: HistoryRepository
        get() = library.history

    /** 转接：[DownloadDomain.files]。 */
    @Deprecated("改用 downloads.files")
    val downloadFiles: DownloadStore
        get() = downloads.files

    /** 转接：[DownloadDomain.manager]。 */
    @Deprecated("改用 downloads.manager")
    val downloadManager: DownloadManager
        get() = downloads.manager

    /** 转接：[UpdateDomain.downloader]。 */
    @Deprecated("改用 updates.downloader")
    val updateDownloader: UpdateDownloader
        get() = updates.downloader

    /** 转接：[SessionDomain.applyDomains]。 */
    @Deprecated("改用 session.applyDomains")
    suspend fun applyDomains(apiDomains: List<String>) = session.applyDomains(apiDomains)

}

@Serializable
sealed interface Route : NavKey {
    @Serializable
    data object Main : Route

    @Serializable
    data object Login : Route

    @Serializable
    data object Favorites : Route

    @Serializable
    data object History : Route

    @Serializable
    data object Shelf : Route

    @Serializable
    data object Settings : Route

    /** 检查更新（二级页，数据源为远端 Release 信息）。 */
    @Serializable
    data object Update : Route

    /** 每周必看（原版 Week 页）。 */
    @Serializable
    data object Week : Route

    /** 站内信箱/通知。 */
    @Serializable
    data object Notifications : Route

    /** 连载追踪列表。 */
    @Serializable
    data object Tracking : Route

    /** 成就/任务（称号与勋章）。 */
    @Serializable
    data object Tasks : Route

    /** 个人资料查看与编辑。 */
    @Serializable
    data object Profile : Route

    /** 服务端观看记录。 */
    @Serializable
    data object WatchHistory : Route

    /** 社区论坛（全部/漫画评论/闲聊三档）。 */
    @Serializable
    data object Forum : Route

    @Serializable
    data class AlbumDetail(
        val albumId: String,
        val initialTitle: String? = null,
        val initialCoverUrl: String? = null,
    ) : Route

    @Serializable
    data class Reader(val photoId: String) : Route

    /** 小说列表（原版 Novels 页）。 */
    @Serializable
    data object Novel : Route

    /** 小说详情：目录/相关/评论三档。 */
    @Serializable
    data class NovelDetail(val novelId: String) : Route

    /** 小说阅读器：记录起始章节，返回详情页而非逐章回退。 */
    @Serializable
    data class NovelReader(val novelId: String, val chapterId: String) : Route

    /** 禁漫书库（原版 Library：创作者/作品双 Tab）。 */
    @Serializable
    data object Library : Route

    /** 创作者主页：头像/背景/赞助入口/作品集合。 */
    @Serializable
    data class Creator(val creatorId: String) : Route

    /** 创作者作品详情：描述 + 整页图片 + 相关作品。 */
    @Serializable
    data class CreatorWork(val workId: String) : Route

    /** 独立搜索结果页：标签/作者/作品/人物点击进入；scope 对应原版搜索类型。 */
    @Serializable
    data class Search(val query: String, val scope: SearchScope = SearchScope.SITE) : Route
}

/** :core 不依赖 Miuix，主题映射收敛在应用层；动态取色开启后改用 Monet 配色。 */
private fun ThemeMode.toColorSchemeMode(dynamicColor: Boolean): ColorSchemeMode =
    if (dynamicColor) {
        when (this) {
            ThemeMode.SYSTEM -> ColorSchemeMode.MonetSystem
            ThemeMode.LIGHT -> ColorSchemeMode.MonetLight
            ThemeMode.DARK -> ColorSchemeMode.MonetDark
        }
    } else {
        when (this) {
            ThemeMode.SYSTEM -> ColorSchemeMode.System
            ThemeMode.LIGHT -> ColorSchemeMode.Light
            ThemeMode.DARK -> ColorSchemeMode.Dark
        }
    }

/**
 * 详情页页面变换：进入与退出共用同一段实现，只做 alpha，不做位移。
 *
 * 共享元素按根坐标插值，页面若整宽平移，头图的根坐标矩形每一帧都在变，飞行落点就会漂；
 * 所以这一页和它覆盖的那一页都不平移，位移感交给飞行的封面本身。
 *
 * relativeDepth 是有符号的：进入时新页 -1 → 0、旧页 0 → 1；退出时旧页 0 → -1、下页 1 → 0。
 * 因此同一段 alpha 在两端天然是正反两向，且手势期间由手指直接驱动。
 */
private val albumDetailPageTransform: GraphicsLayerScope.(NavTransitionScope) -> Unit = { scope ->
    if (scope.relativeDepth <= 0f) alpha = (1f + scope.relativeDepth).coerceIn(0f, 1f)
}

/**
 * 详情页转场：push / pop / predictivePop 各声明一段，pop 与 push 是同一个变换（镜像）。
 *
 * 对应 eh 项目 Navigation Compose 的 enterTransition / popEnterTransition 写法；
 * Miuix Nav 没有 AnimatedContentTransitionScope，用 navDirectionalTransition 表达同一语义。
 */
private val albumDetailTransition = navDirectionalTransition(
    navGraphicsTransition(
        opaqueDepth = 1f,
        dismissDirection = NavSwipeDirection.None,
        block = albumDetailPageTransform,
    ),
    navGraphicsTransition(
        opaqueDepth = 1f,
        dismissDirection = NavSwipeDirection.None,
        block = albumDetailPageTransform,
    ),
    navGraphicsTransition(
        opaqueDepth = 1f,
        dismissDirection = NavSwipeDirection.LeftToRight,
        block = albumDetailPageTransform,
    ),
)

@Composable
fun Jm3App() {
    // 全局图片加载器：所有 AsyncImage 共享，交叉淡入消除列表滚动时的硬切
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context).crossfade(true).build()
    }
    // 显式指定路由超类型，否则 saver 只按 Route.Main 序列化，压入二级页后保存会抛异常
    val backStack = rememberNavBackStack<Route>(Route.Main)
    // 屏幕依赖一次性构造：此后各路由只接 deps，屏幕内部不再直读组装根。
    val deps = remember {
        ScreenDeps(
            api = Jm3Graph.api,
            preferences = Jm3Graph.preferences,
            session = Jm3Graph.session,
            library = Jm3Graph.library,
            downloads = Jm3Graph.downloads,
            updates = Jm3Graph.updates,
            sessionCache = Jm3Graph.sessionCache,
            appScope = Jm3Graph.appScope,
        )
    }
    val scope = rememberCoroutineScope()
    // colorSchemeMode 是 val 不可赋值：主题切换通过重建 controller 完成。
    var themeModeName by rememberSaveable { mutableStateOf(ThemeMode.SYSTEM.name) }
    val themeMode = remember(themeModeName) {
        runCatching { ThemeMode.valueOf(themeModeName) }.getOrDefault(ThemeMode.SYSTEM)
    }
    var dynamicColor by rememberSaveable { mutableStateOf(false) }
    val controller = remember(themeMode, dynamicColor) {
        ThemeController(colorSchemeMode = themeMode.toColorSchemeMode(dynamicColor))
    }
    // 全局 Snackbar：各屏 Scaffold 的 snackbarHost 槽挂载同一个 host state
    val snackbarHostState = remember { SnackbarHostState() }
    // 列表 → 详情的共享元素宿主：封面/头图矩形登记表与当前飞行都挂在它身上
    val heroHost = remember { AlbumHeroHost() }
    // 退栈前先把头图送回列表卡片再出栈；任一端拿不到矩形时退化成普通退栈。
    // 返回手势期间详情页已按手势进度起飞，这里只兜底非手势路径，避免同一段动画被重置。
    val popBack: () -> Unit = {
        (backStack.lastOrNull() as? Route.AlbumDetail)?.let { route ->
            val flight = heroHost.flight
            val alreadyFlying = flight != null && flight.albumId == route.albumId && heroHost.isBackwardFlight(flight.id)
            if (!alreadyFlying) heroHost.startBackward(route.albumId)
        }
        backStack.removeLastOrNull()
    }
    // 兜底：侧滑返回等不经过 popBack 的退出路径，靠栈顶变化补一次反向飞行
    var lastTopAlbumId by remember { mutableStateOf<String?>(null) }
    val topAlbumId = (backStack.lastOrNull() as? Route.AlbumDetail)?.albumId
    LaunchedEffect(topAlbumId) {
        val previous = lastTopAlbumId
        if (previous != null && previous != topAlbumId && heroHost.flight == null && heroHost.pending == null) {
            heroHost.startBackward(previous)
        }
        lastTopAlbumId = topAlbumId
    }

    // 启动顺序：先恢复主题与线路（切了线路时 applyDomains 会把会话注入新网关），最后再兜一次会话恢复。
    LaunchedEffect(Unit) {
        // 设置读盘失败也要往下走：会话恢复是登录态的起点，不能被主题或线路拖住。
        // 五句全是挂起调用，用 runCatchingSuspending：取消原样抛出，失败留一条事实日志。
        runCatchingSuspending {
            themeModeName = Jm3Graph.settings.themeMode().name
            dynamicColor = Jm3Graph.settings.dynamicColor()
            // 分页模式：用户在本进程内已显式改过就保留内存值，否则用盘上值。
            // 读盘发生在组合之后，盘上的旧值不该盖掉用户刚选的那个。
            Jm3Graph.preferences.applyStartupPaginationMode(
                PaginationModePolicy.resolveStartupPaginationMode(
                    persisted = Jm3Graph.preferences.repository.paginationMode(),
                    current = Jm3Graph.preferences.paginationMode,
                    userModified = Jm3Graph.preferences.paginationModeUserModified,
                )
            )
            Jm3Graph.applyDomains(Jm3Graph.settings.apiDomains())
        }.onFailure { failure ->
            Jm3Log.warn("Jm3App", "启动读盘失败（" + failure.javaClass.name + "），改用默认设置")
        }
        Jm3Graph.account.restore()
    }

    MiuixTheme(controller = controller) {
        // edge-to-edge 下状态栏/导航栏图标明暗跟随应用内主题，而非系统夜间模式
        val view = LocalView.current
        val darkSurface = MiuixTheme.colorScheme.surface.luminance() < 0.5f
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkSurface
                isAppearanceLightNavigationBars = !darkSurface
            }
        }
        // 转场特效：圆角裁剪贴合设备屏幕圆角，背景色补上浮层下露出的底色（HyperOS 浮动转场观感）
        val screenCornerRadius = rememberNavSystemCornerRadius()
        CompositionLocalProvider(LocalAlbumHeroHost provides heroHost) {
            Box(modifier = Modifier.fillMaxSize()) {
                NavDisplay(
                    backStack = backStack,
                    onBack = popBack,
                    effects = NavDisplayEffects(
                        cornerClipRadius = screenCornerRadius,
                        backdropColor = MiuixTheme.colorScheme.surface,
                    ),
                ) {
                    entry<Route.Main> {
                        MainScreen(
                            deps = deps,
                            gateway = Jm3Graph.api.gateway,
                            account = Jm3Graph.session.account,
                            downloadManager = Jm3Graph.downloads.manager,
                            unreadBadge = Jm3Graph.session.unread,
                            authCall = Jm3Graph.session.authCall,
                            snackbarHostState = snackbarHostState,
                            onOpenAlbum = { albumId -> backStack.add(Route.AlbumDetail(albumId)) },
                            onOpenLogin = { backStack.add(Route.Login) },
                            onOpenFavorites = { backStack.add(Route.Favorites) },
                            onOpenHistory = { backStack.add(Route.History) },
                            onOpenShelf = { backStack.add(Route.Shelf) },
                            onOpenSettings = { backStack.add(Route.Settings) },
                            onOpenWeek = { backStack.add(Route.Week) },
                            onOpenNotifications = { backStack.add(Route.Notifications) },
                            onOpenTracking = { backStack.add(Route.Tracking) },
                            onOpenProfile = { backStack.add(Route.Profile) },
                            onOpenTasks = { backStack.add(Route.Tasks) },
                            onOpenWatchHistory = { backStack.add(Route.WatchHistory) },
                            onOpenForum = { backStack.add(Route.Forum) },
                            onOpenNovel = { backStack.add(Route.Novel) },
                            onOpenLibrary = { backStack.add(Route.Library) },
                        )
                    }
                    entry<Route.Login>(swipeDismiss = NavSwipeDirection.LeftToRight) {
                        LoginScreen(account = Jm3Graph.account, onBack = { backStack.removeLastOrNull() })
                    }
                    entry<Route.Favorites>(swipeDismiss = NavSwipeDirection.LeftToRight) {
                        FavoritesScreen(
                            deps = deps,
                            onBack = { backStack.removeLastOrNull() },
                            onOpenAlbum = { albumId -> backStack.add(Route.AlbumDetail(albumId)) },
                            authCall = Jm3Graph.session.authCall,
                            onOpenLogin = { backStack.add(Route.Login) },
                        )
                    }
                    entry<Route.History>(swipeDismiss = NavSwipeDirection.LeftToRight) {
                        HistoryScreen(
                            deps = deps,
                            onBack = { backStack.removeLastOrNull() },
                            onOpenAlbum = { albumId -> backStack.add(Route.AlbumDetail(albumId)) },
                            onOpenReader = { photoId -> backStack.add(Route.Reader(photoId)) },
                        )
                    }
                    entry<Route.Shelf>(swipeDismiss = NavSwipeDirection.LeftToRight) {
                        ShelfScreen(
                            deps = deps,
                            gateway = Jm3Graph.gateway,
                            authCall = Jm3Graph.session.authCall,
                            onBack = { backStack.removeLastOrNull() },
                            onOpenReader = { photoId -> backStack.add(Route.Reader(photoId)) },
                        )
                    }
                    entry<Route.Settings>(swipeDismiss = NavSwipeDirection.LeftToRight) {
                        SettingsScreen(
                            deps = deps,
                            themeMode = themeMode,
                            onThemeModeChange = { mode ->
                                themeModeName = mode.name
                                scope.launch { Jm3Graph.settings.setThemeMode(mode) }
                            },
                            dynamicColor = dynamicColor,
                            onDynamicColorChange = { enabled ->
                                dynamicColor = enabled
                                scope.launch { Jm3Graph.settings.setDynamicColor(enabled) }
                            },
                            gateway = Jm3Graph.gateway,
                            authCall = Jm3Graph.session.authCall,
                            onBack = { backStack.removeLastOrNull() },
                            onOpenUpdate = { backStack.add(Route.Update) },
                            snackbarHostState = snackbarHostState,
                        )
                    }
                    entry<Route.Update>(swipeDismiss = NavSwipeDirection.LeftToRight) {
                        UpdateScreen(
                            deps = deps,
                            onBack = { backStack.removeLastOrNull() },
                        )
                    }
                    entry<Route.Week>(swipeDismiss = NavSwipeDirection.LeftToRight) {
                        WeekScreen(
                            deps = deps,
                            onBack = { backStack.removeLastOrNull() },
                            onOpenAlbum = { albumId -> backStack.add(Route.AlbumDetail(albumId)) },
                            authCall = Jm3Graph.session.authCall,
                            onOpenLogin = { backStack.add(Route.Login) },
                        )
                    }
                    entry<Route.Notifications>(swipeDismiss = NavSwipeDirection.LeftToRight) {
                        NotificationsScreen(
                            deps = deps,
                            onBack = { backStack.removeLastOrNull() },
                            onOpenAlbum = { albumId -> backStack.add(Route.AlbumDetail(albumId)) },
                            snackbarHostState = snackbarHostState,
                            authCall = Jm3Graph.session.authCall,
                            unreadBadge = Jm3Graph.session.unread,
                            onOpenLogin = { backStack.add(Route.Login) },
                        )
                    }
                    entry<Route.Tracking>(swipeDismiss = NavSwipeDirection.LeftToRight) {
                        TrackingScreen(
                            deps = deps,
                            onBack = { backStack.removeLastOrNull() },
                            onOpenAlbum = { albumId -> backStack.add(Route.AlbumDetail(albumId)) },
                            snackbarHostState = snackbarHostState,
                            authCall = Jm3Graph.session.authCall,
                            onOpenLogin = { backStack.add(Route.Login) },
                        )
                    }
                    entry<Route.Tasks>(swipeDismiss = NavSwipeDirection.LeftToRight) {
                        TasksScreen(
                            deps = deps,
                            onBack = { backStack.removeLastOrNull() },
                            snackbarHostState = snackbarHostState,
                            authCall = Jm3Graph.session.authCall,
                            onOpenLogin = { backStack.add(Route.Login) },
                        )
                    }
                    entry<Route.Profile>(swipeDismiss = NavSwipeDirection.LeftToRight) {
                        ProfileScreen(
                            deps = deps,
                            onBack = { backStack.removeLastOrNull() },
                            snackbarHostState = snackbarHostState,
                            authCall = Jm3Graph.session.authCall,
                            onOpenLogin = { backStack.add(Route.Login) },
                        )
                    }
                    entry<Route.WatchHistory>(swipeDismiss = NavSwipeDirection.LeftToRight) {
                        WatchHistoryScreen(
                            deps = deps,
                            onBack = { backStack.removeLastOrNull() },
                            onOpenAlbum = { albumId -> backStack.add(Route.AlbumDetail(albumId)) },
                            snackbarHostState = snackbarHostState,
                            authCall = Jm3Graph.session.authCall,
                            onOpenLogin = { backStack.add(Route.Login) },
                        )
                    }
                    entry<Route.Forum>(swipeDismiss = NavSwipeDirection.LeftToRight) {
                        ForumScreen(
                            deps = deps,
                            onBack = { backStack.removeLastOrNull() },
                            onOpenLogin = { backStack.add(Route.Login) },
                            snackbarHostState = snackbarHostState,
                            onOpenAlbum = { albumId -> backStack.add(Route.AlbumDetail(albumId)) },
                            authCall = Jm3Graph.session.authCall,
                        )
                    }
                    entry<Route.AlbumDetail>(
                        transition = albumDetailTransition,
                        swipeDismiss = NavSwipeDirection.LeftToRight,
                    ) { route ->
                        AlbumDetailScreen(
                            albumId = route.albumId,
                            initialTitle = route.initialTitle,
                            initialCoverUrl = route.initialCoverUrl,
                            onBack = popBack,
                            onOpenReader = { photoId -> backStack.add(Route.Reader(photoId)) },
                            onOpenAlbum = { albumId -> backStack.add(Route.AlbumDetail(albumId)) },
                            onOpenSearch = { query, scope -> backStack.add(Route.Search(query, scope)) },
                            onOpenLogin = { backStack.add(Route.Login) },
                            snackbarHostState = snackbarHostState,
                            gateway = Jm3Graph.api.gateway,
                            authCall = Jm3Graph.session.authCall,
                            history = Jm3Graph.library.history,
                            downloadManager = Jm3Graph.downloads.manager,
                            account = Jm3Graph.session.account,
                        )
                    }
                    entry<Route.Reader>(swipeDismiss = NavSwipeDirection.LeftToRight) { route ->
                        ReaderScreen(
                            deps = deps,
                            photoId = route.photoId,
                            authCall = Jm3Graph.session.authCall,
                            gateway = Jm3Graph.api.gateway,
                            downloadManager = Jm3Graph.downloads.manager,
                            settings = Jm3Graph.preferences.repository,
                            history = Jm3Graph.library.history,
                            downloadFiles = Jm3Graph.downloads.files,
                            account = Jm3Graph.session.account,
                            onBack = { backStack.removeLastOrNull() },
                            onOpenChapter = { photoId ->
                                // 同层替换：返回键回到详情页，而不是逐章回退
                                if (backStack.isNotEmpty()) backStack[backStack.lastIndex] = Route.Reader(photoId)
                            },
                            onOpenLogin = { backStack.add(Route.Login) },
                            snackbarHostState = snackbarHostState,
                        )
                    }
                    entry<Route.Novel>(swipeDismiss = NavSwipeDirection.LeftToRight) {
                        NovelListScreen(
                            deps = deps,
                            onBack = { backStack.removeLastOrNull() },
                            onOpenNovel = { novelId -> backStack.add(Route.NovelDetail(novelId)) },
                            onOpenLogin = { backStack.add(Route.Login) },
                            snackbarHostState = snackbarHostState,
                            authCall = Jm3Graph.session.authCall,
                        )
                    }
                    entry<Route.NovelDetail>(swipeDismiss = NavSwipeDirection.LeftToRight) { route ->
                        NovelDetailScreen(
                            deps = deps,
                            novelId = route.novelId,
                            onBack = { backStack.removeLastOrNull() },
                            onOpenNovel = { novelId -> backStack.add(Route.NovelDetail(novelId)) },
                            onOpenReader = { novelId, chapterId ->
                                backStack.add(Route.NovelReader(novelId, chapterId))
                            },
                            onOpenSearch = { query, scope -> backStack.add(Route.Search(query, scope)) },
                            onOpenLogin = { backStack.add(Route.Login) },
                            snackbarHostState = snackbarHostState,
                            authCall = Jm3Graph.session.authCall,
                        )
                    }
                    entry<Route.NovelReader>(swipeDismiss = NavSwipeDirection.LeftToRight) { route ->
                        NovelReaderScreen(
                            deps = deps,
                            novelId = route.novelId,
                            initialChapterId = route.chapterId,
                            onBack = { backStack.removeLastOrNull() },
                            onOpenLogin = { backStack.add(Route.Login) },
                            snackbarHostState = snackbarHostState,
                            authCall = Jm3Graph.session.authCall,
                        )
                    }
                    entry<Route.Library>(swipeDismiss = NavSwipeDirection.LeftToRight) {
                        LibraryScreen(
                            deps = deps,
                            onBack = { backStack.removeLastOrNull() },
                            onOpenCreator = { creatorId -> backStack.add(Route.Creator(creatorId)) },
                            onOpenWork = { workId -> backStack.add(Route.CreatorWork(workId)) },
                            snackbarHostState = snackbarHostState,
                            authCall = Jm3Graph.session.authCall,
                            onOpenLogin = { backStack.add(Route.Login) },
                        )
                    }
                    entry<Route.Creator>(swipeDismiss = NavSwipeDirection.LeftToRight) { route ->
                        CreatorWorksScreen(
                            deps = deps,
                            creatorId = route.creatorId,
                            onBack = { backStack.removeLastOrNull() },
                            onOpenCreator = { creatorId -> backStack.add(Route.Creator(creatorId)) },
                            onOpenWork = { workId -> backStack.add(Route.CreatorWork(workId)) },
                            snackbarHostState = snackbarHostState,
                            authCall = Jm3Graph.session.authCall,
                            onOpenLogin = { backStack.add(Route.Login) },
                        )
                    }
                    entry<Route.CreatorWork>(swipeDismiss = NavSwipeDirection.LeftToRight) { route ->
                        CreatorWorkDetailScreen(
                            deps = deps,
                            workId = route.workId,
                            onBack = { backStack.removeLastOrNull() },
                            onOpenWork = { workId -> backStack.add(Route.CreatorWork(workId)) },
                            snackbarHostState = snackbarHostState,
                            authCall = Jm3Graph.session.authCall,
                            onOpenLogin = { backStack.add(Route.Login) },
                        )
                    }
                    entry<Route.Search>(swipeDismiss = NavSwipeDirection.LeftToRight) { route ->
                        SearchScreen(
                            deps = deps,
                            query = route.query,
                            scope = route.scope,
                            gateway = Jm3Graph.gateway,
                            authCall = Jm3Graph.session.authCall,
                            onBack = { backStack.removeLastOrNull() },
                            onOpenAlbum = { albumId -> backStack.add(Route.AlbumDetail(albumId)) },
                        )
                    }
                }
                AlbumHeroOverlay(host = heroHost, modifier = Modifier.fillMaxSize())
                // 更新页自身也会检查并弹窗，停留在更新页时不再叠加启动期提示
                if (backStack.lastOrNull() != Route.Update) {
                    UpdateStartupPrompt(
                        deps = deps,
                        onOpenUpdatePage = { backStack.add(Route.Update) },
                    )
                }
            }
        }
    }
}
