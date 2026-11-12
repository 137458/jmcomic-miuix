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

    /** 图片地址；相对路径拼域名，绝对地址与协议相对路径原样或补全 https 后返回。 */
    fun image(path: String?): String? {
        val raw = path?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (raw.startsWith(HTTPS, ignoreCase = true) || raw.startsWith(HTTP, ignoreCase = true)) return raw
        if (raw.startsWith("//")) {
            val remainder = raw.substring(2)
            return if (remainder.isNotEmpty() && !remainder.startsWith("/")) HTTPS + remainder else null
        }
        if (host.isEmpty()) return null
        val relative = raw.trimStart('/').takeIf { it.isNotEmpty() } ?: return null
        return HTTPS + host + "/" + relative
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

        /** 只取域名：去首尾空白、去协议头、去路径/查询/锚点/端口、去前导点。 */
        fun hostOf(raw: String?): String {
            var text = raw?.trim().orEmpty()
            if (text.isEmpty()) return ""
            if (text.startsWith(HTTPS, ignoreCase = true)) {
                text = text.substring(HTTPS.length)
            } else if (text.startsWith(HTTP, ignoreCase = true)) {
                text = text.substring(HTTP.length)
            }
            return text
                .substringBefore('/')
                .substringBefore('?')
                .substringBefore('#')
                .substringBefore(':')
                .trimStart('.')
                .trim()
        }
    }
}
/**
 * 封面的三种结果：可加载、域名未就绪（重试有意义）、没有封面（重试无意义）。
 *
 * 两种失败都不给地址，但调用方可以据此决定重试、骨架屏还是占位图。
 */
sealed interface Cover {
    /** 可加载时为地址，其余为 null；这是把三种结果收成 DTO 字段时的唯一点。 */
    val urlOrNull: String?

    data class Ready(val url: String) : Cover {
        override val urlOrNull: String get() = url
    }

    /** 域名未就绪：地址拼不出来，换线路或稍后再试可能成功。 */
    data object Unready : Cover {
        override val urlOrNull: String? get() = null
    }

    /** 没有封面：车号为空，重试不会有别的结果。 */
    data object None : Cover {
        override val urlOrNull: String? get() = null
    }
}

/**
 * 封面与图片地址的唯一入口：地址怎么拼由 [CoverLocator] 决定，域名从哪来由构造它的地方决定。
 *
 * 一次 [resolve] 就同时回答了"有没有封面"和"域名准备好没有"，调用方不必再拼一遍地址、
 * 也不必自己判空两次。本文件里的顶层 [coverCacheKeyText] 与 [resolve] 互逆：同一车号拼出的地址
 * 能被解析回车号，两边改一处就能被往返测试抓到。
 */
class CoverResolver private constructor(private val hostProvider: () -> String?) {

    /** 解析封面地址。车号为空得到 [Cover.None]，域名未就绪得到 [Cover.Unready]。 */
    fun resolve(albumId: String?, size: String = "_3x4"): Cover {
        val id = albumId?.trim()?.takeIf { it.isNotEmpty() } ?: return Cover.None
        // 车号侧不可重试优先于域名侧可重试：两边都缺时告诉调用方"没有封面"
        val url = locator().cover(id, size) ?: return Cover.Unready
        return Cover.Ready(url)
    }

    /** 图片地址：相对路径拼当前域名，绝对地址原样返回。 */
    fun image(path: String?): String? = locator().image(path)

    private fun locator(): CoverLocator = CoverLocator.of(hostProvider())

    companion object {
        /** 域名未就绪的解析器：所有 [resolve] 都是 [Cover.Unready]。 */
        val Unready: CoverResolver = CoverResolver { null }

        /** 固定域名：调用方先问一次域名裁决，页内所有封面走同一份。 */
        fun of(host: String?): CoverResolver = CoverResolver { host }
    }
}

private val ALBUM_COVER_PATH = Regex("""/media/albums/(\d+)[^/]*$""")

private const val ALBUM_COVER_KEY_PREFIX = "jm3-album-cover:"

/**
 * 封面缓存键文本：作品封面按车号归并（同一车号换了线路仍命中同一条缓存），其它地址原样返回。
 *
 * 与 [CoverResolver.resolve] 互逆——拼地址与解车号在同一文件里，往返测试能同时钉住两侧。
 */
fun coverCacheKeyText(url: String?): String? {
    val clean = url?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val albumId = ALBUM_COVER_PATH.find(clean)?.groupValues?.get(1).orEmpty()
    return if (albumId.isNotEmpty()) ALBUM_COVER_KEY_PREFIX + albumId else clean
}