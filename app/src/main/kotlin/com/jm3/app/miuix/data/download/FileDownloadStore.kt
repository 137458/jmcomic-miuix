package com.jm3.app.miuix.data.download

import com.jm3.core.download.DownloadStore
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 文件落盘实现：{root}/{photoId}/{index:05}.jpg，页号从 1 起。
 */
class FileDownloadStore(private val root: File) : DownloadStore {

    /** 临时文件序号：同页并发写时每次用不同名字，避免两个 worker 互相截断同一 .tmp。 */
    private val tempSeq = AtomicLong(0L)

    /** 同页写锁：Windows 上两个 REPLACE_EXISTING 并发替换同一目标会互相失败，必须串行。 */
    private val writeLocks = ConcurrentHashMap<String, Any>()

    private fun dir(photoId: String) = File(root, photoId)

    private fun file(photoId: String, index: Int) = File(dir(photoId), "%05d.jpg".format(index))

    override fun exists(photoId: String, index: Int): Boolean = file(photoId, index).isFile

    override fun write(photoId: String, index: Int, bytes: ByteArray) {
        dir(photoId).mkdirs()
        val target = file(photoId, index)
        // 同一页的并发写串行化：暂停后立即续传时新旧 worker 可能同时落到同一页
        val lock = writeLocks.computeIfAbsent(target.path) { Any() }
        synchronized(lock) {
            // 唯一临时名：队列已按代际挡掉旧 worker，但暂停时尚在写盘的窗口无法取消，
            // 两者重叠时至少不会写到同一个临时文件上互相截断。
            val tmp = File(target.parentFile, "${target.name}.${tempSeq.incrementAndGet()}.tmp")
            // 写字节与移动两段都可能失败（磁盘满、路径被占）：失败必须清掉半截临时文件，
            // 否则契约里「不许留下临时文件」只在 rename 这半段成立。
            // 覆盖写必须真替换：File.renameTo 在 Windows 上遇到已存在目标会返回 false。
            val stored = runCatching {
                tmp.outputStream().use { it.write(bytes) }
                Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }.isSuccess
            if (!stored) {
                tmp.delete()
                error("下载页落盘失败：${target.path}")
            }
        }
    }

    override fun read(photoId: String, index: Int): ByteArray? {
        val f = file(photoId, index)
        return if (f.isFile) f.readBytes() else null
    }

    override fun delete(photoId: String) {
        dir(photoId).deleteRecursively()
    }

    override fun files(photoId: String): List<Int> =
        dir(photoId).listFiles { f -> f.isFile }
            ?.mapNotNull { it.nameWithoutExtension.toIntOrNull() }
            ?.sorted()
            .orEmpty()
}
