@file:OptIn(top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi::class)

package io.github.miuzarte.fhradio.pages

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.miuzarte.fhradio.*
import io.github.miuzarte.fhradio.constants.UiSpacing
import io.github.miuzarte.fhradio.model.RadioSource
import io.github.miuzarte.fhradio.model.RadioStation
import io.github.miuzarte.fhradio.model.Sample
import io.github.miuzarte.fhradio.model.SampleType
import io.github.miuzarte.fhradio.scaffolds.*
import io.github.miuzarte.fhradio.ui.contextClick
import io.github.miuzarte.fhradio.util.format
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Import
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import kotlin.time.Duration

@Composable
fun RadiosScreen(
    scrollBehavior: ScrollBehavior,
    bottomInnerPadding: Dp,
) {
    val haptic = LocalHapticFeedback.current

    val station by rememberUpdatedState(Radio.selectedStation)
    val scope = rememberCoroutineScope()
    var parseError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(parseError) {
        parseError?.let { AppRuntime.snackbar(it) }
    }

    // 电台源管理
    var showManageSheet by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var sourceToDelete by remember { mutableStateOf<String?>(null) }

    // 电台编辑
    var showStationOrderSheet by remember { mutableStateOf(false) }
    var editingSource by remember { mutableStateOf<RadioSource?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "FHRadio",
                subtitle = station?.name ?: "未选中电台",
                color = colorScheme.surface,
                actions = {
                    OverlayIconDropdownMenu(
                        entry = DropdownEntry(
                            items = listOf(
                                DropdownItem(
                                    text = "导入电台源",
                                    icon = { modifier -> Icon(MiuixIcons.Import, null, modifier = modifier) },
                                    onClick = {
                                        scope.launch {
                                            parseError = null
                                            try {
                                                when (val result = importRadio()) {
                                                    is ImportResult.Cancelled -> parseError = "已取消导入"
                                                    is ImportResult.Success -> {
                                                        val source = RadioSource(
                                                            name = "新电台源",
                                                            xmlFilePath = result.xmlPath,
                                                            audioFolderPath = result.audioPath,
                                                            stationOrder = result.config.stations.map { it.number },
                                                        )
                                                        AppSettings.addRadioSource(source, result.config.stations)
                                                        editingSource = source
                                                        showStationOrderSheet = true
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                parseError = "解析失败: ${e.message}"
                                            }
                                        }
                                    },
                                ),
                                DropdownItem(
                                    text = "电台源管理",
                                    icon = { modifier -> Icon(Icons.Rounded.Settings, null, modifier = modifier) },
                                    onClick = { showManageSheet = true },
                                ),
                            ),
                        ),
                    ) {
                        Icon(
                            imageVector = MiuixIcons.More,
                            contentDescription = "更多",
                            tint = colorScheme.onSurface,
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { contentPadding ->
        val listState = rememberLazyListState()
        val sourceGrids = remember(
            AppSettings.radioSourcesXml,
            AppSettings.radioSources,
        ) {
            AppSettings.radioSourcesXml.map { source ->
                val stations = AppSettings.radioSources[source.xmlFilePath] ?: emptyList()
                val ordered = source.stationOrder
                    .mapNotNull { num -> stations.find { it.number == num } }
                    .filterNot { it.name in source.hiddenStationNames }
                Triple(source, FlowGridState<RadioStation>(), ordered)
            }
        }
        Box(modifier = Modifier.fillMaxSize()) {
            sourceGrids.forEach { (_, gridState, ordered) ->
                BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(0.dp)) {
                    val maxColumns =
                        ((maxWidth - UiSpacing.PageHorizontal * 2 + UiSpacing.PageItem) / (480.dp + UiSpacing.PageItem))
                            .toInt()
                            .coerceAtLeast(1)
                    val cols =
                        ((maxWidth - UiSpacing.PageHorizontal * 2 + UiSpacing.PageItem) / (640.dp + UiSpacing.PageItem))
                            .toInt()
                            .coerceAtLeast(1)
                            .let { maxColumns.coerceAtLeast(it) }
                    gridState.columns = cols
                    gridState.rows = ordered.withIndex().chunked(cols)
                }
            }
            LazyColumn(
                contentPadding = contentPadding,
                scrollBehavior = scrollBehavior,
                state = listState,
                bottomInnerPadding = bottomInnerPadding,
                limitLandscapeWidth = false,
            ) {
                sourceGrids.forEach { (source, gridState, ordered) ->
                    item { SectionSmallTitle(source.name) }
                    flowGrid(gridState) { _, station ->
                        val isSelected = Radio.selectedStation == station
                        StationCard(
                            station = station,
                            selected = isSelected,
                            onClick = {
                                haptic.contextClick()
                                Radio.setStation(
                                    station =
                                        if (isSelected) null
                                        else station,
                                )
                            },
                        )
                    }
                }
            }

            VerticalScrollBar(
                adapter = rememberScrollBarAdapter(listState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                trackPadding = contentPadding,
            )
        }
    }

    OverlayBottomSheet(
        show = showManageSheet,
        title = "电台源管理",
        onDismissRequest = { showManageSheet = false },
        defaultWindowInsetsPadding = false,
    ) {
        ReorderableList(
            itemsProvider = {
                AppSettings.radioSourcesXml.map { source ->
                    ReorderableList.Item(
                        id = source.xmlFilePath,
                        title = source.name,
                        subtitle = "${AppSettings.radioSources[source.xmlFilePath]?.size ?: 0} 电台",
                        onClick = {
                            editingSource = source
                            showStationOrderSheet = true
                        },
                        endAction = {
                            IconButton(
                                onClick = {
                                    sourceToDelete = source.xmlFilePath
                                    showDeleteDialog = true
                                },
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.Delete,
                                    contentDescription = "删除",
                                    tint = colorScheme.error,
                                )
                            }
                        },
                    )
                }
            },
            onSettle = { from, to ->
                AppSettings.radioSourcesXml = AppSettings.radioSourcesXml.toMutableList()
                    .apply {
                        add(to, removeAt(from))
                    }
            },
        ).invoke()
        Spacer(Modifier.height(UiSpacing.SheetBottom))
    }

    sourceToDelete?.let { xmlFilePath ->
        val sourceName = AppSettings.radioSourcesXml.find { it.xmlFilePath == xmlFilePath }?.name ?: xmlFilePath
        OverlayDialog(
            show = showDeleteDialog,
            title = "确认删除",
            summary = "确定要删除「${sourceName}」吗？",
            defaultWindowInsetsPadding = false,
            onDismissRequest = { showDeleteDialog = false },
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(UiSpacing.ContentHorizontal),
            ) {
                TextButton(
                    text = "取消",
                    onClick = { showDeleteDialog = false },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = "删除",
                    onClick = {
                        AppSettings.removeRadioSource(xmlFilePath)
                        showDeleteDialog = false
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }

    if (editingSource != null && editingSource!!.stationOrder.isNotEmpty()) {
        OverlayBottomSheet(
            show = showStationOrderSheet,
            title = "电台编辑",
            onDismissRequest = { showStationOrderSheet = false },
            defaultWindowInsetsPadding = false,
        ) {
            val source = editingSource ?: return@OverlayBottomSheet
            val xmlPath = source.xmlFilePath
            val stations = AppSettings.radioSources[xmlPath] ?: return@OverlayBottomSheet
            Column {
                SuperTextField(
                    value = source.name,
                    onValueChange = { editingSource = editingSource?.copy(name = it) },
                    label = "源名称",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    onFocusLost = {
                        editingSource?.let { AppSettings.updateRadioSource(it) }
                    },
                )
                Spacer(Modifier.height(12.dp))
                ReorderableList(
                    itemsProvider = {
                        source.stationOrder.mapNotNull { num ->
                            stations.find { it.number == num }
                        }.map { station ->
                            val playableTracks = station.playableTracks(AppSettings.excludedTrackSuffixes)
                            ReorderableList.Item(
                                id = station.number.toString(),
                                title = station.name,
                                subtitle = "${playableTracks.size} 曲目 | ${station.stingers.size} Stinger | ${station.djSamples.size} DJ",
                            ) {
                                val isHidden = station.name in source.hiddenStationNames
                                IconButton(
                                    onClick = {
                                        haptic.contextClick()
                                        editingSource = editingSource?.let {
                                            it.copy(
                                                hiddenStationNames =
                                                    if (isHidden) it.hiddenStationNames - station.name
                                                    else it.hiddenStationNames + station.name,
                                            )
                                        }
                                        editingSource?.let { AppSettings.updateRadioSource(it) }
                                    },
                                ) {
                                    Icon(
                                        imageVector =
                                            if (isHidden) Icons.Rounded.VisibilityOff
                                            else Icons.Rounded.Visibility,
                                        contentDescription =
                                            if (isHidden) "显示"
                                            else "隐藏",
                                    )
                                }
                            }
                        }
                    },
                    onSettle = { from, to ->
                        editingSource = editingSource?.let {
                            it.copy(
                                stationOrder = it.stationOrder.toMutableList()
                                    .also { list ->
                                        list.add(to, list.removeAt(from))
                                    },
                            )
                        }
                        editingSource?.let { AppSettings.updateRadioSource(it) }
                    },
                ).invoke()
            }
            Spacer(Modifier.height(UiSpacing.SheetBottom))
        }
    }
}

@Composable
private fun StationCard(station: RadioStation, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .clip(RoundedCornerShape(CardDefaults.CornerRadius))
            .clickable(onClick = onClick),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxWidth().padding(UiSpacing.Large)) {
                val playableTracks = station.playableTracks(AppSettings.excludedTrackSuffixes)
                Text(
                    station.name,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) colorScheme.primary else colorScheme.onSurface,
                )
                InfoLine(
                    SampleType.Track.toString(),
                    "${playableTracks.size} | ${playableTracks.totalDuration()}",
                )
                InfoLine(
                    SampleType.Stinger.toString(),
                    "${station.stingers.size} | ${station.stingers.totalDuration()}",
                )
                InfoLine(
                    SampleType.DJ.toString(),
                    "${station.djSamples.size} | ${station.djSamples.totalDuration()}",
                )
            }
            ActiveIcon(selected)
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Text(text = "$label: $value", fontSize = 12.sp, color = colorScheme.onSurface.copy(alpha = 0.6f))
}

inline fun <T> Iterable<T>.sumOfDuration(selector: (T) -> Duration): Duration {
    var sum = Duration.ZERO
    for (element in this) {
        sum += selector(element)
    }
    return sum
}

private fun List<Sample>.totalDuration() =
    sumOfDuration { it.duration }.format()

@Composable
private fun BoxScope.ActiveIcon(visible: Boolean) {
    Box(modifier = Modifier.matchParentSize().offset(18.dp, 26.dp), contentAlignment = Alignment.BottomEnd) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + slideIn { IntOffset(it.width, it.height) },
            exit = fadeOut() + slideOut { IntOffset(it.width, it.height) },
        ) {
            Icon(
                imageVector = Icons.Rounded.CheckCircleOutline,
                contentDescription = "已选中",
                modifier = Modifier.size(96.dp),
                tint = colorScheme.primary.copy(alpha = 0.25f),
            )
        }
    }
}
