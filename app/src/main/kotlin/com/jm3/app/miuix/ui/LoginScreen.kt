package com.jm3.app.miuix.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.jm3.app.miuix.ui.components.BackButton
import com.jm3.app.miuix.ui.glass.GlassScaffold
import com.jm3.app.miuix.ui.glass.GlassTopAppBar
import com.jm3.app.miuix.ui.login.LOGIN_BUSY_LABEL
import com.jm3.app.miuix.ui.login.LOGIN_SUBMIT_LABEL
import com.jm3.app.miuix.ui.login.rememberLoginScaffoldState
import com.jm3.app.miuix.ui.skin.LocalSkin
import com.jm3.core.session.AccountManager
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun LoginScreen(account: AccountManager, onBack: () -> Unit) {
    val skin = LocalSkin.current
    val login = rememberLoginScaffoldState(account, onBack)

    GlassScaffold(
        topBar = {
            GlassTopAppBar(
                title = "登录",
                // 透明底色：玻璃底色与模糊由 GlassScaffold 统一绘制
                color = Color.Transparent,
                navigationIcon = { BackButton(onBack = onBack) },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .readableWidth()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(cornerRadius = skin.shapes.cardCorner, modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextField(
                        value = login.username,
                        onValueChange = { login.username = it },
                        label = "用户名",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextField(
                        value = login.password,
                        onValueChange = { login.password = it },
                        label = "密码",
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    login.error?.let { message ->
                        Text(
                            text = message,
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.error,
                        )
                    }
                    // 主操作使用 Miuix 主色按钮，与设置页的次级 TextButton 区分层级。
                    Button(
                        onClick = { login.submit() },
                        enabled = !login.busy,
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = if (login.busy) LOGIN_BUSY_LABEL else LOGIN_SUBMIT_LABEL)
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}