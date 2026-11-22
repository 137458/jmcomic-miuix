// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.page

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.ui.GlassSnackbarHost
import com.jm3.app.miuix.ui.ReaderSettingsState
import com.jm3.app.miuix.ui.coui.component.CouiBodyText
import com.jm3.app.miuix.ui.coui.component.CouiLayoutTokens
import com.jm3.app.miuix.ui.coui.component.CouiPageScaffold
import com.jm3.app.miuix.ui.coui.component.CouiSegmentedCapsules
import com.jm3.app.miuix.ui.coui.component.couiDestructiveDialogAction
import com.jm3.app.miuix.ui.coui.component.couiPageScroll
import com.jm3.app.miuix.ui.coui.component.isCouiWideWindow
import com.jm3.app.miuix.ui.coui.component.panelBlur
import com.jm3.app.miuix.ui.coui.component.rememberCouiBodyTextStyle
import com.jm3.app.miuix.ui.coui.component.rememberCouiPageScrollBehavior
import com.jm3.app.miuix.ui.coui.theme.CouiTheme
import com.jm3.app.miuix.ui.prefetchPagesLabel
import com.jm3.app.miuix.ui.readableWidth
import com.jm3.app.miuix.ui.readerBrightnessLabel
import com.jm3.app.miuix.ui.readerBrightnessSliderValue
import com.jm3.app.miuix.ui.route.AppRoutes
import com.jm3.app.miuix.ui.settings.SETTINGS_CLEAR_CONFIRM_TIMEOUT_MS
import com.jm3.app.miuix.ui.settings.SETTINGS_DISCLAIMER_TEXT
import com.jm3.app.miuix.ui.settings.SETTINGS_DOMAINS_DEFAULT
import com.jm3.app.miuix.ui.settings.SETTINGS_DOMAIN_UNRESOLVED
import com.jm3.app.miuix.ui.settings.SETTINGS_LATENCY_PLACEHOLDER
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
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.BasicComponentDefaults
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardListGroup
import io.github.suqi8.coui.kmp.basic.CardListGroupScope
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.blur.MaterialBlurDefaults
import io.github.suqi8.coui.kmp.layout.DialogButtonBar
import io.github.suqi8.coui.kmp.layout.DialogButtonBarAction
import io.github.suqi8.coui.kmp.layout.DialogDefaults
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.preference.OverlayDropdownPreference
import io.github.suqi8.coui.kmp.preference.PreferenceCategory
import io.github.suqi8.coui.kmp.preference.SliderPreference
import io.github.suqi8.coui.kmp.preference.SwitchPreference
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.rememberCOUIFlingBehavior
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.SnackbarHostState

/** 输入框与卡片同源的圆角：由库的卡片半径派生，避免另写字面量。 */
private val SettingsInputShape = SquircleShape(
    cornerRadius = CouiLayoutTokens.CardCorner / 2,
    weight = CouiLayoutTokens.CardCornerWeight,
)

/**
 * COUI 设置页：整页基于 `suqi8/coui` 2.1.0 偏好组件体系装配，业务语义与存储完全沿用现有实现。
 *
 * 分组采用 [PreferenceCategory] + [Card] + [CardListGroup] + 偏好行（[SwitchPreference]、
 * [OverlayDropdownPreference]、[SliderPreference]、[ArrowPreference]、[BasicComponent]），
 * 主题模式使用胶囊分段控件 [CouiSegmentedCapsules]；
 * 状态编排、线路诊断四态、提交入口与全部说明文案来自共享骨架 [rememberSettingsScaffoldState]。
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
        val scheme = COUITheme.colorScheme
        val scope = rememberCoroutineScope()
        val scaffold = rememberSettingsScaffoldState(
            deps = deps,
            nav = nav,
            snackbarHostState = snackbarHostState,
            onBack = onBack,
            failureDetail = { error -> error?.message ?: "未知错误" },
            clearConfirmTimeoutMs = SETTINGS_CLEAR_CONFIRM_TIMEOUT_MS,
        )
        val layout = rememberSettingsLayout(deps)
        val diagnostics = scaffold.diagnostics.value
        val scrollBehavior = rememberCouiPageScrollBehavior()

        Box(modifier = Modifier.fillMaxSize()) {
            CouiPageScaffold(
                title = SettingsSections.title,
                onBack = scaffold.onBack,
                wide = isCouiWideWindow(),
                scrollBehavior = scrollBehavior,
            ) { contentPadding ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .readableWidth()
                        .couiPageScroll()
                        .consumeWindowInsets(contentPadding),
                    contentPadding = contentPadding,
                    flingBehavior = rememberCOUIFlingBehavior(),
                ) {
                    item(key = "settings-theme") {
                        PreferenceCategory(title = SettingsSections.sectionTheme)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = CouiLayoutTokens.HorizontalMargin)
                                .padding(bottom = CouiLayoutTokens.GroupSpacing),
                        ) {
                            CardListGroup {
                                item(key = "appearance") {
                                    BasicComponent(
                                        title = SettingsSections.appearanceTitle,
                                        summary = themeModeSummary(themeMode),
                                        bottomAction = {
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
                                        },
                                    )
                                }
                                item(key = "ui_skin") {
                                    CouiChoiceItem(
                                        title = SettingsSections.uiSkinTitle,
                                        summary = uiSkinSummary(uiSkin),
                                        options = SettingsSections.uiSkin,
                                        current = uiSkin,
                                        onSelect = onUiSkinChange,
                                    )
                                }
                                if (dynamicColorSupported) {
                                    item(key = "dynamic_color") {
                                        SwitchPreference(
                                            checked = dynamicColor,
                                            onCheckedChange = onDynamicColorChange,
                                            title = SettingsSections.dynamicColorTitle,
                                            summary = SettingsSections.dynamicColorSummary(dynamicColorSupported),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item(key = "settings-general") {
                        PreferenceCategory(title = SettingsSections.sectionGeneral)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = CouiLayoutTokens.HorizontalMargin)
                                .padding(bottom = CouiLayoutTokens.GroupSpacing),
                        ) {
                            CardListGroup {
                                item(key = "pagination") {
                                    CouiChoiceItem(
                                        title = SettingsSections.paginationTitle,
                                        summary = paginationModeSummary(layout.paginationMode),
                                        options = SettingsSections.paginationMode,
                                        current = layout.paginationMode,
                                        onSelect = { mode ->
                                            scaffold.applyPaginationIndex(SettingsSections.paginationMode.indexOf(mode))
                                        },
                                    )
                                }
                                item(key = "bottom_bar") {
                                    CouiChoiceItem(
                                        title = SettingsSections.bottomBarFormTitle,
                                        summary = bottomBarFormSummary(layout.bottomBarForm),
                                        options = SettingsSections.bottomBarForm,
                                        current = layout.bottomBarForm,
                                        onSelect = scaffold::applyBottomBarForm,
                                    )
                                }
                                if (layout.showWideScreenNavigation) {
                                    item(key = "wide_nav") {
                                        CouiChoiceItem(
                                            title = SettingsSections.wideNavigationTitle,
                                            summary = wideScreenNavigationSummary(layout.wideScreenNavigation),
                                            options = SettingsSections.wideScreenNavigation,
                                            current = layout.wideScreenNavigation,
                                            onSelect = scaffold::applyWideScreenNavigation,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item(key = "settings-reader") {
                        PreferenceCategory(title = "阅读器")
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = CouiLayoutTokens.HorizontalMargin)
                                .padding(bottom = CouiLayoutTokens.GroupSpacing),
                        ) {
                            CardListGroup {
                                couiReaderSettingsItems(
                                    state = scaffold.readerState,
                                    spreadAllowed = layout.readerSpreadAllowed,
                                    scope = scope,
                                )
                            }
                        }
                    }

                    item(key = "settings-line") {
                        PreferenceCategory(title = SettingsSections.sectionLine)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = CouiLayoutTokens.HorizontalMargin)
                                .padding(bottom = CouiLayoutTokens.GroupSpacing),
                        ) {
                            CardListGroup {
                                item(key = "active_domains") {
                                    BasicComponent(
                                        title = SettingsSections.activeDomainsTitle,
                                        summary = scaffold.activeDomains.value.ifBlank { SETTINGS_DOMAINS_DEFAULT },
                                    )
                                }
                                item(key = "used_domain") {
                                    BasicComponent(
                                        title = SettingsSections.usedDomainTitle,
                                        summary = diagnostics.usedDomain ?: SETTINGS_DOMAIN_UNRESOLVED,
                                    )
                                }
                                if (!diagnostics.showsLatencyRows) {
                                    item(key = "latency_placeholder") {
                                        BasicComponent(
                                            title = SettingsSections.latencyTitle,
                                            summary = SETTINGS_LATENCY_PLACEHOLDER,
                                        )
                                    }
                                } else {
                                    diagnostics.latency.forEach { row ->
                                        item(key = "latency_${row.domain}") {
                                            BasicComponent(
                                                title = row.domain,
                                                endActions = {
                                                    SettingsRowValue(SettingsSections.latencyValueText(row.latencyMs))
                                                },
                                            )
                                        }
                                    }
                                }
                                diagnostics.errorMessage?.let { message ->
                                    item(key = "diag_error") {
                                        BasicComponent(
                                            title = SettingsSections.diagnosticsFailureText(message),
                                            titleColor = BasicComponentDefaults.titleColor(
                                                color = scheme.error,
                                                disabledColor = scheme.errorDisabled,
                                            ),
                                        )
                                    }
                                }
                                item(key = "reprobe") {
                                    BasicComponent(
                                        title = SettingsSections.reprobeTitle,
                                        summary = SettingsSections.reprobeSummary(diagnostics.probing),
                                        onClick = scaffold::requestReprobe,
                                        enabled = diagnostics.canRetry,
                                    )
                                }
                                item(key = "custom_domains") {
                                    BasicComponent(
                                        title = SettingsSections.customDomainsTitle,
                                        summary = SettingsSections.customDomainsSummary,
                                        bottomAction = {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(SettingsInputShape)
                                                    .background(scheme.labelPrimary.copy(alpha = 0.06f))
                                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                                                    .heightIn(min = 84.dp),
                                            ) {
                                                BasicTextField(
                                                    value = scaffold.domainsInput.value,
                                                    onValueChange = { scaffold.domainsInput.value = it },
                                                    textStyle = rememberCouiBodyTextStyle().copy(color = scheme.labelPrimary),
                                                    cursorBrush = SolidColor(scheme.primary),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .heightIn(min = 64.dp)
                                                        .semantics { contentDescription = SettingsSections.customDomainsTitle },
                                                    decorationBox = { innerTextField ->
                                                        Box {
                                                            if (scaffold.domainsInput.value.isEmpty()) {
                                                                Text(
                                                                    text = "每行一个域名，例如 api.example.com",
                                                                    style = COUITheme.textStyles.bodyS,
                                                                    color = scheme.labelSecondary,
                                                                    textAlign = TextAlign.Start,
                                                                )
                                                            }
                                                            innerTextField()
                                                        }
                                                    },
                                                )
                                            }
                                        },
                                    )
                                }
                                item(key = "apply_domains") {
                                    BasicComponent(
                                        title = SettingsSections.applyDomainsTitle,
                                        summary = SettingsSections.applyDomainsSummary(scaffold.applying.value),
                                        onClick = scaffold::applyDomains,
                                        enabled = !scaffold.applying.value,
                                    )
                                }
                            }
                        }
                    }

                    item(key = "settings-data") {
                        PreferenceCategory(title = SettingsSections.sectionData)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = CouiLayoutTokens.HorizontalMargin)
                                .padding(bottom = CouiLayoutTokens.GroupSpacing),
                        ) {
                            CardListGroup {
                                item(key = "clear_history") {
                                    BasicComponent(
                                        title = SettingsClearTarget.HISTORY.title,
                                        summary = SettingsSections.clearSummary(SettingsClearTarget.HISTORY),
                                        onClick = { scaffold.requestClear(SettingsClearTarget.HISTORY) },
                                    )
                                }
                                item(key = "clear_downloads") {
                                    BasicComponent(
                                        title = SettingsClearTarget.DOWNLOADS.title,
                                        summary = SettingsSections.clearSummary(SettingsClearTarget.DOWNLOADS),
                                        onClick = { scaffold.requestClear(SettingsClearTarget.DOWNLOADS) },
                                    )
                                }
                            }
                        }
                    }

                    item(key = "settings-about") {
                        PreferenceCategory(title = SettingsSections.sectionAbout)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = CouiLayoutTokens.HorizontalMargin)
                                .padding(bottom = CouiLayoutTokens.GroupSpacing),
                        ) {
                            CardListGroup {
                                item(key = "version") {
                                    BasicComponent(
                                        title = SettingsSections.versionTitle,
                                        summary = scaffold.versionName,
                                        onClick = scaffold::tapVersion,
                                    )
                                }
                                item(key = "update") {
                                    ArrowPreference(
                                        title = SettingsSections.updateTitle,
                                        summary = SettingsSections.updateSummary,
                                        onClick = { scaffold.openUpdate() },
                                    )
                                }
                                item(key = "open_source") {
                                    BasicComponent(
                                        title = SettingsSections.openSourceTitle,
                                        summary = SETTINGS_OPEN_SOURCE_SUMMARY,
                                    )
                                }
                                item(key = "disclaimer") {
                                    BasicComponent(
                                        title = SettingsSections.disclaimerTitle,
                                        summary = SettingsSections.disclaimerSummary,
                                        onClick = scaffold::showDisclaimer,
                                    )
                                }
                            }
                        }
                    }
                }

                // 隐藏诊断：版本号连点 5 次打开，只读汇总运行环境与线路状态
                val diagBlur = panelBlur(MaterialBlurDefaults::dialog)
                OverlayDialog(
                    show = scaffold.diagnosticsVisible.value,
                    title = SettingsSections.diagnosticsTitle,
                    backgroundModifier = diagBlur?.backgroundModifier ?: Modifier,
                    backgroundColor = diagBlur?.backgroundColor ?: DialogDefaults.backgroundColor(),
                    materialStroke = diagBlur != null,
                    onDismissRequest = scaffold::closeDiagnostics,
                    customContent = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 10.dp),
                        ) {
                            scaffold.diagnosticLines(themeMode, dynamicColor && dynamicColorSupported).forEach { line ->
                                CouiDiagnosticLine(label = line.label, value = line.value)
                            }
                        }
                    },
                ) {
                    DialogButtonBar(
                        negative = null,
                        positive = DialogButtonBarAction(
                            text = SettingsSections.dialogAck,
                            recommended = true,
                            onClick = scaffold::closeDiagnostics,
                        ),
                    )
                }

                // 待确认的清空目标由骨架持有；文案取最近一次目标，退场窗口内仍可渲染
                val pendingClear = scaffold.clearDialogTarget
                if (pendingClear != null) {
                    val clearBlur = panelBlur(MaterialBlurDefaults::dialog)
                    OverlayDialog(
                        show = scaffold.clearDialogVisible,
                        title = pendingClear.title,
                        summary = pendingClear.description,
                        backgroundModifier = clearBlur?.backgroundModifier ?: Modifier,
                        backgroundColor = clearBlur?.backgroundColor ?: DialogDefaults.backgroundColor(),
                        materialStroke = clearBlur != null,
                        onDismissRequest = scaffold::dismissClear,
                    ) {
                        DialogButtonBar(
                            negative = DialogButtonBarAction(
                                text = "取消",
                                onClick = scaffold::dismissClear,
                            ),
                            positive = null,
                            neutral = couiDestructiveDialogAction(
                                text = SettingsSections.clearConfirmLabel(scaffold.clearing.value),
                                enabled = !scaffold.clearing.value,
                                onClick = { scaffold.confirmClear() },
                            ),
                        )
                    }
                }

                val disclaimerBlur = panelBlur(MaterialBlurDefaults::dialog)
                OverlayDialog(
                    show = scaffold.disclaimerVisible.value,
                    title = SettingsSections.disclaimerTitle,
                    summary = SETTINGS_DISCLAIMER_TEXT,
                    backgroundModifier = disclaimerBlur?.backgroundModifier ?: Modifier,
                    backgroundColor = disclaimerBlur?.backgroundColor ?: DialogDefaults.backgroundColor(),
                    materialStroke = disclaimerBlur != null,
                    onDismissRequest = scaffold::hideDisclaimer,
                ) {
                    DialogButtonBar(
                        negative = null,
                        positive = DialogButtonBarAction(
                            text = SettingsSections.dialogAck,
                            recommended = true,
                            onClick = scaffold::hideDisclaimer,
                        ),
                    )
                }
            }

            // 提示条叠在正文之上
            GlassSnackbarHost(
                state = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
            )
        }
    }
}

/** 行尾当前值：只显示不点击，点击语义由整行承担。 */
@Composable
private fun SettingsRowValue(text: String) {
    Text(
        text = text,
        style = COUITheme.textStyles.bodyM,
        color = COUITheme.colorScheme.labelTertiary,
    )
}

/** 诊断行：标签在上、值在下，长值自动换行。 */
@Composable
private fun CouiDiagnosticLine(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 5.dp)) {
        Text(
            text = label,
            style = COUITheme.textStyles.bodyS,
            color = COUITheme.colorScheme.labelSecondary,
        )
        CouiBodyText(
            text = value,
            modifier = Modifier.padding(top = 1.dp),
        )
    }
}

/**
 * 阅读设置七项在 COUI 皮肤下的渲染：状态与写盘由 [ReaderSettingsState] 承载，
 * 装配进 [CardListGroup] 的各行（[OverlayDropdownPreference] / [SwitchPreference] / [SliderPreference]）。
 */
private fun CardListGroupScope.couiReaderSettingsItems(
    state: ReaderSettingsState,
    spreadAllowed: Boolean,
    scope: CoroutineScope,
) {
    item(key = "reader_direction") {
        CouiChoiceItem(
            title = "默认阅读方向",
            summary = if (state.horizontal) "新打开的章节按左右翻页" else "新打开的章节纵向连续滚动",
            options = SettingsOptions(listOf(false, true)) { if (it) "左右翻页" else "纵向长图" },
            current = state.horizontal,
            onSelect = { horizontal -> scope.launch { state.applyHorizontal(horizontal) } },
        )
    }
    if (spreadAllowed) {
        item(key = "reader_spread") {
            CouiChoiceItem(
                title = "双页阅读",
                summary = if (state.spread == ReaderSpreadMode.DOUBLE) {
                    "大屏横屏下两页并排，按左右翻页生效"
                } else {
                    "单页显示，可按左右翻页"
                },
                options = SettingsOptions(ReaderSpreadMode.entries) { it.label },
                current = state.spread,
                onSelect = { mode -> scope.launch { state.applySpread(mode) } },
            )
        }
    }
    item(key = "reader_volume_paging") {
        SwitchPreference(
            checked = state.volumeKeyPaging,
            onCheckedChange = { checked -> scope.launch { state.applyVolumeKeyPaging(checked) } },
            title = "音量键翻页",
            summary = "关闭后音量键恢复调节系统音量",
        )
    }
    item(key = "reader_keep_screen_on") {
        SwitchPreference(
            checked = state.keepScreenOn,
            onCheckedChange = { checked -> scope.launch { state.applyKeepScreenOn(checked) } },
            title = "阅读时保持亮屏",
            summary = "阅读页不自动息屏",
        )
    }
    item(key = "reader_brightness") {
        SliderPreference(
            value = readerBrightnessSliderValue(state.brightness),
            onValueChange = state::previewBrightness,
            title = "阅读亮度",
            summary = "0 表示跟随系统亮度，只在阅读页生效",
            valueText = readerBrightnessLabel(state.brightness),
            valueRange = 0f..ReaderPreferences.MAX_BRIGHTNESS.toFloat(),
            onValueChangeFinished = { scope.launch { state.commitBrightness() } },
        )
    }
    item(key = "reader_image_quality") {
        CouiChoiceItem(
            title = "图片质量",
            summary = state.imageQuality.settingsSummary(),
            options = SettingsOptions(ReaderImageQuality.entries) { it.label },
            current = state.imageQuality,
            onSelect = { quality -> scope.launch { state.applyImageQuality(quality) } },
        )
    }
    item(key = "reader_prefetch") {
        CouiChoiceItem(
            title = "预取页数",
            summary = if (state.prefetchPages == 0) {
                "不提前加载后续页"
            } else {
                "提前加载当前页之后的 " + state.prefetchPages + " 页"
            },
            options = SettingsOptions(ReaderPreferences.PREFETCH_OPTIONS) { prefetchPagesLabel(it) },
            current = state.prefetchPages,
            onSelect = { pages -> scope.launch { state.applyPrefetchPages(pages) } },
        )
    }
}

/**
 * 枚举型设置行：基于 [OverlayDropdownPreference]，跟手在触点弹出 COUI 17 菜单。
 */
@Composable
private fun <T> CouiChoiceItem(
    title: String,
    summary: String,
    options: SettingsOptions<T>,
    current: T,
    onSelect: (T) -> Unit,
) {
    val selectedIndex = options.indexOf(current).coerceAtLeast(0)
    OverlayDropdownPreference(
        items = options.labels,
        selectedIndex = selectedIndex,
        title = title,
        summary = summary,
        onSelectedIndexChange = { index ->
            val next = options.valueAt(index) ?: return@OverlayDropdownPreference
            if (next != current) onSelect(next)
        },
    )
}
