// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.page

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.CouiDialogPlacement
import com.itos.coui.dialog.Dialog
import com.itos.coui.effect.SpotlightPreset
import com.itos.coui.effect.couiButtonFeedback
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.layout.Divider
import com.itos.coui.layout.Group
import com.itos.coui.layout.Item
import com.itos.coui.menu.CouiMenuItem
import com.itos.coui.menu.Menu
import com.itos.coui.menu.couiMenuAnchor
import com.itos.coui.menu.rememberCouiMenuAnchor
import com.itos.coui.scroll.couiVerticalScroll
import com.itos.coui.shape.CouiShape
import com.itos.coui.slider.Slider
import com.itos.coui.switch.Switch
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiColor
import com.itos.coui.theme.CouiTheme as CouiKitTheme
import com.itos.coui.topappbar.CouiTopAppBarDefaults
import com.itos.coui.topappbar.LargeTopAppBar
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.ui.GlassSnackbarHost
import com.jm3.app.miuix.ui.ReaderSettingsState
import com.jm3.app.miuix.ui.coui.component.CouiAppScaffold
import com.jm3.app.miuix.ui.coui.component.CouiMinTouchTarget
import com.jm3.app.miuix.ui.coui.component.CouiSegmentedCapsules
import com.jm3.app.miuix.ui.coui.component.CouiTopBarTitle
import com.jm3.app.miuix.ui.coui.component.couiTopAppBarColors
import com.jm3.app.miuix.ui.coui.component.couiDestructiveConfirmAction
import com.jm3.app.miuix.ui.coui.component.couiPressable
import com.jm3.app.miuix.ui.coui.component.couiSpotlightLight
import com.jm3.app.miuix.ui.coui.icon.CouiIcons
import com.jm3.app.miuix.ui.coui.theme.CouiTheme
import com.jm3.app.miuix.ui.planLoad
import com.jm3.app.miuix.ui.prefetchPagesLabel
import com.jm3.app.miuix.ui.readableWidth
import com.jm3.app.miuix.ui.readerBrightnessLabel
import com.jm3.app.miuix.ui.readerBrightnessSliderValue
import com.jm3.app.miuix.ui.rememberReaderSettingsState
import com.jm3.app.miuix.ui.rememberWindowDpSize
import com.jm3.app.miuix.ui.resolvePaginationSelection
import com.jm3.app.miuix.ui.settingsSummary
import com.jm3.app.miuix.ui.toLayoutDpInt
import com.jm3.core.reader.ReaderSpreadPolicy
import com.jm3.core.runCatchingSuspending
import com.jm3.core.session.SessionOutcome
import com.jm3.core.settings.BottomBarForm
import com.jm3.core.settings.BottomBarPreferencePolicy
import com.jm3.core.settings.BottomBarSize
import com.jm3.core.settings.PaginationMode
import com.jm3.core.settings.ReaderImageQuality
import com.jm3.core.settings.ReaderPreferences
import com.jm3.core.settings.ReaderSpreadMode
import com.jm3.core.settings.ThemeMode
import com.jm3.core.settings.UiSkin
import com.jm3.core.settings.WideScreenNavigation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SnackbarHostState

/** 输入框与卡片同源的圆角：由库的卡片半径派生，避免另写字面量。 */
private val SettingsInputShape = CouiShape(radius = CouiPageDefaults.shape.radius / 2)

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
 * COUI 设置页：整页按 COUI-Kit 原厂语言重做，业务语义与存储完全沿用现有实现。
 *
 * 分组用 Group/Item 行、枚举选择用锚点气泡菜单、主题模式用胶囊分段控件；
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
        // 阅读设置七项的状态与写盘交给共享 holder，本页只负责用库控件渲染
        val readerState = rememberReaderSettingsState(repository)
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
                    // 线路与其余设置项同源：一律经 repository（deps.settings 是它的别名）
                    repository.setApiDomains(domainsInput.value.split("\n"))
                    val applied = repository.apiDomains()
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
                val saved = repository.apiDomains()
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
                        title = { CouiTopBarTitle(text = "设置", textAlign = TextAlign.Start) },
                        navigationIcon = { SettingsBackButton(onClick = onBack) },
                        scrollBehavior = scrollBehavior,
                        colors = couiTopAppBarColors(),
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
                        .consumeWindowInsets(innerPadding),
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
                                items = ThemeMode.entries.map { it.displayName },
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
                        CouiChoiceItem(
                            title = "界面风格",
                            summary = uiSkin.couiSummary(),
                            value = uiSkin.label,
                            options = UiSkin.entries.map { it to it.label },
                            current = uiSkin,
                            onSelect = onUiSkinChange,
                        )
                        // ColorOS 固定自有色板，动态取色在那边没有落点：整行不进列表，
                        // 而不是留一个永远禁用的开关解释自己为何无效（HyperOS 分支行为不变）
                        if (dynamicColorSupported) {
                            Divider()
                            Item(
                                // 整行即开关：toggleable 提供点击、Role.Switch 与开/关播报，
                                // Switch 只呈现状态，避免同一控件出现两份点击语义
                                modifier = Modifier.couiSwitchRow(
                                    value = dynamicColor,
                                    onValueChange = onDynamicColorChange,
                                ),
                                title = { SettingsRowTitle("动态取色") },
                                summary = { SettingsRowSummary("跟随壁纸生成配色（Android 12 及以上）") },
                                trailing = {
                                    Switch(
                                        checked = dynamicColor,
                                        onCheckedChange = null,
                                    )
                                },
                            )
                        }
                    }

                    Group(title = "通用") {
                        CouiChoiceItem(
                            title = "分页模式",
                            summary = paginationModeSummary,
                            value = paginationMode.displayName,
                            options = PaginationMode.entries.map { it to it.displayName },
                            current = paginationMode,
                            onSelect = { mode ->
                                // 选择解析要按枚举下标复算，才能沿用「已是最新则不写盘」的既有语义
                                resolvePaginationSelection(
                                    deps.preferences.paginationMode,
                                    PaginationMode.entries.indexOf(mode),
                                )?.takeIf { it.changed }?.let { applyPaginationMode(it.mode) }
                            },
                        )
                        Divider()
                        CouiChoiceItem(
                            title = "底栏尺寸",
                            summary = bottomBarSizeSummary,
                            value = bottomBarSizeOption.label,
                            options = BottomBarSize.entries.map {
                                it to if (it == BottomBarSize.STANDARD) "普通（自适应）" else it.label
                            },
                            current = bottomBarSizeOption,
                            onSelect = ::applyBottomBarSize,
                        )
                        Divider()
                        CouiChoiceItem(
                            title = "底栏形态",
                            summary = bottomBarFormSummary,
                            value = bottomBarForm.label,
                            options = BottomBarForm.entries.map { it to it.label },
                            current = bottomBarForm,
                            onSelect = ::applyBottomBarForm,
                        )
                        if (showWideScreenNavigation) {
                            Divider()
                            CouiChoiceItem(
                                title = "宽屏导航",
                                summary = wideScreenNavigationSummary,
                                value = wideScreenNavigation.label,
                                options = WideScreenNavigation.entries.map { it to it.label },
                                current = wideScreenNavigation,
                                onSelect = ::applyWideScreenNavigation,
                            )
                        }
                    }

                    // 七项阅读器设置与设置中心共用同一份状态与写盘，本页只用 COUI 库控件渲染
                    Group(title = "阅读器") {
                        CouiReaderSettingsSection(
                            state = readerState,
                            spreadAllowed = readerSpreadAllowed,
                        )
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
                            onClick = ::reprobeDomains,
                            enabled = !reprobeBusy,
                        )
                        Divider()
                        // 多行线路输入：库的输入弹窗写死单行，用基础输入框套 COUI 分组卡片
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = CouiPageDefaults.rowPadding,
                                    vertical = CouiPageDefaults.contentPadding,
                                ),
                        ) {
                            SettingsRowTitle("自定义线路")
                            SettingsRowSummary("每行一个域名，留空恢复默认")
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                                    .clip(SettingsInputShape)
                                    .background(scheme.onSurface.copy(alpha = 0.06f))
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                                    .heightIn(min = 84.dp),
                            ) {
                                BasicTextField(
                                    value = domainsInput.value,
                                    onValueChange = { domainsInput.value = it },
                                    textStyle = CouiKitTheme.typography.body.copy(color = scheme.onSurface),
                                    cursorBrush = SolidColor(scheme.accent),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 64.dp)
                                        .semantics { contentDescription = "自定义线路" },
                                    decorationBox = { innerTextField ->
                                        // 占位文案进装饰槽：视觉与读屏都归属同一个输入框
                                        Box {
                                            if (domainsInput.value.isEmpty()) {
                                                Text(
                                                    text = "每行一个域名，例如 api.example.com",
                                                    kind = CouiTextKind.PreferenceSummary,
                                                    color = scheme.onSurfaceVariant,
                                                    textAlign = TextAlign.Start,
                                                )
                                            }
                                            innerTextField()
                                        }
                                    },
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

        // 隐藏诊断：版本号连点 5 次打开，只读汇总运行环境与线路状态
        // 退场窗口由库持有：外层 if 会在关闭瞬间把弹窗抽走，动画来不及播
        Dialog(
            onDismissRequest = { diagnostics = false },
            visible = diagnostics,
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
                    SettingsDiagnosticLine(label = "分页模式", value = paginationMode.displayName)
                    SettingsDiagnosticLine(
                        label = "主题",
                        // 诊断只报真正生效的项：ColorOS 下这个开关即便存着 true 也不影响取色
                        value = themeMode.displayName +
                            if (dynamicColor && dynamicColorSupported) " · 动态取色" else "",
                    )
                }
            }
        }

        // 退场窗口：clearTarget 置空后组合仍需留一份可渲染内容，弹窗才能播完窗口动画
        var lastClearTarget by remember { mutableStateOf<ClearTarget?>(null) }
        SideEffect { if (clearTarget != null) lastClearTarget = clearTarget }
        val pendingClear = clearTarget ?: lastClearTarget
        if (pendingClear != null) {
            Dialog(
                onDismissRequest = { if (!clearing) clearTarget = null },
                visible = clearTarget != null,
                title = pendingClear.title,
                message = pendingClear.description,
                placement = CouiDialogPlacement.Center,
                confirmAction = couiDestructiveConfirmAction(
                    text = if (clearing) "清空中…" else "确认清空",
                    enabled = !clearing,
                ) {
                    if (!clearing) {
                        clearing = true
                        scope.launch {
                            // 下载清理包含数据库写，失败要让用户看见，而不是静默地当作已完成
                            val result = runCatchingSuspending {
                                when (pendingClear) {
                                    ClearTarget.HISTORY -> deps.history.clear()
                                    ClearTarget.DOWNLOADS -> deps.downloadManager.clearAll()
                                }
                            }
                            // showSnackbar 会挂起到提示消失：先收弹窗再提示，
                            // 否则确认框会一直盖在提示条上直到提示自动消失
                            clearing = false
                            clearTarget = null
                            snackbarHostState.showSnackbar(
                                if (result.isSuccess) {
                                    pendingClear.doneMessage
                                } else {
                                    "清空失败：" + (result.exceptionOrNull()?.message ?: "未知错误")
                                },
                            )
                        }
                    }
                },
                dismissAction = CouiDialogAction(text = "取消", onClick = { if (!clearing) clearTarget = null }),
            )
        }

        Dialog(
            onDismissRequest = { showDisclaimer = false },
            visible = showDisclaimer,
            title = "免责声明",
            message = "本应用为第三方客户端，与禁漫官方无关，仅供学习交流；" +
                "全部内容来自第三方站点，本应用不存储任何内容。包含成人内容，" +
                "未满 18 周岁（或当地法定年龄）请勿使用；因使用本应用产生的一切后果由使用者自行承担。",
            confirmAction = CouiDialogAction(text = "知道了", onClick = { showDisclaimer = false }),
        )
    }
}

/** 顶栏返回键：40dp 圆形容器，热区由共享件补到 48dp，视觉尺寸不变。 */
@Composable
private fun SettingsBackButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .couiPressable(shape = CircleShape, minTouchTarget = CouiMinTouchTarget, onClick = onClick),
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

/**
 * 整行开关：库 Switch 传 null 回调后只呈现状态、不再自带 toggleable，
 * 点击、开关角色与开/关播报由这里统一承担；反馈层与 toggleable 共用同一个
 * interactionSource（库的用法约定），按压蒙层与普通设置行一致。
 *
 * 凝光补在蒙层之后：[Item] 的行底色由外层 [Group] 铺，行本体不画底色，
 * 因此这里叠光不会被行底色盖住；关闭的开关不接光（[interactive] 跟随 enabled）。
 */
@Composable
private fun Modifier.couiSwitchRow(
    value: Boolean,
    onValueChange: (Boolean) -> Unit,
    enabled: Boolean = true,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return this
        .couiButtonFeedback(
            dark = CouiKitTheme.isDark,
            interactionSource = interactionSource,
            enabled = enabled,
        )
        .couiSpotlightLight(SpotlightPreset.OpaqueSmall2, interactive = enabled)
        .toggleable(
            value = value,
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onValueChange,
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
 * 阅读设置七项在 COUI 皮肤下的渲染：状态与写盘由 [ReaderSettingsState] 承载，
 * 这里只用库控件（Item/Switch/Slider/锚点菜单），不引 Miuix 偏好件。
 */
@Composable
private fun CouiReaderSettingsSection(
    state: ReaderSettingsState,
    spreadAllowed: Boolean,
) {
    val scope = rememberCoroutineScope()

    CouiChoiceItem(
        title = "默认阅读方向",
        summary = if (state.horizontal) "新打开的章节按左右翻页" else "新打开的章节纵向连续滚动",
        value = if (state.horizontal) "左右翻页" else "纵向长图",
        options = listOf(false to "纵向长图", true to "左右翻页"),
        current = state.horizontal,
        onSelect = { horizontal -> scope.launch { state.applyHorizontal(horizontal) } },
    )
    Divider()
    // 双页是大屏横屏的附加排布：窗口不够宽或竖屏时整行不渲染
    if (spreadAllowed) {
        CouiChoiceItem(
            title = "双页阅读",
            summary = if (state.spread == ReaderSpreadMode.DOUBLE) {
                "大屏横屏下两页并排，按左右翻页生效"
            } else {
                "单页显示，可按左右翻页"
            },
            value = state.spread.label,
            options = ReaderSpreadMode.entries.map { it to it.label },
            current = state.spread,
            onSelect = { mode -> scope.launch { state.applySpread(mode) } },
        )
        Divider()
    }
    Item(
        // 整行即开关：开/关状态由 toggleable 播报，Switch 只呈现视觉
        modifier = Modifier.couiSwitchRow(
            value = state.volumeKeyPaging,
            onValueChange = { checked -> scope.launch { state.applyVolumeKeyPaging(checked) } },
        ),
        title = { SettingsRowTitle("音量键翻页") },
        summary = { SettingsRowSummary("关闭后音量键恢复调节系统音量") },
        trailing = { Switch(checked = state.volumeKeyPaging, onCheckedChange = null) },
    )
    Divider()
    Item(
        modifier = Modifier.couiSwitchRow(
            value = state.keepScreenOn,
            onValueChange = { checked -> scope.launch { state.applyKeepScreenOn(checked) } },
        ),
        title = { SettingsRowTitle("阅读时保持亮屏") },
        summary = { SettingsRowSummary("阅读页不自动息屏") },
        trailing = { Switch(checked = state.keepScreenOn, onCheckedChange = null) },
    )
    Divider()
    // 亮度拖动期间只改本地值，松手才落盘；0 表示跟随系统
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = CouiPageDefaults.rowPadding,
                vertical = CouiPageDefaults.contentPadding,
            ),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SettingsRowTitle("阅读亮度", modifier = Modifier.weight(1f))
            SettingsRowValue(readerBrightnessLabel(state.brightness))
        }
        SettingsRowSummary("0 表示跟随系统亮度，只在阅读页生效")
        Slider(
            value = readerBrightnessSliderValue(state.brightness),
            onValueChange = state::previewBrightness,
            onValueChangeFinished = { scope.launch { state.commitBrightness() } },
            valueRange = 0f..ReaderPreferences.MAX_BRIGHTNESS.toFloat(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        )
    }
    Divider()
    CouiChoiceItem(
        title = "图片质量",
        summary = state.imageQuality.settingsSummary(),
        value = state.imageQuality.label,
        options = ReaderImageQuality.entries.map { it to it.label },
        current = state.imageQuality,
        onSelect = { quality -> scope.launch { state.applyImageQuality(quality) } },
    )
    Divider()
    CouiChoiceItem(
        title = "预取页数",
        summary = if (state.prefetchPages == 0) {
            "不提前加载后续页"
        } else {
            "提前加载当前页之后的 " + state.prefetchPages + " 页"
        },
        value = prefetchPagesLabel(state.prefetchPages),
        options = ReaderPreferences.PREFETCH_OPTIONS.map { it to prefetchPagesLabel(it) },
        current = state.prefetchPages,
        onSelect = { pages -> scope.launch { state.applyPrefetchPages(pages) } },
    )

}

/**
 * 枚举型设置行：整行既是入口也是锚点，点击后贴着该行弹出 COUI 菜单。
 *
 * 每行各持一个 [com.itos.coui.menu.CouiMenuAnchor]，触点坐标随滚动与布局变化重算，
 * 菜单因此总能贴住被点的那一行；选项点选即生效并收起，选中项以勾呈现。
 */
@Composable
private fun <T> CouiChoiceItem(
    title: String,
    summary: String,
    value: String,
    options: List<Pair<T, String>>,
    current: T,
    onSelect: (T) -> Unit,
) {
    val anchor = rememberCouiMenuAnchor()
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .couiMenuAnchor(anchor),
    ) {
        Item(
            title = { SettingsRowTitle(title) },
            summary = { SettingsRowSummary(summary) },
            trailing = { SettingsRowValue(value) },
            onClick = { anchor.open { expanded = true } },
        )
        Menu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            anchor = anchor,
            headerText = title,
            items = options.map { (option, label) ->
                CouiMenuItem(value = option, text = label, selected = option == current)
            },
            onItemClick = { item ->
                expanded = false
                // 与原有弹窗一致：选中项重复点不重复写盘
                if (item.value != current) onSelect(item.value)
            },
        )
    }
}

/** 数据管理里的破坏性清空目标：文案与执行动作绑在同一处，避免魔法串分叉。 */
private enum class ClearTarget(val title: String, val description: String, val doneMessage: String) {
    HISTORY("清空阅读历史", "将删除全部阅读记录，此操作不可撤销。", "已清空阅读历史"),
    DOWNLOADS("清空下载", "将删除全部下载任务并清除已下载文件，此操作不可撤销。", "已清空下载"),
}
