package com.jm3.app.miuix.ui

import android.content.Context
import android.os.Build
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jm3.app.miuix.Jm3Graph
import com.jm3.app.miuix.ui.glass.GlassScaffold
import com.jm3.app.miuix.ui.glass.GlassTopAppBar
import com.jm3.app.miuix.ui.glass.TopBarContentSpacer
import com.jm3.app.miuix.update.UpdateDownloadState
import com.jm3.core.Jm3Gateway
import com.jm3.core.session.AuthCall
import com.jm3.core.session.SessionOutcome
import com.jm3.core.settings.PaginationMode
import com.jm3.core.settings.ThemeMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private fun ThemeMode.label(): String = when (this) {
    ThemeMode.SYSTEM -> "跟随系统"
    ThemeMode.LIGHT -> "浅色"
    ThemeMode.DARK -> "深色"
}

private fun PaginationMode.label(): String = when (this) {
    PaginationMode.INFINITE -> "无限滚动"
    PaginationMode.CLICK -> "点击翻页"
}

/** 版本号连点触发隐藏诊断的阈值与窗口。 */
private const val DIAGNOSTIC_TAPS = 5
private const val DIAGNOSTIC_TAP_WINDOW_MS = 2000L

/** 设置中心：主题/线路/数据管理/关于。内容一屏内可读，关于作为分区而非独立页。 */
@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    dynamicColor: Boolean,
    onDynamicColorChange: (Boolean) -> Unit,
    gateway: Jm3Gateway,
    authCall: AuthCall,
    onBack: () -> Unit,
    onOpenUpdate: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val domainsInput = rememberSaveable { mutableStateOf("") }
    var loadedSaved by rememberSaveable { mutableStateOf(false) }
    val activeDomains = rememberSaveable { mutableStateOf("") }
    val applying = remember { mutableStateOf(false) }

    // 线路诊断：当前实际域名与各域名探活延迟（毫秒升序）。
    var usedDomain by remember { mutableStateOf<String?>(null) }
    var domainLatency by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    var reprobeBusy by remember { mutableStateOf(false) }
    var diagError by remember { mutableStateOf<String?>(null) }

    // 破坏性操作二次确认：3 秒未确认自动复位。
    var confirmClear by remember { mutableStateOf<String?>(null) }
    var showDisclaimer by remember { mutableStateOf(false) }

    // 分页模式：只读全局设置域里的可观察状态，本地不再留一份会滞后的副本
    val paginationMode = Jm3Graph.preferences.paginationMode
    // 版本号连点进入隐藏诊断（原版 secret tap 进 /test）
    var diagnostics by remember { mutableStateOf(false) }
    var versionTapCount by remember { mutableIntStateOf(0) }
    // 测试更新弹窗：只在本地预览弹窗外观，不检查远端
    var showTestUpdateDialog by remember { mutableStateOf(false) }
    var testRelease by remember { mutableStateOf(TEST_UPDATE_RELEASE) }
    var testDownloadState by remember { mutableStateOf<UpdateDownloadState>(UpdateDownloadState.Idle) }

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
        if (mode == Jm3Graph.preferences.paginationMode) return
        Jm3Graph.preferences.paginationMode = mode
        scope.launch { Jm3Graph.preferences.repository.setPaginationMode(mode) }
    }

    // 连点计数在窗口内归零，避免误触累计
    LaunchedEffect(versionTapCount) {
        if (versionTapCount in 1 until DIAGNOSTIC_TAPS) {
            delay(DIAGNOSTIC_TAP_WINDOW_MS)
            versionTapCount = 0
        }
    }

    fun loadDomainsDiagnostics() {
        scope.launch {
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
    }

    fun reprobeDomains() {
        if (reprobeBusy) return
        reprobeBusy = true
        scope.launch {
            val result = authCall.call { gateway.reprobeDomains() }
            reprobeBusy = false
            when (result) {
                is SessionOutcome.Ok -> snackbarHostState.showSnackbar("线路探活完成")
                else -> snackbarHostState.showSnackbar(
                    "探活失败：" + planLoad(result, hasContent = false, failureMessage = "未知错误").message,
                )
            }
            // 无论成败都重读一次：失败时也保留上一次可用的探活结果。
            loadDomainsDiagnostics()
        }
    }

    LaunchedEffect(Unit) {
        if (!loadedSaved) {
            loadedSaved = true
            val saved = Jm3Graph.settings.apiDomains()
            domainsInput.value = saved.joinToString("\n")
            activeDomains.value = saved.joinToString("、")
        }
        loadDomainsDiagnostics()
    }
    LaunchedEffect(confirmClear) {
        if (confirmClear != null) {
            delay(3000)
            confirmClear = null
        }
    }

    val clearingHistory = confirmClear == "history"

    GlassScaffold(
        topBar = {
            GlassTopAppBar(
                title = "设置",
                // 透明底色：玻璃底色与模糊由 GlassScaffold 统一绘制
                color = Color.Transparent,
                navigationIcon = { BackButton(onBack = onBack) },
            )
        },
        snackbarHost = { GlassSnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            // 顶栏是浮层：内容从顶栏下沿起排，滚动时可穿过顶栏继续被玻璃采样
            TopBarContentSpacer(paddingValues = innerPadding)
            SmallTitle(text = "主题")
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                BasicComponent(
                    title = "外观模式",
                    summary = when (themeMode) {
                        ThemeMode.SYSTEM -> "跟随系统自动切换深浅色"
                        ThemeMode.LIGHT -> "始终保持浅色高亮视觉"
                        ThemeMode.DARK -> "始终保持深色暗调视觉"
                    },
                )
                TabRow(
                    tabs = ThemeMode.entries.map { it.label() },
                    selectedTabIndex = ThemeMode.entries.indexOf(themeMode),
                    onTabSelected = { index ->
                        ThemeMode.entries.getOrNull(index)?.let(onThemeModeChange)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
                SwitchPreference(
                    title = "动态取色",
                    summary = "跟随壁纸生成配色（Android 12 及以上）",
                    checked = dynamicColor,
                    onCheckedChange = onDynamicColorChange,
                )
            }

            SmallTitle(text = "通用")
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                OverlaySpinnerPreference(
                    title = "分页模式",
                    summary = when (paginationMode) {
                        PaginationMode.INFINITE -> "滚动到底自动加载下一页"
                        PaginationMode.CLICK -> "列表底部显示上一页/下一页"
                    },
                    items = PaginationMode.entries.mapIndexed { index, mode ->
                        DropdownItem(text = mode.label(), selected = mode == paginationMode)
                    },
                    selectedIndex = PaginationMode.entries.indexOf(paginationMode),
                    showValue = false,
                    onSelectedIndexChange = { index ->
                        resolvePaginationSelection(Jm3Graph.preferences.paginationMode, index)
                            ?.takeIf { it.changed }
                            ?.let { applyPaginationMode(it.mode) }
                    },
                )
            }

            SmallTitle(text = "线路")
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                // Card 内统一用 BasicComponent 行式布局承载诊断信息与操作，替代散落的小号正文与全宽按钮。
                BasicComponent(
                    title = "当前线路",
                    summary = activeDomains.value.ifBlank { "默认（自动解析）" },
                )
                BasicComponent(
                    title = "实际线路",
                    summary = usedDomain ?: "未解析",
                )
                if (domainLatency.isEmpty()) {
                    BasicComponent(title = "线路延迟", summary = "尚未完成线路探活")
                } else {
                    domainLatency.forEach { (domain, latencyMs) ->
                        BasicComponent(title = domain, summary = "${latencyMs} ms")
                    }
                }
                diagError?.let { message ->
                    Text(
                        text = "线路诊断失败：$message",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                BasicComponent(
                    title = "重新探活",
                    summary = if (reprobeBusy) "探活中…" else "逐条测速并按延迟排序",
                    enabled = !reprobeBusy,
                    endActions = {
                        if (reprobeBusy) {
                            CircularProgressIndicator(size = 20.dp)
                        }
                    },
                    onClick = ::reprobeDomains,
                )
                BasicComponent(
                    title = "自定义线路",
                    summary = "每行一个域名，留空恢复默认",
                    bottomAction = {
                        TextField(
                            value = domainsInput.value,
                            onValueChange = { domainsInput.value = it },
                            label = "自定义线路域名",
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
                        )
                    },
                )
                BasicComponent(
                    title = "应用线路",
                    summary = if (applying.value) "应用中…" else "保存配置并重建 API 网关",
                    enabled = !applying.value,
                    onClick = {
                        if (applying.value) return@BasicComponent
                        applying.value = true
                        scope.launch {
                            // 写入即归一（去协议头/路径/空白项并去重），读回的就是真正生效的线路表。
                            Jm3Graph.settings.setApiDomains(domainsInput.value.split("\n"))
                            val applied = Jm3Graph.settings.apiDomains()
                            Jm3Graph.applyDomains(applied)
                            activeDomains.value = applied.joinToString("、")
                            applying.value = false
                            // 网关已重建，诊断数据属于旧实例，重新读取。
                            loadDomainsDiagnostics()
                            snackbarHostState.showSnackbar(
                                if (applied.isEmpty()) "已恢复默认线路" else "已应用 ${applied.size} 个线路域名",
                            )
                        }
                    },
                )
            }

            SmallTitle(text = "数据管理")
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                ArrowPreference(
                    title = "清空阅读历史",
                    summary = "移除全部阅读记录",
                    onClick = { confirmClear = "history" },
                )
                ArrowPreference(
                    title = "清空下载",
                    summary = "清除下载任务与已下载文件",
                    onClick = { confirmClear = "downloads" },
                )
            }

            SmallTitle(text = "关于")
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                // 版本行兼作隐藏诊断入口：2 秒内连点 5 次打开诊断面板
                BasicComponent(
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
                ArrowPreference(
                    title = "测试更新弹窗",
                    summary = "用固定假数据预览弹窗排版，不访问网络",
                    onClick = {
                        testRelease = TEST_UPDATE_RELEASE
                        testDownloadState = UpdateDownloadState.Idle
                        showTestUpdateDialog = true
                    },
                )
                ArrowPreference(
                    title = "测试更新弹窗（下载中）",
                    summary = "预览下载中的弹窗：进度条、速度与取消下载入口",
                    onClick = {
                        testRelease = TEST_UPDATE_RELEASE
                        testDownloadState = TEST_DOWNLOADING_STATE
                        showTestUpdateDialog = true
                    },
                )
                ArrowPreference(
                    title = "测试更新弹窗（长日志）",
                    summary = "用 60 行以上的假更新说明验证折叠、展开与滚动排版",
                    onClick = {
                        testRelease = TEST_LONG_UPDATE_RELEASE
                        testDownloadState = UpdateDownloadState.Idle
                        showTestUpdateDialog = true
                    },
                )
                BasicComponent(
                    title = "开源组件",
                    summary = "jmcomic-api (MIT) · Miuix (Apache-2.0) · Coil (Apache-2.0) · Jetpack Compose (Apache-2.0)",
                )
                ArrowPreference(
                    title = "免责声明",
                    summary = "第三方客户端，仅供学习交流，包含成人内容",
                    onClick = { showDisclaimer = true },
                )
            }
            // 底部净空由 GlassScaffold 统一给出（导航条 inset + 呼吸空间）
            Spacer(modifier = Modifier.height(innerPadding.calculateBottomPadding()))
        }

        // 隐藏诊断：版本号连点 5 次打开，只读汇总运行环境与线路状态
        OverlayBottomSheet(
            show = diagnostics,
            title = "诊断信息",
            onDismissRequest = { diagnostics = false },
        ) {
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    DiagnosticRow(label = "包名", value = context.packageName)
                    DiagnosticRow(label = "版本", value = "$versionName ($versionCode)")
                    DiagnosticRow(label = "设备", value = "${Build.MANUFACTURER} ${Build.MODEL}")
                    DiagnosticRow(
                        label = "系统",
                        value = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                    )
                    DiagnosticRow(label = "当前线路", value = usedDomain ?: "未解析")
                    DiagnosticRow(
                        label = "自定义线路",
                        value = activeDomains.value.ifBlank { "默认（自动解析）" },
                    )
                    DiagnosticRow(
                        label = "线路延迟",
                        value = domainLatency.joinToString("\n") { (domain, ms) -> "$domain · ${ms}ms" }
                            .ifBlank { "尚未完成线路探活" },
                    )
                    DiagnosticRow(label = "分页模式", value = paginationMode.label())
                    DiagnosticRow(
                        label = "主题",
                        value = themeMode.label() + if (dynamicColor) " · 动态取色" else "",
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        OverlayDialog(
            show = confirmClear != null,
            title = if (clearingHistory) "清空阅读历史" else "清空下载",
            summary = if (clearingHistory) {
                "将删除全部阅读记录，此操作不可撤销。"
            } else {
                "将删除全部下载任务并清除已下载文件，此操作不可撤销。"
            },
            onDismissRequest = { confirmClear = null },
        ) {
            // 按钮横排、取消在左、主操作在右；内边距已由弹窗内置的 insideMargin 提供
            DialogActionRow(
                primaryText = "确认清空",
                destructive = true,
                onSecondary = { confirmClear = null },
                onPrimary = {
                    val target = confirmClear
                    confirmClear = null
                    scope.launch {
                        when (target) {
                            "history" -> Jm3Graph.history.clear()
                            "downloads" -> Jm3Graph.downloadManager.clearAll()
                        }
                        snackbarHostState.showSnackbar(
                            if (target == "history") "已清空阅读历史" else "已清空下载",
                        )
                    }
                },
            )
        }

        OverlayDialog(
            show = showDisclaimer,
            title = "免责声明",
            onDismissRequest = { showDisclaimer = false },
        ) {
            Text(
                text = "本应用为第三方客户端，与禁漫官方无关，仅供学习交流；" +
                    "全部内容来自第三方站点，本应用不存储任何内容。包含成人内容，" +
                    "未满 18 周岁（或当地法定年龄）请勿使用；因使用本应用产生的一切后果由使用者自行承担。",
                style = MiuixTheme.textStyles.body2,
            )
            TextButton(
                text = "知道了",
                onClick = { showDisclaimer = false },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )
        }

        // 设置里的"测试更新弹窗"：直接复用真实弹窗，只把数据换成固定假版本
        UpdateAvailableDialog(
            show = showTestUpdateDialog,
            currentVersion = versionName,
            release = testRelease,
            downloadState = testDownloadState,
            onDismiss = { showTestUpdateDialog = false },
            // 不关弹窗：把假状态切成"下载中"，现场看进度条与主按钮怎么写进度
            onDownload = {
                testDownloadState = TEST_DOWNLOADING_STATE
                scope.launch { snackbarHostState.showSnackbar("测试弹窗：真实入口会开始应用内下载") }
            },
            onOpenRelease = {
                showTestUpdateDialog = false
                scope.launch { snackbarHostState.showSnackbar("测试弹窗：真实入口会打开 Releases 页面") }
            },
            onIgnore = { version ->
                showTestUpdateDialog = false
                scope.launch { snackbarHostState.showSnackbar("测试弹窗：真实入口会把 $version 记为忽略版本") }
            },
            onCancelDownload = {
                testDownloadState = UpdateDownloadState.Idle
                scope.launch { snackbarHostState.showSnackbar("测试弹窗：已取消下载") }
            },
            onOpenUpdatePage = {
                showTestUpdateDialog = false
                onOpenUpdate()
            },
        )
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
