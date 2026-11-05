package com.jm3.core

import com.jm3.core.model.FavoriteFolderAction
import com.jm3.core.model.FavoritePage
import com.jm3.core.model.FolderOperation
import com.jm3.core.model.TagFavorite
import io.github.jukomu.jmcomic.api.enums.FavoriteFolderType
import io.github.jukomu.jmcomic.api.model.FavoriteQuery
import io.github.jukomu.jmcomic.api.model.JmFavoriteFolderResult
import io.github.jukomu.jmcomic.api.model.JmFavoritePage
import io.github.jukomu.jmcomic.api.model.JmTagFavorite

/** 收藏夹列表查询：folderId=0 表示全部收藏。 */
fun favoritesQuery(folderId: Int, page: Int): FavoriteQuery =
    FavoriteQuery.Builder().folderId(folderId).page(page).build()

/** 收藏列表页：folders 为收藏夹 id→名称；封面与列表页走同一个入口。 */
fun JmFavoritePage.toFavoritePage(covers: CoverResolver = CoverResolver.Unready): FavoritePage = FavoritePage(
    currentPage = currentPage,
    totalPages = totalPages,
    folders = folderList.orEmpty(),
    folderId = folderId,
    items = content.orEmpty().map { it.toSearchResult(covers) },
)

/** 收藏夹操作结果：服务端 status 非 ok 时携带 msg 作为失败原因。 */
fun JmFavoriteFolderResult.toFolderOperation(): FolderOperation =
    FolderOperation(ok = isOk(), message = msg()?.takeIf { it.isNotBlank() })

/** 收藏夹动作映射：core 枚举值即库 FavoriteFolderType 的 API 值。 */
fun FavoriteFolderAction.toFavoriteFolderType(): FavoriteFolderType =
    FavoriteFolderType.entries.firstOrNull { it.value == apiValue } ?: FavoriteFolderType.ADD

fun JmTagFavorite.toTagFavorite(): TagFavorite = TagFavorite(
    tag = tag().orEmpty(),
    updatedAt = updatedAt()?.takeIf { it.isNotBlank() },
)
