package com.jm3.app.miuix.ui

import com.jm3.core.AlbumDetailPort
import com.jm3.core.history.InMemoryHistoryRepository
import com.jm3.core.model.AlbumDetail
import com.jm3.core.model.Chapter
import com.jm3.core.model.CommentItem
import com.jm3.core.model.CommentPage
import com.jm3.core.model.CommentSegment
import com.jm3.core.model.FavoriteFolderAction
import com.jm3.core.model.FavoritePage
import com.jm3.core.model.FolderOperation
import com.jm3.core.session.AuthCall
import com.jm3.core.session.StartupGate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class StatePersistenceTest {

    @Test
    fun `DraftResetPolicy preserves draft on rotation when still open on same target`() {
        assertFalse(
            DraftResetPolicy.shouldResetDraft(
                wasOpen = true,
                isOpen = true,
                previousTargetKey = "c1",
                currentTargetKey = "c1",
            ),
        )
        assertFalse(
            DraftResetPolicy.shouldResetDraft(
                wasOpen = true,
                isOpen = true,
                previousTargetKey = null,
                currentTargetKey = null,
            ),
        )
    }

    @Test
    fun `DraftResetPolicy resets draft on closed to open and on target switch`() {
        assertTrue(
            DraftResetPolicy.shouldResetDraft(
                wasOpen = false,
                isOpen = true,
                previousTargetKey = "c1",
                currentTargetKey = "c1",
            ),
        )
        assertTrue(
            DraftResetPolicy.shouldResetDraft(
                wasOpen = true,
                isOpen = true,
                previousTargetKey = "c1",
                currentTargetKey = "c2",
            ),
        )
        assertFalse(
            DraftResetPolicy.shouldResetDraft(
                wasOpen = true,
                isOpen = false,
                previousTargetKey = "c1",
                currentTargetKey = null,
            ),
        )
    }

    @Test
    fun `CommentItemCodec round trips complex nested comment fields and delimiters`() {
        val original = CommentItem(
            id = "c-100",
            author = "作者:Alice|Test\nNext",
            content = "第一行\n1:0:[我推荐这本书 JM42];;special",
            postDate = "2026-11-11",
            likes = 42,
            replies = listOf(
                CommentItem(
                    id = "c-101",
                    author = "Bob",
                    content = "子回复正文",
                    postDate = null,
                    likes = 0,
                    replies = emptyList(),
                    albumId = "0",
                    avatarUrl = "https://example.com/a.jpg",
                    level = 3,
                    levelName = "见习",
                    badges = listOf("https://example.com/b1.png", "https://example.com/b2.png"),
                    albumName = null,
                    albumCoverUrl = null,
                    body = emptyList(),
                ),
            ),
            albumId = "42",
            avatarUrl = "https://example.com/avatar.png",
            level = 12,
            levelName = "资深;用户",
            badges = listOf("https://example.com/badge.png"),
            albumName = "JM42",
            albumCoverUrl = "https://example.com/cover.jpg",
            body = listOf(
                CommentSegment.Text("看这里 "),
                CommentSegment.Album(albumId = "42", label = "[我推荐这本书 JM42]"),
            ),
        )

        val encoded = CommentItemCodec.encode(original)
        val decoded = CommentItemCodec.decode(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `CommentItemCodec handles minimal comment and rejects corrupt payload safely`() {
        val minimal = CommentItem(
            id = "",
            author = "",
            content = "",
            postDate = null,
            likes = 0,
            replies = emptyList(),
        )
        assertEquals(minimal, CommentItemCodec.decode(CommentItemCodec.encode(minimal)))
        assertNull(CommentItemCodec.decode(""))
        assertNull(CommentItemCodec.decode("corrupt-payload"))
    }

    @Test
    fun `ProfileDraftPolicy keeps dirty drafts across rotation and resets on account change`() {
        val sameUid = ProfileDraftPolicy.uidToken("1001")
        assertFalse(ProfileDraftPolicy.shouldResetOnUidEffect(sameUid, "1001"))

        val sameLoggedOut = ProfileDraftPolicy.uidToken(null)
        assertFalse(ProfileDraftPolicy.shouldResetOnUidEffect(sameLoggedOut, null))

        assertTrue(ProfileDraftPolicy.shouldResetOnUidEffect(ProfileDraftPolicy.UID_UNSET, "1001"))
        assertTrue(ProfileDraftPolicy.shouldResetOnUidEffect(sameUid, "2002"))
        assertTrue(ProfileDraftPolicy.shouldResetOnUidEffect(sameUid, null))
        assertTrue(ProfileDraftPolicy.shouldResetOnUidEffect(sameLoggedOut, "1001"))
    }

    @Test
    fun `ProfileDraftPolicy merge preserves dirty user edits including intentional blanking`() {
        val server = mapOf(
            "nickname" to "旧昵称",
            "city" to "上海",
            "aboutMe" to "服务端简介",
        )
        val drafts = mapOf(
            "nickname" to "",
            "city" to "北京",
            "extra" to "未提交新字段",
        )
        val merged = ProfileDraftPolicy.mergeProfileFields(
            serverFields = server,
            savedDrafts = drafts,
            dirtyKeys = setOf("nickname", "city", "extra"),
        )
        assertEquals("", merged["nickname"])
        assertEquals("北京", merged["city"])
        assertEquals("服务端简介", merged["aboutMe"])
        assertEquals("未提交新字段", merged["extra"])
    }

    @Test
    fun `AlbumDetailStateHolder saveUiSnapshot and restoreUiSnapshot preserve interaction state`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val testScope = CoroutineScope(dispatcher)
        val gate = object : StartupGate {
            override suspend fun awaitReady() = Unit
            override suspend fun onSessionExpired() = Unit
        }
        val port = object : AlbumDetailPort {
            override fun album(albumId: String) = AlbumDetail(
                id = albumId,
                title = "t",
                description = "d",
                authors = emptyList(),
                tags = emptyList(),
                works = emptyList(),
                actors = emptyList(),
                likes = "0",
                views = "0",
                coverUrl = "",
                chapters = listOf(Chapter("c1", "1", 1)),
                favorited = false,
                liked = false,
                related = emptyList(),
            )
            override fun comments(albumId: String, page: Int) = CommentPage(0, emptyList())
            override fun postComment(albumId: String, text: String) = Unit
            override fun replyComment(albumId: String, text: String, parentCommentId: String) = Unit
            override fun toggleLike(albumId: String) = Unit
            override fun isTracking(albumId: String) = false
            override fun setTracking(albumId: String) = Unit
            override fun favorites(page: Int) = FavoritePage(1, 1, emptyList())
            override fun manageFavoriteFolder(action: FavoriteFolderAction, folderId: String, name: String, albumId: String) =
                FolderOperation(true, null)
            override fun toggleFavorite(albumId: String, folderId: String) = Unit
        }

        val firstHolder = AlbumDetailStateHolder(
            albumId = "99",
            gateway = port,
            authCall = AuthCall(gate, dispatcher),
            history = InMemoryHistoryRepository(),
            scope = testScope,
            showMessage = {},
            onOpenLogin = {},
            engagement = AlbumEngagementStore(),
        )
        val target = CommentItem(id = "c9", author = "Tester", content = "hi", postDate = null, likes = 1, replies = emptyList())
        firstHolder.setCommentText("草稿内容")
        firstHolder.setReplyTarget(target)
        firstHolder.setDescriptionExpanded(true)
        firstHolder.toggleGroup("g2")
        firstHolder.openFolderSheet()

        val snapshot = firstHolder.saveUiSnapshot()
        assertEquals("草稿内容", snapshot.commentDraft)
        assertEquals(target, snapshot.replyTarget)
        assertTrue(snapshot.descriptionExpanded)
        assertEquals(setOf("g2"), snapshot.expandedGroups)
        assertTrue(snapshot.showFolderSheet)

        val restoredHolder = AlbumDetailStateHolder(
            albumId = "99",
            gateway = port,
            authCall = AuthCall(gate, dispatcher),
            history = InMemoryHistoryRepository(),
            scope = testScope,
            showMessage = {},
            onOpenLogin = {},
            engagement = AlbumEngagementStore(),
        )
        restoredHolder.restoreUiSnapshot(snapshot)

        assertEquals("草稿内容", restoredHolder.commentDraft.value)
        assertNotNull(restoredHolder.uiState.value.replyTarget)
        assertEquals(target, restoredHolder.uiState.value.replyTarget)
        assertTrue(restoredHolder.uiState.value.descriptionExpanded)
        assertEquals(setOf("g2"), restoredHolder.uiState.value.expandedGroups)
        assertTrue(restoredHolder.uiState.value.showFolderSheet)

        testScope.cancel()
    }
}
