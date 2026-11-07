package com.jm3.app.miuix.ui

/**
 * 阅读页窗口状态引用计数。
 *
 * 换章是同层替换加转场，新旧阅读页会短暂同时挂载：亮度原值只在首个页面进入时记录、
 * 最后一个页面离开时恢复，亮屏标记同理，否则旧页销毁会把新页刚应用的值一起抹掉。
 */
internal class ReaderWindowLease {

    /** 进入阅读页前窗口的亮度；只有首个进入的页面记录，后续页面不得覆盖。 */
    var originalBrightness: Float? = null
        private set

    private var brightnessLeases = 0
    private var keepScreenOnLeases = 0

    /** 进入页面；返回是否首个持有者（首个持有者负责记录原亮度）。 */
    fun enterBrightness(originalBrightness: Float?): Boolean {
        brightnessLeases++
        if (brightnessLeases == 1) this.originalBrightness = originalBrightness
        return brightnessLeases == 1
    }

    /** 离开页面；返回是否最后一个持有者（最后一个持有者负责恢复原亮度）。 */
    fun exitBrightness(): Boolean {
        if (brightnessLeases == 0) return false
        brightnessLeases--
        return brightnessLeases == 0
    }

    /** 进入页面；返回是否首个持有者（首个持有者负责加亮屏标记）。 */
    fun enterKeepScreenOn(): Boolean {
        keepScreenOnLeases++
        return keepScreenOnLeases == 1
    }

    /** 离开页面；返回是否最后一个持有者（最后一个持有者负责清亮屏标记）。 */
    fun exitKeepScreenOn(): Boolean {
        if (keepScreenOnLeases == 0) return false
        keepScreenOnLeases--
        return keepScreenOnLeases == 0
    }
}

/** 进程内唯一实例：换章并存的两块组合必须共享同一份计数。 */
internal val ReaderWindowLeases = ReaderWindowLease()
