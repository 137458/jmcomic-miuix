package com.jm3.app.miuix.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.jm3.app.miuix.ui.coui.theme.CouiStrings
import com.jm3.core.model.Chapter
import com.jm3.core.model.CommentItem
import com.jm3.core.model.CommentPage
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 阅读器的四处浮层：跳页、章节、评论抽屉、回复面板与追更引导。 */

/** 跳页抽屉：页码区间由总页数决定，确认时由调用方在点击瞬间取当前选择值。 */
@Composable
internal fun ReaderPageJumpSheet(
    show: Boolean,
    pickerValue: Int,
    totalPages: Int,
    sheetCorner: Dp,
    onPickerValueChange: (Int) -> Unit,
    onDismiss: () -> Unit,
    onJump: () -> Unit,
) {
    OverlayBottomSheet(
        show = show,
        title = "跳页",
        cornerRadius = sheetCorner,
        onDismissRequest = onDismiss,
    ) {
        NumberPicker(
            value = pickerValue,
            onValueChange = onPickerValueChange,
            range = 1..totalPages,
            label = { it.toString() },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                onDismiss()
                onJump()
            },
            colors = ButtonDefaults.buttonColorsPrimary(),
            // 水平内边距由抽屉自带，这里只留垂直间距
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        ) {
            Text(text = "跳转到第 $pickerValue 页")
        }
    }
}

/** 章节目录抽屉：当前章节不可点，其余条目切章并关闭抽屉。 */
@Composable
internal fun ReaderChapterSheet(
    show: Boolean,
    chapters: List<Chapter>,
    currentPhotoId: String,
    sheetCorner: Dp,
    onDismiss: () -> Unit,
    onOpenChapter: (String) -> Unit,
) {
    OverlayBottomSheet(
        show = show,
        title = "章节",
        cornerRadius = sheetCorner,
        onDismissRequest = onDismiss,
    ) {
        if (chapters.isEmpty()) {
            Text(
                text = CouiStrings.EmptyChapters,
                style = MiuixTheme.textStyles.body2,
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = overlayMaxHeight(420.dp))) {
                items(chapters, key = { it.id }) { chapter ->
                    BasicComponent(
                        title = chapter.title,
                        summary = if (chapter.id == currentPhotoId) "当前章节" else null,
                        onClick = {
                            onDismiss()
                            if (chapter.id != currentPhotoId) onOpenChapter(chapter.id)
                        },
                    )
                }
                item(key = "chapter-gap") { Spacer(modifier = Modifier.height(12.dp)) }
            }
        }
    }
}

/**
 * 评论抽屉：加载中/过期/失败/空态四态与列表共用一个抽屉，底部常驻输入框。
 * 回复入口在未登录时先走登录，登录后由调用方决定回复目标。
 */
@Composable
internal fun ReaderCommentsSheet(
    show: Boolean,
    comments: CommentPage?,
    commentsLoading: Boolean,
    commentsFailed: Boolean,
    commentsExpired: Boolean,
    loggedIn: Boolean,
    commentText: String,
    commentBusy: Boolean,
    sheetCorner: Dp,
    onDismiss: () -> Unit,
    onOpenLogin: () -> Unit,
    onRetry: () -> Unit,
    onReply: (CommentItem) -> Unit,
    onCommentTextChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    OverlayBottomSheet(
        show = show,
        title = "评论",
        cornerRadius = sheetCorner,
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            val page = comments
            when {
                commentsLoading -> Box(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    contentAlignment = Alignment.Center,
                ) { InfiniteProgressIndicator() }

                commentsExpired -> Box(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val openLogin = {
                        onDismiss()
                        onOpenLogin()
                    }
                    MinTouchTarget(onClick = openLogin) {
                        TextButton(text = "登录已过期，点击去登录", onClick = openLogin, minWidth = 0.dp)
                    }
                }

                commentsFailed -> Box(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    MinTouchTarget(onClick = onRetry) {
                        TextButton(text = "加载失败，点击重试", onClick = onRetry, minWidth = 0.dp)
                    }
                }

                page == null || page.items.isEmpty() -> Text(
                    text = CouiStrings.EmptyComments,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                )

                else -> LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = overlayMaxHeight(320.dp))) {
                    items(page.items, key = { it.id }) { item ->
                        CommentRow(
                            item = item,
                            onReply = {
                                if (!loggedIn) onOpenLogin() else onReply(item)
                            },
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            CommentComposer(
                loggedIn = loggedIn,
                text = commentText,
                busy = commentBusy,
                onTextChange = { if (it.length <= COMMENT_REPLY_MAX_LENGTH) onCommentTextChange(it) },
                onLogin = onOpenLogin,
                onSend = onSend,
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

/** 回复面板：与评论抽屉平级，避免底部抽屉叠加导致高度抖动。 */
@Composable
internal fun ReaderCommentReplyPanel(
    replyTarget: CommentItem?,
    replyBusy: Boolean,
    onDismiss: () -> Unit,
    onSend: (String) -> Unit,
) {
    // 回复面板：与评论抽屉平级，避免底部抽屉叠加导致高度抖动
    CommentReplySheet(
        show = replyTarget != null,
        targetAuthor = replyTarget?.author,
        busy = replyBusy,
        onDismiss = onDismiss,
        onSend = onSend,
    )
}

/** 追更引导：最后一话最后一页弹出，可勾选静默三天。 */
@Composable
internal fun ReaderTrackPromptDialog(
    show: Boolean,
    trackMuteChecked: Boolean,
    trackBusy: Boolean,
    dialogCorner: Dp?,
    onMuteCheckedChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onFollow: () -> Unit,
) {
    OverlayDialog(
        show = show,
        maxWidth = overlayDialogMaxWidth(),
        title = "已经最后一话了！",
        cornerRadius = dialogCorner,
        onDismissRequest = onDismiss,
    ) {
        // 水平内边距由弹窗自带，这里只补垂直间距，避免左右变成 48dp
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text(
                text = "随时追踪最新连载漫画更新，不错过每一集精彩内容",
                style = MiuixTheme.textStyles.body2,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onMuteCheckedChange(!trackMuteChecked) }
                    .padding(vertical = 4.dp),
            ) {
                Checkbox(
                    state = ToggleableState(trackMuteChecked),
                    onClick = { onMuteCheckedChange(!trackMuteChecked) },
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "3天不要显示", style = MiuixTheme.textStyles.body2)
            }
            Spacer(modifier = Modifier.height(10.dp))
            // HyperOS 弹窗惯例：按钮纵向堆叠、各占满宽，避免横排等分与系统弹窗观感不一致
            TextButton(
                text = "追踪连载",
                onClick = onFollow,
                enabled = !trackBusy,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                text = "关闭",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * 阅读页的浮层集合：跳页、目录、评论、回复与末页追更引导。
 * 显隐与回调全部由调用方持有，这里只负责把它们接到各自的浮层上，顺序与原先的层叠次序一致。
 */
@Composable
internal fun ReaderOverlays(
    showPageSheet: Boolean,
    pickerValue: Int,
    totalPages: Int,
    showChapterSheet: Boolean,
    chapters: List<Chapter>,
    currentPhotoId: String,
    showCommentSheet: Boolean,
    comments: CommentPage?,
    commentsLoading: Boolean,
    commentsFailed: Boolean,
    commentsExpired: Boolean,
    loggedIn: Boolean,
    commentText: String,
    commentBusy: Boolean,
    replyTarget: CommentItem?,
    replyBusy: Boolean,
    showTrackPrompt: Boolean,
    trackMuteChecked: Boolean,
    trackBusy: Boolean,
    sheetCorner: Dp,
    dialogCorner: Dp?,
    onPickerValueChange: (Int) -> Unit,
    onDismissPageSheet: () -> Unit,
    onJumpToPage: () -> Unit,
    onDismissChapterSheet: () -> Unit,
    onOpenChapter: (String) -> Unit,
    onDismissComments: () -> Unit,
    onOpenLogin: () -> Unit,
    onRetryComments: () -> Unit,
    onReply: (CommentItem) -> Unit,
    onCommentTextChange: (String) -> Unit,
    onSendComment: () -> Unit,
    onDismissReply: () -> Unit,
    onSendReply: (String) -> Unit,
    onTrackMuteCheckedChange: (Boolean) -> Unit,
    onDismissTrackPrompt: () -> Unit,
    onFollowSeries: () -> Unit,
) {
    ReaderPageJumpSheet(
        show = showPageSheet,
        pickerValue = pickerValue,
        totalPages = totalPages,
        sheetCorner = sheetCorner,
        onPickerValueChange = onPickerValueChange,
        onDismiss = onDismissPageSheet,
        onJump = onJumpToPage,
    )

    ReaderChapterSheet(
        show = showChapterSheet,
        chapters = chapters,
        currentPhotoId = currentPhotoId,
        sheetCorner = sheetCorner,
        onDismiss = onDismissChapterSheet,
        onOpenChapter = onOpenChapter,
    )

    ReaderCommentsSheet(
        show = showCommentSheet,
        comments = comments,
        commentsLoading = commentsLoading,
        commentsFailed = commentsFailed,
        commentsExpired = commentsExpired,
        loggedIn = loggedIn,
        commentText = commentText,
        commentBusy = commentBusy,
        sheetCorner = sheetCorner,
        onDismiss = onDismissComments,
        onOpenLogin = onOpenLogin,
        onRetry = onRetryComments,
        onReply = onReply,
        onCommentTextChange = onCommentTextChange,
        onSend = onSendComment,
    )

    ReaderCommentReplyPanel(
        replyTarget = replyTarget,
        replyBusy = replyBusy,
        onDismiss = onDismissReply,
        onSend = onSendReply,
    )

    ReaderTrackPromptDialog(
        show = showTrackPrompt,
        trackMuteChecked = trackMuteChecked,
        trackBusy = trackBusy,
        dialogCorner = dialogCorner,
        onMuteCheckedChange = onTrackMuteCheckedChange,
        onDismiss = onDismissTrackPrompt,
        onFollow = onFollowSeries,
    )
}

