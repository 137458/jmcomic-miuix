package com.jm3.core

import com.jm3.core.model.CookieSnapshot
import com.jm3.core.model.Jm3Session
import com.jm3.core.model.Jm3User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SessionCodecTest {

    private fun session() = Jm3Session(
        user = Jm3User(
            uid = "u1",
            username = "alice",
            email = "a@b.c",
            emailVerified = true,
            photoUrl = null,
            coin = 10,
            albumFavorites = 3,
            maxAlbumFavorites = 100,
            level = 5,
            levelName = "LV5",
            nextLevelExp = 1000L,
            currentExp = 500L,
            expPercent = 50,
        ),
        cookies = listOf(CookieSnapshot("AVS", "abc123", "www.jmcomic.com", "/")),
    )

    @Test
    fun 编解码_往返_字段全等() {
        assertEquals(session(), SessionCodec.decode(SessionCodec.encode(session())))
    }

    @Test
    fun 解码_损坏输入_返回null不抛异常() {
        assertNull(SessionCodec.decode("not-a-session"))
        assertNull(SessionCodec.decode(""))
    }
}
