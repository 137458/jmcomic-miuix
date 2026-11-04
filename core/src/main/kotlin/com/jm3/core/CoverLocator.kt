package com.jm3.core

/**
 * 图片域名与图片地址的唯一入口：域名归一、封面地址、相对图片路径三件事只在这里判定。
 * 域名未就绪用 [CoverLocator.Unready] 表示，两个构造器都返回 null，调用方不必自己判空。
 */
@JvmInline
value class CoverLocator private constructor(val host: String) {

    /** 封面地址；车号为空或域名未就绪时为 null。 */
    fun cover(albumId: String?, size: String = ""): String? {
        val id = albumId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (host.isEmpty()) return null
        return HTTPS + host + ALBUM_COVER_PATH + id + size + ".jpg"
    }

    /** 图片地址；相对路径拼域名，绝对地址原样返回（域名未就绪也不影响绝对地址）。 */
    fun image(path: String?): String? {
        val raw = path?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (raw.startsWith(HTTPS) || raw.startsWith(HTTP)) return raw
        if (host.isEmpty()) return null
        return HTTPS + host + "/" + raw.trimStart('/')
    }

    companion object {
        private const val HTTPS = "https://"
        private const val HTTP = "http://"
        private const val ALBUM_COVER_PATH = "/media/albums/"

        val Unready: CoverLocator = CoverLocator("")

        /** 任意域名形态归一；空白输入得到 [Unready]。 */
        fun of(raw: String?): CoverLocator {
            val host = hostOf(raw)
            return if (host.isEmpty()) Unready else CoverLocator(host)
        }

        /** 只取域名：去首尾空白、去协议头、去路径、去尾部斜杠。 */
        fun hostOf(raw: String?): String {
            val text = raw?.trim().orEmpty()
            if (text.isEmpty()) return ""
            return text.removePrefix(HTTPS).removePrefix(HTTP).substringBefore('/').trimEnd('/')
        }
    }
}
