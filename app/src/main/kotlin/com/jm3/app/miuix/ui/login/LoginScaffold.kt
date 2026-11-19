package com.jm3.app.miuix.ui.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import com.jm3.core.session.AccountManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** 账号或密码为空时的提示：空判与这句话都只在 [LoginScaffoldState.submit] 里写一次。 */
internal const val LOGIN_BLANK_FIELDS_MESSAGE = "请输入用户名和密码"

/** 提交键的两种文案：在途时同时换字并禁用，避免重复提交。 */
internal const val LOGIN_SUBMIT_LABEL = "登录"
internal const val LOGIN_BUSY_LABEL = "登录中…"

/**
 * 登录页的状态编排：输入、错误文案、在途标记与一次提交的落点都在这里，皮肤只画表单。
 *
 * 两套皮肤原先各自持有一份逐字相同的 username/password/error/busy 与同一段提交顺序
 * （先挡在途、再判空、成功后回退、失败把原因写进 error）。顺序在这里只写一次：
 * 在途判定 → 空判 → 置在途并清错 → 取数 → 置闲 → 成功回退或写错。
 *
 * 取数边界是注入的 [login]（成功返回 null、失败返回一句可展示的提示），
 * 因此这里不认识 [AccountManager]，也不需要协程之外的任何 Android 依赖。
 */
@Stable
internal class LoginScaffoldState internal constructor(
    private val scope: CoroutineScope,
    private val fields: LoginScaffoldFields,
    private val login: suspend (username: String, password: String) -> String?,
    private val onLoggedIn: () -> Unit,
) {
    /** 用户名：皮肤持有输入框只是读写它。 */
    var username: String
        get() = fields.usernameState.value
        set(value) {
            fields.usernameState.value = value
        }

    /** 密码：与用户名同一条读写路径，但不随进程重建保存（沿用原页面语义）。 */
    var password: String
        get() = fields.passwordState.value
        set(value) {
            fields.passwordState.value = value
        }

    /** 展示中的错误文案；null 表示没有错误。 */
    val error: String?
        get() = fields.errorState.value

    /** 提交在途：提交键据此禁用，文案换成 [LOGIN_BUSY_LABEL]。 */
    val busy: Boolean
        get() = fields.busyState.value

    /**
     * 清掉当前错误文案。
     *
     * 「输入即清错」是 COUI 皮肤独有的手感：它在两个输入框的回调里调这里，
     * Miuix 皮肤不调，于是同一条差异留在皮肤侧，不需要在这里长出一个开关。
     */
    fun clearError() {
        if (fields.errorState.value != null) fields.errorState.value = null
    }

    /** 提交一次登录：在途时直接忽略，空输入只提示不动在途标记。 */
    fun submit() {
        if (busy) return
        if (username.isBlank() || password.isBlank()) {
            fields.errorState.value = LOGIN_BLANK_FIELDS_MESSAGE
            return
        }
        fields.busyState.value = true
        fields.errorState.value = null
        scope.launch {
            // 与原先内联实现同序：先置闲再决定回退还是写错，回退期间不会残留「登录中…」
            val failure = login(username.trim(), password)
            fields.busyState.value = false
            if (failure == null) onLoggedIn() else fields.errorState.value = failure
        }
    }
}

/**
 * 承载登录页可变状态的容器。
 *
 * 由 [rememberLoginScaffoldFields] 用 remember/rememberSaveable 建一次，[LoginScaffoldState]
 * 只读不建：状态的生命周期归组合，动作类的生命周期只归 remember 键。
 */
@Stable
internal class LoginScaffoldFields internal constructor(
    internal val usernameState: MutableState<String>,
    internal val passwordState: MutableState<String>,
    internal val errorState: MutableState<String?>,
    internal val busyState: MutableState<Boolean>,
)

/** 用户名跨进程重建保留，其余三项随页面重建（与原登录页的保存语义逐项一致）。 */
@Composable
private fun rememberLoginScaffoldFields(): LoginScaffoldFields {
    val username = rememberSaveable { mutableStateOf("") }
    val password = remember { mutableStateOf("") }
    val error = remember { mutableStateOf<String?>(null) }
    val busy = remember { mutableStateOf(false) }
    return remember(username, password, error, busy) {
        LoginScaffoldFields(
            usernameState = username,
            passwordState = password,
            errorState = error,
            busyState = busy,
        )
    }
}

/**
 * 两套登录皮肤共用的状态入口：remember 状态容器、接线 [AccountManager] 与回退动作。
 *
 * [onBack] 走 [rememberUpdatedState]：编排对象被 remember 固定，但回退动作要跟最新的那个。
 */
@Composable
internal fun rememberLoginScaffoldState(
    account: AccountManager,
    onBack: () -> Unit,
): LoginScaffoldState {
    val fields = rememberLoginScaffoldFields()
    val scope = rememberCoroutineScope()
    val onBackRef = rememberUpdatedState(onBack)
    return remember(account, scope, fields) {
        LoginScaffoldState(
            scope = scope,
            fields = fields,
            login = account::login,
            onLoggedIn = { onBackRef.value() },
        )
    }
}
