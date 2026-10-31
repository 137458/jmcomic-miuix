package com.jm3.core.settings

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpdateSettingsTest {

    private val settings = InMemorySettingsRepository()

    @Test
    fun 自动检查默认开启() = runTest {
        assertTrue(settings.autoCheckUpdate())
    }

    @Test
    fun 自动检查开关可往返() = runTest {
        settings.setAutoCheckUpdate(false)
        assertFalse(settings.autoCheckUpdate())
        settings.setAutoCheckUpdate(true)
        assertTrue(settings.autoCheckUpdate())
    }

    @Test
    fun 忽略版本默认未设置() = runTest {
        assertNull(settings.ignoredUpdateVersion())
    }

    @Test
    fun 忽略版本可记录与清除() = runTest {
        settings.setIgnoredUpdateVersion("1.2.0")
        assertEquals("1.2.0", settings.ignoredUpdateVersion())
        settings.setIgnoredUpdateVersion(null)
        assertNull(settings.ignoredUpdateVersion())
    }

    @Test
    fun 已提示版本可记录与清除() = runTest {
        settings.setPromptedUpdateVersion("1.2.0")
        assertEquals("1.2.0", settings.promptedUpdateVersion())
        settings.setPromptedUpdateVersion(null)
        assertNull(settings.promptedUpdateVersion())
    }
}
