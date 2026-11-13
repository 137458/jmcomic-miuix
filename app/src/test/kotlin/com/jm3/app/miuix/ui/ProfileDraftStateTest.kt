package com.jm3.app.miuix.ui

import com.jm3.core.model.UserProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfileDraftStateTest {

    @Test
    fun `apply with resetDrafts 清空草稿并全量采纳服务端资料`() {
        val state = ProfileDraftState()
        state.apply(profile(nickname = "旧昵称"))
        state.noteEdit("nickname", "用户改的")
        assertTrue("nickname" in state.dirtyKeys)

        val saved = profile(nickname = "提交后的昵称")
        state.apply(saved, resetDrafts = true)

        assertTrue(state.dirtyKeys.isEmpty())
        assertTrue(state.savedDrafts.isEmpty())
        assertEquals(saved.editableFields(), state.backingFields.toMap())
        assertEquals(saved, state.profile)
    }

    @Test
    fun `apply 不覆盖用户正在编辑的脏字段`() {
        val state = ProfileDraftState()
        state.apply(profile(nickname = "旧昵称"))
        state.noteEdit("nickname", "我改的")

        state.apply(profile(nickname = "旧昵称"))

        assertEquals("我改的", state.backingFields["nickname"])
        assertTrue("nickname" in state.dirtyKeys)
    }

    @Test
    fun `apply 让非脏字段采用服务端新值`() {
        val state = ProfileDraftState()
        state.apply(profile(nickname = "旧昵称", birthday = "2000-01-01"))
        state.noteEdit("nickname", "我改的")

        state.apply(profile(nickname = "旧昵称", birthday = "1999-09-09"))

        assertEquals("我改的", state.backingFields["nickname"])
        assertEquals("1999-09-09", state.backingFields["birthday"])
    }

    @Test
    fun `reset 丢弃资料与全部草稿`() {
        val state = ProfileDraftState()
        state.apply(profile(nickname = "旧昵称"))
        state.noteEdit("nickname", "我改的")

        state.reset()

        assertNull(state.profile)
        assertTrue(state.dirtyKeys.isEmpty())
        assertTrue(state.savedDrafts.isEmpty())
        assertTrue(state.backingFields.isEmpty())
    }

    @Test
    fun `saver restore 还原脏字段与草稿而不带资料`() {
        val restored = assertNotNull(
            ProfileDraftStateSaver.restore(listOf("1", "nickname", "nickname", "我改的")),
        )

        assertEquals(setOf("nickname"), restored.dirtyKeys)
        assertEquals(mapOf("nickname" to "我改的"), restored.savedDrafts)
        assertNull(restored.profile)
        assertTrue(restored.backingFields.isEmpty())
    }
}

private fun profile(nickname: String, birthday: String = "2000-01-01"): UserProfile = UserProfile(
    username = "u1",
    email = "u1@example.com",
    nickname = nickname,
    lastName = "姓",
    firstName = "名",
    birthday = birthday,
    relations = "",
    sexuality = "",
    website = "",
    birthPlace = "",
    city = "",
    country = "",
    occupation = "",
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
