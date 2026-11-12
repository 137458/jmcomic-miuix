// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.page

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.itos.coui.dialog.CouiDialogDefaults
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.layout.Divider
import com.itos.coui.layout.Footer
import com.itos.coui.layout.Group
import com.itos.coui.layout.Item
import com.itos.coui.scroll.rememberCouiOverscrollEffect
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme as CouiKitTheme
import com.jm3.app.miuix.ui.coui.component.CouiBodyText
import com.jm3.app.miuix.ui.coui.component.CouiPrimaryActionButton
import com.jm3.app.miuix.ui.coui.component.CouiScreenScaffold
import com.jm3.app.miuix.ui.coui.component.rememberCouiBodyTextStyle
import com.jm3.app.miuix.ui.coui.theme.CouiTheme
import com.jm3.app.miuix.ui.readableWidth
import com.jm3.app.miuix.ui.skin.LocalSkin
import com.jm3.core.session.AccountManager
import kotlinx.coroutines.launch

/**
 * COUI 登录页：沿用 [AccountManager.login] 会话入口与错误回传约定，
 * 使用分组输入框 + 主色胶囊动作键呈现 ColorOS 风格表单。
 */
@Composable
internal fun CouiLoginScreen(
    account: AccountManager,
    onBack: () -> Unit,
) {
    var username by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun submit() {
        if (busy) return
        if (username.isBlank() || password.isBlank()) {
            error = "请输入用户名和密码"
            return
        }
        busy = true
        error = null
        scope.launch {
            val failure = account.login(username.trim(), password)
            busy = false
            if (failure == null) onBack() else error = failure
        }
    }

    CouiTheme {
        val errorColor = CouiDialogDefaults.themeColors(CouiKitTheme.isDark).destructive
        val overscroll = rememberCouiOverscrollEffect()

        CouiScreenScaffold(
            title = "登录",
            onBack = onBack,
        ) { contentPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .readableWidth()
                    .consumeWindowInsets(contentPadding),
                contentPadding = contentPadding,
                overscrollEffect = overscroll,
            ) {
                item(key = "coui-login-form") {
                    Group(
                        title = "账号信息",
                        modifier = Modifier.padding(top = CouiPageDefaults.groupSpacing),
                    ) {
                        Item(
                            title = {
                                Text(
                                    text = "用户名",
                                    kind = CouiTextKind.ItemTitle,
                                    textAlign = TextAlign.Start,
                                )
                            },
                            summary = {
                                CouiLoginField(
                                    value = username,
                                    onValueChange = {
                                        username = it
                                        if (error != null) error = null
                                    },
                                    placeholder = "请输入用户名",
                                    label = "用户名",
                                    enabled = !busy,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                )
                            },
                        )
                        Divider()
                        Item(
                            title = {
                                Text(
                                    text = "密码",
                                    kind = CouiTextKind.ItemTitle,
                                    textAlign = TextAlign.Start,
                                )
                            },
                            summary = {
                                CouiLoginField(
                                    value = password,
                                    onValueChange = {
                                        password = it
                                        if (error != null) error = null
                                    },
                                    placeholder = "请输入密码",
                                    label = "密码",
                                    enabled = !busy,
                                    visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Password,
                                        imeAction = ImeAction.Done,
                                    ),
                                    keyboardActions = KeyboardActions(onDone = { submit() }),
                                )
                            },
                        )
                    }
                }

                error?.let { message ->
                    item(key = "coui-login-error") {
                        Footer(
                            text = message,
                            color = errorColor,
                        )
                    }
                }

                item(key = "coui-login-submit") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = CouiPageDefaults.horizontalMargin,
                                vertical = CouiPageDefaults.groupSpacing,
                            ),
                    ) {
                        CouiPrimaryActionButton(
                            title = if (busy) "登录中…" else "登录",
                            enabled = !busy,
                            shape = CouiCapsuleShape,
                            onClick = ::submit,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CouiLoginField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    label: String,
    enabled: Boolean,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val scheme = CouiKitTheme.colorScheme
    val shape = RoundedCornerShape(LocalSkin.current.shapes.controlCorner)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(shape)
            .background(scheme.onSurface.copy(alpha = 0.06f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            textStyle = rememberCouiBodyTextStyle().copy(color = scheme.onSurface),
            cursorBrush = SolidColor(scheme.accent),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = label },
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        CouiBodyText(
                            text = placeholder,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Start,
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}
