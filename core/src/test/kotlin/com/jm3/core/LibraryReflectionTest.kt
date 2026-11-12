package com.jm3.core

import io.github.jukomu.jmcomic.core.client.AbstractJmClient
import io.github.jukomu.jmcomic.core.client.impl.JmApiClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 库内反射成员是 core 里唯一的跨版本脆弱点：库升级改名或改签名时，缺成员只降级、不抛异常，
 * 因此「表里有哪几条」和「降级时留下什么」都必须由测试钉住，否则只能等到线上登录态静默失效才发现。
 */
class LibraryReflectionTest {

    private fun captureWarnings(block: () -> Unit): List<String> {
        val originalSink = Jm3Log.sink
        val warnings = mutableListOf<String>()
        Jm3Log.sink = { tag, message, _ -> warnings += tag + ":" + message }
        try {
            block()
        } finally {
            Jm3Log.sink = originalSink
        }
        return warnings
    }

    @Test
    fun `成员表恰好十条且名字与类别固定`() {
        assertEquals(10, LibraryReflection.members.size)
        assertEquals(
            setOf(
                "cookieManager",
                "cacheUsername",
                "autoLoginRefreshAllowed",
                "loginHost",
                "encryptPasswordInMemory",
                "encryptedPassword",
                "newHttpUrlBuilder",
                "executeGetRequest",
                "domainManager",
                "createDomainProbe",
            ),
            LibraryReflection.members.map { it.name }.toSet(),
        )
        assertEquals(5, LibraryReflection.members.count { it.kind == LibraryMemberKind.FIELD })
        assertEquals(5, LibraryReflection.members.count { it.kind == LibraryMemberKind.METHOD })
        // 连载接口声明在子类，其余九条都在 AbstractJmClient。
        assertEquals(
            listOf("executeGetRequest"),
            LibraryReflection.members.filter { it.owner == JmApiClient::class.java }.map { it.name },
        )
        assertEquals(9, LibraryReflection.members.count { it.owner == AbstractJmClient::class.java })
    }

    @Test
    fun `成员表逐条能在库里解析到且可访问`() {
        LibraryReflection.members.forEach { spec ->
            val resolved = assertNotNull(
                LibraryReflection.resolve(spec),
                "成员表里的 " + spec.name + " 应能在 " + spec.owner.name + " 上解析到",
            )
            assertEquals(spec.name, resolved.name)
            assertEquals(true, (resolved as? java.lang.reflect.AccessibleObject)?.isAccessible)
        }
    }

    @Test
    fun `方法成员的签名与库一致`() {
        assertEquals(listOf(String::class.java), LibraryReflection.cacheUsername.parameterTypes)
        assertEquals(listOf(String::class.java), LibraryReflection.encryptPasswordInMemory.parameterTypes)
        assertEquals(
            listOf(okhttp3.HttpUrl::class.java, String::class.java),
            LibraryReflection.executeGetRequest.parameterTypes,
        )
        assertEquals(emptyList(), LibraryReflection.newHttpUrlBuilder.parameterTypes)
        assertEquals(emptyList(), LibraryReflection.createDomainProbe.parameterTypes)
    }

    @Test
    fun `缺失字段降级返回 null 并留下成员名与异常类`() {
        var resolved: java.lang.reflect.Field? = null
        val warnings = captureWarnings {
            resolved = LibraryReflection.fieldOf(Any::class.java, "definitelyMissingField")
        }

        assertNull(resolved)
        assertEquals(1, warnings.size)
        assertEquals(true, warnings.single().contains("definitelyMissingField"))
        assertEquals(true, warnings.single().contains("NoSuchFieldException"))
    }

    @Test
    fun `缺失方法降级返回 null 并留下成员名与异常类`() {
        var resolved: java.lang.reflect.Method? = null
        val warnings = captureWarnings {
            resolved = LibraryReflection.methodOf(Any::class.java, "cacheUsername", String::class.java)
        }

        assertNull(resolved)
        assertEquals(1, warnings.size)
        assertEquals(true, warnings.single().contains("cacheUsername"))
        assertEquals(true, warnings.single().contains("NoSuchMethodException"))
    }

    private class ThrowingTarget {
        @Suppress("unused")
        fun broken(): okhttp3.HttpUrl.Builder = throw IllegalStateException("builder failed")
    }

    @Test
    fun `调用成员自己抛异常时降级返回 null 并报告目标异常类`() {
        val method = ThrowingTarget::class.java.getDeclaredMethod("broken")
        var result: okhttp3.HttpUrl.Builder? = null
        val warnings = captureWarnings {
            result = LibraryReflection.invokeOrNull(LibraryReflection.newHttpUrlBuilder.name) {
                method.invoke(ThrowingTarget()) as? okhttp3.HttpUrl.Builder
            }
        }

        assertNull(result)
        assertEquals(1, warnings.size)
        assertEquals(true, warnings.single().contains("newHttpUrlBuilder"))
        assertEquals(true, warnings.single().contains("IllegalStateException"))
    }

    @Test
    fun `invokeOrThrow 把目标方法自己的异常原样抛出`() {
        val method = ThrowingTarget::class.java.getDeclaredMethod("broken")

        val failure = kotlin.runCatching {
            LibraryReflection.invokeOrThrow(LibraryReflection.newHttpUrlBuilder.name) {
                method.invoke(ThrowingTarget())
            }
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException, "应抛库方法自己的异常，实际为 " + failure)
        assertEquals("builder failed", failure?.message)
    }

    @Test
    fun `invokeOrNull 遇到接收者类型不符时降级并记录成员名`() {
        val lengthMethod = String::class.java.getDeclaredMethod("length")
        var result: Any? = null
        val warnings = captureWarnings {
            result = LibraryReflection.invokeOrNull("createDomainProbe") { lengthMethod.invoke(Any()) }
        }

        assertNull(result)
        assertEquals(1, warnings.size)
        assertEquals(true, warnings.single().contains("createDomainProbe"))
        assertEquals(true, warnings.single().contains("IllegalArgumentException"))
    }

    @Test
    fun `invokeOrThrow 遇到不可访问成员时降级并记录成员名`() {
        val cloneMethod = Any::class.java.getDeclaredMethod("clone")
        var result: Any? = null
        val warnings = captureWarnings {
            result = LibraryReflection.invokeOrThrow("clone") { cloneMethod.invoke(Any()) }
        }

        assertNull(result)
        assertEquals(1, warnings.size)
        assertEquals(true, warnings.single().contains("clone"))
        assertEquals(true, warnings.single().contains("IllegalAccessException"))
    }
}
