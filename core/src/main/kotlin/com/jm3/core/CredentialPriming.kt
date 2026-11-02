package com.jm3.core

/**
 * 把登录凭据交回网关、让库内自动重登生效的能力。
 *
 * 库只在内存里还留着密码时才会在会话失效后自行重登并重放请求
 * （JmApiClient.shouldRefreshLogin），而那段内存每次冷启动必为空。
 * 从本地凭据恢复会话时补喂一次密码，页面就只需处理真实错误。
 */
interface CredentialPriming {

    /** 把 [username] / [password] 写进网关的内存登录态；实现方不得持久化明文。 */
    fun primeCredentials(username: String, password: String)
}
