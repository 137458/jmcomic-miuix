package com.jm3.app.miuix.ui.coui.component

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.text.rememberCouiTextStyle
import com.itos.coui.theme.CouiTheme
import com.itos.coui.theme.LocalCouiContentColor

/**
 * coui 0.0.1-alpha10 将旧版 Body/SectionTitle/Stepper/SearchCancel 收敛进更精简的
 * [CouiTextKind] 枚举；这里基于现有预设派生出对应字阶，保持全端排版与 sp 缩放一致。
 */
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
