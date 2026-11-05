package com.jm3.core.session

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class AuthCallTest {

    /** 可手动放行的闸门：记录放行前后的状态与失效通知次数。 */
    private class FakeGate : StartupGate {
        private val ready = CompletableDeferred<Unit>()
        var expiredCount = 0
            private set
        var readyCount = 0
            private set
        var releaseError: Exception? = null
        var expireError: Exception? = null

        fun release() = ready.complete(Unit)

        override suspend fun awaitReady() {
            readyCount++
            ready.await()
            releaseError?.let { throw it }
        }

        override suspend fun onSessionExpired() {
            expiredCount++
            expireError?.let { throw it }
        }
    }

    /** 只用来断言「业务块确实被交给了注入的调度器」，不真正切换线程。 */
    private class RecordingDispatcher : CoroutineDispatcher() {
        var dispatchCount = 0
            private set

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            dispatchCount++
            block.run()
        }
    }

    @Test
    fun `启动闸门放行前不得执行业务块`() = runBlocking {
        val gate = FakeGate()
        val call = AuthCall(gate, RecordingDispatcher())
        var executed = false
        val job: Job = launch { call.call { executed = true; "value" } }

        withTimeout(2_000) { while (gate.readyCount == 0) kotlinx.coroutines.yield() }
        assertFalse(executed, "闸门未放行时业务块已经执行")

        gate.release()
        job.join()
        assertTrue(executed, "闸门放行后业务块仍未执行")
        assertEquals(0, gate.expiredCount, "成功调用不该通知账号层")
    }

    @Test
    fun `会话失效时通知账号层恰好一次`() = runBlocking {
        val gate = FakeGate()
        val call = AuthCall(gate, RecordingDispatcher())
        gate.release()

        val outcome = call.call<Unit> { throw IllegalStateException("請先登入會員") }

        assertEquals(SessionOutcome.Expired, outcome)
        assertEquals(1, gate.expiredCount, "失效通知次数不对")
    }

    @Test
    fun `普通失败不触碰账号层`() = runBlocking {
        val gate = FakeGate()
        val call = AuthCall(gate, RecordingDispatcher())
        gate.release()

        val outcome = call.call<Unit> { throw IllegalStateException("HTTP 502") }

        assertEquals(listOf("HTTP 502"), (outcome as SessionOutcome.Failed).message.let { listOf(it) })
        assertEquals(0, gate.expiredCount)
    }

    @Test
    fun `取消原样抛出且不通知账号层`() = runBlocking {
        val gate = FakeGate()
        val call = AuthCall(gate, RecordingDispatcher())
        gate.release()

        val thrown = try {
            call.call<Unit> { throw kotlinx.coroutines.CancellationException("cancelled") }
            fail("取消被吞掉了")
        } catch (c: kotlinx.coroutines.CancellationException) {
            c
        }

        assertEquals("cancelled", thrown.message)
        assertEquals(0, gate.expiredCount)
    }

    @Test
    fun `业务块交给注入的调度器执行`() = runBlocking {
        val gate = FakeGate()
        val dispatcher = RecordingDispatcher()
        val call = AuthCall(gate, dispatcher)
        gate.release()

        val outcome = call.call { 42 }

        assertEquals(SessionOutcome.Ok(42), outcome, "业务块的返回值没有原样带回")
        assertEquals(1, dispatcher.dispatchCount, "业务块没有经过注入的调度器")
    }

    @Test
    fun `闸门放行失败按普通失败返回且不执行业务块`() = runBlocking {
        val gate = FakeGate()
        val call = AuthCall(gate, RecordingDispatcher())
        gate.releaseError = IllegalStateException("boom")
        var executed = false
        gate.release()

        val outcome = call.call { executed = true; 1 }

        assertEquals("boom", (outcome as SessionOutcome.Failed).message)
        assertFalse(executed, "闸门失败后业务块仍被执行")
    }

    @Test
    fun `失效处置自己抛异常也仍返回失效`() = runBlocking {
        val gate = FakeGate()
        val call = AuthCall(gate, RecordingDispatcher())
        gate.expireError = IllegalStateException("store broken")
        gate.release()

        // 会话失效在真实链路里是服务端回的 401 业务 JSON，不是界面文案
        val outcome = call.call<Unit> { throw IllegalStateException("""{"code":401,"data":null,"errorMsg":"請先登入會員"}""") }

        assertEquals(SessionOutcome.Expired, outcome)
        assertEquals(1, gate.expiredCount)
    }
}
