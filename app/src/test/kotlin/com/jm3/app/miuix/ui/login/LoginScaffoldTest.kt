package com.jm3.app.miuix.ui.login

import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [LoginScaffoldState] 的纯 JVM 用例：两皮肤登录页共用这一份表单状态与提交编排。
 *
 * 取数边界是注入的挂起函数，所以这里不需要 Android、Miuix 或 Mock 框架。
 */
class LoginScaffoldTest {
    @Test
    fun 初始态是空表单且不在提交中() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val state = state(scope)

        assertEquals("", state.username)
        assertEquals("", state.password)
        assertNull(state.error)
        assertFalse(state.busy)
        scope.cancel()
    }

    @Test
    fun 用户名或密码为空时提示补全且不发起登录() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        var loginCalls = 0
        var backCalls = 0
        val state = state(
            scope,
            username = "alice",
            password = "   ",
            login = { _, _ ->
                loginCalls++
                null
            },
            onLoggedIn = { backCalls++ },
        )

        state.submit()

        assertEquals(0, loginCalls)
        assertEquals(0, backCalls)
        assertEquals(LOGIN_BLANK_FIELDS_MESSAGE, state.error)
        assertFalse(state.busy)
        scope.cancel()
    }

    @Test
    fun 提交前只去掉用户名首尾空格而密码原样送服务端() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        var sentUsername: String? = null
        var sentPassword: String? = null
        val state = state(
            scope,
            username = "  alice  ",
            password = " pa ss ",
            login = { username, password ->
                sentUsername = username
                sentPassword = password
                null
            },
        )

        state.submit()

        assertEquals("alice", sentUsername)
        assertEquals(" pa ss ", sentPassword)
        scope.cancel()
    }

    @Test
    fun 提交期间置忙并清掉上一次的错误() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val gate = CompletableDeferred<String?>()
        val state = state(
            scope,
            username = "alice",
            password = "secret",
            error = "账号或密码错误",
            login = { _, _ -> gate.await() },
        )

        state.submit()

        assertTrue(state.busy)
        assertNull(state.error)

        gate.complete(null)
        testScheduler.advanceUntilIdle()
        assertFalse(state.busy)
        scope.cancel()
    }

    @Test
    fun 登录成功后结束提交态并回退() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        var loginCalls = 0
        var backCalls = 0
        val state = state(
            scope,
            username = "alice",
            password = "secret",
            login = { _, _ ->
                loginCalls++
                null
            },
            onLoggedIn = { backCalls++ },
        )

        state.submit()

        assertEquals(1, loginCalls)
        assertEquals(1, backCalls)
        assertNull(state.error)
        assertFalse(state.busy)
        scope.cancel()
    }

    @Test
    fun 登录失败后把服务端原因写进错误且不回退() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        var backCalls = 0
        val state = state(
            scope,
            username = "alice",
            password = "secret",
            login = { _, _ -> "账号或密码错误" },
            onLoggedIn = { backCalls++ },
        )

        state.submit()

        assertEquals("账号或密码错误", state.error)
        assertEquals(0, backCalls)
        assertFalse(state.busy)
        scope.cancel()
    }

    @Test
    fun 提交在途时重复点登录不会二次发起请求() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val gate = CompletableDeferred<String?>()
        var loginCalls = 0
        val state = state(
            scope,
            username = "alice",
            password = "secret",
            login = { _, _ ->
                loginCalls++
                gate.await()
            },
        )

        state.submit()
        state.submit()

        assertEquals(1, loginCalls)
        assertTrue(state.busy)

        gate.complete("账号或密码错误")
        testScheduler.advanceUntilIdle()
        assertEquals(1, loginCalls)
        assertEquals("账号或密码错误", state.error)
        scope.cancel()
    }

    @Test
    fun 清掉错误后界面不再展示旧提示() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val state = state(scope, username = "alice", password = "secret", error = "账号或密码错误")

        state.clearError()

        assertNull(state.error)
        assertEquals("alice", state.username)
        scope.cancel()
    }

    @Test
    fun 清空密码后再提交时补全提示会覆盖上一次的服务端原因() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val state = state(scope, username = "alice", password = "secret", error = "账号或密码错误")

        state.password = ""
        state.submit()

        assertEquals(LOGIN_BLANK_FIELDS_MESSAGE, state.error)
        scope.cancel()
    }

    @Test
    fun 输入框与按钮文案固定() {
        assertEquals("请输入用户名和密码", LOGIN_BLANK_FIELDS_MESSAGE)
        assertEquals("登录", LOGIN_SUBMIT_LABEL)
        assertEquals("登录中…", LOGIN_BUSY_LABEL)
    }

    private fun state(
        scope: CoroutineScope,
        username: String = "",
        password: String = "",
        error: String? = null,
        busy: Boolean = false,
        login: suspend (String, String) -> String? = { _, _ -> null },
        onLoggedIn: () -> Unit = {},
    ): LoginScaffoldState {
        val fields = LoginScaffoldFields(
            usernameState = mutableStateOf(username),
            passwordState = mutableStateOf(password),
            errorState = mutableStateOf(error),
            busyState = mutableStateOf(busy),
        )
        return LoginScaffoldState(
            scope = scope,
            fields = fields,
            login = login,
            onLoggedIn = onLoggedIn,
        )
    }
}
