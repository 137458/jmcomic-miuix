package com.jm3.app.miuix.ui

import android.content.Context
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
import com.jm3.core.settings.Domains
import com.jm3.core.settings.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
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

    val versionName = remember {
        @Suppress("DEPRECATION")
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: "未知"
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

    Scaffold(
        topBar = { TopAppBar(title = "设置", navigationIcon = { BackButton(onBack = onBack) }) },
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

            SmallTitle(text = "关于")
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                // 只读信息行不画箭头，避免误导为可点击项。
                BasicComponent(title = "版本", summary = versionName)
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
