package com.jm3.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LoginExpiredTest {

    @Test
    fun `401 且要求先登入会员时命中`() {
        assertTrue(isSessionExpired("""{"code":401,"data":"abc","errorMsg":"請先登入會員"}"""))
    }

    @Test
    fun `401 且提示尚未登入时命中`() {
        assertTrue(isSessionExpired("""{"code":401,"data":"{}","errorMsg":"尚未登入"}"""))
    }

    @Test
    fun `只有业务码 401 的 JSON 也命中`() {
        // 边界：没有 errorMsg，只能靠业务码判断
        assertTrue(isSessionExpired("""{"code":401,"data":""}"""))
        // 边界：值被写成字符串
        assertTrue(isSessionExpired("""{"code": "401","data":""}"""))
    }

    @Test
    fun `只剩数字 401 的文案也命中`() {
        assertTrue(isSessionExpired("401"))
        assertTrue(isSessionExpired(" 401 "))
    }

    @Test
    fun `简体登录提示命中`() {
        assertTrue(isSessionExpired("请先登录后再操作"))
    }

    @Test
    fun `500 错误 JSON 不命中`() {
        assertFalse(isSessionExpired("""{"code":500,"data":"","errorMsg":"伺服器錯誤"}"""))
    }

    @Test
    fun `401 开头的其它业务码不命中`() {
        // 边界：4010 不是 401
        assertFalse(isSessionExpired("""{"code":4010,"errorMsg":"參數錯誤"}"""))
    }

    @Test
    fun `空文案不命中`() {
        assertFalse(isSessionExpired(null))
        assertFalse(isSessionExpired(""))
        assertFalse(isSessionExpired("   "))
    }

    @Test
    fun `普通网络错误不命中`() {
        assertFalse(isSessionExpired("timeout"))
        assertFalse(isSessionExpired("Network is unreachable"))
        assertFalse(isSessionExpired("java.net.SocketTimeoutException: failed to connect"))
    }
}
