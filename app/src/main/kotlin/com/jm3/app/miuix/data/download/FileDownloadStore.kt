package com.jm3.app.miuix.data.download

import com.jm3.core.download.DownloadStore
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * 文件落盘实现：{root}/{photoId}/{index:05}.jpg，页号从 1 起。
 */
class FileDownloadStore(private val root: File) : DownloadStore {

    private fun dir(photoId: String) = File(root, photoId)

    private fun file(photoId: String, index: Int) = File(dir(photoId), "%05d.jpg".format(index))

    override fun exists(photoId: String, index: Int): Boolean = file(photoId, index).isFile

    override fun write(photoId: String, index: Int, bytes: ByteArray) {
        dir(photoId).mkdirs()
        val target = file(photoId, index)
        val tmp = File(target.parentFile, target.name + ".tmp")
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
