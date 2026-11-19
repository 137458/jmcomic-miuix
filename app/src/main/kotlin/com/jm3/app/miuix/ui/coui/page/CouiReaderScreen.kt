package com.jm3.app.miuix.ui.coui.page

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.itos.coui.theme.CouiTheme as CouiKitTheme
import com.itos.coui.topappbar.LargeTopAppBar
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.ui.coui.component.CouiAppScaffold
import com.jm3.app.miuix.ui.coui.component.CouiErrorGroup
import com.jm3.app.miuix.ui.coui.component.CouiFeedLoginGroup
import com.jm3.app.miuix.ui.coui.component.CouiLoadingGroup
import com.jm3.app.miuix.ui.coui.component.CouiTopBarBackButton
import com.jm3.app.miuix.ui.coui.component.CouiTopBarTitle
import com.jm3.app.miuix.ui.coui.component.couiTopAppBarColors
import com.jm3.app.miuix.ui.coui.theme.CouiTheme
import com.jm3.app.miuix.ui.readableWidth
import com.jm3.app.miuix.ui.reader.READER_ENTRY_TITLE
import com.jm3.app.miuix.ui.reader.ReaderUiState
import com.jm3.app.miuix.ui.reader.rememberReaderEntryScaffoldState
import com.jm3.app.miuix.ui.route.AppRoutes
import com.jm3.core.settings.ReaderSettings
import top.yukonga.miuix.kmp.basic.SnackbarHostState

/**
 * 阅读器入口页（ColorOS 皮肤）：状态与接线全在 [rememberReaderEntryScaffoldState] 里，
 * 这里只决定空态用哪套 COUI 骨架、内容态交给 ReaderPages。
 */
@Composable
internal fun CouiReaderScreen(
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
        is ReaderUiState.Loading -> ReaderStateScreen(title = READER_ENTRY_TITLE, onBack = onBack) {
            CouiLoadingGroup(
                modifier = Modifier.padding(top = 8.dp),
                text = "正在加载章节…",
            )
        }
        is ReaderUiState.Failed -> ReaderStateScreen(title = READER_ENTRY_TITLE, onBack = onBack) {
            // 失效不给重试：重试必然再吃一次 401，只能去登录
            if (s.sessionExpired) {
                CouiFeedLoginGroup(message = s.message, onOpenLogin = nav::login)
            } else {
                CouiErrorGroup(
                    message = s.message,
                    onRetry = entry::load,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        is ReaderUiState.Loaded -> CouiTheme {
            ReaderPages(
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
}

/** 首载态外壳：加载、失败、登录失效共用一顶栏，只换正文。 */
@Composable
private fun ReaderStateScreen(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    CouiTheme {
        CouiAppScaffold(
            modifier = Modifier
                .fillMaxSize()
                .background(CouiKitTheme.colorScheme.background),
            topBar = {
                LargeTopAppBar(
                    title = { CouiTopBarTitle(text = title, textAlign = TextAlign.Start) },
                    navigationIcon = { CouiTopBarBackButton(onClick = onBack) },
                    colors = couiTopAppBarColors(),
                )
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .readableWidth()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding),
            ) {
                content()
            }
        }
    }
}
