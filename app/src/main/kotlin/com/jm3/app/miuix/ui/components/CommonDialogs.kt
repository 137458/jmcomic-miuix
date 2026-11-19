package com.jm3.app.miuix.ui.components

/** 通用对话框与动作行：评论回复弹层、动作格、返回键、对话框按钮行。 */

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jm3.app.miuix.ui.rememberSavedDialogDraft
import com.jm3.app.miuix.ui.skin.LocalSkin
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.layout.DialogDefaults
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 评论回复面板：显示被回复作者、200 字上限与发送状态；换目标时清空草稿。 */
@Composable
fun CommentReplySheet(
    show: Boolean,
    targetAuthor: String?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSend: (String) -> Unit,
) {
    val skin = LocalSkin.current
    var text by rememberSavedDialogDraft(isOpen = show, targetKey = targetAuthor)
    OverlayBottomSheet(
        show = show,
        title = targetAuthor?.let { "回复 $it" } ?: "回复",
        cornerRadius = skin.shapes.sheetCorner,
        onDismissRequest = onDismiss,
    ) {
        Card(cornerRadius = skin.shapes.cardCorner, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                TextField(
                    value = text,
                    onValueChange = { text = it.take(COMMENT_REPLY_MAX_LENGTH) },
                    label = "写下你的回复（最多 $COMMENT_REPLY_MAX_LENGTH 字）",
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "${text.length}/$COMMENT_REPLY_MAX_LENGTH",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Button(
                        onClick = { onSend(text.trim()) },
                        enabled = !busy && text.isNotBlank(),
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) {
                        Text(text = if (busy) "发送中…" else "发送")
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}

/** 评论字数上限与原版一致（发帖/评论/回复共用 200）。 */
const val COMMENT_REPLY_MAX_LENGTH = 200

/** 纵向图标单元：详情动作台与悬浮工具条共用；禁用降灰、激活主色、按压微缩放。 */
@Composable
fun ActionCell(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    glyph: String? = null,
    enabled: Boolean = true,
    active: Boolean = false,
) {
    val color = when {
        !enabled -> MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.4f)
        active -> MiuixTheme.colorScheme.primary
        else -> MiuixTheme.colorScheme.onSurface
    }
    Column(
        modifier = modifier
            // 点击涟漪按胶囊裁剪：底部工具栏与详情操作栏都是圆形/胶囊外形，
            // 不裁的话 ripple 会按单元格矩形铺开，看起来就是一块方块。
            .clip(RoundedCornerShape(percent = 50))
            .clickable(enabled = enabled, onClick = onClick)
            .pressScale(hapticFeedback = true)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(22.dp),
            )
        } else if (glyph != null) {
            // 这里是图标字形单元而非正文：字号与 22dp 图标对齐，随 fontScale 放大属于预期，
            // 因此不用 textStyles 收口，避免行高被样式带偏
            Text(text = glyph, fontSize = 22.sp, color = color)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote2,
            color = color,
            maxLines = 1,
        )
    }
}

/** 标题栏返回键。 */
@Composable
fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(
            imageVector = MiuixIcons.Back,
            contentDescription = "返回",
            modifier = Modifier.size(24.dp),
        )
    }
}

/**
 * Miuix 弹窗的按钮排布：取消在左、主操作在右，两颗按钮等分一行。
 *
 * 弹窗内容已经由 `DialogDefaults.insideMargin` 给出 24dp 内边距，调用方不要再补水平 padding，
 * 否则左右会变成 48dp，与系统弹窗以及 Miuix 分页/诊断面板的观感不一致。
 * [destructive] 仅改文字色，背景仍与取消键同档，避免在浅色底上出现整块红色。
 */
@Composable
fun DialogActionRow(
    primaryText: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryText: String = "取消",
    onSecondary: () -> Unit,
    primaryEnabled: Boolean = true,
    destructive: Boolean = false,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            text = secondaryText,
            onClick = onSecondary,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            text = primaryText,
            onClick = onPrimary,
            enabled = primaryEnabled,
            colors = if (destructive) {
                // 只换文字色、底色仍与取消键同档：整块红在浅色底上过于刺眼，也确实会让两颗按钮不成对
                ButtonDefaults.textButtonColors(textColor = MiuixTheme.colorScheme.error)
            } else {
                ButtonDefaults.textButtonColors()
            },
            modifier = Modifier.weight(1f),
        )
    }
}
