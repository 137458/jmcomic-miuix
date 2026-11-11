// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.page

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
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
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme as CouiKitTheme
import com.itos.coui.topappbar.CouiTopAppBarDefaults
import com.itos.coui.topappbar.LargeTopAppBar
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.ui.GlassSnackbarHost
import com.jm3.app.miuix.ui.ScreenLoadPhase
import com.jm3.app.miuix.ui.coui.component.CouiAppScaffold
import com.jm3.app.miuix.ui.coui.component.CouiAvatar
import com.jm3.app.miuix.ui.coui.component.CouiErrorGroup
import com.jm3.app.miuix.ui.coui.component.CouiLoadingGroup
import com.jm3.app.miuix.ui.coui.component.CouiTopBarBackButton
import com.jm3.app.miuix.ui.coui.component.CouiTopBarTitle
import com.jm3.app.miuix.ui.coui.component.couiPressable
import com.jm3.app.miuix.ui.coui.component.couiTopAppBarColors
import com.jm3.app.miuix.ui.coui.icon.CouiIcons
import com.jm3.app.miuix.ui.coui.theme.CouiStrings
import com.jm3.app.miuix.ui.coui.theme.CouiTheme
import com.jm3.app.miuix.ui.planLoad
import com.jm3.app.miuix.ui.readableWidth
import com.jm3.core.model.UserProfile
import com.jm3.core.session.SessionOutcome
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SnackbarHostState

/** 输入行与卡片同源的圆角：由库的卡片半径派生，避免另写字面量。 */
private val ProfileInputShape = CouiShape(radius = CouiPageDefaults.shape.radius / 2)

/** 资料里只有这两项是固定枚举，其余都是自由文本。 */
private enum class ProfileSelector(val title: String, val field: String) {
    RELATIONS("感情状态", "relations"),
    SEXUALITY("性取向", "sexuality"),
}

private val RelationOptions = listOf(
    "" to "未填写",
    "Single" to "单身",
    "Taken" to "非单身",
    "Open" to "开放关系",
)

private val SexualityOptions = listOf(
    "" to "未填写",
    "Guys" to "男性",
    "Girls" to "女性",
    "Guys + Girls" to "男女皆可",
)

private fun ProfileSelector.options(): List<Pair<String, String>> = when (this) {
    ProfileSelector.RELATIONS -> RelationOptions
    ProfileSelector.SEXUALITY -> SexualityOptions
}

private sealed interface ProfileUiState {
    data object Loading : ProfileUiState
    data object Loaded : ProfileUiState
    data class Failed(val message: String, val sessionExpired: Boolean = false) : ProfileUiState
}

/**
 * COUI 个人资料页：顶部头图卡 + 四组资料行，签名与原页面一致。
 *
 * 资料只从 [ScreenDeps.account] 与网关读写；未登录时不发请求，直接落到「请先登录」态，
 * 并把重新登录的入口交给调用方（[onOpenLogin]）。枚举字段走库的锚点气泡菜单，
 * 自由文本走自绘单行/多行输入行（库没有输入框组件）。
 */
@Composable
fun CouiProfileScreen(
    deps: ScreenDeps,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    onOpenLogin: () -> Unit = {},
) {
    val user by deps.account.user.collectAsState()
    var state by remember { mutableStateOf<ProfileUiState>(ProfileUiState.Loading) }
    var profile by remember { mutableStateOf<UserProfile?>(null) }
    var saving by remember { mutableStateOf(false) }
    val fields = remember { mutableStateMapOf<String, String>() }
    val scope = rememberCoroutineScope()

    fun applyProfile(next: UserProfile) {
        profile = next
        fields.clear()
        fields.putAll(next.editableFields())
    }

    fun applyFailure(outcome: SessionOutcome<*>) {
        val plan = planLoad(outcome, hasContent = profile != null, failureMessage = "个人资料加载失败")
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
        val uid = user?.uid
        if (uid == null) {
            state = ProfileUiState.Failed("请先登录后查看个人资料")
            return
        }
        if (showLoading) state = ProfileUiState.Loading
        scope.launch {
            when (val outcome = deps.authCall.call { deps.gateway.userProfile(uid) }) {
                is SessionOutcome.Ok -> {
                    applyProfile(outcome.value)
                    state = ProfileUiState.Loaded
                }
                else -> applyFailure(outcome)
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
            when (outcome) {
                is SessionOutcome.Ok -> {
                    applyProfile(outcome.value)
                    snackbarHostState.showSnackbar("资料已保存")
                }
                is SessionOutcome.Expired -> snackbarHostState.showSnackbar(
                    planLoad(outcome, hasContent = false, failureMessage = "保存失败").message.orEmpty(),
                )
                is SessionOutcome.Failed -> snackbarHostState.showSnackbar(
                    "保存失败：" + outcome.messageOr(CouiStrings.RetryLater),
                )
            }
        }
    }

    LaunchedEffect(user?.uid) {
        fields.clear()
        profile = null
        load()
    }

    CouiTheme {
        val scrollState = rememberScrollState()
        val scrollBehavior = CouiTopAppBarDefaults.exitUntilCollapsedScrollBehavior()

        Box(modifier = Modifier.fillMaxSize()) {
            CouiAppScaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = {
                    // 顶栏必须写在 Scaffold 的 topBar 槽位：库在那里提供 backdrop，
                    // 自己提到外面会拿不到采样源，直接抛 checkNotNull。
                    LargeTopAppBar(
                        title = { CouiTopBarTitle(text = "个人资料", textAlign = TextAlign.Start) },
                        navigationIcon = { CouiTopBarBackButton(onClick = onBack) },
                        scrollBehavior = scrollBehavior,
                        colors = couiTopAppBarColors(),
                    )
                },
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .readableWidth()
                        .nestedScroll(scrollBehavior.nestedScrollConnection)
                        .couiVerticalScroll(scrollState)
                        .padding(scrollBehavior.state.contentPadding(innerPadding, LocalDensity.current))
                        .consumeWindowInsets(innerPadding),
                ) {
                    when (val current = state) {
                        ProfileUiState.Loading -> CouiLoadingGroup(
                            modifier = Modifier.padding(top = 8.dp),
                            text = "正在加载个人资料…",
                        )
                        is ProfileUiState.Failed -> if (current.sessionExpired) {
                            ProfileReloginGroup(message = current.message, onOpenLogin = onOpenLogin)
                        } else {
                            CouiErrorGroup(
                                message = current.message,
                                onRetry = { load() },
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        ProfileUiState.Loaded -> ProfileLoadedContent(
                            username = user?.username.orEmpty(),
                            email = user?.email.orEmpty(),
                            photoUrl = user?.photoUrl,
                            fields = fields,
                            saving = saving,
                            onSave = ::save,
                        )
                    }
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
    }
}

/** 登录过期时给一条能走出去的入口，比只报错更有用。 */
@Composable
private fun ProfileReloginGroup(message: String, onOpenLogin: () -> Unit) {
    Group(
        modifier = Modifier.padding(top = 8.dp),
        title = "登录已过期",
        footer = message,
    ) {
        Item(
            title = { ProfileRowTitle("重新登录") },
            trailing = {
                Text(
                    text = CouiStrings.OpenLogin,
                    kind = CouiTextKind.PreferenceValue,
                    color = CouiKitTheme.colorScheme.accent,
                    textAlign = TextAlign.End,
                )
            },
            onClick = onOpenLogin,
        )
    }
}

/** 资料正文：头图卡 + 四组字段 + 保存行。 */
@Composable
private fun ProfileLoadedContent(
    username: String,
    email: String,
    photoUrl: String?,
    fields: MutableMap<String, String>,
    saving: Boolean,
    onSave: () -> Unit,
) {
    ProfileHeader(username = username, email = email, photoUrl = photoUrl)

    Group(title = "账号信息") {
        ProfileReadonlyRow(label = "用户名", value = username)
        Divider()
        ProfileReadonlyRow(label = "邮箱", value = email.ifEmpty { "未绑定" })
    }

    Group(title = "基本信息") {
        ProfileFieldRow(fields = fields, key = "nickname", label = "昵称")
        Divider()
        ProfileFieldRow(fields = fields, key = "lastName", label = "姓")
        Divider()
        ProfileFieldRow(fields = fields, key = "firstName", label = "名")
        Divider()
        ProfileFieldRow(fields = fields, key = "birthday", label = "生日", placeholder = "YYYY-MM-DD")
        Divider()
        ProfileChoiceRow(fields = fields, selector = ProfileSelector.RELATIONS)
        Divider()
        ProfileChoiceRow(fields = fields, selector = ProfileSelector.SEXUALITY)
        Divider()
        ProfileFieldRow(fields = fields, key = "website", label = "个人网站", placeholder = "https://")
    }

    Group(title = "位置信息") {
        ProfileFieldRow(fields = fields, key = "birthPlace", label = "出生地")
        Divider()
        ProfileFieldRow(fields = fields, key = "city", label = "城市")
        Divider()
        ProfileFieldRow(fields = fields, key = "country", label = "国家")
        Divider()
        ProfileFieldRow(fields = fields, key = "occupation", label = "职业")
        Divider()
        ProfileFieldRow(fields = fields, key = "company", label = "公司")
        Divider()
        ProfileFieldRow(fields = fields, key = "school", label = "学校")
    }

    Group(title = "其他信息") {
        ProfileFieldRow(fields = fields, key = "aboutMe", label = "关于我", multiline = true)
        Divider()
        ProfileFieldRow(fields = fields, key = "infoHere", label = "其他信息", multiline = true)
        Divider()
        ProfileFieldRow(fields = fields, key = "collections", label = "收藏类别", multiline = true)
        Divider()
        ProfileFieldRow(fields = fields, key = "ideal", label = "理想型", multiline = true)
        Divider()
        ProfileFieldRow(fields = fields, key = "erogenic", label = "敏感区域", multiline = true)
        Divider()
        ProfileFieldRow(fields = fields, key = "favorite", label = "最喜欢", multiline = true)
        Divider()
        ProfileFieldRow(fields = fields, key = "hate", label = "最讨厌", multiline = true)
    }

    Group(title = "保存", footer = if (saving) "正在提交…" else "资料只写入你自己的账号") {
        Item(
            title = {
                Text(
                    text = if (saving) "保存中…" else "保存修改",
                    kind = CouiTextKind.PreferenceTitle,
                    color = CouiKitTheme.colorScheme.accent,
                    textAlign = TextAlign.Start,
                )
            },
            trailing = {
                if (saving) {
                    ProfileBusySpinner()
                } else {
                    Icon(
                        imageVector = CouiIcons.Check,
                        contentDescription = null,
                        modifier = Modifier.size(CouiPageDefaults.iconSize),
                        tint = CouiKitTheme.colorScheme.accent,
                    )
                }
            },
            enabled = !saving,
            onClick = onSave,
        )
    }
}

/** 头图：有头像就铺满，没有就用主题色渐变兜底；底部压一层暗色渐变保证文字可读。 */
@Composable
private fun ProfileHeader(username: String, email: String, photoUrl: String?) {
    val scheme = CouiKitTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CouiPageDefaults.horizontalMargin)
            .padding(top = 8.dp)
            .height(176.dp)
            .clip(CouiShape(radius = 24.dp, cornerWeight = 3f)),
    ) {
        if (!photoUrl.isNullOrBlank()) {
            AsyncImage(
                model = photoUrl,
                // 头图是装饰：昵称与用户名在下方文字里已经播报
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(scheme.accent.copy(alpha = 0.85f), scheme.container),
                        ),
                    ),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.62f)),
                    ),
                ),
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(CouiPageDefaults.rowPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CouiAvatar(url = photoUrl, size = 64.dp, name = username)
            Spacer(Modifier.width(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = username.ifEmpty { "未命名" },
                    kind = CouiTextKind.SectionTitle,
                    color = Color.White,
                    textAlign = TextAlign.Start,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = email.ifEmpty { "未绑定邮箱" },
                    kind = CouiTextKind.PreferenceSummary,
                    color = Color.White.copy(alpha = 0.82f),
                    textAlign = TextAlign.Start,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 只读行：服务端决定的值不给输入口，只摆出来。 */
@Composable
private fun ProfileReadonlyRow(label: String, value: String) {
    Item(
        title = { ProfileRowTitle(label) },
        summary = { ProfileRowSummary(value) },
    )
}

/** 自由文本输入行：Label 在上、输入框在下，多行字段给更高的触区。 */
@Composable
private fun ProfileFieldRow(
    fields: MutableMap<String, String>,
    key: String,
    label: String,
    placeholder: String = "",
    multiline: Boolean = false,
) {
    val scheme = CouiKitTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CouiPageDefaults.rowPadding, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ProfileRowSummary(label)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ProfileInputShape)
                .background(scheme.onSurface.copy(alpha = 0.06f))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            BasicTextField(
                value = fields[key].orEmpty(),
                onValueChange = { fields[key] = it },
                textStyle = CouiKitTheme.typography.body.copy(color = scheme.onSurface),
                cursorBrush = SolidColor(scheme.accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = if (multiline) 64.dp else 22.dp),
                decorationBox = { innerTextField ->
                    Box {
                        if (fields[key].isNullOrEmpty() && placeholder.isNotEmpty()) {
                            Text(
                                text = placeholder,
                                kind = CouiTextKind.Body,
                                color = scheme.onSurfaceVariant.copy(alpha = 0.6f),
                                textAlign = TextAlign.Start,
                            )
                        }
                        innerTextField()
                    }
                },
            )
        }
    }
}

/** 枚举字段行：整行是锚点，点开贴着本行弹出选项菜单。 */
@Composable
private fun ProfileChoiceRow(fields: MutableMap<String, String>, selector: ProfileSelector) {
    val anchor = rememberCouiMenuAnchor()
    var expanded by rememberSaveable { mutableStateOf(false) }
    val options = selector.options()
    val current = fields[selector.field].orEmpty()
    val label = options.firstOrNull { it.first == current }?.second ?: "未填写"
    Box(modifier = Modifier.fillMaxWidth().couiMenuAnchor(anchor)) {
        Item(
            title = { ProfileRowTitle(selector.title) },
            summary = { ProfileRowSummary("点选后随资料一起保存") },
            trailing = { ProfileRowValue(label) },
            onClick = { anchor.open { expanded = true } },
        )
        Menu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            anchor = anchor,
            headerText = selector.title,
            items = options.map { (value, text) ->
                CouiMenuItem(value = value, text = text, selected = value == current)
            },
            onItemClick = { item ->
                expanded = false
                if (item.value != current) fields[selector.field] = item.value
            },
        )
    }
}

/** 库没有进度件：用旋转的刷新图标表达提交中，尺寸与行尾数值对齐。 */
@Composable
private fun ProfileBusySpinner() {
    val scheme = CouiKitTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "profileBusy")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900, easing = LinearEasing)),
        label = "profileBusyAngle",
    )
    Icon(
        imageVector = CouiIcons.Refresh,
        contentDescription = null,
        modifier = Modifier.size(16.dp).graphicsLayer(rotationZ = angle),
        tint = scheme.accent,
    )
}

@Composable
private fun ProfileRowTitle(text: String) {
    Text(
        modifier = Modifier.fillMaxWidth(),
        text = text,
        kind = CouiTextKind.PreferenceTitle,
        textAlign = TextAlign.Start,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun ProfileRowSummary(text: String) {
    Text(
        modifier = Modifier.fillMaxWidth(),
        text = text,
        kind = CouiTextKind.PreferenceSummary,
        color = CouiKitTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Start,
    )
}

@Composable
private fun ProfileRowValue(text: String) {
    Text(
        modifier = Modifier.padding(end = 4.dp),
        text = text,
        kind = CouiTextKind.PreferenceValue,
        color = CouiKitTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.End,
    )
}
