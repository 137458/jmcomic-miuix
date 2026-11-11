// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.jm3.core.model.CommentItem
import com.jm3.core.model.CommentSegment

/**
 * 回复 / 发帖弹窗草稿的重置判定。
 *
 * 旋转重建时 [wasOpen] 与 [isOpen] 同为 true 且 [previousTargetKey] 未变，
 * 绝不能清掉用户正在输入的草稿。
 */
internal object DraftResetPolicy {

    fun shouldResetDraft(
        wasOpen: Boolean,
        isOpen: Boolean,
        previousTargetKey: String?,
        currentTargetKey: String?,
    ): Boolean = when {
        !isOpen -> false
        !wasOpen -> true
        else -> previousTargetKey != currentTargetKey
    }
}

/**
 * 跨旋转保留弹窗草稿，仅在“关闭 → 打开”或“打开期间切换目标”时清空旧文本。
 */
@Composable
internal fun rememberSavedDialogDraft(
    isOpen: Boolean,
    targetKey: String?,
    resetKey: Any? = Unit,
): MutableState<String> {
    val draft = rememberSaveable(resetKey) { mutableStateOf("") }
    var wasOpen by rememberSaveable(resetKey) { mutableStateOf(false) }
    var lastTargetKey by rememberSaveable(resetKey) { mutableStateOf<String?>(null) }
    LaunchedEffect(isOpen, targetKey) {
        if (DraftResetPolicy.shouldResetDraft(wasOpen, isOpen, lastTargetKey, targetKey)) {
            draft.value = ""
        }
        wasOpen = isOpen
        if (isOpen) lastTargetKey = targetKey
    }
    return draft
}

/**
 * [CommentItem] 的无损 Bundle 字符串编解码，供 rememberSaveable 保存回复目标。
 */
internal object CommentItemCodec {

    private class Writer {
        val sb = StringBuilder()

        fun writeStr(value: String): Writer = apply {
            sb.append(value.length).append(':').append(value)
        }

        fun writeOptStr(value: String?): Writer = apply {
            if (value == null) sb.append('0') else sb.append('1').also { writeStr(value) }
        }

        fun writeInt(value: Int): Writer = apply {
            sb.append(value).append(';')
        }

        fun writeOptInt(value: Int?): Writer = apply {
            if (value == null) sb.append('0') else sb.append('1').also { writeInt(value) }
        }

        fun writeSegment(seg: CommentSegment): Writer = apply {
            when (seg) {
                is CommentSegment.Text -> sb.append('T').also { writeStr(seg.text) }
                is CommentSegment.Album -> sb.append('A').also {
                    writeStr(seg.albumId)
                    writeStr(seg.label)
                }
            }
        }

        fun writeComment(item: CommentItem): Writer = apply {
            writeStr(item.id)
            writeStr(item.author)
            writeStr(item.content)
            writeOptStr(item.postDate)
            writeInt(item.likes)
            writeOptStr(item.albumId)
            writeOptStr(item.avatarUrl)
            writeOptInt(item.level)
            writeOptStr(item.levelName)
            writeInt(item.badges.size)
            item.badges.forEach(::writeStr)
            writeOptStr(item.albumName)
            writeOptStr(item.albumCoverUrl)
            writeInt(item.body.size)
            item.body.forEach(::writeSegment)
            writeInt(item.replies.size)
            item.replies.forEach(::writeComment)
        }
    }

    private class Reader(private val raw: String) {
        var pos = 0

        fun readChar(): Char {
            require(pos < raw.length)
            return raw[pos++]
        }

        fun readInt(): Int {
            val end = raw.indexOf(';', pos)
            require(end > pos)
            val v = raw.substring(pos, end).toInt()
            pos = end + 1
            return v
        }

        fun readStr(): String {
            val colon = raw.indexOf(':', pos)
            require(colon > pos)
            val len = raw.substring(pos, colon).toInt()
            require(len >= 0 && colon + 1 + len <= raw.length)
            val out = raw.substring(colon + 1, colon + 1 + len)
            pos = colon + 1 + len
            return out
        }

        fun readOptStr(): String? = when (readChar()) {
            '0' -> null
            '1' -> readStr()
            else -> error("Invalid opt marker")
        }

        fun readOptInt(): Int? = when (readChar()) {
            '0' -> null
            '1' -> readInt()
            else -> error("Invalid opt marker")
        }

        fun readSegment(): CommentSegment = when (readChar()) {
            'T' -> CommentSegment.Text(readStr())
            'A' -> CommentSegment.Album(albumId = readStr(), label = readStr())
            else -> error("Invalid segment tag")
        }

        fun readComment(): CommentItem {
            val id = readStr()
            val author = readStr()
            val content = readStr()
            val postDate = readOptStr()
            val likes = readInt()
            val albumId = readOptStr()
            val avatarUrl = readOptStr()
            val level = readOptInt()
            val levelName = readOptStr()
            val badgeCount = readInt()
            require(badgeCount in 0..1000)
            val badges = List(badgeCount) { readStr() }
            val albumName = readOptStr()
            val albumCoverUrl = readOptStr()
            val segCount = readInt()
            require(segCount in 0..10_000)
            val body = List(segCount) { readSegment() }
            val replyCount = readInt()
            require(replyCount in 0..10_000)
            val replies = List(replyCount) { readComment() }
            return CommentItem(
                id = id,
                author = author,
                content = content,
                postDate = postDate,
                likes = likes,
                replies = replies,
                albumId = albumId,
                avatarUrl = avatarUrl,
                level = level,
                levelName = levelName,
                badges = badges,
                albumName = albumName,
                albumCoverUrl = albumCoverUrl,
                body = body,
            )
        }
    }

    fun encode(item: CommentItem): String = Writer().writeComment(item).sb.toString()

    fun decode(raw: String): CommentItem? {
        if (raw.isEmpty()) return null
        return runCatching {
            val reader = Reader(raw)
            val item = reader.readComment()
            require(reader.pos == raw.length)
            item
        }.getOrNull()
    }
}

internal val CommentItemNullableSaver: Saver<CommentItem?, String> = Saver(
    save = { item -> if (item == null) "" else "1:${CommentItemCodec.encode(item)}" },
    restore = { raw ->
        if (!raw.startsWith("1:")) null else CommentItemCodec.decode(raw.removePrefix("1:"))
    },
)

internal val StringSetSaver: Saver<Set<String>, Any> = listSaver<Set<String>, String>(
    save = { it.toList() },
    restore = { it.toSet() },
)

internal val IntStringPairNullableSaver: Saver<Pair<Int, String>?, String> = Saver(
    save = { pair -> if (pair == null) "" else "${pair.first}:${pair.second}" },
    restore = { raw ->
        if (raw.isEmpty()) {
            null
        } else {
            val sep = raw.indexOf(':')
            if (sep <= 0) {
                null
            } else {
                raw.substring(0, sep).toIntOrNull()?.let { id -> id to raw.substring(sep + 1) }
            }
        }
    },
)

/**
 * 个人资料表单的跨旋转草稿合并与账号切换判定。
 */
internal object ProfileDraftPolicy {

    const val UID_UNSET: String = "__JM3_UID_UNSET__"
    private const val UID_LOGGED_OUT: String = "__JM3_UID_LOGGED_OUT__"

    fun uidToken(uid: String?): String = if (uid == null) UID_LOGGED_OUT else "u:$uid"

    fun shouldResetOnUidEffect(savedUidToken: String, currentUid: String?): Boolean =
        savedUidToken != uidToken(currentUid)

    fun mergeProfileFields(
        serverFields: Map<String, String>,
        savedDrafts: Map<String, String>,
        dirtyKeys: Set<String>,
    ): Map<String, String> = buildMap {
        for ((key, serverValue) in serverFields) {
            put(key, if (key in dirtyKeys && key in savedDrafts) savedDrafts.getValue(key) else serverValue)
        }
        for (key in dirtyKeys) {
            if (key !in serverFields && key in savedDrafts) {
                put(key, savedDrafts.getValue(key))
            }
        }
    }
}

internal val StringMapSaver: Saver<Map<String, String>, Any> = listSaver<Map<String, String>, String>(
    save = { map -> map.entries.flatMap { listOf(it.key, it.value) } },
    restore = { flat ->
        buildMap {
            var i = 0
            while (i + 1 < flat.size) {
                put(flat[i], flat[i + 1])
                i += 2
            }
        }
    },
)

/**
 * 创建由 rememberSaveable 备份的个人资料表单 Map：任何写入都会同步记入 [dirtyKeys] 与 [savedDrafts]。
 */
internal fun trackedProfileFieldMap(
    backing: MutableMap<String, String>,
    onFieldEdited: (key: String, value: String) -> Unit,
): MutableMap<String, String> = object : MutableMap<String, String> by backing {
    override fun put(key: String, value: String): String? {
        onFieldEdited(key, value)
        return backing.put(key, value)
    }

    override fun putAll(from: Map<out String, String>) {
        from.forEach { (key, value) -> put(key, value) }
    }
}

/**
 * 相册详情页需跨配置变更保留的轻交互快照。
 */
internal data class AlbumDetailUiSnapshot(
    val commentDraft: String = "",
    val replyTarget: CommentItem? = null,
    val descriptionExpanded: Boolean = false,
    val expandedGroups: Set<String> = emptySet(),
    val showFolderSheet: Boolean = false,
)

internal val AlbumDetailUiSnapshotSaver: Saver<AlbumDetailUiSnapshot, Any> =
    listSaver<AlbumDetailUiSnapshot, String>(
        save = { s ->
            buildList {
                add(s.commentDraft)
                add(if (s.replyTarget == null) "" else CommentItemCodec.encode(s.replyTarget))
                add(if (s.descriptionExpanded) "1" else "0")
                add(if (s.showFolderSheet) "1" else "0")
                addAll(s.expandedGroups)
            }
        },
        restore = { raw ->
            if (raw.size < 4) {
                AlbumDetailUiSnapshot()
            } else {
                AlbumDetailUiSnapshot(
                    commentDraft = raw[0],
                    replyTarget = raw[1].takeIf { it.isNotEmpty() }?.let(CommentItemCodec::decode),
                    descriptionExpanded = raw[2] == "1",
                    showFolderSheet = raw[3] == "1",
                    expandedGroups = raw.drop(4).toSet(),
                )
            }
        },
    )
