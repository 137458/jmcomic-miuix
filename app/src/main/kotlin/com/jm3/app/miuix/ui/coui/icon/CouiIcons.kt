// SPDX-License-Identifier: ISC

/*
 * CouiIcons —— Compose 线性图标集合（stroke-only，24dp / 24x24 viewport）。
 *
 * Icons derived from Lucide (ISC License) — https://lucide.dev
 * 来源版本：lucide-static v1.70.0（https://www.npmjs.com/package/lucide-static）
 *
 * 本文件由脚本生成，请勿手工编辑：生成器解析 Lucide SVG 的 path / circle / rect 元素，
 * 将每个 SVG 路径命令（M/L/H/V/C/S/Q/T/A/Z，含相对命令换算为绝对坐标）逐一转换为
 * PathBuilder 调用，不包含任何手写近似形状。重新生成优于直接修改。
 *
 * 属性名 -> Lucide slug（共 35 个图标）：
 *   Home          -> house
 *   Category      -> layout-grid
 *   Rank          -> trophy
 *   Mine          -> user-round
 *   Back          -> chevron-left
 *   Forward       -> chevron-right
 *   Close         -> x
 *   Download      -> download
 *   Star          -> star
 *   History       -> history
 *   Shuffle       -> shuffle
 *   Refresh       -> refresh-cw
 *   Settings      -> settings
 *   Share         -> share
 *   Comment       -> message-circle
 *   Check         -> check
 *   Folder        -> folder
 *   Book          -> book-open
 *   Clock         -> clock
 *   Info          -> info
 *   LogOut        -> log-out
 *   Plus          -> plus
 *   Minus         -> minus
 *   Sort          -> arrow-up-down
 *   List          -> list
 *   Lock          -> lock
 *   Bell          -> bell
 *   Search        -> search
 *   Heart         -> heart
 *   ChevronsUpDown -> chevrons-up-down
 *   ChevronDown   -> chevron-down
 *   ChevronUp     -> chevron-up
 *   Calendar      -> calendar
 *   CalendarCheck -> calendar-check
 *   Copy          -> copy
 *
 * 缺失/替代说明：请求的 35 个 slug 全部解析成功，无替代项。
 * 其中 history 已不在 Lucide 仓库 main 分支的 icons 目录直接提供，
 * 因此取自仍发布该文件的 lucide-static v1.70.0 包，图形与 Lucide 一致。
 *
 * 全部图标为描边（stroke 2f，round cap/join，无 fill）；本文件不生成 filled 变体。
 */
package com.jm3.app.miuix.ui.coui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object CouiIcons {
    /** Lucide slug: `house`. */
    val Home: ImageVector = ImageVector.Builder(
        name = "CouiIcons.Home",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(15f, 21f)
            verticalLineTo(13f)
            arcTo(1f, 1f, 0f, false, false, 14f, 12f)
            horizontalLineTo(10f)
            arcTo(1f, 1f, 0f, false, false, 9f, 13f)
            verticalLineTo(21f)
            moveTo(3f, 10f)
            arcTo(2f, 2f, 0f, false, true, 3.709f, 8.472f)
            lineTo(10.709f, 2.472f)
            arcTo(2f, 2f, 0f, false, true, 13.291f, 2.472f)
            lineTo(20.291f, 8.472f)
            arcTo(2f, 2f, 0f, false, true, 21f, 10f)
            verticalLineTo(19f)
            arcTo(2f, 2f, 0f, false, true, 19f, 21f)
            horizontalLineTo(5f)
            arcTo(2f, 2f, 0f, false, true, 3f, 19f)
            close()
        }
    }.build()

    /** Lucide slug: `layout-grid`. */
    val Category: ImageVector = ImageVector.Builder(
        name = "CouiIcons.Category",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(4f, 3f)
            horizontalLineTo(9f)
            arcTo(1f, 1f, 0f, false, true, 10f, 4f)
            verticalLineTo(9f)
            arcTo(1f, 1f, 0f, false, true, 9f, 10f)
            horizontalLineTo(4f)
            arcTo(1f, 1f, 0f, false, true, 3f, 9f)
            verticalLineTo(4f)
            arcTo(1f, 1f, 0f, false, true, 4f, 3f)
            close()
            moveTo(15f, 3f)
            horizontalLineTo(20f)
            arcTo(1f, 1f, 0f, false, true, 21f, 4f)
            verticalLineTo(9f)
            arcTo(1f, 1f, 0f, false, true, 20f, 10f)
            horizontalLineTo(15f)
            arcTo(1f, 1f, 0f, false, true, 14f, 9f)
            verticalLineTo(4f)
            arcTo(1f, 1f, 0f, false, true, 15f, 3f)
            close()
            moveTo(15f, 14f)
            horizontalLineTo(20f)
            arcTo(1f, 1f, 0f, false, true, 21f, 15f)
            verticalLineTo(20f)
            arcTo(1f, 1f, 0f, false, true, 20f, 21f)
            horizontalLineTo(15f)
            arcTo(1f, 1f, 0f, false, true, 14f, 20f)
            verticalLineTo(15f)
            arcTo(1f, 1f, 0f, false, true, 15f, 14f)
            close()
            moveTo(4f, 14f)
            horizontalLineTo(9f)
            arcTo(1f, 1f, 0f, false, true, 10f, 15f)
            verticalLineTo(20f)
            arcTo(1f, 1f, 0f, false, true, 9f, 21f)
            horizontalLineTo(4f)
            arcTo(1f, 1f, 0f, false, true, 3f, 20f)
            verticalLineTo(15f)
            arcTo(1f, 1f, 0f, false, true, 4f, 14f)
            close()
        }
    }.build()

    /** Lucide slug: `trophy`. */
    val Rank: ImageVector = ImageVector.Builder(
        name = "CouiIcons.Rank",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(10f, 14.66f)
            verticalLineTo(17f)
            arcTo(1f, 1f, 0f, false, true, 9f, 18f)
            arcTo(2f, 2f, 0f, false, false, 7f, 20f)
            verticalLineTo(22f)
            moveTo(14f, 14.66f)
            verticalLineTo(17f)
            arcTo(1f, 1f, 0f, false, false, 15f, 18f)
            arcTo(2f, 2f, 0f, false, true, 17f, 20f)
            verticalLineTo(22f)
            moveTo(17.916f, 10f)
            horizontalLineTo(19.5f)
            arcTo(2.5f, 2.5f, 0f, false, false, 22f, 7.5f)
            verticalLineTo(5f)
            arcTo(1f, 1f, 0f, false, false, 21f, 4f)
            horizontalLineTo(18f)
            moveTo(4f, 22f)
            horizontalLineTo(20f)
            moveTo(6f, 9f)
            arcTo(6f, 6f, 0f, false, false, 18f, 9f)
            verticalLineTo(3f)
            arcTo(1f, 1f, 0f, false, false, 17f, 2f)
            horizontalLineTo(7f)
            arcTo(1f, 1f, 0f, false, false, 6f, 3f)
            close()
            moveTo(6.084f, 10f)
            horizontalLineTo(4.5f)
            arcTo(2.5f, 2.5f, 0f, false, true, 2f, 7.5f)
            verticalLineTo(5f)
            arcTo(1f, 1f, 0f, false, true, 3f, 4f)
            horizontalLineTo(6f)
        }
    }.build()

    /** Lucide slug: `user-round`. */
    val Mine: ImageVector = ImageVector.Builder(
        name = "CouiIcons.Mine",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(7f, 8f)
            arcTo(5f, 5f, 0f, true, false, 17f, 8f)
            arcTo(5f, 5f, 0f, true, false, 7f, 8f)
            close()
            moveTo(20f, 21f)
            arcTo(8f, 8f, 0f, false, false, 4f, 21f)
        }
    }.build()

    /** Lucide slug: `chevron-left`. */
    val Back: ImageVector = ImageVector.Builder(
        name = "CouiIcons.Back",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(15f, 18f)
            lineTo(9f, 12f)
            lineTo(15f, 6f)
        }
    }.build()

    /** Lucide slug: `chevron-right`. */
    val Forward: ImageVector = ImageVector.Builder(
        name = "CouiIcons.Forward",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(9f, 18f)
            lineTo(15f, 12f)
            lineTo(9f, 6f)
        }
    }.build()

    /** Lucide slug: `x`. */
    val Close: ImageVector = ImageVector.Builder(
        name = "CouiIcons.Close",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(18f, 6f)
            lineTo(6f, 18f)
            moveTo(6f, 6f)
            lineTo(18f, 18f)
        }
    }.build()

    /** Lucide slug: `download`. */
    val Download: ImageVector = ImageVector.Builder(
        name = "CouiIcons.Download",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(12f, 15f)
            verticalLineTo(3f)
            moveTo(21f, 15f)
            verticalLineTo(19f)
            arcTo(2f, 2f, 0f, false, true, 19f, 21f)
            horizontalLineTo(5f)
            arcTo(2f, 2f, 0f, false, true, 3f, 19f)
            verticalLineTo(15f)
            moveTo(7f, 10f)
            lineTo(12f, 15f)
            lineTo(17f, 10f)
        }
    }.build()

    /** Lucide slug: `star`. */
    val Star: ImageVector = ImageVector.Builder(
        name = "CouiIcons.Star",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(11.525f, 2.295f)
            arcTo(0.53f, 0.53f, 0f, false, true, 12.475f, 2.295f)
            lineTo(14.785f, 6.974f)
            arcTo(2.123f, 2.123f, 0f, false, false, 16.38f, 8.134f)
            lineTo(21.546f, 8.89f)
            arcTo(0.53f, 0.53f, 0f, false, true, 21.84f, 9.794f)
            lineTo(18.104f, 13.432f)
            arcTo(2.123f, 2.123f, 0f, false, false, 17.493f, 15.31f)
            lineTo(18.375f, 20.45f)
            arcTo(0.53f, 0.53f, 0f, false, true, 17.604f, 21.01f)
            lineTo(12.986f, 18.582f)
            arcTo(2.122f, 2.122f, 0f, false, false, 11.013f, 18.582f)
            lineTo(6.396f, 21.01f)
            arcTo(0.53f, 0.53f, 0f, false, true, 5.626f, 20.45f)
            lineTo(6.507f, 15.311f)
            arcTo(2.122f, 2.122f, 0f, false, false, 5.896f, 13.432f)
            lineTo(2.16f, 9.795f)
            arcTo(0.53f, 0.53f, 0f, false, true, 2.454f, 8.889f)
            lineTo(7.619f, 8.134f)
            arcTo(2.122f, 2.122f, 0f, false, false, 9.216f, 6.974f)
            close()
        }
    }.build()

    /** Lucide slug: `history`. */
    val History: ImageVector = ImageVector.Builder(
        name = "CouiIcons.History",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(3f, 12f)
            arcTo(9f, 9f, 0f, true, false, 12f, 3f)
            arcTo(9.75f, 9.75f, 0f, false, false, 5.26f, 5.74f)
            lineTo(3f, 8f)
            moveTo(3f, 3f)
            verticalLineTo(8f)
            horizontalLineTo(8f)
            moveTo(12f, 7f)
            verticalLineTo(12f)
            lineTo(16f, 14f)
        }
    }.build()

    /** Lucide slug: `shuffle`. */
    val Shuffle: ImageVector = ImageVector.Builder(
        name = "CouiIcons.Shuffle",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(18f, 14f)
            lineTo(22f, 18f)
            lineTo(18f, 22f)
            moveTo(18f, 2f)
            lineTo(22f, 6f)
            lineTo(18f, 10f)
            moveTo(2f, 18f)
            horizontalLineTo(3.973f)
            arcTo(4f, 4f, 0f, false, false, 7.273f, 16.3f)
            lineTo(12.727f, 7.7f)
            arcTo(4f, 4f, 0f, false, true, 16.027f, 6f)
            horizontalLineTo(22f)
            moveTo(2f, 6f)
            horizontalLineTo(3.972f)
            arcTo(4f, 4f, 0f, false, true, 7.572f, 8.2f)
            moveTo(22f, 18f)
            horizontalLineTo(15.959f)
            arcTo(4f, 4f, 0f, false, true, 12.659f, 16.2f)
            lineTo(12.3f, 15.75f)
        }
    }.build()

    /** Lucide slug: `refresh-cw`. */
    val Refresh: ImageVector = ImageVector.Builder(
        name = "CouiIcons.Refresh",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(3f, 12f)
            arcTo(9f, 9f, 0f, false, true, 12f, 3f)
            arcTo(9.75f, 9.75f, 0f, false, true, 18.74f, 5.74f)
            lineTo(21f, 8f)
            moveTo(21f, 3f)
            verticalLineTo(8f)
            horizontalLineTo(16f)
            moveTo(21f, 12f)
            arcTo(9f, 9f, 0f, false, true, 12f, 21f)
            arcTo(9.75f, 9.75f, 0f, false, true, 5.26f, 18.26f)
            lineTo(3f, 16f)
            moveTo(8f, 16f)
            horizontalLineTo(3f)
            verticalLineTo(21f)
        }
    }.build()

    /** Lucide slug: `settings`. */
    val Settings: ImageVector = ImageVector.Builder(
        name = "CouiIcons.Settings",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(9.671f, 4.136f)
            arcTo(2.34f, 2.34f, 0f, false, true, 14.33f, 4.136f)
            arcTo(2.34f, 2.34f, 0f, false, false, 17.649f, 6.051f)
            arcTo(2.34f, 2.34f, 0f, false, true, 19.979f, 10.084f)
            arcTo(2.34f, 2.34f, 0f, false, false, 19.979f, 13.915f)
            arcTo(2.34f, 2.34f, 0f, false, true, 17.649f, 17.948f)
            arcTo(2.34f, 2.34f, 0f, false, false, 14.33f, 19.863f)
            arcTo(2.34f, 2.34f, 0f, false, true, 9.671f, 19.863f)
            arcTo(2.34f, 2.34f, 0f, false, false, 6.351f, 17.948f)
            arcTo(2.34f, 2.34f, 0f, false, true, 4.021f, 13.915f)
            arcTo(2.34f, 2.34f, 0f, false, false, 4.021f, 10.084f)
            arcTo(2.34f, 2.34f, 0f, false, true, 6.35f, 6.051f)
            arcTo(2.34f, 2.34f, 0f, false, false, 9.669f, 4.136f)
            moveTo(9f, 12f)
            arcTo(3f, 3f, 0f, true, false, 15f, 12f)
            arcTo(3f, 3f, 0f, true, false, 9f, 12f)
            close()
        }
    }.build()

    /** Lucide slug: `share`. */
    val Share: ImageVector = ImageVector.Builder(
        name = "CouiIcons.Share",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(12f, 2f)
            verticalLineTo(15f)
            moveTo(16f, 6f)
            lineTo(12f, 2f)
            lineTo(8f, 6f)
            moveTo(4f, 12f)
            verticalLineTo(20f)
            arcTo(2f, 2f, 0f, false, false, 6f, 22f)
            horizontalLineTo(18f)
            arcTo(2f, 2f, 0f, false, false, 20f, 20f)
            verticalLineTo(12f)
        }
    }.build()

    /** Lucide slug: `message-circle`. */
    val Comment: ImageVector = ImageVector.Builder(
        name = "CouiIcons.Comment",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(2.992f, 16.342f)
            arcTo(2f, 2f, 0f, false, true, 3.086f, 17.509f)
            lineTo(2.021f, 20.799f)
            arcTo(1f, 1f, 0f, false, false, 3.257f, 21.967f)
            lineTo(6.67f, 20.969f)
            arcTo(2f, 2f, 0f, false, true, 7.769f, 21.061f)
            arcTo(10f, 10f, 0f, true, false, 2.992f, 16.342f)
        }
    }.build()

    /** Lucide slug: `check`. */
    val Check: ImageVector = ImageVector.Builder(
        name = "CouiIcons.Check",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(20f, 6f)
            lineTo(9f, 17f)
            lineTo(4f, 12f)
        }
    }.build()

    /** Lucide slug: `folder`. */
    val Folder: ImageVector = ImageVector.Builder(
        name = "CouiIcons.Folder",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(20f, 20f)
            arcTo(2f, 2f, 0f, false, false, 22f, 18f)
            verticalLineTo(8f)
            arcTo(2f, 2f, 0f, false, false, 20f, 6f)
            horizontalLineTo(12.1f)
            arcTo(2f, 2f, 0f, false, true, 10.41f, 5.1f)
            lineTo(9.6f, 3.9f)
            arcTo(2f, 2f, 0f, false, false, 7.93f, 3f)
            horizontalLineTo(4f)
            arcTo(2f, 2f, 0f, false, false, 2f, 5f)
            verticalLineTo(18f)
            arcTo(2f, 2f, 0f, false, false, 4f, 20f)
            close()
        }
    }.build()

    /** Lucide slug: `book-open`. */
    val Book: ImageVector = ImageVector.Builder(
        name = "CouiIcons.Book",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(12f, 5f)
            verticalLineTo(21f)
            moveTo(20.001f, 19f)
            arcTo(2f, 2f, 0f, false, false, 22f, 17f)
            verticalLineTo(5f)
            arcTo(2f, 2f, 0f, false, false, 20.001f, 3f)
            lineTo(16f, 3.002f)
            arcTo(5f, 5f, 0f, false, false, 12f, 5f)
            arcTo(5f, 5f, 0f, false, false, 8f, 3f)
            horizontalLineTo(4f)
            arcTo(2f, 2f, 0f, false, false, 2f, 5f)
            verticalLineTo(17f)
            arcTo(2f, 2f, 0f, false, false, 3.999f, 19f)
            horizontalLineTo(8f)
            arcTo(5f, 5f, 0f, false, true, 12f, 21f)
            arcTo(5f, 5f, 0f, false, true, 16f, 19f)
            close()
        }
    }.build()

    /** Lucide slug: `clock`. */
    val Clock: ImageVector = ImageVector.Builder(
        name = "CouiIcons.Clock",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(2f, 12f)
            arcTo(10f, 10f, 0f, true, false, 22f, 12f)
            arcTo(10f, 10f, 0f, true, false, 2f, 12f)
            close()
            moveTo(12f, 6f)
            verticalLineTo(12f)
            lineTo(16f, 14f)
        }
    }.build()

    /** Lucide slug: `info`. */
    val Info: ImageVector = ImageVector.Builder(
        name = "CouiIcons.Info",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(2f, 12f)
            arcTo(10f, 10f, 0f, true, false, 22f, 12f)
            arcTo(10f, 10f, 0f, true, false, 2f, 12f)
            close()
            moveTo(12f, 16f)
            verticalLineTo(12f)
            moveTo(12f, 8f)
            horizontalLineTo(12.01f)
        }
    }.build()

    /** Lucide slug: `log-out`. */
    val LogOut: ImageVector = ImageVector.Builder(
        name = "CouiIcons.LogOut",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(16f, 17f)
            lineTo(21f, 12f)
            lineTo(16f, 7f)
            moveTo(21f, 12f)
            horizontalLineTo(9f)
            moveTo(9f, 21f)
            horizontalLineTo(5f)
            arcTo(2f, 2f, 0f, false, true, 3f, 19f)
            verticalLineTo(5f)
            arcTo(2f, 2f, 0f, false, true, 5f, 3f)
            horizontalLineTo(9f)
        }
    }.build()

    /** Lucide slug: `plus`. */
    val Plus: ImageVector = ImageVector.Builder(
        name = "CouiIcons.Plus",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(5f, 12f)
            horizontalLineTo(19f)
            moveTo(12f, 5f)
            verticalLineTo(19f)
        }
    }.build()

    /** Lucide slug: `minus`. */
    val Minus: ImageVector = ImageVector.Builder(
        name = "CouiIcons.Minus",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(5f, 12f)
            horizontalLineTo(19f)
        }
    }.build()

    /** Lucide slug: `arrow-up-down`. */
    val Sort: ImageVector = ImageVector.Builder(
        name = "CouiIcons.Sort",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(21f, 16f)
            lineTo(17f, 20f)
            lineTo(13f, 16f)
            moveTo(17f, 20f)
            verticalLineTo(4f)
            moveTo(3f, 8f)
            lineTo(7f, 4f)
            lineTo(11f, 8f)
            moveTo(7f, 4f)
            verticalLineTo(20f)
        }
    }.build()

    /** Lucide slug: `list`. */
    val List: ImageVector = ImageVector.Builder(
        name = "CouiIcons.List",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(3f, 5f)
            horizontalLineTo(3.01f)
            moveTo(3f, 12f)
            horizontalLineTo(3.01f)
            moveTo(3f, 19f)
            horizontalLineTo(3.01f)
            moveTo(8f, 5f)
            horizontalLineTo(21f)
            moveTo(8f, 12f)
            horizontalLineTo(21f)
            moveTo(8f, 19f)
            horizontalLineTo(21f)
        }
    }.build()

    /** Lucide slug: `lock`. */
    val Lock: ImageVector = ImageVector.Builder(
        name = "CouiIcons.Lock",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(5f, 11f)
            horizontalLineTo(19f)
            arcTo(2f, 2f, 0f, false, true, 21f, 13f)
            verticalLineTo(20f)
            arcTo(2f, 2f, 0f, false, true, 19f, 22f)
            horizontalLineTo(5f)
            arcTo(2f, 2f, 0f, false, true, 3f, 20f)
            verticalLineTo(13f)
            arcTo(2f, 2f, 0f, false, true, 5f, 11f)
            close()
            moveTo(7f, 11f)
            verticalLineTo(7f)
            arcTo(5f, 5f, 0f, false, true, 17f, 7f)
            verticalLineTo(11f)
        }
    }.build()

    /** Lucide slug: `bell`. */
    val Bell: ImageVector = ImageVector.Builder(
        name = "CouiIcons.Bell",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(10.268f, 21f)
            arcTo(2f, 2f, 0f, false, false, 13.732f, 21f)
            moveTo(3.262f, 15.326f)
            arcTo(1f, 1f, 0f, false, false, 4f, 17f)
            horizontalLineTo(20f)
            arcTo(1f, 1f, 0f, false, false, 20.74f, 15.327f)
            curveTo(19.41f, 13.956f, 18f, 12.499f, 18f, 8f)
            arcTo(6f, 6f, 0f, false, false, 6f, 8f)
            curveTo(6f, 12.499f, 4.589f, 13.956f, 3.262f, 15.326f)
        }
    }.build()

    /** Lucide slug: `search`. */
    val Search: ImageVector = ImageVector.Builder(
        name = "CouiIcons.Search",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(21f, 21f)
            lineTo(16.66f, 16.66f)
            moveTo(3f, 11f)
            arcTo(8f, 8f, 0f, true, false, 19f, 11f)
            arcTo(8f, 8f, 0f, true, false, 3f, 11f)
            close()
        }
    }.build()

    /** Lucide slug: `heart`. */
    val Heart: ImageVector = ImageVector.Builder(
        name = "CouiIcons.Heart",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(2f, 9.5f)
            arcTo(5.5f, 5.5f, 0f, false, true, 11.591f, 5.824f)
            arcTo(0.56f, 0.56f, 0f, false, false, 12.409f, 5.824f)
            arcTo(5.49f, 5.49f, 0f, false, true, 22f, 9.5f)
            curveTo(22f, 11.79f, 20.5f, 13.5f, 19f, 15f)
            lineTo(13.508f, 20.313f)
            arcTo(2f, 2f, 0f, false, true, 10.508f, 20.332f)
            lineTo(5f, 15f)
            curveTo(3.5f, 13.5f, 2f, 11.8f, 2f, 9.5f)
        }
    }.build()

    /** Lucide slug: `chevrons-up-down`. */
    val ChevronsUpDown: ImageVector = ImageVector.Builder(
        name = "CouiIcons.ChevronsUpDown",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(7f, 15f)
            lineTo(12f, 20f)
            lineTo(17f, 15f)
            moveTo(7f, 9f)
            lineTo(12f, 4f)
            lineTo(17f, 9f)
        }
    }.build()

    /** Lucide slug: `chevron-down`. */
    val ChevronDown: ImageVector = ImageVector.Builder(
        name = "CouiIcons.ChevronDown",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(6f, 9f)
            lineTo(12f, 15f)
            lineTo(18f, 9f)
        }
    }.build()

    /** Lucide slug: `chevron-up`. */
    val ChevronUp: ImageVector = ImageVector.Builder(
        name = "CouiIcons.ChevronUp",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(18f, 15f)
            lineTo(12f, 9f)
            lineTo(6f, 15f)
        }
    }.build()

    /** Lucide slug: `calendar`. */
    val Calendar: ImageVector = ImageVector.Builder(
        name = "CouiIcons.Calendar",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(8f, 2f)
            verticalLineTo(6f)
            moveTo(16f, 2f)
            verticalLineTo(6f)
            moveTo(5f, 4f)
            horizontalLineTo(19f)
            arcTo(2f, 2f, 0f, false, true, 21f, 6f)
            verticalLineTo(20f)
            arcTo(2f, 2f, 0f, false, true, 19f, 22f)
            horizontalLineTo(5f)
            arcTo(2f, 2f, 0f, false, true, 3f, 20f)
            verticalLineTo(6f)
            arcTo(2f, 2f, 0f, false, true, 5f, 4f)
            close()
            moveTo(3f, 10f)
            horizontalLineTo(21f)
        }
    }.build()

    /** Lucide slug: `calendar-check`. */
    val CalendarCheck: ImageVector = ImageVector.Builder(
        name = "CouiIcons.CalendarCheck",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(8f, 2f)
            verticalLineTo(6f)
            moveTo(16f, 2f)
            verticalLineTo(6f)
            moveTo(5f, 4f)
            horizontalLineTo(19f)
            arcTo(2f, 2f, 0f, false, true, 21f, 6f)
            verticalLineTo(20f)
            arcTo(2f, 2f, 0f, false, true, 19f, 22f)
            horizontalLineTo(5f)
            arcTo(2f, 2f, 0f, false, true, 3f, 20f)
            verticalLineTo(6f)
            arcTo(2f, 2f, 0f, false, true, 5f, 4f)
            close()
            moveTo(3f, 10f)
            horizontalLineTo(21f)
            moveTo(9f, 16f)
            lineTo(11f, 18f)
            lineTo(15f, 14f)
        }
    }.build()

    /** Lucide slug: `copy`. */
    val Copy: ImageVector = ImageVector.Builder(
        name = "CouiIcons.Copy",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(10f, 8f)
            horizontalLineTo(20f)
            arcTo(2f, 2f, 0f, false, true, 22f, 10f)
            verticalLineTo(20f)
            arcTo(2f, 2f, 0f, false, true, 20f, 22f)
            horizontalLineTo(10f)
            arcTo(2f, 2f, 0f, false, true, 8f, 20f)
            verticalLineTo(10f)
            arcTo(2f, 2f, 0f, false, true, 10f, 8f)
            close()
            moveTo(4f, 16f)
            curveTo(2.9f, 16f, 2f, 15.1f, 2f, 14f)
            verticalLineTo(4f)
            curveTo(2f, 2.9f, 2.9f, 2f, 4f, 2f)
            horizontalLineTo(14f)
            curveTo(15.1f, 2f, 16f, 2.9f, 16f, 4f)
        }
    }.build()
}