package com.jm3.app.miuix.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.ui.components.BackButton
import com.jm3.app.miuix.ui.components.DialogActionRow
import com.jm3.app.miuix.ui.components.SectionHorizontalPadding
import com.jm3.app.miuix.ui.components.overlayDialogMaxWidth
import com.jm3.app.miuix.ui.glass.GlassScaffold
import com.jm3.app.miuix.ui.glass.GlassTopAppBar
import com.jm3.app.miuix.ui.glass.TopBarContentSpacer
import com.jm3.app.miuix.ui.route.AppRoutes
import com.jm3.app.miuix.ui.settings.SETTINGS_DISCLAIMER_TEXT
import com.jm3.app.miuix.ui.settings.SETTINGS_DOMAINS_DEFAULT
import com.jm3.app.miuix.ui.settings.SETTINGS_DOMAIN_UNRESOLVED
import com.jm3.app.miuix.ui.settings.SETTINGS_LATENCY_PLACEHOLDER
import com.jm3.app.miuix.ui.settings.SETTINGS_OPEN_SOURCE_SUMMARY
import com.jm3.app.miuix.ui.settings.SettingsClearTarget
import com.jm3.app.miuix.ui.settings.SettingsSections
import com.jm3.app.miuix.ui.settings.bottomBarFormSummary
import com.jm3.app.miuix.ui.settings.paginationModeSummary
import com.jm3.app.miuix.ui.settings.rememberSettingsLayout
import com.jm3.app.miuix.ui.settings.rememberSettingsScaffoldState
import com.jm3.app.miuix.ui.settings.themeModeSummary
import com.jm3.app.miuix.ui.settings.uiSkinSummary
import com.jm3.app.miuix.ui.settings.wideScreenNavigationSummary
import com.jm3.app.miuix.ui.skin.LocalSkin
import com.jm3.core.settings.BottomBarSize
import com.jm3.core.settings.ThemeMode
import com.jm3.core.settings.UiSkin
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 设置中心：主题/线路/数据管理/关于。内容一屏内可读，关于作为分区而非独立页。
 *
 * 状态编排、线路诊断四态、提交入口与全部说明文案都来自共享骨架
 * [rememberSettingsScaffoldState]；这里只保留 Miuix 的控件与弹窗外观。
 */
@Composable
internal fun SettingsScreen(
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
    val skin = LocalSkin.current
    // 骨架持有状态机与提交动作；皮肤侧失败提示沿用既有「按失败归类」的文案
    val scaffold = rememberSettingsScaffoldState(
        deps = deps,
        nav = nav,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
    )
    // 分页/底栏/宽屏导航/阅读器双页的展示值由骨架按同一套窗口断点推导
    val layout = rememberSettingsLayout(deps)
    val diagnostics = scaffold.diagnostics.value

    GlassScaffold(
        topBar = {
            GlassTopAppBar(
                title = SettingsSections.title,
                // 透明底色：玻璃底色与模糊由 GlassScaffold 统一绘制
                color = Color.Transparent,
                navigationIcon = { BackButton(onBack = scaffold.onBack) },
            )
        },
        snackbarHost = { GlassSnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        // 大屏把设置内容收进可读宽度并居中，避免一行文字横跨整屏；手机断点以下不限宽
        Column(
            modifier = Modifier
                .fillMaxSize()
                .readableWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            // 顶栏是浮层：内容从顶栏下沿起排，滚动时可穿过顶栏继续被玻璃采样
            TopBarContentSpacer(paddingValues = innerPadding)
            SmallTitle(text = SettingsSections.sectionTheme)
            Card(cornerRadius = skin.shapes.cardCorner, modifier = Modifier.fillMaxWidth().padding(horizontal = SectionHorizontalPadding)) {
                BasicComponent(
                    title = SettingsSections.appearanceTitle,
                    summary = themeModeSummary(themeMode),
                )
                TabRow(
                    tabs = SettingsSections.themeMode.labels,
                    selectedTabIndex = SettingsSections.themeMode.indexOf(themeMode),
                    onTabSelected = { index ->
                        SettingsSections.themeMode.valueAt(index)?.let(onThemeModeChange)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    // 默认背景与 Card 不同，会在卡片里露出一圈异色矩形；
                    // 这里让轨道贴合卡片底色，选中格用同一色系的淡色区分。
                    colors = TabRowDefaults.tabRowColors(
                        backgroundColor = MiuixTheme.colorScheme.surfaceContainer,
                        selectedBackgroundColor = MiuixTheme.colorScheme.surfaceContainerHigh,
                    ),
                )
                OverlaySpinnerPreference(
                    title = SettingsSections.uiSkinTitle,
                    summary = uiSkinSummary(uiSkin),
                    items = SettingsSections.uiSkin.items.map { option ->
                        DropdownItem(text = option.label, selected = option.value == uiSkin)
                    },
                    selectedIndex = SettingsSections.uiSkin.indexOf(uiSkin),
                    showValue = false,
                    onSelectedIndexChange = { index ->
                        SettingsSections.uiSkin.valueAt(index)?.takeIf { it != uiSkin }?.let(onUiSkinChange)
                    },
                )
                SwitchPreference(
                    title = SettingsSections.dynamicColorTitle,
                    summary = SettingsSections.dynamicColorSummary(dynamicColorSupported),
                    checked = dynamicColor,
                    onCheckedChange = onDynamicColorChange,
                    enabled = dynamicColorSupported,
                )
            }

            SmallTitle(text = SettingsSections.sectionGeneral)
            Card(cornerRadius = skin.shapes.cardCorner, modifier = Modifier.fillMaxWidth().padding(horizontal = SectionHorizontalPadding)) {
                OverlaySpinnerPreference(
                    title = SettingsSections.paginationTitle,
                    summary = paginationModeSummary(layout.paginationMode),
                    items = SettingsSections.paginationMode.items.map { option ->
                        DropdownItem(text = option.label, selected = option.value == layout.paginationMode)
                    },
                    selectedIndex = SettingsSections.paginationMode.indexOf(layout.paginationMode),
                    showValue = false,
                    onSelectedIndexChange = scaffold::applyPaginationIndex,
                )
                OverlaySpinnerPreference(
                    title = "底栏尺寸",
                    summary = if (layout.bottomBarSizeOption == BottomBarSize.GENEROUS) {
                        "偏大：所有设备都使用更高的底栏，图标与标签同步放大"
                    } else {
                        "普通：按设备自适应，手机用标准高度、平板用偏大高度" +
                            "（当前设备按${if (layout.bottomBarSize == BottomBarSize.GENEROUS) "偏大" else "标准"}显示）"
                    },
                    items = BottomBarSize.entries.mapIndexed { index, size ->
                        DropdownItem(
                            text = if (size == BottomBarSize.STANDARD) "普通（自适应）" else size.label,
                            selected = size == layout.bottomBarSizeOption,
                        )
                    },
                    selectedIndex = BottomBarSize.entries.indexOf(layout.bottomBarSizeOption),
                    showValue = false,
                    onSelectedIndexChange = { index ->
                        BottomBarSize.entries.getOrNull(index)?.let { scaffold.applyBottomBarSize(it) }
                    },
                )
                OverlaySpinnerPreference(
                    title = SettingsSections.bottomBarFormTitle,
                    summary = bottomBarFormSummary(layout.bottomBarForm),
                    items = SettingsSections.bottomBarForm.items.map { option ->
                        DropdownItem(text = option.label, selected = option.value == layout.bottomBarForm)
                    },
                    selectedIndex = SettingsSections.bottomBarForm.indexOf(layout.bottomBarForm),
                    showValue = false,
                    onSelectedIndexChange = { index ->
                        SettingsSections.bottomBarForm.valueAt(index)?.let { scaffold.applyBottomBarForm(it) }
                    },
                )
                if (layout.showWideScreenNavigation) {
                    OverlaySpinnerPreference(
                        title = SettingsSections.wideNavigationTitle,
                        summary = wideScreenNavigationSummary(layout.wideScreenNavigation),
                        items = SettingsSections.wideScreenNavigation.items.map { option ->
                            DropdownItem(text = option.label, selected = option.value == layout.wideScreenNavigation)
                        },
                        selectedIndex = SettingsSections.wideScreenNavigation.indexOf(layout.wideScreenNavigation),
                        showValue = false,
                        onSelectedIndexChange = { index ->
                            SettingsSections.wideScreenNavigation.valueAt(index)?.let { scaffold.applyWideScreenNavigation(it) }
                        },
                    )
                }
                ReaderSettingsSection(
                    state = scaffold.readerState,
                    spreadAllowed = layout.readerSpreadAllowed,
                )
            }

            SmallTitle(text = SettingsSections.sectionLine)
            Card(cornerRadius = skin.shapes.cardCorner, modifier = Modifier.fillMaxWidth().padding(horizontal = SectionHorizontalPadding)) {
                // Card 内统一用 BasicComponent 行式布局承载诊断信息与操作，替代散落的小号正文与全宽按钮。
                BasicComponent(
                    title = SettingsSections.activeDomainsTitle,
                    summary = scaffold.activeDomains.value.ifBlank { SETTINGS_DOMAINS_DEFAULT },
                )
                BasicComponent(
                    title = SettingsSections.usedDomainTitle,
                    summary = diagnostics.usedDomain ?: SETTINGS_DOMAIN_UNRESOLVED,
                )
                // 四态由骨架判定：只有内容态逐行列出延迟，其余三态共用一句占位文案
                if (!diagnostics.showsLatencyRows) {
                    BasicComponent(title = SettingsSections.latencyTitle, summary = SETTINGS_LATENCY_PLACEHOLDER)
                } else {
                    diagnostics.latency.forEach { row ->
                        BasicComponent(title = row.domain, summary = SettingsSections.latencyValueText(row.latencyMs))
                    }
                }
                diagnostics.errorMessage?.let { message ->
                    Text(
                        text = SettingsSections.diagnosticsFailureText(message),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                BasicComponent(
                    title = SettingsSections.reprobeTitle,
                    summary = SettingsSections.reprobeSummary(diagnostics.probing),
                    enabled = diagnostics.canRetry,
                    endActions = {
                        if (diagnostics.probing) {
                            CircularProgressIndicator(size = 20.dp)
                        }
                    },
                    onClick = scaffold::requestReprobe,
                )
                BasicComponent(
                    title = SettingsSections.customDomainsTitle,
                    summary = SettingsSections.customDomainsSummary,
                    bottomAction = {
                        TextField(
                            value = scaffold.domainsInput.value,
                            onValueChange = { scaffold.domainsInput.value = it },
                            label = "自定义线路域名",
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
                        )
                    },
                )
                BasicComponent(
                    title = SettingsSections.applyDomainsTitle,
                    summary = SettingsSections.applyDomainsSummary(scaffold.applying.value),
                    enabled = !scaffold.applying.value,
                    onClick = scaffold::applyDomains,
                )
            }

            SmallTitle(text = SettingsSections.sectionData)
            Card(cornerRadius = skin.shapes.cardCorner, modifier = Modifier.fillMaxWidth().padding(horizontal = SectionHorizontalPadding)) {
                ArrowPreference(
                    title = SettingsClearTarget.HISTORY.title,
                    summary = SettingsSections.clearSummary(SettingsClearTarget.HISTORY),
                    onClick = { scaffold.requestClear(SettingsClearTarget.HISTORY) },
                )
                ArrowPreference(
                    title = SettingsClearTarget.DOWNLOADS.title,
                    summary = SettingsSections.clearSummary(SettingsClearTarget.DOWNLOADS),
                    onClick = { scaffold.requestClear(SettingsClearTarget.DOWNLOADS) },
                )
            }

            SmallTitle(text = SettingsSections.sectionAbout)
            Card(cornerRadius = skin.shapes.cardCorner, modifier = Modifier.fillMaxWidth().padding(horizontal = SectionHorizontalPadding)) {
                // 版本行兼作隐藏诊断入口：2 秒内连点 5 次打开诊断面板
                BasicComponent(
                    title = SettingsSections.versionTitle,
                    summary = scaffold.versionName,
                    onClick = scaffold::tapVersion,
                )
                ArrowPreference(
                    title = SettingsSections.updateTitle,
                    summary = SettingsSections.updateSummary,
                    onClick = { scaffold.openUpdate() },
                )
                BasicComponent(
                    title = SettingsSections.openSourceTitle,
                    summary = SETTINGS_OPEN_SOURCE_SUMMARY,
                )
                ArrowPreference(
                    title = SettingsSections.disclaimerTitle,
                    summary = SettingsSections.disclaimerSummary,
                    onClick = scaffold::showDisclaimer,
                )
            }
            // 底部净空由 GlassScaffold 统一给出（导航条 inset + 呼吸空间）
            Spacer(modifier = Modifier.height(innerPadding.calculateBottomPadding()))
        }

        // 隐藏诊断：版本号连点 5 次打开，只读汇总运行环境与线路状态
        OverlayBottomSheet(
            show = scaffold.diagnosticsVisible.value,
            title = SettingsSections.diagnosticsTitle,
            cornerRadius = skin.shapes.sheetCorner,
            onDismissRequest = scaffold::closeDiagnostics,
        ) {
            Card(cornerRadius = skin.shapes.cardCorner, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // HyperOS 侧照实回显存储的取色开关，与既有诊断面板一致
                    scaffold.diagnosticLines(themeMode, dynamicColor).forEach { line ->
                        DiagnosticRow(label = line.label, value = line.value)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // 待确认的清空目标由骨架持有；文案取最近一次目标，退场动画期间不会翻成另一项名字
        val clearTarget = scaffold.clearDialogTarget
        OverlayDialog(
            show = scaffold.clearDialogVisible,
            maxWidth = overlayDialogMaxWidth(),
            title = clearTarget?.title ?: "",
            cornerRadius = skin.shapes.dialogCorner,
            summary = clearTarget?.description ?: "",
            onDismissRequest = scaffold::dismissClear,
        ) {
            // 按钮横排、取消在左、主操作在右；内边距已由弹窗内置的 insideMargin 提供
            DialogActionRow(
                primaryText = SettingsSections.clearConfirmLabel(scaffold.clearing.value),
                primaryEnabled = !scaffold.clearing.value,
                destructive = true,
                onSecondary = scaffold::dismissClear,
                onPrimary = scaffold::confirmClear,
            )
        }

        OverlayDialog(
            show = scaffold.disclaimerVisible.value,
            maxWidth = overlayDialogMaxWidth(),
            title = SettingsSections.disclaimerTitle,
            cornerRadius = skin.shapes.dialogCorner,
            onDismissRequest = scaffold::hideDisclaimer,
        ) {
            Text(
                text = SETTINGS_DISCLAIMER_TEXT,
                style = MiuixTheme.textStyles.body2,
            )
            TextButton(
                text = SettingsSections.dialogAck,
                onClick = scaffold::hideDisclaimer,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )
        }
    }
}

/** 诊断面板单行：标签在上、值在下，长值自动换行。 */
@Composable
private fun DiagnosticRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Text(text = value, style = MiuixTheme.textStyles.body2)
    }
}
