// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.favorites

import com.jm3.core.model.FavoriteFolderAction
import com.jm3.core.model.FavoritePage
import com.jm3.core.model.FolderOperation
import com.jm3.core.model.SearchResult
import com.jm3.core.model.TagFavorite
import com.jm3.core.session.SessionOutcome
import java.net.SocketTimeoutException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 收藏页骨架的状态机与文案测试（纯 kotlin.test，不碰 Android/Compose）。
 *
 * 覆盖：三个动作态（收藏夹、移动、标签）的初始态、成功、失败、重试/清空，
 * 会话失效要登录的分支，以及名册派生与切片的退化口径。
 */
class FavoriteScaffoldTest {

    private val album = SearchResult(
        id = "42",
        title = "作品标题",
        authors = listOf("作者"),
        tags = emptyList(),
        coverUrl = null,
    )

    private val timeout = SocketTimeoutException("read timed out")

    // ── 收藏夹弹窗 ──

    @Test
    fun `收藏夹动作初始态没有弹窗也没有错误`() {
        val ops = FavoriteFolderOps()
        assertFalse(ops.dialogVisible)
        assertFalse(ops.deleteVisible)
        assertNull(ops.editTarget)
        assertNull(ops.deleteTarget)
        assertNull(ops.errorMessage)
        assertEquals("", ops.nameInput)
        assertFalse(ops.busy)
    }

    @Test
    fun `新建入口清空名称并露出新建弹窗`() {
        val ops = FavoriteFolderOps().named("旧名字").creating()
        assertTrue(ops.dialogVisible)
        assertEquals(FAVORITE_ADD_FOLDER_TITLE, ops.dialogTitle)
        assertEquals(FAVORITE_CREATE_CONFIRM, ops.confirmLabel)
        assertEquals("", ops.nameInput)
        assertNull(ops.editTarget)
        assertEquals(true to 0, ops.dialogMode)
    }

    @Test
    fun `重命名入口带出当前收藏夹名称并切到保存`() {
        val ops = FavoriteFolderOps().renaming(3, "科幻小说")
        assertTrue(ops.dialogVisible)
        assertEquals(FAVORITE_EDIT_FOLDER_TITLE, ops.dialogTitle)
        assertEquals(FAVORITE_EDIT_CONFIRM, ops.confirmLabel)
        assertEquals("科幻小说", ops.nameInput)
        assertEquals(3 to "科幻小说", ops.editTarget)
        assertEquals(false to 3, ops.dialogMode)
    }

    @Test
    fun `关闭弹窗只清可见性与错误并保留没写完的输入`() {
        val ops = FavoriteFolderOps(
            showCreate = true,
            nameInput = "还没写完",
            request = FavoriteRequestState(errorMessage = FAVORITE_FOLDER_NAME_BLANK),
        ).dismissed()
        assertFalse(ops.dialogVisible)
        assertNull(ops.errorMessage)
        assertEquals("还没写完", ops.nameInput)
    }

    @Test
    fun `名称为空白时只写回弹窗错误不发请求`() {
        val submit = FavoriteFolderOps(showCreate = true, nameInput = "   ").submitRequest()
        assertNull(submit.action)
        assertEquals(FAVORITE_FOLDER_NAME_BLANK, submit.ops.errorMessage)
        assertFalse(submit.ops.busy)
        assertEquals("   ", submit.ops.nameInput)
    }

    @Test
    fun `新建提交带 add 动作与去掉空白的名称`() {
        val submit = FavoriteFolderOps(showCreate = true, nameInput = " 新夹 ").submitRequest()
        assertEquals(FavoriteFolderAction.ADD, submit.action)
        assertEquals("", submit.folderId)
        assertEquals("新夹", submit.name)
        assertTrue(submit.ops.busy)
        assertNull(submit.ops.errorMessage)
    }

    @Test
    fun `重命名提交带 edit 动作与收藏夹 id`() {
        val submit = FavoriteFolderOps(editTarget = 7 to "原名", nameInput = "新名").submitRequest()
        assertEquals(FavoriteFolderAction.EDIT, submit.action)
        assertEquals("7", submit.folderId)
        assertEquals("新名", submit.name)
        assertTrue(submit.ops.busy)
    }

    @Test
    fun `删除提交带 del 动作且不带名称`() {
        val submit = FavoriteFolderOps(deleteTarget = 5 to "旧夹").deleteRequest()
        assertEquals(FavoriteFolderAction.DELETE, submit.action)
        assertEquals("5", submit.folderId)
        assertEquals("", submit.name)
    }

    @Test
    fun `动作进行中时忽略再次提交`() {
        val busy = FavoriteFolderOps(
            showCreate = true,
            nameInput = "新夹",
            deleteTarget = 5 to "旧夹",
            request = FavoriteRequestState(busy = true),
        )
        assertNull(busy.submitRequest().action)
        assertNull(busy.deleteRequest().action)
        assertTrue(busy.submitRequest().ops.busy)
        assertEquals("新夹", busy.submitRequest().ops.nameInput)
    }

    @Test
    fun `删掉当前所在的收藏夹后回落到全部且不重复重拉`() {
        val effect = FavoriteFolderOps(
            deleteTarget = 5 to "旧夹",
            request = FavoriteRequestState(busy = true),
        ).succeeded(FavoriteFolderAction.DELETE, "5", currentFolderId = 5)
        assertTrue(effect.resetFolder)
        assertEquals(FavoriteFeedReload.None, effect.feedReload)
        assertNull(effect.ops.deleteTarget)
        assertFalse(effect.ops.dialogVisible)
        assertEquals("", effect.ops.nameInput)
        assertFalse(effect.ops.busy)
        assertNull(effect.notice)
        assertFalse(effect.loginRequired)
    }

    @Test
    fun `删掉别的收藏夹只重拉第一页`() {
        val effect = FavoriteFolderOps(deleteTarget = 5 to "旧夹")
            .succeeded(FavoriteFolderAction.DELETE, "5", currentFolderId = 0)
        assertFalse(effect.resetFolder)
        assertEquals(FavoriteFeedReload.FirstPage, effect.feedReload)
    }

    @Test
    fun `改名成功关闭弹窗并重拉第一页`() {
        val effect = FavoriteFolderOps(editTarget = 7 to "旧名", nameInput = "新名")
            .succeeded(FavoriteFolderAction.EDIT, "7", currentFolderId = 7)
        assertFalse(effect.resetFolder)
        assertEquals(FavoriteFeedReload.FirstPage, effect.feedReload)
        assertNull(effect.ops.editTarget)
        assertFalse(effect.ops.dialogVisible)
    }

    @Test
    fun `收藏夹接口回 ok=false 时留在弹窗内并解锁`() {
        val effect = FavoriteFolderOps(showCreate = true, nameInput = "重名夹").reduce(
            action = FavoriteFolderAction.ADD,
            targetId = "",
            currentFolderId = 0,
            outcome = SessionOutcome.Ok(FolderOperation(ok = false, message = "收藏夹重名")),
            failureText = ::favoriteDetailFailure,
        )
        assertEquals("收藏夹重名", effect.ops.errorMessage)
        assertFalse(effect.ops.busy)
        assertTrue(effect.ops.dialogVisible)
        assertEquals(FavoriteFeedReload.None, effect.feedReload)
        assertFalse(effect.loginRequired)
    }

    @Test
    fun `收藏夹接口回 ok=false 且没有消息时用操作失败兜底`() {
        val effect = FavoriteFolderOps(showCreate = true).reduce(
            action = FavoriteFolderAction.ADD,
            targetId = "",
            currentFolderId = 0,
            outcome = SessionOutcome.Ok(FolderOperation(ok = false, message = null)),
            failureText = ::favoriteDetailFailure,
        )
        assertEquals(FAVORITE_ACTION_FALLBACK, effect.ops.errorMessage)
    }

    @Test
    fun `收藏夹动作的失败文案由注入策略决定`() {
        val outcome = SessionOutcome.Failed(message = "read timed out", cause = timeout)
        val detail = FavoriteFolderOps(showCreate = true).reduce(
            action = FavoriteFolderAction.ADD,
            targetId = "",
            currentFolderId = 0,
            outcome = outcome,
            failureText = ::favoriteDetailFailure,
        )
        val fixed = FavoriteFolderOps(showCreate = true).reduce(
            action = FavoriteFolderAction.ADD,
            targetId = "",
            currentFolderId = 0,
            outcome = outcome,
            failureText = ::favoriteFixedFailure,
        )
        assertEquals("网络超时，请稍后重试", detail.ops.errorMessage)
        assertEquals(FAVORITE_ACTION_FALLBACK + FAVORITE_FAILURE_SUFFIX, fixed.ops.errorMessage)
        assertFalse(detail.ops.busy)
        assertFalse(fixed.ops.busy)
    }

    @Test
    fun `失败后可以重新提交并清掉上次错误`() {
        val failed = FavoriteFolderOps(showCreate = true, nameInput = "新夹").failed(FAVORITE_ACTION_FALLBACK)
        assertEquals(FAVORITE_ACTION_FALLBACK, failed.errorMessage)
        assertFalse(failed.busy)
        val retry = failed.submitRequest()
        assertEquals(FavoriteFolderAction.ADD, retry.action)
        assertNull(retry.ops.errorMessage)
        assertTrue(retry.ops.busy)
    }

    @Test
    fun `请求未结束前先解锁的转移保持错误文案`() {
        val state = FavoriteRequestState(busy = true)
        assertEquals(state, state.started())
        assertEquals(FavoriteRequestState(busy = false), state.finished())
        assertEquals(FavoriteRequestState(errorMessage = "操作失败"), FavoriteRequestState().failed("操作失败"))
        assertNull(FavoriteRequestState(errorMessage = "操作失败").cleared().errorMessage)
    }

    // ── 移动到收藏夹 ──

    @Test
    fun `移动初始态没有选择层也没有错误`() {
        val ops = FavoriteMoveOps()
        assertFalse(ops.visible)
        assertNull(ops.target)
        assertNull(ops.errorMessage)
        assertFalse(ops.busy)
    }

    @Test
    fun `移动入口带出作品并清掉上次错误`() {
        val ops = FavoriteMoveOps(request = FavoriteRequestState(errorMessage = FAVORITE_MOVE_FALLBACK))
            .opening(album)
        assertTrue(ops.visible)
        assertEquals(album, ops.target)
        assertNull(ops.errorMessage)
    }

    @Test
    fun `移动进行中既不关层也不接受第二次选择`() {
        val busy = FavoriteMoveOps(target = album, request = FavoriteRequestState(busy = true))
        assertEquals(album, busy.dismissing().target)
        assertNull(busy.moveRequest(folderId = 2, folderName = "科幻"))
    }

    @Test
    fun `移动请求带作品 id 与目标收藏夹`() {
        val submit = FavoriteMoveOps(target = album).moveRequest(folderId = 2, folderName = "科幻")
        assertNotNull(submit)
        assertEquals("42", submit.albumId)
        assertEquals(2, submit.targetFolderId)
        assertEquals("科幻", submit.folderName)
        assertTrue(submit.ops.busy)
    }

    @Test
    fun `移动成功关掉选择层提示并刷新列表`() {
        val effect = FavoriteMoveOps(target = album, request = FavoriteRequestState(busy = true))
            .reduce(folderName = "科幻", outcome = SessionOutcome.Ok(FolderOperation(ok = true, message = null)), failureText = ::favoriteFixedFailure)
        assertNull(effect.ops.target)
        assertFalse(effect.ops.visible)
        assertEquals("已移动到「科幻」", effect.notice)
        assertEquals(FavoriteFeedReload.Refresh, effect.feedReload)
        assertFalse(effect.ops.busy)
        assertFalse(effect.loginRequired)
    }

    @Test
    fun `移动失败保留选择层并写固定文案`() {
        val effect = FavoriteMoveOps(target = album, request = FavoriteRequestState(busy = true)).reduce(
            folderName = "科幻",
            outcome = SessionOutcome.Failed(message = "boom", cause = null),
            failureText = ::favoriteFixedFailure,
        )
        assertEquals(album, effect.ops.target)
        assertEquals(FAVORITE_MOVE_FALLBACK + FAVORITE_FAILURE_SUFFIX, effect.ops.errorMessage)
        assertEquals(FavoriteFeedReload.None, effect.feedReload)
        assertNull(effect.notice)
        assertFalse(effect.ops.busy)
    }

    @Test
    fun `移动时会话失效提示一次并要求登录`() {
        val effect = FavoriteMoveOps(target = album, request = FavoriteRequestState(busy = true))
            .reduce(folderName = "科幻", outcome = SessionOutcome.Expired, failureText = ::favoriteFixedFailure)
        assertEquals(SessionOutcome.Expired.message, effect.notice)
        assertTrue(effect.loginRequired)
        assertEquals(FavoriteFeedReload.None, effect.feedReload)
        assertEquals(album, effect.ops.target)
        assertFalse(effect.ops.busy)
    }

    // ── 标签收藏 ──

    @Test
    fun `标签动作初始态没有弹窗也没有错误`() {
        val ops = FavoriteTagOps()
        assertFalse(ops.addVisible)
        assertFalse(ops.deleteVisible)
        assertNull(ops.deleteTarget)
        assertEquals("", ops.input)
        assertNull(ops.errorMessage)
        assertFalse(ops.busy)
    }

    @Test
    fun `标签为空时只弹提示不发请求`() {
        val submit = FavoriteTagOps(showAdd = true, input = "   ").addRequest(existing = emptyList())
        assertEquals(FAVORITE_TAG_BLANK, submit.notice)
        assertNull(submit.tag)
        assertFalse(submit.ops.busy)
        assertTrue(submit.ops.addVisible)
        assertNull(submit.ops.errorMessage)
    }

    @Test
    fun `标签重复时只弹提示不发请求`() {
        val submit = FavoriteTagOps(showAdd = true, input = "合集").addRequest(
            existing = listOf(TagFavorite(tag = "合集", updatedAt = null)),
        )
        assertEquals(FAVORITE_TAG_DUPLICATE, submit.notice)
        assertNull(submit.tag)
        assertFalse(submit.ops.busy)
    }

    @Test
    fun `添加标签请求去掉首尾空白`() {
        val submit = FavoriteTagOps(showAdd = true, input = " 科幻 ").addRequest(existing = emptyList())
        assertEquals("科幻", submit.tag)
        assertNull(submit.notice)
        assertTrue(submit.ops.busy)
    }

    @Test
    fun `添加标签成功关弹窗清输入提示并刷新标签`() {
        val effect = FavoriteTagOps(showAdd = true, input = "科幻", request = FavoriteRequestState(busy = true))
            .reduceAdd(tag = "科幻", outcome = SessionOutcome.Ok(Unit), failureText = ::favoriteFixedFailure)
        assertFalse(effect.ops.showAdd)
        assertEquals("", effect.ops.input)
        assertEquals("已添加标签 #科幻", effect.notice)
        assertTrue(effect.reloadTags)
        assertFalse(effect.ops.busy)
        assertFalse(effect.loginRequired)
    }

    @Test
    fun `添加标签失败留在弹窗内并写固定文案`() {
        val effect = FavoriteTagOps(showAdd = true, input = "科幻", request = FavoriteRequestState(busy = true)).reduceAdd(
            tag = "科幻",
            outcome = SessionOutcome.Failed(message = "boom", cause = null),
            failureText = ::favoriteFixedFailure,
        )
        assertTrue(effect.ops.showAdd)
        assertEquals("科幻", effect.ops.input)
        assertEquals(FAVORITE_ADD_TAG_FALLBACK + FAVORITE_FAILURE_SUFFIX, effect.ops.errorMessage)
        assertFalse(effect.reloadTags)
        assertNull(effect.notice)
    }

    @Test
    fun `添加标签时会话失效提示一次并要求登录`() {
        val effect = FavoriteTagOps(showAdd = true, input = "科幻", request = FavoriteRequestState(busy = true))
            .reduceAdd(tag = "科幻", outcome = SessionOutcome.Expired, failureText = ::favoriteFixedFailure)
        assertEquals(SessionOutcome.Expired.message, effect.notice)
        assertTrue(effect.loginRequired)
        assertFalse(effect.reloadTags)
        assertFalse(effect.ops.busy)
    }

    @Test
    fun `删除标签请求取打开弹窗时记下的标签`() {
        val submit = FavoriteTagOps().confirmingDelete("科幻").deleteRequest()
        assertEquals("科幻", submit.tag)
        assertTrue(submit.ops.busy)
    }

    @Test
    fun `删除标签成功清目标提示并刷新标签`() {
        val effect = FavoriteTagOps(deleteTarget = "科幻", request = FavoriteRequestState(busy = true))
            .reduceDelete(tag = "科幻", outcome = SessionOutcome.Ok(Unit), failureText = ::favoriteFixedFailure)
        assertNull(effect.ops.deleteTarget)
        assertFalse(effect.ops.deleteVisible)
        assertEquals("已删除标签 #科幻", effect.notice)
        assertTrue(effect.reloadTags)
        assertFalse(effect.ops.busy)
    }

    @Test
    fun `删除标签失败保留目标并写固定文案`() {
        val effect = FavoriteTagOps(deleteTarget = "科幻", request = FavoriteRequestState(busy = true)).reduceDelete(
            tag = "科幻",
            outcome = SessionOutcome.Failed(message = "boom", cause = null),
            failureText = ::favoriteFixedFailure,
        )
        assertEquals("科幻", effect.ops.deleteTarget)
        assertEquals(FAVORITE_DELETE_TAG_FALLBACK + FAVORITE_FAILURE_SUFFIX, effect.ops.errorMessage)
        assertFalse(effect.reloadTags)
    }

    @Test
    fun `删除标签时进行中不关弹窗`() {
        val busy = FavoriteTagOps(deleteTarget = "科幻", request = FavoriteRequestState(busy = true))
        assertEquals("科幻", busy.dismissingDelete().deleteTarget)
        assertEquals("科幻", busy.deleteRequest().ops.deleteTarget)
        assertNull(busy.deleteRequest().tag)
    }

    // ── 名册派生与切片口径 ──

    @Test
    fun `名册派生下拉项为首项全部并按 id 升序过滤脏键`() {
        val options = favoriteFolderOptions(mapOf("3" to "科幻", "1" to "记录", "bad" to "坏键"))
        assertEquals(
            listOf(
                FavoriteFolderOption(FAVORITE_FOLDER_ALL_ID, FAVORITE_FOLDER_ALL_NAME),
                FavoriteFolderOption(1, "记录"),
                FavoriteFolderOption(3, "科幻"),
            ),
            options,
        )
        assertEquals("科幻", favoriteCurrentFolderName(options, 3))
        assertEquals(FAVORITE_FOLDER_ALL_NAME, favoriteCurrentFolderName(options, 9))
        assertEquals(listOf(1, 3), favoriteMovableFolders(options).map { it.id })
    }

    @Test
    fun `接口没给总页数时两种切片口径各自保持原样`() {
        val page = FavoritePage(currentPage = 1, totalPages = 0, items = emptyList())
        assertNull(favoritePageSlice(page).pageCount)
        assertEquals(0, favoritePageSliceKeepingPageCount(page).pageCount)
        assertEquals(1, favoritePageSlice(page).page)
        assertFalse(favoritePageSlice(page).hasMore)
    }

    @Test
    fun `有总页数时两种切片口径给出同样的翻页信息`() {
        val page = FavoritePage(currentPage = 2, totalPages = 5, items = emptyList())
        val miuix = favoritePageSlice(page)
        val coui = favoritePageSliceKeepingPageCount(page)
        assertEquals(5, miuix.pageCount)
        assertEquals(5, coui.pageCount)
        assertEquals(2, miuix.page)
        assertTrue(miuix.hasMore)
        assertTrue(coui.hasMore)
    }

    @Test
    fun `顶栏文案随分段切换`() {
        assertEquals(FAVORITE_ADD_ACTION_LABEL, favoriteAddActionLabel(FAVORITE_TAB_FOLDERS))
        assertEquals(FAVORITE_TAG_ACTION_LABEL, favoriteAddActionLabel(FAVORITE_TAB_TAGS))
        assertEquals(listOf("收藏夹", "标签收藏"), FAVORITE_TAB_TITLES)
    }

    @Test
    fun `弹窗正文文案保持原样`() {
        assertEquals("将删除收藏夹「科幻」，夹内作品不会取消收藏。", favoriteDeleteFolderMessage("科幻"))
        assertEquals("将取消收藏标签「#科幻」。", favoriteDeleteTagMessage("科幻"))
        assertEquals("已移动到「科幻」", favoriteMovedMessage("科幻"))
        assertEquals("已添加标签 #科幻", favoriteTagAddedMessage("科幻"))
        assertEquals("已删除标签 #科幻", favoriteTagDeletedMessage("科幻"))
    }

    @Test
    fun `一页返回空名册时保留已有名册`() {
        val current = mapOf("3" to "科幻", "1" to "推理")
        assertEquals(current, favoriteFoldersAfter(current, emptyMap()))
        assertEquals(listOf("全部", "推理", "科幻"), favoriteFolderOptions(favoriteFoldersAfter(current, emptyMap())).map { it.name })
    }

    @Test
    fun `一页返回非空名册时整份替换并保持下拉顺序`() {
        val current = mapOf("3" to "科幻", "1" to "推理")
        val next = mapOf("7" to "音乐", "2" to "历史")
        assertEquals(next, favoriteFoldersAfter(current, next))
        assertEquals(listOf("全部", "历史", "音乐"), favoriteFolderOptions(favoriteFoldersAfter(current, next)).map { it.name })
    }
}
