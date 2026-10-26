package com.jm3.app.ui

import android.content.Context
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jm3.app.Jm3Graph
import com.jm3.core.settings.Domains
import com.jm3.core.settings.ThemeMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.RadioButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 设置中心：主题/线路/数据管理/关于。内容一屏内可读，关于作为分区而非独立页。 */
@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val domainsInput = rememberSaveable { mutableStateOf("") }
    var loadedSaved by rememberSaveable { mutableStateOf(false) }
    val activeDomains = rememberSaveable { mutableStateOf("") }
    val applying = remember { mutableStateOf(false) }
    val applyResult = remember { mutableStateOf<String?>(null) }

    // 破坏性操作二次确认：3 秒未确认自动复位。
    var confirmClear by remember { mutableStateOf<String?>(null) }

    val versionName = remember {
        @Suppress("DEPRECATION")
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: "未知"
    }

    LaunchedEffect(Unit) {
        if (!loadedSaved) {
            loadedSaved = true
            val saved = Jm3Graph.settings.apiDomains()
            domainsInput.value = saved.joinToString("\n")
            activeDomains.value = saved.joinToString("、")
        }
    }
    LaunchedEffect(confirmClear) {
        if (confirmClear != null) {
            delay(3000)
            confirmClear = null
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = "设置", navigationIcon = { BackButton(onBack = onBack) }) },
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
                    BasicComponent(
                        title = when (mode) {
                            ThemeMode.SYSTEM -> "跟随系统"
                            ThemeMode.LIGHT -> "浅色"
                            ThemeMode.DARK -> "深色"
                        },
                        summary = null,
                        endActions = { RadioButton(selected = themeMode == mode, onClick = null) },
                        onClick = { onThemeModeChange(mode) },
                    )
                }
            }

            SmallTitle(text = "线路")
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text(
                    text = "当前线路：" + activeDomains.value.ifBlank { "默认（自动解析）" },
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
                Text(
                    text = "API 域名（每行一个，留空恢复默认）",
                    fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
                TextField(
                    value = domainsInput.value,
                    onValueChange = { domainsInput.value = it },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
                TextButton(
                    text = if (applying.value) "应用中…" else "应用线路",
                    onClick = {
                        if (applying.value) return@TextButton
                        applying.value = true
                        scope.launch {
                            val domains = Domains.normalize(domainsInput.value.split("\n"))
                            Jm3Graph.settings.setApiDomains(domains)
                            Jm3Graph.applyDomains(domains)
                            activeDomains.value = domains.joinToString("、")
                            applyResult.value =
                                if (domains.isEmpty()) "已恢复默认线路" else "已应用 ${domains.size} 个线路域名"
                            applying.value = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
                applyResult.value?.let { msg ->
                    Text(
                        text = msg,
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }

            SmallTitle(text = "数据管理")
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                BasicComponent(
                    title = "清空阅读历史",
                    summary = if (confirmClear == "history") "再点一次确认清空" else null,
                    onClick = {
                        if (confirmClear == "history") {
                            confirmClear = null
                            scope.launch { Jm3Graph.history.clear() }
                        } else {
                            confirmClear = "history"
                        }
                    },
                )
                BasicComponent(
                    title = "清空下载",
                    summary = if (confirmClear == "downloads") "再点一次确认清空（含已下载文件）" else null,
                    onClick = {
                        if (confirmClear == "downloads") {
                            confirmClear = null
                            scope.launch { Jm3Graph.downloadManager.clearAll() }
                        } else {
                            confirmClear = "downloads"
                        }
                    },
                )
            }

            SmallTitle(text = "关于")
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                BasicComponent(title = "版本", summary = versionName, onClick = null)
                BasicComponent(
                    title = "开源组件",
                    summary = "jmcomic-api (MIT) · Miuix (Apache-2.0) · Coil (Apache-2.0) · Jetpack Compose (Apache-2.0)",
                    onClick = null,
                )
            }
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text(
                    text = "免责声明：本应用为第三方客户端，与禁漫官方无关，仅供学习交流；" +
                        "全部内容来自第三方站点，本应用不存储任何内容。包含成人内容，" +
                        "未满 18 周岁（或当地法定年龄）请勿使用；因使用本应用产生的一切后果由使用者自行承担。",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
