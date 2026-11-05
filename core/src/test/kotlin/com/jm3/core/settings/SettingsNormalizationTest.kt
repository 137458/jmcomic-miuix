package com.jm3.core.settings

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 设置归一规则：折行/去空白/夹取/线路归一只有一份，两个 Adapter（DataStore / 内存）共用。
 *
 * 断言全部穿过 [SettingsRepository] 的公开接口，不碰实现细节。
 */
class SettingsNormalizationTest {

    private fun repo() = InMemorySettingsRepository()

    @Test
    fun 搜索历史_换行折叠为空格后入表() = runTest {
        val r = repo()
        r.addSearchHistory("两个\n词")
        assertEquals(listOf("两个 词"), r.searchHistory())
    }

    @Test
    fun 搜索历史_回车换行连写折叠成一个空格() = runTest {
        val r = repo()
        r.addSearchHistory("作者\r\n名")
        assertEquals(listOf("作者 名"), r.searchHistory())
    }

    @Test
    fun 搜索历史_折行后与已有词去重() = runTest {
        val r = repo()
        r.addSearchHistory("两个 词")
        r.addSearchHistory("两个\n词")
        assertEquals(listOf("两个 词"), r.searchHistory())
    }

    @Test
    fun 搜索历史_全空白不写入() = runTest {
        val r = repo()
        r.addSearchHistory(" \n\t ")
        assertEquals(emptyList(), r.searchHistory())
    }

    @Test
    fun 搜索历史_超长词整条保留() = runTest {
        val r = repo()
        val long = "长".repeat(300)
        r.addSearchHistory(long)
        // 上限只约束条数，不截断单条内容
        assertEquals(listOf(long), r.searchHistory())
    }

    @Test
    fun 线路_写入即归一读回一致() = runTest {
        val r = repo()
        r.setApiDomains(listOf(" https://A.example/ ", "a.example", "B.example/x", "  "))
        // 与 Domains.normalize 同口径：去协议头/尾斜杠/路径/空白项并去重保序；主机名大小写保持原样。
        assertEquals(listOf("A.example", "a.example", "B.example"), r.apiDomains())
    }

    @Test
    fun 线路_两步归一幂等() = runTest {
        val once = Domains.normalize(listOf(" https://a.example/ ", "a.example"))
        assertEquals(once, Domains.normalize(once))
    }

    @Test
    fun 线路_归一后写读往返不丢项() = runTest {
        val r = repo()
        r.setApiDomains(listOf(" https://a.example/ ", "b.example"))
        val first = r.apiDomains()
        r.setApiDomains(first)
        assertEquals(first, r.apiDomains())
    }

    @Test
    fun 版本_首尾空白写入即去除() = runTest {
        val r = repo()
        r.setIgnoredUpdateVersion(" 1.2.0 ")
        assertEquals("1.2.0", r.ignoredUpdateVersion())
        r.setPromptedUpdateVersion(" 1.2.0 ")
        assertEquals("1.2.0", r.promptedUpdateVersion())
    }

    @Test
    fun 版本_全空白视为未设置() = runTest {
        val r = repo()
        r.setIgnoredUpdateVersion("   ")
        assertEquals(null, r.ignoredUpdateVersion())
        r.setPromptedUpdateVersion("\t")
        assertEquals(null, r.promptedUpdateVersion())
    }

    @Test
    fun 小说语言_非法值归一为默认() = runTest {
        val r = repo()
        r.setNovelLang("jp")
        assertEquals(SettingsRepository.DEFAULT_NOVEL_LANG, r.novelLang())
        r.setNovelLang("cn")
        assertEquals("cn", r.novelLang())
    }

    @Test
    fun 小说字号_越界夹取到区间端点() = runTest {
        val r = repo()
        r.setNovelFontSize(100)
        assertEquals(SettingsRepository.NOVEL_FONT_SIZE_MAX, r.novelFontSize())
        r.setNovelFontSize(-3)
        assertEquals(SettingsRepository.NOVEL_FONT_SIZE_MIN, r.novelFontSize())
    }
}
