// SPDX-License-Identifier: Apache-2.0
package com.jm3.app.miuix.ui.coui.page

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.Dialog
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.layout.Divider
import com.itos.coui.layout.Group
import com.itos.coui.layout.Item
import com.itos.coui.scroll.couiVerticalScroll
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.shape.CouiShape
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme
import com.itos.coui.topappbar.CouiTopAppBarDefaults
import com.itos.coui.topappbar.LargeTopAppBar
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.ui.ScreenLoadPhase
import com.jm3.app.miuix.ui.ScreenLoadPlan
import com.jm3.app.miuix.ui.awaitGatewayReady
import com.jm3.app.miuix.ui.checkInDisplay
import com.jm3.app.miuix.ui.checkInFailureCopy
import com.jm3.app.miuix.ui.coui.component.CouiAppScaffold
import com.jm3.app.miuix.ui.coui.component.CouiErrorGroup
import com.jm3.app.miuix.ui.coui.component.CouiLoadingGroup
import com.jm3.app.miuix.ui.coui.component.CouiMinTouchTarget
import com.jm3.app.miuix.ui.coui.component.couiDestructiveConfirmAction
import com.jm3.app.miuix.ui.coui.component.couiPressable
import com.jm3.app.miuix.ui.coui.icon.CouiIcons
import com.jm3.app.miuix.ui.formatCheckInSummary
import com.jm3.app.miuix.ui.planLoad
import com.jm3.app.miuix.ui.resolveCheckInResult
import com.jm3.app.miuix.ui.retryCheckInLoad
import com.jm3.core.Jm3Log
import com.jm3.core.download.activeDownloadCount
import com.jm3.core.model.DailyCheckInStatus
import com.jm3.core.model.Jm3User
import com.jm3.core.runCatchingSuspending
import com.jm3.core.session.SessionOutcome
import com.jm3.core.session.UnreadBadge
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Group 内的分隔线按图标列缩进：图标（24dp）+ 图标间距（16dp）+ 行首留白（16dp）。 */
private val MineDividerStartIndent =
    CouiPageDefaults.rowPadding + CouiPageDefaults.iconSize + CouiPageDefaults.iconSpacing

@Composable
internal fun CouiMineScreen(
    deps: ScreenDeps,
    unreadBadge: UnreadBadge,
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
    val user by deps.account.user.collectAsState()
    val unread by unreadBadge.count.collectAsState()
    val downloads by deps.downloadManager.queue.updates.collectAsState()
    val activeDownloads = downloads.activeDownloadCount()
    val scope = rememberCoroutineScope()

    var checkInStatus by remember { mutableStateOf<DailyCheckInStatus?>(null) }
    var checkInLoading by remember { mutableStateOf(false) }
    var checkInError by remember { mutableStateOf<String?>(null) }
    // 会话失效会同时把 user 置空，签到卡本应整块消失；用独立标记把它留在原位解释原因并给出登录入口
    var checkInExpiredMessage by remember { mutableStateOf<String?>(null) }
    var checkInBusy by remember { mutableStateOf(false) }
    // 记录「哪一天签到成功」而不是布尔值：应用跨过午夜后不会继续显示昨天的「今日已签到」
    var checkedInOn by remember { mutableStateOf<LocalDate?>(null) }
    var confirmLogout by remember { mutableStateOf(false) }

    /** 失效与失败共用一条落点：卡片的失效态只解释原因并给出登录入口。 */
    fun applyCheckInPlan(plan: ScreenLoadPlan) {
        when (plan.phase) {
            ScreenLoadPhase.Expired -> {
                checkInExpiredMessage = plan.message
                checkInError = null
            }

            ScreenLoadPhase.Failed -> checkInError = plan.message
            ScreenLoadPhase.Content -> Unit
        }
    }

    // 加载签到状态；失败保留 error 由卡片上的重试行兜底
    fun loadCheckInStatus() {
        val uid = user?.uid ?: return
        scope.launch {
            // 启动恢复会先置登录态再注入 Cookie：AuthCall 的闸门保证注入完成前不发出请求
            checkInLoading = true
            checkInError = null
            // 冷启动时域名管理器可能还在跑库的初始化链，先等 READY 再发，避免首个请求直接撞上初始化失败；
            // 超时也照常发：真实网络错误必须显示出来，不能让卡片永远停在加载中
            awaitGatewayReady(ready = { runCatching { deps.gateway.domainsReady() }.getOrDefault(false) })
            val outcome = retryCheckInLoad(
                attempt = { deps.authCall.call { deps.gateway.dailyCheckInStatus(uid) } },
            )
            checkInLoading = false
            when (outcome) {
                is SessionOutcome.Ok -> checkInStatus = outcome.value
                SessionOutcome.Expired -> applyCheckInPlan(
                    planLoad(outcome, hasContent = false, failureMessage = "签到状态加载失败"),
                )

                is SessionOutcome.Failed -> {
                    // 库侧失败此前完全不可见（异常被吞、slf4j 无绑定），这里至少留下类型与 message
                    Jm3Log.warn(
                        "CheckIn",
                        "签到状态加载失败（${outcome.cause?.javaClass?.name ?: "无异常"}）：${outcome.message.orEmpty()}",
                        outcome.cause,
                    )
                    checkInError = checkInFailureCopy(outcome, "签到状态加载失败，请稍后重试")
                }
            }
        }
    }

    /** 签到后的回读：失效时仍只解释原因并留登录入口，不把「未生效」误报给用户。 */
    suspend fun refreshCheckInStatus(uid: String): DailyCheckInStatus? {
        val outcome = deps.authCall.call {
            runCatchingSuspending { deps.gateway.dailyCheckInStatus(uid) }.getOrNull()
        }
        return when (outcome) {
            is SessionOutcome.Ok -> outcome.value

            SessionOutcome.Expired -> {
                applyCheckInPlan(planLoad(outcome, hasContent = false, failureMessage = "签到状态加载失败"))
                null
            }

            is SessionOutcome.Failed -> null
        }
    }

    // 签到成功后刷新进度并提示；本次会话内禁止重复点击
    fun performCheckIn(status: DailyCheckInStatus) {
        val uid = user?.uid ?: return
        val today = LocalDate.now()
        if (checkInBusy || checkedInOn == today) return
        checkInBusy = true
        checkInError = null
        scope.launch {
            val outcome = deps.authCall.call { deps.gateway.dailyCheckIn(uid, status.dailyId) }
            checkInBusy = false
            when (outcome) {
                is SessionOutcome.Ok -> {
                    // 请求已成功落地，但回读可能撞上服务端/CDN 缓存延迟：两次都没标记时按乐观补签展示
                    var refreshed = refreshCheckInStatus(uid)
                    if (refreshed != null && !refreshed.isSignedOn(today.dayOfMonth)) {
                        delay(400)
                        val retry = refreshCheckInStatus(uid)
                        if (retry != null) refreshed = retry
                    }
                    val resolution = resolveCheckInResult(
                        current = checkInStatus,
                        refreshed = refreshed,
                        todayDayOfMonth = today.dayOfMonth,
                    )
                    resolution.status?.let { checkInStatus = it }
                    // 回读为 null 时没有任何证据说明今天已落账，不能锁死按钮，否则当天无法重试
                    if (resolution.markToday) checkedInOn = today
                    snackbarHostState.showSnackbar(resolution.message)
                }

                SessionOutcome.Expired -> applyCheckInPlan(
                    planLoad(outcome, hasContent = false, failureMessage = "签到失败，请重试"),
                )

                is SessionOutcome.Failed -> checkInError = checkInFailureCopy(outcome, "签到失败，请稍后重试")
            }
        }
    }
    // 切换账号时清掉上一个账号的签到状态，避免串号
    LaunchedEffect(user?.uid) {
        checkInStatus = null
        checkInError = null
        checkedInOn = null
        // 清会话也会把 user 置空，此时不能顺手抹掉失效提示，否则用户看不到「为什么突然要重新登录」
        if (user == null) return@LaunchedEffect
        checkInExpiredMessage = null
        loadCheckInStatus()
    }

    val scrollState = rememberScrollState()
    val topBarBehavior = CouiTopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        canScroll = { scrollState.maxValue > 0 },
    )
    // 顶栏折叠与正文顶部留白必须共用同一个 state：各自 remember 会让折叠位移与留白补偿脱钩
    val header = topBarBehavior.state

    CouiAppScaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(header.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(text = "我的", kind = CouiTextKind.PageTitle) },
                scrollBehavior = topBarBehavior,
            )
        },
    ) { innerPadding ->
        val density = LocalDensity.current
        val contentPadding = remember(header, innerPadding, density) {
            header.contentPadding(innerPadding, density)
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .couiVerticalScroll(scrollState)
                // 折叠补偿后的留白进滚动内容内侧；Scaffold 原始 padding 被 consume，不能再整体平移视口
                .padding(contentPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            MineAccountCard(
                user = user,
                unread = unread,
                onOpenLogin = onOpenLogin,
                onOpenProfile = onOpenProfile,
            )

            if (user != null || checkInExpiredMessage != null) {
                MineCheckInGroup(
                    status = checkInStatus,
                    loading = checkInLoading,
                    busy = checkInBusy,
                    error = checkInError,
                    expiredMessage = checkInExpiredMessage,
                    checkedInOn = checkedInOn,
                    onCheckIn = { performCheckIn(it) },
                    onReload = { loadCheckInStatus() },
                    onOpenLogin = onOpenLogin,
                )
            }

            Group(title = "内容") {
                MineNavRow(
                    icon = CouiIcons.Star,
                    title = "我的收藏",
                    value = user?.albumFavorites?.takeIf { it > 0 }?.toString(),
                    onClick = onOpenFavorites,
                )
                Divider(startIndent = MineDividerStartIndent)
                MineNavRow(icon = CouiIcons.History, title = "阅读历史", onClick = onOpenHistory)
                Divider(startIndent = MineDividerStartIndent)
                // 下载队列在途时行尾留一个圆点，不用进书架页确认进度
                MineNavRow(
                    icon = CouiIcons.Download,
                    title = "本地书架",
                    dot = activeDownloads > 0,
                    onClick = onOpenShelf,
                )
                Divider(startIndent = MineDividerStartIndent)
                MineNavRow(
                    icon = CouiIcons.Clock,
                    title = "观看历史",
                    summary = "账号同步的阅读足迹",
                    onClick = { if (user == null) onOpenLogin() else onOpenWatchHistory() },
                )
                Divider(startIndent = MineDividerStartIndent)
                MineNavRow(
                    icon = CouiIcons.Refresh,
                    title = "连载追踪",
                    onClick = { if (user == null) onOpenLogin() else onOpenTracking() },
                )
                Divider(startIndent = MineDividerStartIndent)
                MineNavRow(
                    icon = CouiIcons.Check,
                    title = "成就任务",
                    summary = "称号与勋章",
                    onClick = { if (user == null) onOpenLogin() else onOpenTasks() },
                )
            }

            Group(title = "社区") {
                // 论坛为公开内容，未登录也可浏览；发帖/回复时才在论坛页内引导登录
                MineNavRow(
                    icon = CouiIcons.Comment,
                    title = "社区论坛",
                    summary = "全部讨论 · 漫画评论 · 闲聊",
                    onClick = onOpenForum,
                )
                Divider(startIndent = MineDividerStartIndent)
                MineNavRow(
                    icon = CouiIcons.Book,
                    title = "小说",
                    summary = "连载小说阅读",
                    onClick = onOpenNovel,
                )
                Divider(startIndent = MineDividerStartIndent)
                MineNavRow(
                    icon = CouiIcons.Folder,
                    title = "禁漫书库",
                    summary = "创作者与作品",
                    onClick = onOpenLibrary,
                )
                Divider(startIndent = MineDividerStartIndent)
                MineNavRow(
                    icon = CouiIcons.Mine,
                    title = "个人资料",
                    onClick = { if (user == null) onOpenLogin() else onOpenProfile() },
                )
                Divider(startIndent = MineDividerStartIndent)
                MineNavRow(
                    icon = CouiIcons.Bell,
                    title = "通知",
                    summary = when {
                        user == null -> "登录后接收提醒"
                        unread > 0 -> "$unread 条未读通知"
                        else -> ""
                    },
                    // 角标只做展示：清理由通知页负责，返回本页时自然归零
                    badge = unread,
                    onClick = { if (user == null) onOpenLogin() else onOpenNotifications() },
                )
            }

            Group(title = "其他") {
                MineNavRow(icon = CouiIcons.Settings, title = "设置", onClick = onOpenSettings)
                if (user != null) {
                    Divider(startIndent = MineDividerStartIndent)
                    MineNavRow(
                        icon = CouiIcons.LogOut,
                        title = "退出登录",
                        titleColor = MiuixTheme.colorScheme.error,
                        onClick = { confirmLogout = true },
                    )
                }
            }
        }
    }

    // 登出属于会话级操作：与清空本地数据一致，采用二次确认
    if (confirmLogout) {
        Dialog(
            onDismissRequest = { confirmLogout = false },
            title = "退出登录",
            message = "确定要登出账号吗？",
            confirmAction = couiDestructiveConfirmAction(text = "确认退出") {
                confirmLogout = false
                scope.launch {
                    if (runCatchingSuspending { deps.account.logout() }.isSuccess) {
                        snackbarHostState.showSnackbar("已退出登录")
                    } else {
                        snackbarHostState.showSnackbar("退出登录失败，请稍后重试")
                    }
                }
            },
            dismissAction = CouiDialogAction(text = "取消", onClick = { confirmLogout = false }),
        )
    }
}

/** 顶部个人卡：头像、昵称、等级与未读提示；未登录时右侧换成登录胶囊。 */
@Composable
private fun MineAccountCard(
    user: Jm3User?,
    unread: Int,
    onOpenLogin: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    val scheme = CouiTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Group 自带组间留白，卡片只需给出与顶栏之间的呼吸位
            .padding(horizontal = CouiPageDefaults.horizontalMargin)
            .padding(top = 8.dp)
            .clip(CouiShape(24.dp, 3f))
            .background(scheme.card)
            .clickable { if (user == null) onOpenLogin() else onOpenProfile() }
            .padding(horizontal = CouiPageDefaults.rowPadding, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MineAvatar(photoUrl = user?.photoUrl, name = user?.username)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = user?.username ?: "未登录",
                kind = CouiTextKind.SectionTitle,
                textAlign = TextAlign.Start,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = user?.let { mineAccountSummary(it) } ?: "登录后同步收藏、书架与观看记录",
                kind = CouiTextKind.PreferenceSummary,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Start,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        when {
            user == null -> MineLoginCapsule(onOpenLogin = onOpenLogin)
            unread > 0 -> MineUnreadCapsule(count = unread)
        }
    }
}

/** 等级、硬币与经验同为账号基础信息，压在一行里由省略号兜住窄屏。 */
private fun mineAccountSummary(user: Jm3User): String {
    val levelName = user.levelName?.trim().orEmpty()
    val level = if (levelName.isEmpty()) "Lv.${user.level}" else "Lv.${user.level} $levelName"
    return "$level · 硬币 ${user.coin} · 经验 ${user.currentExp}/${user.nextLevelExp}"
}

/** 没有头像时用昵称首字母占位，避免空圈比未登录卡片更可疑。 */
@Composable
private fun MineAvatar(photoUrl: String?, name: String?) {
    val scheme = CouiTheme.colorScheme
    val modifier = Modifier.size(56.dp)
    if (!photoUrl.isNullOrBlank()) {
        AsyncImage(
            model = photoUrl,
            contentDescription = "头像",
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(CircleShape),
        )
    } else {
        Box(
            modifier = modifier.clip(CircleShape).background(scheme.container),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = name?.trim()?.firstOrNull()?.uppercaseChar()?.toString() ?: "客",
                kind = CouiTextKind.SectionTitle,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MineLoginCapsule(onOpenLogin: () -> Unit) {
    val scheme = CouiTheme.colorScheme
    Box(
        modifier = Modifier
            // 触摸目标扩到 48dp，视觉尺寸不变；可点区放在裁剪之前，背景不会被撑到 48dp
            .couiPressable(minTouchTarget = CouiMinTouchTarget) { onOpenLogin() }
            .clip(CouiCapsuleShape)
            .background(scheme.accent)
            .padding(horizontal = 18.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "登录", kind = CouiTextKind.PreferenceValue, color = scheme.onAccent)
    }
}

/** 库没有进度件：用旋转的刷新图标表达忙碌，尺寸与行内文字对齐。 */
@Composable
private fun MineBusySpinner() {
    val scheme = CouiTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "checkInBusy")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900, easing = LinearEasing)),
        label = "checkInBusyAngle",
    )
    Icon(
        imageVector = CouiIcons.Refresh,
        // 行标题已播报「签到中…」，图标不再重复出声
        contentDescription = null,
        modifier = Modifier
            .size(16.dp)
            .graphicsLayer(rotationZ = angle),
        tint = scheme.accent,
    )
}

/** 未读角标沿用胶囊语言；三位以上收敛成 99+，避免撑宽右侧槽位。 */
@Composable
private fun MineUnreadCapsule(count: Int) {
    val scheme = CouiTheme.colorScheme
    Box(
        modifier = Modifier
            .clip(CouiCapsuleShape)
            .background(scheme.accent)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            kind = CouiTextKind.Description,
            color = scheme.onAccent,
        )
    }
}

/** 分组行图标统一走同一尺寸与色阶，避免各行图标大小与深浅不一。 */
@Composable
private fun MineGroupIcon(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(CouiPageDefaults.iconSize),
        tint = CouiTheme.colorScheme.onSurfaceVariant,
    )
}

/** COUI 设置行：图标 + 标题 + 可选摘要 + 行尾数值/角标 + 导航箭头。 */
@Composable
private fun MineNavRow(
    icon: ImageVector,
    title: String,
    summary: String = "",
    value: String? = null,
    dot: Boolean = false,
    badge: Int = 0,
    titleColor: Color = Color.Unspecified,
    onClick: () -> Unit,
) {
    val scheme = CouiTheme.colorScheme
    Item(
        icon = { MineGroupIcon(icon) },
        title = { Text(text = title, kind = CouiTextKind.PreferenceTitle, color = titleColor) },
        // 空摘要仍占位会多出一条 13sp 行高，这里按有无文案条件渲染
        summary = {
            if (summary.isNotEmpty()) {
                Text(text = summary, kind = CouiTextKind.PreferenceSummary, color = scheme.onSurfaceVariant)
            }
        },
        trailing = {
            when {
                badge > 0 -> MineUnreadCapsule(count = badge)
                dot -> Box(Modifier.size(8.dp).clip(CircleShape).background(scheme.accent))
                value != null -> Text(
                    text = value,
                    kind = CouiTextKind.PreferenceValue,
                    color = scheme.onSurfaceVariant,
                )
            }
            // Item 不自带箭头：可点击行补一个，维持「点进去」的暗示
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = CouiIcons.Forward,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = scheme.onSurfaceVariant,
            )
        },
        onClick = onClick,
    )
}

/** 签到组：一行主操作加月度进度；失效态只解释原因并给登录入口。 */
@Composable
private fun MineCheckInGroup(
    status: DailyCheckInStatus?,
    loading: Boolean,
    busy: Boolean,
    error: String?,
    expiredMessage: String?,
    checkedInOn: LocalDate?,
    onCheckIn: (DailyCheckInStatus) -> Unit,
    onReload: () -> Unit,
    onOpenLogin: () -> Unit,
) {
    val scheme = CouiTheme.colorScheme
    // COUI 调色板没有语义错误色，这里沿用全项目既有的 MIUIX 主题 token（只取颜色，不取组件）
    val errorColor = MiuixTheme.colorScheme.error
    val today = LocalDate.now()
    when {
        // 会话失效时重试必然再吃一次 401，唯一动作只能是去登录
        expiredMessage != null -> Group(title = "每日签到") {
            Item(
                icon = { MineGroupIcon(CouiIcons.Lock) },
                title = { Text(text = "需要重新登录", kind = CouiTextKind.PreferenceTitle) },
                summary = {
                    Text(text = expiredMessage, kind = CouiTextKind.PreferenceSummary, color = errorColor)
                },
                trailing = {
                    Text(text = "去登录", kind = CouiTextKind.PreferenceValue, color = scheme.accent)
                },
                onClick = onOpenLogin,
            )
        }

        status != null -> Group(title = "每日签到") {
            // 今日已签、漏签天数与进度统一交给纯函数推导，与 Miuix 我的页共用同一口径
            val display = checkInDisplay(status, checkedInOn, today)
            val signedToday = display.signedToday
            val checkInAction: () -> Unit = { onCheckIn(status) }
            val rowClick: (() -> Unit)? = if (busy || signedToday) null else checkInAction
            Item(
                icon = { MineGroupIcon(CouiIcons.Check) },
                title = {
                    Text(
                        text = when {
                            busy -> "签到中…"
                            signedToday -> "今日已签到"
                            else -> "立即签到"
                        },
                        kind = CouiTextKind.PreferenceTitle,
                    )
                },
                summary = {
                    Text(
                        text = formatCheckInSummary(status.eventName, signedToday),
                        kind = CouiTextKind.PreferenceSummary,
                        color = scheme.onSurfaceVariant,
                    )
                },
                // 已签到只去掉点击、不置灰：禁用态会让人以为这一行坏了
                trailing = {
                    if (busy) {
                        MineBusySpinner()
                    } else if (signedToday) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = CouiIcons.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = scheme.accent,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(text = "已签到", kind = CouiTextKind.PreferenceValue, color = scheme.accent)
                        }
                    }
                },
                onClick = rowClick,
            )
            // 进度按 signedDays/totalDays 换算，不用接口返回的 currentProgress 文案百分比，两者会互相矛盾；
            // totalDays 为 0 时不画 0/0，避免读成「签到坏了」
            if (status.totalDays > 0) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CouiPageDefaults.rowPadding)
                        .padding(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(text = "本月签到", kind = CouiTextKind.PreferenceSummary, color = scheme.onSurfaceVariant)
                        Text(
                            text = display.progressLabel,
                            kind = CouiTextKind.PreferenceSummary,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    // 进度条按胶囊轮廓绘制，与 COUI 的圆角语言保持一致
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(CouiCapsuleShape)
                            .background(scheme.outlineVariant)
                            .semantics {
                                progressBarRangeInfo = ProgressBarRangeInfo(
                                    current = display.progress.coerceIn(0f, 1f),
                                    range = 0f..1f,
                                )
                            },
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(display.progress.coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .clip(CouiCapsuleShape)
                                .background(scheme.accent),
                        )
                    }
                    if (display.missedDays > 0) {
                        Text(
                            text = "本月已有 ${display.missedDays} 天未签到",
                            kind = CouiTextKind.Description,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (error != null) {
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = CouiPageDefaults.rowPadding, end = CouiPageDefaults.rowPadding, bottom = 12.dp),
                    text = error,
                    kind = CouiTextKind.PreferenceSummary,
                    color = errorColor,
                    textAlign = TextAlign.Start,
                )
            }
        }

        // 首屏加载、失败与空态统一用公共状态卡片，避免各页自造一套壳子
        loading -> CouiLoadingGroup(text = "正在加载签到状态…")
        error != null -> CouiErrorGroup(message = error, onRetry = onReload)
        // 回读成功却没有状态：按取数失败处理，保留重试入口
        else -> CouiErrorGroup(message = "没有取到签到信息，可能是网络波动", onRetry = onReload)
    }
}
