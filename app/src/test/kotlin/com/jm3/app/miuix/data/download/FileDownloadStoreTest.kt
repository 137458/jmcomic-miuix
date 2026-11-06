package com.jm3.app.miuix.data.download

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.assertFailsWith
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        // 让写临时文件这一步必然失败（同名路径已被目录占住），验证失败分支也守"不留临时文件"的契约。
        val blocked = File(dir, "00001.jpg.tmp").apply { mkdirs() }

        assertFailsWith<IllegalStateException> { store.write("p", 1, byteArrayOf(1, 2, 3)) }
        assertFalse(blocked.exists(), "写入失败后不许留下临时路径")
        assertFalse(store.exists("p", 1), "失败时不得把半截内容当成已落盘")
        assertEquals(emptyList<Int>(), store.files("p"))
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
}
