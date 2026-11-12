package com.jm3.app.miuix.ui.coui.component

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.text.rememberCouiTextStyle
import com.itos.coui.theme.CouiTheme
import com.itos.coui.theme.LocalCouiContentColor
import com.jm3.app.miuix.ui.LocalWideLayout

/**
 * coui 0.0.1-alpha10 将旧版 Body/SectionTitle/Stepper/SearchCancel 收敛进更精简的
 * [CouiTextKind] 枚举；这里基于现有预设派生出对应字阶，保持全端排版与 sp 缩放一致。
 */
/** 大屏放大档比例：840dp 起字号、行高与配套图标抬一档，手机断点以下恒为 1。 */
private const val CouiLargeTypographyScale = 1.12f

/**
 * 大屏放大档是否生效：只认组合根下发的窗口宽度，不看 Configuration。
 *
 * 缩放只作用于我们自己画的文字与图标；组件库内部写死高度的控件（顶栏、胶囊按钮、
 * 弹层标题）不参与，否则会先撑破库内高度再被裁掉。
 */
@Composable
internal fun couiLargeTypography(): Boolean = LocalWideLayout.current.largeTypography

/** 按大屏档缩放字阶：字号与行高同比放大，未指定的行高原样保留。 */
@Composable
internal fun couiScaledStyle(style: TextStyle): TextStyle {
    if (!couiLargeTypography()) return style
    return style.copy(
        fontSize = if (style.fontSize.isSpecified) style.fontSize * CouiLargeTypographyScale else style.fontSize,
        lineHeight = if (style.lineHeight.isSpecified) {
            style.lineHeight * CouiLargeTypographyScale
        } else {
            style.lineHeight
        },
    )
}

/** 只给字号时的逐项放大：侧栏与底栏标签这类自带行距的短文本用它。 */
@Composable
internal fun couiScaledFontSize(base: TextUnit): TextUnit =
    if (couiLargeTypography()) base * CouiLargeTypographyScale else base

/** 与大屏字号配套的图标尺寸：字变大了图标不跟着变会显得失衡。 */
@Composable
internal fun couiScaledSize(base: Dp): Dp =
    if (couiLargeTypography()) base * CouiLargeTypographyScale else base

@Composable
internal fun rememberCouiBodyTextStyle(): TextStyle = rememberCouiTextStyle(CouiTextKind.Search)

@Composable
internal fun rememberCouiSectionTitleStyle(): TextStyle =
    rememberCouiTextStyle(CouiTextKind.ItemTitle).copy(
        fontSize = 18.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
    )

@Composable
internal fun rememberCouiStepperTextStyle(): TextStyle =
    rememberCouiTextStyle(CouiTextKind.ItemTitle).copy(
        fontSize = 18.sp,
        lineHeight = 21.sp,
    )

@Composable
internal fun rememberCouiActionTextStyle(): TextStyle =
    rememberCouiTextStyle(CouiTextKind.ItemTitle).copy(
        fontSize = 14.sp,
        lineHeight = 18.sp,
    )

@Composable
internal fun CouiBodyText(
    modifier: Modifier = Modifier,
    text: String,
    color: Color = Color.Unspecified,
    onLineCount: (Int) -> Unit = {},
    textAlign: TextAlign = TextAlign.Start,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    Text(
        modifier = modifier,
        text = text,
        kind = CouiTextKind.Search,
        color = color,
        onLineCount = onLineCount,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = overflow,
    )
}

@Composable
internal fun CouiSectionTitleText(
    modifier: Modifier = Modifier,
    text: String,
    color: Color = Color.Unspecified,
    textAlign: TextAlign = TextAlign.Start,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    CouiCustomStyledText(
        text = text,
        style = rememberCouiSectionTitleStyle(),
        color = color,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = overflow,
        modifier = modifier,
    )
}

@Composable
internal fun CouiBodyText(
    modifier: Modifier = Modifier,
    text: AnnotatedString,
    color: Color = Color.Unspecified,
    textAlign: TextAlign = TextAlign.Start,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    CouiCustomStyledText(
        text = text,
        style = rememberCouiBodyTextStyle(),
        color = color,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = overflow,
        modifier = modifier,
    )
}

@Composable
internal fun CouiCustomStyledText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textAlign: TextAlign = TextAlign.Start,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val resolvedColor = color.takeOrElse {
        LocalCouiContentColor.current.takeOrElse { CouiTheme.colorScheme.onSurface }
    }
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(
            color = resolvedColor,
            textAlign = textAlign,
        ),
        maxLines = maxLines,
        overflow = overflow,
    )
}

@Composable
internal fun CouiCustomStyledText(
    text: AnnotatedString,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textAlign: TextAlign = TextAlign.Start,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val resolvedColor = color.takeOrElse {
        LocalCouiContentColor.current.takeOrElse { CouiTheme.colorScheme.onSurface }
    }
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(
            color = resolvedColor,
            textAlign = textAlign,
        ),
        maxLines = maxLines,
        overflow = overflow,
    )
}
