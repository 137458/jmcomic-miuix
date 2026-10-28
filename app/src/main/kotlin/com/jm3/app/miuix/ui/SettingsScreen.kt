package com.jm3.app.miuix.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import com.jm3.core.settings.Domains
import com.jm3.core.settings.PaginationMode
import com.jm3.core.settings.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
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

/** 原版「JM 公告」对应官方 Trello，网址发布页用于获取最新可用域名。 */
private const val ANNOUNCEMENT_URL = "https://trello.com/b/McDZAm8C/"
private const val RELEASE_PAGE_URL = "https://jm-uu.cc/mJ8rWd"
private const val CONTACT_MAIL = "mailto:www18comic@gmail.com?subject=JMComic3_APP"

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
    onBack: () -> Unit,
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

    // 分页模式：与全局状态同步，切换后各列表页按新语义从第一页重载
    var paginationMode by remember { mutableStateOf(Jm3Graph.paginationMode) }
    var showPaginationPicker by remember { mutableStateOf(false) }
    // 版本号连点进入隐藏诊断（原版 secret tap 进 /test）
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

    /** 打开外链；无浏览器等失败场景回落到 Snackbar。 */
    fun openUrl(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure {
                scope.launch { snackbarHostState.showSnackbar("无法打开链接：$url") }
            }
    }

    fun applyPaginationMode(mode: PaginationMode) {
        showPaginationPicker = false
        if (mode == paginationMode) return
        paginationMode = mode
        Jm3Graph.paginationMode = mode
        scope.launch { Jm3Graph.settings.setPaginationMode(mode) }
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
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    Jm3Graph.gateway.usedDomain() to
                        Jm3Graph.gateway.domainLatency().entries
                            .sortedBy { it.value }
                            .map { it.key to it.value }
                }
            }
            result
                .onSuccess { (domain, latency) ->
                    usedDomain = domain
                    domainLatency = latency
                }
                .onFailure { diagError = it.message ?: "线路诊断失败" }
        }
    }

    fun reprobeDomains() {
        if (reprobeBusy) return
        reprobeBusy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { Jm3Graph.gateway.reprobeDomains() }
            }
            reprobeBusy = false
            result
                .onSuccess { snackbarHostState.showSnackbar("线路探活完成") }
                .onFailure { snackbarHostState.showSnackbar("探活失败：" + (it.message ?: "未知错误")) }
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
            TopAppBar(
                title = "设置",
                // 透明底色：玻璃底色与模糊由 GlassScaffold 统一绘制
                color = Color.Transparent,
                navigationIcon = { BackButton(onBack = onBack) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding),
        ) {
            SmallTitle(text = "主题")
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                ThemeMode.entries.forEach { mode ->
                    RadioButtonPreference(
                        title = mode.label(),
                        selected = themeMode == mode,
                        onClick = { onThemeModeChange(mode) },
                    )
                }
                SwitchPreference(
                    title = "动态取色",
                    summary = "跟随壁纸生成配色（Android 12 及以上）",
                    checked = dynamicColor,
                    onCheckedChange = onDynamicColorChange,
                )
            }

            SmallTitle(text = "通用")
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                ArrowPreference(
                    title = "分页模式",
                    summary = paginationMode.label(),
                    onClick = { showPaginationPicker = true },
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
                        fontSize = MiuixTheme.textStyles.footnote1.fontSize,
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
                            val domains = Domains.normalize(domainsInput.value.split("\n"))
                            Jm3Graph.settings.setApiDomains(domains)
                            Jm3Graph.applyDomains(domains)
                            activeDomains.value = domains.joinToString("、")
                            applying.value = false
                            // 网关已重建，诊断数据属于旧实例，重新读取。
                            loadDomainsDiagnostics()
                            snackbarHostState.showSnackbar(
                                if (domains.isEmpty()) "已恢复默认线路" else "已应用 ${domains.size} 个线路域名",
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

            SmallTitle(text = "公告与支持")
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                ArrowPreference(
                    title = "JM 公告",
                    summary = "官方公告与版本动态",
                    onClick = { openUrl(ANNOUNCEMENT_URL) },
                )
                ArrowPreference(
                    title = "网址发布页",
                    summary = "获取最新可用线路",
                    onClick = { openUrl(RELEASE_PAGE_URL) },
                )
                ArrowPreference(
                    title = "广告合作",
                    summary = "www18comic@gmail.com",
                    onClick = { openUrl(CONTACT_MAIL) },
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
            Spacer(modifier = Modifier.height(24.dp))
        }

        // 分页模式选择：两档语义在 summary 中说明，避免只改名字造成困惑
        OverlayBottomSheet(
            show = showPaginationPicker,
            title = "分页模式",
            onDismissRequest = { showPaginationPicker = false },
        ) {
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                PaginationMode.entries.forEach { mode ->
                    RadioButtonPreference(
                        title = mode.label(),
                        summary = when (mode) {
                            PaginationMode.INFINITE -> "滚动到底自动加载下一页"
                            PaginationMode.CLICK -> "列表底部显示上一页/下一页"
                        },
                        selected = paginationMode == mode,
                        onClick = { applyPaginationMode(mode) },
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
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
            // HyperOS 弹窗惯例：按钮纵向堆叠、各占满宽，避免横排等分与系统弹窗观感不一致
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    text = "取消",
                    onClick = { confirmClear = null },
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    text = "确认清空",
                    colors = ButtonDefaults.textButtonColors(
                        color = Color.Transparent,
                        textColor = MiuixTheme.colorScheme.error,
                    ),
                    onClick = {
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
                    modifier = Modifier.fillMaxWidth(),
                )
            }
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
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            )
            TextButton(
                text = "知道了",
                onClick = { showDisclaimer = false },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
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
            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Text(text = value, fontSize = MiuixTheme.textStyles.body2.fontSize)
    }
}
