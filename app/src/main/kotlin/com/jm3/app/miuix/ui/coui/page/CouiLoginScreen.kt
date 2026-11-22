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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.jm3.app.miuix.ui.coui.component.CouiBodyText
import com.jm3.app.miuix.ui.coui.component.CouiLayoutTokens
import com.jm3.app.miuix.ui.coui.component.CouiPageScaffold
import com.jm3.app.miuix.ui.coui.component.CouiPrimaryActionButton
import com.jm3.app.miuix.ui.coui.component.couiPageScroll
import com.jm3.app.miuix.ui.coui.component.isCouiWideWindow
import com.jm3.app.miuix.ui.coui.component.rememberCouiBodyTextStyle
import com.jm3.app.miuix.ui.coui.theme.CouiTheme
import com.jm3.app.miuix.ui.login.LOGIN_BUSY_LABEL
import com.jm3.app.miuix.ui.login.LOGIN_SUBMIT_LABEL
import com.jm3.app.miuix.ui.login.rememberLoginScaffoldState
import com.jm3.app.miuix.ui.readableWidth
import com.jm3.app.miuix.ui.skin.LocalSkin
import com.jm3.core.session.AccountManager
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardListGroup
import io.github.suqi8.coui.kmp.preference.PagerFooterPreference
import io.github.suqi8.coui.kmp.preference.PreferenceCategory
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.theme.COUIRoundCorner
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.rememberCOUIFlingBehavior

/**
 * COUI 登录页：沿用 [AccountManager.login] 会话入口与错误回传约定，
 * 使用分组输入框 + 主色胶囊动作键呈现 ColorOS 风格表单。
 */
@Composable
internal fun CouiLoginScreen(
    account: AccountManager,
    onBack: () -> Unit,
) {
    val login = rememberLoginScaffoldState(account, onBack)

    CouiTheme {
        val errorColor = COUITheme.colorScheme.error

        CouiPageScaffold(
            title = "登录",
            onBack = onBack,
            wide = isCouiWideWindow(),
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
                item(key = "coui-login-form") {
                    PreferenceCategory(title = "账号信息")
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = CouiLayoutTokens.HorizontalMargin)
                            .padding(bottom = CouiLayoutTokens.GroupSpacing),
                    ) {
                        CardListGroup {
                            item(key = "username") {
                                BasicComponent(
                                    title = "用户名",
                                    bottomAction = {
                                        CouiLoginField(
                                            value = login.username,
                                            onValueChange = {
                                                login.username = it
                                                login.clearError()
                                            },
                                            placeholder = "请输入用户名",
                                            label = "用户名",
                                            enabled = !login.busy,
                                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                        )
                                    },
                                )
                            }
                            item(key = "password") {
                                BasicComponent(
                                    title = "密码",
                                    bottomAction = {
                                        CouiLoginField(
                                            value = login.password,
                                            onValueChange = {
                                                login.password = it
                                                login.clearError()
                                            },
                                            placeholder = "请输入密码",
                                            label = "密码",
                                            enabled = !login.busy,
                                            visualTransformation = PasswordVisualTransformation(),
                                            keyboardOptions = KeyboardOptions(
                                                keyboardType = KeyboardType.Password,
                                                imeAction = ImeAction.Done,
                                            ),
                                            keyboardActions = KeyboardActions(onDone = { login.submit() }),
                                        )
                                    },
                                )
                            }
                        }
                    }
                }

                login.error?.let { message ->
                    item(key = "coui-login-error") {
                        PagerFooterPreference(
                            summary = message,
                            summaryColor = errorColor,
                            withExtraMarginBottom = false,
                        )
                    }
                }

                item(key = "coui-login-submit") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = CouiLayoutTokens.HorizontalMargin,
                                vertical = CouiLayoutTokens.GroupSpacing,
                            ),
                    ) {
                        CouiPrimaryActionButton(
                            title = if (login.busy) LOGIN_BUSY_LABEL else LOGIN_SUBMIT_LABEL,
                            enabled = !login.busy,
                            shape = CircleShape,
                            onClick = { login.submit() },
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
    val scheme = COUITheme.colorScheme
    val corner = LocalSkin.current.shapes.controlCorner
    val shape = remember(corner) { SquircleShape(corner, COUIRoundCorner.Normal.Weight) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(scheme.labelPrimary.copy(alpha = 0.06f))
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
            textStyle = rememberCouiBodyTextStyle().copy(color = scheme.labelPrimary),
            cursorBrush = SolidColor(scheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = label },
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        CouiBodyText(
                            text = placeholder,
                            color = scheme.labelSecondary,
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
