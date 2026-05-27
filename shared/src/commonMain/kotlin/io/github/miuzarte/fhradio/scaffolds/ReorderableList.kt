package io.github.miuzarte.fhradio.scaffolds

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.miuzarte.fhradio.constants.UiSpacing
import io.github.miuzarte.fhradio.ui.confirm
import io.github.miuzarte.fhradio.ui.contextClick
import io.github.miuzarte.fhradio.ui.longPress
import io.github.miuzarte.fhradio.ui.segmentTick
import sh.calvin.reorderable.ReorderableColumn
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

class ReorderableList(
    private val itemsProvider: () -> List<Item>,
    private val orientation: Orientation = Orientation.Column,
    private val onSettle: (fromIndex: Int, toIndex: Int) -> Unit = { _, _ -> },
    private val modifier: Modifier = Modifier,
) {
    enum class Orientation { Column, Row; }

    data class Item(
        val id: String,
        val icon: ImageVector? = null,
        val title: String,
        val subtitle: String? = null,
        val onClick: (() -> Unit)? = null,
        val dragEnabled: Boolean = true,
        val endAction: (@Composable RowScope.() -> Unit)? = null,
    )

    @Composable
    operator fun invoke() {
        val haptic = LocalHapticFeedback.current
        val items = itemsProvider()
        when (orientation) {
            Orientation.Column -> {
                ReorderableColumn(
                    list = items,
                    modifier = modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(UiSpacing.Small),
                    onSettle = onSettle,
                    onMove = haptic::segmentTick,
                ) { _, item, _ ->
                    key(item.id) {
                        ReorderableItem {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(IntrinsicSize.Min)
                                        .padding(
                                            horizontal = UiSpacing.CardTitle,
                                            vertical = UiSpacing.FieldLabelBottom,
                                        ),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .then(
                                                if (item.onClick != null)
                                                    Modifier.clickable(onClick = item.onClick)
                                                else Modifier,
                                            ),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(UiSpacing.Small),
                                    ) {
                                        item.icon?.let {
                                            Icon(it, contentDescription = item.title)
                                        }
                                        Column {
                                            Text(
                                                text = item.title,
                                                color = colorScheme.onSurface,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                            item.subtitle?.let {
                                                Text(
                                                    text = it,
                                                    color = colorScheme.onSurfaceVariantSummary,
                                                    fontSize = 13.sp,
                                                )
                                            }
                                        }
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(UiSpacing.Small),
                                    ) {
                                        item.endAction?.invoke(this)
                                        if (item.dragEnabled) IconButton(
                                            onClick = haptic::contextClick,
                                            modifier = Modifier
                                                .draggableHandle(
                                                    onDragStarted = { haptic.longPress() },
                                                    onDragStopped = { haptic.confirm() },
                                                ),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.DragIndicator,
                                                contentDescription = "拖动排序",
                                                tint = colorScheme.onSurfaceVariantSummary,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Orientation.Row -> {}
        }
    }
}
