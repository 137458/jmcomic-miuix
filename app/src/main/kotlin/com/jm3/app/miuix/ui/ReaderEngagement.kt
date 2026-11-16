package com.jm3.app.miuix.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.jm3.app.miuix.ScreenDeps
import com.jm3.core.session.SessionOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.SnackbarHostState

/** 动作型调用的统一落点：失效给登录入口，失败给「动作：原因」。 */
internal suspend fun reportReaderAction(
    snackbarHostState: SnackbarHostState,
    outcome: SessionOutcome<*>,
    failurePrefix: String,
    onOpenLogin: () -> Unit,
) {
    outcome.notifyAction(
        notify = { snackbarHostState.showSnackbar(it) },
        failurePrefix = failurePrefix,
        onExpired = onOpenLogin,
    )
}

/** 收藏与喜欢：详情页改过喜欢状态时以共享表为准，避免回来读到的还是首载快照。 */
internal class ReaderEngagement(
    private val deps: ScreenDeps,
    private val albumId: String,
    private val scope: CoroutineScope,
    private val snackbarHostState: SnackbarHostState,
    private val loggedIn: State<Boolean>,
    private val onOpenLogin: () -> Unit,
    favoritedInit: Boolean,
    likedInit: Boolean,
) {
    var favorited by mutableStateOf(favoritedInit)
        private set

    var liked by mutableStateOf(deps.sessionCache.engagement.likedOf(albumId) ?: likedInit)
        private set

    var busy by mutableStateOf(false)
        private set

    fun toggleFavorite() {
        if (!requireLogin() || busy) return
        busy = true
        scope.launch {
            val outcome = deps.authCall.call { deps.gateway.toggleFavorite(albumId) }
            busy = false
            if (outcome is SessionOutcome.Ok) {
                favorited = !favorited
                snackbarHostState.showSnackbar(if (favorited) "已收藏" else "已取消收藏")
            } else {
                reportReaderAction(snackbarHostState, outcome, "收藏失败", onOpenLogin)
            }
        }
    }

    fun toggleLike() {
        if (!requireLogin() || busy) return
        val next = !liked
        busy = true
        scope.launch {
            val outcome = deps.authCall.call { deps.gateway.toggleLike(albumId) }
            busy = false
            if (outcome is SessionOutcome.Ok) {
                liked = next
                deps.sessionCache.engagement.setLiked(albumId, next)
                snackbarHostState.showSnackbar(if (next) "已喜欢" else "已取消喜欢")
            } else {
                reportReaderAction(snackbarHostState, outcome, "操作失败", onOpenLogin)
            }
        }
    }

    private fun requireLogin(): Boolean {
        if (loggedIn.value) return true
        onOpenLogin()
        return false
    }
}

/** 收藏/喜欢的状态挂在组合里，换章（albumId 变化）时随首载快照重置。 */
@Composable
internal fun rememberReaderEngagement(
    deps: ScreenDeps,
    albumId: String,
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    loggedIn: Boolean,
    onOpenLogin: () -> Unit,
    favoritedInit: Boolean,
    likedInit: Boolean,
): ReaderEngagement {
    val loggedInState = rememberUpdatedState(loggedIn)
    return remember(albumId) {
        ReaderEngagement(
            deps = deps,
            albumId = albumId,
            scope = scope,
            snackbarHostState = snackbarHostState,
            loggedIn = loggedInState,
            onOpenLogin = onOpenLogin,
            favoritedInit = favoritedInit,
            likedInit = likedInit,
        )
    }
}

