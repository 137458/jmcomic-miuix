package com.jm3.app.miuix

import android.app.Activity
import android.app.Application
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
import com.jm3.app.miuix.ui.LocalWideLayout
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
import com.jm3.app.miuix.ui.WideLayout
import com.jm3.app.miuix.ui.NotificationsScreen
import com.jm3.app.miuix.ui.rememberWindowDpSize
import com.jm3.app.miuix.ui.toLayoutDpInt
import com.jm3.app.miuix.update.UpdateDownloader
import com.jm3.app.miuix.ui.coui.page.CouiAlbumDetailScreen
import com.jm3.app.miuix.ui.coui.page.CouiCreatorScreen
import com.jm3.app.miuix.ui.coui.page.CouiCreatorWorkScreen
import com.jm3.app.miuix.ui.coui.page.CouiFavoritesScreen
import com.jm3.app.miuix.ui.coui.page.CouiForumScreen
import com.jm3.app.miuix.ui.coui.page.CouiHistoryScreen
import com.jm3.app.miuix.ui.coui.page.CouiLibraryScreen
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
import com.jm3.app.miuix.ui.coui.theme.CouiTheme
import com.jm3.app.miuix.ui.glass.LocalGlassStyle
import com.jm3.app.miuix.ui.route.SkinRoute
import com.jm3.app.miuix.ui.route.SkinWindowBackdrop
import com.jm3.app.miuix.ui.skin.DynamicColorPolicy
import com.jm3.app.miuix.ui.skin.LocalSkin
import com.jm3.app.miuix.ui.skin.SkinMotion
import com.jm3.app.miuix.ui.skin.SkinTokens
import com.jm3.app.miuix.ui.skin.navMotionOrDefault
import com.jm3.app.miuix.ui.skin.skinTokensFor
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
import com.jm3.core.settings.UiSkin
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
import androidx.compose.ui.unit.LayoutDirection
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import top.yukonga.miuix.kmp.nav.transition.NavTransition
import top.yukonga.miuix.kmp.nav.transition.NavTransitionScope
import top.yukonga.miuix.kmp.nav.transition.navDirectionalTransition
import top.yukonga.miuix.kmp.nav.transition.navGraphicsTransition
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/**
 * 进程级入口：在任何 Activity、Service 或恢复组件创建之前完成 [Jm3Graph] 装配，
 * 避免后台拉起服务或系统恢复进程时触发未初始化异常。
 */
class Jm3Application : Application() {
    override fun onCreate() {
        super.onCreate()
        Jm3Graph.init(this)
    }
}

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

    private val initLock = Any()

    @Volatile
    private var initialized = false

    /**
     * 装配顺序即启动顺序：线路池先落进 [ApiDomain]（网关构造时读它），
     * 会话域拿设置存储当凭据密文库，下载域拿线路域当页源、拿本地库当台账。
     *
     * 幂等且线程安全：六个域全部装配完成后才将 [initialized] 置为 true，
     * 防止并发初始化期间其它线程读取到尚未赋值的域。
     */
    fun init(context: Context) {
        if (initialized) return
        synchronized(initLock) {
            if (initialized) return
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
            initialized = true
        }
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
    data class Reader(val photoId: String, val startPage: Int? = null) : Route

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
 * 详情页页面变换：两套皮肤统一采用只做 alpha 的原地渐变，不做位移。
 *
 * 共享元素按根坐标插值，页面若整宽平移，头图的根坐标矩形每一帧都在变，飞行落点就会漂，
 * 且会出现「页面往右跑、封面往左飞」的视觉撕裂；
 * 所以这一页和它覆盖的那一页都不平移，位移感与焦点完全交给飞行的封面本身。
 *
 * relativeDepth 是有符号的：进入时新页 -1 → 0、旧页 0 → 1；退出时旧页 0 → -1、下页 1 → 0。
 * 因此同一段 alpha 在两端天然是正反两向，且手势期间由手指直接驱动。
 */
private val albumDetailFadeTransform: GraphicsLayerScope.(NavTransitionScope) -> Unit = { scope ->
    if (scope.relativeDepth <= 0f) alpha = (1f + scope.relativeDepth).coerceIn(0f, 1f)
}

/**
 * 普通二级页面页面变换（COUI）：整页从右缘滑入、向右侧滑出，底层页面带 25% 视差位移与轻度变暗。
 */
private val couiSubPageSlideTransform: GraphicsLayerScope.(NavTransitionScope) -> Unit = { scope ->
    val depth = scope.relativeDepth
    val factor = if (scope.layoutDirection == LayoutDirection.Rtl) -1f else 1f
    if (depth <= 0f) {
        // 顶层页面跟随手势向右滑动出栈
        translationX = factor * (-depth).coerceIn(0f, 1f) * scope.layoutSize.width
    } else {
        // 底层页面跟随 25% 视差位移与微弱渐隐
        translationX = -factor * (depth * 0.25f).coerceIn(0f, 1f) * scope.layoutSize.width
        alpha = (1f - depth * 0.2f).coerceIn(0f, 1f)
    }
}

/**
 * 详情页转场：push / pop / predictivePop 各声明一段，pop 与 push 是同一个变换（镜像）。
 * 两套皮肤统一走渐变淡出，位移由共享封面主导；转场物理取各自皮肤令牌。
 */
private fun albumDetailTransitionFor(skin: SkinTokens): NavTransition {
    val motion = skin.motion.navMotionOrDefault()
    return navDirectionalTransition(
        navGraphicsTransition(
            opaqueDepth = 1f,
            dismissDirection = NavSwipeDirection.None,
            block = albumDetailFadeTransform,
            motion = motion,
        ),
        navGraphicsTransition(
            opaqueDepth = 1f,
            dismissDirection = NavSwipeDirection.None,
            block = albumDetailFadeTransform,
            motion = motion,
        ),
        navGraphicsTransition(
            opaqueDepth = 1f,
            dismissDirection = NavSwipeDirection.LeftToRight,
            block = albumDetailFadeTransform,
            motion = motion,
        ),
    )
}

/**
 * 普通二级页转场：ColorOS 下统一采用整页侧滑 + 底层视差，HyperOS 沿用 Miuix 默认。
 */
private fun subPageTransitionFor(skin: SkinTokens): NavTransition? {
    if (skin.id != UiSkin.COLOROS) return null
    val motion = skin.motion.navMotionOrDefault()
    return navDirectionalTransition(
        navGraphicsTransition(
            opaqueDepth = 1f,
            dismissDirection = NavSwipeDirection.None,
            block = couiSubPageSlideTransform,
            motion = motion,
        ),
        navGraphicsTransition(
            opaqueDepth = 1f,
            dismissDirection = NavSwipeDirection.None,
            block = couiSubPageSlideTransform,
            motion = motion,
        ),
        navGraphicsTransition(
            opaqueDepth = 1f,
            dismissDirection = NavSwipeDirection.LeftToRight,
            block = couiSubPageSlideTransform,
            motion = motion,
        ),
    )
}

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
    // 界面风格：null 表示从未选择，盘上不写键，展示按默认皮肤回落。
    var uiSkinName by rememberSaveable { mutableStateOf(UiSkin.HYPEROS.name) }
    val uiSkin = remember(uiSkinName) {
        UiSkin.entries.firstOrNull { it.name == uiSkinName } ?: UiSkin.HYPEROS
    }
    val skin = remember(uiSkin) { skinTokensFor(uiSkin) }
    // 动态取色只在 HyperOS 生效；ColorOS 固定自有色板，盘上开关值保留以便切回恢复。
    val effectiveDynamicColor = DynamicColorPolicy.effective(uiSkin, dynamicColor)
    val albumDetailTransition = remember(skin) { albumDetailTransitionFor(skin) }
    val subPageTransition = remember(skin) { subPageTransitionFor(skin) }
    val controller = remember(themeMode, effectiveDynamicColor, skin) {
        ThemeController(
            colorSchemeMode = themeMode.toColorSchemeMode(effectiveDynamicColor),
            lightColors = skin.lightColors,
            darkColors = skin.darkColors,
        )
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
        // 上层作品仍然留在栈里（详情 A 里再进详情 B）不算退出，否则会给 A 起一段停在起点的反向飞行
        val stillStacked = previous != null && backStack.any { (it as? Route.AlbumDetail)?.albumId == previous }
        if (previous != null && !stillStacked && heroHost.flight == null && heroHost.pending == null) {
            heroHost.startBackward(previous)
        }
        lastTopAlbumId = topAlbumId
    }

    // 启动顺序：先恢复主题与线路（切了线路时 applyDomains 会把会话注入新网关），最后再兜一次会话恢复。
    LaunchedEffect(Unit) {
        // 设置读盘失败也要往下走：会话恢复是登录态的起点，不能被主题或线路拖住。
        // 五句全是挂起调用，用 runCatchingSuspending：取消原样抛出，失败留一条事实日志。
        runCatchingSuspending {
            themeModeName = deps.settings.themeMode().name
            dynamicColor = deps.settings.dynamicColor()
            // 皮肤：盘上是 null 表示用户从未显式选择，保留默认且不回写键。
            deps.settings.uiSkinOrNull()?.let { uiSkinName = it.name }
            // 分页模式：用户在本进程内已显式改过就保留内存值，否则用盘上值。
            // 读盘发生在组合之后，盘上的旧值不该盖掉用户刚选的那个。
            deps.preferences.applyStartupPaginationMode(
                PaginationModePolicy.resolveStartupPaginationMode(
                    persisted = deps.settings.paginationMode(),
                    current = deps.preferences.paginationMode,
                    userModified = deps.preferences.paginationModeUserModified,
                )
            )
            // 底栏尺寸/形态：盘上是 null 表示用户从未显式设置，交给窗口断点在组合期决定。
            deps.preferences.applyStartupBottomBarSize(deps.settings.bottomBarSizeOrNull())
            deps.preferences.applyStartupBottomBarForm(deps.settings.bottomBarFormOrNull())
            // 宽屏导航：null 表示未显式设置，默认底栏在组合期解析。
            deps.preferences.applyStartupWideScreenNavigation(
                deps.settings.wideScreenNavigationOrNull(),
            )
            deps.applyDomains(deps.settings.apiDomains())
        }.onFailure { failure ->
            Jm3Log.warn("Jm3App", "启动读盘失败（" + failure.javaClass.name + "），改用默认设置")
        }
        deps.account.restore()
    }

    // 窗口宽高变化（折叠展开、分屏、旋转）会重建这份布局参数，各页随之重新判定列数与分栏
    val windowSize = rememberWindowDpSize()
    val wideLayout = remember(windowSize) {
        WideLayout(
            widthDp = windowSize.width.toLayoutDpInt(),
            heightDp = windowSize.height.toLayoutDpInt(),
        )
    }

    // 皮肤只替换主题取值与 CompositionLocal，不切换宿主、不重建页面树：
    // 换肤时导航栈、阅读器与下载任务状态原地保留（对齐 Eta 用根级 movableContentOf 保状态的结果）。
    MiuixTheme(controller = controller, textStyles = skin.textStyles) {
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
        CompositionLocalProvider(
            LocalSkin provides skin,
            LocalGlassStyle provides skin.glassStyle,
            LocalAlbumHeroHost provides heroHost,
            LocalWideLayout provides wideLayout,
        ) {
            // ColorOS 的菜单/弹窗材质需要窗口级背景源，且必须挂在窗口内容根节点：
            // 挂在路由里的话，路由之外的弹窗（启动更新提示）拿不到源，库会直接抛异常。
            SkinWindowBackdrop(uiSkin = uiSkin) { windowModifier ->
                Box(modifier = Modifier.fillMaxSize().then(windowModifier)) {
                    NavDisplay(
                        backStack = backStack,
                        onBack = popBack,
                        effects = NavDisplayEffects(
                            cornerClipRadius = screenCornerRadius,
                            backdropColor = MiuixTheme.colorScheme.surface,
                        ),
                    ) {
                        entry<Route.Main> {
                            SkinRoute(
                                uiSkin = uiSkin,
                                hyperOs = {
                                    MainScreen(
                                        deps = deps,
                                        unreadBadge = deps.session.unread,
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
                                },
                                colorOs = {
                                    CouiMainScreen(
                                        deps = deps,
                                        unreadBadge = deps.session.unread,
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
                                },
                            )
                        }
                        entry<Route.Login>(
                            transition = subPageTransition,
                            swipeDismiss = NavSwipeDirection.LeftToRight,
                        ) {
                            LoginScreen(account = deps.account, onBack = { backStack.removeLastOrNull() })
                        }
                        entry<Route.Favorites>(
                            transition = subPageTransition,
                            swipeDismiss = NavSwipeDirection.LeftToRight,
                        ) {
                            SkinRoute(
                                uiSkin = uiSkin,
                                hyperOs = {
                                    FavoritesScreen(
                                        deps = deps,
                                        onBack = { backStack.removeLastOrNull() },
                                        onOpenAlbum = { albumId -> backStack.add(Route.AlbumDetail(albumId)) },
                                        onOpenLogin = { backStack.add(Route.Login) },
                                    )
                                },
                                colorOs = {
                                    CouiFavoritesScreen(
                                        deps = deps,
                                        onBack = { backStack.removeLastOrNull() },
                                        onOpenAlbum = { albumId -> backStack.add(Route.AlbumDetail(albumId)) },
                                        onOpenLogin = { backStack.add(Route.Login) },
                                    )
                                },
                            )
                        }
                        entry<Route.History>(
                            transition = subPageTransition,
                            swipeDismiss = NavSwipeDirection.LeftToRight,
                        ) {
                            SkinRoute(
                                uiSkin = uiSkin,
                                hyperOs = {
                                    HistoryScreen(
                                        deps = deps,
                                        onBack = { backStack.removeLastOrNull() },
                                        onOpenAlbum = { albumId -> backStack.add(Route.AlbumDetail(albumId)) },
                                        onOpenReader = { photoId -> backStack.add(Route.Reader(photoId)) },
                                    )
                                },
                                colorOs = {
                                    CouiHistoryScreen(
                                        deps = deps,
                                        onBack = { backStack.removeLastOrNull() },
                                        onOpenAlbum = { albumId -> backStack.add(Route.AlbumDetail(albumId)) },
                                        onOpenReader = { photoId -> backStack.add(Route.Reader(photoId)) },
                                    )
                                },
                            )
                        }
                        entry<Route.Shelf>(
                            transition = subPageTransition,
                            swipeDismiss = NavSwipeDirection.LeftToRight,
                        ) {
                            SkinRoute(
                                uiSkin = uiSkin,
                                hyperOs = {
                                    ShelfScreen(
                                        deps = deps,
                                        onBack = { backStack.removeLastOrNull() },
                                        onOpenReader = { photoId -> backStack.add(Route.Reader(photoId)) },
                                    )
                                },
                                colorOs = {
                                    CouiShelfScreen(
                                        deps = deps,
                                        onBack = { backStack.removeLastOrNull() },
                                        onOpenReader = { photoId -> backStack.add(Route.Reader(photoId)) },
                                    )
                                },
                            )
                        }
                        entry<Route.Settings>(
                            transition = subPageTransition,
                            swipeDismiss = NavSwipeDirection.LeftToRight,
                        ) {
                            val openUpdate: () -> Unit = { backStack.add(Route.Update) }
                            val changeThemeMode: (ThemeMode) -> Unit = { mode ->
                                themeModeName = mode.name
                                scope.launch { deps.settings.setThemeMode(mode) }
                            }
                            val changeDynamicColor: (Boolean) -> Unit = { enabled ->
                                dynamicColor = enabled
                                scope.launch { deps.settings.setDynamicColor(enabled) }
                            }
                            val changeSkin: (UiSkin) -> Unit = { nextSkin ->
                                uiSkinName = nextSkin.name
                                scope.launch { deps.settings.setUiSkin(nextSkin) }
                            }
                            SkinRoute(
                                uiSkin = uiSkin,
                                hyperOs = {
                                    SettingsScreen(
                                        deps = deps,
                                        themeMode = themeMode,
                                        onThemeModeChange = changeThemeMode,
                                        dynamicColor = effectiveDynamicColor,
                                        onDynamicColorChange = changeDynamicColor,
                                        dynamicColorSupported = DynamicColorPolicy.supports(uiSkin),
                                        uiSkin = uiSkin,
                                        onUiSkinChange = changeSkin,
                                        onBack = { backStack.removeLastOrNull() },
                                        onOpenUpdate = openUpdate,
                                        snackbarHostState = snackbarHostState,
                                    )
                                },
                                colorOs = {
                                    CouiSettingsScreen(
                                        deps = deps,
                                        themeMode = themeMode,
                                        onThemeModeChange = changeThemeMode,
                                        dynamicColor = effectiveDynamicColor,
                                        onDynamicColorChange = changeDynamicColor,
                                        dynamicColorSupported = DynamicColorPolicy.supports(uiSkin),
                                        uiSkin = uiSkin,
                                        onUiSkinChange = changeSkin,
                                        onBack = { backStack.removeLastOrNull() },
                                        onOpenUpdate = openUpdate,
                                        snackbarHostState = snackbarHostState,
                                    )
                                },
                            )
                        }
                        entry<Route.Update>(
                            transition = subPageTransition,
                            swipeDismiss = NavSwipeDirection.LeftToRight,
                        ) {
                            SkinRoute(
                                uiSkin = uiSkin,
                                hyperOs = {
                                    UpdateScreen(
                                        deps = deps,
                                        onBack = { backStack.removeLastOrNull() },
                                    )
                                },
                                colorOs = {
                                    CouiUpdateScreen(
                                        deps = deps,
                                        onBack = { backStack.removeLastOrNull() },
                                        snackbarHostState = snackbarHostState,
                                    )
                                },
                            )
                        }
                        entry<Route.Week>(
                            transition = subPageTransition,
                            swipeDismiss = NavSwipeDirection.LeftToRight,
                        ) {
                            SkinRoute(
                                uiSkin = uiSkin,
                                hyperOs = {
                                    WeekScreen(
                                        deps = deps,
                                        onBack = { backStack.removeLastOrNull() },
                                        onOpenAlbum = { albumId -> backStack.add(Route.AlbumDetail(albumId)) },
                                        onOpenLogin = { backStack.add(Route.Login) },
                                    )
                                },
                                colorOs = {
                                    CouiWeekScreen(
                                        deps = deps,
                                        onBack = { backStack.removeLastOrNull() },
                                        onOpenAlbum = { albumId -> backStack.add(Route.AlbumDetail(albumId)) },
                                        onOpenLogin = { backStack.add(Route.Login) },
                                    )
                                },
                            )
                        }
                        entry<Route.Notifications>(
                            transition = subPageTransition,
                            swipeDismiss = NavSwipeDirection.LeftToRight,
                        ) {
                            SkinRoute(
                                uiSkin = uiSkin,
                                hyperOs = {
                                    NotificationsScreen(
                                        deps = deps,
                                        onBack = { backStack.removeLastOrNull() },
                                        onOpenAlbum = { albumId -> backStack.add(Route.AlbumDetail(albumId)) },
                                        snackbarHostState = snackbarHostState,
                                        unreadBadge = deps.session.unread,
                                        onOpenLogin = { backStack.add(Route.Login) },
                                    )
                                },
                                colorOs = {
                                    CouiNotificationsScreen(
                                        deps = deps,
                                        onBack = { backStack.removeLastOrNull() },
                                        snackbarHostState = snackbarHostState,
                                        unreadBadge = deps.session.unread,
                                        onOpenAlbum = { albumId -> backStack.add(Route.AlbumDetail(albumId)) },
                                        onOpenLogin = { backStack.add(Route.Login) },
                                    )
                                },
                            )
                        }
                        entry<Route.Tracking>(
                            transition = subPageTransition,
                            swipeDismiss = NavSwipeDirection.LeftToRight,
                        ) {
                            SkinRoute(
                                uiSkin = uiSkin,
                                hyperOs = {
                                    TrackingScreen(
                                        deps = deps,
                                        onBack = { backStack.removeLastOrNull() },
                                        onOpenAlbum = { albumId -> backStack.add(Route.AlbumDetail(albumId)) },
                                        snackbarHostState = snackbarHostState,
                                        onOpenLogin = { backStack.add(Route.Login) },
                                    )
                                },
                                colorOs = {
                                    CouiTrackingScreen(
                                        deps = deps,
                                        onBack = { backStack.removeLastOrNull() },
                                        onOpenAlbum = { albumId -> backStack.add(Route.AlbumDetail(albumId)) },
                                        snackbarHostState = snackbarHostState,
                                        onOpenLogin = { backStack.add(Route.Login) },
                                    )
                                },
                            )
                        }
                        entry<Route.Tasks>(
                            transition = subPageTransition,
                            swipeDismiss = NavSwipeDirection.LeftToRight,
                        ) {
                            SkinRoute(
                                uiSkin = uiSkin,
                                hyperOs = {
                                    TasksScreen(
                                        deps = deps,
                                        onBack = { backStack.removeLastOrNull() },
                                        snackbarHostState = snackbarHostState,
                                        onOpenLogin = { backStack.add(Route.Login) },
                                    )
                                },
                                colorOs = {
                                    CouiTasksScreen(
                                        deps = deps,
                                        onBack = { backStack.removeLastOrNull() },
                                        snackbarHostState = snackbarHostState,
                                        onOpenLogin = { backStack.add(Route.Login) },
                                    )
                                },
                            )
                        }
                        entry<Route.Profile>(
                            transition = subPageTransition,
                            swipeDismiss = NavSwipeDirection.LeftToRight,
                        ) {
                            SkinRoute(
                                uiSkin = uiSkin,
                                hyperOs = {
                                    ProfileScreen(
                                        deps = deps,
                                        onBack = { backStack.removeLastOrNull() },
                                        snackbarHostState = snackbarHostState,
                                        onOpenLogin = { backStack.add(Route.Login) },
                                    )
                                },
                                colorOs = {
                                    CouiProfileScreen(
                                        deps = deps,
                                        onBack = { backStack.removeLastOrNull() },
                                        snackbarHostState = snackbarHostState,
                                        onOpenLogin = { backStack.add(Route.Login) },
                                    )
                                },
                            )
                        }
                        entry<Route.WatchHistory>(
                            transition = subPageTransition,
                            swipeDismiss = NavSwipeDirection.LeftToRight,
                        ) {
                            SkinRoute(
                                uiSkin = uiSkin,
                                hyperOs = {
                                    WatchHistoryScreen(
                                        deps = deps,
                                        onBack = { backStack.removeLastOrNull() },
                                        onOpenAlbum = { albumId -> backStack.add(Route.AlbumDetail(albumId)) },
                                        snackbarHostState = snackbarHostState,
                                        onOpenLogin = { backStack.add(Route.Login) },
                                    )
                                },
                                colorOs = {
                                    CouiWatchHistoryScreen(
                                        deps = deps,
                                        onBack = { backStack.removeLastOrNull() },
                                        onOpenAlbum = { albumId -> backStack.add(Route.AlbumDetail(albumId)) },
                                        snackbarHostState = snackbarHostState,
                                        onOpenLogin = { backStack.add(Route.Login) },
                                    )
                                },
                            )
                        }
                        entry<Route.Forum>(
                            transition = subPageTransition,
                            swipeDismiss = NavSwipeDirection.LeftToRight,
                        ) {
                            SkinRoute(
                                uiSkin = uiSkin,
                                hyperOs = {
                                    ForumScreen(
                                        deps = deps,
                                        onBack = { backStack.removeLastOrNull() },
                                        onOpenLogin = { backStack.add(Route.Login) },
                                        snackbarHostState = snackbarHostState,
                                        onOpenAlbum = { albumId -> backStack.add(Route.AlbumDetail(albumId)) },
                                    )
                                },
                                colorOs = {
                                    CouiForumScreen(
                                        deps = deps,
                                        onBack = { backStack.removeLastOrNull() },
                                        onOpenLogin = { backStack.add(Route.Login) },
                                        snackbarHostState = snackbarHostState,
                                        onOpenAlbum = { albumId -> backStack.add(Route.AlbumDetail(albumId)) },
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
                                        onBack = popBack,
                                        onOpenReader = { photoId, startPage ->
                                            backStack.add(Route.Reader(photoId, startPage))
                                        },
                                        onOpenAlbum = { albumId -> backStack.add(Route.AlbumDetail(albumId)) },
                                        onOpenSearch = { query, scope -> backStack.add(Route.Search(query, scope)) },
                                        onOpenLogin = { backStack.add(Route.Login) },
                                        snackbarHostState = snackbarHostState,
                                        gateway = deps.gateway,
                                        authCall = deps.authCall,
                                        history = deps.history,
                                        downloadManager = deps.downloadManager,
                                        downloadFiles = deps.downloadFiles,
                                        account = deps.account,
                                        sessionCache = deps.sessionCache,
                                    )
                                },
                                colorOs = {
                                    CouiAlbumDetailScreen(
                                        albumId = route.albumId,
                                        initialTitle = route.initialTitle,
                                        initialCoverUrl = route.initialCoverUrl,
                                        onBack = popBack,
                                        onOpenReader = { photoId, startPage ->
                                            backStack.add(Route.Reader(photoId, startPage))
                                        },
                                        onOpenAlbum = { albumId -> backStack.add(Route.AlbumDetail(albumId)) },
                                        onOpenSearch = { query, scope -> backStack.add(Route.Search(query, scope)) },
                                        onOpenLogin = { backStack.add(Route.Login) },
                                        snackbarHostState = snackbarHostState,
                                        gateway = deps.gateway,
                                        authCall = deps.authCall,
                                        history = deps.history,
                                        downloadManager = deps.downloadManager,
                                        downloadFiles = deps.downloadFiles,
                                        account = deps.account,
                                        sessionCache = deps.sessionCache,
                                    )
                                },
                            )
                        }
                        entry<Route.Reader>(
                            transition = subPageTransition,
                            swipeDismiss = NavSwipeDirection.LeftToRight,
                        ) { route ->
                            SkinRoute(
                                uiSkin = uiSkin,
                                hyperOs = {
                                    ReaderScreen(
                                        deps = deps,
                                        photoId = route.photoId,
                                        startPage = route.startPage,
                                        settings = deps.settings,
                                        onBack = { backStack.removeLastOrNull() },
                                        onOpenChapter = { nextPageId ->
                                            // 同层替换：返回键回到详情页，而不是逐章回退
                                            if (backStack.isNotEmpty()) backStack[backStack.lastIndex] = Route.Reader(nextPageId)
                                        },
                                        onOpenLogin = { backStack.add(Route.Login) },
                                        snackbarHostState = snackbarHostState,
                                    )
                                },
                                colorOs = {
                                    CouiReaderScreen(
                                        deps = deps,
                                        photoId = route.photoId,
                                        startPage = route.startPage,
                                        settings = deps.settings,
                                        onBack = { backStack.removeLastOrNull() },
                                        onOpenChapter = { nextPageId ->
                                            // 同层替换：返回键回到详情页，而不是逐章回退
                                            if (backStack.isNotEmpty()) backStack[backStack.lastIndex] = Route.Reader(nextPageId)
                                        },
                                        onOpenLogin = { backStack.add(Route.Login) },
                                        snackbarHostState = snackbarHostState,
                                    )
                                },
                            )
                        }
                        entry<Route.Novel>(
                            transition = subPageTransition,
                            swipeDismiss = NavSwipeDirection.LeftToRight,
                        ) {
                            SkinRoute(
                                uiSkin = uiSkin,
                                hyperOs = {
                                    NovelListScreen(
                                        deps = deps,
                                        onBack = { backStack.removeLastOrNull() },
                                        onOpenNovel = { novelId -> backStack.add(Route.NovelDetail(novelId)) },
                                        onOpenLogin = { backStack.add(Route.Login) },
                                        snackbarHostState = snackbarHostState,
                                    )
                                },
                                colorOs = {
                                    CouiNovelListScreen(
                                        deps = deps,
                                        onBack = { backStack.removeLastOrNull() },
                                        onOpenNovel = { novelId -> backStack.add(Route.NovelDetail(novelId)) },
                                        onOpenLogin = { backStack.add(Route.Login) },
                                        snackbarHostState = snackbarHostState,
                                    )
                                },
                            )
                        }
                        entry<Route.NovelDetail>(
                            transition = subPageTransition,
                            swipeDismiss = NavSwipeDirection.LeftToRight,
                        ) { route ->
                            SkinRoute(
                                uiSkin = uiSkin,
                                hyperOs = {
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
                                    )
                                },
                                colorOs = {
                                    CouiNovelDetailScreen(
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
                                    )
                                },
                            )
                        }
                        entry<Route.NovelReader>(
                            transition = subPageTransition,
                            swipeDismiss = NavSwipeDirection.LeftToRight,
                        ) { route ->
                            SkinRoute(
                                uiSkin = uiSkin,
                                hyperOs = {
                                    NovelReaderScreen(
                                        deps = deps,
                                        novelId = route.novelId,
                                        initialChapterId = route.chapterId,
                                        onBack = { backStack.removeLastOrNull() },
                                        onOpenLogin = { backStack.add(Route.Login) },
                                        snackbarHostState = snackbarHostState,
                                    )
                                },
                                colorOs = {
                                    CouiNovelReaderScreen(
                                        deps = deps,
                                        novelId = route.novelId,
                                        initialChapterId = route.chapterId,
                                        onBack = { backStack.removeLastOrNull() },
                                        onOpenLogin = { backStack.add(Route.Login) },
                                        snackbarHostState = snackbarHostState,
                                    )
                                },
                            )
                        }
                        entry<Route.Library>(
                            transition = subPageTransition,
                            swipeDismiss = NavSwipeDirection.LeftToRight,
                        ) {
                            SkinRoute(
                                uiSkin = uiSkin,
                                hyperOs = {
                                    LibraryScreen(
                                        deps = deps,
                                        onBack = { backStack.removeLastOrNull() },
                                        onOpenCreator = { creatorId -> backStack.add(Route.Creator(creatorId)) },
                                        onOpenWork = { workId -> backStack.add(Route.CreatorWork(workId)) },
                                        snackbarHostState = snackbarHostState,
                                        onOpenLogin = { backStack.add(Route.Login) },
                                    )
                                },
                                colorOs = {
                                    CouiLibraryScreen(
                                        deps = deps,
                                        onBack = { backStack.removeLastOrNull() },
                                        onOpenCreator = { creatorId -> backStack.add(Route.Creator(creatorId)) },
                                        onOpenWork = { workId -> backStack.add(Route.CreatorWork(workId)) },
                                        snackbarHostState = snackbarHostState,
                                        onOpenLogin = { backStack.add(Route.Login) },
                                    )
                                },
                            )
                        }
                        entry<Route.Creator>(
                            transition = subPageTransition,
                            swipeDismiss = NavSwipeDirection.LeftToRight,
                        ) { route ->
                            SkinRoute(
                                uiSkin = uiSkin,
                                hyperOs = {
                                    CreatorWorksScreen(
                                        deps = deps,
                                        creatorId = route.creatorId,
                                        onBack = { backStack.removeLastOrNull() },
                                        onOpenCreator = { creatorId -> backStack.add(Route.Creator(creatorId)) },
                                        onOpenWork = { workId -> backStack.add(Route.CreatorWork(workId)) },
                                        snackbarHostState = snackbarHostState,
                                        onOpenLogin = { backStack.add(Route.Login) },
                                    )
                                },
                                colorOs = {
                                    CouiCreatorScreen(
                                        deps = deps,
                                        creatorId = route.creatorId,
                                        onBack = { backStack.removeLastOrNull() },
                                        onOpenWork = { workId -> backStack.add(Route.CreatorWork(workId)) },
                                        snackbarHostState = snackbarHostState,
                                        onOpenLogin = { backStack.add(Route.Login) },
                                    )
                                },
                            )
                        }
                        entry<Route.CreatorWork>(
                            transition = subPageTransition,
                            swipeDismiss = NavSwipeDirection.LeftToRight,
                        ) { route ->
                            SkinRoute(
                                uiSkin = uiSkin,
                                hyperOs = {
                                    CreatorWorkDetailScreen(
                                        deps = deps,
                                        workId = route.workId,
                                        onBack = { backStack.removeLastOrNull() },
                                        onOpenWork = { workId -> backStack.add(Route.CreatorWork(workId)) },
                                        snackbarHostState = snackbarHostState,
                                        onOpenLogin = { backStack.add(Route.Login) },
                                    )
                                },
                                colorOs = {
                                    CouiCreatorWorkScreen(
                                        deps = deps,
                                        workId = route.workId,
                                        onBack = { backStack.removeLastOrNull() },
                                        onOpenWork = { workId -> backStack.add(Route.CreatorWork(workId)) },
                                        onOpenLogin = { backStack.add(Route.Login) },
                                    )
                                },
                            )
                        }
                        entry<Route.Search>(
                            transition = subPageTransition,
                            swipeDismiss = NavSwipeDirection.LeftToRight,
                        ) { route ->
                            SkinRoute(
                                uiSkin = uiSkin,
                                hyperOs = {
                                    SearchScreen(
                                        deps = deps,
                                        query = route.query,
                                        scope = route.scope,
                                        onBack = { backStack.removeLastOrNull() },
                                        onOpenAlbum = { albumId -> backStack.add(Route.AlbumDetail(albumId)) },
                                        onOpenLogin = { backStack.add(Route.Login) },
                                    )
                                },
                                colorOs = {
                                    CouiSearchScreen(
                                        deps = deps,
                                        query = route.query,
                                        scope = route.scope,
                                        onBack = { backStack.removeLastOrNull() },
                                        onOpenAlbum = { albumId -> backStack.add(Route.AlbumDetail(albumId)) },
                                        onOpenLogin = { backStack.add(Route.Login) },
                                    )
                                },
                            )
                        }
                    }
                    AlbumHeroOverlay(host = heroHost, modifier = Modifier.fillMaxSize())
                    // 更新页自身也会检查并弹窗，停留在更新页时不再叠加启动期提示；
                    // 弹窗在路由外组合，ColorOS 下须先包进 CouiTheme，颜色与深浅标记才和当前皮肤同步。
                    if (backStack.lastOrNull() != Route.Update) {
                        if (uiSkin == UiSkin.COLOROS) {
                            CouiTheme {
                                UpdateStartupPrompt(
                                    deps = deps,
                                    onOpenUpdatePage = { backStack.add(Route.Update) },
                                )
                            }
                        } else {
                            UpdateStartupPrompt(
                                deps = deps,
                                onOpenUpdatePage = { backStack.add(Route.Update) },
                            )
                        }
                    }
                }
            }
        }
    }
}
