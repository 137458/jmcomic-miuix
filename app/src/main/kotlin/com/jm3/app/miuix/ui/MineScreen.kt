package com.jm3.app.miuix.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jm3.app.miuix.Jm3Graph
import com.jm3.app.miuix.data.AccountManager
import com.jm3.core.model.DailyCheckInStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun MineScreen(
    account: AccountManager,
    snackbarHostState: SnackbarHostState,
    onOpenLogin: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenShelf: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val user by account.user.collectAsState()
    val scope = rememberCoroutineScope()

    var checkInStatus by remember { mutableStateOf<DailyCheckInStatus?>(null) }
    var checkInLoading by remember { mutableStateOf(false) }
    var checkInError by remember { mutableStateOf<String?>(null) }
    var checkInBusy by remember { mutableStateOf(false) }
    var checkedIn by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }

    // 加载签到状态；失败保留 error 由卡片上的重试行兜底。
    fun loadCheckInStatus() {
        val uid = user?.uid ?: return
        scope.launch {
            checkInLoading = true
            checkInError = null
            val result = withContext(Dispatchers.IO) {
                runCatching { Jm3Graph.gateway.dailyCheckInStatus(uid) }
            }
            checkInLoading = false
            result
                .onSuccess { checkInStatus = it }
                .onFailure { checkInError = it.message ?: "签到状态加载失败" }
        }
    }

    // 签到成功后刷新进度并提示；本次会话内禁止重复点击。
    fun performCheckIn(status: DailyCheckInStatus) {
        val uid = user?.uid ?: return
        if (checkInBusy || checkedIn) return
        checkInBusy = true
        checkInError = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { Jm3Graph.gateway.dailyCheckIn(uid, status.dailyId) }
            }
            checkInBusy = false
            result
                .onSuccess {
                    checkedIn = true
                    snackbarHostState.showSnackbar("签到成功")
                    loadCheckInStatus()
                }
                .onFailure { checkInError = it.message ?: "签到失败，请重试" }
        }
    }

    // 切换账号时清掉上一个账号的签到状态，避免串号。
    LaunchedEffect(user?.uid) {
        checkInStatus = null
        checkInError = null
        checkedIn = false
        if (user != null) loadCheckInStatus()
    }

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
                    BasicComponent(
                        title = u.username,
                        summary = "Lv.${u.level} ${u.levelName ?: ""} · 经验 ${u.currentExp}/${u.nextLevelExp} · 硬币 ${u.coin}",
                    )
                    ArrowPreference(
                        title = "退出登录",
                        onClick = { confirmLogout = true },
                    )
                }
            }
            if (user != null) {
                SmallTitle(text = "每日签到")
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    val status = checkInStatus
                    when {
                        status != null -> {
                            // 签到作为整行可点操作，进度与活动信息放进 summary，避免卡片内再嵌全宽按钮。
                            BasicComponent(
                                title = when {
                                    checkInBusy -> "签到中…"
                                    checkedIn -> "今日已签到"
                                    else -> "立即签到"
                                },
                                summary = "${status.eventName} · 已签到 ${status.signedDays}/${status.totalDays} 天 · 奖励进度 ${status.currentProgress}",
                                enabled = !checkInBusy && !checkedIn,
                                endActions = {
                                    if (checkInBusy) {
                                        CircularProgressIndicator(size = 20.dp)
                                    }
                                },
                                onClick = { performCheckIn(status) },
                            )
                            checkInError?.let { message ->
                                Text(
                                    text = message,
                                    fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                                    color = MiuixTheme.colorScheme.error,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                )
                            }
                        }
                        checkInLoading -> BasicComponent(title = "签到", summary = "加载中…")
                        else -> BasicComponent(
                            title = "签到",
                            summary = checkInError ?: "暂无签到信息，点击重试",
                            onClick = ::loadCheckInStatus,
                        )
                    }
                }
            }
            SmallTitle(text = "功能")
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                ArrowPreference(title = "我的收藏", onClick = onOpenFavorites)
                ArrowPreference(title = "阅读历史", onClick = onOpenHistory)
                ArrowPreference(title = "本地书架", onClick = onOpenShelf)
                ArrowPreference(title = "设置", onClick = onOpenSettings)
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // 登出属于会话级操作：与清空数据一致，采用纵向全宽按钮二次确认
        OverlayDialog(
            show = confirmLogout,
            title = "退出登录",
            summary = "确定要登出账号吗？",
            onDismissRequest = { confirmLogout = false },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    text = "取消",
                    onClick = { confirmLogout = false },
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    text = "确认退出",
                    colors = ButtonDefaults.textButtonColors(
                        color = Color.Transparent,
                        textColor = MiuixTheme.colorScheme.error,
                    ),
                    onClick = {
                        confirmLogout = false
                        scope.launch {
                            account.logout()
                            snackbarHostState.showSnackbar("已退出登录")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
