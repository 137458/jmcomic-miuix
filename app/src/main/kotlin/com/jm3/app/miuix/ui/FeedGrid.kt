package com.jm3.app.miuix.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 行卡列表的大屏分列：手机单列，大屏按 [columns] 排成多列。
 *
 * 仍然是一条 LazyColumn 的项，所以标题项、分页脚、触底预取与既有 key 都不受影响；
 * 只有同质的行卡段按列切片，每行一个 [Row]，格子等宽（尾部空位留白，不把最后一张拉宽）。
 * 列数由 [com.jm3.core.layout.WideLayoutPolicy.feedColumns] 判定后经
 * [com.jm3.app.miuix.ui.WideLayout.feedColumns] 传入，页面不自己算断点。
 *
 * @param count 行卡总数
 * @param columns 列数（1 表示与手机一致的单列）
 * @param keyPrefix 与切片前的单列 key 相同的字符串前缀，保证升级后滚动位置不丢
 * @param keyOf 第 index 条的唯一标识，与前缀拼成该行的 key
 * @param rowSpacing 列间距；行卡自带左右外边距时留 0.dp，免得栏间留白翻倍
 * @param content 第 index 条的内容，调用方原样复用既有行卡组件
 */
fun LazyListScope.feedRowItems(
    count: Int,
    columns: Int,
    keyPrefix: String,
    keyOf: (index: Int) -> Any,
    rowSpacing: Dp = 0.dp,
    content: @Composable (index: Int) -> Unit,
) {
    val columnCount = columns.coerceAtLeast(1)
    val rowCount = (count + columnCount - 1) / columnCount
    items(count = rowCount, key = { row -> keyPrefix + keyOf(row * columnCount) }) { row ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (columnCount > 1) {
                Arrangement.spacedBy(rowSpacing)
            } else {
                Arrangement.Start
            },
        ) {
            for (column in 0 until columnCount) {
                Box(modifier = Modifier.weight(1f)) {
                    val index = row * columnCount + column
                    if (index < count) content(index)
                }
            }
        }
    }
}
