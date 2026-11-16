package com.jm3.app.miuix.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.jm3.app.miuix.ScreenDeps
import com.jm3.core.session.SessionOutcome
import com.jm3.core.settings.ReaderSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.SnackbarHostState

/** 末页追更引导：勾选「3天不要显示」时写入静默截止时间，追踪成功后收起。 */
internal class ReaderTrackPrompt(
    private val deps: ScreenDeps,
    private val albumId: String,
    private val scope: CoroutineScope,
    private val settings: ReaderSettings,
    private val snackbarHostState: SnackbarHostState,
    private val loggedIn: State<Boolean>,
    private val onOpenLogin: () -> Unit,
    val show: MutableState<Boolean>,
    val shown: MutableState<Boolean>,
) {
    var muteChecked by mutableStateOf(false)

    var busy by mutableStateOf(false)
        private set

    /** 关闭末页追更引导：勾选「3天不要显示」时写入静默截止时间。 */
    fun close() {
        show.value = false
        if (muteChecked) {
            scope.launch {
                settings.setTrackPromptMuteUntil(System.currentTimeMillis() + TRACK_MUTE_DAYS_MS)
            }
        }
    }

    fun follow() {
        if (!requireLogin() || busy) return
        busy = true
        scope.launch {
            val outcome = deps.authCall.call { deps.gateway.setTracking(albumId) }
            busy = false
            if (outcome is SessionOutcome.Ok) {
                snackbarHostState.showSnackbar("已追踪连载")
            } else {
                reportReaderAction(snackbarHostState, outcome, "追踪失败", onOpenLogin)
            }
            close()
        }
    }

    private fun requireLogin(): Boolean {
        if (loggedIn.value) return true
        onOpenLogin()
        return false
    }
}

/** 追更引导的显隐与「已提示过」标记按章节保存，静默开关与忙态只需活在组合里。 */
@Composable
internal fun rememberReaderTrackPrompt(
    deps: ScreenDeps,
    albumId: String,
    photoId: String,
    scope: CoroutineScope,
    settings: ReaderSettings,
    snackbarHostState: SnackbarHostState,
    loggedIn: Boolean,
    onOpenLogin: () -> Unit,
): ReaderTrackPrompt {
    val loggedInState = rememberUpdatedState(loggedIn)
    val show = rememberSaveable(photoId) { mutableStateOf(false) }
    val shown = rememberSaveable(photoId) { mutableStateOf(false) }
    return remember(albumId, photoId) {
        ReaderTrackPrompt(
            deps = deps,
            albumId = albumId,
            scope = scope,
            settings = settings,
            snackbarHostState = snackbarHostState,
            loggedIn = loggedInState,
            onOpenLogin = onOpenLogin,
            show = show,
            shown = shown,
        )
    }
}

