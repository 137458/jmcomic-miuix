package com.jm3.core.download

import com.jm3.core.model.PageImage
import com.jm3.core.model.PhotoPages

/**
 * 章节取图能力：页清单 + 单页字节。
 *
 * 为什么存在：下载链路的 [PageFetcher] 与 [TaskResolver] 只需要这两个方法，而实现方是
 * 63 个方法的 Jm3Gateway。若不单独抽出，调用方与测试都被迫依赖整张网关——测试要么伪造
 * 63 个方法，要么用反射代理（接口改名在编译期毫无反应）。抽出后下载 seam 真的可穿：
 * 生产侧传 Jm3Gateway（它继承本接口），测试侧传两方法的假实现即可。
 */
interface PhotoSource {

    /** 章节图片清单。阻塞调用。 */
    fun photo(photoId: String): PhotoPages

    /** 章节图片二进制（已还原分割）。阻塞调用。 */
    fun imageBytes(image: PageImage): ByteArray
}
