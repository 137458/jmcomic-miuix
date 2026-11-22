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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.CouiDialogActionStyle
import com.itos.coui.dialog.CouiDialogDefaults
import com.itos.coui.dialog.CouiDialogPlacement
import com.itos.coui.dialog.Dialog
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
import com.jm3.app.miuix.ui.couiPageExtraBottomDp
import com.jm3.app.miuix.ui.coui.component.CouiBodyText
import com.jm3.app.miuix.ui.coui.component.CouiAppScaffold
import com.jm3.app.miuix.ui.coui.component.CouiSegmentedCapsules
import com.jm3.app.miuix.ui.coui.component.CouiTopBarBackButton
import com.jm3.app.miuix.ui.coui.component.CouiTopBarTitle
import com.jm3.app.miuix.ui.coui.component.couiTopAppBarColors
import com.jm3.app.miuix.ui.coui.component.couiDestructiveConfirmAction
import com.jm3.app.miuix.ui.coui.component.couiDialogMaterial
import com.jm3.app.miuix.ui.coui.component.couiSwitchRow
import com.jm3.app.miuix.ui.coui.component.rememberCouiImeOccupiesPage
import com.jm3.app.miuix.ui.coui.icon.CouiIcons
import com.jm3.app.miuix.ui.coui.theme.CouiTheme
import top.yukonga.miuix.kmp.basic.Icon
import com.jm3.app.miuix.ui.prefetchPagesLabel
import com.jm3.app.miuix.ui.readableWidth
import com.jm3.app.miuix.ui.readerBrightnessLabel
import com.jm3.app.miuix.ui.readerBrightnessSliderValue
import com.jm3.app.miuix.ui.route.AppRoutes
import com.jm3.app.miuix.ui.settings.SETTINGS_CLEAR_CONFIRM_TIMEOUT_MS
import com.jm3.app.miuix.ui.settings.SETTINGS_DISCLAIMER_TEXT
import com.jm3.app.miuix.ui.settings.SETTINGS_OPEN_SOURCE_SUMMARY
import com.jm3.app.miuix.ui.settings.SettingsClearTarget
import com.jm3.app.miuix.ui.settings.SettingsOptions
import com.jm3.app.miuix.ui.settings.SettingsSections
import com.jm3.app.miuix.ui.settings.bottomBarFormSummary
import com.jm3.app.miuix.ui.settings.paginationModeSummary
import com.jm3.app.miuix.ui.settings.rememberSettingsLayout
import com.jm3.app.miuix.ui.settings.rememberSettingsScaffoldState
import com.jm3.app.miuix.ui.settings.themeModeSummary
import com.jm3.app.miuix.ui.settings.uiSkinSummary
import com.jm3.app.miuix.ui.settings.wideScreenNavigationSummary
import com.jm3.app.miuix.ui.settingsSummary
import com.jm3.core.settings.ReaderImageQuality
import com.jm3.core.settings.ReaderPreferences
import com.jm3.core.settings.ReaderSpreadMode
import com.jm3.core.settings.ThemeMode
import com.jm3.core.settings.UiSkin
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.SnackbarHostState

/** 输入框与卡片同源的圆角：由库的卡片半径派生，避免另写字面量。 */
private val SettingsInputShape = CouiShape(radius = CouiPageDefaults.shape.radius / 2)

/**
 * COUI 设置页：整页按 COUI-Kit 原厂语言重做，业务语义与存储完全沿用现有实现。
 *
 * 分组用 Group/Item 行、枚举选择用锚点气泡菜单、主题模式用胶囊分段控件；
 * 状态编排、线路诊断四态、提交入口与全部说明文案来自共享骨架
 * [rememberSettingsScaffoldState]，本页只保留 COUI 的控件与弹窗外观。
 * 本页是独立路由，不在主框架内，所以自己持有一套 CouiTheme 与 Scaffold。
 */
@Composable
internal fun CouiSettingsScreen(
    deps: ScreenDeps,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    dynamicColor: Boolean,
    onDynamicColorChange: (Boolean) -> Unit,
    dynamicColorSupported: Boolean,
    uiSkin: UiSkin,
    onUiSkinChange: (UiSkin) -> Unit,
    onBack: () -> Unit,
    nav: AppRoutes,
    snackbarHostState: SnackbarHostState,
) {
    CouiTheme {
        val scheme = CouiKitTheme.colorScheme
        // 骨架持有状态机与提交动作；COUI 侧失败提示沿用既有「回显异常原文」的写法
        val scaffold = rememberSettingsScaffoldState(
            deps = deps,
            nav = nav,
            snackbarHostState = snackbarHostState,
            onBack = onBack,
            failureDetail = { error -> error?.message ?: "未知错误" },
            // 颜色皮肤保持「5 秒未确认自动复位」的既有行为
            clearConfirmTimeoutMs = SETTINGS_CLEAR_CONFIRM_TIMEOUT_MS,
        )
        // 分页/底栏形态/宽屏导航/阅读器双页的展示值由骨架按同一套窗口断点推导
        val layout = rememberSettingsLayout(deps)
        val diagnostics = scaffold.diagnostics.value

        val scrollState = rememberScrollState()
        // 折叠顶栏：正文自己消费滚动，顶栏按同一套回弹手势收起/展开
        val scrollBehavior = CouiTopAppBarDefaults.exitUntilCollapsedScrollBehavior()

        Box(modifier = Modifier.fillMaxSize()) {
            CouiAppScaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = {
                    // 顶栏必须写在 Scaffold 的 topBar 槽位：库在那里提供 backdrop，
                    // 自己提到外面会拿不到采样源，直接抛 checkNotNull。
                    LargeTopAppBar(
                        title = { CouiTopBarTitle(text = SettingsSections.title, textAlign = TextAlign.Start) },
                        navigationIcon = { CouiTopBarBackButton(onClick = scaffold.onBack) },
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
                        // 越界回弹走库默认实现，与其余 COUI 页面一致：库把甩动触边的回弹映射成
                        // 最高 0.3731 屏高的视口位移，页面侧没有可调幅度或开关。
                        .couiVerticalScroll(scrollState)
                        // 顶栏的高与折叠位移由 state 参与计算，正文才会跟着顶栏一起让位
                        .padding(scrollBehavior.state.contentPadding(innerPadding, LocalDensity.current))
                        .consumeWindowInsets(innerPadding),
                ) {
                    Group(title = SettingsSections.sectionTheme) {
                        Item(
                            title = { SettingsRowTitle(SettingsSections.appearanceTitle) },
                            summary = { SettingsRowSummary(themeModeSummary(themeMode)) },
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                        ) {
                            CouiSegmentedCapsules(
                                items = SettingsSections.themeMode.labels,
                                selectedIndex = SettingsSections.themeMode.indexOf(themeMode),
                                onSelect = { index ->
                                    SettingsSections.themeMode.valueAt(index)
                                        ?.takeIf { it != themeMode }
                                        ?.let(onThemeModeChange)
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Divider()
                        CouiChoiceItem(
                            title = SettingsSections.uiSkinTitle,
                            summary = uiSkinSummary(uiSkin),
                            value = uiSkin.label,
                            options = SettingsSections.uiSkin,
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
                                title = { SettingsRowTitle(SettingsSections.dynamicColorTitle) },
                                summary = { SettingsRowSummary(SettingsSections.dynamicColorSummary(dynamicColorSupported)) },
                                trailing = {
                                    Switch(
                                        checked = dynamicColor,
                                        onCheckedChange = null,
                                    )
                                },
                            )
                        }
                    }

                    Group(title = SettingsSections.sectionGeneral) {
                        CouiChoiceItem(
                            title = SettingsSections.paginationTitle,
                            summary = paginationModeSummary(layout.paginationMode),
                            value = layout.paginationMode.displayName,
                            options = SettingsSections.paginationMode,
                            current = layout.paginationMode,
                            // 选择按枚举下标交给骨架复算，沿用「已是最新则不写盘」的既有语义
                            onSelect = { mode ->
                                scaffold.applyPaginationIndex(SettingsSections.paginationMode.indexOf(mode))
                            },
                        )
                        Divider()
                        CouiChoiceItem(
                            title = SettingsSections.bottomBarFormTitle,
                            summary = bottomBarFormSummary(layout.bottomBarForm),
                            value = layout.bottomBarForm.label,
                            options = SettingsSections.bottomBarForm,
                            current = layout.bottomBarForm,
                            onSelect = scaffold::applyBottomBarForm,
                        )
                        if (layout.showWideScreenNavigation) {
                            Divider()
                            CouiChoiceItem(
                                title = SettingsSections.wideNavigationTitle,
                                summary = wideScreenNavigationSummary(layout.wideScreenNavigation),
                                value = layout.wideScreenNavigation.label,
                                options = SettingsSections.wideScreenNavigation,
                                current = layout.wideScreenNavigation,
                                onSelect = scaffold::applyWideScreenNavigation,
                            )
                        }
                    }

                    // 七项阅读器设置与设置中心共用同一份状态与写盘，本页只用 COUI 库控件渲染
                    Group(title = "阅读器") {
                        CouiReaderSettingsSection(
                            state = scaffold.readerState,
                            spreadAllowed = layout.readerSpreadAllowed,
                        )
                    }

                    Group(title = SettingsSections.sectionLine) {
                        Item(
                            title = { SettingsRowTitle(SettingsSections.networkLineTitle) },
                            summary = {
                                SettingsRowSummary(
                                    SettingsSections.networkLineSummary(
                                        selectedDomain = scaffold.selectedDomain.value,
                                        usedDomain = diagnostics.usedDomain,
                                    ),
                                )
                            },
                            trailing = {
                                Icon(
                                    imageVector = CouiIcons.Forward,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = scheme.onSurfaceVariant,
                                )
                            },
                            onClick = scaffold::openLinePicker,
                        )
                        Divider()
                        Item(
                            title = { SettingsRowTitle(SettingsSections.customDomainsTitle) },
                            summary = {
                                SettingsRowSummary(SettingsSections.customDomainsSummaryText(scaffold.customDomains))
                            },
                            trailing = {
                                Icon(
                                    imageVector = CouiIcons.Forward,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = scheme.onSurfaceVariant,
                                )
                            },
                            onClick = scaffold::openCustomDomainsEditor,
                        )
                    }

                    Group(title = SettingsSections.sectionData) {
                        Item(
                            title = { SettingsRowTitle(SettingsClearTarget.HISTORY.title) },
                            summary = { SettingsRowSummary(SettingsSections.clearSummary(SettingsClearTarget.HISTORY)) },
                            onClick = { scaffold.requestClear(SettingsClearTarget.HISTORY) },
                        )
                        Divider()
                        Item(
                            title = { SettingsRowTitle(SettingsClearTarget.DOWNLOADS.title) },
                            summary = { SettingsRowSummary(SettingsSections.clearSummary(SettingsClearTarget.DOWNLOADS)) },
                            onClick = { scaffold.requestClear(SettingsClearTarget.DOWNLOADS) },
                        )
                    }

                    Group(title = SettingsSections.sectionAbout) {
                        // 版本行兼作隐藏诊断入口：2 秒内连点 5 次打开诊断面板
                        Item(
                            title = { SettingsRowTitle(SettingsSections.versionTitle) },
                            summary = { SettingsRowSummary(scaffold.versionName) },
                            onClick = scaffold::tapVersion,
                        )
                        Divider()
                        Item(
                            title = { SettingsRowTitle(SettingsSections.updateTitle) },
                            summary = { SettingsRowSummary(SettingsSections.updateSummary) },
                            onClick = { scaffold.openUpdate() },
                        )
                        Divider()
                        Item(
                            title = { SettingsRowTitle(SettingsSections.openSourceTitle) },
                            summary = { SettingsRowSummary(SETTINGS_OPEN_SOURCE_SUMMARY) },
                        )
                        Divider()
                        Item(
                            title = { SettingsRowTitle(SettingsSections.disclaimerTitle) },
                            summary = { SettingsRowSummary(SettingsSections.disclaimerSummary) },
                            onClick = scaffold::showDisclaimer,
                        )
                    }
                    Spacer(
                        modifier = Modifier.height(
                            couiPageExtraBottomDp(
                            hasFab = false,
                            imeOccupiesPage = rememberCouiImeOccupiesPage(),
                        ).dp,
                        ),
                    )
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

        // 网络线路选择弹窗：支持自动选择、手动固定节点与按需测速
        val nodes = scaffold.lineCandidates
        val selectedLine = scaffold.selectedDomain.value
        Dialog(
            onDismissRequest = scaffold::closeLinePicker,
            visible = scaffold.linePickerVisible.value,
            material = couiDialogMaterial(),
            title = SettingsSections.linePickerTitle,
            placement = CouiDialogPlacement.Bottom,
            confirmAction = CouiDialogAction(
                text = SettingsSections.probeLatencyLabel(diagnostics.probing),
                enabled = diagnostics.canRetry && !scaffold.applying.value,
                style = CouiDialogActionStyle.Primary,
                colors = CouiDialogDefaults.primaryActionColors(scheme.accent),
                onClick = scaffold::probeLatency,
            ),
            dismissAction = CouiDialogAction(
                text = SettingsSections.linePickerClose,
                onClick = scaffold::closeLinePicker,
                style = CouiDialogActionStyle.Secondary,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 340.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Item(
                    title = { SettingsRowTitle(SettingsSections.autoLineTitle) },
                    summary = { SettingsRowSummary(SettingsSections.autoLineSummary) },
                    trailing = {
                        if (selectedLine == null) {
                            Icon(
                                imageVector = CouiIcons.Check,
                                contentDescription = "已选中",
                                modifier = Modifier.size(18.dp),
                                tint = scheme.accent,
                            )
                        }
                    },
                    enabled = !scaffold.applying.value,
                    onClick = { scaffold.selectLine(null) },
                )
                Divider()
                if (nodes.isEmpty()) {
                    Item(
                        title = { SettingsRowSummary(SettingsSections.linePickerEmptyText) },
                    )
                } else {
                    nodes.forEachIndexed { index, node ->
                        Item(
                            title = { SettingsRowTitle(node.domain) },
                            summary = {
                                SettingsRowSummary(SettingsSections.nodeSummaryText(node.isCustom, node.latencyMs))
                            },
                            trailing = {
                                if (selectedLine == node.domain) {
                                    Icon(
                                        imageVector = CouiIcons.Check,
                                        contentDescription = "已选中",
                                        modifier = Modifier.size(18.dp),
                                        tint = scheme.accent,
                                    )
                                }
                            },
                            enabled = !scaffold.applying.value,
                            onClick = { scaffold.selectLine(node.domain) },
                        )
                        if (index < nodes.lastIndex) Divider()
                    }
                }
                diagnostics.errorMessage?.let { message ->
                    Divider()
                    Text(
                        text = SettingsSections.diagnosticsFailureText(message),
                        kind = CouiTextKind.ItemSummary,
                        color = if (CouiKitTheme.isDark) CouiColor.Night.RED else CouiColor.Day.RED,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.padding(horizontal = CouiPageDefaults.rowPadding, vertical = 8.dp),
                    )
                }
            }
        }

        // 自定义域名编辑弹窗：多行输入框，留空恢复官方线路
        Dialog(
            onDismissRequest = scaffold::closeCustomDomainsEditor,
            visible = scaffold.customDomainsVisible.value,
            material = couiDialogMaterial(),
            title = SettingsSections.customDomainsTitle,
            message = SettingsSections.customDomainsSummary,
            placement = CouiDialogPlacement.Bottom,
            confirmAction = CouiDialogAction(
                text = SettingsSections.customDomainsSaveLabel(scaffold.applying.value),
                enabled = !scaffold.applying.value,
                style = CouiDialogActionStyle.Primary,
                colors = CouiDialogDefaults.primaryActionColors(scheme.accent),
                onClick = scaffold::saveCustomDomains,
            ),
            dismissAction = CouiDialogAction(
                text = SettingsSections.customDomainsCancel,
                enabled = !scaffold.applying.value,
                onClick = scaffold::closeCustomDomainsEditor,
                style = CouiDialogActionStyle.Secondary,
            ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .clip(SettingsInputShape)
                    .background(scheme.onSurface.copy(alpha = 0.06f))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .heightIn(min = 96.dp),
            ) {
                BasicTextField(
                    value = scaffold.domainsInput.value,
                    onValueChange = { scaffold.domainsInput.value = it },
                    textStyle = CouiKitTheme.typography.body.copy(color = scheme.onSurface),
                    cursorBrush = SolidColor(scheme.accent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 76.dp)
                        .semantics { contentDescription = SettingsSections.customDomainsTitle },
                    decorationBox = { innerTextField ->
                        Box {
                            if (scaffold.domainsInput.value.isEmpty()) {
                                Text(
                                    text = SettingsSections.customDomainsPlaceholder,
                                    kind = CouiTextKind.ItemSummary,
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

        // 隐藏诊断：版本号连点 5 次打开，只读汇总运行环境与线路状态
        // 退场窗口由库持有：外层 if 会在关闭瞬间把弹窗抽走，动画来不及播
        Dialog(
            onDismissRequest = scaffold::closeDiagnostics,
            visible = scaffold.diagnosticsVisible.value,
            material = couiDialogMaterial(),
            title = SettingsSections.diagnosticsTitle,
            confirmAction = CouiDialogAction(text = SettingsSections.dialogAck, onClick = scaffold::closeDiagnostics),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                // 诊断只报真正生效的项：ColorOS 下取色开关即便存着 true 也不影响取色
                scaffold.diagnosticLines(themeMode, dynamicColor && dynamicColorSupported).forEach { line ->
                    CouiDiagnosticLine(label = line.label, value = line.value)
                }
            }
        }

        // 待确认的清空目标由骨架持有；文案取最近一次目标，退场窗口内仍可渲染
        val pendingClear = scaffold.clearDialogTarget
        if (pendingClear != null) {
            Dialog(
                onDismissRequest = scaffold::dismissClear,
                visible = scaffold.clearDialogVisible,
                material = couiDialogMaterial(),
                title = pendingClear.title,
                message = pendingClear.description,
                placement = CouiDialogPlacement.Center,
                confirmAction = couiDestructiveConfirmAction(
                    text = SettingsSections.clearConfirmLabel(scaffold.clearing.value),
                    enabled = !scaffold.clearing.value,
                ) { scaffold.confirmClear() },
                dismissAction = CouiDialogAction(text = "取消", onClick = scaffold::dismissClear),
            )
        }

        Dialog(
            onDismissRequest = scaffold::hideDisclaimer,
            visible = scaffold.disclaimerVisible.value,
            material = couiDialogMaterial(),
            title = SettingsSections.disclaimerTitle,
            message = SETTINGS_DISCLAIMER_TEXT,
            confirmAction = CouiDialogAction(text = SettingsSections.dialogAck, onClick = scaffold::hideDisclaimer),
        )
    }
}

/** 行标题统一走 PreferenceTitle 字阶，避免每行各写一份字号。 */
@Composable
private fun SettingsRowTitle(text: String, modifier: Modifier = Modifier) {
    Text(text = text, kind = CouiTextKind.ItemTitle, modifier = modifier)
}

/** 行说明用次要色：与标题拉开层级，同时跟随皮肤深浅。 */
@Composable
private fun SettingsRowSummary(text: String) {
    Text(
        text = text,
        kind = CouiTextKind.ItemSummary,
        color = CouiKitTheme.colorScheme.onSurfaceVariant,
    )
}

/** 行尾当前值：只显示不点击，点击语义由整行承担。 */
@Composable
private fun SettingsRowValue(text: String) {
    Text(
        text = text,
        kind = CouiTextKind.ItemValue,
        color = CouiKitTheme.colorScheme.onSurfaceVariant,
    )
}

/** 诊断行：标签在上、值在下，长值自动换行。 */
@Composable
private fun CouiDiagnosticLine(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 5.dp)) {
        Text(
            text = label,
            kind = CouiTextKind.ItemSummary,
            color = CouiKitTheme.colorScheme.onSurfaceVariant,
        )
        CouiBodyText(
            text = value,
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
        options = SettingsOptions(listOf(false, true)) { if (it) "左右翻页" else "纵向长图" },
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
            options = SettingsOptions(ReaderSpreadMode.entries) { it.label },
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
        options = SettingsOptions(ReaderImageQuality.entries) { it.label },
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
        options = SettingsOptions(ReaderPreferences.PREFETCH_OPTIONS) { prefetchPagesLabel(it) },
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
    options: SettingsOptions<T>,
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
            trailing = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SettingsRowValue(value)
                    Icon(
                        imageVector = CouiIcons.ChevronsUpDown,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = CouiKitTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            onClick = { anchor.open { expanded = true } },
        )
        Menu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            anchor = anchor,
            headerText = title,
            items = options.items.map { option ->
                CouiMenuItem(value = option.value, text = option.label, selected = option.value == current)
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
