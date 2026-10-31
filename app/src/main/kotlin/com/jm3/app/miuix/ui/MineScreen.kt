package com.jm3.app.miuix.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.jm3.app.miuix.ui.glass.GlassScaffold
import com.jm3.app.miuix.ui.glass.GlassTopAppBar
import com.jm3.app.miuix.ui.glass.TopBarContentSpacer
import com.jm3.core.download.DownloadStatus
import com.jm3.core.model.DailyCheckInStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Badge
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
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
    onOpenNotifications: () -> Unit,
    onOpenTracking: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenWatchHistory: () -> Unit,
    onOpenForum: () -> Unit,
    onOpenNovel: () -> Unit,
    onOpenLibrary: () -> Unit,
) {
    val user by account.user.collectAsState()
    val unread by Jm3Graph.unreadNotifications.collectAsState()
    val downloads by Jm3Graph.downloadManager.queue.updates.collectAsState()
    val activeDownloads = downloads.values.count {
        it.status == DownloadStatus.RUNNING || it.status == DownloadStatus.QUEUED
    }
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

    GlassScaffold(
        topBar = { GlassTopAppBar(title = "我的") },
    ) { innerPadding ->
        // 主框架切换 tab 会重建页面，滚动容器恢复到原位置后顶栏要跟上，否则大标题压在已滚内容上
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            // 顶栏是浮层：内容从顶栏下沿起排，滚动时可穿过顶栏继续被玻璃采样
            TopBarContentSpacer(paddingValues = innerPadding)
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
                    ArrowPreference(title = "个人资料", onClick = onOpenProfile)
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
                            // 「今日已签到」由 record 里当天的标记推导，切页重建后依旧成立；checkedIn 兜住接口延迟
                            val signedToday = checkedIn || status.isSignedOn(LocalDate.now().dayOfMonth)
                            val eventLabel = status.eventName.ifBlank { "每日签到" }
                            val rowOnClick: (() -> Unit)? = if (checkInBusy || signedToday) {
                                null
                            } else {
                                ({ performCheckIn(status) })
                            }
                            BasicComponent(
                                title = when {
                                    checkInBusy -> "签到中…"
                                    signedToday -> "今日已签到"
                                    else -> "立即签到"
                                },
                                summary = if (signedToday) {
                                    "「${eventLabel}」今天的奖励已入账"
                                } else {
                                    "「${eventLabel}」签到可得金币与经验"
                                },
                                endActions = {
                                    when {
                                        checkInBusy -> CircularProgressIndicator(size = 20.dp)
                                        // 已签到只去掉点击、不置灰：禁用态会让人以为这一行坏了
                                        signedToday -> Text(
                                            text = "✓ 已完成",
                                            style = MiuixTheme.textStyles.footnote1,
                                            color = MiuixTheme.colorScheme.primary,
                                        )
                                    }
                                },
                                onClick = rowOnClick,
                            )
                            // 进度只有一处来源：record 自算的已签天数，不叠加接口返回的活动百分比（两者会互相矛盾）
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = "本月签到",
                                        style = MiuixTheme.textStyles.footnote1,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                    Text(
                                        text = "${status.signedDays}/${status.totalDays} 天 · ${(status.progress * 100).roundToInt()}%",
                                        style = MiuixTheme.textStyles.footnote1,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = status.progress,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            checkInError?.let { message ->
                                Text(
                                    text = message,
                                    style = MiuixTheme.textStyles.footnote1,
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
            SmallTitle(text = "我的内容")
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                ArrowPreference(title = "我的收藏", onClick = onOpenFavorites)
                ArrowPreference(title = "阅读历史", onClick = onOpenHistory)
                ArrowPreference(
                    title = "小说",
                    summary = "连载小说阅读",
                    onClick = onOpenNovel,
                )
                ArrowPreference(
                    title = "禁漫书库",
                    summary = "创作者与作品",
                    onClick = onOpenLibrary,
                )
                // 下载队列在途时行尾显示圆点，无需进书架页确认
                ArrowPreference(
                    title = "本地书架",
                    onClick = onOpenShelf,
                    endActions = {
                        if (activeDownloads > 0) Badge()
                    },
                )
                ArrowPreference(
                    title = "信箱",
                    summary = if (unread > 0) "$unread 条未读通知" else null,
                    onClick = { if (user == null) onOpenLogin() else onOpenNotifications() },
                    endActions = {
                        if (unread > 0) Badge { Text(text = if (unread > 99) "99+" else unread.toString()) }
                    },
                )
                ArrowPreference(
                    title = "连载追踪",
                    onClick = { if (user == null) onOpenLogin() else onOpenTracking() },
                )
                // 论坛为公开内容，未登录也可浏览；发帖/回复时才引导登录
                ArrowPreference(
                    title = "社区论坛",
                    summary = "全部讨论 · 漫画评论 · 闲聊",
                    onClick = onOpenForum,
                )
            }
            SmallTitle(text = "成就与服务")
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                ArrowPreference(
                    title = "成就任务",
                    summary = "称号与勋章",
                    onClick = { if (user == null) onOpenLogin() else onOpenTasks() },
                )
                ArrowPreference(
                    title = "云端观看记录",
                    summary = "账号同步的阅读足迹",
                    onClick = { if (user == null) onOpenLogin() else onOpenWatchHistory() },
                )
                ArrowPreference(title = "设置", onClick = onOpenSettings)
            }
            // 底部净空由 GlassScaffold 统一给出（导航条 inset + 呼吸空间）
            Spacer(modifier = Modifier.height(innerPadding.calculateBottomPadding()))
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
