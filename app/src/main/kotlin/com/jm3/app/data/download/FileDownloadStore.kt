package com.jm3.app.data.download

import com.jm3.core.download.DownloadStore
import java.io.File

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
        tmp.outputStream().use { it.write(bytes) }
        if (!tmp.renameTo(target)) {
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
