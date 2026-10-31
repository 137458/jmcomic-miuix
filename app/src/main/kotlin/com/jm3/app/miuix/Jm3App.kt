package com.jm3.app.miuix

import android.app.Activity
import android.content.Context
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
import androidx.room.Room
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.request.crossfade
import com.jm3.app.miuix.data.AccountManager
import com.jm3.app.miuix.data.SessionStore
import com.jm3.app.miuix.data.SettingsStore
import com.jm3.app.miuix.data.download.DownloadManager
import com.jm3.app.miuix.data.download.DownloadRecordStore
import com.jm3.app.miuix.data.download.FileDownloadStore
import com.jm3.app.miuix.data.download.GatewayPageFetcher
import com.jm3.app.miuix.data.download.GatewayTaskResolver
import com.jm3.app.miuix.data.history.HistoryDb
import com.jm3.app.miuix.data.history.MIGRATION_1_2
import com.jm3.app.miuix.data.history.RoomDownloadRepository
import com.jm3.app.miuix.data.history.RoomHistoryRepository
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
import com.jm3.app.miuix.ui.SettingsScreen
import com.jm3.app.miuix.ui.ShelfScreen
import com.jm3.app.miuix.ui.TasksScreen
import com.jm3.app.miuix.ui.UpdateScreen
import com.jm3.app.miuix.ui.TrackingScreen
import com.jm3.app.miuix.ui.WatchHistoryScreen
import com.jm3.app.miuix.ui.WeekScreen
import com.jm3.app.miuix.ui.NotificationsScreen
import com.jm3.core.GatewayFactory
import com.jm3.core.Jm3Gateway
import com.jm3.core.download.DownloadQueue
import com.jm3.core.download.DownloadStore
import com.jm3.core.history.HistoryRepository
import com.jm3.core.settings.SettingsRepository
import com.jm3.core.settings.PaginationMode
import com.jm3.core.settings.ThemeMode
import com.jm3.core.model.SearchScope
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack
import top.yukonga.miuix.kmp.nav.core.rememberNavSystemCornerRadius
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import top.yukonga.miuix.kmp.nav.transition.navGraphicsTransition
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/** 轻量依赖图：协议网关/账号/设置/阅读历史/本地书架全应用单例。 */
object Jm3Graph {

    @Volatile
    private var contextRef: Context? = null

    /** 线路切换后的网关实例；组合内读取可感知切换并重组。 */
    private var gatewayOverride by mutableStateOf<Jm3Gateway?>(null)

    /** 当前生效网关：线路覆盖优先，否则库默认解析。 */
    val gateway: Jm3Gateway
        get() = gatewayOverride ?: defaultGateway

    private val defaultGateway: Jm3Gateway by lazy { GatewayFactory.create(emptyList()) }

    val sessionStore: SessionStore by lazy { SessionStore(appContext()) }

    val settings: SettingsRepository by lazy { SettingsStore(appContext()) }

    val account: AccountManager by lazy { AccountManager({ gateway }, sessionStore) }

    /** 信箱未读数：登录后刷新，供底栏「我的」角标与我的页入口共用。 */
    val unreadNotifications = MutableStateFlow(0)

    /** 列表分页模式：设置页写入后各列表页立即生效（跨 tab 无需重建）。 */
    var paginationMode by mutableStateOf(PaginationMode.INFINITE)

    /** 重新统计未读通知；未登录归零，请求失败保留上次结果避免角标闪烁。 */
    fun refreshUnreadNotifications() {
        if (account.user.value == null) {
            unreadNotifications.value = 0
            return
        }
        appScope.launch {
            runCatching { gateway.notifications().items.count { !it.read } }
                .onSuccess { unreadNotifications.value = it }
        }
    }

    /** 历史与下载共用一个 Room 库（jm3.db），v2 起含 downloads 表。 */
    private val db: HistoryDb by lazy {
        Room.databaseBuilder(appContext(), HistoryDb::class.java, "jm3.db")
            .addMigrations(MIGRATION_1_2)
            .build()
    }

    val history: HistoryRepository by lazy { RoomHistoryRepository(db.historyDao()) }

    val downloadStore: DownloadRecordStore by lazy { RoomDownloadRepository(db.downloadDao()) }

    /** 下载页文件存储：filesDir/downloads/{photoId}/{index:05}.jpg。 */
    val downloadFiles: DownloadStore by lazy {
        FileDownloadStore(File(appContext().filesDir, "downloads"))
    }

    val appScope: CoroutineScope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.IO) }

    val downloadManager: DownloadManager by lazy {
        val pageSource = GatewayPageFetcher { gateway }
        DownloadManager(
            queue = DownloadQueue(
                fetcher = pageSource,
                store = downloadFiles,
                resolver = GatewayTaskResolver { gateway },
            ),
            fileStore = downloadFiles,
            recordStore = downloadStore,
            pageSource = pageSource,
            appScope = appScope,
        )
    }

    /** 应用自定义 API 线路：重建网关并把已持久化会话迁移过去；空表回库默认解析。 */
    suspend fun applyDomains(apiDomains: List<String>) {
        // 旧网关的 HTTP 客户端持有连接池，切线路后不再可达：迁移完会话即主动释放。
        val previous = gatewayOverride
        val next = GatewayFactory.create(apiDomains)
        runCatching { sessionStore.load() }.getOrNull()?.let { session ->
            runCatching { next.restoreSession(session) }
        }
        gatewayOverride = next
        previous?.let { old -> runCatching { old.close() } }
    }

    private fun appContext(): Context =
        contextRef ?: error("Jm3Graph.init(context) 未初始化")

    fun init(context: Context) {
        contextRef = context.applicationContext
    }
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
 * 详情页进场转场：只做 alpha，不做位移。
 *
 * 共享元素按根坐标插值，页面若整宽平移，头图的根坐标矩形每一帧都在变，飞行落点就会漂；
 * 所以这一页和它覆盖的那一页都不平移，位移感交给飞行的封面本身。
 */
private val albumDetailTransition = navGraphicsTransition(
    opaqueDepth = 1f,
    dismissDirection = NavSwipeDirection.LeftToRight,
) { scope ->
    if (scope.relativeDepth <= 0f) alpha = (1f + scope.relativeDepth).coerceIn(0f, 1f)
}

@Composable
fun Jm3App() {
    // 全局图片加载器：所有 AsyncImage 共享，交叉淡入消除列表滚动时的硬切
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context).crossfade(true).build()
    }
    // 显式指定路由超类型，否则 saver 只按 Route.Main 序列化，压入二级页后保存会抛异常
    val backStack = rememberNavBackStack<Route>(Route.Main)
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
    // 退栈前先把头图送回列表卡片再出栈；任一端拿不到矩形时退化成普通退栈
    val popBack: () -> Unit = {
        (backStack.lastOrNull() as? Route.AlbumDetail)?.let { heroHost.startBackward(it.albumId) }
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

    // 启动顺序：先恢复主题与线路，最后 restore 会话——保证 Cookie 注入到切换后的网关。
    LaunchedEffect(Unit) {
        themeModeName = Jm3Graph.settings.themeMode().name
        dynamicColor = Jm3Graph.settings.dynamicColor()
        Jm3Graph.paginationMode = Jm3Graph.settings.paginationMode()
        Jm3Graph.applyDomains(Jm3Graph.settings.apiDomains())
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
                            gateway = Jm3Graph.gateway,
                            account = Jm3Graph.account,
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
                            onBack = { backStack.removeLastOrNull() },
                            onOpenAlbum = { albumId -> backStack.add(Route.AlbumDetail(albumId)) },
                        )
                    }
                    entry<Route.History>(swipeDismiss = NavSwipeDirection.LeftToRight) {
                        HistoryScreen(
                            onBack = { backStack.removeLastOrNull() },
                            onOpenAlbum = { albumId -> backStack.add(Route.AlbumDetail(albumId)) },
                            onOpenReader = { photoId -> backStack.add(Route.Reader(photoId)) },
                        )
                    }
                    entry<Route.Shelf>(swipeDismiss = NavSwipeDirection.LeftToRight) {
                        ShelfScreen(
                            onBack = { backStack.removeLastOrNull() },
                            onOpenReader = { photoId -> backStack.add(Route.Reader(photoId)) },
                        )
                    }
                    entry<Route.Settings>(swipeDismiss = NavSwipeDirection.LeftToRight) {
                        SettingsScreen(
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
                            onBack = { backStack.removeLastOrNull() },
                            onOpenUpdate = { backStack.add(Route.Update) },
                            snackbarHostState = snackbarHostState,
                        )
                    }
                    entry<Route.Update>(swipeDismiss = NavSwipeDirection.LeftToRight) {
                        UpdateScreen(onBack = { backStack.removeLastOrNull() })
                    }
                    entry<Route.Week>(swipeDismiss = NavSwipeDirection.LeftToRight) {
                        WeekScreen(
                            onBack = { backStack.removeLastOrNull() },
                            onOpenAlbum = { albumId -> backStack.add(Route.AlbumDetail(albumId)) },
                        )
                    }
                    entry<Route.Notifications>(swipeDismiss = NavSwipeDirection.LeftToRight) {
                        NotificationsScreen(
                            onBack = { backStack.removeLastOrNull() },
                            onOpenAlbum = { albumId -> backStack.add(Route.AlbumDetail(albumId)) },
                            snackbarHostState = snackbarHostState,
                        )
                    }
                    entry<Route.Tracking>(swipeDismiss = NavSwipeDirection.LeftToRight) {
                        TrackingScreen(
                            onBack = { backStack.removeLastOrNull() },
                            onOpenAlbum = { albumId -> backStack.add(Route.AlbumDetail(albumId)) },
                            snackbarHostState = snackbarHostState,
                        )
                    }
                    entry<Route.Tasks>(swipeDismiss = NavSwipeDirection.LeftToRight) {
                        TasksScreen(
                            onBack = { backStack.removeLastOrNull() },
                            snackbarHostState = snackbarHostState,
                        )
                    }
                    entry<Route.Profile>(swipeDismiss = NavSwipeDirection.LeftToRight) {
                        ProfileScreen(
                            onBack = { backStack.removeLastOrNull() },
                            snackbarHostState = snackbarHostState,
                        )
                    }
                    entry<Route.WatchHistory>(swipeDismiss = NavSwipeDirection.LeftToRight) {
                        WatchHistoryScreen(
                            onBack = { backStack.removeLastOrNull() },
                            onOpenAlbum = { albumId -> backStack.add(Route.AlbumDetail(albumId)) },
                            snackbarHostState = snackbarHostState,
                        )
                    }
                    entry<Route.Forum>(swipeDismiss = NavSwipeDirection.LeftToRight) {
                        ForumScreen(
                            onBack = { backStack.removeLastOrNull() },
                            onOpenLogin = { backStack.add(Route.Login) },
                            snackbarHostState = snackbarHostState,
                            onOpenAlbum = { albumId -> backStack.add(Route.AlbumDetail(albumId)) },
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
                        )
                    }
                    entry<Route.Reader>(swipeDismiss = NavSwipeDirection.LeftToRight) { route ->
                        ReaderScreen(
                            photoId = route.photoId,
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
                            onBack = { backStack.removeLastOrNull() },
                            onOpenNovel = { novelId -> backStack.add(Route.NovelDetail(novelId)) },
                            onOpenLogin = { backStack.add(Route.Login) },
                            snackbarHostState = snackbarHostState,
                        )
                    }
                    entry<Route.NovelDetail>(swipeDismiss = NavSwipeDirection.LeftToRight) { route ->
                        NovelDetailScreen(
                            novelId = route.novelId,
                            onBack = { backStack.removeLastOrNull() },
                            onOpenNovel = { novelId -> backStack.add(Route.NovelDetail(novelId)) },
                            onOpenReader = { novelId, chapterId ->
                                backStack.add(Route.NovelReader(novelId, chapterId))
                            },
                            onOpenSearch = { query, scope -> backStack.add(Route.Search(query, scope)) },
                            onOpenLogin = { backStack.add(Route.Login) },
                            snackbarHostState = snackbarHostState,
                        )
                    }
                    entry<Route.NovelReader>(swipeDismiss = NavSwipeDirection.LeftToRight) { route ->
                        NovelReaderScreen(
                            novelId = route.novelId,
                            initialChapterId = route.chapterId,
                            onBack = { backStack.removeLastOrNull() },
                            onOpenLogin = { backStack.add(Route.Login) },
                            snackbarHostState = snackbarHostState,
                        )
                    }
                    entry<Route.Library>(swipeDismiss = NavSwipeDirection.LeftToRight) {
                        LibraryScreen(
                            onBack = { backStack.removeLastOrNull() },
                            onOpenCreator = { creatorId -> backStack.add(Route.Creator(creatorId)) },
                            onOpenWork = { workId -> backStack.add(Route.CreatorWork(workId)) },
                            snackbarHostState = snackbarHostState,
                        )
                    }
                    entry<Route.Creator>(swipeDismiss = NavSwipeDirection.LeftToRight) { route ->
                        CreatorWorksScreen(
                            creatorId = route.creatorId,
                            onBack = { backStack.removeLastOrNull() },
                            onOpenCreator = { creatorId -> backStack.add(Route.Creator(creatorId)) },
                            onOpenWork = { workId -> backStack.add(Route.CreatorWork(workId)) },
                            snackbarHostState = snackbarHostState,
                        )
                    }
                    entry<Route.CreatorWork>(swipeDismiss = NavSwipeDirection.LeftToRight) { route ->
                        CreatorWorkDetailScreen(
                            workId = route.workId,
                            onBack = { backStack.removeLastOrNull() },
                            onOpenWork = { workId -> backStack.add(Route.CreatorWork(workId)) },
                            snackbarHostState = snackbarHostState,
                        )
                    }
                    entry<Route.Search>(swipeDismiss = NavSwipeDirection.LeftToRight) { route ->
                        SearchScreen(
                            query = route.query,
                            scope = route.scope,
                            onBack = { backStack.removeLastOrNull() },
                            onOpenAlbum = { albumId -> backStack.add(Route.AlbumDetail(albumId)) },
                        )
                    }
                }
                AlbumHeroOverlay(host = heroHost, modifier = Modifier.fillMaxSize())
            }
        }
    }
}
