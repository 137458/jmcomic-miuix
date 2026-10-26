package com.jm3.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.jm3.app.ui.AlbumDetailScreen
import com.jm3.app.ui.HomeScreen
import com.jm3.app.ui.ReaderScreen
import com.jm3.core.Jm3Gateway
import com.jm3.core.Jm3GatewayImpl
import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/** 轻量依赖图：协议网关全应用单例。 */
object Jm3Graph {
    val gateway: Jm3Gateway by lazy { Jm3GatewayImpl() }
}

@Serializable
sealed interface Route : NavKey {
    @Serializable
    data object Home : Route

    @Serializable
    data class AlbumDetail(val albumId: String) : Route

    @Serializable
    data class Reader(val photoId: String) : Route
}

@Composable
fun Jm3App() {
    val backStack = rememberNavBackStack(Route.Home)
    MiuixTheme(controller = remember { ThemeController(colorSchemeMode = ColorSchemeMode.System) }) {
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
        ) {
            entry<Route.Home> {
                HomeScreen(
                    gateway = Jm3Graph.gateway,
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
