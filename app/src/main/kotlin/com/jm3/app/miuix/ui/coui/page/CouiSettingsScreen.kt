// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.page

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.Dialog
import com.itos.coui.layout.Divider
import com.itos.coui.layout.Group
import com.itos.coui.layout.Item
import com.itos.coui.scroll.couiVerticalScroll
import com.itos.coui.switch.Switch
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiColor
import com.itos.coui.theme.CouiTheme as CouiKitTheme
import com.itos.coui.topappbar.CouiTopAppBarDefaults
import com.itos.coui.topappbar.LargeTopAppBar
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.ui.GlassSnackbarHost
import com.jm3.app.miuix.ui.ReaderSettingsSection
import com.jm3.app.miuix.ui.coui.component.CouiAppScaffold
import com.jm3.app.miuix.ui.coui.component.CouiSegmentedCapsules
import com.jm3.app.miuix.ui.coui.icon.CouiIcons
import com.jm3.app.miuix.ui.coui.theme.CouiTheme
import com.jm3.app.miuix.ui.planLoad
import com.jm3.app.miuix.ui.readableWidth
import com.jm3.app.miuix.ui.rememberWindowDpSize
import com.jm3.app.miuix.ui.resolvePaginationSelection
import com.jm3.app.miuix.ui.toLayoutDpInt
import com.jm3.core.reader.ReaderSpreadPolicy
import com.jm3.core.runCatchingSuspending
import com.jm3.core.session.SessionOutcome
import com.jm3.core.settings.BottomBarForm
import com.jm3.core.settings.BottomBarPreferencePolicy
import com.jm3.core.settings.BottomBarSize
import com.jm3.core.settings.PaginationMode
import com.jm3.core.settings.ThemeMode
import com.jm3.core.settings.UiSkin
import com.jm3.core.settings.WideScreenNavigation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SnackbarHostState

private fun ThemeMode.couiLabel(): String = when (this) {
    ThemeMode.SYSTEM -> "跟随系统"
    ThemeMode.LIGHT -> "浅色"
    ThemeMode.DARK -> "深色"
}

private fun PaginationMode.couiLabel(): String = when (this) {
    PaginationMode.INFINITE -> "无限滚动"
    PaginationMode.CLICK -> "点击翻页"
}

private fun UiSkin.couiSummary(): String = when (this) {
    UiSkin.HYPEROS -> "HyperOS：液态玻璃折射与饱和高光"
    UiSkin.COLOROS -> "ColorOS：均匀磨砂与材质混色"
}

/** 版本号连点触发隐藏诊断的阈值与窗口。 */
private const val DIAGNOSTIC_TAPS = 5
private const val DIAGNOSTIC_TAP_WINDOW_MS = 2000L

/** 清空确认留着自动收起的时长：太短会在用户读文案时就消失。 */
private const val CLEAR_CONFIRM_TIMEOUT_MS = 5000L

/**
 * 设置页当前打开的选择面板。
 *
 * 五个枚举型设置共用一套「标题 + 选项 + 完成/取消」的 COUI 弹窗，
 * 用一个可空状态描述当前打开哪一格，避免并行布尔互相叠加出两个弹窗。
 * 阅读器分项不在此列：它们已交给设置中心共用的 ReaderSettingsSection 自行弹层。
 */
private enum class SettingsPicker {
    PAGINATION_MODE,
    BOTTOM_BAR_SIZE,
    BOTTOM_BAR_FORM,
    WIDE_SCREEN_NAVIGATION,
    UI_SKIN,
}

/**
 * COUI 设置页：整页按 COUI-Kit 原厂语言重做，业务语义与存储完全沿用现有实现。
 *
 * 分组用 Group/Item 行、枚举选择用带选中勾的 COUI Dialog、主题模式用胶囊分段控件；
 * 读写一律经 [ScreenDeps.preferences] 与既有回调，不新增键、不改 null 语义。
 * 本页是独立路由，不在主框架内，所以自己持有一套 CouiTheme 与 Scaffold。
 */
@Composable
fun CouiSettingsScreen(
    deps: ScreenDeps,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    dynamicColor: Boolean,
    onDynamicColorChange: (Boolean) -> Unit,
    dynamicColorSupported: Boolean,
    uiSkin: UiSkin,
    onUiSkinChange: (UiSkin) -> Unit,
    onBack: () -> Unit,
    onOpenUpdate: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    CouiTheme {
        val scheme = CouiKitTheme.colorScheme
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        // 网关与结局翻译统一从 ScreenDeps 取，调用方不再并行传一份
        val gateway = deps.gateway
        val authCall = deps.authCall
        val repository = deps.preferences.repository

        val domainsInput = rememberSaveable { mutableStateOf("") }
        var loadedSaved by rememberSaveable { mutableStateOf(false) }
        val activeDomains = rememberSaveable { mutableStateOf("") }
        val applying = remember { mutableStateOf(false) }

        // 线路诊断：当前实际域名与各域名探活延迟（毫秒升序）。
        var usedDomain by remember { mutableStateOf<String?>(null) }
        var domainLatency by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
        var reprobeBusy by remember { mutableStateOf(false) }
        var diagError by remember { mutableStateOf<String?>(null) }

        // 破坏性操作二次确认：5 秒未确认自动复位；清空执行中不自动关，等任务结束。
        var clearTarget by rememberSaveable { mutableStateOf<ClearTarget?>(null) }
        var clearing by remember { mutableStateOf(false) }
        var showDisclaimer by rememberSaveable { mutableStateOf(false) }
        var picker by rememberSaveable { mutableStateOf<SettingsPicker?>(null) }

        // 分页模式：只读全局设置域里的可观察状态，本地不再留一份会滞后的副本
        val paginationMode = deps.preferences.paginationMode
        // 底栏尺寸/形态：偏好存 null 表示未显式设置，展示与生效都按同一处窗口断点解析
        val windowDp = rememberWindowDpSize()
        val widthDp = windowDp.width.toLayoutDpInt()
        val bottomBarSize = BottomBarPreferencePolicy.resolveSize(deps.preferences.bottomBarSize, widthDp)
        // 尺寸档只有「偏大」是强制选择；「普通」与未设置同义，都表示按设备自适应
        val bottomBarSizeOption =
            if (deps.preferences.bottomBarSize == BottomBarSize.GENEROUS) BottomBarSize.GENEROUS else BottomBarSize.STANDARD
        val bottomBarForm = BottomBarPreferencePolicy.resolveForm(deps.preferences.bottomBarForm)
        // 宽屏导航：未设置时默认底栏，用户显式选侧栏才切换；
        // 该项只在 600dp 及以上（含小平板/折叠展开/分屏）渲染整行，其余设备完全隐藏。
        val wideScreenNavigation =
            BottomBarPreferencePolicy.resolveWideScreenNavigation(deps.preferences.wideScreenNavigation)
        val showWideScreenNavigation = BottomBarPreferencePolicy.supportsWideScreenNavigation(widthDp)
        // 阅读器设置：与分页模式同源，读写沿用现有设置存储，不新建二级页
        // 双页只在 600dp 以上横屏可见；其它窗口尺寸整行不渲染，阅读器也会自动回落到单页
        val readerSpreadAllowed = ReaderSpreadPolicy.supportsSpread(
            widthDp,
            windowDp.height.toLayoutDpInt(),
        )
        // 版本号连点进入隐藏诊断
        var diagnostics by remember { mutableStateOf(false) }
        var versionTapCount by remember { mutableIntStateOf(0) }
        // 诊断读取的代号：进页、探活、应用线路都会触发，只让最后一次写的旧结果落不到界面
        var diagnosticsRequest by remember { mutableIntStateOf(0) }

        val versionName = remember {
            @Suppress("DEPRECATION")
            runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
                .getOrNull() ?: "未知"
        }
        val versionCode = remember {
            @Suppress("DEPRECATION")
            runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).let { info ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()
                }
            }.getOrDefault(0L)
        }

        fun applyPaginationMode(mode: PaginationMode) {
            if (mode == deps.preferences.paginationMode) return
            deps.preferences.paginationMode = mode
            // 列表页此时已被设置路由移出组合；作废旧模式的缓存，回来时才会按新语义从第 1 页加载。
            deps.sessionCache.forgetPages()
            scope.launch { deps.preferences.repository.setPaginationMode(mode) }
        }

        fun applyBottomBarSize(size: BottomBarSize) {
            // 「普通」表示自适应，清空显式值回到断点解析；只有「偏大」是需要落盘的强制选择
            val target = if (size == BottomBarSize.GENEROUS) BottomBarSize.GENEROUS else null
            if (target == deps.preferences.bottomBarSize) return
            deps.preferences.bottomBarSize = target
            scope.launch {
                if (target == null) deps.preferences.repository.clearBottomBarSize()
                else deps.preferences.repository.setBottomBarSize(target)
            }
        }

        fun applyBottomBarForm(form: BottomBarForm) {
            if (form == deps.preferences.bottomBarForm) return
            deps.preferences.bottomBarForm = form
            scope.launch { deps.preferences.repository.setBottomBarForm(form) }
        }

        fun applyWideScreenNavigation(navigation: WideScreenNavigation) {
            if (navigation == deps.preferences.wideScreenNavigation) return
            deps.preferences.wideScreenNavigation = navigation
            scope.launch { deps.preferences.repository.setWideScreenNavigation(navigation) }
        }

        // 连点计数在窗口内归零，避免误触累计
        LaunchedEffect(versionTapCount) {
            if (versionTapCount in 1 until DIAGNOSTIC_TAPS) {
                delay(DIAGNOSTIC_TAP_WINDOW_MS)
                versionTapCount = 0
            }
        }

        /** 读一次线路诊断；调用方在协程内等到读完，避免旧数据先回到界面。 */
        suspend fun loadDomainsDiagnostics() {
            val request = ++diagnosticsRequest
            diagError = null
            // 两个网关查询合成一次结局：等待会话恢复、切 IO、判失效都在 AuthCall 内完成。
            val result = authCall.call {
                gateway.usedDomain() to
                    gateway.domainLatency().entries
                        .sortedBy { it.value }
                        .map { it.key to it.value }
            }
            // 探活与应用线路都可能并发触发读取，只有后发起的那次代表最新线路表
            if (request != diagnosticsRequest) return
            when (result) {
                is SessionOutcome.Ok -> {
                    usedDomain = result.value.first
                    domainLatency = result.value.second
                }
                else -> diagError = planLoad(
                    result,
                    hasContent = false,
                    failureMessage = "线路诊断失败",
                ).message
            }
        }

        fun reprobeDomains() {
            if (reprobeBusy) return
            reprobeBusy = true
            scope.launch {
                val result = authCall.call { gateway.reprobeDomains() }
                // 先重读诊断再解锁：按钮解锁那一刻列表已是新延迟，不会停留在旧数字。
                // 无论成败都重读一次，失败时也保留上一次可用的探活结果。
                loadDomainsDiagnostics()
                reprobeBusy = false
                when (result) {
                    is SessionOutcome.Ok -> snackbarHostState.showSnackbar("线路探活完成")
                    else -> snackbarHostState.showSnackbar(
                        "探活失败：" + planLoad(result, hasContent = false, failureMessage = "未知错误").message,
                    )
                }
            }
        }

        fun applyCustomDomains() {
            if (applying.value) return
            applying.value = true
            scope.launch {
                // 写入即归一（去协议头/路径/空白项并去重），读回的就是真正生效的线路表；
                // 失败也必须解锁按钮，否则本页会永久停在「应用中…」。
                val result = runCatchingSuspending {
                    deps.settings.setApiDomains(domainsInput.value.split("\n"))
                    val applied = deps.settings.apiDomains()
                    deps.applyDomains(applied)
                    applied
                }
                applying.value = false
                result.onSuccess { applied ->
                    activeDomains.value = applied.joinToString("、")
                    // 归一后的结果回填输入框，让用户看到真正生效的线路，而不是带协议头的原文。
                    domainsInput.value = applied.joinToString("\n")
                    // 网关已重建，诊断数据属于旧实例，重新读取（不阻塞提示）。
                    scope.launch { loadDomainsDiagnostics() }
                    snackbarHostState.showSnackbar(
                        if (applied.isEmpty()) "已恢复默认线路" else "已应用 " + applied.size + " 个线路域名",
                    )
                }.onFailure { error ->
                    snackbarHostState.showSnackbar("线路应用失败：" + (error.message ?: "未知错误"))
                }
            }
        }

        LaunchedEffect(Unit) {
            if (!loadedSaved) {
                loadedSaved = true
                val saved = deps.settings.apiDomains()
                domainsInput.value = saved.joinToString("\n")
                activeDomains.value = saved.joinToString("、")
            }
            loadDomainsDiagnostics()
        }

        LaunchedEffect(clearTarget, clearing) {
            if (clearTarget != null && !clearing) {
                delay(CLEAR_CONFIRM_TIMEOUT_MS)
                clearTarget = null
            }
        }

        val scrollState = rememberScrollState()
        // 折叠顶栏：正文自己消费滚动，顶栏按同一套回弹手势收起/展开
        val scrollBehavior = CouiTopAppBarDefaults.exitUntilCollapsedScrollBehavior()

        val themeModeSummary = when (themeMode) {
            ThemeMode.SYSTEM -> "跟随系统自动切换深浅色"
            ThemeMode.LIGHT -> "始终保持浅色高亮视觉"
            ThemeMode.DARK -> "始终保持深色暗调视觉"
        }
        val paginationModeSummary = when (paginationMode) {
            PaginationMode.INFINITE -> "滚动到底自动加载下一页"
            PaginationMode.CLICK -> "列表底部显示上一页/下一页"
        }
        val bottomBarSizeSummary = if (bottomBarSizeOption == BottomBarSize.GENEROUS) {
            "偏大：所有设备都使用更高的底栏，图标与标签同步放大"
        } else {
            "普通：按设备自适应，手机用标准高度、平板用偏大高度" +
                "（当前设备按" + (if (bottomBarSize == BottomBarSize.GENEROUS) "偏大" else "标准") + "显示）"
        }
        val bottomBarFormSummary = when (bottomBarForm) {
            BottomBarForm.FLOATING -> "圆角胶囊，与屏幕底边留出悬浮间隙"
            BottomBarForm.DOCKED -> "贴底通栏，紧接导航栏上方并撑满宽度"
        }
        val wideScreenNavigationSummary = when (wideScreenNavigation) {
            WideScreenNavigation.BOTTOM_BAR -> "宽屏同样显示底栏，与手机保持同一套样式"
            WideScreenNavigation.RAIL -> "宽屏改用可收起的侧栏，列表获得更宽横向空间"
        }

        Box(modifier = Modifier.fillMaxSize()) {
            CouiAppScaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = {
                    // 顶栏必须写在 Scaffold 的 topBar 槽位：库在那里提供 backdrop，
                    // 自己提到外面会拿不到采样源，直接抛 checkNotNull。
                    LargeTopAppBar(
                        title = {
                            Text(
                                text = "设置",
                                kind = CouiTextKind.PageTitle,
                                textAlign = TextAlign.Start,
                            )
                        },
                        navigationIcon = { SettingsBackButton(onClick = onBack) },
                        scrollBehavior = scrollBehavior,
                    )
                },
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        // 宽屏下留出可读栏宽，和设置中心保持一致
                        .readableWidth()
                        .nestedScroll(scrollBehavior.nestedScrollConnection)
                        .couiVerticalScroll(scrollState)
                        // 顶栏的高与折叠位移由 state 参与计算，正文才会跟着顶栏一起让位
                        .padding(scrollBehavior.state.contentPadding(innerPadding, LocalDensity.current))
                        .consumeWindowInsets(innerPadding)
                        .padding(bottom = 24.dp),
                ) {
                    Group(title = "主题") {
                        Item(
                            title = { SettingsRowTitle("外观模式") },
                            summary = { SettingsRowSummary(themeModeSummary) },
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                        ) {
                            CouiSegmentedCapsules(
                                items = ThemeMode.entries.map { it.couiLabel() },
                                selectedIndex = ThemeMode.entries.indexOf(themeMode),
                                onSelect = { index ->
                                    ThemeMode.entries.getOrNull(index)
                                        ?.takeIf { it != themeMode }
                                        ?.let(onThemeModeChange)
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Divider()
                        Item(
                            title = { SettingsRowTitle("界面风格") },
                            summary = { SettingsRowSummary(uiSkin.couiSummary()) },
                            trailing = { SettingsRowValue(uiSkin.label) },
                            onClick = { picker = SettingsPicker.UI_SKIN },
                        )
                        Divider()
                        Item(
                            title = { SettingsRowTitle("动态取色") },
                            summary = {
                                SettingsRowSummary(
                                    if (dynamicColorSupported) {
                                        "跟随壁纸生成配色（Android 12 及以上）"
                                    } else {
                                        "ColorOS 皮肤固定自有色板，动态取色不生效"
                                    },
                                )
                            },
                            trailing = {
                                Switch(
                                    checked = dynamicColor,
                                    onCheckedChange = onDynamicColorChange,
                                    enabled = dynamicColorSupported,
                                )
                            },
                        )
                    }

                    Group(title = "通用") {
                        Item(
                            title = { SettingsRowTitle("分页模式") },
                            summary = { SettingsRowSummary(paginationModeSummary) },
                            trailing = { SettingsRowValue(paginationMode.couiLabel()) },
                            onClick = { picker = SettingsPicker.PAGINATION_MODE },
                        )
                        Divider()
                        Item(
                            title = { SettingsRowTitle("底栏尺寸") },
                            summary = { SettingsRowSummary(bottomBarSizeSummary) },
                            trailing = { SettingsRowValue(bottomBarSizeOption.label) },
                            onClick = { picker = SettingsPicker.BOTTOM_BAR_SIZE },
                        )
                        Divider()
                        Item(
                            title = { SettingsRowTitle("底栏形态") },
                            summary = { SettingsRowSummary(bottomBarFormSummary) },
                            trailing = { SettingsRowValue(bottomBarForm.label) },
                            onClick = { picker = SettingsPicker.BOTTOM_BAR_FORM },
                        )
                        if (showWideScreenNavigation) {
                            Divider()
                            Item(
                                title = { SettingsRowTitle("宽屏导航") },
                                summary = { SettingsRowSummary(wideScreenNavigationSummary) },
                                trailing = { SettingsRowValue(wideScreenNavigation.label) },
                                onClick = { picker = SettingsPicker.WIDE_SCREEN_NAVIGATION },
                            )
                        }
                    }

                    // 七项阅读器设置复用设置中心的同一实现：读盘、写盘与文案只维护一处
                    Group(title = "阅读器") {
                        ReaderSettingsSection(repository = repository, spreadAllowed = readerSpreadAllowed)
                    }

                    Group(title = "线路") {
                        Item(
                            title = { SettingsRowTitle("当前线路") },
                            summary = { SettingsRowSummary(activeDomains.value.ifBlank { "默认（自动解析）" }) },
                        )
                        Divider()
                        Item(
                            title = { SettingsRowTitle("实际线路") },
                            summary = { SettingsRowSummary(usedDomain ?: "未解析") },
                        )
                        Divider()
                        if (domainLatency.isEmpty()) {
                            Item(
                                title = { SettingsRowTitle("线路延迟") },
                                summary = { SettingsRowSummary("尚未完成线路探活") },
                            )
                        } else {
                            domainLatency.forEach { (domain, latencyMs) ->
                                Item(
                                    title = { SettingsRowTitle(domain) },
                                    trailing = { SettingsRowValue(latencyMs.toString() + " ms") },
                                )
                                Divider()
                            }
                        }
                        diagError?.let { message ->
                            Item(
                                title = {
                                    Text(
                                        text = "线路诊断失败：" + message,
                                        kind = CouiTextKind.PreferenceSummary,
                                        color = if (CouiKitTheme.isDark) CouiColor.Night.RED else CouiColor.Day.RED,
                                        textAlign = TextAlign.Start,
                                    )
                                },
                            )
                            Divider()
                        }
                        Item(
                            title = { SettingsRowTitle("重新探活") },
                            summary = {
                                SettingsRowSummary(if (reprobeBusy) "探活中…" else "逐条测速并按延迟排序")
                            },
                            // 探活要走完整轮域名测速，给出进行中的转圈反馈，避免误以为点击没生效
                            trailing = {
                                if (reprobeBusy) CircularProgressIndicator(size = 20.dp)
                            },
                            onClick = ::reprobeDomains,
                            enabled = !reprobeBusy,
                        )
                        Divider()
                        // 多行线路输入：库没有多行输入组件，用基础输入框套 COUI 分组卡片
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        ) {
                            SettingsRowTitle("自定义线路")
                            SettingsRowSummary("每行一个域名，留空恢复默认")
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(scheme.onSurface.copy(alpha = 0.06f))
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                                    .heightIn(min = 84.dp),
                            ) {
                                if (domainsInput.value.isEmpty()) {
                                    Text(
                                        text = "每行一个域名，留空恢复默认",
                                        kind = CouiTextKind.PreferenceSummary,
                                        color = scheme.onSurfaceVariant,
                                        textAlign = TextAlign.Start,
                                    )
                                }
                                BasicTextField(
                                    value = domainsInput.value,
                                    onValueChange = { domainsInput.value = it },
                                    textStyle = CouiKitTheme.typography.body.copy(color = scheme.onSurface),
                                    cursorBrush = SolidColor(scheme.accent),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 64.dp),
                                )
                            }
                        }
                        Divider()
                        Item(
                            title = { SettingsRowTitle("应用线路") },
                            summary = {
                                SettingsRowSummary(if (applying.value) "应用中…" else "保存配置并重建 API 网关")
                            },
                            onClick = ::applyCustomDomains,
                            enabled = !applying.value,
                        )
                    }

                    Group(title = "数据管理") {
                        Item(
                            title = { SettingsRowTitle("清空阅读历史") },
                            summary = { SettingsRowSummary("移除全部阅读记录") },
                            onClick = { clearTarget = ClearTarget.HISTORY },
                        )
                        Divider()
                        Item(
                            title = { SettingsRowTitle("清空下载") },
                            summary = { SettingsRowSummary("清除下载任务与已下载文件") },
                            onClick = { clearTarget = ClearTarget.DOWNLOADS },
                        )
                    }

                    Group(title = "关于") {
                        // 版本行兼作隐藏诊断入口：2 秒内连点 5 次打开诊断面板
                        Item(
                            title = { SettingsRowTitle("版本") },
                            summary = { SettingsRowSummary(versionName) },
                            trailing = { SettingsRowValue(versionCode.toString()) },
                            onClick = {
                                versionTapCount += 1
                                if (versionTapCount >= DIAGNOSTIC_TAPS) {
                                    versionTapCount = 0
                                    diagnostics = true
                                }
                            },
                        )
                        Divider()
                        Item(
                            title = { SettingsRowTitle("检查更新") },
                            summary = { SettingsRowSummary("查看远端最新版本，支持应用内下载与安装") },
                            onClick = onOpenUpdate,
                        )
                        Divider()
                        Item(
                            title = { SettingsRowTitle("开源组件") },
                            summary = {
                                SettingsRowSummary(
                                    "jmcomic-api (MIT) · Miuix (Apache-2.0) · Coil (Apache-2.0) · Jetpack Compose (Apache-2.0) · " +
                                        "COUI-Kit (MPL-2.0) · Lucide (ISC)",
                                )
                            },
                        )
                        Divider()
                        Item(
                            title = { SettingsRowTitle("免责声明") },
                            summary = { SettingsRowSummary("第三方客户端，仅供学习交流，包含成人内容") },
                            onClick = { showDisclaimer = true },
                        )
                    }
                }
            }

            // 提示条叠在正文之上：它不属于 Scaffold 槽位，不能进滚动内容，否则会跟着一起滚走
            GlassSnackbarHost(
                state = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
            )
        }

        // 界面风格：沿用原数据流与存储键，只在选中不同项时回调一次。
        val openPicker = picker
        when (openPicker) {
            SettingsPicker.UI_SKIN -> SettingsChoiceDialog(
                title = "界面风格",
                options = UiSkin.entries.map { it.label },
                summaries = UiSkin.entries.map { it.couiSummary() },
                selectedIndex = UiSkin.entries.indexOf(uiSkin),
                onSelect = { index ->
                    UiSkin.entries.getOrNull(index)?.let { skin ->
                        if (skin != uiSkin) onUiSkinChange(skin)
                    }
                },
                onDismiss = { picker = null },
            )

            SettingsPicker.PAGINATION_MODE -> SettingsChoiceDialog(
                title = "分页模式",
                options = PaginationMode.entries.map { it.couiLabel() },
                selectedIndex = PaginationMode.entries.indexOf(paginationMode),
                onSelect = { index ->
                    resolvePaginationSelection(deps.preferences.paginationMode, index)
                        ?.takeIf { it.changed }
                        ?.let { applyPaginationMode(it.mode) }
                },
                onDismiss = { picker = null },
            )

            SettingsPicker.BOTTOM_BAR_SIZE -> SettingsChoiceDialog(
                title = "底栏尺寸",
                options = BottomBarSize.entries.map { if (it == BottomBarSize.STANDARD) "普通（自适应）" else it.label },
                selectedIndex = BottomBarSize.entries.indexOf(bottomBarSizeOption),
                onSelect = { index -> BottomBarSize.entries.getOrNull(index)?.let { applyBottomBarSize(it) } },
                onDismiss = { picker = null },
            )

            SettingsPicker.BOTTOM_BAR_FORM -> SettingsChoiceDialog(
                title = "底栏形态",
                options = BottomBarForm.entries.map { it.label },
                selectedIndex = BottomBarForm.entries.indexOf(bottomBarForm),
                onSelect = { index -> BottomBarForm.entries.getOrNull(index)?.let { applyBottomBarForm(it) } },
                onDismiss = { picker = null },
            )

            SettingsPicker.WIDE_SCREEN_NAVIGATION -> SettingsChoiceDialog(
                title = "宽屏导航",
                options = WideScreenNavigation.entries.map { it.label },
                selectedIndex = WideScreenNavigation.entries.indexOf(wideScreenNavigation),
                onSelect = { index ->
                    WideScreenNavigation.entries.getOrNull(index)?.let { applyWideScreenNavigation(it) }
                },
                onDismiss = { picker = null },
            )

            null -> Unit
        }

        // 隐藏诊断：版本号连点 5 次打开，只读汇总运行环境与线路状态
        if (diagnostics) {
            Dialog(
                onDismissRequest = { diagnostics = false },
                title = "诊断信息",
                confirmAction = CouiDialogAction(text = "知道了", onClick = { diagnostics = false }),
            ) {
                Group {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        SettingsDiagnosticLine(label = "包名", value = context.packageName)
                        SettingsDiagnosticLine(label = "版本", value = versionName + " (" + versionCode + ")")
                        SettingsDiagnosticLine(
                            label = "设备",
                            value = Build.MANUFACTURER + " " + Build.MODEL,
                        )
                        SettingsDiagnosticLine(
                            label = "系统",
                            value = "Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")",
                        )
                        SettingsDiagnosticLine(label = "当前线路", value = usedDomain ?: "未解析")
                        SettingsDiagnosticLine(
                            label = "自定义线路",
                            value = activeDomains.value.ifBlank { "默认（自动解析）" },
                        )
                        SettingsDiagnosticLine(
                            label = "线路延迟",
                            value = domainLatency.joinToString("\n") { (domain, ms) -> domain + " · " + ms + "ms" }
                                .ifBlank { "尚未完成线路探活" },
                        )
                        SettingsDiagnosticLine(label = "分页模式", value = paginationMode.couiLabel())
                        SettingsDiagnosticLine(
                            label = "主题",
                            value = themeMode.couiLabel() + if (dynamicColor) " · 动态取色" else "",
                        )
                    }
                }
            }
        }

        val pendingClear = clearTarget
        if (pendingClear != null) {
            Dialog(
                onDismissRequest = { if (!clearing) clearTarget = null },
                title = pendingClear.title,
                message = pendingClear.description,
                confirmAction = CouiDialogAction(
                    text = if (clearing) "清空中…" else "确认清空",
                    enabled = !clearing,
                    onClick = {
                        if (!clearing) {
                            clearing = true
                            scope.launch {
                                try {
                                    when (pendingClear) {
                                        ClearTarget.HISTORY -> deps.history.clear()
                                        ClearTarget.DOWNLOADS -> deps.downloadManager.clearAll()
                                    }
                                    snackbarHostState.showSnackbar(pendingClear.doneMessage)
                                } finally {
                                    clearing = false
                                    clearTarget = null
                                }
                            }
                        }
                    },
                ),
                dismissAction = CouiDialogAction(text = "取消", onClick = { if (!clearing) clearTarget = null }),
            )
        }

        if (showDisclaimer) {
            Dialog(
                onDismissRequest = { showDisclaimer = false },
                title = "免责声明",
                message = "本应用为第三方客户端，与禁漫官方无关，仅供学习交流；" +
                    "全部内容来自第三方站点，本应用不存储任何内容。包含成人内容，" +
                    "未满 18 周岁（或当地法定年龄）请勿使用；因使用本应用产生的一切后果由使用者自行承担。",
                confirmAction = CouiDialogAction(text = "知道了", onClick = { showDisclaimer = false }),
            )
        }
    }
}

/** 顶栏返回键：40dp 圆形容器，热区与图标都不依赖顶栏默认的按钮样式。 */
@Composable
private fun SettingsBackButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = CouiIcons.Back,
            contentDescription = "返回",
            modifier = Modifier.size(22.dp),
            tint = CouiKitTheme.colorScheme.onSurface,
        )
    }
}

/** 行标题统一走 PreferenceTitle 字阶，避免每行各写一份字号。 */
@Composable
private fun SettingsRowTitle(text: String, modifier: Modifier = Modifier) {
    Text(text = text, kind = CouiTextKind.PreferenceTitle, modifier = modifier)
}

/** 行说明用次要色：与标题拉开层级，同时跟随皮肤深浅。 */
@Composable
private fun SettingsRowSummary(text: String) {
    Text(
        text = text,
        kind = CouiTextKind.PreferenceSummary,
        color = CouiKitTheme.colorScheme.onSurfaceVariant,
    )
}

/** 行尾当前值：只显示不点击，点击语义由整行承担。 */
@Composable
private fun SettingsRowValue(text: String) {
    Text(
        text = text,
        kind = CouiTextKind.PreferenceValue,
        color = CouiKitTheme.colorScheme.onSurfaceVariant,
    )
}

/** 诊断行：标签在上、值在下，长值自动换行。 */
@Composable
private fun SettingsDiagnosticLine(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 5.dp)) {
        Text(
            text = label,
            kind = CouiTextKind.PreferenceSummary,
            color = CouiKitTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            kind = CouiTextKind.Body,
            modifier = Modifier.padding(top = 1.dp),
        )
    }
}

/**
 * 枚举型设置的选择面板：Row + 选中勾，确认与取消都不改设置。
 *
 * 选项直接点即生效并关闭，与 COUI 原厂列表选择的手感一致；
 * 「完成」与「取消」都只负责关闭，避免用户以为还要再确认一次。
 */
@Composable
private fun SettingsChoiceDialog(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    summaries: List<String> = emptyList(),
) {
    val scheme = CouiKitTheme.colorScheme
    Dialog(
        onDismissRequest = onDismiss,
        title = title,
        confirmAction = CouiDialogAction(text = "完成", onClick = onDismiss),
        dismissAction = CouiDialogAction(text = "取消", onClick = onDismiss),
    ) {
        Group {
            options.forEachIndexed { index, label ->
                if (index > 0) Divider()
                val summary = summaries.getOrNull(index)?.takeIf { it.isNotBlank() }
                Item(
                    title = { SettingsRowTitle(label) },
                    summary = if (summary == null) {
                        null
                    } else {
                        { SettingsRowSummary(summary) }
                    },
                    trailing = {
                        if (index == selectedIndex) {
                            Icon(
                                imageVector = CouiIcons.Check,
                                contentDescription = "已选",
                                modifier = Modifier.size(20.dp),
                                tint = scheme.accent,
                            )
                        }
                    },
                    onClick = {
                        onSelect(index)
                        onDismiss()
                    },
                )
            }
        }
    }
}

/** 数据管理里的破坏性清空目标：文案与执行动作绑在同一处，避免魔法串分叉。 */
private enum class ClearTarget(val title: String, val description: String, val doneMessage: String) {
    HISTORY("清空阅读历史", "将删除全部阅读记录，此操作不可撤销。", "已清空阅读历史"),
    DOWNLOADS("清空下载", "将删除全部下载任务并清除已下载文件，此操作不可撤销。", "已清空下载"),
}
