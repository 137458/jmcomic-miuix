// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

internal const val INLINE_CODE_TAG = "inline_code"

private const val THIN_OUTER_SPACE = '\u2009'
private const val HAIR_INNER_PAD = '\u200A'
private const val OPENING_PUNCT = "([{\"'“‘（【《「『"
private const val CLOSING_PUNCT = ".,;:!?)]}\"'”’。，、；：！？）】》」』"

internal data class InlineCodeDisplay(
    val text: AnnotatedString,
    val codeRanges: List<IntRange>,
)

/**
 * 将原始 Markdown 行内代码标记转换成可渲染的胶囊显示模型：
 * 保留原文链接与非代码 SpanStyle，在代码片段两侧补窄间隔与内部胶囊留白，并剥离生硬矩形背景。
 */
internal fun buildInlineCodeDisplay(
    source: AnnotatedString,
    codeColor: Color,
    codeFontSize: TextUnit,
): InlineCodeDisplay {
    val rawSpans = collectInlineCodeSpans(source)
    if (rawSpans.isEmpty()) {
        return InlineCodeDisplay(text = source, codeRanges = emptyList())
    }

    val charOutputPos = IntArray(source.length)
    val builder = AnnotatedString.Builder()
    val codeRanges = mutableListOf<IntRange>()
    var cursor = 0

    for (span in rawSpans) {
        val spanStart = span.first
        val spanEnd = span.last + 1
        if (spanStart > cursor) {
            appendPlainSlice(source.text, cursor, spanStart, builder, charOutputPos)
        }
        val prev = source.text.getOrNull(spanStart - 1)
        if (prev != null && !prev.isWhitespace() && prev !in OPENING_PUNCT) {
            builder.append(THIN_OUTER_SPACE)
        }

        val pillStart = builder.length
        builder.pushStyle(
            SpanStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = codeFontSize,
                fontWeight = FontWeight.Medium,
                color = codeColor,
                background = Color.Transparent,
            ),
        )
        builder.append(HAIR_INNER_PAD)
        for (i in spanStart until spanEnd) {
            charOutputPos[i] = builder.length
            builder.append(source.text[i])
        }
        builder.append(HAIR_INNER_PAD)
        builder.pop()
        val pillEnd = builder.length
        if (pillEnd > pillStart) {
            codeRanges += pillStart until pillEnd
        }

        val next = source.text.getOrNull(spanEnd)
        if (next != null && !next.isWhitespace() && next !in CLOSING_PUNCT) {
            builder.append(THIN_OUTER_SPACE)
        }
        cursor = spanEnd
    }
    if (cursor < source.length) {
        appendPlainSlice(source.text, cursor, source.length, builder, charOutputPos)
    }

    remapNonCodeAnnotations(source = source, codeSpans = rawSpans, charOutputPos = charOutputPos, builder = builder)
    return InlineCodeDisplay(text = builder.toAnnotatedString(), codeRanges = codeRanges)
}

private fun collectInlineCodeSpans(source: AnnotatedString): List<IntRange> {
    val tagged = source.getStringAnnotations(tag = INLINE_CODE_TAG, start = 0, end = source.length)
        .mapNotNull { ann -> (ann.start until ann.end).takeIf { it.first < it.last + 1 } }
    val candidates = if (tagged.isNotEmpty()) {
        tagged
    } else {
        source.spanStyles.mapNotNull { range ->
            val style = range.item
            val isCodeSpan = style.fontFamily == FontFamily.Monospace &&
                style.background != Color.Unspecified &&
                style.background != Color.Transparent
            if (isCodeSpan && range.start < range.end) range.start until range.end else null
        }
    }
    if (candidates.isEmpty()) return emptyList()
    val sorted = candidates.sortedWith(compareBy<IntRange> { it.first }.thenBy { it.last })
    val merged = mutableListOf<IntRange>()
    var lastEnd = 0
    for (range in sorted) {
        val start = range.first.coerceIn(0, source.length)
        val end = (range.last + 1).coerceIn(0, source.length)
        if (start >= lastEnd && start < end) {
            merged += start until end
            lastEnd = end
        }
    }
    return merged
}

private fun appendPlainSlice(
    rawText: String,
    start: Int,
    end: Int,
    builder: AnnotatedString.Builder,
    charOutputPos: IntArray,
) {
    for (i in start until end) {
        charOutputPos[i] = builder.length
        builder.append(rawText[i])
    }
}

private fun remapRange(start: Int, end: Int, totalLength: Int, charOutputPos: IntArray): IntRange? {
    if (start < 0 || end <= start || end > totalLength) return null
    val mappedStart = charOutputPos[start]
    val mappedEnd = charOutputPos[end - 1] + 1
    return if (mappedStart < mappedEnd) mappedStart until mappedEnd else null
}

private fun remapNonCodeAnnotations(
    source: AnnotatedString,
    codeSpans: List<IntRange>,
    charOutputPos: IntArray,
    builder: AnnotatedString.Builder,
) {
    source.spanStyles.forEach { range ->
        val overlapsCode = codeSpans.any { code -> range.start <= code.last && range.end > code.first }
        val isRawCodeBackground = range.item.fontFamily == FontFamily.Monospace &&
            range.item.background != Color.Unspecified
        if (overlapsCode && isRawCodeBackground) return@forEach
        val mapped = remapRange(range.start, range.end, source.length, charOutputPos) ?: return@forEach
        val style = if (overlapsCode && range.item.background != Color.Unspecified) {
            range.item.copy(background = Color.Transparent)
        } else {
            range.item
        }
        builder.addStyle(style, mapped.first, mapped.last + 1)
    }

    source.getLinkAnnotations(0, source.length).forEach { range ->
        val mapped = remapRange(range.start, range.end, source.length, charOutputPos) ?: return@forEach
        when (val item = range.item) {
            is LinkAnnotation.Url -> builder.addLink(item, mapped.first, mapped.last + 1)
            is LinkAnnotation.Clickable -> builder.addLink(item, mapped.first, mapped.last + 1)
        }
    }
}

/**
 * 带行内代码圆角胶囊底托与精细间距的 Markdown 文本渲染组件。
 */
@Composable
internal fun InlineCodeText(
    text: AnnotatedString,
    style: TextStyle,
    color: Color,
    codeColor: Color,
    pillBackground: Color,
    pillBorder: Color,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val resolvedCodeFontSize = remember(style.fontSize) {
        if (style.fontSize.isSpecified) {
            (style.fontSize.value * 0.88f).coerceAtLeast(11f).sp
        } else {
            12.5.sp
        }
    }
    val display = remember(text, codeColor, resolvedCodeFontSize) {
        buildInlineCodeDisplay(
            source = text,
            codeColor = codeColor,
            codeFontSize = resolvedCodeFontSize,
        )
    }
    var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }

    val mergedStyle = remember(style, color, textAlign) {
        style.merge(
            TextStyle(
                color = color,
                textAlign = textAlign ?: style.textAlign,
            ),
        )
    }

    BasicText(
        text = display.text,
        style = mergedStyle,
        maxLines = maxLines,
        overflow = overflow,
        onTextLayout = { textLayout = it },
        modifier = modifier.drawBehind {
            val layout = textLayout ?: return@drawBehind
            if (display.codeRanges.isEmpty()) return@drawBehind
            val cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx())
            val hPad = 1.dp.toPx()
            val vPad = 1.5.dp.toPx()
            val borderStroke = Stroke(width = 0.5.dp.toPx())
            val lineInset = 0.5.dp.toPx()

            display.codeRanges.forEach { range ->
                val start = range.first.coerceIn(0, display.text.length - 1)
                val end = range.last.coerceIn(start, display.text.length - 1)
                val firstLine = layout.getLineForOffset(start)
                val lastLine = layout.getLineForOffset(end)

                for (line in firstLine..lastLine) {
                    val segStart = if (line == firstLine) start else layout.getLineStart(line)
                    val segEnd = if (line == lastLine) {
                        end
                    } else {
                        (layout.getLineEnd(line, visibleEnd = true) - 1).coerceAtLeast(segStart)
                    }
                    if (segStart > segEnd) continue

                    val startBox = layout.getBoundingBox(segStart)
                    val endBox = layout.getBoundingBox(segEnd)
                    val left = minOf(startBox.left, endBox.left) - hPad
                    val right = maxOf(startBox.right, endBox.right) + hPad

                    var minCharTop = Float.MAX_VALUE
                    var maxCharBottom = Float.MIN_VALUE
                    for (offset in segStart..segEnd) {
                        if (display.text.text[offset] == HAIR_INNER_PAD) continue
                        val box = layout.getBoundingBox(offset)
                        if (box.top < minCharTop) minCharTop = box.top
                        if (box.bottom > maxCharBottom) maxCharBottom = box.bottom
                    }
                    val lineTop = layout.getLineTop(line)
                    val lineBottom = layout.getLineBottom(line)
                    val rawTop = if (minCharTop < Float.MAX_VALUE) minCharTop - vPad else lineTop + lineInset
                    val rawBottom = if (maxCharBottom > Float.MIN_VALUE) maxCharBottom + vPad else lineBottom - lineInset
                    val top = rawTop.coerceAtLeast(lineTop + lineInset)
                    val bottom = rawBottom.coerceAtMost(lineBottom - lineInset)
                    if (right <= left || bottom <= top) continue

                    val topLeft = Offset(left, top)
                    val pillSize = Size(right - left, bottom - top)
                    drawRoundRect(
                        color = pillBackground,
                        topLeft = topLeft,
                        size = pillSize,
                        cornerRadius = cornerRadius,
                    )
                    if (pillBorder.alpha > 0f) {
                        drawRoundRect(
                            color = pillBorder,
                            topLeft = topLeft,
                            size = pillSize,
                            cornerRadius = cornerRadius,
                            style = borderStroke,
                        )
                    }
                }
            }
        },
    )
}
