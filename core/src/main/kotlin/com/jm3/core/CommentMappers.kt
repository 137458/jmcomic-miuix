package com.jm3.core

import com.jm3.core.model.CommentItem
import com.jm3.core.model.CommentPage
import com.jm3.core.model.CommentSegment
import io.github.jukomu.jmcomic.api.model.JmComment
import io.github.jukomu.jmcomic.api.model.JmCommentList
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** 推荐作品标记：正文里以「[ 我推荐这本书 JM123456 ]」形式出现，繁体写作「推薦」。 */
private val COMMENT_RECOMMEND =
    Regex("""\[\s*(?:我推荐这本书|我推薦這本書)\s+(?:JM)?(\d+)\s*\]""")

/**
 * 评论正文分段：接口给的是 HTML，先转段落文本，再把推荐作品标记单独拆成可点链接。
 *
 * contentHtml 为空时回落到纯文本 content；纯文本不再过 HTML 清洗，避免吃掉正文里的尖括号。
 */
fun commentSegments(content: String?, contentHtml: String? = null): List<CommentSegment> {
    val html = contentHtml?.takeIf { it.isNotBlank() }
    val source = html ?: content?.takeIf { it.isNotBlank() } ?: return emptyList()
    val text = if (html != null) htmlToText(html) else source
    val segments = mutableListOf<CommentSegment>()
    var cursor = 0
    for (match in COMMENT_RECOMMEND.findAll(text)) {
        text.substring(cursor, match.range.first).trim()
            .takeIf { it.isNotEmpty() }
            ?.let { segments += CommentSegment.Text(it) }
        segments += CommentSegment.Album(albumId = match.groupValues[1], label = match.value)
        cursor = match.range.last + 1
    }
    text.substring(cursor).trim()
        .takeIf { it.isNotEmpty() }
        ?.let { segments += CommentSegment.Text(it) }
    return segments
}

/** 库把徽章数组元素原样转成 JSON 字符串透传，这里取出其中的 content 图片路径。 */
fun badgeImagePaths(raw: List<String>): List<String> = raw.mapNotNull { entry ->
    val trimmed = entry.trim()
    when {
        trimmed.isEmpty() -> null
        trimmed.startsWith("{") -> runCatching {
            Json.parseToJsonElement(trimmed).jsonObject["content"]?.jsonPrimitive?.contentOrNull
        }.getOrNull()?.takeIf { it.isNotBlank() }
        else -> trimmed
    }
}

// 评论字段可能缺失，逐级回退；回复列表递归映射。
fun JmComment.toCommentItem(covers: CoverResolver = CoverResolver.Unready): CommentItem = CommentItem(
    id = commentId() ?: "",
    author = nickname() ?: username() ?: name() ?: "匿名",
    content = content() ?: "",
    postDate = postDate(),
    likes = likes(),
    replies = replys().orEmpty().map { it.toCommentItem(covers) },
    albumId = aid()?.takeIf { it.isNotBlank() && it != "0" },
    // 头像由库拼成绝对地址（/media/users/{photo}），无需再拼图片域名
    avatarUrl = photo()?.takeIf { it.isNotBlank() },
    level = expinfoData()?.level(),
    levelName = expinfoData()?.levelName()?.takeIf { it.isNotBlank() },
    badges = badgeImagePaths(expinfoData()?.badges().orEmpty())
        .mapNotNull { covers.image(it) },
    albumName = name()?.takeIf { it.isNotBlank() },
    // 关联作品封面与详情页同规格：3:4 缩略图由同一入口拼接，域名未知即"未就绪"
    albumCoverUrl = covers.resolve(aid()?.takeIf { it.isNotBlank() && it != "0" }, "_3x4").urlOrNull,
    body = commentSegments(content(), contentHtml()),
)

fun JmCommentList.toCommentPage(covers: CoverResolver = CoverResolver.Unready): CommentPage = CommentPage(
    total = total(),
    items = list().orEmpty().map { it.toCommentItem(covers) },
)
