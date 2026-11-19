package com.jm3.app.miuix.ui.components

/** 状态视图：加载中、错误、空态与失败重试外壳。 */

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jm3.app.miuix.ui.LoginExpiredScreen
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 全屏加载态：进度指示 + 文案；调用方需传入 innerPadding 以免被标题栏覆盖。 */
@Composable
fun LoadingScreen(modifier: Modifier = Modifier, text: String = "加载中…") {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        InfiniteProgressIndicator()
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = text)
    }
}

/** 全屏错误态：错误信息 + 重试入口；调用方需传入 innerPadding。 */
@Composable
fun ErrorScreen(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(12.dp))
        TextButton(text = "重试", onClick = onRetry)
    }
}

/** 列表空态：标题 + 可选引导文案。 */
@Composable
fun EmptyState(title: String, modifier: Modifier = Modifier, hint: String? = null) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = title)
        if (hint != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = hint,
                style = MiuixTheme.textStyles.footnote1,
                textAlign = TextAlign.Center,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

/** 全屏空态：在可用区域内垂直居中，供无列表的空页面使用。 */
@Composable
fun CenteredEmptyState(title: String, modifier: Modifier = Modifier, hint: String? = null) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EmptyState(title = title, hint = hint)
    }
}

/** 点击分页结果列表：单页整页展示，底部附翻页条；与无限滚动共用同一行样式。 *//** 屏级/列表区失败块：会话失效切换为去登录，普通失败给重试。 */
@Composable
internal fun ScreenFailureOrLogin(
    message: String,
    expired: Boolean,
    onRetry: () -> Unit,
    onOpenLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (expired) {
        LoginExpiredScreen(onOpenLogin = onOpenLogin, modifier = modifier)
    } else {
        ErrorScreen(message = message, onRetry = onRetry, modifier = modifier)
    }
}
