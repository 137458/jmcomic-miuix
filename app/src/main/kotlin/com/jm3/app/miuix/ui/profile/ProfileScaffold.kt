package com.jm3.app.miuix.ui.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.jm3.app.miuix.ScreenDeps
import com.jm3.app.miuix.failureCopy
import com.jm3.app.miuix.ui.ProfileDraftPolicy
import com.jm3.app.miuix.ui.ProfileDraftState
import com.jm3.app.miuix.ui.ProfileDraftStateSaver
import com.jm3.app.miuix.ui.ScreenLoadPhase
import com.jm3.app.miuix.ui.planLoad
import com.jm3.app.miuix.ui.trackedProfileFieldMap
import com.jm3.core.model.Jm3User
import com.jm3.core.model.UserProfile
import com.jm3.core.session.SessionOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.SnackbarHostState

/**
 * 个人资料的共享骨架：两套皮肤原先逐字重复的「枚举字段表 + 屏级三态 + 装载/保存编排」收在这里。
 *
 * 抽出来的是行为，不是外观：这里没有一处控件，皮肤只负责把 [ProfileScaffoldState] 画成自己的样子。
 * 两皮肤的真实差异用字段与参数表达，例如 COUI 把格式提示画在输入行 placeholder、HyperOS 塞进字段名，
 * 因此字段清单本身留在各自皮肤；选择器弹层形态（底部抽屉 vs 锚点气泡）同理。
 *
 * 唯一合并的语义差异：HyperOS 皮肤原本有「请求令牌 + 账号未变」的过期响应守卫，COUI 没有。
 * 收敛为始终保留守卫——正常路径（一次装载、一次保存）的事件顺序与提示文案不变，
 * 只在「响应回来前切了账号或又发起了新请求」时丢弃旧结局，这正是守卫存在的理由。
 */
internal enum class ProfileSelector(val title: String, val field: String) {
    RELATIONS("感情状态", "relations"),
    SEXUALITY("性取向", "sexuality"),
}

/** 感情状态的可选值：服务端只接受固定枚举，空串表示清空。 */
internal val ProfileRelationOptions: List<Pair<String, String>> = listOf(
    "" to "未填写",
    "Single" to "单身",
    "Taken" to "非单身",
    "Open" to "开放关系",
)

/** 性取向的可选值。 */
internal val ProfileSexualityOptions: List<Pair<String, String>> = listOf(
    "" to "未填写",
    "Guys" to "男性",
    "Girls" to "女性",
    "Guys + Girls" to "男女皆可",
)

internal fun ProfileSelector.options(): List<Pair<String, String>> = when (this) {
    ProfileSelector.RELATIONS -> ProfileRelationOptions
    ProfileSelector.SEXUALITY -> ProfileSexualityOptions
}

/** 枚举值 → 屏上文案；空值或未知值一律「未填写」，与两皮肤原有查表口径一致。 */
internal fun ProfileSelector.labelFor(value: String?): String =
    options().firstOrNull { it.first == value }?.second ?: "未填写"

/** 资料页的屏级三态。 */
internal sealed interface ProfileUiState {
    data object Loading : ProfileUiState
    data object Loaded : ProfileUiState

    /** [sessionExpired] 区分「重试可能好」和「会话没了」：后者只能去登录，给重试按钮是骗人的。 */
    data class Failed(val message: String, val sessionExpired: Boolean = false) : ProfileUiState
}

/**
 * 资料读写的窄端口：皮肤只注入「怎么读、怎么写」，状态类不碰 AuthCall 与网关。
 *
 * 纯 JVM 测试因此可以只实现两个挂起函数，把成功、失败、会话失效三种结局喂进来。
 */
internal interface ProfileAccountPort {
    suspend fun loadProfile(uid: String): SessionOutcome<UserProfile>

    suspend fun saveProfile(uid: String, fields: Map<String, String>): SessionOutcome<UserProfile>
}

/** 提示通道：把 SnackbarHostState 收成一句话，状态类不依赖任何皮肤控件。 */
internal interface ProfileNotice {
    suspend fun show(message: String)
}

private fun ScreenDeps.asProfileAccountPort(): ProfileAccountPort = object : ProfileAccountPort {
    override suspend fun loadProfile(uid: String): SessionOutcome<UserProfile> =
        authCall.call { gateway.userProfile(uid) }

    override suspend fun saveProfile(uid: String, fields: Map<String, String>): SessionOutcome<UserProfile> =
        authCall.call { gateway.editUserProfile(uid, fields) }
}

/**
 * 资料页的可变状态与动作：装载、保存、换账号重置草稿。
 *
 * [userProvider] 用取值函数而非快照值：皮肤在组合期读一次就会订阅账号流，
 * 状态类内部每次动作再取一次最新账号，不需要在构造时钉死。
 */
@Stable
internal class ProfileScaffoldState internal constructor(
    private val scope: CoroutineScope,
    private val port: ProfileAccountPort,
    private val notice: ProfileNotice,
    private val draft: ProfileDraftState,
    private val userProvider: () -> Jm3User?,
    private val savedUidToken: MutableState<String>,
) {
    /** 编辑缓冲：键与 [UserProfile.editableFields] 一致，改动即记脏。 */
    val fields: MutableMap<String, String> = trackedProfileFieldMap(draft.backingFields, draft::noteEdit)

    var uiState: ProfileUiState by mutableStateOf<ProfileUiState>(ProfileUiState.Loading)
        private set

    var saving: Boolean by mutableStateOf(false)
        private set

    private var requestToken = 0

    val user: Jm3User? get() = userProvider()

    /** 服务端资料；为 null 表示还没装载成功。 */
    val profile: UserProfile? get() = draft.profile

    /**
     * 装载当前账号资料。
     *
     * [showLoading] 为 false 时保留屏上已有内容（保存后的回读、重试静默刷新）。
     * 未登录不发请求：直接落到「请先登录」态并标记会话失效，重试按钮对这种情况没意义。
     */
    fun load(showLoading: Boolean = true) {
        val token = ++requestToken
        val uid = user?.uid
        if (uid == null) {
            uiState = ProfileUiState.Failed("请先登录后查看个人资料", sessionExpired = true)
            return
        }
        if (showLoading) uiState = ProfileUiState.Loading
        scope.launch {
            val outcome = port.loadProfile(uid)
            // 守卫：切账号、重进或又发起了新请求时，旧结局一律丢弃
            if (token != requestToken || user?.uid != uid) return@launch
            when (outcome) {
                is SessionOutcome.Ok -> {
                    draft.apply(outcome.value)
                    uiState = ProfileUiState.Loaded
                }

                // 失效与失败同一落点：planLoad 内部已按 phase 分流
                else -> {
                    val plan = planLoad(
                        outcome = outcome,
                        hasContent = draft.profile != null,
                        failureMessage = "个人资料加载失败",
                    )
                    if (plan.keepContent) {
                        notice.show(plan.notice.orEmpty())
                    } else {
                        uiState = ProfileUiState.Failed(
                            message = plan.message.orEmpty(),
                            sessionExpired = plan.phase == ScreenLoadPhase.Expired,
                        )
                    }
                }
            }
        }
    }

    /** 提交编辑缓冲：成功后以服务端返回为准并清空未保存记录；失败按会话失效/普通失败给文案。 */
    fun save() {
        val uid = user?.uid ?: return
        if (saving) return
        saving = true
        scope.launch {
            val outcome = port.saveProfile(uid, fields.toMap())
            saving = false
            // 守卫：提交期间换了账号，结果不能写进新账号的草稿
            if (user?.uid != uid) return@launch
            when (outcome) {
                is SessionOutcome.Ok -> {
                    draft.apply(outcome.value, resetDrafts = true)
                    notice.show("资料已保存")
                }

                SessionOutcome.Expired -> notice.show(
                    planLoad(outcome, hasContent = false, failureMessage = "保存失败").message.orEmpty(),
                )

                is SessionOutcome.Failed -> notice.show("保存失败：" + failureCopy(outcome, "请稍后重试"))
            }
        }
    }

    /** 进屏与换账号的唯一入口：账号变了先作废草稿，再重新装载。 */
    suspend fun enter() {
        val uid = user?.uid
        if (ProfileDraftPolicy.shouldResetOnUidEffect(savedUidToken.value, uid)) {
            savedUidToken.value = ProfileDraftPolicy.uidToken(uid)
            draft.reset()
        }
        load()
    }
}

/**
 * 建一份资料页状态。
 *
 * 草稿与账号令牌用 rememberSaveable 保存：跨旋转恢复未保存修改是原有行为，不能因为搬进骨架丢掉。
 * [snackbarHostState] 只在这里被包成 [ProfileNotice]，状态类本身不依赖它。
 */
@Composable
internal fun rememberProfileScaffoldState(
    deps: ScreenDeps,
    snackbarHostState: SnackbarHostState,
): ProfileScaffoldState {
    val scope = rememberCoroutineScope()
    val userState = deps.account.user.collectAsState()
    val savedUidToken = rememberSaveable { mutableStateOf(ProfileDraftPolicy.UID_UNSET) }
    // 服务端资料、脏字段、未保存草稿与编辑缓冲同属一份草稿状态：跨旋转由 Saver 保留
    val draft = rememberSaveable(saver = ProfileDraftStateSaver) { ProfileDraftState() }
    val notice = remember(snackbarHostState) {
        object : ProfileNotice {
            override suspend fun show(message: String) {
                snackbarHostState.showSnackbar(message)
            }
        }
    }
    val port = remember(deps) { deps.asProfileAccountPort() }
    val state = remember(deps, scope, draft, notice, port, savedUidToken) {
        ProfileScaffoldState(
            scope = scope,
            port = port,
            notice = notice,
            draft = draft,
            userProvider = { userState.value },
            savedUidToken = savedUidToken,
        )
    }
    LaunchedEffect(state, userState.value?.uid) { state.enter() }
    return state
}
