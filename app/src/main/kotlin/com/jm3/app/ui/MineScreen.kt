package com.jm3.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jm3.app.data.AccountManager
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun MineScreen(
    account: AccountManager,
    onOpenLogin: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenShelf: () -> Unit,
) {
    val user by account.user.collectAsState()
    val scope = rememberCoroutineScope()

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding),
        ) {
            SmallTitle(text = "账号")
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                val u = user
                if (u == null) {
                    BasicComponent(title = "未登录", summary = "点击登录账号", onClick = onOpenLogin)
                } else {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(text = u.username, fontSize = 18.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Lv.${u.level} ${u.levelName ?: ""} · 经验 ${u.currentExp}/${u.nextLevelExp} · 硬币 ${u.coin}",
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(text = "退出登录", onClick = { scope.launch { account.logout() } })
                    }
                }
            }
            SmallTitle(text = "功能")
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                BasicComponent(title = "我的收藏", summary = null, onClick = onOpenFavorites)
                BasicComponent(title = "阅读历史", summary = null, onClick = onOpenHistory)
                BasicComponent(title = "本地书架", summary = null, onClick = onOpenShelf)
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
