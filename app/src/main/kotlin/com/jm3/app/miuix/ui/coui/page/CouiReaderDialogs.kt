package com.jm3.app.miuix.ui.coui.page

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.itos.coui.checkbox.Checkbox
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.Dialog
import com.itos.coui.effect.SpotlightPreset
import com.itos.coui.layout.Divider
import com.itos.coui.layout.Item
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.slider.Slider
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme as CouiKitTheme
import com.jm3.app.miuix.ui.components.COMMENT_REPLY_MAX_LENGTH
import com.jm3.app.miuix.ui.coui.component.CouiBodyText
import com.jm3.app.miuix.ui.coui.component.CouiCommentFieldShape
import com.jm3.app.miuix.ui.coui.component.CouiCommentRow
import com.jm3.app.miuix.ui.coui.component.CouiMinTouchTarget
import com.jm3.app.miuix.ui.coui.component.couiDialogListMaxHeight
import com.jm3.app.miuix.ui.coui.component.couiDialogMaterial
import com.jm3.app.miuix.ui.coui.component.couiPressable
import com.jm3.app.miuix.ui.coui.component.rememberCouiBodyTextStyle
import com.jm3.app.miuix.ui.coui.icon.CouiIcons
import com.jm3.app.miuix.ui.coui.theme.CouiStrings
import com.jm3.core.model.Chapter
import com.jm3.core.model.CommentItem
import com.jm3.core.model.CommentPage
import kotlin.math.roundToInt

/** 页码滑杆画刻度的页数上限：再长点位会密到成带，改回纯滑杆。 */
private const val PageSliderTickMaxPages = 24

/**
 * 跳页：滑杆搭配加减微调按钮，百页以上长章节也能精准选定目标页；
 * 页码越界交给 coerceIn / couiStepJumpPage 夹紧，避免把非法页码送进 goToPage。
 */
@Composable
internal fun ReaderPageJumpDialog(
    visible: Boolean,
    pickerValue: Int,
    totalPages: Int,
    onValueChange: (Int) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        visible = visible,
        material = couiDialogMaterial(),
        title = "跳页",
        confirmAction = CouiDialogAction(
            text = "跳转到第 $pickerValue 页",
            onClick = {
                onDismiss()
                onConfirm()
            },
        ),
        dismissAction = CouiDialogAction(text = "取消", onClick = onDismiss),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "第 $pickerValue 页 / 共 $totalPages 页",
                    kind = CouiTextKind.ItemTitle,
                    modifier = Modifier.weight(1f),
                )
                if (totalPages > 1) {
                    ReaderPageJumpStepper(
                        icon = CouiIcons.Minus,
                        contentDescription = "上一页",
                        enabled = pickerValue > 1,
                        onClick = { onValueChange(couiStepJumpPage(pickerValue, -1, totalPages)) },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    ReaderPageJumpStepper(
                        icon = CouiIcons.Plus,
                        contentDescription = "下一页",
                        enabled = pickerValue < totalPages,
                        onClick = { onValueChange(couiStepJumpPage(pickerValue, 1, totalPages)) },
                    )
                }
            }
            if (totalPages > 1) {
                Slider(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    value = pickerValue.toFloat(),
                    onValueChange = { onValueChange(it.roundToInt().coerceIn(1, totalPages)) },
                    valueRange = 1f..totalPages.toFloat(),
                    // 滑杆只停整数页：两端各留一档，中间档数就是总页数减二
                    steps = (totalPages - 2).coerceAtLeast(0),
                    // 短章节把可落位点画出来，页码不用靠猜；长章节点位会密到成带，改回纯滑杆
                    showTickMarks = totalPages <= PageSliderTickMaxPages,
                )
            }
        }
    }
}

/** 章节：列表高度封顶后自身滚动，当前话用后缀标注而不是副标题，省一行行高。 */
@Composable
internal fun ReaderChaptersDialog(
    visible: Boolean,
    chapters: List<Chapter>,
    currentPhotoId: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        visible = visible,
        material = couiDialogMaterial(),
        title = "章节",
        confirmAction = CouiDialogAction(text = "关闭", onClick = onDismiss),
    ) {
        if (chapters.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp),
            ) {
                CouiBodyText(text = CouiStrings.EmptyChapters)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = couiDialogListMaxHeight(420.dp))) {
                items(chapters, key = { it.id }) { chapter ->
                    Item(
                        title = {
                            Text(
                                text = if (chapter.id == currentPhotoId) {
                                    chapter.title + "（当前话）"
                                } else {
                                    chapter.title
                                },
                                kind = CouiTextKind.ItemTitle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        onClick = {
                            onDismiss()
                            if (chapter.id != currentPhotoId) onSelect(chapter.id)
                        },
                    )
                    // 组卡片内部没有自动分隔线，行间需要自己补；末行不补，避免贴着卡片下沿
                    if (chapter.id != chapters.last().id) Divider()
                }
                item(key = "chapter-gap") { Spacer(modifier = Modifier.height(12.dp)) }
            }
        }
    }
}

/** 评论：直接承接 Dialog 玻璃材质，不再嵌套实底 Group 割裂质感。 */
@Composable
internal fun ReaderCommentsDialog(
    visible: Boolean,
    comments: CommentPage?,
    loading: Boolean,
    failed: Boolean,
    expired: Boolean,
    loggedIn: Boolean,
    text: String,
    busy: Boolean,
    notice: String?,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onLogin: () -> Unit,
    onReload: () -> Unit,
    onReply: (CommentItem) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        visible = visible,
        material = couiDialogMaterial(),
        title = "评论",
        confirmAction = CouiDialogAction(text = "关闭", onClick = onDismiss),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            val page = comments
            when {
                loading -> ReaderDialogHint(text = "正在加载评论…")

                expired -> ReaderDialogHint(
                    text = "登录已过期，点此登录",
                    onClick = onLogin,
                )

                failed -> ReaderDialogHint(
                    text = "评论加载失败，点此重试",
                    onClick = onReload,
                )

                page == null || page.items.isEmpty() -> ReaderDialogHint(text = CouiStrings.EmptyComments)

                else -> LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = couiDialogListMaxHeight(320.dp)),
                ) {
                    items(page.items, key = { it.id }) { item ->
                        ReaderCommentRow(
                            item = item,
                            onReply = { onReply(item) },
                        )
                        if (item.id != page.items.last().id) Divider()
                    }
                }
            }
            Divider()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                ReaderCommentComposer(
                    loggedIn = loggedIn,
                    text = text,
                    busy = busy,
                    notice = notice,
                    onTextChange = onTextChange,
                    onLogin = onLogin,
                    onSend = onSend,
                )
            }
        }
    }
}

/** 回复：与评论弹窗互斥；回退到评论弹窗的时序由调用方按 reopenCommentsAfterReply 决定。 */
@Composable
internal fun ReaderReplyDialog(
    visible: Boolean,
    author: String?,
    text: String,
    busy: Boolean,
    notice: String?,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = { if (!busy) onDismiss() },
        visible = visible,
        material = couiDialogMaterial(),
        title = "回复 " + author.orEmpty(),
        confirmAction = CouiDialogAction(
            text = if (busy) "发送中…" else "发送",
            enabled = !busy,
            onClick = onSend,
        ),
        dismissAction = CouiDialogAction(
            text = "取消",
            onClick = { if (!busy) onDismiss() },
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            ReaderCommentComposer(
                loggedIn = true,
                text = text,
                busy = busy,
                notice = notice,
                onTextChange = onTextChange,
                onLogin = {},
                onSend = onSend,
            )
        }
    }
}

/** 追更引导：直接承接 Dialog 玻璃材质，动作按钮交给弹窗自身槽位（竖向堆叠由库负责）。 */
@Composable
internal fun ReaderTrackPromptDialog(
    visible: Boolean,
    muted: Boolean,
    busy: Boolean,
    onMutedChange: (Boolean) -> Unit,
    onFollow: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        visible = visible,
        material = couiDialogMaterial(),
        title = "已经最后一话了！",
        confirmAction = CouiDialogAction(
            text = if (busy) "追踪中…" else "追踪连载",
            enabled = !busy,
            onClick = onFollow,
        ),
        dismissAction = CouiDialogAction(text = "关闭", onClick = onDismiss),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            CouiBodyText(
                text = "随时追踪最新连载漫画更新，不错过每一集精彩内容",
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .couiPressable(minTouchTarget = CouiMinTouchTarget) {
                        onMutedChange(!muted)
                    }
                    .padding(vertical = 8.dp),
            ) {
                // 库复选框只画框，点击语义与热区由这一行自己提供
                Checkbox(checked = muted)
                Spacer(modifier = Modifier.width(10.dp))
                Text(text = "3天不要显示", kind = CouiTextKind.ItemTitle)
            }
        }
    }
}

/** 弹窗内部提示条：避免在打开 CouiDialogWindow 时提示被主窗口下方的 Snackbar 挡住。 */
@Composable
internal fun ReaderDialogNotice(message: String?) {
    if (message.isNullOrBlank()) return
    Text(
        text = message,
        kind = CouiTextKind.GroupFooter,
        color = CouiKitTheme.colorScheme.accentOnSurface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
    )
}

/** 评论行：头像 + 作者 + 时间 + 正文 + 回复入口，回复入口只在正文下方占一行。 */
@Composable
internal fun ReaderCommentRow(item: CommentItem, onReply: () -> Unit) {
    val scheme = CouiKitTheme.colorScheme
    CouiCommentRow(
        item = item,
        authorColor = scheme.accentOnSurface,
        onReply = { onReply() },
    )
}

/** 评论输入区：未登录只给登录入口，登录后展示内联提示、输入框加字数与发表。 */
@Composable
internal fun ReaderCommentComposer(
    loggedIn: Boolean,
    text: String,
    busy: Boolean,
    notice: String? = null,
    onTextChange: (String) -> Unit,
    onLogin: () -> Unit,
    onSend: () -> Unit,
) {
    val scheme = CouiKitTheme.colorScheme
    ReaderDialogNotice(notice)
    if (!loggedIn) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .couiPressable(spotlight = null, minTouchTarget = CouiMinTouchTarget) { onLogin() }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "登录后参与评论", kind = CouiTextKind.ItemTitle, color = scheme.accent)
        }
        return
    }
    // 空内容时输入框自身没有占位能力，这里叠一层说明文字当占位
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CouiCommentFieldShape)
            .background(scheme.container)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        if (text.isEmpty()) {
            CouiBodyText(text = "说点什么…", color = scheme.onSurfaceVariant)
        }
        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = rememberCouiBodyTextStyle().copy(color = scheme.onSurface),
            cursorBrush = SolidColor(scheme.accent),
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text.length.toString() + "/" + COMMENT_REPLY_MAX_LENGTH,
            kind = CouiTextKind.GroupFooter,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        val enabled = !busy && text.isNotBlank()
        // 底色经 backgroundColor 交给 couiPressable，确保 48dp 触区不把可见胶囊撑大
        Box(
            modifier = Modifier
                .couiPressable(
                    shape = CouiCapsuleShape,
                    backgroundColor = if (enabled) scheme.accent else scheme.outlineVariant,
                    spotlight = if (enabled) SpotlightPreset.OpaqueMedium1 else null,
                    enabled = enabled,
                    minTouchTarget = CouiMinTouchTarget,
                    onClick = onSend,
                )
                .padding(horizontal = 16.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (busy) "发送中…" else "发表",
                kind = CouiTextKind.ItemValue,
                color = if (enabled) scheme.onAccent else scheme.onSurfaceVariant,
            )
        }
    }
}

/** 评论弹窗的加载/失败/空态占位：失败时整行可点重试。 */
@Composable
internal fun ReaderDialogHint(text: String, onClick: (() -> Unit)? = null) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        CouiBodyText(
            text = text,
            color = CouiKitTheme.colorScheme.onSurfaceVariant,
            modifier = if (onClick != null) {
                Modifier.couiPressable(minTouchTarget = CouiMinTouchTarget) { onClick() }.padding(vertical = 8.dp)
            } else {
                Modifier
            },
        )
    }
}