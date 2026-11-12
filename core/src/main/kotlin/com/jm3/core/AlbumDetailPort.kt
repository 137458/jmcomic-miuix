package com.jm3.core

import com.jm3.core.model.AlbumDetail
import com.jm3.core.model.CommentPage
import com.jm3.core.model.FavoriteFolderAction
import com.jm3.core.model.FavoritePage
import com.jm3.core.model.FolderOperation

/**
 * 作品详情页需要的网关能力。
 *
 * 详情页只用到 [Jm3Gateway] 63 个方法里的 10 个，页面状态持有者依赖这一小块，
 * 测试可以直接手写实现，不必反射伪造整个网关。全部为阻塞调用。
 */
interface AlbumDetailPort {
    /** 作品详情。 */
    fun album(albumId: String): AlbumDetail

    /** 某一页评论。 */
    fun comments(albumId: String, page: Int = 1): CommentPage

    /** 发表评论。 */
    fun postComment(albumId: String, text: String)

    /** 回复评论。 */
    fun replyComment(albumId: String, text: String, parentCommentId: String)

    /** 切换点赞。 */
    fun toggleLike(albumId: String)

    /** 是否已追更。 */
    fun isTracking(albumId: String): Boolean

    /** 切换追更。 */
    fun setTracking(albumId: String)

    /** 收藏夹列表。 */
    fun favorites(page: Int): FavoritePage

    /** 收藏夹管理（新建/重命名/移动/删除）。 */
    fun manageFavoriteFolder(
        action: FavoriteFolderAction,
        folderId: String = "",
        name: String = "",
        albumId: String = "",
    ): FolderOperation

    /** 切换收藏状态（已收藏则取消）；folderId 指定加入的收藏夹。 */
    fun toggleFavorite(albumId: String, folderId: String = "0")
}

/** 把整宽网关收成详情页那 10 个方法；纯转发，不做任何判断。 */
fun Jm3Gateway.asAlbumDetailPort(): AlbumDetailPort = GatewayAlbumDetailPort(this)

private class GatewayAlbumDetailPort(private val gateway: Jm3Gateway) : AlbumDetailPort {
    override fun album(albumId: String): AlbumDetail = gateway.album(albumId)

    override fun comments(albumId: String, page: Int): CommentPage = gateway.comments(albumId, page)

    override fun postComment(albumId: String, text: String) = gateway.postComment(albumId, text)

    override fun replyComment(albumId: String, text: String, parentCommentId: String) =
        gateway.replyComment(albumId, text, parentCommentId)

    override fun toggleLike(albumId: String) = gateway.toggleLike(albumId)

    override fun isTracking(albumId: String): Boolean = gateway.isTracking(albumId)

    override fun setTracking(albumId: String) = gateway.setTracking(albumId)

    override fun favorites(page: Int): FavoritePage = gateway.favorites(page)

    override fun manageFavoriteFolder(
        action: FavoriteFolderAction,
        folderId: String,
        name: String,
        albumId: String,
    ): FolderOperation = gateway.manageFavoriteFolder(action, folderId, name, albumId)

    override fun toggleFavorite(albumId: String, folderId: String) = gateway.toggleFavorite(albumId, folderId)
}
