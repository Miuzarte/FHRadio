package io.github.miuzarte.fhradio.scaffolds

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

class TableScope internal constructor() {
    internal val items = mutableListOf<@Composable () -> Unit>()

    @Composable
    fun item(content: @Composable () -> Unit) {
        items.add(content)
    }
}

// columns: 逐行填充，填满一行再下一行
// rows:    逐列填充，填满一列再下一列
// 两者互斥，必须指定其一
@Composable
fun Table(
    modifier: Modifier = Modifier,
    columns: Int = 0,
    rows: Int = 0,
    spacing: Dp = 16.dp,
    content: @Composable TableScope.() -> Unit,
) {
    require((columns > 0) xor (rows > 0)) { "必须指定 columns 或 rows 其一" }

    val scope = remember { TableScope() }
    scope.items.clear()
    scope.content()

    val count = scope.items.size
    if (count == 0) return

    val colCount = if (columns > 0) columns else (count + rows - 1) / rows
    val rowCount = if (rows > 0) rows else (count + columns - 1) / columns
    val spacingPx = with(LocalDensity.current) { spacing.roundToPx() }
    val itemList = scope.items.toList()

    Layout(
        modifier = modifier,
        content = {
            for (item in itemList) {
                item()
            }
        },
    ) { measurables, constraints ->
        val containerWidth = constraints.maxWidth
        val spacingTotal = spacingPx * (colCount - 1)

        val boundedConstraints = if (containerWidth < Constraints.Infinity) {
            val perColumn = maxOf(1, (containerWidth - spacingTotal) / colCount)
            constraints.copy(maxWidth = perColumn)
        } else {
            constraints
        }

        val round1 = measurables.map { it.measure(boundedConstraints) }

        val colWidths = IntArray(colCount) { 0 }
        for (i in round1.indices) {
            val col = if (columns > 0) i % colCount else i / rows
            colWidths[col] = maxOf(colWidths[col], round1[i].width)
        }

        var placeables: List<Placeable> = round1
        var totalWidth = colWidths.sum() + spacingTotal

        if (containerWidth < Constraints.Infinity && totalWidth > containerWidth) {
            val contentWidth = containerWidth - spacingTotal
            val naturalWidthSum = colWidths.sum()
            if (naturalWidthSum > 0) {
                val scale = contentWidth.toFloat() / naturalWidthSum
                for (c in 0 until colCount) {
                    colWidths[c] = (colWidths[c] * scale).toInt()
                }
            }
            totalWidth = containerWidth

            placeables = measurables.mapIndexed { i, m ->
                val col = if (columns > 0) i % colCount else i / rows
                m.measure(constraints.copy(maxWidth = colWidths[col]))
            }
        }

        if (columns > 0) {
            val rowHeights = IntArray(rowCount) { 0 }
            for (r in 0 until rowCount) {
                for (c in 0 until colCount) {
                    val idx = r * colCount + c
                    if (idx < count) {
                        rowHeights[r] = maxOf(rowHeights[r], placeables[idx].height)
                    }
                }
            }
            val totalHeight = rowHeights.sum()

            layout(totalWidth, totalHeight) {
                var y = 0
                for (r in 0 until rowCount) {
                    var x = 0
                    for (c in 0 until colCount) {
                        val idx = r * colCount + c
                        if (idx < count) {
                            placeables[idx].place(x, y)
                        }
                        x += colWidths[c] + spacingPx
                    }
                    y += rowHeights[r]
                }
            }
        } else {
            val rowHeights = IntArray(rowCount) { 0 }
            for (c in 0 until colCount) {
                for (r in 0 until rowCount) {
                    val idx = c * rowCount + r
                    if (idx < count) {
                        rowHeights[r] = maxOf(rowHeights[r], placeables[idx].height)
                    }
                }
            }
            val totalHeight = rowHeights.maxOrNull() ?: 0

            layout(totalWidth, totalHeight) {
                var x = 0
                for (c in 0 until colCount) {
                    var y = 0
                    for (r in 0 until rowCount) {
                        val idx = c * rowCount + r
                        if (idx < count) {
                            placeables[idx].place(x, y)
                        }
                        y += rowHeights[r]
                    }
                    x += colWidths[c] + spacingPx
                }
            }
        }
    }
}
