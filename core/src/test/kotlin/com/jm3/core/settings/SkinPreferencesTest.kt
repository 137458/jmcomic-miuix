package com.jm3.core.settings

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SkinPreferencesTest {

    @Test
    fun `未设置时读回 null 且不写键 默认 HyperOS`() = runBlocking {
        val repo = InMemorySettingsRepository()
        assertNull(repo.uiSkinOrNull())
        assertNull(repo.readString(SettingsKeys.UI_SKIN), "未选择过不应写入键")
        assertEquals(UiSkin.HYPEROS, SkinPreferencePolicy.resolve(repo.uiSkinOrNull()))
    }

    @Test
    fun `显式选择 ColorOS 后原样读回`() = runBlocking {
        val repo = InMemorySettingsRepository()
        repo.setUiSkin(UiSkin.COLOROS)
        assertEquals(UiSkin.COLOROS, repo.uiSkinOrNull())
        assertEquals(UiSkin.COLOROS, SkinPreferencePolicy.resolve(repo.uiSkinOrNull()))
    }

    @Test
    fun `非法残留值等价于未设置并回落默认`() = runBlocking {
        val repo = InMemorySettingsRepository()
        repo.writeString(SettingsKeys.UI_SKIN, "MIUI")
        assertNull(repo.uiSkinOrNull())
        assertEquals(UiSkin.HYPEROS, SkinPreferencePolicy.resolve(repo.uiSkinOrNull()))
    }

    @Test
    fun `两档都有可展示的名称且顺序稳定`() {
        assertEquals(listOf("HyperOS", "ColorOS"), UiSkin.entries.map { it.label })
    }
}
