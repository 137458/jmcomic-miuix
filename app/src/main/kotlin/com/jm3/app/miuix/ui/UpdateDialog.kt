package com.jm3.app.miuix.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jm3.core.update.MarkdownBlock
import com.jm3.core.update.MarkdownBlocks
import com.jm3.core.update.ReleaseInfo
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 说明块的间距与列表符号列宽，集中成常量避免样式散落在各个 when 分支里。 */
private val NotesBlockSpacing = 6.dp
private val NotesHeadingTopSpacing = 6.dp
private val NotesPrefixMinWidth = 20.dp
private val NotesPrefixGap = 6.dp

/** 弹窗内说明区的限高：超出部分在弹窗内部滚动，不把底部的"立即下载"顶出屏幕。 */
private val NotesDialogMaxHeight = 200.dp

/**
 * 把 [MarkdownBlocks] 的块级结果排版成 Compose 文本，供页面与弹窗共用。
 *
 * 取舍：core 只做分块、不做行内解析（见 [MarkdownBlocks] 的说明），粗体与内联代码这层由这里补上。
 * 只认 `**粗体**` 与反引号两种标记，其余标记（链接、图片、HTML）一律按原文显示：更新说明是
 * 外部输入，多认一种标记就多一份误渲染与解析失败的风险，而 Releases 正文的实际用法就是这两种。
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
    val codeBackground = MiuixTheme.colorScheme.surfaceVariant
    return remember(text, codeColor, codeBackground) {
        parseInlineNotes(text = text, codeColor = codeColor, codeBackground = codeBackground)
    }
}

/**
 * 解析行内的 `**粗体**` 与反引号内联代码。
 *
 * 两种标记都只在"成对出现"时生效，未闭合的标记按原文输出。更新说明里出现半个标记的概率很低，
 * 但一旦把它当开标记，剩下整段都会变成粗体或等宽：错误会从一个小符号扩散成整段排版事故，
 * 所以这里宁可漏渲染也不误渲染。
 */
private fun parseInlineNotes(text: String, codeColor: Color, codeBackground: Color): AnnotatedString {
    val builder = AnnotatedString.Builder()
    // pushStyle 与 pop 严格成对：只有确认后面确实存在结束标记时才 push，因此循环结束时栈必定为空
    var bold = false
    var index = 0
    while (index < text.length) {
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
 * 发现新版本的一次性提示框：版本跃迁 + 更新说明 + 立即下载/稍后。
 *
 * 取舍：
 * - 说明区限高 [NotesDialogMaxHeight] 并独立滚动。Releases 说明可能很长，不限高会把弹窗撑到
 *   超出屏幕，"立即下载"被挤出可视区，用户反而找不到主操作。
 * - 页面本身也有完整的版本与说明，所以"稍后"就是关闭弹窗，不需要额外状态记录"本次已提示"：
 *   是否弹窗由页面每次重检的结果决定。
 * - 没有 .apk 资产时主按钮改为打开 Releases 页面，保证主操作永远有明确结果，不出现点了没反应。
 *
 * - [onIgnore] 非空时多一颗"忽略此版本"：写入忽略版本后该版本不再弹窗，但页面仍可看到它。
 *
 * @param show 由页面控制显隐；下载进行中时页面不会置为 true
 */
@Composable
internal fun UpdateAvailableDialog(
    show: Boolean,
    currentVersion: String,
    release: ReleaseInfo,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onOpenRelease: () -> Unit,
    onIgnore: ((version: String) -> Unit)? = null,
) {
    val hasApk = release.apk != null
    OverlayDialog(
        show = show,
        title = "发现新版本 ${release.version}",
        summary = dialogSummary(currentVersion = currentVersion, release = release),
        onDismissRequest = onDismiss,
    ) {
        // 水平内边距由弹窗的 insideMargin 提供，这里只负责限高与滚动，避免左右变成双倍留白
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = NotesDialogMaxHeight)
                .verticalScroll(rememberScrollState()),
        ) {
            MarkdownNotes(markdown = release.notes, modifier = Modifier.fillMaxWidth())
        }
        // 忽略放在两键之上单独一行：三键等分会把"忽略此版本"挤成折行
        if (onIgnore != null) {
            TextButton(
                text = "忽略此版本",
                onClick = { onIgnore.invoke(release.version) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
        DialogActionRow(
            primaryText = if (hasApk) "立即下载" else "前往 Releases 页面",
            onPrimary = if (hasApk) onDownload else onOpenRelease,
            secondaryText = "稍后",
            onSecondary = onDismiss,
        )
    }
}

/** 弹窗摘要：版本跃迁，外加发布日期（有则显示）；安装包大小留给页面上的版本信息卡。 */
private fun dialogSummary(currentVersion: String, release: ReleaseInfo): String {
    val base = "当前 $currentVersion → 最新 ${release.version}"
    return if (release.publishedDate.isNotBlank()) "$base，发布于 ${release.publishedDate}" else base
}

/** 行内标记：只认粗体与内联代码两种，长度变化时两处扫描逻辑需同步。 */
private const val BoldMark = "**"
private const val CodeMark = '`'
private val BoldSpan = SpanStyle(fontWeight = FontWeight.Bold)
