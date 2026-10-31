package com.jm3.core.update

/**
 * 从 Release 挂载的文件里挑出可用于应用内安装的 APK。
 *
 * 取舍一：只认 `.apk` 后缀，不按 ABI（arm64-v8a / armeabi-v7a）匹配。本应用的发布产物只有
 * universal 包，没有 abi splits，按 ABI 匹配只会在产物命名变化时把安装包漏掉。
 *
 * 取舍二：优先“名字含 universal”再退化为第一个 apk。发布流程会把 mapping.txt、symbols.zip
 * 这类构建附属物一并挂到 Release 上，它们的文件名里也常带 ABI 字样；因此先走 universal 关键字，
 * 避免把符号表当成安装包。全都不满足时返回 null，由调用方回落到 Release 页面。
 */
object ApkAssetSelector {

    /**
     * 选择安装包：滤掉非 apk 后缀与下载地址为空白的项，其中优先名字含 universal 的，
     * 没有则取过滤后的第一个；一个都没有返回 null。
     */
    fun select(assets: List<ReleaseAsset>): ReleaseAsset? {
        val apks = assets.filter { asset ->
            asset.name.endsWith(APK_SUFFIX, ignoreCase = true) && asset.downloadUrl.isNotBlank()
        }
        return apks.firstOrNull { it.name.contains(UNIVERSAL_KEYWORD, ignoreCase = true) }
            ?: apks.firstOrNull()
    }

    private const val APK_SUFFIX = ".apk"
    private const val UNIVERSAL_KEYWORD = "universal"
}
