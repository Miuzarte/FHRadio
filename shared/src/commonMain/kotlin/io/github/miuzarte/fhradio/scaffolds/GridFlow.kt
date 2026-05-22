package io.github.miuzarte.fhradio.scaffolds

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.miuzarte.fhradio.constants.UiSpacing

/**
 * 自适应网格布局，将列表项按行排列，每项宽度控制在 [itemMinWidth]~[itemMaxWidth] 之间。
 * 窗口过窄减列，过宽加列。末行不足列用 Spacer 补齐。
 *
 * @param items 数据列表
 * @param itemMinWidth 单项最小宽度
 * @param itemMaxWidth 单项最大宽度
 * @param spacing 行列间距
 * @param itemContent 单项渲染，接收 (index, item)
 */
fun <T> LazyListScope.flowGrid(
    items: List<T>,
    itemMinWidth: Dp = 480.dp,
    itemMaxWidth: Dp = 640.dp,
    spacing: Dp = UiSpacing.PageItem,
    itemContent: @Composable (index: Int, item: T) -> Unit,
) {
    if (items.isEmpty()) return

    item {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            // 可用宽度只减去左右 PageHorizontal padding，因为间距由 Arrangement 处理
            val availableWidth = maxWidth - UiSpacing.PageHorizontal * 2
            val maxColumns =
                ((availableWidth + spacing) / (itemMinWidth + spacing))
                    .toInt()
                    .coerceAtLeast(1)
            val columns =
                ((availableWidth + spacing) / (itemMaxWidth + spacing))
                    .toInt()
                    .coerceAtLeast(1)
                    .let { maxColumns.coerceAtLeast(it) }
            val rows = remember(items, columns) {
                items.withIndex().chunked(columns)
            }

            Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
                rows.forEach { rowEntries ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(spacing),
                    ) {
                        rowEntries.forEach { (index, item) ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                            ) {
                                itemContent(index, item)
                            }
                        }
                        repeat(columns - rowEntries.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}
