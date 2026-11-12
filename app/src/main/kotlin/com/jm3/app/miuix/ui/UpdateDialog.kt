package com.jm3.app.miuix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.CouiDialogActionStyle
import com.itos.coui.dialog.CouiDialogDefaults
import com.itos.coui.dialog.Dialog as CouiDialog
import com.itos.coui.effect.CouiContourStyle
import com.itos.coui.effect.SpotlightPreset
import com.itos.coui.shape.CouiShape
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text as CouiText
import com.itos.coui.text.rememberCouiTextStyle
import com.itos.coui.theme.CouiTheme as CouiKitTheme
import com.jm3.app.miuix.ui.coui.component.CouiDialogSecondaryButton
import com.jm3.app.miuix.ui.coui.component.CouiLinearProgressBar
import com.jm3.app.miuix.ui.coui.component.couiContourSurface
import com.jm3.app.miuix.ui.coui.component.couiDialogMaterial
import com.jm3.app.miuix.ui.coui.component.couiDialogSurfaceColor
import com.jm3.app.miuix.ui.coui.component.couiPressable
import com.jm3.app.miuix.ui.coui.icon.CouiIcons
import com.jm3.app.miuix.ui.skin.LocalSkin
import com.jm3.app.miuix.update.UpdateDownloadState
import com.jm3.core.settings.UiSkin
import com.jm3.core.update.MarkdownBlock
import com.jm3.core.update.MarkdownBlocks
import com.jm3.core.update.ReleaseInfo
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
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
internal data class MarkdownNotesStyle(
    val textColor: Color,
    val secondaryColor: Color,
    val codeColor: Color,
    val linkColor: Color,
    val pillBackground: Color,
    val pillBorder: Color,
    val dividerColor: Color,
    val heading1Style: TextStyle,
    val heading2Style: TextStyle,
    val heading3Style: TextStyle,
    val bodyStyle: TextStyle,
    val emptyStyle: TextStyle,
)

@Composable
internal fun rememberMiuixMarkdownNotesStyle(): MarkdownNotesStyle {
    val colors = MiuixTheme.colorScheme
    val styles = MiuixTheme.textStyles
    val dark = colors.surface.luminance() < 0.5f
    return MarkdownNotesStyle(
        textColor = colors.onSurface,
        secondaryColor = colors.onSurfaceVariantSummary,
        codeColor = colors.onSurface.copy(alpha = 0.90f),
        linkColor = colors.primary,
        pillBackground = colors.onSurface.copy(alpha = if (dark) 0.14f else 0.065f),
        pillBorder = colors.outline.copy(alpha = if (dark) 0.45f else 0.35f),
        dividerColor = colors.outline.copy(alpha = 0.4f),
        heading1Style = styles.title4.copy(fontWeight = FontWeight.Bold),
        heading2Style = styles.body1.copy(fontWeight = FontWeight.Bold),
        heading3Style = styles.body2.copy(fontWeight = FontWeight.Bold),
        bodyStyle = styles.body2,
        emptyStyle = styles.footnote2,
    )
}

@Composable
private fun rememberCouiMarkdownNotesStyle(): MarkdownNotesStyle {
    val dark = CouiKitTheme.isDark
    val colors = CouiKitTheme.colorScheme
    val itemTitle = rememberCouiTextStyle(CouiTextKind.ItemTitle)
    val searchBody = rememberCouiTextStyle(CouiTextKind.Search)
    val summary = rememberCouiTextStyle(CouiTextKind.ItemSummary)
    val footer = rememberCouiTextStyle(CouiTextKind.GroupFooter)
    return MarkdownNotesStyle(
        textColor = colors.onSurface,
        secondaryColor = colors.onSurfaceVariant,
        codeColor = colors.onSurface.copy(alpha = 0.90f),
        linkColor = colors.accent,
        pillBackground = colors.onSurface.copy(alpha = if (dark) 0.14f else 0.065f),
        pillBorder = colors.outlineVariant.copy(alpha = if (dark) 0.50f else 0.42f),
        dividerColor = colors.outlineVariant.copy(alpha = 0.65f),
        heading1Style = itemTitle.copy(fontWeight = FontWeight.Bold),
        heading2Style = searchBody.copy(fontWeight = FontWeight.Bold),
        heading3Style = summary.copy(fontWeight = FontWeight.Bold),
        bodyStyle = summary,
        emptyStyle = footer,
    )
}

@Composable
internal fun MarkdownNotes(
    markdown: String,
    modifier: Modifier = Modifier,
    notesStyle: MarkdownNotesStyle = rememberMiuixMarkdownNotesStyle(),
) {
    // 解析结果按原文缓存：进度刷新、滚动都会触发重组，没必要每次都重新切块
    val blocks = remember(markdown) { MarkdownBlocks.parse(markdown) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(NotesBlockSpacing)) {
        if (blocks.isEmpty()) {
            Text(
                text = "该版本未提供更新说明。",
                style = notesStyle.emptyStyle,
                color = notesStyle.secondaryColor,
            )
            return@Column
        }
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> NotesHeading(heading = block, notesStyle = notesStyle)
                is MarkdownBlock.Bullet -> NotesListItem(prefix = "·", text = block.text, notesStyle = notesStyle)
                is MarkdownBlock.Ordered -> NotesListItem(prefix = "${block.number}.", text = block.text, notesStyle = notesStyle)
                MarkdownBlock.Divider -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .height(0.5.dp)
                        .background(notesStyle.dividerColor),
                )
                is MarkdownBlock.Paragraph -> InlineCodeText(
                    text = rememberNotesInline(text = block.text, notesStyle = notesStyle),
                    style = notesStyle.bodyStyle,
                    color = notesStyle.textColor,
                    codeColor = notesStyle.codeColor,
                    pillBackground = notesStyle.pillBackground,
                    pillBorder = notesStyle.pillBorder,
                )
            }
        }
    }
}

/**
 * 标题按层级映射字号。
 */
@Composable
private fun NotesHeading(
    heading: MarkdownBlock.Heading,
    notesStyle: MarkdownNotesStyle,
) {
    val style = when {
        heading.level <= 1 -> notesStyle.heading1Style
        heading.level == 2 -> notesStyle.heading2Style
        else -> notesStyle.heading3Style
    }
    InlineCodeText(
        text = rememberNotesInline(text = heading.text, notesStyle = notesStyle),
        style = style,
        color = notesStyle.textColor,
        codeColor = notesStyle.codeColor,
        pillBackground = notesStyle.pillBackground,
        pillBorder = notesStyle.pillBorder,
        modifier = Modifier.padding(top = NotesHeadingTopSpacing),
    )
}

/**
 * 列表项：符号列定宽 + 正文自适应。
 */
@Composable
private fun NotesListItem(
    prefix: String,
    text: String,
    notesStyle: MarkdownNotesStyle,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NotesPrefixGap)) {
        Text(
            text = prefix,
            style = notesStyle.bodyStyle,
            color = notesStyle.secondaryColor,
            modifier = Modifier.widthIn(min = NotesPrefixMinWidth),
        )
        InlineCodeText(
            text = rememberNotesInline(text = text, notesStyle = notesStyle),
            style = notesStyle.bodyStyle,
            color = notesStyle.textColor,
            codeColor = notesStyle.codeColor,
            pillBackground = notesStyle.pillBackground,
            pillBorder = notesStyle.pillBorder,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 行内标记解析结果的缓存；配色参与 key，主题切换后重新扫描。 */
@Composable
private fun rememberNotesInline(
    text: String,
    notesStyle: MarkdownNotesStyle,
): AnnotatedString = remember(text, notesStyle.codeColor, notesStyle.linkColor, notesStyle.pillBackground) {
    parseInlineNotes(
        text = text,
        codeColor = notesStyle.codeColor,
        linkColor = notesStyle.linkColor,
        codeBackground = notesStyle.pillBackground,
    )
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
                val codeText = text.substring(index + 1, closing)
                val codeStart = builder.length
                builder.pushStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        color = codeColor,
                        background = codeBackground,
                    ),
                )
                builder.append(codeText)
                builder.pop()
                builder.addStringAnnotation(
                    tag = INLINE_CODE_TAG,
                    annotation = codeText,
                    start = codeStart,
                    end = builder.length,
                )
                index = closing + 1
            } else {
                builder.append(CodeMark)
                index++
            }
            continue
        }
        if (text[index] == '<') {
            val closingAngle = text.indexOf('>', index + 1)
            if (closingAngle > index + 1) {
                val candidate = text.substring(index + 1, closingAngle)
                if (looksLikeHttpUrl(candidate) && candidate.none { it.isWhitespace() }) {
                    builder.appendUrlLink(candidate, linkColor)
                    index = closingAngle + 1
                    continue
                }
            }
        }
        if (looksLikeHttpUrlAt(text, index)) {
            val end = extractBareUrlEnd(text, index)
            if (end > index) {
                builder.appendUrlLink(text.substring(index, end), linkColor)
                index = end
                continue
            }
        }
        builder.append(text[index])
        index++
    }
    return builder.toAnnotatedString()
}

private fun AnnotatedString.Builder.appendUrlLink(url: String, linkColor: Color) {
    withLink(
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
        append(url)
    }
}

private fun looksLikeHttpUrl(text: String): Boolean =
    text.startsWith("https://") || text.startsWith("http://")

private fun looksLikeHttpUrlAt(text: String, index: Int): Boolean {
    if (!text.startsWith("https://", index) && !text.startsWith("http://", index)) return false
    if (index == 0) return true
    val prev = text[index - 1]
    return prev !in 'a'..'z' && prev !in 'A'..'Z' && prev !in '0'..'9'
}

private fun extractBareUrlEnd(text: String, start: Int): Int {
    var end = start
    while (end < text.length) {
        val c = text[end]
        if (c.isWhitespace() || c == '<' || c == '>' || c == '"' || c == '\'' ||
            c in "\u3001\uFF0C\u3002\uFF1B\uFF1A\uFF08\uFF09\u3010\u3011"
        ) {
            break
        }
        end++
    }
    while (end > start) {
        val tail = text[end - 1]
        val trimPunct = when (tail) {
            '.', ',', ';', '!', '?', ':' -> true
            ')' -> text.substring(start, end).count { it == '(' } < text.substring(start, end).count { it == ')' }
            ']' -> text.substring(start, end).count { it == '[' } < text.substring(start, end).count { it == ']' }
            else -> false
        }
        if (!trimPunct) break
        end--
    }
    return if (end > text.indexOf("://", start) + 3) end else start
}

/**
 * 发现新版本的一次性提示框：一行版本跃迁 + 更新说明 + 主次两级动作。
 *
 * 取舍：
 * - 版本信息不再单独成卡：顶部摘要已给出「当前 → 最新 · 发布日期」，弹窗里不再重复
 *   当前版本、版本名称、安装包文件名、附件数量、说明行数等对"是否更新"没有影响的信息。
 * - 两种皮肤的说明区共用 [NotesScrollViewport]：按 [NotesDialogViewport] 限高并内部滚动，
 *   只在还有内容的一侧羽化；折叠行渲染在视口之外，说明展开后仍然一屏内可点。
 * - 弹窗本体不再自带滚动，避免同向两层滚动把折叠入口挤到视口外。
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
        updateDialogNotesState(release.notes, notesExpanded, COUI_UPDATE_NOTES_PREVIEW_LINES)
    }
    val downloading = downloadState is UpdateDownloadState.Downloading
    val dark = CouiKitTheme.isDark
    val colors = CouiKitTheme.colorScheme
    val dialogMaterial = couiDialogMaterial()
    val hasMaterial = dialogMaterial != null
    val horizontalPadding = CouiDialogDefaults.contentPadding
    val notesCardShape = remember { CouiShape(18.dp, 3f) }
    val couiNotesStyle = rememberCouiMarkdownNotesStyle()

    CouiDialog(
        visible = show,
        title = "发现新版本",
        message = plan.summary.takeIf { it.isNotBlank() },
        dark = dark,
        material = dialogMaterial,
        onDismissRequest = onDismiss,
        confirmAction = CouiDialogAction(
            text = plan.primaryText,
            onClick = { if (release.apk != null) onDownload() else onOpenRelease() },
            enabled = !downloading,
            style = CouiDialogActionStyle.Primary,
            colors = CouiDialogDefaults.primaryActionColors(colors.accent),
        ),
        neutralAction = if (plan.showCancelDownload) {
            CouiDialogAction(
                text = "取消下载",
                onClick = { onCancelDownload?.invoke() },
            )
        } else {
            null
        },
        dismissAction = CouiDialogAction(
            text = plan.dismissText,
            onClick = onDismiss,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding),
        ) {
            if (plan.showProgress) {
                CouiText(
                    text = plan.progressDetail.orEmpty(),
                    kind = CouiTextKind.ItemSummary,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Start,
                )
                Spacer(modifier = Modifier.height(6.dp))
                CouiLinearProgressBar(progress = plan.progressFraction)
                Spacer(modifier = Modifier.height(12.dp))
            }
            if (plan.hasNotes) {
                CouiText(
                    text = "更新说明",
                    kind = CouiTextKind.GroupTitle,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                // 卡片只负责材质与描边；限高、羽化与滚动统一由 FoldableNotes 提供。
                // 折叠行渲染在视口外侧，展开后不会跟着长文跑到几十屏开外，羽化也盖不到它。
                val notesFold = updateNotesFold(
                    expanded = notesExpanded,
                    hiddenLineCount = notesState.hiddenLineCount,
                    onToggle = { notesExpanded = !notesExpanded },
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .couiContourSurface(
                            shape = notesCardShape,
                            surfaceColor = couiDialogSurfaceColor(),
                            cornerRadius = 18.dp,
                            cornerWeight = 3f,
                            style = CouiContourStyle.Dialog,
                            dark = dark,
                            emphasized = false,
                            fallbackBorderColor = colors.onSurface.copy(alpha = if (dark) 0.14f else 0.08f),
                        ),
                ) {
                    FoldableNotes(
                        fold = notesFold,
                        spec = NotesDialogViewport,
                        content = {
                            MarkdownNotes(
                                markdown = notesState.visibleMarkdown,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                notesStyle = couiNotesStyle,
                            )
                        },
                        footer = {
                            // 视口只负责在视口里滚动；折叠行贴在视口下缘，展开再多说明也不用滑到底才点得到。
                            notesFold?.let { fold ->
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(0.5.dp)
                                            .background(colors.outlineVariant.copy(alpha = 0.55f)),
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 44.dp)
                                            .couiPressable(
                                                spotlight = SpotlightPreset.ChipTab,
                                                onClick = fold.onToggle,
                                            )
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        CouiText(
                                            text = fold.label,
                                            kind = CouiTextKind.ItemValue,
                                            color = colors.accent,
                                            textAlign = TextAlign.Start,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Icon(
                                            imageVector = if (fold.expanded) CouiIcons.ChevronUp else CouiIcons.ChevronDown,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = colors.accent,
                                        )
                                    }
                                }
                            }
                        },
                    )
                }
            }
            if (plan.showIgnore) {
                Spacer(modifier = Modifier.height(12.dp))
                CouiDialogSecondaryButton(
                    text = "忽略此版本",
                    contentColor = CouiDialogDefaults.themeColors(dark).destructive,
                    hasMaterial = hasMaterial,
                    onClick = { onIgnore?.invoke(release.version) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (plan.showProgress || plan.hasNotes || plan.showIgnore) {
                Spacer(modifier = Modifier.height(12.dp))
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
    val skin = LocalSkin.current
    // 说明区自带视口后，弹窗本体不再套第二层滚动：两层同向滚动时折叠入口会掉在视口外，
    // 用户得先把整段说明滑到底才点得到"收起说明"。
    val notesFold = updateNotesFold(
        expanded = notesExpanded,
        hiddenLineCount = notesState.hiddenLineCount,
        onToggle = { notesExpanded = !notesExpanded },
    )

    WindowDialog(
        show = show,
        title = "发现新版本",
        summary = plan.summary,
        cornerRadius = skin.shapes.dialogCorner,
        onDismissRequest = onDismiss,
    ) {
        // 水平内边距由弹窗的 insideMargin 提供；限高、羽化与滚动都交给说明区自己的视口，
        // 这样进度、说明与按键区各就各位，说明再长也不会把按键区推出屏幕。
        Column(modifier = Modifier.fillMaxWidth()) {
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
                        FoldableNotes(
                            fold = notesFold,
                            spec = NotesDialogViewport,
                            content = {
                                MarkdownNotes(
                                    markdown = notesState.visibleMarkdown,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            },
                            footer = {
                                notesFold?.let { fold ->
                                    TextButton(
                                        text = fold.label,
                                        onClick = fold.onToggle,
                                        modifier = Modifier.fillMaxWidth().padding(top = NotesBlockSpacing),
                                    )
                                }
                            },
                        )
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
