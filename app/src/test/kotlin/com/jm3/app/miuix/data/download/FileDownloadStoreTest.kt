package com.jm3.app.miuix.data.download

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.assertFailsWith
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 文件落盘契约：{root}/{photoId}/{index:05}.jpg，页号从 1 起（FileDownloadStore.kt:7、:13）。
 *
 * 这里只钉接口行为：路径布局、读写往返、缺文件与空文件的区别、页号列表的筛选与排序。
 * 实现细节（先写 .tmp 再 rename，FileDownloadStore.kt:20-25）不作为断言对象，只验证它不留下可见残留。
 * 覆盖写受接口契约约束：目标已存在时必须原子替换，且不留临时文件（DownloadStore.write KDoc）。
 */
class FileDownloadStoreTest {

    private lateinit var root: File

    private fun store(): FileDownloadStore {
        root = Files.createTempDirectory("jm3-file-store").toFile()
        return FileDownloadStore(root)
    }

    @AfterTest
    fun cleanUp() {
        if (::root.isInitialized) root.deleteRecursively()
    }

    @Test
    fun `页号按五位补零落在以photoId命名的目录下`() {
        val store = store()
        store.write("1051", 7, byteArrayOf(1, 2, 3))

        val expected = File(root, "1051/00007.jpg")
        assertTrue(expected.isFile, "落盘路径应为 {root}/{photoId}/{index:05}.jpg")
        assertContentEquals(byteArrayOf(1, 2, 3), expected.readBytes())
    }

    @Test
    fun `写入后按页号读回原始字节`() {
        val store = store()
        val payload = ByteArray(256) { (it % 251).toByte() }
        store.write("album-9", 1, payload)

        assertContentEquals(payload, store.read("album-9", 1))
        assertTrue(store.exists("album-9", 1))
    }

    @Test
    fun `未写入的页读回null且判定为不存在`() {
        val store = store()

        assertNull(store.read("no-such-photo", 1))
        assertFalse(store.exists("no-such-photo", 1))
    }

    @Test
    fun `空字节页与缺页可区分`() {
        val store = store()
        store.write("empty", 2, ByteArray(0))

        assertTrue(store.exists("empty", 2))
        val read = store.read("empty", 2)
        assertTrue(read != null && read.isEmpty(), "空文件应读回空数组，而不是 null")
        assertNull(store.read("empty", 3))
    }

    @Test
    fun `写入后目录里只留页文件不留临时文件`() {
        val store = store()
        store.write("p", 1, byteArrayOf(1, 2, 3))

        val names = File(root, "p").listFiles().orEmpty().map { it.name }.sorted()
        assertEquals(listOf("00001.jpg"), names, "落盘不应留下 .tmp 残留")
    }

    @Test
    fun `覆盖写替换旧内容且不留临时文件`() {
        val store = store()
        store.write("p", 1, byteArrayOf(9, 9))
        store.write("p", 1, byteArrayOf(7))

        assertContentEquals(byteArrayOf(7), store.read("p", 1))
        assertEquals(listOf(1), store.files("p"))
        val names = File(root, "p").listFiles().orEmpty().map { it.name }.sorted()
        assertEquals(listOf("00001.jpg"), names, "覆盖写不应留下 .tmp 残留")
    }

    @Test
    fun `删除章节后重写替换旧内容`() {
        val store = store()
        store.write("p", 1, "旧页".encodeToByteArray())

        store.delete("p")
        store.write("p", 1, "新页".encodeToByteArray())

        assertContentEquals("新页".encodeToByteArray(), store.read("p", 1))
        assertEquals(listOf(1), store.files("p"))
    }

    @Test
    fun `写入失败时不留临时文件`() {
        val store = store()
        val dir = File(root, "p").apply { mkdirs() }
        // 让最后一步替换目标必然失败：目标路径被非空目录占住，文件无法替换目录。
        File(dir, "00001.jpg").apply { mkdirs() }
        File(dir, "00001.jpg/keep").writeText("x")

        assertFailsWith<IllegalStateException> { store.write("p", 1, byteArrayOf(1, 2, 3)) }
        val leftovers = dir.listFiles().orEmpty().filter { it.name.endsWith(".tmp") }
        assertEquals(emptyList(), leftovers.map { it.name }, "写入失败后不许留下临时文件")
        assertFalse(store.exists("p", 1), "失败时不得把半截内容当成已落盘")
        assertEquals(emptyList<Int>(), store.files("p"))
    }

    @Test
    fun `同页并发写不会因临时文件冲突失败或写出混合内容`() {
        val store = store()
        val first = ByteArray(1 shl 20) { 1 }
        val second = ByteArray(1 shl 20) { 2 }
        val errors = java.util.Collections.synchronizedList(mutableListOf<Throwable>())

        repeat(10) {
            val barrier = java.util.concurrent.CyclicBarrier(2)
            val workers = listOf(first, second).map { payload ->
                Thread {
                    try {
                        barrier.await()
                        store.write("p", 1, payload)
                    } catch (error: Throwable) {
                        errors += error
                    }
                }.apply { start() }
            }
            workers.forEach { it.join() }

            val stored = assertNotNull(store.read("p", 1), "并发写后页文件必须存在")
            assertEquals(first.size, stored.size, "并发写不得产生被截断的页文件")
            assertTrue(
                stored.contentEquals(first) || stored.contentEquals(second),
                "并发写结果必须是完整的一份内容，不能互相截断成混合字节",
            )
            assertEquals(listOf(1), store.files("p"))
            assertEquals(emptyList(), File(root, "p").listFiles().orEmpty().filter { it.name.endsWith(".tmp") }.map { it.name })
        }
        assertEquals(emptyList(), errors.map { it.toString() }, "同页并发写不应抛错")
    }

    @Test
    fun `页号列表按数序升序`() {
        val store = store()
        listOf(10, 2, 1, 3).forEach { store.write("p", it, byteArrayOf(it.toByte())) }

        assertEquals(listOf(1, 2, 3, 10), store.files("p"), "页号按数值排序，不是字典序")
    }

    @Test
    fun `页号列表只认数字文件名`() {
        val store = store()
        store.write("p", 8, byteArrayOf(8))
        val dir = File(root, "p")
        File(dir, "abc.jpg").writeText("x")
        File(dir, "封面.png").writeText("x")

        assertEquals(listOf(8), store.files("p"))
    }

    @Test
    fun `未落盘的章节页号列表为空`() {
        val store = store()

        assertEquals(emptyList<Int>(), store.files("never-downloaded"))
    }

    @Test
    fun `删除章节后页号列表为空且读回null`() {
        val store = store()
        store.write("p", 1, byteArrayOf(1))
        store.write("p", 2, byteArrayOf(2))

        store.delete("p")

        assertEquals(emptyList<Int>(), store.files("p"))
        assertNull(store.read("p", 1))
        assertFalse(store.exists("p", 2))
        assertFalse(File(root, "p").exists())
    }

    @Test
    fun `删除未落盘的章节后仍为空`() {
        val store = store()

        store.delete("never-downloaded")

        assertEquals(emptyList<Int>(), store.files("never-downloaded"))
    }

    @Test
    fun `非法photoId拒绝路径穿越与根目录误删`() {
        val store = store()
        val sibling = File(root.parentFile, "${root.name}-sibling").apply { mkdirs() }
        val siblingKeep = File(sibling, "keep.txt").apply { writeText("keep") }
        val validFile = store.also { it.write("valid-1", 1, byteArrayOf(42)) }

        try {
            val invalidIds = listOf("", "   ", ".", "..", "../${sibling.name}", "a/b", "a\\b", "/abs", "a\u0000b")
            for (badId in invalidIds) {
                assertFailsWith<IllegalArgumentException>("exists 应拒绝非法 photoId: [$badId]") {
                    validFile.exists(badId, 1)
                }
                assertFailsWith<IllegalArgumentException>("write 应拒绝非法 photoId: [$badId]") {
                    validFile.write(badId, 1, byteArrayOf(1))
                }
                assertFailsWith<IllegalArgumentException>("read 应拒绝非法 photoId: [$badId]") {
                    validFile.read(badId, 1)
                }
                assertFailsWith<IllegalArgumentException>("delete 应拒绝非法 photoId: [$badId]") {
                    validFile.delete(badId)
                }
                assertFailsWith<IllegalArgumentException>("files 应拒绝非法 photoId: [$badId]") {
                    validFile.files(badId)
                }
            }
            assertTrue(siblingKeep.isFile, "同级目录文件不得受路径穿越影响")
            assertContentEquals(byteArrayOf(42), store.read("valid-1", 1))
        } finally {
            sibling.deleteRecursively()
        }
    }

    @Test
    fun `非正数页号在读写与存在性校验时抛出异常`() {
        val store = store()
        for (badIndex in listOf(0, -1, -99)) {
            assertFailsWith<IllegalArgumentException>("exists 应拒绝页号 $badIndex") {
                store.exists("p", badIndex)
            }
            assertFailsWith<IllegalArgumentException>("write 应拒绝页号 $badIndex") {
                store.write("p", badIndex, byteArrayOf(1))
            }
            assertFailsWith<IllegalArgumentException>("read 应拒绝页号 $badIndex") {
                store.read("p", badIndex)
            }
        }
    }

    @Test
    fun `删除章节后清理对应的页写锁与章节读写锁`() {
        val store = store()
        store.write("p1", 1, byteArrayOf(1))
        store.write("p1", 2, byteArrayOf(2))
        store.write("p2", 1, byteArrayOf(3))
        assertEquals(3 to 0, store.activeLockCounts(), "写入完成后 writeLocks 持有 3 页、photoLocks 空闲自动回收")

        store.delete("p1")
        assertEquals(1 to 0, store.activeLockCounts(), "删除 p1 后应清理 p1 两页对应的 writeLocks")

        store.delete("p2")
        assertEquals(0 to 0, store.activeLockCounts(), "删除全部章节后锁表应归零")
    }
}
