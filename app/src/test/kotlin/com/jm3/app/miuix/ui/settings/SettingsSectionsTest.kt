package com.jm3.app.miuix.ui.settings

import com.jm3.core.settings.BottomBarForm
import com.jm3.core.settings.PaginationMode
import com.jm3.core.settings.ThemeMode
import com.jm3.core.settings.UiSkin
import com.jm3.core.settings.WideScreenNavigation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * 设置页分区与条目：两套皮肤共用的展示数据。
 *
 * 这里覆盖的是「皮肤只消费、不再各自拼字符串」的那一层：分区与行标题、固定说明、
 * 随状态变化的行内说明、弹窗按钮，以及偏好项的选项表。断言全部落在具体文案与顺序上，
 * 因为两套皮肤的视觉不同但文案必须逐字一致——文案漂移就是皮肤之间的行为漂移。
 * 状态类函数的每个分支都给两组取值，避免只验证其中一条路径。
 */
class SettingsSectionsTest {

    @Test
    fun 顶栏与分区标题沿用页面原文() {
        assertEquals("设置", SettingsSections.title)

        assertEquals("主题", SettingsSections.sectionTheme)
        assertEquals("通用", SettingsSections.sectionGeneral)
        assertEquals("线路", SettingsSections.sectionLine)
        assertEquals("数据管理", SettingsSections.sectionData)
        assertEquals("关于", SettingsSections.sectionAbout)
    }

    @Test
    fun 十二个行标题逐字保留且互不相同() {
        val titles = listOf(
            SettingsSections.appearanceTitle,
            SettingsSections.uiSkinTitle,
            SettingsSections.dynamicColorTitle,
            SettingsSections.paginationTitle,
            SettingsSections.bottomBarFormTitle,
            SettingsSections.wideNavigationTitle,
            SettingsSections.networkLineTitle,
            SettingsSections.customDomainsTitle,
            SettingsSections.versionTitle,
            SettingsSections.updateTitle,
            SettingsSections.openSourceTitle,
            SettingsSections.disclaimerTitle,
        )

        assertEquals(
            listOf(
                "外观模式",
                "界面风格",
                "动态取色",
                "分页模式",
                "底栏形态",
                "宽屏导航",
                "网络线路",
                "自定义域名",
                "版本",
                "检查更新",
                "开源组件",
                "免责声明",
            ),
            titles,
        )
        assertEquals(titles.size, titles.distinct().size)
    }

    @Test
    fun 固定说明与弹窗文案逐字保留() {
        assertEquals("选择网络线路", SettingsSections.linePickerTitle)
        assertEquals("自动选择（推荐）", SettingsSections.autoLineTitle)
        assertEquals("每行一个域名，留空则恢复仅使用官方线路", SettingsSections.customDomainsSummary)
        assertEquals("查看远端最新版本，支持应用内下载与安装", SettingsSections.updateSummary)
        assertEquals("第三方客户端，仅供学习交流，包含成人内容", SettingsSections.disclaimerSummary)

        assertEquals("诊断信息", SettingsSections.diagnosticsTitle)
        assertEquals("知道了", SettingsSections.dialogAck)
    }

    @Test
    fun 动态取色说明在支持时给出跟随壁纸文案() {
        assertEquals(
            "跟随壁纸生成配色（Android 12 及以上）",
            SettingsSections.dynamicColorSummary(supported = true),
        )
    }

    @Test
    fun 动态取色说明在不支持时解释固定自有色板() {
        val unsupported = SettingsSections.dynamicColorSummary(supported = false)

        assertEquals("ColorOS 皮肤固定自有色板，动态取色不生效", unsupported)
        assertNotEquals(SettingsSections.dynamicColorSummary(supported = true), unsupported)
    }

    @Test
    fun 网络线路与自定义域名说明随配置与解析状态切换() {
        assertEquals("自动选择 · 自动挑选最优节点", SettingsSections.networkLineSummary(null, null))
        assertEquals("自动选择 · 当前 a.example", SettingsSections.networkLineSummary(null, "a.example"))
        assertEquals("已固定 · b.example", SettingsSections.networkLineSummary("b.example", "a.example"))

        assertEquals("未配置（仅使用官方线路）", SettingsSections.customDomainsSummaryText(emptyList()))
        assertEquals("已配置 2 个域名（优先尝试）", SettingsSections.customDomainsSummaryText(listOf("a.example", "b.example")))

        assertEquals("未测速", SettingsSections.nodeSummaryText(isCustom = false, latencyMs = null))
        assertEquals("自定义 · 42 ms", SettingsSections.nodeSummaryText(isCustom = true, latencyMs = 42))
        assertEquals("自定义 · 超时", SettingsSections.nodeSummaryText(isCustom = true, latencyMs = -1))
    }

    @Test
    fun 测速按钮与域名保存按钮文案随在途状态切换() {
        assertEquals("测速中…", SettingsSections.probeLatencyLabel(probing = true))
        assertEquals("测速", SettingsSections.probeLatencyLabel(probing = false))

        assertEquals("保存中…", SettingsSections.customDomainsSaveLabel(applying = true))
        assertEquals("保存", SettingsSections.customDomainsSaveLabel(applying = false))
    }

    @Test
    fun 清空确认按钮随清空状态在进度与确认之间切换() {
        assertEquals("清空中…", SettingsSections.clearConfirmLabel(clearing = true))
        assertEquals("确认清空", SettingsSections.clearConfirmLabel(clearing = false))
    }

    @Test
    fun 延迟值文案统一为毫秒数加空格加毫秒单位且负值显示超时() {
        assertEquals("0 ms", SettingsSections.latencyValueText(0))
        assertEquals("37 ms", SettingsSections.latencyValueText(37))
        assertEquals("1234 ms", SettingsSections.latencyValueText(1234))
        assertEquals("超时", SettingsSections.latencyValueText(-1))
    }

    @Test
    fun 诊断失败文案带统一前缀并原样保留原因() {
        assertEquals("线路诊断失败：timeout", SettingsSections.diagnosticsFailureText("timeout"))
        assertEquals(
            "线路诊断失败：Connection reset by peer",
            SettingsSections.diagnosticsFailureText("Connection reset by peer"),
        )
    }

    @Test
    fun 清空目标的说明与目标一一绑定() {
        val summaries = SettingsClearTarget.entries.map { SettingsSections.clearSummary(it) }

        assertEquals("移除全部阅读记录", SettingsSections.clearSummary(SettingsClearTarget.HISTORY))
        assertEquals("清除下载任务与已下载文件", SettingsSections.clearSummary(SettingsClearTarget.DOWNLOADS))
        assertEquals(SettingsClearTarget.entries.size, summaries.distinct().size)
    }

    @Test
    fun 主题模式选项与枚举同序且文案取显示名() {
        assertEquals(listOf("跟随系统", "浅色", "深色"), SettingsSections.themeMode.labels)
        assertEquals(ThemeMode.entries.toList(), SettingsSections.themeMode.items.map { it.value })
    }

    @Test
    fun 界面风格选项只显示皮肤名() {
        assertEquals(listOf("HyperOS", "ColorOS"), SettingsSections.uiSkin.labels)
        assertEquals(listOf(UiSkin.HYPEROS, UiSkin.COLOROS), SettingsSections.uiSkin.items.map { it.value })
    }

    @Test
    fun 分页模式选项文案取显示名而不是存储标签() {
        assertEquals(listOf("无限滚动", "点击翻页"), SettingsSections.paginationMode.labels)
        assertNotEquals(PaginationMode.INFINITE.label, SettingsSections.paginationMode.labels.first())
        assertEquals(PaginationMode.entries.toList(), SettingsSections.paginationMode.items.map { it.value })
    }

    @Test
    fun 底栏形态与宽屏导航选项按枚举声明顺序给出() {
        assertEquals(listOf("悬浮", "不悬浮"), SettingsSections.bottomBarForm.labels)
        assertEquals(
            listOf(BottomBarForm.FLOATING, BottomBarForm.DOCKED),
            SettingsSections.bottomBarForm.items.map { it.value },
        )

        assertEquals(listOf("底栏", "侧栏"), SettingsSections.wideScreenNavigation.labels)
        assertEquals(
            listOf(WideScreenNavigation.BOTTOM_BAR, WideScreenNavigation.RAIL),
            SettingsSections.wideScreenNavigation.items.map { it.value },
        )
    }

    @Test
    fun 选中下标命中当前值() {
        assertEquals(0, SettingsSections.themeMode.indexOf(ThemeMode.SYSTEM))
        assertEquals(2, SettingsSections.themeMode.indexOf(ThemeMode.DARK))
        assertEquals(1, SettingsSections.uiSkin.indexOf(UiSkin.COLOROS))
        assertEquals(1, SettingsSections.paginationMode.indexOf(PaginationMode.CLICK))
        assertEquals(0, SettingsSections.bottomBarForm.indexOf(BottomBarForm.FLOATING))
        assertEquals(1, SettingsSections.wideScreenNavigation.indexOf(WideScreenNavigation.RAIL))
    }

    @Test
    fun 下标回落取回原值且越界为空() {
        val themeMode = SettingsSections.themeMode

        assertEquals(ThemeMode.DARK, themeMode.valueAt(2))
        themeMode.items.indices.forEach { index ->
            assertEquals(themeMode.items[index].value, themeMode.valueAt(index))
        }
        assertNull(themeMode.valueAt(ThemeMode.entries.size))
        assertNull(themeMode.valueAt(-1))
    }

    @Test
    fun 选项表标签与选项一一对应且覆盖全部枚举档() {
        assertEquals(SettingsSections.themeMode.items.size, SettingsSections.themeMode.labels.size)
        assertEquals(ThemeMode.entries.size, SettingsSections.themeMode.labels.size)
        assertEquals(UiSkin.entries.size, SettingsSections.uiSkin.labels.size)
        assertEquals(PaginationMode.entries.size, SettingsSections.paginationMode.labels.size)
        assertEquals(BottomBarForm.entries.size, SettingsSections.bottomBarForm.labels.size)
        assertEquals(WideScreenNavigation.entries.size, SettingsSections.wideScreenNavigation.labels.size)
    }
}
