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
import com.jm3.core.model.CommentItem
import com.jm3.core.model.CommentPage
import com.jm3.core.session.SessionOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.SnackbarHostState

/**
 * 评论抽屉：拉取、发表与回复都在这里，抽屉显隐、草稿与回复目标交给 rememberSaveable 托管；
 * 评论列表不可序列化，旋转恢复后由 ReaderCommentRefetchEffect 补拉一次。
 */
internal class ReaderComments(
    private val deps: ScreenDeps,
    private val albumId: String,
    private val scope: CoroutineScope,
    private val snackbarHostState: SnackbarHostState,
    private val loggedIn: State<Boolean>,
    private val onOpenLogin: () -> Unit,
    val show: MutableState<Boolean>,
    val text: MutableState<String>,
    val replyTarget: MutableState<CommentItem?>,
) {
    var comments by mutableStateOf<CommentPage?>(null)
        private set

    var loading by mutableStateOf(false)
        private set

    var failed by mutableStateOf(false)
        private set

    var expired by mutableStateOf(false)
        private set

    var busy by mutableStateOf(false)
        private set

    var replyBusy by mutableStateOf(false)
        private set

    fun open() {
        show.value = true
        if (comments == null) load()
    }

    fun load() {
        loading = true
        failed = false
        expired = false
        scope.launch {
            val outcome = deps.authCall.call { deps.gateway.comments(albumId) }
            loading = false
            when (outcome) {
                is SessionOutcome.Ok -> comments = outcome.value
                // 内联「加载失败 + 重试」对失效是误导：重试必然再吃一次 401
                SessionOutcome.Expired -> {
                    expired = true
                    reportReaderAction(snackbarHostState, outcome, "评论加载失败", onOpenLogin)
                }
                is SessionOutcome.Failed -> failed = true
            }
        }
    }

    fun send() {
        if (!requireLogin() || busy || text.value.isBlank()) return
        busy = true
        val body = text.value.trim()
        scope.launch {
            val outcome = deps.authCall.call { deps.gateway.postComment(albumId, body) }
            busy = false
            if (outcome is SessionOutcome.Ok) {
                text.value = ""
                snackbarHostState.showSnackbar("评论已发表")
                load()
            } else {
                reportReaderAction(snackbarHostState, outcome, "发表失败", onOpenLogin)
            }
        }
    }

    fun reply(body: String) {
        val target = replyTarget.value ?: return
        if (body.isBlank() || replyBusy) return
        replyBusy = true
        scope.launch {
            val outcome = deps.authCall.call { deps.gateway.replyComment(albumId, body, target.id) }
            replyBusy = false
            if (outcome is SessionOutcome.Ok) {
                replyTarget.value = null
                snackbarHostState.showSnackbar("回复已发送")
                load()
            } else {
                reportReaderAction(snackbarHostState, outcome, "回复失败", onOpenLogin)
            }
        }
    }

    private fun requireLogin(): Boolean {
        if (loggedIn.value) return true
        onOpenLogin()
        return false
    }
}

/** 评论抽屉的状态挂在组合里，换章（albumId 变化）时重置列表，草稿与回复目标按章节保存。 */
@Composable
internal fun rememberReaderComments(
    deps: ScreenDeps,
    albumId: String,
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    loggedIn: Boolean,
    onOpenLogin: () -> Unit,
): ReaderComments {
    val loggedInState = rememberUpdatedState(loggedIn)
    val show = rememberSaveable { mutableStateOf(false) }
    val text = rememberSaveable(albumId) { mutableStateOf("") }
    val replyTarget = rememberSaveable(albumId, stateSaver = CommentItemNullableSaver) {
        mutableStateOf<CommentItem?>(null)
    }
    return remember(albumId) {
        ReaderComments(
            deps = deps,
            albumId = albumId,
            scope = scope,
            snackbarHostState = snackbarHostState,
            loggedIn = loggedInState,
            onOpenLogin = onOpenLogin,
            show = show,
            text = text,
            replyTarget = replyTarget,
        )
    }
}

