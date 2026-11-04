package com.jm3.core.update

/**
 * 下载进度的换算规则。
 *
 * 取舍：比例与百分比都收在这里，而不是各自在界面里算。弹窗主按钮、页面下载卡、下载中状态
 * 三处原先各写一份 `(downloaded / total).coerceIn(0f, 1f) * 100`，边界（总长未知、越界）
 * 只能靠人肉对齐。
 */
object UpdateProgress {

    /**
     * 已下载字节占总长的比例（0~1）。
     *
     * 总长未知（服务器未给 Content-Length）或越界数据时给出可展示的边界值：未知按 0，
     * 已下载大于总长按 1；界面在总长为 0 时改报已下载字节，不会显示成"进度为零"。
     */
    fun fraction(downloaded: Long, total: Long): Float =
        if (total > 0L) (downloaded.toDouble() / total).coerceIn(0.0, 1.0).toFloat() else 0f

    /**
     * 比例转百分比整数（0~100），向下取整。
     *
     * 不四舍五入：服务器给出的总长与实际字节差一点时，99.6% 会变成 100%，用户以为下完了却还在等。
     */
    fun percent(fraction: Float): Int = (fraction.coerceIn(0f, 1f) * 100).toInt()
}
