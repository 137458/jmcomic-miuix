package com.jm3.app.miuix.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jm3.app.miuix.Jm3Graph
import com.jm3.app.miuix.data.AccountManager
import com.jm3.app.miuix.ui.glass.GlassScaffold
import com.jm3.app.miuix.ui.glass.GlassTopAppBar
import com.jm3.app.miuix.ui.glass.TopBarContentSpacer
import com.jm3.core.LOGIN_EXPIRED_MESSAGE
import com.jm3.core.download.DownloadStatus
import com.jm3.core.isSessionExpired
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
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
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
    // 会话失效时用户会被置空，卡片本来整块消失；用独立标记把它留在原位解释原因并给出登录入口
    var checkInSessionExpired by remember { mutableStateOf(false) }
    var checkInBusy by remember { mutableStateOf(false) }
    // 记录"哪一天签到成功"而不是一个布尔值：应用跨过午夜后不会继续显示昨天的「今日已签到」。
    var checkedInOn by remember { mutableStateOf<LocalDate?>(null) }
    var confirmLogout by remember { mutableStateOf(false) }

    /** 会话失效：清掉本地会话，不请求网关登出（会话已作废，只会再吃一次 401）。 */
    fun expireCheckInSession() {
        checkInSessionExpired = true
        checkInError = null
        scope.launch { Jm3Graph.account.markSessionExpired() }
    }

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
                .onFailure {
                    if (isSessionExpired(it.message)) expireCheckInSession() else checkInError = it.message ?: "签到状态加载失败"
                }
        }
    }

    // 签到成功后刷新进度并提示；本次会话内禁止重复点击。
    fun performCheckIn(status: DailyCheckInStatus) {
        val uid = user?.uid ?: return
        val today = LocalDate.now()
        if (checkInBusy || checkedInOn == today) return
        checkInBusy = true
        checkInError = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { Jm3Graph.gateway.dailyCheckIn(uid, status.dailyId) }
            }
            checkInBusy = false
            result
                .onSuccess {
                    // 接口只要返回的 msg 不含「已签到过」就算成功，服务端没落账时同样会走到这里。
                    // 因此以刷新后的日历为准确认这一天真的记上了，避免把没生效的签到显示成「今日已签到」。
                    val refreshed = withContext(Dispatchers.IO) {
                        runCatching { Jm3Graph.gateway.dailyCheckInStatus(uid) }.getOrNull()
                    }
                    refreshed?.let { checkInStatus = it }
                    val snackbarText = when (refreshed?.isSignedOn(today.dayOfMonth)) {
                        null -> "签到已提交，暂时无法确认状态"
                        true -> {
                            checkedInOn = today
                            "签到成功"
                        }
                        false -> "签到未生效，请稍后重试"
                    }
                    snackbarHostState.showSnackbar(snackbarText)
                }
                .onFailure {
                    if (isSessionExpired(it.message)) expireCheckInSession() else checkInError = it.message ?: "签到失败，请重试"
                }
        }
    }

    // 切换账号时清掉上一个账号的签到状态，避免串号。
    LaunchedEffect(user?.uid) {
        checkInStatus = null
        checkInError = null
        checkedInOn = null
        // 清会话也会把 user 置空，此时不能顺手抹掉失效提示，否则用户看不到「为什么突然要重新登录」
        if (user == null) return@LaunchedEffect
        checkInSessionExpired = false
        loadCheckInStatus()
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
            if (user != null || checkInSessionExpired) {
                SmallTitle(text = "每日签到")
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    val status = checkInStatus
                    when {
                        // 会话失效：文案与其它页面一致，动作只能是去登录
                        checkInSessionExpired -> Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = LOGIN_EXPIRED_MESSAGE,
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.error,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                            TextButton(
                                text = "去登录",
                                onClick = onOpenLogin,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                        status != null -> {
                            val today = LocalDate.now()
                            // 「今日已签到」由记录里当天的标记推导，切页重建后依旧成立；
                            // checkedInOn 只兜住接口回读延迟，且按日期比较，跨过午夜会自动失效。
                            val signedToday = checkedInOn == today || status.isSignedOn(today.dayOfMonth)
                            // 活动名可能自带「」，外层再包一层就会出现嵌套引号，这里原样拼接、不额外加引号
                            val eventLabel = status.eventName.trim()
                            // 今天之前仍未签到的日期就是漏签；只做提示，不参与进度计算
                            val missedDays = status.days.count {
                                it.dayOfMonth < today.dayOfMonth && !it.signed
                            }
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
                                summary = when {
                                    eventLabel.isEmpty() && signedToday -> "今天的奖励已入账"
                                    eventLabel.isEmpty() -> "签到可得金币与经验"
                                    signedToday -> "${eventLabel}今天的奖励已入账"
                                    else -> "${eventLabel}签到可得金币与经验"
                                },
                                endActions = {
                                    when {
                                        checkInBusy -> CircularProgressIndicator(size = 20.dp)
                                        // 已签到只去掉点击、不置灰：禁用态会让人以为这一行坏了
                                        signedToday -> Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(
                                                imageVector = MiuixIcons.Basic.Check,
                                                contentDescription = null,
                                                tint = MiuixTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp),
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "已签到",
                                                style = MiuixTheme.textStyles.footnote1,
                                                color = MiuixTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                },
                                onClick = rowOnClick,
                            )
                            // 进度只有一处来源：record 自算的已签天数，不叠加接口返回的活动百分比（两者会互相矛盾）；
                            // 没有日历数据（totalDays 为 0）时不展示 0/0 的进度条，避免读成"签到坏了"
                            if (status.totalDays > 0) {
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
                                            text = "${status.signedDays}/${status.totalDays} 天 · " +
                                                "${(status.progress * 100).roundToInt()}%",
                                            style = MiuixTheme.textStyles.footnote1,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        )
                                    }
                                    LinearProgressIndicator(
                                        progress = status.progress,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    if (missedDays > 0) {
                                        Text(
                                            text = "本月已有 $missedDays 天未签到",
                                            style = MiuixTheme.textStyles.footnote2,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        )
                                    }
                                }
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
                        checkInLoading -> BasicComponent(
                            title = "签到",
                            summary = "正在加载签到状态…",
                            endActions = { CircularProgressIndicator(size = 20.dp) },
                        )
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

        // 登出属于会话级操作：与清空数据一致，采用二次确认弹窗
        OverlayDialog(
            show = confirmLogout,
            title = "退出登录",
            summary = "确定要登出账号吗？",
            onDismissRequest = { confirmLogout = false },
        ) {
            DialogActionRow(
                primaryText = "确认退出",
                destructive = true,
                onSecondary = { confirmLogout = false },
                onPrimary = {
                    confirmLogout = false
                    scope.launch {
                        account.logout()
                        snackbarHostState.showSnackbar("已退出登录")
                    }
                },
            )
        }
    }
}
