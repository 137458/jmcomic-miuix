package com.jm3.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.room.Room
import com.jm3.app.data.AccountManager
import com.jm3.app.data.SessionStore
import com.jm3.app.data.download.DownloadManager
import com.jm3.app.data.download.DownloadRecordStore
import com.jm3.app.data.download.FileDownloadStore
import com.jm3.app.data.download.GatewayPageFetcher
import com.jm3.app.data.download.GatewayTaskResolver
import com.jm3.app.data.history.HistoryDb
import com.jm3.app.data.history.MIGRATION_1_2
import com.jm3.app.data.history.RoomDownloadRepository
import com.jm3.app.data.history.RoomHistoryRepository
import com.jm3.app.ui.AlbumDetailScreen
import com.jm3.app.ui.FavoritesScreen
import com.jm3.app.ui.HistoryScreen
import com.jm3.app.ui.LoginScreen
import com.jm3.app.ui.MainScreen
import com.jm3.app.ui.ReaderScreen
import com.jm3.app.ui.ShelfScreen
import com.jm3.core.Jm3Gateway
import com.jm3.core.Jm3GatewayImpl
import com.jm3.core.download.DownloadQueue
import com.jm3.core.download.DownloadStore
import com.jm3.core.history.HistoryRepository
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/** 轻量依赖图：协议网关/账号/阅读历史/本地书架全应用单例。 */
object Jm3Graph {

    @Volatile
    private var contextRef: Context? = null

    val gateway: Jm3Gateway by lazy { Jm3GatewayImpl() }

    val account: AccountManager by lazy { AccountManager(gateway, SessionStore(appContext())) }

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
        val pageSource = GatewayPageFetcher(gateway)
        DownloadManager(
            queue = DownloadQueue(
                fetcher = pageSource,
                store = downloadFiles,
                resolver = GatewayTaskResolver(gateway),
            ),
            fileStore = downloadFiles,
            recordStore = downloadStore,
            pageSource = pageSource,
            appScope = appScope,
        )
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
    data class AlbumDetail(val albumId: String) : Route

    @Serializable
    data class Reader(val photoId: String) : Route
}

@Composable
fun Jm3App() {
    val backStack = rememberNavBackStack(Route.Main)
    LaunchedEffect(Unit) { Jm3Graph.account.restore() }
    MiuixTheme(controller = remember { ThemeController(colorSchemeMode = ColorSchemeMode.System) }) {
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
        ) {
            entry<Route.Main> {
                MainScreen(
                    gateway = Jm3Graph.gateway,
                    account = Jm3Graph.account,
                    onOpenAlbum = { albumId -> backStack.add(Route.AlbumDetail(albumId)) },
                    onOpenLogin = { backStack.add(Route.Login) },
                    onOpenFavorites = { backStack.add(Route.Favorites) },
                    onOpenHistory = { backStack.add(Route.History) },
                    onOpenShelf = { backStack.add(Route.Shelf) },
                )
            }
            entry<Route.Login> {
                LoginScreen(account = Jm3Graph.account, onBack = { backStack.removeLastOrNull() })
            }
            entry<Route.Favorites> {
                FavoritesScreen(
                    onBack = { backStack.removeLastOrNull() },
                    onOpenAlbum = { albumId -> backStack.add(Route.AlbumDetail(albumId)) },
                )
            }
            entry<Route.History> {
                HistoryScreen(
                    onBack = { backStack.removeLastOrNull() },
                    onOpenAlbum = { albumId -> backStack.add(Route.AlbumDetail(albumId)) },
                )
            }
            entry<Route.Shelf> {
                ShelfScreen(
                    onBack = { backStack.removeLastOrNull() },
                    onOpenReader = { photoId -> backStack.add(Route.Reader(photoId)) },
                )
            }
            entry<Route.AlbumDetail> { route ->
                AlbumDetailScreen(
                    albumId = route.albumId,
                    onBack = { backStack.removeLastOrNull() },
                    onOpenReader = { photoId -> backStack.add(Route.Reader(photoId)) },
                )
            }
            entry<Route.Reader> { route ->
                ReaderScreen(photoId = route.photoId, onBack = { backStack.removeLastOrNull() })
            }
        }
    }
}
