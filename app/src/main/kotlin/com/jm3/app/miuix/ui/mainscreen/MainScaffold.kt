package com.jm3.app.miuix.ui.mainscreen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.ui.glass.GlassBottomBarTokens
import com.jm3.app.miuix.ui.glass.resolveBottomBarTokens
import com.jm3.core.session.UnreadBadge
import com.jm3.core.settings.BottomBarForm
import com.jm3.core.settings.BottomBarPreferencePolicy
import com.jm3.core.settings.BottomBarSize
import com.jm3.core.settings.UiSkin
import com.jm3.core.settings.WideScreenNavigation

/*
 * 主屏的单一真源：HyperOS 与 ColorOS 两个皮肤此前把同一份分页表、栏位解析与启动接线写了两遍。
 *
 * 收进来的是行为与口径：四个分页、角标规则、底栏/侧栏解析、内容可用宽度、选页与启动动作。
 * 视觉一律不进来——两种导航栏、转场动画、骨架与四个页面本身仍在各皮肤文件里，
 * 所以本文件不 import 任何 Miuix / COUI 控件。
 * 皮肤差异用参数表达：皮肤档位 [UiSkin] 决定底栏尺寸口径（ColorOS 恒标准档），
 * 侧栏实际宽度由皮肤量出来传给 [mainContentWidthDp]。
 */

/** 主屏四个分页：两皮肤同序同文案，顺序即底栏与侧栏的展示顺序。 */
internal enum class MainTab(val label: String) {
    HOME("首页"),
    CATEGORY("分类"),
    RANK("榜单"),
    MINE("我的"),
}

/** 分页角标：未读优先，其次在途下载；只有「我的」挂角标。 */
internal sealed interface MainTabBadge {

    /** 不挂角标。 */
    data object None : MainTabBadge

    /** 未读数角标，超过上限显示 99+。 */
    data class Unread(val text: String) : MainTabBadge

    /** 在途下载角标（无数字，只表示后台在忙）。 */
    data object Downloads : MainTabBadge
}

/** 未读角标的最大显示值：再多也只写 99+，免得撑坏底栏图标。 */
internal const val UNREAD_BADGE_MAX = 99

/** 未读数文案：超过 [UNREAD_BADGE_MAX] 收敛成 99+。 */
internal fun unreadBadgeText(unread: Int): String =
    if (unread > UNREAD_BADGE_MAX) {
        UNREAD_BADGE_MAX.toString() + "+"
    } else {
        unread.toString()
    }

/** 分页角标规则：未读优先于在途下载，且只有「我的」这一项挂角标。 */
internal fun mainTabBadge(tab: MainTab, unread: Int, activeDownloads: Int): MainTabBadge = when {
    tab != MainTab.MINE -> MainTabBadge.None
    unread > 0 -> MainTabBadge.Unread(unreadBadgeText(unread))
    activeDownloads > 0 -> MainTabBadge.Downloads
    else -> MainTabBadge.None
}

/**
 * 内容可用宽度：用侧栏时扣掉侧栏实际宽度。
 *
 * 两皮肤量宽的方式不同（Miuix 在布局里实测，COUI 用固定码值），量不到时传 0 即等价于不扣。
 */
internal fun mainContentWidthDp(widthDp: Int, useRail: Boolean, railWidthDp: Int): Int =
    if (useRail) {
        widthDp - railWidthDp
    } else {
        widthDp
    }

/** 主屏栏位解析结果：宽屏导航形态 + 生效的底栏尺寸/形态 token。 */
internal data class MainBarLayout(
    val wideScreenNavigation: WideScreenNavigation,
    val tokens: GlassBottomBarTokens,
) {

    /** 走侧栏还是走底栏。 */
    val useRail: Boolean get() = wideScreenNavigation == WideScreenNavigation.RAIL
}

/**
 * 按窗口宽高、用户显式设置与皮肤档位解析栏位。
 *
 * 宽屏导航由 [BottomBarPreferencePolicy.resolveActiveWideScreenNavigation] 判定（宽高都够才给侧栏）；
 * 底栏尺寸过皮肤口径，用户没设时按断点自适应，形态没设恒悬浮。
 */
internal fun resolveMainBarLayout(
    wideScreenNavigation: WideScreenNavigation?,
    explicitSize: BottomBarSize?,
    explicitForm: BottomBarForm?,
    widthDp: Int,
    heightDp: Int,
    skin: UiSkin = UiSkin.HYPEROS,
): MainBarLayout {
    val navigation = BottomBarPreferencePolicy.resolveActiveWideScreenNavigation(wideScreenNavigation, widthDp, heightDp)
    return MainBarLayout(
        wideScreenNavigation = navigation,
        tokens = resolveBottomBarTokens(explicitSize = explicitSize, explicitForm = explicitForm, widthDp = widthDp, skin = skin),
    )
}

/**
 * 主屏的启动依赖：只开启动接线要用的两扇门。
 *
 * 窄端口是为了让状态机能在纯 kotlin.test 里跑（不碰 Android、不碰下载器实现）。
 */
internal interface MainScaffoldPort {

    /** 拉起下载队列（应用启动后就得开始补投）。 */
    suspend fun ensureDownloadsStarted()

    /** 刷新未读角标数；未登录时归零。 */
    suspend fun refreshUnread(signedIn: Boolean)
}

/** 主屏端口来源：下载器与未读角标。 */
private fun mainScaffoldPort(deps: ScreenDeps, unreadBadge: UnreadBadge): MainScaffoldPort {
    val downloadManager = deps.downloadManager
    return object : MainScaffoldPort {
        override suspend fun ensureDownloadsStarted() {
            downloadManager.ensureStarted()
        }

        override suspend fun refreshUnread(signedIn: Boolean) {
            unreadBadge.refresh(signedIn)
        }
    }
}

/**
 * 主屏状态机：当前分页 + 启动接线。
 *
 * 分页值的保存必须留在 rememberSaveable 里（进程重建后要回到同一页），
 * 所以这里只做越界校验与动作转发，读写各自走一对方程。
 */
@Stable
internal class MainScaffoldState internal constructor(
    private val port: MainScaffoldPort,
    private val tabIndex: () -> Int,
    private val onTabIndexChange: (Int) -> Unit,
) {

    /** 当前分页下标，取值必在 [MainTab] 范围内。 */
    val tab: Int get() = tabIndex()

    /** 选页：越界下标直接忽略，避免动画里出现空页。 */
    fun selectTab(index: Int) {
        if (index in MainTab.entries.indices) {
            onTabIndexChange(index)
        }
    }

    /** 应用起来后拉起下载队列。 */
    suspend fun start() {
        port.ensureDownloadsStarted()
    }

    /** 登录状态变化后刷新未读角标。 */
    suspend fun refreshUnread(signedIn: Boolean) {
        port.refreshUnread(signedIn)
    }
}

/**
 * 建立主屏状态机并按既有次序接上线：进入即拉起下载队列，登录用户变化即刷未读。
 *
 * 未读与下载角标不入状态机——角标要跟着两条流走，交由皮肤各自收集后调 [mainTabBadge] 渲染，
 * 免得经状态机转手慢一帧。
 */
@Composable
internal fun rememberMainScaffoldState(deps: ScreenDeps, unreadBadge: UnreadBadge): MainScaffoldState {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val state = remember(deps, unreadBadge) {
        MainScaffoldState(
            port = mainScaffoldPort(deps, unreadBadge),
            tabIndex = { tab },
            onTabIndexChange = { tab = it },
        )
    }
    LaunchedEffect(state) { state.start() }
    val user by deps.account.user.collectAsState()
    LaunchedEffect(state, user?.uid) { state.refreshUnread(user != null) }
    return state
}
