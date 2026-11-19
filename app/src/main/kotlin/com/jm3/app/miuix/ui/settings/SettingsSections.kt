package com.jm3.app.miuix.ui.settings

import com.jm3.core.settings.BottomBarForm
import com.jm3.core.settings.PaginationMode
import com.jm3.core.settings.ThemeMode
import com.jm3.core.settings.UiSkin
import com.jm3.core.settings.WideScreenNavigation

/*
 * 设置页分区与条目（双皮肤去重第二批）。
 *
 * 共享骨架收口的是行为：线路诊断四态、探活重试、自定义线路提交、清空二次确认与隐藏诊断。
 * 这里收口的是两套皮肤此前逐字抄同一份的展示数据：分区与行标题、固定说明、随状态变化的
 * 行内说明、弹窗按钮，以及各偏好项的选项表。皮肤只负责把 SettingsOption 渲染成自己的控件
 * （HyperOS 的下拉与分段控件、ColorOS 的锚点菜单与胶囊分段），控件差异不进本文件。
 *
 * 本文件不引用任何皮肤控件：没有 Miuix、COUI 与 Compose 的 import，只用 core 的偏好枚举，
 * 因此两套皮肤都能直接引用，纯 kotlin.test 也能直接覆盖。
 *
 * 皮肤之间的真实差异不在这里统一，仍留在各自页面：
 * - 「底栏尺寸」只有 HyperOS 有，选项与文案留在那页；
 * - 动态取色在 ColorOS 是整行不渲染、在 HyperOS 是禁用开关，只共用配色支持的说明；
 * - 「阅读器」七项与 ReaderSettingsSection.kt 之间另有重复，跨文件，本次不动。
 */

/** 选项表里的一个值：枚举值与它在两套皮肤里逐字相同的文案（下拉项、菜单项、分段标签共用）。 */
internal data class SettingsOption<T>(val value: T, val label: String)

/**
 * 一组可选值：标签序列、当前值下标与下标回落收在一处。
 *
 * 皮肤不再各自 `entries.map { it.label }`，标签只算一次；选中下标与枚举 `entries.indexOf` 同义
 * （值缺席时为 -1），[valueAt] 越界返回 null，因此越界点击与点击已选项都不写盘。
 */
internal class SettingsOptions<T> internal constructor(values: List<T>, label: (T) -> String) {

    /** 渲染用的选项序列：与枚举声明同序。 */
    val items: List<SettingsOption<T>> = values.map { SettingsOption(it, label(it)) }

    /** 只要标签的控件（页签行、胶囊分段）直接取它，不用每次重组重算。 */
    val labels: List<String> = items.map { it.label }

    /** 当前值的选中下标；值不在选项里时为 -1。 */
    fun indexOf(current: T): Int = items.indexOfFirst { it.value == current }

    /** 下标回落到值；越界返回 null。 */
    fun valueAt(index: Int): T? = items.getOrNull(index)?.value
}

/** 设置页的共享文案与选项表：两套皮肤只消费，不再各自拼字符串。 */
internal object SettingsSections {

    /** 顶栏标题。 */
    const val title = "设置"

    /** 分区标题：两套皮肤同名同序，ColorOS 另有「阅读器」一节留在自己页面。 */
    const val sectionTheme = "主题"
    const val sectionGeneral = "通用"
    const val sectionLine = "线路"
    const val sectionData = "数据管理"
    const val sectionAbout = "关于"

    /** 行标题：两套皮肤的控件外观不同，文案逐字相同。 */
    const val appearanceTitle = "外观模式"
    const val uiSkinTitle = "界面风格"
    const val dynamicColorTitle = "动态取色"
    const val paginationTitle = "分页模式"
    const val bottomBarFormTitle = "底栏形态"
    const val wideNavigationTitle = "宽屏导航"
    const val activeDomainsTitle = "当前线路"
    const val usedDomainTitle = "实际线路"
    const val latencyTitle = "线路延迟"
    const val reprobeTitle = "重新探活"
    const val customDomainsTitle = "自定义线路"
    const val applyDomainsTitle = "应用线路"
    const val versionTitle = "版本"
    const val updateTitle = "检查更新"
    const val openSourceTitle = "开源组件"
    const val disclaimerTitle = "免责声明"

    /** 与状态无关的行内说明。 */
    const val customDomainsSummary = "每行一个域名，留空恢复默认"
    const val updateSummary = "查看远端最新版本，支持应用内下载与安装"
    const val disclaimerSummary = "第三方客户端，仅供学习交流，包含成人内容"

    /** 隐藏诊断面板与提示弹窗的固定文案。 */
    const val diagnosticsTitle = "诊断信息"
    const val dialogAck = "知道了"

    /**
     * 动态取色行的说明：不支持时另给一句原因。
     *
     * ColorOS 侧整行不渲染（固定自有色板），只会取到第一句；第二句留给 HyperOS 的禁用开关，
     * 那里需要解释为何不可点。
     */
    fun dynamicColorSummary(supported: Boolean): String =
        if (supported) {
            "跟随壁纸生成配色（Android 12 及以上）"
        } else {
            "ColorOS 皮肤固定自有色板，动态取色不生效"
        }

    /** 重新探活行的说明：探活在途时换进度文案，与按钮禁用同一判据。 */
    fun reprobeSummary(probing: Boolean): String =
        if (probing) "探活中…" else "逐条测速并按延迟排序"

    /** 应用线路行的说明：提交在途时换进度文案。 */
    fun applyDomainsSummary(applying: Boolean): String =
        if (applying) "应用中…" else "保存配置并重建 API 网关"

    /** 清空确认弹窗的主按钮：清空在途时换进度文案。 */
    fun clearConfirmLabel(clearing: Boolean): String =
        if (clearing) "清空中…" else "确认清空"

    /** 延迟行的值文案：两套皮肤都是「毫秒数 + 空格 + ms」。 */
    fun latencyValueText(latencyMs: Int): String = "$latencyMs ms"

    /** 线路诊断失败行：两套皮肤共用同一前缀，异常原因由骨架给出。 */
    fun diagnosticsFailureText(message: String): String = "线路诊断失败：$message"

    /** 清空目标的说明：与 [SettingsClearTarget] 一一绑定，改一项只改这里。 */
    fun clearSummary(target: SettingsClearTarget): String = when (target) {
        SettingsClearTarget.HISTORY -> "移除全部阅读记录"
        SettingsClearTarget.DOWNLOADS -> "清除下载任务与已下载文件"
    }

    /** 主题模式：标签取 [ThemeMode.displayName]，行内说明由骨架的 themeModeSummary 给出。 */
    val themeMode: SettingsOptions<ThemeMode> = SettingsOptions(ThemeMode.entries) { it.displayName }

    /** 界面风格：两套皮肤都只显示皮肤名。 */
    val uiSkin: SettingsOptions<UiSkin> = SettingsOptions(UiSkin.entries) { it.label }

    /** 分页模式：标签取 [PaginationMode.displayName]。 */
    val paginationMode: SettingsOptions<PaginationMode> =
        SettingsOptions(PaginationMode.entries) { it.displayName }

    /** 底栏形态。 */
    val bottomBarForm: SettingsOptions<BottomBarForm> =
        SettingsOptions(BottomBarForm.entries) { it.label }

    /** 宽屏导航：只在 600dp 断点以上渲染，断点判定留在骨架。 */
    val wideScreenNavigation: SettingsOptions<WideScreenNavigation> =
        SettingsOptions(WideScreenNavigation.entries) { it.label }
}
