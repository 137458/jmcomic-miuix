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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.ui.components.BackButton
import com.jm3.app.miuix.ui.components.ErrorScreen
import com.jm3.app.miuix.ui.components.LoadingScreen
import com.jm3.app.miuix.ui.components.SectionHorizontalPadding
import com.jm3.app.miuix.ui.glass.GlassScaffold
import com.jm3.app.miuix.ui.glass.GlassTopAppBar
import com.jm3.app.miuix.ui.profile.ProfileSelector
import com.jm3.app.miuix.ui.profile.ProfileUiState
import com.jm3.app.miuix.ui.profile.labelFor
import com.jm3.app.miuix.ui.profile.options
import com.jm3.app.miuix.ui.profile.rememberProfileScaffoldState
import com.jm3.app.miuix.ui.route.AppRoutes
import com.jm3.app.miuix.ui.skin.LocalSkin
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

/** 个人资料：只读账号信息 + 全量可编辑字段，保存后以服务端返回为准。 */
@Composable
internal fun ProfileScreen(
    deps: ScreenDeps,
    onBack: () -> Unit,
    nav: AppRoutes,
    snackbarHostState: SnackbarHostState,
) {
    val skin = LocalSkin.current
    // 装载/保存编排与草稿缓冲都来自共享骨架；本文件只剩视觉与选择器弹层
    val state = rememberProfileScaffoldState(deps = deps, snackbarHostState = snackbarHostState)
    val uiState = state.uiState
    val saving = state.saving
    val fields = state.fields
    val profile = state.profile
    var selector by remember { mutableStateOf<ProfileSelector?>(null) }

    GlassScaffold(
        topBar = {
            GlassTopAppBar(
                title = "个人资料",
                // 透明底色：玻璃底色与模糊由 GlassScaffold 统一绘制
                color = Color.Transparent,
                navigationIcon = { BackButton(onBack = onBack) },
                actions = {
                    if (uiState is ProfileUiState.Loaded) {
                        TextButton(
                            text = if (saving) "保存中…" else "保存",
                            onClick = state::save,
                            enabled = !saving,
                        )
                    }
                },
            )
        },
        snackbarHost = { GlassSnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when (val s = uiState) {
            ProfileUiState.Loading -> LoadingScreen(modifier = Modifier.padding(innerPadding))
            is ProfileUiState.Failed -> if (s.sessionExpired) {
                LoginExpiredScreen(
                    onOpenLogin = nav::login,
                    modifier = Modifier.padding(innerPadding),
                )
            } else {
                ErrorScreen(
                    message = s.message,
                    onRetry = { state.load() },
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
                        summary = ProfileSelector.RELATIONS.labelFor(fields[ProfileSelector.RELATIONS.field]),
                        onClick = { selector = ProfileSelector.RELATIONS },
                    )
                    BasicComponent(
                        title = ProfileSelector.SEXUALITY.title,
                        summary = ProfileSelector.SEXUALITY.labelFor(fields[ProfileSelector.SEXUALITY.field]),
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
                        onClick = state::save,
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
