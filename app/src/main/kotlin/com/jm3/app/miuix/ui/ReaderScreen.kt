package com.jm3.app.miuix.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.ui.components.BackButton
import com.jm3.app.miuix.ui.components.ErrorScreen
import com.jm3.app.miuix.ui.components.LoadingScreen
import com.jm3.app.miuix.ui.glass.GlassScaffold
import com.jm3.app.miuix.ui.glass.GlassTopAppBar
import com.jm3.app.miuix.ui.reader.READER_ENTRY_TITLE
import com.jm3.app.miuix.ui.reader.ReaderUiState
import com.jm3.app.miuix.ui.reader.rememberReaderEntryScaffoldState
import com.jm3.app.miuix.ui.route.AppRoutes
import com.jm3.core.settings.ReaderSettings
import top.yukonga.miuix.kmp.basic.SnackbarHostState

/** 阅读进度落盘前的静置时长：翻页停下来才写，避免连滚时逐页写设置存储。 */
internal const val PROGRESS_SAVE_DEBOUNCE_MS = 400L
internal const val VOLUME_THROTTLE_MS = 150L
internal const val TRACK_MUTE_DAYS_MS = 3L * 24 * 60 * 60 * 1000

/** 音量键翻页分发：Activity 收到按键后转交挂载中的阅读器，未挂载时返回 false 走系统音量。 */
object ReaderVolumeKeys {
    @Volatile
    var handler: ((Int) -> Boolean)? = null

    fun dispatch(keyCode: Int): Boolean = handler?.invoke(keyCode) ?: false
}

/** 从 Compose 的 Context 链上找宿主 Activity，窗口亮度与常亮标记都要打在它上面。 */
internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * 阅读器入口页（HyperOS 皮肤）：状态与接线全在 [rememberReaderEntryScaffoldState] 里，
 * 这里只决定空态用哪套玻璃骨架、内容态交给 ReaderPages。
 */
@Composable
internal fun ReaderScreen(
    deps: ScreenDeps,
    photoId: String,
    startPage: Int? = null,
    settings: ReaderSettings,
    onBack: () -> Unit,
    nav: AppRoutes,
    snackbarHostState: SnackbarHostState,
) {
    val entry = rememberReaderEntryScaffoldState(deps = deps, photoId = photoId)
    when (val s = entry.uiState) {
        is ReaderUiState.Loading -> GlassScaffold(
            topBar = {
                GlassTopAppBar(
                    title = READER_ENTRY_TITLE,
                    navigationIcon = { BackButton(onBack = onBack) },
                    // 透明底色：玻璃底色与模糊由 GlassScaffold 统一绘制
                    color = Color.Transparent,
                )
            },
        ) { innerPadding ->
            LoadingScreen(modifier = Modifier.padding(innerPadding))
        }
        is ReaderUiState.Failed -> GlassScaffold(
            topBar = {
                GlassTopAppBar(
                    title = READER_ENTRY_TITLE,
                    navigationIcon = { BackButton(onBack = onBack) },
                    // 透明底色：玻璃底色与模糊由 GlassScaffold 统一绘制
                    color = Color.Transparent,
                )
            },
        ) { innerPadding ->
            // 失效不给重试：重试必然再吃一次 401，只能去登录
            if (s.sessionExpired) {
                LoginExpiredScreen(onOpenLogin = nav::login, modifier = Modifier.padding(innerPadding))
            } else {
                ErrorScreen(
                    message = s.message,
                    onRetry = entry::load,
                    modifier = Modifier.padding(innerPadding),
                )
            }
        }
        is ReaderUiState.Loaded -> ReaderPages(
            deps = deps,
            photo = s.photo,
            startPage = startPage,
            chapters = s.chapters,
            chapterIndex = s.chapterIndex,
            favoritedInit = s.favorited,
            likedInit = s.liked,
            settings = settings,
            onBack = onBack,
            onOpenChapter = nav::nextChapter,
            onOpenLogin = nav::login,
            snackbarHostState = snackbarHostState,
        )
    }
}
