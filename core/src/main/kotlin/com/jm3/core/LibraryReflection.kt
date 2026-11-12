package com.jm3.core

import io.github.jukomu.jmcomic.core.client.AbstractJmClient
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Member
import java.lang.reflect.Method

/** 反射成员的类别：字段与方法分开解析，避免拿方法名去查字段。 */
internal enum class LibraryMemberKind { FIELD, METHOD }

/**
 * 库内一条非公开成员的声明。
 *
 * [name] 是库里的真实成员名；[owner] 是声明它的类（方法继承来的成员要用声明类而不是子类去查）；
 * [parameterTypes] 只对方法有意义，用来钉住签名，签名不符同样按缺成员降级。
 */
internal data class LibraryMember(
    val name: String,
    val kind: LibraryMemberKind,
    val owner: Class<*>,
    val parameterTypes: List<Class<*>> = emptyList(),
)

/**
 * 库内反射成员的唯一收口。
 *
 * 上游库把 CookieStore、登录身份、域名管理器与几条 protected 的请求构造入口都收在私有成员里，
 * 恢复会话与预置线路池只能反射读写。成员名、解析、取不到时的降级与调用期异常包装全部集中在这里：
 * 库升级后要核对影响面，只看 [members] 这张表。
 *
 * 任何单个成员缺失都只让对应能力降级（无 CookieStore 兜底、无库内自动重登、不刷新线路池），
 * 不抛异常、不影响登录态判定。日志只写成员名与异常类型，绝不打印成员值。
 */
internal object LibraryReflection {

    private const val TAG = "LibraryReflection"

    private val abstractClient = AbstractJmClient::class.java
    private val apiClient = io.github.jukomu.jmcomic.core.client.impl.JmApiClient::class.java

    /** CookieStore 的持有者，读库内 Cookie 的唯一入口。 */
    val cookieManager = LibraryMember("cookieManager", LibraryMemberKind.FIELD, abstractClient)

    /** 库内用户名缓存：恢复会话必须写，否则库自己判「未登录」。 */
    val cacheUsername = LibraryMember(
        "cacheUsername",
        LibraryMemberKind.METHOD,
        abstractClient,
        listOf(String::class.java),
    )

    /** 是否允许库自己重登：注入会话后打开，登出后关闭。 */
    val autoLoginRefreshAllowed = LibraryMember("autoLoginRefreshAllowed", LibraryMemberKind.FIELD, abstractClient)

    /** 当前登录域，决定库拼请求域名时的首选。 */
    val loginHost = LibraryMember("loginHost", LibraryMemberKind.FIELD, abstractClient)

    /** 库的 protected 方法：用客户端内存密钥加密明文密码。 */
    val encryptPasswordInMemory = LibraryMember(
        "encryptPasswordInMemory",
        LibraryMemberKind.METHOD,
        abstractClient,
        listOf(String::class.java),
    )

    /** 库内内存密码（已加密），有它库才会自己重登。 */
    val encryptedPassword = LibraryMember("encryptedPassword", LibraryMemberKind.FIELD, abstractClient)

    /** 库的 protected 方法：造一个带公共查询参数的 HttpUrl.Builder。 */
    val newHttpUrlBuilder = LibraryMember("newHttpUrlBuilder", LibraryMemberKind.METHOD, abstractClient)

    /** 库的 protected 方法：按 App 请求头发一次 GET，连载列表用它补齐查询参数。 */
    val executeGetRequest = LibraryMember(
        "executeGetRequest",
        LibraryMemberKind.METHOD,
        apiClient,
        listOf(okhttp3.HttpUrl::class.java, String::class.java),
    )

    /** 域名管理器：预置、读取与替换线路池都靠它。 */
    val domainManager = LibraryMember("domainManager", LibraryMemberKind.FIELD, abstractClient)

    /** 库的 protected 方法：造探活器；取不到就整段跳过线路池刷新。 */
    val createDomainProbe = LibraryMember("createDomainProbe", LibraryMemberKind.METHOD, abstractClient)

    /** 全部依赖成员，顺序与使用顺序一致。库升级后的影响面就是这张表。 */
    val members: List<LibraryMember> = listOf(
        cookieManager,
        cacheUsername,
        autoLoginRefreshAllowed,
        loginHost,
        encryptPasswordInMemory,
        encryptedPassword,
        newHttpUrlBuilder,
        executeGetRequest,
        domainManager,
        createDomainProbe,
    )

    /** 按声明解析成员；缺成员同样是 null 加一条日志。自检与调用方共用同一条解析路径。 */
    fun resolve(spec: LibraryMember): Member? = when (spec.kind) {
        LibraryMemberKind.FIELD -> fieldOf(spec.owner, spec.name)
        LibraryMemberKind.METHOD -> methodOf(spec.owner, spec.name, *spec.parameterTypes.toTypedArray())
    }

    /** 按类与名字取一个可写字段；取不到时记录成员名与异常类型并返回 null。 */
    fun fieldOf(owner: Class<*>, name: String): Field? = member(name) {
        owner.getDeclaredField(name).apply { isAccessible = true }
    }

    /** 按类、名字与参数表取一个可调用方法；签名不符同样按缺成员降级。 */
    fun methodOf(owner: Class<*>, name: String, vararg parameterTypes: Class<*>): Method? = member(name) {
        owner.getDeclaredMethod(name, *parameterTypes).apply { isAccessible = true }
    }

    /**
     * 统一降级原语：任何反射失败都折成 null 加一条事实日志。
     *
     * 只报成员名与异常类，不打印成员值（凭据与 Cookie 都可能路过这些成员）。
     */
    fun <T> member(memberName: String, block: () -> T): T? =
        runCatching(block).getOrElse { failure ->
            Jm3Log.warn(TAG, "库内反射成员 " + memberName + " 取不到（" + failure.javaClass.name + "），相关能力降级")
            null
        }

    /** 调用成员：库方法自己抛的异常原样上抛（调用方要靠它判会话失效），反射层不可访问才降级。 */
    fun <T> invokeOrThrow(memberName: String, block: () -> T): T? =
        try {
            block()
        } catch (e: InvocationTargetException) {
            throw e.targetException ?: e
        } catch (e: IllegalAccessException) {
            Jm3Log.warn(TAG, "调用库内反射成员 " + memberName + " 失败（" + e.javaClass.name + "），相关能力降级")
            null
        }

    /**
     * 调用成员：任何反射异常都降级为 null，只留一条带成员名与异常类型的事实日志。
     *
     * 除 InvocationTargetException 与 IllegalAccessException 外还要接住 IllegalArgumentException：
     * 接收者或参数与声明不匹配（把别处取到的方法当成库成员调用）同样是反射失配，
     * 它必须和缺成员一样降级，否则异常会穿到调用方，把「能力降级」变成「整条链路崩溃」。
     */
    fun <T> invokeOrNull(memberName: String, block: () -> T): T? =
        try {
            block()
        } catch (e: InvocationTargetException) {
            degrade(memberName, e.targetException ?: e)
            null
        } catch (e: IllegalAccessException) {
            degrade(memberName, e)
            null
        } catch (e: IllegalArgumentException) {
            degrade(memberName, e)
            null
        }

    private fun degrade(memberName: String, failure: Throwable) {
        Jm3Log.warn(TAG, "调用库内反射成员 " + memberName + " 失败（" + failure.javaClass.name + "），相关能力降级")
    }

    // 十个解析句柄：惰性只解析一次，失败就是长期降级（不会反复反射，也不会重复刷日志）。

    val cookieManagerField: Field? by lazy { fieldOf(cookieManager.owner, cookieManager.name) }

    val cacheUsernameMethod: Method? by lazy {
        methodOf(
            cacheUsername.owner,
            cacheUsername.name,
            *cacheUsername.parameterTypes.toTypedArray(),
        )
    }

    val autoLoginRefreshAllowedField: Field? by lazy {
        fieldOf(autoLoginRefreshAllowed.owner, autoLoginRefreshAllowed.name)
    }

    val loginHostField: Field? by lazy { fieldOf(loginHost.owner, loginHost.name) }

    val encryptPasswordInMemoryMethod: Method? by lazy {
        methodOf(
            encryptPasswordInMemory.owner,
            encryptPasswordInMemory.name,
            *encryptPasswordInMemory.parameterTypes.toTypedArray(),
        )
    }

    val encryptedPasswordField: Field? by lazy { fieldOf(encryptedPassword.owner, encryptedPassword.name) }

    val newHttpUrlBuilderMethod: Method? by lazy { resolve(newHttpUrlBuilder) as? Method }

    val executeGetRequestMethod: Method? by lazy {
        resolve(executeGetRequest) as? Method
    }

    val domainManagerField: Field? by lazy { resolve(domainManager) as? Field }

    val domainProbeMethod: Method? by lazy { resolve(createDomainProbe) as? Method }
}
