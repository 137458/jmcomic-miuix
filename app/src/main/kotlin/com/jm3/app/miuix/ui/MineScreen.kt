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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.ui.components.DialogActionRow
import com.jm3.app.miuix.ui.components.SectionHorizontalPadding
import com.jm3.app.miuix.ui.components.overlayDialogMaxWidth
import com.jm3.app.miuix.ui.glass.GlassScaffold
import com.jm3.app.miuix.ui.glass.GlassTopAppBar
import com.jm3.app.miuix.ui.glass.TopBarContentSpacer
import com.jm3.app.miuix.ui.mine.MineEntry
import com.jm3.app.miuix.ui.mine.rememberMineScaffoldState
import com.jm3.app.miuix.ui.route.AppRoutes
import com.jm3.app.miuix.ui.skin.LocalSkin
import com.jm3.core.session.UnreadBadge
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
internal fun MineScreen(
    deps: ScreenDeps,
    unreadBadge: UnreadBadge,
    snackbarHostState: SnackbarHostState,
    nav: AppRoutes,
) {
    val skin = LocalSkin.current
    // 账号取值、未读与在途计数、签到行推导、入口守卫与登出编排都来自共享骨架；本文件只剩分组与视觉
    val state = rememberMineScaffoldState(
        deps = deps,
        unreadBadge = unreadBadge,
        snackbarHostState = snackbarHostState,
        onOpenLogin = nav::login,
    )
    val user = state.user
    val unread = state.unread
    val activeDownloads = state.inFlightDownloads
    val checkInState = state.checkInState

    GlassScaffold(
        topBar = { GlassTopAppBar(title = "我的") },
    ) { innerPadding ->
        // 主框架切换 tab 会重建页面，滚动容器恢复到原位置后顶栏要跟上，否则大标题压在已滚内容上
        Column(
            modifier = Modifier
                .fillMaxSize()
                .readableWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            // 顶栏是浮层：内容从顶栏下沿起排，滚动时可穿过顶栏继续被玻璃采样
            TopBarContentSpacer(paddingValues = innerPadding)
            SmallTitle(text = "账号")
            Card(cornerRadius = skin.shapes.cardCorner, modifier = Modifier.fillMaxWidth().padding(horizontal = SectionHorizontalPadding)) {
                val u = user
                if (u == null) {
                    BasicComponent(title = "未登录", summary = "点击登录账号", onClick = nav::login)
                } else {
                    BasicComponent(
                        title = u.username,
                        summary = "Lv.${u.level} ${u.levelName ?: ""} · 经验 ${u.currentExp}/${u.nextLevelExp} · 硬币 ${u.coin}",
                    )
                    ArrowPreference(
                        title = "个人资料",
                        onClick = { state.enter(MineEntry.Profile) { nav.profile() } },
                    )
                    ArrowPreference(title = "退出登录", onClick = state::askLogout)
                }
            }
            if (state.showsCheckIn) {
                SmallTitle(text = "每日签到")
                Card(cornerRadius = skin.shapes.cardCorner, modifier = Modifier.fillMaxWidth().padding(horizontal = SectionHorizontalPadding)) {
                    val row = state.checkInRow
                    when {
                        // 会话失效：文案与其它页面一致，动作只能是去登录
                        checkInState.expiredMessage != null -> Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = checkInState.expiredMessage.orEmpty(),
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.error,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                            TextButton(
                                text = "去登录",
                                onClick = nav::login,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                        row != null -> {
                            // 行标题、副标题、可点状态与进度都由骨架按同一口径推导，COUI 页读同一份结果
                            val display = row.display
                            val signedToday = row.signedToday
                            BasicComponent(
                                title = row.title,
                                summary = row.summary,
                                endActions = {
                                    when {
                                        row.busy -> CircularProgressIndicator(size = 20.dp)
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
                                onClick = if (row.enabled) state::checkIn else null,
                            )
                            // 进度只有一处来源：record 自算的已签天数，不叠加接口返回的活动百分比（两者会互相矛盾）；
                            // 没有日历数据（totalDays 为 0）时不展示 0/0 的进度条，避免读成"签到坏了"
                            if (row.showsProgress) {
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
                                            text = display.progressLabel,
                                            style = MiuixTheme.textStyles.footnote1,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        )
                                    }
                                    LinearProgressIndicator(
                                        progress = display.progress,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    if (display.missedDays > 0) {
                                        Text(
                                            text = "本月已有 ${display.missedDays} 天未签到",
                                            style = MiuixTheme.textStyles.footnote2,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        )
                                    }
                                }
                            }
                            checkInState.error?.let { message ->
                                Text(
                                    text = message,
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = MiuixTheme.colorScheme.error,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                )
                            }
                        }
                        checkInState.loading -> BasicComponent(
                            title = "签到",
                            summary = "正在加载签到状态…",
                            endActions = { CircularProgressIndicator(size = 20.dp) },
                        )
                        else -> BasicComponent(
                            title = "签到",
                            summary = checkInState.error ?: "暂无签到信息，点击重试",
                            onClick = state::reloadCheckIn,
                        )
                    }
                }
            }
            SmallTitle(text = "我的内容")
            Card(cornerRadius = skin.shapes.cardCorner, modifier = Modifier.fillMaxWidth().padding(horizontal = SectionHorizontalPadding)) {
                ArrowPreference(title = "我的收藏", onClick = { state.enter(MineEntry.Favorites) { nav.favorites() } })
                ArrowPreference(title = "阅读历史", onClick = { state.enter(MineEntry.History) { nav.history() } })
                ArrowPreference(title = "小说", summary = "连载小说阅读", onClick = { state.enter(MineEntry.Novel) { nav.novelList() } })
                ArrowPreference(title = "禁漫书库", summary = "创作者与作品", onClick = { state.enter(MineEntry.Library) { nav.library() } })
                // 下载队列在途时行尾显示圆点，无需进书架页确认
                ArrowPreference(
                    title = "本地书架",
                    onClick = { state.enter(MineEntry.LocalShelf) { nav.shelf() } },
                    endActions = {
                        if (activeDownloads > 0) Badge()
                    },
                )
                ArrowPreference(
                    title = "信箱",
                    summary = if (unread > 0) "$unread 条未读通知" else null,
                    onClick = { state.enter(MineEntry.Notifications) { nav.notifications() } },
                    endActions = {
                        if (unread > 0) Badge { Text(text = if (unread > 99) "99+" else unread.toString()) }
                    },
                )
                ArrowPreference(
                    title = "连载追踪",
                    onClick = { state.enter(MineEntry.Tracking) { nav.tracking() } },
                )
                // 论坛为公开内容，未登录也可浏览；发帖/回复时才引导登录
                ArrowPreference(
                    title = "社区论坛",
                    summary = "全部讨论 · 漫画评论 · 闲聊",
                    onClick = { state.enter(MineEntry.Forum) { nav.forum() } },
                )
            }
            SmallTitle(text = "成就与服务")
            Card(cornerRadius = skin.shapes.cardCorner, modifier = Modifier.fillMaxWidth().padding(horizontal = SectionHorizontalPadding)) {
                ArrowPreference(
                    title = "成就任务",
                    summary = "称号与勋章",
                    onClick = { state.enter(MineEntry.Tasks) { nav.tasks() } },
                )
                ArrowPreference(
                    title = "云端观看记录",
                    summary = "账号同步的阅读足迹",
                    onClick = { state.enter(MineEntry.WatchHistory) { nav.watchHistory() } },
                )
                ArrowPreference(title = "设置", onClick = { state.enter(MineEntry.Settings) { nav.settings() } })
            }
            // 底部净空由 GlassScaffold 统一给出（导航条 inset + 呼吸空间）
            Spacer(modifier = Modifier.height(innerPadding.calculateBottomPadding()))
        }

        // 登出属于会话级操作：与清空数据一致，采用二次确认弹窗
        OverlayDialog(
            show = state.logoutDialogVisible,
            maxWidth = overlayDialogMaxWidth(),
            title = "退出登录",
            cornerRadius = skin.shapes.dialogCorner,
            summary = "确定要登出账号吗？",
            onDismissRequest = state::dismissLogout,
        ) {
            DialogActionRow(
                primaryText = "确认退出",
                destructive = true,
                onSecondary = state::dismissLogout,
                // 登出后账号维度的列表缓存、侧栏与喜欢状态都属于上一个账号，骨架里整份作废后再提示
                onPrimary = state::submitLogout,
            )
        }
    }
}

/**
 * 签到卡片副标题文案：活动名非空时以间隔号与后置文案自然分隔，避免直接拼接时中文粘连；活动名为空时仅保留状态说明。
 */
internal fun formatCheckInSummary(eventName: String?, signedToday: Boolean): String {
    val label = eventName?.trim().orEmpty()
    val actionText = if (signedToday) "今天的奖励已入账" else "签到可得金币与经验"
    return if (label.isEmpty()) actionText else "$label · $actionText"
}
