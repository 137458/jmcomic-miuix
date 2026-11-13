package com.jm3.app.miuix.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import com.jm3.core.model.UserProfile

/**
 * 个人资料表单的草稿状态：服务端资料、脏字段、未保存草稿与编辑缓冲只有这一份。
 *
 * 两套皮肤的 ProfileScreen 都持有同一个容器实例，谁都不能各自维护一套记脏/合并规则。
 * [apply] 复用 [ProfileDraftPolicy.mergeProfileFields]：非脏字段采纳服务端新值，
 * 脏字段保留用户正在编辑的输入。
 */
internal class ProfileDraftState(
    initialDirtyKeys: Set<String> = emptySet(),
    initialSavedDrafts: Map<String, String> = emptyMap(),
) {
    var profile: UserProfile? by mutableStateOf<UserProfile?>(null)
        private set

    /** 用户改过但尚未提交成功的字段名。 */
    var dirtyKeys: Set<String> by mutableStateOf(initialDirtyKeys)
        private set

    /** 脏字段最后一次的输入值；跨旋转保留，提交成功后清空。 */
    var savedDrafts: Map<String, String> by mutableStateOf(initialSavedDrafts)
        private set

    private val draftFields = mutableStateMapOf<String, String>()

    /** 编辑缓冲：键与 UserProfile.editableFields() 一致，提交时整体回传。 */
    val backingFields: MutableMap<String, String> get() = draftFields

    /** 用户改动一个字段：先记脏，再记账草稿值（与原页内 lambda 的顺序一致）。 */
    fun noteEdit(key: String, value: String) {
        dirtyKeys = dirtyKeys + key
        savedDrafts = savedDrafts + (key to value)
    }

    /**
     * 用服务端返回的资料刷新表单。[resetDrafts] 为 true 表示这份资料就是刚提交成功的草稿，
     * 未保存记录全部作废；否则保留脏字段的用户输入。
     */
    fun apply(next: UserProfile, resetDrafts: Boolean = false) {
        profile = next
        if (resetDrafts) {
            dirtyKeys = emptySet()
            savedDrafts = emptyMap()
        }
        val merged = ProfileDraftPolicy.mergeProfileFields(
            serverFields = next.editableFields(),
            savedDrafts = savedDrafts,
            dirtyKeys = dirtyKeys,
        )
        draftFields.clear()
        draftFields.putAll(merged)
    }

    /** 账号切换：资料、脏字段、草稿与编辑缓冲一起丢弃。 */
    fun reset() {
        profile = null
        dirtyKeys = emptySet()
        savedDrafts = emptyMap()
        draftFields.clear()
    }
}

/**
 * 只备份脏字段与草稿两套记录：服务端资料与编辑缓冲本来就不跨进程保存，
 * 旋转后由 load() 重新拉取并交给 [ProfileDraftState.apply] 合并回草稿，
 * 与改造前分别用 StringSetSaver/StringMapSaver 备份两套记录的行为等价。
 */
internal val ProfileDraftStateSaver: Saver<ProfileDraftState, Any> = listSaver<ProfileDraftState, String>(
    save = { state ->
        buildList {
            add(state.dirtyKeys.size.toString())
            addAll(state.dirtyKeys)
            for ((key, value) in state.savedDrafts) {
                add(key)
                add(value)
            }
        }
    },
    restore = { raw ->
        val dirtyCount = raw.firstOrNull()?.toIntOrNull() ?: 0
        val dirty = raw.drop(1).take(dirtyCount).toSet()
        val flatDrafts = raw.drop(1 + dirtyCount)
        val drafts = buildMap<String, String> {
            var index = 0
            while (index + 1 < flatDrafts.size) {
                put(flatDrafts[index], flatDrafts[index + 1])
                index += 2
            }
        }
        ProfileDraftState(initialDirtyKeys = dirty, initialSavedDrafts = drafts)
    },
)
