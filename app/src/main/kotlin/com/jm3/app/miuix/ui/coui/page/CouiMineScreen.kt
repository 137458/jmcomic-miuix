// SPDX-License-Identifier: Apache-2.0
package com.jm3.app.miuix.ui.coui.page

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.ui.CheckInViewState
import com.jm3.app.miuix.ui.coui.component.CouiAvatar
import com.jm3.app.miuix.ui.coui.component.CouiErrorGroup
import com.jm3.app.miuix.ui.coui.component.CouiFeedLoginGroup
import com.jm3.app.miuix.ui.coui.component.CouiLayoutTokens
import com.jm3.app.miuix.ui.coui.component.CouiLoadingGroup
import com.jm3.app.miuix.ui.coui.component.CouiMinTouchTarget
import com.jm3.app.miuix.ui.coui.component.CouiPageScaffold
import com.jm3.app.miuix.ui.coui.component.CouiSectionTitleText
import com.jm3.app.miuix.ui.coui.component.couiDestructiveDialogAction
import com.jm3.app.miuix.ui.coui.component.couiPageScroll
import com.jm3.app.miuix.ui.coui.component.couiPressable
import com.jm3.app.miuix.ui.coui.component.isCouiWideWindow
import com.jm3.app.miuix.ui.coui.component.panelBlur
import com.jm3.app.miuix.ui.coui.component.rememberCouiPageScrollBehavior
import com.jm3.app.miuix.ui.coui.icon.CouiIcons
import com.jm3.app.miuix.ui.mine.MineCheckInRow
import com.jm3.app.miuix.ui.mine.MineEntry
import com.jm3.app.miuix.ui.mine.rememberMineScaffoldState
import com.jm3.app.miuix.ui.readableWidth
import com.jm3.core.model.Jm3User
import com.jm3.core.session.UnreadBadge
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.BasicComponentDefaults
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardListGroup
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.InfiniteProgressIndicator
import io.github.suqi8.coui.kmp.basic.LinearProgressIndicator
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.blur.MaterialBlurDefaults
import io.github.suqi8.coui.kmp.layout.DialogButtonBar
import io.github.suqi8.coui.kmp.layout.DialogButtonBarAction
import io.github.suqi8.coui.kmp.layout.DialogDefaults
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.preference.PreferenceCategory
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.rememberCOUIFlingBehavior
import top.yukonga.miuix.kmp.basic.SnackbarHostState

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
    val state = rememberMineScaffoldState(
        deps = deps,
        unreadBadge = unreadBadge,
        snackbarHostState = snackbarHostState,
        onOpenLogin = onOpenLogin,
        logoutFailureNotice = "退出登录失败，请稍后重试",
    )
    val user = state.user
    val unread = state.unread
    val activeDownloads = state.inFlightDownloads

    val listState = rememberLazyListState()
    val scrollBehavior = rememberCouiPageScrollBehavior {
        listState.canScrollForward || listState.canScrollBackward
    }

    CouiPageScaffold(
        title = "我的",
        wide = isCouiWideWindow(),
        scrollBehavior = scrollBehavior,
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .readableWidth()
                    .couiPageScroll()
                    .consumeWindowInsets(innerPadding),
                contentPadding = innerPadding,
                flingBehavior = rememberCOUIFlingBehavior(),
            ) {
                item(key = "mine-account") {
                    MineAccountCard(
                        user = user,
                        unread = unread,
                        onOpenLogin = onOpenLogin,
                        onOpenProfile = onOpenProfile,
                    )
                }

                if (state.showsCheckIn) {
                    item(key = "mine-checkin") {
                        MineCheckInGroup(
                            view = state.checkInState,
                            row = state.checkInRow,
                            onCheckIn = state::checkIn,
                            onReload = state::reloadCheckIn,
                            onOpenLogin = onOpenLogin,
                        )
                    }
                }

                item(key = "mine-content") {
                    PreferenceCategory(title = "内容")
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = CouiLayoutTokens.HorizontalMargin)
                            .padding(bottom = CouiLayoutTokens.GroupSpacing),
                    ) {
                        CardListGroup {
                            item(key = "favorites") {
                                MineNavRow(
                                    icon = CouiIcons.Star,
                                    title = "我的收藏",
                                    value = user?.albumFavorites?.takeIf { it > 0 }?.toString(),
                                    onClick = { state.enter(MineEntry.Favorites, onOpenFavorites) },
                                )
                            }
                            item(key = "history") {
                                MineNavRow(
                                    icon = CouiIcons.History,
                                    title = "阅读历史",
                                    onClick = { state.enter(MineEntry.History, onOpenHistory) },
                                )
                            }
                            item(key = "shelf") {
                                MineNavRow(
                                    icon = CouiIcons.Download,
                                    title = "本地书架",
                                    dot = activeDownloads > 0,
                                    onClick = { state.enter(MineEntry.LocalShelf, onOpenShelf) },
                                )
                            }
                            item(key = "watch-history") {
                                MineNavRow(
                                    icon = CouiIcons.Clock,
                                    title = "观看历史",
                                    summary = "账号同步的阅读足迹",
                                    onClick = { state.enter(MineEntry.WatchHistory, onOpenWatchHistory) },
                                )
                            }
                            item(key = "tracking") {
                                MineNavRow(
                                    icon = CouiIcons.Refresh,
                                    title = "连载追踪",
                                    onClick = { state.enter(MineEntry.Tracking, onOpenTracking) },
                                )
                            }
                            item(key = "tasks") {
                                MineNavRow(
                                    icon = CouiIcons.Check,
                                    title = "成就任务",
                                    summary = "称号与勋章",
                                    onClick = { state.enter(MineEntry.Tasks, onOpenTasks) },
                                )
                            }
                        }
                    }
                }

                item(key = "mine-community") {
                    PreferenceCategory(title = "社区")
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = CouiLayoutTokens.HorizontalMargin)
                            .padding(bottom = CouiLayoutTokens.GroupSpacing),
                    ) {
                        CardListGroup {
                            item(key = "forum") {
                                MineNavRow(
                                    icon = CouiIcons.Comment,
                                    title = "社区论坛",
                                    summary = "全部讨论 · 漫画评论 · 闲聊",
                                    onClick = { state.enter(MineEntry.Forum, onOpenForum) },
                                )
                            }
                            item(key = "novel") {
                                MineNavRow(
                                    icon = CouiIcons.Book,
                                    title = "小说",
                                    summary = "连载小说阅读",
                                    onClick = { state.enter(MineEntry.Novel, onOpenNovel) },
                                )
                            }
                            item(key = "library") {
                                MineNavRow(
                                    icon = CouiIcons.Folder,
                                    title = "禁漫书库",
                                    summary = "创作者与作品",
                                    onClick = { state.enter(MineEntry.Library, onOpenLibrary) },
                                )
                            }
                            item(key = "profile") {
                                MineNavRow(
                                    icon = CouiIcons.Mine,
                                    title = "个人资料",
                                    onClick = { state.enter(MineEntry.Profile, onOpenProfile) },
                                )
                            }
                            item(key = "notifications") {
                                MineNavRow(
                                    icon = CouiIcons.Bell,
                                    title = "通知",
                                    summary = when {
                                        user == null -> "登录后接收提醒"
                                        unread > 0 -> "$unread 条未读通知"
                                        else -> ""
                                    },
                                    badge = unread,
                                    onClick = { state.enter(MineEntry.Notifications, onOpenNotifications) },
                                )
                            }
                        }
                    }
                }

                item(key = "mine-other") {
                    PreferenceCategory(title = "其他")
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = CouiLayoutTokens.HorizontalMargin)
                            .padding(bottom = CouiLayoutTokens.GroupSpacing),
                    ) {
                        CardListGroup {
                            item(key = "settings") {
                                MineNavRow(
                                    icon = CouiIcons.Settings,
                                    title = "设置",
                                    onClick = { state.enter(MineEntry.Settings, onOpenSettings) },
                                )
                            }
                            if (user != null) {
                                item(key = "logout") {
                                    MineNavRow(
                                        icon = CouiIcons.LogOut,
                                        title = "退出登录",
                                        titleColor = COUITheme.colorScheme.error,
                                        onClick = state::askLogout,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 退出登录确认弹窗置于 LazyColumn 之外、CouiPageScaffold 之内
            val dialogBlur = panelBlur(MaterialBlurDefaults::dialog)
            OverlayDialog(
                show = state.logoutDialogVisible,
                title = "退出登录",
                summary = "确定要登出账号吗？",
                backgroundModifier = dialogBlur?.backgroundModifier ?: Modifier,
                backgroundColor = dialogBlur?.backgroundColor ?: DialogDefaults.backgroundColor(),
                materialStroke = dialogBlur != null,
                onDismissRequest = state::dismissLogout,
            ) {
                DialogButtonBar(
                    negative = DialogButtonBarAction(
                        text = "取消",
                        onClick = state::dismissLogout,
                    ),
                    positive = null,
                    neutral = couiDestructiveDialogAction(
                        text = "确认退出",
                        onClick = state::submitLogout,
                    ),
                )
            }
        }
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
    val scheme = COUITheme.colorScheme
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CouiLayoutTokens.HorizontalMargin)
            .padding(top = 8.dp, bottom = CouiLayoutTokens.GroupSpacing),
        onClick = { if (user == null) onOpenLogin() else onOpenProfile() },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CouiLayoutTokens.RowPadding, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CouiAvatar(url = user?.photoUrl, size = 56.dp, name = user?.username)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                CouiSectionTitleText(
                    modifier = Modifier.fillMaxWidth(),
                    text = user?.username ?: "未登录",
                    textAlign = TextAlign.Start,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = user?.let { mineAccountSummary(it) } ?: "登录后同步收藏、书架与观看记录",
                    style = COUITheme.textStyles.bodyS,
                    color = scheme.labelSecondary,
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
}

/** 等级、硬币与经验同为账号基础信息，压在一行里由省略号兜住窄屏。 */
internal fun mineAccountSummary(user: Jm3User): String {
    val levelName = user.levelName?.trim().orEmpty()
    val level = if (levelName.isEmpty()) "Lv.${user.level}" else "Lv.${user.level} $levelName"
    return "$level · 硬币 ${user.coin} · 经验 ${user.currentExp}/${user.nextLevelExp}"
}

@Composable
private fun MineLoginCapsule(onOpenLogin: () -> Unit) {
    val scheme = COUITheme.colorScheme
    Box(
        modifier = Modifier
            .couiPressable(
                shape = CircleShape,
                backgroundColor = scheme.primary,
                minTouchTarget = CouiMinTouchTarget,
                onClick = onOpenLogin,
            )
            .padding(horizontal = 18.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "登录",
            style = COUITheme.textStyles.buttonM,
            color = scheme.onPrimary,
        )
    }
}

/** 未读角标沿用胶囊语言；三位以上收敛成 99+，避免撑宽右侧槽位。 */
@Composable
private fun MineUnreadCapsule(count: Int) {
    val scheme = COUITheme.colorScheme
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(scheme.primary)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            style = COUITheme.textStyles.bodyXS,
            color = scheme.onPrimary,
        )
    }
}

/** 分组行图标统一走同一尺寸与色阶，不手动加额外内边距。 */
@Composable
private fun MineGroupIcon(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(CouiLayoutTokens.IconSize),
        tint = COUITheme.colorScheme.labelSecondary,
    )
}

/** COUI 设置跳转行：委托至 [ArrowPreference]，红点与数字角标走原生 [endBadgeCount]。 */
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
    val resolvedTitleColor = if (titleColor == Color.Unspecified) {
        BasicComponentDefaults.titleColor()
    } else {
        BasicComponentDefaults.titleColor(color = titleColor)
    }
    val endBadgeCount: Int? = when {
        badge > 0 -> badge
        dot -> 0
        else -> null
    }
    ArrowPreference(
        title = title,
        titleColor = resolvedTitleColor,
        summary = summary.ifEmpty { null },
        startAction = { MineGroupIcon(icon) },
        endBadgeCount = endBadgeCount,
        endActions = {
            if (badge <= 0 && !dot && value != null) {
                Text(
                    text = value,
                    style = COUITheme.textStyles.bodyM,
                    color = COUITheme.colorScheme.labelTertiary,
                )
            }
        },
        onClick = onClick,
    )
}

/** 签到组：一行主操作加月度进度；失效态只解释原因并给登录入口。 */
@Composable
private fun MineCheckInGroup(
    view: CheckInViewState,
    row: MineCheckInRow?,
    onCheckIn: () -> Unit,
    onReload: () -> Unit,
    onOpenLogin: () -> Unit,
) {
    val scheme = COUITheme.colorScheme
    when {
        view.expiredMessage != null -> CouiFeedLoginGroup(
            title = "每日签到",
            message = view.expiredMessage,
            onOpenLogin = onOpenLogin,
        )

        row != null -> {
            val display = row.display
            val hasBottomContent = row.showsProgress || view.error != null
            PreferenceCategory(title = "每日签到")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CouiLayoutTokens.HorizontalMargin)
                    .padding(bottom = CouiLayoutTokens.GroupSpacing),
            ) {
                CardListGroup {
                    item(key = "checkin-row") {
                        BasicComponent(
                            startAction = {
                                MineGroupIcon(
                                    if (row.signedToday) CouiIcons.CalendarCheck else CouiIcons.Calendar,
                                )
                            },
                            title = row.title,
                            summary = row.summary,
                            endActions = {
                                if (row.busy) {
                                    InfiniteProgressIndicator(
                                        size = 16.dp,
                                        strokeWidth = 2.dp,
                                        color = scheme.primary,
                                    )
                                } else if (row.signedToday) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = CouiIcons.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = scheme.primaryText,
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = "已签到",
                                            style = COUITheme.textStyles.bodyM,
                                            color = scheme.primaryText,
                                        )
                                    }
                                }
                            },
                            bottomAction = if (hasBottomContent) {
                                {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        if (row.showsProgress) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                            ) {
                                                Text(
                                                    text = "本月签到",
                                                    style = COUITheme.textStyles.bodyS,
                                                    color = scheme.labelSecondary,
                                                )
                                                Text(
                                                    text = display.progressLabel,
                                                    style = COUITheme.textStyles.bodyS,
                                                    color = scheme.labelSecondary,
                                                )
                                            }
                                            LinearProgressIndicator(
                                                progress = display.progress.coerceIn(0f, 1f),
                                                modifier = Modifier.fillMaxWidth(),
                                                height = 4.dp,
                                            )
                                            if (display.missedDays > 0) {
                                                Text(
                                                    text = "本月已有 ${display.missedDays} 天未签到",
                                                    style = COUITheme.textStyles.bodyXS,
                                                    color = scheme.labelSecondary,
                                                )
                                            }
                                        }
                                        if (view.error != null) {
                                            Text(
                                                text = view.error,
                                                style = COUITheme.textStyles.bodyS,
                                                color = scheme.error,
                                                textAlign = TextAlign.Start,
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                        }
                                    }
                                }
                            } else {
                                null
                            },
                            onClick = if (row.enabled) onCheckIn else null,
                        )
                    }
                }
            }
        }

        view.loading -> CouiLoadingGroup(text = "正在加载签到状态…")
        view.error != null -> CouiErrorGroup(message = view.error, onRetry = onReload)
        else -> CouiErrorGroup(message = "没有取到签到信息，可能是网络波动", onRetry = onReload)
    }
}
