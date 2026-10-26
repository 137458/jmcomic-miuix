package com.jm3.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.room.Room
import com.jm3.app.data.AccountManager
import com.jm3.app.data.SessionStore
import com.jm3.app.data.history.HistoryDb
import com.jm3.app.data.history.RoomHistoryRepository
import com.jm3.app.ui.AlbumDetailScreen
import com.jm3.app.ui.FavoritesScreen
import com.jm3.app.ui.HistoryScreen
import com.jm3.app.ui.LoginScreen
import com.jm3.app.ui.MainScreen
import com.jm3.app.ui.ReaderScreen
import com.jm3.core.Jm3Gateway
import com.jm3.core.Jm3GatewayImpl
import com.jm3.core.history.HistoryRepository
import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/** 轻量依赖图：协议网关/账号/阅读历史全应用单例。 */
object Jm3Graph {

    @Volatile
    private var contextRef: Context? = null

    val gateway: Jm3Gateway by lazy { Jm3GatewayImpl() }

    val account: AccountManager by lazy { AccountManager(gateway, SessionStore(appContext())) }

    val history: HistoryRepository by lazy {
        val db = Room.databaseBuilder(appContext(), HistoryDb::class.java, "jm3.db").build()
        RoomHistoryRepository(db.historyDao())
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
