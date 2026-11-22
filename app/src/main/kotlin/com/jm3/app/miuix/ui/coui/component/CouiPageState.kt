// SPDX-License-Identifier: Apache-2.0

package com.jm3.app.miuix.ui.coui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.layout.Divider
import com.itos.coui.layout.Group
import com.itos.coui.layout.Item
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import kotlin.math.ceil
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * 虚拟化列表要用的「分组标题 + 分组行」。
 *
 * 加载 / 空 / 失败 / 分页脚四件在 CouiAppComponents.kt（[CouiLoadingGroup]、[CouiEmptyGroup]、
 * [CouiErrorGroup]、[CouiLoadMoreFooter]）；这里只补 [Group] 拆不开的那两块：
 * 一个 LazyColumn 的 item 只能装一段，整块 Group 必须拆成标题与逐行卡片下发。
 * 样式一律取 CouiPageDefaults，与 [Group] 的间距、字阶、圆角保持同一套。
 */

/**
 * 分组标题行：与 [Group] 内置标题同一套间距与字号，供 LazyColumn 单独下发（列表行要自己滚动）。
 *
 * [actions] 放在标题右侧，等价于 [Group] 的 headerActions。
 */
@Composable
fun CouiGroupTitle(
    title: String,
    modifier: Modifier = Modifier,
    topSpacing: Dp = CouiPageDefaults.categoryTopSpacing,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = CouiPageDefaults.themeColors()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = topSpacing)
            .padding(horizontal = 32.dp, vertical = 8.dp)
            .heightIn(min = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            kind = CouiTextKind.GroupTitle,
            color = colors.summary,
            modifier = Modifier.weight(1f).semantics { heading() },
            textAlign = TextAlign.Start,
        )
        actions()
    }
}

/**
 * 分组内的一行卡片：只给分组首行圆上角、末行圆下角，相邻行拼起来与 [Group] 的整卡一致。
 *
 * [Group] 把整组内容包在一张带圆角的卡片里，LazyColumn 却必须逐行下发。因此这里按行画卡片：
 * 首行为 false 时不给上圆角，行与行之间由 [showDivider] 画分隔线，末行补下圆角。
 *
 * @param isFirst 分组第一行（圆上角、不画上分隔线）
 * @param isLast 分组最后一行（圆下角）
 * @param showDivider 是否在本行顶部画分隔线，默认与 [isFirst] 相反
 */
@Composable
fun CouiGroupRow(
    isFirst: Boolean,
    isLast: Boolean,
    modifier: Modifier = Modifier,
    showDivider: Boolean = !isFirst,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CouiPageDefaults.themeColors()
    // 行卡形状只在首末行变化时重造：每次重组新建 Shape 会让 clip 层跟着重建
    val shape = remember(isFirst, isLast) {
        CouiRowShape(
            radius = CouiPageDefaults.shape.radius,
            roundTop = isFirst,
            roundBottom = isLast,
            cornerWeight = CouiPageDefaults.shape.cornerWeight,
        )
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = CouiPageDefaults.horizontalMargin)
            .clip(shape)
            .background(colors.card),
    ) {
        if (showDivider) Divider()
        content()
    }
}

/**
 * 分组行卡片的形状：[com.itos.coui.shape.CouiShape] 只有单一半径，无法只圆首行/末行的两个角，
 * 这里按它的半径与角权重复刻同一段 G2 超椭圆，只把不需要的角走直线。
 *
 * 采样密度与 CouiShape 相同（线段相对曲线的误差上限 0.05px），因此与 [Group] 的整卡圆角逐像素贴合。
 */
internal data class CouiRowShape(
    private val radius: Dp,
    private val roundTop: Boolean,
    private val roundBottom: Boolean,
    private val cornerWeight: Float,
) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val r = (radius.value * density.density)
            .coerceIn(0f, size.minDimension.coerceAtLeast(0f) / 2f)
        if (r <= 0f || (!roundTop && !roundBottom)) {
            return Outline.Rectangle(Rect(0f, 0f, size.width, size.height))
        }
        val weight = cornerWeight.toDouble()
        val diagonal = 0.5.pow(1.0 / weight)
        val segments = ceil(sqrt(r * (weight - 1.0) * diagonal / (4.0 * 0.05)))
            .toInt()
            .coerceAtLeast(1)
        val halfCorner = DoubleArray(segments + 1) { index ->
            (1.0 - (diagonal * index / segments).pow(weight)).pow(1.0 / weight)
        }
        val path = Path()
        // 与 CouiShape 相同：从右上角之前的上边起点出发，顺时针绕四角
        path.moveTo(r, 0f)
        for (corner in 0..3) {
            // corner 0 右上、1 右下、2 左下、3 左上；上边两角看 roundTop，下边两角看 roundBottom
            val rounded = if (corner == 0 || corner == 3) roundTop else roundBottom
            if (!rounded) {
                when (corner) {
                    0 -> path.lineTo(size.width, 0f)
                    1 -> path.lineTo(size.width, size.height)
                    2 -> path.lineTo(0f, size.height)
                    else -> path.lineTo(0f, 0f)
                }
                continue
            }
            for (index in 0..segments * 2) {
                val firstHalf = index <= segments
                val halfIndex = if (firstHalf) index else segments * 2 - index
                val a = diagonal * halfIndex / segments
                val b = halfCorner[halfIndex]
                val x = (r * if (firstHalf) a else b).toFloat()
                val y = (r * if (firstHalf) b else a).toFloat()
                when (corner) {
                    0 -> path.lineTo(size.width - r + x, r - y)
                    1 -> path.lineTo(size.width - r + y, size.height - r + x)
                    2 -> path.lineTo(r - x, size.height - r + y)
                    else -> path.lineTo(r - y, r - x)
                }
            }
        }
        path.close()
        return Outline.Generic(path)
    }
}

/**
 * 点击式分页脚：与无限滚动的 [CouiLoadMoreFooter] 互斥，翻页语义与 `pagerTargetPage` 一致。
 *
 * @param page 当前页（从 1 起）
 * @param pageCount 总页数，接口没给时传 null，脚注与「下一页」按 [hasMore] 兜底
 * @param hasMore 接口是否还有下一页
 */
@Composable
fun CouiPagingGroup(
    page: Int,
    pageCount: Int?,
    hasMore: Boolean,
    onJumpPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val totalPages = pageCount?.toString() ?: "?"
    Group(modifier = modifier, footer = "第 $page / $totalPages 页") {
        Item(
            title = { CouiBodyText(text = "上一页", textAlign = TextAlign.Start) },
            enabled = page > 1,
            onClick = { onJumpPage(page - 1) },
        )
        Divider()
        Item(
            title = { CouiBodyText(text = "下一页", textAlign = TextAlign.Start) },
            enabled = pageCount?.let { page < it } ?: hasMore,
            onClick = { onJumpPage(page + 1) },
        )
    }
}

