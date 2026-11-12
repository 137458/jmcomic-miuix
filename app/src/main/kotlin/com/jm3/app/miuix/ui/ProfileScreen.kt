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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.failureCopy
import com.jm3.app.miuix.ui.glass.GlassScaffold
import com.jm3.app.miuix.ui.glass.GlassTopAppBar
import com.jm3.app.miuix.ui.skin.LocalSkin
import com.jm3.core.model.UserProfile
import com.jm3.core.session.SessionOutcome
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
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
    /** sessionExpired 区分「重试可能好」和「会话没了」：后者只能去登录，给重试按钮是骗人的。 */
    data class Failed(val message: String, val sessionExpired: Boolean = false) : ProfileUiState
}

/** 个人资料：只读账号信息 + 全量可编辑字段，保存后以服务端返回为准。 */
@Composable
fun ProfileScreen(
    deps: ScreenDeps,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    onOpenLogin: () -> Unit = {},
) {
    val skin = LocalSkin.current
    val user by deps.account.user.collectAsState()
    var state by remember { mutableStateOf<ProfileUiState>(ProfileUiState.Loading) }
    var profile by remember { mutableStateOf<UserProfile?>(null) }
    var saving by remember { mutableStateOf(false) }
    var selector by remember { mutableStateOf<ProfileSelector?>(null) }
    var requestToken by remember { mutableIntStateOf(0) }
    var savedUidToken by rememberSaveable { mutableStateOf(ProfileDraftPolicy.UID_UNSET) }
    var savedDrafts by rememberSaveable(stateSaver = StringMapSaver) {
        mutableStateOf<Map<String, String>>(emptyMap())
    }
    var dirtyKeys by rememberSaveable(stateSaver = StringSetSaver) {
        mutableStateOf<Set<String>>(emptySet())
    }
    // 编辑缓冲：键与 editableFields() 一致，提交时整体回传；未保存的修改跨旋转保留
    val backingFields = remember { mutableStateMapOf<String, String>() }
    val fields = remember {
        trackedProfileFieldMap(backingFields) { key, value ->
            dirtyKeys = dirtyKeys + key
            savedDrafts = savedDrafts + (key to value)
        }
    }
    val scope = rememberCoroutineScope()

    fun applyProfile(next: UserProfile, resetDrafts: Boolean = false) {
        profile = next
        if (resetDrafts) {
            dirtyKeys = emptySet()
            savedDrafts = emptyMap()
        }
        val merged = ProfileDraftPolicy.mergeProfileFields(
            serverFields = next.editableFields(),
            savedDrafts = savedDrafts,
            dirtyKeys = dirtyKeys,
        )
        backingFields.clear()
        backingFields.putAll(merged)
    }

    /** 失效与失败共用一个落点：已读到资料就保留表单，只弹提示；否则整屏化，失效换成「去登录」。 */
    fun applyFailure(outcome: SessionOutcome<*>) {
        val plan = planLoad(
            outcome = outcome,
            hasContent = profile != null,
            failureMessage = "个人资料加载失败",
        )
        if (plan.keepContent) {
            scope.launch { snackbarHostState.showSnackbar(plan.notice.orEmpty()) }
        } else {
            state = ProfileUiState.Failed(
                message = plan.message.orEmpty(),
                sessionExpired = plan.phase == ScreenLoadPhase.Expired,
            )
        }
    }

    fun load(showLoading: Boolean = true) {
        val token = ++requestToken
        val uid = user?.uid
        if (uid == null) {
            state = ProfileUiState.Failed("请先登录后查看个人资料", sessionExpired = true)
            return
        }
        if (showLoading) state = ProfileUiState.Loading
        scope.launch {
            val outcome = deps.authCall.call { deps.gateway.userProfile(uid) }
            if (token != requestToken || user?.uid != uid) return@launch
            when (outcome) {
                is SessionOutcome.Ok -> {
                    applyProfile(outcome.value)
                    state = ProfileUiState.Loaded
                }

                SessionOutcome.Expired -> applyFailure(outcome)

                is SessionOutcome.Failed -> applyFailure(outcome)
            }
        }
    }

    fun save() {
        val uid = user?.uid ?: return
        if (saving) return
        saving = true
        scope.launch {
            val outcome = deps.authCall.call { deps.gateway.editUserProfile(uid, fields.toMap()) }
            saving = false
            if (user?.uid != uid) return@launch
            when (outcome) {
                is SessionOutcome.Ok -> {
                    applyProfile(outcome.value, resetDrafts = true)
                    snackbarHostState.showSnackbar("资料已保存")
                }

                SessionOutcome.Expired -> snackbarHostState.showSnackbar(
                    planLoad(outcome, hasContent = false, failureMessage = "保存失败").message.orEmpty(),
                )

                is SessionOutcome.Failed -> snackbarHostState.showSnackbar("保存失败：" + failureCopy(outcome, "请稍后重试"))
            }
        }
    }

    LaunchedEffect(user?.uid) {
        if (ProfileDraftPolicy.shouldResetOnUidEffect(savedUidToken, user?.uid)) {
            savedUidToken = ProfileDraftPolicy.uidToken(user?.uid)
            backingFields.clear()
            savedDrafts = emptyMap()
            dirtyKeys = emptySet()
            profile = null
        }
        load()
    }

    GlassScaffold(
        topBar = {
            GlassTopAppBar(
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
        snackbarHost = { GlassSnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when (val s = state) {
            ProfileUiState.Loading -> LoadingScreen(modifier = Modifier.padding(innerPadding))
            is ProfileUiState.Failed -> if (s.sessionExpired) {
                LoginExpiredScreen(
                    onOpenLogin = onOpenLogin,
                    modifier = Modifier.padding(innerPadding),
                )
            } else {
                ErrorScreen(
                    message = s.message,
                    onRetry = { load() },
                    modifier = Modifier.padding(innerPadding),
                )
            }
            ProfileUiState.Loaded -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .readableWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(innerPadding),
            ) {
                // SmallTitle 自带 8dp 上间距，再补 8dp 与顶栏拉开呼吸空间
                Spacer(modifier = Modifier.height(8.dp))
                SmallTitle(text = "账号信息")
                Card(cornerRadius = skin.shapes.cardCorner, modifier = Modifier.fillMaxWidth().padding(horizontal = SectionHorizontalPadding)) {
                    BasicComponent(title = "用户名", summary = profile?.username.orEmpty())
                    BasicComponent(title = "邮箱", summary = profile?.email.orEmpty())
                }

                SmallTitle(text = "基本信息")
                Card(cornerRadius = skin.shapes.cardCorner, modifier = Modifier.fillMaxWidth().padding(horizontal = SectionHorizontalPadding)) {
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
                Card(cornerRadius = skin.shapes.cardCorner, modifier = Modifier.fillMaxWidth().padding(horizontal = SectionHorizontalPadding)) {
                    FieldInput(fields, "birthPlace", "出生地")
                    FieldInput(fields, "city", "城市")
                    FieldInput(fields, "country", "国家")
                    FieldInput(fields, "occupation", "职业")
                    FieldInput(fields, "company", "公司")
                    FieldInput(fields, "school", "学校")
                }

                SmallTitle(text = "其他信息")
                Card(cornerRadius = skin.shapes.cardCorner, modifier = Modifier.fillMaxWidth().padding(horizontal = SectionHorizontalPadding)) {
                    FieldInput(fields, "aboutMe", "关于我", multiline = true)
                    FieldInput(fields, "infoHere", "其他信息", multiline = true)
                    FieldInput(fields, "collections", "收藏类别", multiline = true)
                    FieldInput(fields, "ideal", "理想型", multiline = true)
                    FieldInput(fields, "erogenic", "敏感区域", multiline = true)
                    FieldInput(fields, "favorite", "最喜欢", multiline = true)
                    FieldInput(fields, "hate", "最讨厌", multiline = true)
                }

                // 长表单滚动到底部时提供就近保存入口，避免每次都回到标题栏
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = SectionHorizontalPadding, vertical = 16.dp)) {
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
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = SectionHorizontalPadding),
                    )
                }
            }
        }

        val kind = selector
        if (kind != null) {
            OverlayBottomSheet(
                show = true,
                title = kind.title,
                cornerRadius = skin.shapes.sheetCorner,
                onDismissRequest = { selector = null },
            ) {
                Card(cornerRadius = skin.shapes.cardCorner, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
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
