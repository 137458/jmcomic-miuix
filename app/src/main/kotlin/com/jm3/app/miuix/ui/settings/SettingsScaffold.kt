package com.jm3.app.miuix.ui.settings

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.failureCopy
import com.jm3.app.miuix.ui.ReaderSettingsState
import com.jm3.app.miuix.ui.planLoad
import com.jm3.app.miuix.ui.rememberReaderSettingsState
import com.jm3.app.miuix.ui.rememberWindowDpSize
import com.jm3.app.miuix.ui.resolvePaginationSelection
import com.jm3.app.miuix.ui.route.AppRoutes
import com.jm3.app.miuix.ui.toLayoutDpInt
import com.jm3.core.reader.ReaderSpreadPolicy
import com.jm3.core.runCatchingSuspending
import com.jm3.core.session.SessionOutcome
import com.jm3.core.settings.BottomBarForm
import com.jm3.core.settings.BottomBarPreferencePolicy
import com.jm3.core.settings.BottomBarSize
import com.jm3.core.settings.PaginationMode
import com.jm3.core.settings.ThemeMode
import com.jm3.core.settings.UiSkin
import com.jm3.core.settings.WideScreenNavigation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.SnackbarHostState

/*
 * 设置页共享骨架（C4 双皮肤试点）。
 *
 * 两套皮肤的设置页此前各写一份逐字相同的状态编排：线路诊断的读取与四态选择、探活重试、
 * 自定义线路提交、破坏性清空的二次确认、隐藏诊断与版本连点，以及所有说明文案。
 * 这里把「行为」收口到一处，皮肤侧只留自己的控件与弹窗外观；
 * 骨架不引用任何皮肤控件，Miuix 与 COUI 都能直接 import。
 */

/** 线路延迟表的一行：域名与本次探活得到的毫秒数（读回时已按延迟升序排好）。 */
internal data class DomainLatency(val domain: String, val latencyMs: Int)

/** 线路诊断区块的四态：加载中、读回为空、读回失败、有可渲染的延迟行。 */
internal enum class SettingsDiagnosticsPhase { Loading, Empty, Failed, Content }

/** 延迟区块的占位文案：加载中、读回为空、失败三态共用同一句，与既有界面一致。 */
internal const val SETTINGS_LATENCY_PLACEHOLDER = "尚未完成线路探活"

/** 实际线路尚未解析时的展示文案。 */
internal const val SETTINGS_DOMAIN_UNRESOLVED = "未解析"

/** 自定义线路为空时的展示文案。 */
internal const val SETTINGS_DOMAINS_DEFAULT = "默认（自动解析）"

/** 版本号连点进入隐藏诊断的阈值与窗口：两套皮肤同一阈值。 */
internal const val SETTINGS_DIAGNOSTIC_TAPS = 5
internal const val SETTINGS_DIAGNOSTIC_TAP_WINDOW_MS = 2000L

/** 清空确认自动收起的时长：颜色皮肤 5 秒后自动复位，浅色皮肤不自动收起。 */
internal const val SETTINGS_CLEAR_CONFIRM_TIMEOUT_MS = 5000L

/** 免责声明正文：两套皮肤共用同一段，改文案只改这里。 */
internal const val SETTINGS_DISCLAIMER_TEXT = "本应用为第三方客户端，与禁漫官方无关，仅供学习交流；" +
    "全部内容来自第三方站点，本应用不存储任何内容。包含成人内容，" +
    "未满 18 周岁（或当地法定年龄）请勿使用；因使用本应用产生的一切后果由使用者自行承担。"

/** 开源组件清单：与 NOTICE 对齐的用户可见展示文案。 */
internal const val SETTINGS_OPEN_SOURCE_SUMMARY = "jmcomic-api (MIT) · Miuix (Apache-2.0) · " +
    "Coil (Apache-2.0) · Jetpack Compose (Apache-2.0) · COUI-Kit (MPL-2.0) · Lucide (ISC)"

/** 数据管理里的破坏性清空目标：标题、确认文案与完成提示绑在同一处，避免魔法串分叉。 */
internal enum class SettingsClearTarget(val title: String, val description: String, val doneMessage: String) {
    HISTORY("清空阅读历史", "将删除全部阅读记录，此操作不可撤销。", "已清空阅读历史"),
    DOWNLOADS("清空下载", "将删除全部下载任务并清除已下载文件，此操作不可撤销。", "已清空下载"),
}

/** 设置项说明文案：两套皮肤逐字相同，只换控件。 */
internal fun themeModeSummary(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "跟随系统自动切换深浅色"
    ThemeMode.LIGHT -> "始终保持浅色高亮视觉"
    ThemeMode.DARK -> "始终保持深色暗调视觉"
}

internal fun paginationModeSummary(mode: PaginationMode): String = when (mode) {
    PaginationMode.INFINITE -> "滚动到底自动加载下一页"
    PaginationMode.CLICK -> "列表底部显示上一页/下一页"
}

internal fun bottomBarFormSummary(form: BottomBarForm): String = when (form) {
    BottomBarForm.FLOATING -> "圆角胶囊，与屏幕底边留出悬浮间隙"
    BottomBarForm.DOCKED -> "贴底通栏，紧接导航栏上方并撑满宽度"
}

internal fun wideScreenNavigationSummary(navigation: WideScreenNavigation): String = when (navigation) {
    WideScreenNavigation.BOTTOM_BAR -> "宽屏同样显示底栏，与手机保持同一套样式"
    WideScreenNavigation.RAIL -> "宽屏改用可收起的侧栏，列表获得更宽横向空间"
}

internal fun uiSkinSummary(skin: UiSkin): String = when (skin) {
    UiSkin.HYPEROS -> "HyperOS：液态玻璃折射与饱和高光"
    UiSkin.COLOROS -> "ColorOS：均匀磨砂与材质混色"
}

/**
 * 线路诊断区块的状态：读回结果、失败文案与探活动作的在途标记放在同一处。
 *
 * 四态只由读回结果推导，界面据此决定「逐行列出延迟」还是「一句占位」：
 * 有延迟行就是 [SettingsDiagnosticsPhase.Content]；没有时按「有失败文案 → 失败态、
 * 已读回 → 空态、尚未读回 → 加载态」依次判定。失败时旧延迟行原样保留，
 * 因此形态仍是内容态，错误行另附在下面——与既有「失败不顶掉已有结果」的语义一致。
 */
internal data class SettingsDiagnostics(
    /** 是否已有一次读回落地（成功或失败）；探活开始后回到 false。 */
    val settled: Boolean = false,
    /** 重新探活是否在途。 */
    val probing: Boolean = false,
    /** 最近一次读回的失败文案；成功即清空。 */
    val errorMessage: String? = null,
    /** 当前实际线路域名；未解析时为 null。 */
    val usedDomain: String? = null,
    /** 最近一次读回的延迟表，按延迟升序。 */
    val latency: List<DomainLatency> = emptyList(),
) {

    /** 四态选择。 */
    val phase: SettingsDiagnosticsPhase
        get() = when {
            latency.isNotEmpty() -> SettingsDiagnosticsPhase.Content
            errorMessage != null -> SettingsDiagnosticsPhase.Failed
            settled -> SettingsDiagnosticsPhase.Empty
            else -> SettingsDiagnosticsPhase.Loading
        }

    /** 是否逐行渲染延迟表；否则显示一句占位文案。 */
    val showsLatencyRows: Boolean get() = phase == SettingsDiagnosticsPhase.Content

    /** 重试入口：探活进行中不重复发起，与页面按钮的禁用条件同源。 */
    val canRetry: Boolean get() = !probing

    /** 探活开始：只上在途标记，旧延迟行与失败文案都留到新结果回来。 */
    fun probingStarted(): SettingsDiagnostics = if (probing) this else copy(probing = true)

    /** 探活结束：只解锁按钮，结果由随后的读回写入。 */
    fun probingFinished(): SettingsDiagnostics = copy(probing = false)

    /** 一次读回开始：清掉上次失败文案并回到加载中；旧延迟行继续显示。 */
    fun reloading(): SettingsDiagnostics = copy(settled = false, errorMessage = null)

    /** 一次读回成功：写入实际线路与延迟表并清掉失败文案。 */
    fun succeeded(usedDomain: String?, latency: List<DomainLatency>): SettingsDiagnostics = copy(
        settled = true,
        errorMessage = null,
        usedDomain = usedDomain,
        latency = latency,
    )

    /** 一次读回失败：保留上一次的延迟行，只落失败文案。 */
    fun failed(message: String): SettingsDiagnostics = copy(settled = true, errorMessage = message)
}

/** 诊断面板的一行：标签与值分开给，字号与换行由皮肤决定。 */
internal data class SettingsDiagnosticLine(val label: String, val value: String)

/**
 * 诊断面板内容：环境与线路状态的只读汇总，不触网。
 *
 * [dynamicColorEffective] 由皮肤决定：ColorOS 固定自有色板，取色开关即便存着 true 也不生效，
 * 因此只在真正生效时追加「动态取色」；HyperOS 沿用既有判断，照实回显存储值。
 */
internal fun settingsDiagnosticLines(
    packageName: String,
    versionName: String,
    versionCode: Long,
    manufacturer: String,
    model: String,
    release: String,
    sdkInt: Int,
    usedDomain: String?,
    activeDomains: String,
    latency: List<DomainLatency>,
    paginationMode: PaginationMode,
    themeMode: ThemeMode,
    dynamicColorEffective: Boolean,
): List<SettingsDiagnosticLine> = listOf(
    SettingsDiagnosticLine(label = "包名", value = packageName),
    SettingsDiagnosticLine(label = "版本", value = versionName + " (" + versionCode + ")"),
    SettingsDiagnosticLine(label = "设备", value = manufacturer + " " + model),
    SettingsDiagnosticLine(label = "系统", value = "Android " + release + " (API " + sdkInt + ")"),
    SettingsDiagnosticLine(label = "当前线路", value = usedDomain ?: SETTINGS_DOMAIN_UNRESOLVED),
    SettingsDiagnosticLine(label = "自定义线路", value = activeDomains.ifBlank { SETTINGS_DOMAINS_DEFAULT }),
    SettingsDiagnosticLine(
        label = "线路延迟",
        value = latency.joinToString("\n") { it.domain + " · " + it.latencyMs + "ms" }
            .ifBlank { SETTINGS_LATENCY_PLACEHOLDER },
    ),
    SettingsDiagnosticLine(label = "分页模式", value = paginationMode.displayName),
    SettingsDiagnosticLine(
        label = "主题",
        value = themeMode.displayName + if (dynamicColorEffective) " · 动态取色" else "",
    ),
)

/** 窗口断点与偏好合并后的设置页展示值：两套皮肤共用同一套判定，避免各自推导出不同数值。 */
internal data class SettingsLayout(
    val paginationMode: PaginationMode,
    val bottomBarSize: BottomBarSize,
    val bottomBarSizeOption: BottomBarSize,
    val bottomBarForm: BottomBarForm,
    val wideScreenNavigation: WideScreenNavigation,
    val showWideScreenNavigation: Boolean,
    val readerSpreadAllowed: Boolean,
)

/**
 * 由偏好值与窗口尺寸推导展示值。
 *
 * 底栏尺寸档只有「偏大」是强制选择，「普通」与未设置同义（都按设备自适应）；
 * 宽屏导航与双页阅读只在 600dp 断点以上渲染整行，其余窗口整行不出现。
 */
internal fun resolveSettingsLayout(
    paginationMode: PaginationMode,
    bottomBarSize: BottomBarSize?,
    bottomBarForm: BottomBarForm?,
    wideScreenNavigation: WideScreenNavigation?,
    widthDp: Int,
    heightDp: Int,
): SettingsLayout = SettingsLayout(
    paginationMode = paginationMode,
    bottomBarSize = BottomBarPreferencePolicy.resolveSize(bottomBarSize, widthDp),
    bottomBarSizeOption = if (bottomBarSize == BottomBarSize.GENEROUS) BottomBarSize.GENEROUS else BottomBarSize.STANDARD,
    bottomBarForm = BottomBarPreferencePolicy.resolveForm(bottomBarForm),
    wideScreenNavigation = BottomBarPreferencePolicy.resolveActiveWideScreenNavigation(
        wideScreenNavigation,
        widthDp,
        heightDp,
    ),
    showWideScreenNavigation = BottomBarPreferencePolicy.supportsWideScreenNavigation(widthDp),
    readerSpreadAllowed = ReaderSpreadPolicy.supportsSpread(widthDp, heightDp),
)

/** 建立当前窗口下的设置页展示值；窗口尺寸或偏好变化时自动重算。 */
@Composable
internal fun rememberSettingsLayout(deps: ScreenDeps): SettingsLayout {
    val windowDp = rememberWindowDpSize()
    val widthDp = windowDp.width.toLayoutDpInt()
    val heightDp = windowDp.height.toLayoutDpInt()
    return resolveSettingsLayout(
        paginationMode = deps.paging.paginationMode,
        bottomBarSize = deps.bottomBar.bottomBarSize,
        bottomBarForm = deps.bottomBar.bottomBarForm,
        wideScreenNavigation = deps.bottomBar.wideScreenNavigation,
        widthDp = widthDp,
        heightDp = heightDp,
    )
}

/**
 * 骨架持有的可保存字段。
 *
 * 由 [rememberSettingsScaffoldState] 用 rememberSaveable 建立，类本身只读不建：
 * 旋转或进程重建后输入框、已生效线路、待确认清空项与弹窗开关都不会丢。
 * 线路诊断数据与「提交中/清空中」这类瞬时标记沿用 remember，与既有实现一致。
 */
internal class SettingsScaffoldFields internal constructor(
    val domainsInput: MutableState<String>,
    val activeDomains: MutableState<String>,
    val entered: MutableState<Boolean>,
    val applying: MutableState<Boolean>,
    val diagnostics: MutableState<SettingsDiagnostics>,
    val clearTarget: MutableState<SettingsClearTarget?>,
    val clearing: MutableState<Boolean>,
    val disclaimerVisible: MutableState<Boolean>,
    val diagnosticsVisible: MutableState<Boolean>,
)

/** 建立一组骨架字段：可保存的走 rememberSaveable，瞬时状态走 remember。 */
@Composable
private fun rememberSettingsScaffoldFields(): SettingsScaffoldFields = SettingsScaffoldFields(
    domainsInput = rememberSaveable { mutableStateOf("") },
    activeDomains = rememberSaveable { mutableStateOf("") },
    entered = rememberSaveable { mutableStateOf(false) },
    applying = remember { mutableStateOf(false) },
    diagnostics = remember { mutableStateOf(SettingsDiagnostics()) },
    clearTarget = rememberSaveable { mutableStateOf<SettingsClearTarget?>(null) },
    clearing = remember { mutableStateOf(false) },
    disclaimerVisible = rememberSaveable { mutableStateOf(false) },
    diagnosticsVisible = rememberSaveable { mutableStateOf(false) },
)

/**
 * 设置页共享骨架：两套皮肤逐字重复的状态编排、提交动作与文案。
 *
 * 骨架只管行为，不管长相：四态选择、探活重试、线路提交、清空确认、隐藏诊断与版本连点
 * 都在这里；皮肤侧拿状态去渲染自己的控件，弹窗外观也留在皮肤里。
 * 回退与跳转入口只做转发，导航语义仍由组装根决定。
 */
@Stable
internal class SettingsScaffoldState internal constructor(
    private val deps: ScreenDeps,
    private val scope: CoroutineScope,
    private val snackbarHostState: SnackbarHostState,
    val nav: AppRoutes,
    private val onBackRef: State<() -> Unit>,
    private val failureDetailRef: State<(Throwable?) -> String>,
    private val fields: SettingsScaffoldFields,
    /** 阅读设置七项的共享状态：两套皮肤各用自己的控件渲染。 */
    val readerState: ReaderSettingsState,
    /** 版本名：关于页的版本行逐字展示，取不到时为「未知」。 */
    val versionName: String,
    private val versionCode: Long,
    private val packageName: String,
) {

    /** 线路输入框原文，每行一个域名。 */
    val domainsInput: MutableState<String> get() = fields.domainsInput

    /** 已生效线路的展示文本（顿号分隔）。 */
    val activeDomains: MutableState<String> get() = fields.activeDomains

    /** 线路提交是否在途。 */
    val applying: MutableState<Boolean> get() = fields.applying

    /** 线路诊断区块状态。 */
    val diagnostics: MutableState<SettingsDiagnostics> get() = fields.diagnostics

    /** 待确认的破坏性清空目标；null 表示没有待确认项。 */
    val clearTarget: MutableState<SettingsClearTarget?> get() = fields.clearTarget

    /** 清空执行中。 */
    val clearing: MutableState<Boolean> get() = fields.clearing

    /** 免责声明弹窗是否可见。 */
    val disclaimerVisible: MutableState<Boolean> get() = fields.disclaimerVisible

    /** 隐藏诊断面板是否可见。 */
    val diagnosticsVisible: MutableState<Boolean> get() = fields.diagnosticsVisible

    /** 版本行连点计数：窗口内累计到阈值即打开诊断并清零。 */
    var versionTapCount by mutableIntStateOf(0)
        private set

    /** 最近一次待确认的清空目标：弹窗退场动画期间仍要能渲染文案。 */
    var lastClearTarget by mutableStateOf<SettingsClearTarget?>(null)
        private set

    /** 清空弹窗是否可见。 */
    val clearDialogVisible: Boolean get() = clearTarget.value != null

    /** 清空弹窗的文案来源：取最近一次目标，避免退场时翻成另一项的名字。 */
    val clearDialogTarget: SettingsClearTarget? get() = lastClearTarget

    /** 返回入口：由组装根注入，骨架只转发给皮肤的顶栏。 */
    val onBack: () -> Unit get() = onBackRef.value

    /** 一次诊断读回的竞态代号：只有后发起的那次代表最新线路表。 */
    private var diagnosticsRequest = 0

    /** 进页：读一次保存的线路，再读一次线路诊断；已进过页只刷新诊断。 */
    fun enter() {
        if (fields.entered.value) {
            loadDiagnostics()
            return
        }
        fields.entered.value = true
        scope.launch {
            val saved = deps.settings.apiDomains()
            domainsInput.value = saved.joinToString("\n")
            activeDomains.value = saved.joinToString("、")
            reloadDiagnostics()
        }
    }

    /** 读一次线路诊断（不等待结果）。 */
    fun loadDiagnostics() {
        scope.launch { reloadDiagnostics() }
    }

    /** 读一次线路诊断并等到写完：探活结束后要先刷新再解锁按钮。 */
    private suspend fun reloadDiagnostics() {
        val request = ++diagnosticsRequest
        diagnostics.value = diagnostics.value.reloading()
        // 两个网关查询合成一次结局：等待会话恢复、切 IO、判失效都在 AuthCall 内完成。
        val result = deps.authCall.call {
            deps.routes.usedDomain() to deps.routes.domainLatency().entries
                .sortedBy { it.value }
                .map { DomainLatency(domain = it.key, latencyMs = it.value) }
        }
        // 探活与应用线路都可能并发触发读取，只有后发起的那次才落界面。
        if (request != diagnosticsRequest) return
        when (result) {
            is SessionOutcome.Ok -> diagnostics.value =
                diagnostics.value.succeeded(usedDomain = result.value.first, latency = result.value.second)

            else -> diagnostics.value = diagnostics.value.failed(
                planLoad(result, hasContent = false, failureMessage = "线路诊断失败").message.orEmpty(),
            )
        }
    }

    /** 重新探活：在途时忽略重复点击，先刷新诊断再解锁按钮。 */
    fun requestReprobe() {
        if (!diagnostics.value.canRetry) return
        diagnostics.value = diagnostics.value.probingStarted()
        scope.launch {
            val result = deps.authCall.call { deps.routes.reprobeDomains() }
            // 先重读诊断再解锁：按钮解锁那一刻列表已是新延迟，不会停留在旧数字。
            reloadDiagnostics()
            diagnostics.value = diagnostics.value.probingFinished()
            when (result) {
                is SessionOutcome.Ok -> snackbarHostState.showSnackbar("线路探活完成")
                else -> snackbarHostState.showSnackbar(
                    "探活失败：" + planLoad(result, hasContent = false, failureMessage = "未知错误").message.orEmpty(),
                )
            }
        }
    }

    /** 提交自定义线路：写盘 → 读回归一结果 → 重建网关，成功后回填输入框并刷新诊断。 */
    fun applyDomains() {
        if (applying.value) return
        applying.value = true
        scope.launch {
            // 线路落盘与网关重建挂进程作用域：应用中途离开设置页不能留下半份配置。
            val result = deps.appScope.async {
                runCatchingSuspending {
                    deps.settings.setApiDomains(domainsInput.value.split("\n"))
                    val applied = deps.settings.apiDomains()
                    deps.applyDomains(applied)
                    applied
                }
            }.await()
            applying.value = false
            result.onSuccess { applied ->
                activeDomains.value = applied.joinToString("、")
                // 归一后的结果回填输入框，让用户看到真正生效的线路，而不是带协议头的原文。
                domainsInput.value = applied.joinToString("\n")
                // 网关已重建，诊断数据属于旧实例，重新读取。
                loadDiagnostics()
                snackbarHostState.showSnackbar(
                    if (applied.isEmpty()) "已恢复默认线路" else "已应用 " + applied.size + " 个线路域名",
                )
            }.onFailure { error ->
                snackbarHostState.showSnackbar("线路应用失败：" + failureDetailRef.value(error))
            }
        }
    }

    /** 分页模式提交：越界索引忽略，同值不重复落盘（判定沿用 [resolvePaginationSelection]）。 */
    fun applyPaginationIndex(index: Int) {
        resolvePaginationSelection(deps.paging.paginationMode, index)
            ?.takeIf { it.changed }
            ?.let { deps.selectPaginationMode(it.mode) }
    }

    fun applyBottomBarSize(size: BottomBarSize) = deps.selectBottomBarSize(size)

    fun applyBottomBarForm(form: BottomBarForm) = deps.selectBottomBarForm(form)

    fun applyWideScreenNavigation(navigation: WideScreenNavigation) = deps.selectWideScreenNavigation(navigation)

    /** 请求清空：只置入待确认目标，弹窗由皮肤渲染。 */
    fun requestClear(target: SettingsClearTarget) {
        lastClearTarget = target
        clearTarget.value = target
    }

    /** 取消清空确认：执行中不响应，与弹窗按钮的禁用条件同源。 */
    fun dismissClear() {
        if (clearing.value) return
        clearTarget.value = null
    }

    /** 确认清空：执行挂进程作用域，完成后先收弹窗再提示。 */
    fun confirmClear() {
        val target = clearTarget.value ?: return
        if (clearing.value) return
        clearing.value = true
        scope.launch {
            // 清库与删文件不挂页面作用域：确认后立刻返回也不能让清理半途中断。
            val result = deps.appScope.async {
                runCatchingSuspending {
                    when (target) {
                        SettingsClearTarget.HISTORY -> deps.history.clear()
                        SettingsClearTarget.DOWNLOADS -> deps.downloadManager.clearAll()
                    }
                }
            }.await()
            clearing.value = false
            clearTarget.value = null
            // showSnackbar 会挂起到提示消失：先收弹窗再提示，否则确认框会盖住提示条。
            snackbarHostState.showSnackbar(
                if (result.isSuccess) target.doneMessage else "清空失败：" + failureDetailRef.value(result.exceptionOrNull()),
            )
        }
    }

    /** 打开免责声明。 */
    fun showDisclaimer() {
        disclaimerVisible.value = true
    }

    /** 关闭免责声明。 */
    fun hideDisclaimer() {
        disclaimerVisible.value = false
    }

    /** 版本行连点：窗口内累计到阈值即打开隐藏诊断并清零。 */
    fun tapVersion() {
        versionTapCount += 1
        if (versionTapCount >= SETTINGS_DIAGNOSTIC_TAPS) {
            versionTapCount = 0
            diagnosticsVisible.value = true
        }
    }

    /** 连点计数在窗口内归零，避免误触累计；由骨架自己的 LaunchedEffect 触发。 */
    fun resetVersionTapsAfterWindow() {
        versionTapCount = 0
    }

    /** 关闭隐藏诊断面板。 */
    fun closeDiagnostics() {
        diagnosticsVisible.value = false
    }

    /** 检查更新入口：页内跳转仍走路由。 */
    fun openUpdate() = nav.update()

    /** 诊断面板内容：环境与线路状态的只读汇总。 */
    fun diagnosticLines(themeMode: ThemeMode, dynamicColorEffective: Boolean): List<SettingsDiagnosticLine> =
        settingsDiagnosticLines(
            packageName = packageName,
            versionName = versionName,
            versionCode = versionCode,
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            release = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            usedDomain = diagnostics.value.usedDomain,
            activeDomains = activeDomains.value,
            latency = diagnostics.value.latency,
            paginationMode = deps.paging.paginationMode,
            themeMode = themeMode,
            dynamicColorEffective = dynamicColorEffective,
        )
}

/** 全局设置写入失败时的兜底提示：与 [failureCopy] 的通用文案一致。 */
private const val APPLY_FAILURE_FALLBACK = "请稍后重试"

/**
 * 建立设置页共享骨架。
 *
 * [failureDetail] 只决定失败提示里「原因」怎么写：两套皮肤的历史文案不同（一套按失败归类，
 * 一套回显异常原文），该差异留在皮肤侧，骨架只负责流程与时机。
 * [clearConfirmTimeoutMs] 为 null 表示确认弹窗不自动收起：颜色皮肤 5 秒自动复位，浅色皮肤保持。
 */
@Composable
internal fun rememberSettingsScaffoldState(
    deps: ScreenDeps,
    nav: AppRoutes,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    failureDetail: (Throwable?) -> String = { error -> failureCopy(error, APPLY_FAILURE_FALLBACK) },
    clearConfirmTimeoutMs: Long? = null,
): SettingsScaffoldState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fields = rememberSettingsScaffoldFields()
    val readerState = rememberReaderSettingsState(deps.settings)
    val onBackRef = rememberUpdatedState(onBack)
    val failureDetailRef = rememberUpdatedState(failureDetail)
    val clearTimeoutRef = rememberUpdatedState(clearConfirmTimeoutMs)
    val state = remember(deps, nav, snackbarHostState, fields, readerState) {
        SettingsScaffoldState(
            deps = deps,
            scope = scope,
            snackbarHostState = snackbarHostState,
            nav = nav,
            onBackRef = onBackRef,
            failureDetailRef = failureDetailRef,
            fields = fields,
            readerState = readerState,
            versionName = readVersionName(context),
            versionCode = readVersionCode(context),
            packageName = context.packageName,
        )
    }

    LaunchedEffect(state) { state.enter() }

    // 连点计数在窗口内归零，避免误触累计
    LaunchedEffect(state.versionTapCount) {
        if (state.versionTapCount in 1 until SETTINGS_DIAGNOSTIC_TAPS) {
            delay(SETTINGS_DIAGNOSTIC_TAP_WINDOW_MS)
            state.resetVersionTapsAfterWindow()
        }
    }

    // 清空确认的自动收起：只有声明了时长的皮肤才启用
    val autoDismiss = clearTimeoutRef.value
    val clearTarget = fields.clearTarget
    val clearing = fields.clearing
    if (autoDismiss != null) {
        LaunchedEffect(clearTarget.value, clearing.value) {
            if (clearTarget.value != null && !clearing.value) {
                delay(autoDismiss)
                clearTarget.value = null
            }
        }
    }

    return state
}

/** 版本号：取不到时回落「未知」，与既有设置页一致。 */
private fun readVersionName(context: Context): String {
    @Suppress("DEPRECATION")
    return runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
        .getOrNull() ?: "未知"
}

/** 版本号数值：取不到时回落 0。 */
private fun readVersionCode(context: Context): Long {
    @Suppress("DEPRECATION")
    return runCatching { context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode }
        .getOrDefault(0L)
}
