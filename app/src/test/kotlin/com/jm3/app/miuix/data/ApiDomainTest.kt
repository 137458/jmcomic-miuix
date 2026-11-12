package com.jm3.app.miuix.data

import android.content.Context
import android.content.ContextWrapper
import com.jm3.core.Jm3Gateway
import java.io.File
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class ApiDomainTest {

    private lateinit var tempDir: File

    @AfterTest
    fun cleanUp() {
        if (::tempDir.isInitialized) tempDir.deleteRecursively()
    }

    private fun fakeContext(): Context {
        tempDir = Files.createTempDirectory("jm3-api-domain").toFile()
        return object : ContextWrapper(null) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = tempDir
        }
    }

    private fun fakeGateway(onClose: () -> Unit): Jm3Gateway =
        Proxy.newProxyInstance(
            Jm3Gateway::class.java.classLoader,
            arrayOf(Jm3Gateway::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "close" -> {
                    onClose()
                    Unit
                }
                "toString" -> "FakeGateway"
                "hashCode" -> 0
                "equals" -> false
                else -> error("Unexpected call: ${method.name}")
            }
        } as Jm3Gateway

    @Test
    fun `并发切换线路串行执行onSwitched且在迁移完成前不关闭旧网关`() = runTest {
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val closedOrder = mutableListOf<String>()
        val createdThreads = mutableListOf<String>()
        val domain = ApiDomain(fakeContext(), scope) { domains, _, _ ->
            createdThreads += Thread.currentThread().name
            val label = domains.joinToString(",")
            fakeGateway { closedOrder += label }
        }

        val firstSwitchedEntered = CompletableDeferred<Unit>()
        val releaseFirstSwitch = CompletableDeferred<Unit>()
        val activeCallbacks = AtomicInteger(0)
        val maxConcurrentCallbacks = AtomicInteger(0)

        val job1 = launch {
            domain.applyDomains(listOf("a.example.com")) {
                val now = activeCallbacks.incrementAndGet()
                maxConcurrentCallbacks.updateAndGet { maxOf(it, now) }
                firstSwitchedEntered.complete(Unit)
                releaseFirstSwitch.await()
                // 迁移期间旧网关（此次为 null，不涉及）与当前网关都不得被并发关闭
                activeCallbacks.decrementAndGet()
            }
        }
        runCurrent()
        firstSwitchedEntered.await()

        val job2 = launch {
            domain.applyDomains(listOf("b.example.com")) {
                val now = activeCallbacks.incrementAndGet()
                maxConcurrentCallbacks.updateAndGet { maxOf(it, now) }
                activeCallbacks.decrementAndGet()
            }
        }
        runCurrent()
        // 第一次切换的 onSwitched 尚未放行，第二次切换不得提前进入并关闭 a.example.com
        assertEquals(emptyList(), closedOrder, "第一次切换未完成时不得关闭旧网关")

        releaseFirstSwitch.complete(Unit)
        advanceUntilIdle()
        job1.join()
        job2.join()

        assertEquals(1, maxConcurrentCallbacks.get(), "onSwitched 必须严格串行执行")
        assertEquals(listOf("a.example.com"), closedOrder, "第二次切换完成后才关闭上一代网关")
        assertTrue(createdThreads.isNotEmpty())
        scope.cancel()
    }
}
