package com.jm3.app.miuix.data

import java.io.File
import java.nio.file.Files
import java.util.Collections
import java.util.concurrent.CyclicBarrier
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DomainPoolCacheTest {

    private lateinit var dir: File

    @AfterTest
    fun cleanUp() {
        if (::dir.isInitialized) dir.deleteRecursively()
    }

    @Test
    fun `并发写入不留临时文件且读回完整有效池`() {
        dir = Files.createTempDirectory("jm3-pool-cache").toFile()
        val target = File(dir, "api_domain_pool.txt")
        val cache = FileDomainPoolCache(target)
        val poolA = listOf("a1.example.com", "a2.example.com", "a3.example.com")
        val poolB = listOf("b1.example.com", "b2.example.com")
        val errors = Collections.synchronizedList(mutableListOf<Throwable>())

        repeat(20) {
            val barrier = CyclicBarrier(3)
            val t1 = Thread {
                runCatching {
                    barrier.await()
                    cache.write(poolA)
                }.onFailure { errors += it }
            }
            val t2 = Thread {
                runCatching {
                    barrier.await()
                    cache.write(poolB)
                }.onFailure { errors += it }
            }
            val reader = Thread {
                runCatching {
                    barrier.await()
                    val read = cache.read()
                    assertTrue(
                        read.isEmpty() || read == poolA || read == poolB,
                        "并发读回不得出现截断或混合行：$read",
                    )
                }.onFailure { errors += it }
            }
            t1.start(); t2.start(); reader.start()
            t1.join(); t2.join(); reader.join()
        }

        assertEquals(emptyList(), errors.map { it.toString() })
        val finalRead = cache.read()
        assertTrue(finalRead == poolA || finalRead == poolB)
        val leftovers = dir.listFiles().orEmpty().filter { it.name.endsWith(".tmp") }
        assertEquals(emptyList(), leftovers.map { it.name }, "写入后不得保留 .tmp 临时文件")
    }
}
