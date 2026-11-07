package com.jm3.core.settings

/**
 * 界面.style 两档：默认 HyperOS（现行视觉），可选 ColorOS。
 *
 * 未选择过不写盘：读回 null 表示升级用户从未设置，展示与生效都回落 [HYPEROS]。
 */
enum class UiSkin(val label: String) {
    HYPEROS("HyperOS"),
    COLOROS("ColorOS"),
}

/** 皮肤偏好的归一：非法残留值与未设置同义，都回落默认皮肤。 */
object SkinPreferencePolicy {

    /** 未设置或持久化值损坏时都按默认皮肤解析。 */
    fun resolve(stored: UiSkin?): UiSkin = stored ?: UiSkin.HYPEROS
}

/** 外观风格：读回 null 表示从未显式选择，调用方不写键、按默认皮肤渲染。 */
interface SkinSettings : SettingsKeyStore {

    suspend fun uiSkinOrNull(): UiSkin? =
        UiSkin.entries.firstOrNull { it.name == readString(SettingsKeys.UI_SKIN) }

    suspend fun setUiSkin(skin: UiSkin) {
        writeString(SettingsKeys.UI_SKIN, skin.name)
    }
}
