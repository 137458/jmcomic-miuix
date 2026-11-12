package com.jm3.app.miuix.data

import android.content.Context
import android.content.ContextWrapper
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class UpdateDomainTest {

    private lateinit var tempDir: File

    @AfterTest
    fun cleanUp() {
        if (::tempDir.isInitialized) tempDir.deleteRecursively()
    }

    @Test
    fun `启动自动更新检查名额同一进程仅可认领一次`() {
        tempDir = Files.createTempDirectory("jm3-update-domain").toFile()
        val context = object : ContextWrapper(null) {
            override fun getApplicationContext(): Context = this
            override fun getCacheDir(): File = tempDir
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val domain = UpdateDomain(context, scope)

        assertTrue(domain.claimStartupUpdateCheck(), "首次冷启动应允许发起自动更新检查")
        assertFalse(domain.claimStartupUpdateCheck(), "从更新页返回或重组时不得重复认领启动检查")
        assertFalse(domain.claimStartupUpdateCheck())

        scope.cancel()
    }
}
