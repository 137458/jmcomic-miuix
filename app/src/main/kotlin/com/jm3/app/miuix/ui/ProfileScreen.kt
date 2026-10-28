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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jm3.app.miuix.Jm3Graph
import com.jm3.app.miuix.ui.glass.GlassScaffold
import com.jm3.core.model.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 个人资料中需要下拉选择的字段：服务端只接受固定枚举值。 */
private enum class ProfileSelector(val title: String, val field: String) {
    RELATIONS("感情状态", "relations"),
    SEXUALITY("性取向", "sexuality"),
}

private val relationOptions = listOf(
    "" to "未填写",
    "Single" to "单身",
    "Taken" to "非单身",
    "Open" to "开放关系",
)

private val sexualityOptions = listOf(
    "" to "未填写",
    "Guys" to "男性",
    "Girls" to "女性",
    "Guys + Girls" to "男女皆可",
)

private fun ProfileSelector.options(): List<Pair<String, String>> = when (this) {
    ProfileSelector.RELATIONS -> relationOptions
    ProfileSelector.SEXUALITY -> sexualityOptions
}

private sealed interface ProfileUiState {
    data object Loading : ProfileUiState
    data object Loaded : ProfileUiState
    data class Failed(val message: String) : ProfileUiState
}

/** 个人资料：只读账号信息 + 全量可编辑字段，保存后以服务端返回为准。 */
@Composable
fun ProfileScreen(onBack: () -> Unit, snackbarHostState: SnackbarHostState) {
    val user by Jm3Graph.account.user.collectAsState()
    var state by remember { mutableStateOf<ProfileUiState>(ProfileUiState.Loading) }
    var profile by remember { mutableStateOf<UserProfile?>(null) }
    var saving by remember { mutableStateOf(false) }
    var selector by remember { mutableStateOf<ProfileSelector?>(null) }
    // 编辑缓冲：键与 editableFields() 一致，提交时整体回传
    val fields = remember { mutableStateMapOf<String, String>() }
    val scope = rememberCoroutineScope()

    fun applyProfile(next: UserProfile) {
        profile = next
        fields.clear()
        fields.putAll(next.editableFields())
    }

    fun load(showLoading: Boolean = true) {
        val uid = user?.uid
        if (uid == null) {
            state = ProfileUiState.Failed("请先登录后查看个人资料")
            return
        }
        if (showLoading) state = ProfileUiState.Loading
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { Jm3Graph.gateway.userProfile(uid) }
            }
            result
                .onSuccess {
                    applyProfile(it)
                    state = ProfileUiState.Loaded
                }
                .onFailure { state = ProfileUiState.Failed(it.message ?: "个人资料加载失败") }
        }
    }

    fun save() {
        val uid = user?.uid ?: return
        if (saving) return
        saving = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { Jm3Graph.gateway.editUserProfile(uid, fields.toMap()) }
            }
            saving = false
            result
                .onSuccess {
                    applyProfile(it)
                    snackbarHostState.showSnackbar("资料已保存")
                }
                .onFailure { snackbarHostState.showSnackbar("保存失败：" + (it.message ?: "请稍后重试")) }
        }
    }

    LaunchedEffect(user?.uid) {
        fields.clear()
        profile = null
        load()
    }

    GlassScaffold(
        topBar = {
            TopAppBar(
                title = "个人资料",
                // 透明底色：玻璃底色与模糊由 GlassScaffold 统一绘制
                color = Color.Transparent,
                navigationIcon = { BackButton(onBack = onBack) },
                actions = {
                    if (state is ProfileUiState.Loaded) {
                        TextButton(
                            text = if (saving) "保存中…" else "保存",
                            onClick = ::save,
                            enabled = !saving,
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when (val s = state) {
            ProfileUiState.Loading -> LoadingScreen(modifier = Modifier.padding(innerPadding))
            is ProfileUiState.Failed -> ErrorScreen(
                message = s.message,
                onRetry = { load() },
                modifier = Modifier.padding(innerPadding),
            )
            ProfileUiState.Loaded -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState()),
            ) {
                SmallTitle(text = "账号信息")
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    BasicComponent(title = "用户名", summary = profile?.username.orEmpty())
                    BasicComponent(title = "邮箱", summary = profile?.email.orEmpty())
                }

                SmallTitle(text = "基本信息")
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    FieldInput(fields, "nickname", "昵称")
                    FieldInput(fields, "lastName", "姓")
                    FieldInput(fields, "firstName", "名")
                    FieldInput(fields, "birthday", "生日（YYYY-MM-DD）")
                    BasicComponent(
                        title = ProfileSelector.RELATIONS.title,
                        summary = relationOptions
                            .firstOrNull { it.first == fields[ProfileSelector.RELATIONS.field] }
                            ?.second
                            ?: "未填写",
                        onClick = { selector = ProfileSelector.RELATIONS },
                    )
                    BasicComponent(
                        title = ProfileSelector.SEXUALITY.title,
                        summary = sexualityOptions
                            .firstOrNull { it.first == fields[ProfileSelector.SEXUALITY.field] }
                            ?.second
                            ?: "未填写",
                        onClick = { selector = ProfileSelector.SEXUALITY },
                    )
                    FieldInput(fields, "website", "个人网站")
                }

                SmallTitle(text = "位置信息")
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    FieldInput(fields, "birthPlace", "出生地")
                    FieldInput(fields, "city", "城市")
                    FieldInput(fields, "country", "国家")
                    FieldInput(fields, "occupation", "职业")
                    FieldInput(fields, "company", "公司")
                    FieldInput(fields, "school", "学校")
                }

                SmallTitle(text = "其他信息")
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    FieldInput(fields, "aboutMe", "关于我", multiline = true)
                    FieldInput(fields, "infoHere", "其他信息", multiline = true)
                    FieldInput(fields, "collections", "收藏类别", multiline = true)
                    FieldInput(fields, "ideal", "理想型", multiline = true)
                    FieldInput(fields, "erogenic", "敏感区域", multiline = true)
                    FieldInput(fields, "favorite", "最喜欢", multiline = true)
                    FieldInput(fields, "hate", "最讨厌", multiline = true)
                }

                // 长表单滚动到底部时提供就近保存入口，避免每次都回到标题栏
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Button(
                        onClick = ::save,
                        enabled = !saving,
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = if (saving) "保存中…" else "保存修改")
                    }
                }
                if (saving) {
                    Text(
                        text = "正在提交…",
                        fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        val kind = selector
        if (kind != null) {
            OverlayBottomSheet(
                show = true,
                title = kind.title,
                onDismissRequest = { selector = null },
            ) {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    kind.options().forEach { (value, label) ->
                        RadioButtonPreference(
                            title = label,
                            selected = fields[kind.field] == value,
                            onClick = {
                                fields[kind.field] = value
                                selector = null
                            },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

/** 单行/多行输入行：值存于 [fields]，空值也允许提交以便清空服务端字段。 */
@Composable
private fun FieldInput(
    fields: MutableMap<String, String>,
    key: String,
    label: String,
    multiline: Boolean = false,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TextField(
            value = fields[key].orEmpty(),
            onValueChange = { fields[key] = it },
            label = label,
            singleLine = !multiline,
            minLines = if (multiline) 3 else 1,
            maxLines = if (multiline) 5 else 1,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
