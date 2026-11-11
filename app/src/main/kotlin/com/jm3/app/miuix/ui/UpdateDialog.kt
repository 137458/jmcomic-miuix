package com.jm3.app.miuix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.CouiDialogActionStyle
import com.itos.coui.dialog.CouiDialogDefaults
import com.itos.coui.dialog.Dialog as CouiDialog
import com.itos.coui.effect.SpotlightPreset
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.theme.CouiTheme as CouiKitTheme
import com.jm3.app.miuix.ui.coui.component.couiPressable
import com.jm3.app.miuix.ui.skin.LocalSkin
import com.jm3.app.miuix.update.UpdateDownloadState
import com.jm3.core.settings.UiSkin
import com.jm3.core.update.MarkdownBlock
import com.jm3.core.update.MarkdownBlocks
import com.jm3.core.update.ReleaseInfo
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/** 说明块的间距与列表符号列宽，集中成常量避免样式散落在各个 when 分支里。 */
private val NotesBlockSpacing = 6.dp
private val NotesHeadingTopSpacing = 6.dp
private val NotesPrefixMinWidth = 20.dp
private val NotesPrefixGap = 6.dp

/** 弹窗内容区的限高：超长说明在弹窗内部滚动，底部主按钮始终留在可视区。 */
private val DialogBodyMaxHeight = 240.dp

/** 内容区上下羽化距离：按视口高度取，太短会只剩一条几乎看不见的边。 */
private val DialogBodyFade = 40.dp

/** 弹窗底部：内容到按键区、按键之间各留一档，按键边界才分得清。 */
private val DialogButtonsTopSpacing = 12.dp
private val DialogButtonGap = 8.dp

/**
 * 弹窗内容已由 WindowDialog 内建水平内边距，卡片贴齐内容区；标题同样按 0 对齐卡外缘。
 * 不用页面的 28dp 基线：弹窗内没有第二层缩进空间，套用后标题会比上方的进度说明再右移 28dp。
 */
private val DialogSectionMargin = PaddingValues(horizontal = 0.dp, vertical = 6.dp)
private val DialogCardPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)

/**
 * 把 [MarkdownBlocks] 的块级结果排版成 Compose 文本，供页面与弹窗共用。
 *
 * 取舍：core 只做分块、不做行内解析（见 [MarkdownBlocks] 的说明），粗体、内联代码与链接这层由这里补上。
 * 只认 `**粗体**`、反引号与 `[文字](链接)` 三种标记，其余标记（图片、HTML）一律按原文显示：
 * 更新说明是外部输入，多认一种标记就多一份误渲染与解析失败的风险，而 Releases 正文的实际用法就是这三种。
 *
 * @param markdown Release 说明原文，空串表示该版本没写说明
 */
@Composable
internal fun MarkdownNotes(markdown: String, modifier: Modifier = Modifier) {
    // 解析结果按原文缓存：进度刷新、滚动都会触发重组，没必要每次都重新切块
    val blocks = remember(markdown) { MarkdownBlocks.parse(markdown) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(NotesBlockSpacing)) {
        if (blocks.isEmpty()) {
            Text(
                text = "该版本未提供更新说明。",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            return@Column
        }
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> NotesHeading(heading = block)
                is MarkdownBlock.Bullet -> NotesListItem(prefix = "·", text = block.text)
                is MarkdownBlock.Ordered -> NotesListItem(prefix = "${block.number}.", text = block.text)
                MarkdownBlock.Divider -> HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                is MarkdownBlock.Paragraph -> Text(
                    text = rememberNotesInline(text = block.text),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/**
 * 标题按层级映射字号。
 *
 * 取舍：Miuix 0.9.4 的 textStyles 只到 title4，没有 title5，三级标题若继续沿用标题档位就要
 * 手写字号；这里 1 级用 title4、2 级用 body1 加粗、3 级及更深用 body2 加粗，靠字重与字号
 * 两档区分层级，既不再造一套字号，也不会出现"三级标题和正文同字号且分不出层级"。
 */
@Composable
private fun NotesHeading(heading: MarkdownBlock.Heading) {
    val style = when {
        heading.level <= 1 -> MiuixTheme.textStyles.title4
        heading.level == 2 -> MiuixTheme.textStyles.body1
        else -> MiuixTheme.textStyles.body2
    }
    Text(
        text = rememberNotesInline(text = heading.text),
        style = style,
        fontWeight = FontWeight.Bold,
        color = MiuixTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = NotesHeadingTopSpacing),
    )
}

/**
 * 列表项：符号列定宽 + 正文自适应。
 *
 * 符号列定宽而不是把 "·" 拼进正文，是为了让多行文本的换行位置对齐到同一列；
 * 否则长条目换行后会顶到符号下方，列表的层级感就没了。
 */
@Composable
private fun NotesListItem(prefix: String, text: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NotesPrefixGap)) {
        Text(
            text = prefix,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.widthIn(min = NotesPrefixMinWidth),
        )
        Text(
            text = rememberNotesInline(text = text),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 行内标记解析结果的缓存；配色参与 key，主题切换后重新扫描。 */
@Composable
private fun rememberNotesInline(text: String): AnnotatedString {
    val codeColor = MiuixTheme.colorScheme.primary
    val linkColor = MiuixTheme.colorScheme.primary
    val codeBackground = MiuixTheme.colorScheme.surfaceContainerHigh
    return remember(text, codeColor, linkColor, codeBackground) {
        parseInlineNotes(
            text = text,
            codeColor = codeColor,
            linkColor = linkColor,
            codeBackground = codeBackground,
        )
    }
}

/**
 * 解析行内的 `**粗体**`、反引号内联代码与 `[文字](链接)`。
 *
 * 三类标记都只在"成对出现"时生效，未闭合的标记按原文输出。更新说明里出现半个标记的概率很低，
 * 但一旦把它当开标记，剩下整段都会变成粗体或等宽：错误会从一个小符号扩散成整段排版事故，
 * 所以这里宁可漏渲染也不误渲染。
 */
internal fun parseInlineNotes(
    text: String,
    codeColor: Color,
    linkColor: Color,
    codeBackground: Color,
): AnnotatedString {
    val builder = AnnotatedString.Builder()
    // pushStyle 与 pop 严格成对：只有确认后面确实存在结束标记时才 push，因此循环结束时栈必定为空
    var bold = false
    var index = 0
    while (index < text.length) {
        if (text[index] == LinkOpen) {
            val closingBracket = text.indexOf(LinkClose, index + 1)
            val closingParen = if (
                closingBracket > index + 1 &&
                closingBracket + 1 < text.length &&
                text[closingBracket + 1] == LinkTargetOpen
            ) {
                text.indexOf(LinkTargetClose, closingBracket + 2)
            } else {
                -1
            }
            if (closingBracket > index + 1 && closingParen > closingBracket + 2) {
                val label = text.substring(index + 1, closingBracket)
                val url = text.substring(closingBracket + 2, closingParen)
                builder.withLink(
                    LinkAnnotation.Url(
                        url = url,
                        styles = TextLinkStyles(
                            style = SpanStyle(
                                color = linkColor,
                                textDecoration = TextDecoration.Underline,
                            ),
                        ),
                    ),
                ) {
                    append(label)
                }
                index = closingParen + 1
                continue
            }
        }
        if (text.startsWith(BoldMark, index)) {
            val closing = text.indexOf(BoldMark, index + BoldMark.length)
            when {
                bold -> {
                    builder.pop()
                    bold = false
                    index += BoldMark.length
                }

                // 结束标记必须晚于起始标记的下一位，空内容（****）按原文输出
                closing > index + BoldMark.length -> {
                    builder.pushStyle(BoldSpan)
                    bold = true
                    index += BoldMark.length
                }

                else -> {
                    builder.append(BoldMark)
                    index += BoldMark.length
                }
            }
            continue
        }
        if (text[index] == CodeMark) {
            val closing = text.indexOf(CodeMark, index + 1)
            if (closing > index + 1) {
                builder.pushStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        color = codeColor,
                        background = codeBackground,
                    ),
                )
                builder.append(text.substring(index + 1, closing))
                builder.pop()
                index = closing + 1
            } else {
                builder.append(CodeMark)
                index++
            }
            continue
        }
        builder.append(text[index])
        index++
    }
    return builder.toAnnotatedString()
}

/**
 * 发现新版本的一次性提示框：一行版本跃迁 + 更新说明 + 主次两级动作。
 *
 * 取舍：
 * - 版本信息不再单独成卡：顶部摘要已给出「当前 → 最新 · 发布日期」，弹窗里不再重复
 *   当前版本、版本名称、安装包文件名、附件数量、说明行数等对"是否更新"没有影响的信息。
 * - 说明区限高 [DialogBodyMaxHeight] 并整体滚动，上下边缘按是否还有内容做羽化
 *   （[verticalEdgeFade]）：滚到顶/底时对应一侧完全实心，不做"永远有雾"的暗示。
 * - 长说明默认折叠到 [UPDATE_NOTES_PREVIEW_LINES] 行，短说明不出现"展开全部"。
 * - 底部只有主按钮（下载 / 前往 Releases）与「稍后」「取消下载（仅下载中）」；
 *   「忽略此版本」降级为说明下方的文字链接，不再与主操作并列。
 * - 没有 .apk 资产时主按钮改为打开 Releases 页面，保证主操作永远有明确结果。
 *
 * @param show 由页面控制显隐；下载完成或失败时页面收起弹窗，安装与重试交给页面的下载卡
 * @param downloadState 下载状态，参与进度条、主按钮文案与禁用态
 */
@Composable
internal fun UpdateAvailableDialog(
    show: Boolean,
    currentVersion: String,
    release: ReleaseInfo,
    downloadState: UpdateDownloadState = UpdateDownloadState.Idle,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onOpenRelease: () -> Unit,
    onIgnore: ((version: String) -> Unit)? = null,
    onCancelDownload: (() -> Unit)? = null,
) {
    val skin = LocalSkin.current
    if (skin.id == UiSkin.COLOROS) {
        CouiUpdateAvailableDialog(
            show = show,
            currentVersion = currentVersion,
            release = release,
            downloadState = downloadState,
            onDismiss = onDismiss,
            onDownload = onDownload,
            onOpenRelease = onOpenRelease,
            onIgnore = onIgnore,
            onCancelDownload = onCancelDownload,
        )
    } else {
        MiuixUpdateAvailableDialog(
            show = show,
            currentVersion = currentVersion,
            release = release,
            downloadState = downloadState,
            onDismiss = onDismiss,
            onDownload = onDownload,
            onOpenRelease = onOpenRelease,
            onIgnore = onIgnore,
            onCancelDownload = onCancelDownload,
        )
    }
}

@Composable
private fun CouiUpdateAvailableDialog(
    show: Boolean,
    currentVersion: String,
    release: ReleaseInfo,
    downloadState: UpdateDownloadState,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onOpenRelease: () -> Unit,
    onIgnore: ((version: String) -> Unit)?,
    onCancelDownload: (() -> Unit)?,
) {
    val canIgnore = onIgnore != null
    val canCancelDownload = onCancelDownload != null
    val plan = remember(currentVersion, release, downloadState, canIgnore, canCancelDownload) {
        buildUpdateDialogPlan(
            currentVersion = currentVersion,
            release = release,
            downloadState = downloadState,
            canIgnore = canIgnore,
            canCancelDownload = canCancelDownload,
        )
    }
    var notesExpanded by remember(show, release.tagName) { mutableStateOf(false) }
    val notesState = remember(release.notes, notesExpanded) {
        updateDialogNotesState(release.notes, notesExpanded, UPDATE_NOTES_PREVIEW_LINES)
    }
    val downloading = downloadState is UpdateDownloadState.Downloading
    val scrollState = rememberScrollState()
    val skin = LocalSkin.current
    val colors = CouiKitTheme.colorScheme

    CouiDialog(
        visible = show,
        title = "发现新版本",
        dark = CouiKitTheme.isDark,
        onDismissRequest = onDismiss,
        confirmAction = CouiDialogAction(
            text = plan.primaryText,
            onClick = { if (release.apk != null) onDownload() else onOpenRelease() },
            enabled = !downloading,
            style = CouiDialogActionStyle.Primary,
            colors = CouiDialogDefaults.primaryActionColors(colors.accent),
        ),
        dismissAction = CouiDialogAction(
            text = if (plan.showCancelDownload) "取消下载" else plan.dismissText,
            onClick = { if (plan.showCancelDownload) onCancelDownload?.invoke() else onDismiss() },
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = DialogBodyMaxHeight)
                .verticalScroll(scrollState),
        ) {
            if (plan.summary.isNotBlank()) {
                Text(
                    text = plan.summary,
                    style = MiuixTheme.textStyles.footnote2,
                    color = colors.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            if (plan.showProgress) {
                Text(
                    text = plan.progressDetail.orEmpty(),
                    style = MiuixTheme.textStyles.footnote2,
                    color = colors.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors.container),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((plan.progressFraction ?: 0f).coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(colors.accent),
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
            if (plan.hasNotes) {
                Text(
                    text = "更新说明",
                    style = MiuixTheme.textStyles.title4,
                    color = colors.onSurface,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(skin.shapes.cardCorner))
                        .background(colors.card)
                        .padding(DialogCardPadding),
                ) {
                    Column {
                        MarkdownNotes(
                            markdown = notesState.visibleMarkdown,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (notesExpanded || notesState.hiddenLineCount > 0) {
                            Text(
                                text = if (notesExpanded) "收起说明" else "展开全部（还有 ${notesState.hiddenLineCount} 行）",
                                style = MiuixTheme.textStyles.footnote1,
                                color = colors.accent,
                                modifier = Modifier
                                    .clip(CouiCapsuleShape)
                                    .couiPressable(shape = CouiCapsuleShape) { notesExpanded = !notesExpanded }
                                    .padding(top = NotesBlockSpacing, bottom = 4.dp),
                            )
                        }
                    }
                }
            }
            if (plan.showIgnore) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "忽略此版本",
                    style = MiuixTheme.textStyles.footnote1,
                    color = CouiDialogDefaults.themeColors(CouiKitTheme.isDark).destructive,
                    modifier = Modifier
                        .clip(CouiCapsuleShape)
                        .couiPressable(shape = CouiCapsuleShape) { onIgnore?.invoke(release.version) }
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun MiuixUpdateAvailableDialog(
    show: Boolean,
    currentVersion: String,
    release: ReleaseInfo,
    downloadState: UpdateDownloadState,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onOpenRelease: () -> Unit,
    onIgnore: ((version: String) -> Unit)?,
    onCancelDownload: (() -> Unit)?,
) {
    val canIgnore = onIgnore != null
    val canCancelDownload = onCancelDownload != null
    val plan = remember(currentVersion, release, downloadState, canIgnore, canCancelDownload) {
        buildUpdateDialogPlan(
            currentVersion = currentVersion,
            release = release,
            downloadState = downloadState,
            canIgnore = canIgnore,
            canCancelDownload = canCancelDownload,
        )
    }
    var notesExpanded by remember(show, release.tagName) { mutableStateOf(false) }
    val notesState = remember(release.notes, notesExpanded) {
        updateDialogNotesState(release.notes, notesExpanded, UPDATE_NOTES_PREVIEW_LINES)
    }
    val downloading = downloadState is UpdateDownloadState.Downloading
    val scrollState = rememberScrollState()
    val skin = LocalSkin.current
    // 只在还有内容的方向羽化：滚到顶或底时对应一侧恢复实心，避免"永远有雾"的假暗示
    val hasContentAbove by remember { derivedStateOf { scrollState.value > 0 } }
    val hasContentBelow by remember { derivedStateOf { scrollState.maxValue > scrollState.value } }

    WindowDialog(
        show = show,
        title = "发现新版本",
        summary = plan.summary,
        cornerRadius = skin.shapes.dialogCorner,
        onDismissRequest = onDismiss,
    ) {
        // 水平内边距由弹窗的 insideMargin 提供，这里只负责限高、滚动与边缘羽化。
        // 羽化必须在 verticalScroll 外侧：放内侧时量到的是内容整高，遮罩落在滚出视口的顶部，
        // 用户看到的就是"被硬裁切、没有渐变"。放外侧后按视口高度生成渐变带，上下边缘才肉眼可见。
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = DialogBodyMaxHeight)
                .verticalEdgeFade(
                    top = hasContentAbove,
                    bottom = hasContentBelow,
                    fade = DialogBodyFade,
                )
                .verticalScroll(scrollState),
        ) {
            if (plan.showProgress) {
                Text(
                    text = plan.progressDetail.orEmpty(),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = plan.progressFraction,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (plan.hasNotes) {
                SmallTitle(text = "更新说明", insideMargin = DialogSectionMargin)
                Card(cornerRadius = skin.shapes.cardCorner, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(DialogCardPadding)) {
                        MarkdownNotes(
                            markdown = notesState.visibleMarkdown,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (notesExpanded || notesState.hiddenLineCount > 0) {
                            TextButton(
                                text = if (notesExpanded) "收起说明" else "展开全部（还有 ${notesState.hiddenLineCount} 行）",
                                onClick = { notesExpanded = !notesExpanded },
                                modifier = Modifier.fillMaxWidth().padding(top = NotesBlockSpacing),
                            )
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(top = DialogButtonsTopSpacing),
            verticalArrangement = Arrangement.spacedBy(DialogButtonGap),
        ) {
            Button(
                onClick = if (release.apk != null) onDownload else onOpenRelease,
                enabled = !downloading,
                colors = ButtonDefaults.buttonColorsPrimary(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = plan.primaryText, style = MiuixTheme.textStyles.button)
            }
            if (plan.showCancelDownload) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(DialogButtonGap),
                ) {
                    TextButton(
                        text = "取消下载",
                        onClick = { onCancelDownload?.invoke() },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = plan.dismissText,
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                TextButton(text = plan.dismissText, onClick = onDismiss, modifier = Modifier.fillMaxWidth())
            }
            if (plan.showIgnore) {
                TextButton(
                    text = "忽略此版本",
                    onClick = { onIgnore?.invoke(release.version) },
                    colors = ButtonDefaults.textButtonColors(textColor = MiuixTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * 弹窗主按钮的文案。
 *
 * 下载中把百分比放进按钮本身，用户不必回页面就能看到进度；没有安装包时主操作只能是打开
 * Releases 页面，此时下载状态恒为 Idle，同一分支即可覆盖两种情况。
 */
internal fun updateDialogPrimaryText(hasApk: Boolean, downloadState: UpdateDownloadState): String = when {
    !hasApk -> "前往 Releases 页面"
    downloadState is UpdateDownloadState.Downloading -> "下载中 ${updateProgressText(downloadState)}"
    else -> "立即下载"
}

/** 行内标记：只认粗体、内联代码与链接三种，长度变化时两处扫描逻辑需同步。 */
private const val BoldMark = "**"
private const val CodeMark = '`'
private const val LinkOpen = '['
private const val LinkClose = ']'
private const val LinkTargetOpen = '('
private const val LinkTargetClose = ')'
private val BoldSpan = SpanStyle(fontWeight = FontWeight.Bold)
