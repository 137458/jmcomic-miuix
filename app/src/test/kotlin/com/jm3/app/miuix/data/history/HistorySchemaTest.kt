/**
 * Copyright (c) 2026 jm3 contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.jm3.app.miuix.data.history

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 守住 Room 表结构快照（app/schemas）：版本、实体与索引必须和源码里的实体定义、MIGRATION_2_3 对得上。
 * 漂移了有两种后果——少索引则升级设备全表扫描，多索引则在旧版本上建库失败；都只在真机升级时才暴露。
 */
class HistorySchemaTest {

    /** 单测工作目录是 app 模块目录；从仓库根直接跑 IDE 单测时落在上一层的 app/schemas。 */
    private fun schemaFile(): File {
        val root = listOf(File("schemas"), File("app/schemas")).firstOrNull { it.isDirectory }
        assertTrue(
            root != null,
            "缺少 Room schema 目录（工作目录=" + File("").absolutePath + "）：确认 room.schemaLocation 已配置并构建过一次",
        )
        return File(root, "com.jm3.app.miuix.data.history.HistoryDb/3.json")
    }

    private fun schema(): JsonObject {
        val file = schemaFile()
        assertTrue(file.isFile, "缺少 Room schema 快照：" + file.absolutePath)
        return Json.parseToJsonElement(file.readText()).jsonObject
    }

    private fun entities(): List<JsonObject> =
        schema().getValue("database").jsonObject.getValue("entities").jsonArray.map { it.jsonObject }

    private fun indicesOf(tableName: String): Map<String, List<String>> {
        val entity = entities().firstOrNull { it.getValue("tableName").jsonPrimitive.content == tableName }
            ?: return emptyMap()
        return entity.getValue("indices").jsonArray.associate { index ->
            val obj = index.jsonObject
            obj.getValue("name").jsonPrimitive.content to obj.getValue("columnNames").jsonArray.map { it.jsonPrimitive.content }
        }
    }

    @Test
    fun `导出的库版本与实体清单齐全`() {
        val database = schema().getValue("database").jsonObject
        assertEquals(3, database.getValue("version").jsonPrimitive.content.toInt())
        assertEquals(
            setOf("history", "downloads"),
            entities().map { it.getValue("tableName").jsonPrimitive.content }.toSet(),
        )
    }

    @Test
    fun `导出索引与迁移脚本建的一致`() {
        assertEquals(
            mapOf(
                "index_history_albumId" to listOf("albumId"),
                "index_history_updatedAt" to listOf("updatedAt"),
            ),
            indicesOf("history"),
        )
        assertEquals(mapOf("index_downloads_updatedAt" to listOf("updatedAt")), indicesOf("downloads"))
    }

    @Test
    fun `迁移脚本建的索引名与导出快照同名`() {
        val migrationSql = File("src/main/kotlin/com/jm3/app/miuix/data/history/HistoryStore.kt").readText()
        val expected = listOf("index_history_albumId", "index_history_updatedAt", "index_downloads_updatedAt")
        assertEquals(
            expected.toSet(),
            expected.filter { migrationSql.contains("CREATE INDEX IF NOT EXISTS [$it]") }.toSet(),
        )
    }
}
