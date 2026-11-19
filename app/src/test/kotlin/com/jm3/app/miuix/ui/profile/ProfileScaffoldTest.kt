package com.jm3.app.miuix.ui.profile

import androidx.compose.runtime.mutableStateOf
import com.jm3.app.miuix.ui.ProfileDraftPolicy
import com.jm3.app.miuix.ui.ProfileDraftState
import com.jm3.core.LOGIN_EXPIRED_MESSAGE
import com.jm3.core.model.Jm3User
import com.jm3.core.model.UserProfile
import com.jm3.core.session.SessionOutcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 资料骨架的行为测试：装载/保存的每个结局分支、换账号重置、重入拦截。
 *
 * 全部走 [ProfileAccountPort] 与 [ProfileNotice] 两个窄端口，因此不需要 Android、
 * 不需要 Miuix，也不需要真的网关。
 */
private class FakeProfilePort : ProfileAccountPort {
    var loadOutcome: SessionOutcome<UserProfile> = SessionOutcome.Failed("装载结局尚未摆放")
    var saveOutcome: SessionOutcome<UserProfile> = SessionOutcome.Failed("保存结局尚未摆放")

    /** 非 null 时保存停在这里，用来验证「提交中再点保存」被忽略。 */
    var saveGate: CompletableDeferred<Unit>? = null

    val loadCalls = mutableListOf<String>()
    val saveCalls = mutableListOf<SaveCall>()

    override suspend fun loadProfile(uid: String): SessionOutcome<UserProfile> {
        loadCalls += uid
        return loadOutcome
    }

    override suspend fun saveProfile(uid: String, fields: Map<String, String>): SessionOutcome<UserProfile> {
        saveCalls += SaveCall(uid, fields.toMap())
        saveGate?.await()
        return saveOutcome
    }
}

private data class SaveCall(val uid: String, val fields: Map<String, String>)

/** 假的提示通道：只记被要求弹的文案。 */
private class RecordingNotice : ProfileNotice {
    val messages = mutableListOf<String>()

    override suspend fun show(message: String) {
        messages += message
    }
}

private fun testUser(uid: String = "uid-1"): Jm3User = Jm3User(
    uid = uid,
    username = "鲸娘",
    email = null,
    emailVerified = false,
    photoUrl = null,
    coin = 120,
    albumFavorites = 0,
    maxAlbumFavorites = 0,
    level = 5,
    levelName = "萌新",
    nextLevelExp = 100L,
    currentExp = 30L,
    expPercent = 30,
)

private fun testProfile(
    username: String = "鲸娘",
    nickname: String = "小鲸",
): UserProfile = UserProfile(
    username = username,
    email = "",
    nickname = nickname,
    lastName = "尾",
    firstName = "鳍",
    birthday = "2000-01-01",
    relations = "Single",
    sexuality = "Girls",
    website = "",
    birthPlace = "海",
    city = "港",
    country = "CN",
    occupation = "学生",
    company = "",
    school = "",
    aboutMe = "",
    infoHere = "",
    collections = "",
    ideal = "",
    erogenic = "",
    favorite = "",
    hate = "",
)

/**
 * 状态机与它的替身。
 *
 * 账号用 [user] 这份快照 State 表达，测试可以在半途换账号来验证守卫；
 * 草稿自己持有，便于断言脏字段是否被清空。
 */
private class ProfileHarness(scope: CoroutineScope) {
    val user = mutableStateOf<Jm3User?>(null)
    val savedUidToken = mutableStateOf(ProfileDraftPolicy.UID_UNSET)
    val draft = ProfileDraftState()
    val port = FakeProfilePort()
    val notice = RecordingNotice()

    val subject = ProfileScaffoldState(
        scope = scope,
        port = port,
        notice = notice,
        draft = draft,
        userProvider = { user.value },
        savedUidToken = savedUidToken,
    )

    fun signedIn(uid: String = "uid-1") {
        user.value = testUser(uid)
    }
}

class ProfileScaffoldTest {

    @Test
    fun `初始态为加载中且尚未发起任何装载`() = runTest {
        val harness = ProfileHarness(this)

        assertEquals(ProfileUiState.Loading, harness.subject.uiState)
        assertFalse(harness.subject.saving)
        assertNull(harness.subject.profile)
        assertTrue(harness.port.loadCalls.isEmpty())
    }

    @Test
    fun `未登录装载落到请先登录且不发请求`() = runTest {
        val harness = ProfileHarness(this)

        harness.subject.load()
        advanceUntilIdle()

        assertEquals(
            ProfileUiState.Failed("请先登录后查看个人资料", sessionExpired = true),
            harness.subject.uiState,
        )
        assertTrue(harness.port.loadCalls.isEmpty())
    }

    @Test
    fun `已登录装载成功后进入已加载并回填服务端资料`() = runTest {
        val harness = ProfileHarness(this)
        harness.signedIn()
        harness.port.loadOutcome = SessionOutcome.Ok(testProfile())

        harness.subject.load()
        advanceUntilIdle()

        assertEquals(ProfileUiState.Loaded, harness.subject.uiState)
        assertEquals("鲸娘", harness.subject.profile?.username)
        assertEquals("小鲸", harness.subject.fields["nickname"])
        assertEquals("Single", harness.subject.fields["relations"])
        assertEquals(listOf("uid-1"), harness.port.loadCalls)
    }

    @Test
    fun `装载失败给整屏兜底文案且重新装载可以成功`() = runTest {
        val harness = ProfileHarness(this)
        harness.signedIn()
        harness.port.loadOutcome = SessionOutcome.Failed("boom")

        harness.subject.load()
        advanceUntilIdle()
        assertEquals(
            ProfileUiState.Failed("个人资料加载失败", sessionExpired = false),
            harness.subject.uiState,
        )

        harness.port.loadOutcome = SessionOutcome.Ok(testProfile())
        harness.subject.load()
        advanceUntilIdle()

        assertEquals(ProfileUiState.Loaded, harness.subject.uiState)
        assertEquals(listOf("uid-1", "uid-1"), harness.port.loadCalls)
    }

    @Test
    fun `装载时会话失效落到需要重新登录的失败态`() = runTest {
        val harness = ProfileHarness(this)
        harness.signedIn()
        harness.port.loadOutcome = SessionOutcome.Expired

        harness.subject.load()
        advanceUntilIdle()

        assertEquals(
            ProfileUiState.Failed(LOGIN_EXPIRED_MESSAGE, sessionExpired = true),
            harness.subject.uiState,
        )
    }

    @Test
    fun `已有资料时静默刷新失败保留已加载态并提示刷新失败`() = runTest {
        val harness = ProfileHarness(this)
        harness.signedIn()
        harness.port.loadOutcome = SessionOutcome.Ok(testProfile())
        harness.subject.load()
        advanceUntilIdle()

        harness.port.loadOutcome = SessionOutcome.Failed("boom")
        harness.subject.load(showLoading = false)
        advanceUntilIdle()

        assertEquals(ProfileUiState.Loaded, harness.subject.uiState)
        assertEquals(listOf("刷新失败：个人资料加载失败"), harness.notice.messages)
    }

    @Test
    fun `已有资料时静默刷新会话失效保留内容并提示重新登录`() = runTest {
        val harness = ProfileHarness(this)
        harness.signedIn()
        harness.port.loadOutcome = SessionOutcome.Ok(testProfile())
        harness.subject.load()
        advanceUntilIdle()

        harness.port.loadOutcome = SessionOutcome.Expired
        harness.subject.load(showLoading = false)
        advanceUntilIdle()

        assertEquals(ProfileUiState.Loaded, harness.subject.uiState)
        assertEquals(listOf(LOGIN_EXPIRED_MESSAGE), harness.notice.messages)
    }

    @Test
    fun `保存成功以服务端资料为准并清空未保存记录`() = runTest {
        val harness = ProfileHarness(this)
        harness.signedIn()
        harness.port.loadOutcome = SessionOutcome.Ok(testProfile())
        harness.subject.load()
        advanceUntilIdle()

        harness.subject.fields["nickname"] = "手写昵称"
        assertEquals(setOf("nickname"), harness.draft.dirtyKeys)
        harness.port.saveOutcome = SessionOutcome.Ok(testProfile(nickname = "手写昵称"))

        harness.subject.save()
        advanceUntilIdle()

        assertEquals(listOf("资料已保存"), harness.notice.messages)
        assertFalse(harness.subject.saving)
        assertEquals(emptySet(), harness.draft.dirtyKeys)
        assertEquals("手写昵称", harness.draft.profile?.nickname)
        assertEquals(1, harness.port.saveCalls.size)
        assertEquals("uid-1", harness.port.saveCalls.single().uid)
        assertEquals("手写昵称", harness.port.saveCalls.single().fields["nickname"])
        assertEquals("Single", harness.port.saveCalls.single().fields["relations"])
    }

    @Test
    fun `保存失败给保存失败前缀与兜底文案并保留用户输入`() = runTest {
        val harness = ProfileHarness(this)
        harness.signedIn()
        harness.port.loadOutcome = SessionOutcome.Ok(testProfile())
        harness.subject.load()
        advanceUntilIdle()

        harness.subject.fields["nickname"] = "手写昵称"
        harness.port.saveOutcome = SessionOutcome.Failed("boom")

        harness.subject.save()
        advanceUntilIdle()

        assertEquals(listOf("保存失败：请稍后重试"), harness.notice.messages)
        assertFalse(harness.subject.saving)
        assertTrue("nickname" in harness.draft.dirtyKeys)
        assertEquals("手写昵称", harness.subject.fields["nickname"])
    }

    @Test
    fun `保存时会话失效提示重新登录`() = runTest {
        val harness = ProfileHarness(this)
        harness.signedIn()
        harness.port.loadOutcome = SessionOutcome.Ok(testProfile())
        harness.subject.load()
        advanceUntilIdle()

        harness.port.saveOutcome = SessionOutcome.Expired
        harness.subject.save()
        advanceUntilIdle()

        assertEquals(listOf(LOGIN_EXPIRED_MESSAGE), harness.notice.messages)
    }

    @Test
    fun `未登录保存不发请求也不进入提交中`() = runTest {
        val harness = ProfileHarness(this)

        harness.subject.save()
        advanceUntilIdle()

        assertTrue(harness.port.saveCalls.isEmpty())
        assertFalse(harness.subject.saving)
        assertTrue(harness.notice.messages.isEmpty())
    }

    @Test
    fun `提交中再点保存被忽略`() = runTest {
        val harness = ProfileHarness(this)
        harness.signedIn()
        harness.port.loadOutcome = SessionOutcome.Ok(testProfile())
        harness.subject.load()
        advanceUntilIdle()

        val gate = CompletableDeferred<Unit>()
        harness.port.saveGate = gate
        harness.port.saveOutcome = SessionOutcome.Ok(testProfile())

        harness.subject.save()
        runCurrent()
        assertTrue(harness.subject.saving)

        harness.subject.save()
        runCurrent()
        assertEquals(1, harness.port.saveCalls.size)

        gate.complete(Unit)
        advanceUntilIdle()

        assertFalse(harness.subject.saving)
        assertEquals(listOf("资料已保存"), harness.notice.messages)
    }

    @Test
    fun `换账号进入时作废草稿并重新装载`() = runTest {
        val harness = ProfileHarness(this)
        harness.draft.apply(testProfile())
        harness.subject.fields["nickname"] = "上一个账号的草稿"
        harness.savedUidToken.value = ProfileDraftPolicy.uidToken("uid-1")
        harness.user.value = testUser("uid-2")
        harness.port.loadOutcome = SessionOutcome.Ok(testProfile(username = "鲸娘二号", nickname = "新号"))

        harness.subject.enter()
        advanceUntilIdle()

        assertEquals("u:uid-2", harness.savedUidToken.value)
        assertEquals(emptySet(), harness.draft.dirtyKeys)
        assertEquals("鲸娘二号", harness.draft.profile?.username)
        assertEquals("新号", harness.subject.fields["nickname"])
        assertEquals(listOf("uid-2"), harness.port.loadCalls)
    }

    @Test
    fun `同一账号重新进入不清空未保存草稿`() = runTest {
        val harness = ProfileHarness(this)
        harness.signedIn()
        harness.draft.apply(testProfile())
        harness.subject.fields["nickname"] = "手写昵称"
        harness.savedUidToken.value = ProfileDraftPolicy.uidToken("uid-1")
        harness.port.loadOutcome = SessionOutcome.Failed("boom")

        harness.subject.enter()
        advanceUntilIdle()

        assertEquals("u:uid-1", harness.savedUidToken.value)
        assertEquals("手写昵称", harness.draft.savedDrafts["nickname"])
        assertTrue("nickname" in harness.draft.dirtyKeys)
        assertEquals("手写昵称", harness.subject.fields["nickname"])
    }

    @Test
    fun `两个选择器的选项表与字段名`() {
        assertEquals("relations", ProfileSelector.RELATIONS.field)
        assertEquals("感情状态", ProfileSelector.RELATIONS.title)
        assertEquals("sexuality", ProfileSelector.SEXUALITY.field)
        assertEquals("性取向", ProfileSelector.SEXUALITY.title)
        assertEquals(4, ProfileSelector.RELATIONS.options().size)
        assertEquals("" to "未填写", ProfileSelector.RELATIONS.options().first())
        assertEquals("Guys + Girls" to "男女皆可", ProfileSelector.SEXUALITY.options().last())
    }

    @Test
    fun `选择器文案在空值与未知值上一律未填写`() {
        assertEquals("未填写", ProfileSelector.RELATIONS.labelFor(null))
        assertEquals("未填写", ProfileSelector.RELATIONS.labelFor(""))
        assertEquals("未填写", ProfileSelector.RELATIONS.labelFor("Unknown"))
        assertEquals("单身", ProfileSelector.RELATIONS.labelFor("Single"))
        assertEquals("非单身", ProfileSelector.RELATIONS.labelFor("Taken"))
        assertEquals("女性", ProfileSelector.SEXUALITY.labelFor("Girls"))
        assertEquals("男女皆可", ProfileSelector.SEXUALITY.labelFor("Guys + Girls"))
    }
}
