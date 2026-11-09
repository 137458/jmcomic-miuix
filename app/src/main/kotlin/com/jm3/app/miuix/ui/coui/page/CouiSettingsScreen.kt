// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.page

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.ui.BackButton
import com.jm3.app.miuix.ui.GlassSnackbarHost
import com.jm3.app.miuix.ui.ReaderSettingsSection
import com.jm3.app.miuix.ui.coui.component.CouiBottomSheet
import com.jm3.app.miuix.ui.coui.component.CouiButton
import com.jm3.app.miuix.ui.coui.component.CouiCard
import com.jm3.app.miuix.ui.coui.component.CouiDialogOverlay
import com.jm3.app.miuix.ui.coui.component.CouiSectionTitle
import com.jm3.app.miuix.ui.coui.component.CouiSegmented
import com.jm3.app.miuix.ui.coui.component.CouiSurfaceBackground
import com.jm3.app.miuix.ui.coui.component.CouiTextButton
import com.jm3.app.miuix.ui.coui.component.CouiTopBar
import com.jm3.app.miuix.ui.coui.component.couiBackdropSource
import com.jm3.app.miuix.ui.coui.component.couiClickable
import com.jm3.app.miuix.ui.coui.component.couiSelectable
import com.jm3.app.miuix.ui.coui.component.couiTopBarHeight
import com.jm3.app.miuix.ui.coui.component.rememberCouiBlurBackdrop
import com.jm3.app.miuix.ui.coui.theme.CouiTheme
import com.jm3.app.miuix.ui.coui.theme.CouiType
import com.jm3.app.miuix.ui.coui.theme.LocalCouiColors
import com.jm3.app.miuix.ui.coui.theme.LocalCouiShapes
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
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.squircle.squircleClip

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

/**
 * COUI 设置页：外壳、分组、顶栏按 COUI 重做，设置项语义与存储完全沿用现有实现。
 *
 * 主题模式走 COUI 分段控件，界面风格走 COUI 底部面板；其余开关/滑杆/枚举项仍复用 Miuix
 * preference，读写一律经 [ScreenDeps.preferences] 与既有回调，不新增键、不改 null 语义。
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
        val colors = LocalCouiColors.current
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val backdrop = rememberCouiBlurBackdrop()
        // 网关与结局翻译统一从 ScreenDeps 取，调用方不再并行传一份
        val gateway = deps.gateway
        val authCall = deps.authCall

        val domainsInput = rememberSaveable { mutableStateOf("") }
        var loadedSaved by rememberSaveable { mutableStateOf(false) }
        val activeDomains = rememberSaveable { mutableStateOf("") }
        val applying = remember { mutableStateOf(false) }

        // 线路诊断：当前实际域名与各域名探活延迟（毫秒升序）。
        var usedDomain by remember { mutableStateOf<String?>(null) }
        var domainLatency by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
        var reprobeBusy by remember { mutableStateOf(false) }
        var diagError by remember { mutableStateOf<String?>(null) }

        // 破坏性操作二次确认：3 秒未确认自动复位；清空执行中不自动关，等任务结束。
        var clearTarget by rememberSaveable { mutableStateOf<ClearTarget?>(null) }
        var clearing by remember { mutableStateOf(false) }
        var showDisclaimer by rememberSaveable { mutableStateOf(false) }
        var showSkinPicker by rememberSaveable { mutableStateOf(false) }

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
            diagError = null
            // 两个网关查询合成一次结局：等待会话恢复、切 IO、判失效都在 AuthCall 内完成。
            val result = authCall.call {
                gateway.usedDomain() to
                    gateway.domainLatency().entries
                        .sortedBy { it.value }
                        .map { it.key to it.value }
            }
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
                        if (applied.isEmpty()) "已恢复默认线路" else "已应用 ${applied.size} 个线路域名",
                    )
                }.onFailure { error ->
                    snackbarHostState.showSnackbar("线路应用失败：${error.message ?: "未知错误"}")
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
                delay(3000)
                clearTarget = null
            }
        }
        val scrollState = rememberScrollState()
        val scrolled by remember(scrollState) {
            derivedStateOf { scrollState.value > 8 }
        }

        Box(modifier = Modifier.fillMaxSize().couiBackdropSource(backdrop)) {
            CouiSurfaceBackground(modifier = Modifier.fillMaxSize())

            // 大屏把设置内容收进可读宽度并居中；顶栏是浮层，内容从顶栏下沿起排
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .readableWidth()
                    .verticalScroll(scrollState),
            ) {
                Spacer(modifier = Modifier.height(couiTopBarHeight()))

                CouiSettingsGroup(title = "主题") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        BasicText(
                            text = "外观模式",
                            style = CouiType.bodyEmphasis.copy(color = colors.textPrimary),
                        )
                        BasicText(
                            text = when (themeMode) {
                                ThemeMode.SYSTEM -> "跟随系统自动切换深浅色"
                                ThemeMode.LIGHT -> "始终保持浅色高亮视觉"
                                ThemeMode.DARK -> "始终保持深色暗调视觉"
                            },
                            style = CouiType.caption.copy(color = colors.textSecondary),
                            modifier = Modifier.padding(top = 2.dp),
                        )
                        CouiSegmented(
                            options = ThemeMode.entries.map { it.couiLabel() },
                            selectedIndex = ThemeMode.entries.indexOf(themeMode),
                            onSelect = { index ->
                                ThemeMode.entries.getOrNull(index)
                                    ?.takeIf { it != themeMode }
                                    ?.let(onThemeModeChange)
                            },
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                    CouiSettingsRow(
                        title = "界面风格",
                        summary = uiSkin.couiSummary(),
                        value = uiSkin.label,
                        onClick = { showSkinPicker = true },
                    )
                    SwitchPreference(
                        title = "动态取色",
                        summary = if (dynamicColorSupported) {
                            "跟随壁纸生成配色（Android 12 及以上）"
                        } else {
                            "ColorOS 皮肤固定自有色板，动态取色不生效"
                        },
                        checked = dynamicColor,
                        onCheckedChange = onDynamicColorChange,
                        enabled = dynamicColorSupported,
                    )
                }

                CouiSettingsGroup(title = "通用") {
                    OverlaySpinnerPreference(
                        title = "分页模式",
                        summary = when (paginationMode) {
                            PaginationMode.INFINITE -> "滚动到底自动加载下一页"
                            PaginationMode.CLICK -> "列表底部显示上一页/下一页"
                        },
                        items = PaginationMode.entries.mapIndexed { index, mode ->
                            DropdownItem(text = mode.couiLabel(), selected = mode == paginationMode)
                        },
                        selectedIndex = PaginationMode.entries.indexOf(paginationMode),
                        showValue = false,
                        onSelectedIndexChange = { index ->
                            resolvePaginationSelection(deps.preferences.paginationMode, index)
                                ?.takeIf { it.changed }
                                ?.let { applyPaginationMode(it.mode) }
                        },
                    )
                    OverlaySpinnerPreference(
                        title = "底栏尺寸",
                        summary = if (bottomBarSizeOption == BottomBarSize.GENEROUS) {
                            "偏大：所有设备都使用更高的底栏，图标与标签同步放大"
                        } else {
                            "普通：按设备自适应，手机用标准高度、平板用偏大高度" +
                                "（当前设备按${if (bottomBarSize == BottomBarSize.GENEROUS) "偏大" else "标准"}显示）"
                        },
                        items = BottomBarSize.entries.mapIndexed { index, size ->
                            DropdownItem(
                                text = if (size == BottomBarSize.STANDARD) "普通（自适应）" else size.label,
                                selected = size == bottomBarSizeOption,
                            )
                        },
                        selectedIndex = BottomBarSize.entries.indexOf(bottomBarSizeOption),
                        showValue = false,
                        onSelectedIndexChange = { index ->
                            BottomBarSize.entries.getOrNull(index)?.let { applyBottomBarSize(it) }
                        },
                    )
                    OverlaySpinnerPreference(
                        title = "底栏形态",
                        summary = when (bottomBarForm) {
                            BottomBarForm.FLOATING -> "圆角胶囊，与屏幕底边留出悬浮间隙"
                            BottomBarForm.DOCKED -> "贴底通栏，紧接导航栏上方并撑满宽度"
                        },
                        items = BottomBarForm.entries.mapIndexed { index, form ->
                            DropdownItem(text = form.label, selected = form == bottomBarForm)
                        },
                        selectedIndex = BottomBarForm.entries.indexOf(bottomBarForm),
                        showValue = false,
                        onSelectedIndexChange = { index ->
                            BottomBarForm.entries.getOrNull(index)?.let { applyBottomBarForm(it) }
                        },
                    )
                    if (showWideScreenNavigation) {
                        OverlaySpinnerPreference(
                            title = "宽屏导航",
                            summary = when (wideScreenNavigation) {
                                WideScreenNavigation.BOTTOM_BAR -> "宽屏同样显示底栏，与手机保持同一套样式"
                                WideScreenNavigation.RAIL -> "宽屏改用可收起的侧栏，列表获得更宽横向空间"
                            },
                            items = WideScreenNavigation.entries.mapIndexed { index, navigation ->
                                DropdownItem(text = navigation.label, selected = navigation == wideScreenNavigation)
                            },
                            selectedIndex = WideScreenNavigation.entries.indexOf(wideScreenNavigation),
                            showValue = false,
                            onSelectedIndexChange = { index ->
                                WideScreenNavigation.entries.getOrNull(index)?.let { applyWideScreenNavigation(it) }
                            },
                        )
                    }
                }

                CouiSettingsGroup(title = "阅读器") {
                    ReaderSettingsSection(
                        repository = deps.preferences.repository,
                        spreadAllowed = readerSpreadAllowed,
                    )
                }

                CouiSettingsGroup(title = "线路") {
                    CouiSettingsRow(
                        title = "当前线路",
                        summary = activeDomains.value.ifBlank { "默认（自动解析）" },
                    )
                    CouiSettingsRow(
                        title = "实际线路",
                        summary = usedDomain ?: "未解析",
                    )
                    if (domainLatency.isEmpty()) {
                        CouiSettingsRow(title = "线路延迟", summary = "尚未完成线路探活")
                    } else {
                        domainLatency.forEach { (domain, latencyMs) ->
                            CouiSettingsRow(title = domain, summary = "${latencyMs} ms")
                        }
                    }
                    diagError?.let { message ->
                        BasicText(
                            text = "线路诊断失败：$message",
                            style = CouiType.caption.copy(color = colors.error),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                        )
                    }
                    CouiSettingsRow(
                        title = "重新探活",
                        summary = if (reprobeBusy) "探活中…" else "逐条测速并按延迟排序",
                        enabled = !reprobeBusy,
                        onClick = ::reprobeDomains,
                        trailing = {
                            if (reprobeBusy) {
                                CircularProgressIndicator(size = 20.dp)
                            }
                        },
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                    ) {
                        BasicText(
                            text = "自定义线路",
                            style = CouiType.bodyEmphasis.copy(color = colors.textPrimary),
                        )
                        BasicText(
                            text = "每行一个域名，留空恢复默认",
                            style = CouiType.caption.copy(color = colors.textSecondary),
                            modifier = Modifier.padding(top = 2.dp),
                        )
                        TextField(
                            value = domainsInput.value,
                            onValueChange = { domainsInput.value = it },
                            label = "自定义线路域名",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 4.dp),
                        )
                    }
                    CouiSettingsRow(
                        title = "应用线路",
                        summary = if (applying.value) "应用中…" else "保存配置并重建 API 网关",
                        enabled = !applying.value,
                        onClick = ::applyCustomDomains,
                    )
                }

                CouiSettingsGroup(title = "数据管理") {
                    ArrowPreference(
                        title = "清空阅读历史",
                        summary = "移除全部阅读记录",
                        onClick = { clearTarget = ClearTarget.HISTORY },
                    )
                    ArrowPreference(
                        title = "清空下载",
                        summary = "清除下载任务与已下载文件",
                        onClick = { clearTarget = ClearTarget.DOWNLOADS },
                    )
                }

                CouiSettingsGroup(title = "关于") {
                    // 版本行兼作隐藏诊断入口：2 秒内连点 5 次打开诊断面板
                    CouiSettingsRow(
                        title = "版本",
                        summary = versionName,
                        onClick = {
                            versionTapCount += 1
                            if (versionTapCount >= DIAGNOSTIC_TAPS) {
                                versionTapCount = 0
                                diagnostics = true
                            }
                        },
                    )
                    ArrowPreference(
                        title = "检查更新",
                        summary = "查看远端最新版本，支持应用内下载与安装",
                        onClick = onOpenUpdate,
                    )
                    CouiSettingsRow(
                        title = "开源组件",
                        summary = "jmcomic-api (MIT) · Miuix (Apache-2.0) · Coil (Apache-2.0) · Jetpack Compose (Apache-2.0)",
                    )
                    ArrowPreference(
                        title = "免责声明",
                        summary = "第三方客户端，仅供学习交流，包含成人内容",
                        onClick = { showDisclaimer = true },
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            CouiTopBar(
                title = "设置",
                scrolled = scrolled,
                modifier = Modifier.align(Alignment.TopCenter),
                backdrop = backdrop,
                navigation = { BackButton(onBack = onBack) },
            )

            GlassSnackbarHost(
                state = snackbarHostState,
                backdrop = backdrop,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
            )

            // 界面风格：沿用原数据流与存储键，只在选中不同项时回调一次。
            CouiBottomSheet(
                visible = showSkinPicker,
                onDismiss = { showSkinPicker = false },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) {
                    BasicText(
                        text = "界面风格",
                        style = CouiType.section.copy(color = colors.textPrimary),
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    UiSkin.entries.forEach { skin ->
                        CouiSkinOption(
                            skin = skin,
                            selected = skin == uiSkin,
                            onClick = {
                                if (skin != uiSkin) onUiSkinChange(skin)
                                showSkinPicker = false
                            },
                        )
                    }
                    CouiTextButton(
                        text = "取消",
                        onClick = { showSkinPicker = false },
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = 8.dp),
                    )
                }
            }

            // 隐藏诊断：版本号连点 5 次打开，只读汇总运行环境与线路状态
            CouiBottomSheet(
                visible = diagnostics,
                onDismiss = { diagnostics = false },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) {
                    BasicText(
                        text = "诊断信息",
                        style = CouiType.section.copy(color = colors.textPrimary),
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                    CouiCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(16.dp),
                    ) {
                        CouiDiagnosticRow(label = "包名", value = context.packageName)
                        CouiDiagnosticRow(label = "版本", value = "$versionName ($versionCode)")
                        CouiDiagnosticRow(label = "设备", value = "${Build.MANUFACTURER} ${Build.MODEL}")
                        CouiDiagnosticRow(
                            label = "系统",
                            value = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                        )
                        CouiDiagnosticRow(label = "当前线路", value = usedDomain ?: "未解析")
                        CouiDiagnosticRow(
                            label = "自定义线路",
                            value = activeDomains.value.ifBlank { "默认（自动解析）" },
                        )
                        CouiDiagnosticRow(
                            label = "线路延迟",
                            value = domainLatency.joinToString("\n") { (domain, ms) -> "$domain · ${ms}ms" }
                                .ifBlank { "尚未完成线路探活" },
                        )
                        CouiDiagnosticRow(label = "分页模式", value = paginationMode.couiLabel())
                        CouiDiagnosticRow(
                            label = "主题",
                            value = themeMode.couiLabel() + if (dynamicColor) " · 动态取色" else "",
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            val pendingClear = clearTarget
            CouiDialogOverlay(
                visible = pendingClear != null,
                onDismiss = { if (!clearing) clearTarget = null },
            ) {
                if (pendingClear != null) {
                    BasicText(
                        text = pendingClear.title,
                        style = CouiType.title.copy(color = colors.textPrimary),
                    )
                    BasicText(
                        text = pendingClear.description,
                        style = CouiType.body.copy(color = colors.textSecondary),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            CouiTextButton(
                                text = "取消",
                                onClick = { if (!clearing) clearTarget = null },
                            )
                        }
                        CouiButton(
                            text = if (clearing) "清空中…" else "确认清空",
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
                            modifier = Modifier.weight(1f),
                            enabled = !clearing,
                        )
                    }
                }
            }

            CouiDialogOverlay(
                visible = showDisclaimer,
                onDismiss = { showDisclaimer = false },
            ) {
                BasicText(
                    text = "免责声明",
                    style = CouiType.title.copy(color = colors.textPrimary),
                )
                BasicText(
                    text = "本应用为第三方客户端，与禁漫官方无关，仅供学习交流；" +
                        "全部内容来自第三方站点，本应用不存储任何内容。包含成人内容，" +
                        "未满 18 周岁（或当地法定年龄）请勿使用；因使用本应用产生的一切后果由使用者自行承担。",
                    style = CouiType.body.copy(color = colors.textSecondary),
                    modifier = Modifier.padding(top = 8.dp),
                )
                CouiButton(
                    text = "知道了",
                    onClick = { showDisclaimer = false },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp),
                )
            }
        }
    }
}

/** COUI 分组：17sp 分组标题 + squircle 主卡片，卡内统一 16dp 水平内衬。 */
@Composable
private fun CouiSettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    CouiSectionTitle(
        text = title,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    CouiCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        // Miuix preference 依赖 Card 的 insideMargin 提供内衬，这里必须给回 16dp，
        // 否则整组 Miuix 行贴住 squircle 边缘、与 CouiSettingsRow 的文字起点错位。
        contentPadding = PaddingValues(horizontal = 16.dp),
        content = content,
    )
}

/**
 * COUI 设置行：标题 + 辅助说明 + 可选右侧值。
 *
 * Miuix preference 无法表达的行（只读诊断、版本连点、线路操作）用这一套；
 * 水平内衬由外层 CouiCard 提供，这里只补纵向间距。
 */
@Composable
private fun CouiSettingsRow(
    title: String,
    summary: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = LocalCouiColors.current
    val titleColor = if (enabled) colors.textPrimary else colors.textTertiary
    val summaryColor = if (enabled) colors.textSecondary else colors.textTertiary
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null && enabled) {
                    Modifier.couiClickable(onClick)
                } else {
                    Modifier
                },
            )
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            BasicText(
                text = title,
                style = CouiType.bodyEmphasis.copy(color = titleColor),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (summary.isNotEmpty()) {
                BasicText(
                    text = summary,
                    style = CouiType.caption.copy(color = summaryColor),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        value?.let {
            BasicText(
                text = it,
                style = CouiType.caption.copy(color = colors.textTertiary),
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        trailing?.invoke()
    }
}

/** 面板内单选项：选中态用绿色容器，不铺整块绿色。 */
@Composable
private fun CouiSkinOption(
    skin: UiSkin,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalCouiColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .squircleClip(LocalCouiShapes.current.control)
            .background(if (selected) colors.accentContainer else Color.Transparent)
            .couiSelectable(selected = selected, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            BasicText(
                text = skin.label,
                style = CouiType.bodyEmphasis.copy(color = if (selected) colors.accent else colors.textPrimary),
            )
            BasicText(
                text = skin.couiSummary(),
                style = CouiType.caption.copy(color = colors.textSecondary),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (selected) {
            BasicText(
                text = "已选",
                style = CouiType.caption.copy(color = colors.accent),
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

/** 诊断面板单行：标签在上、值在下，长值自动换行。 */
@Composable
private fun CouiDiagnosticRow(label: String, value: String) {
    val colors = LocalCouiColors.current
    Column(modifier = Modifier.padding(vertical = 5.dp)) {
        BasicText(
            text = label,
            style = CouiType.caption.copy(color = colors.textTertiary),
        )
        BasicText(
            text = value,
            style = CouiType.body.copy(color = colors.textPrimary),
            modifier = Modifier.padding(top = 1.dp),
        )
    }
}

/** 数据管理里的破坏性清空目标：文案与执行动作绑在同一处，避免魔法串分叉。 */
private enum class ClearTarget(val title: String, val description: String, val doneMessage: String) {
    HISTORY("清空阅读历史", "将删除全部阅读记录，此操作不可撤销。", "已清空阅读历史"),
    DOWNLOADS("清空下载", "将删除全部下载任务并清除已下载文件，此操作不可撤销。", "已清空下载"),
}

