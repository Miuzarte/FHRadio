package io.github.miuzarte.fhradio.scaffolds

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import io.github.miuzarte.fhradio.constants.UiSpacing

class FlowGridState<T> {
    var columns by mutableStateOf(1)
    var rows by mutableStateOf(emptyList<List<IndexedValue<T>>>())
    fun rowFor(index: Int): Int = if (columns <= 0) 0 else index / columns
    fun itemIndexForRow(row: Int): Int = row
}

fun <T> LazyListScope.flowGrid(
    state: FlowGridState<T>,
    spacing: Dp = UiSpacing.PageItem,
    itemContent: @Composable (index: Int, item: T) -> Unit,
) {
    items(count = state.rows.size, key = { "fg_$it" }) { rowIndex ->
        val rowEntries = state.rows[rowIndex]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(spacing),
        ) {
            rowEntries.forEach { (index, item) ->
                key(index) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    ) {
                        itemContent(index, item)
                    }
                }
            }
            repeat(state.columns - rowEntries.size) {
                Spacer(Modifier.weight(1f))
            }
        }
    }
}
