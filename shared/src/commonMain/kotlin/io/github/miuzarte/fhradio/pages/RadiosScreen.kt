@file:OptIn(top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi::class)

package io.github.miuzarte.fhradio.pages

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOut
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.miuzarte.fhradio.AppRuntime
import io.github.miuzarte.fhradio.AppSettings
import io.github.miuzarte.fhradio.ImportResult
import io.github.miuzarte.fhradio.Radio
import io.github.miuzarte.fhradio.RadioSource
import io.github.miuzarte.fhradio.importRadio
import io.github.miuzarte.fhradio.constants.UiSpacing
import io.github.miuzarte.fhradio.model.RadioStation
import io.github.miuzarte.fhradio.model.Sample
import io.github.miuzarte.fhradio.scaffolds.LazyColumn
import io.github.miuzarte.fhradio.scaffolds.ReorderableList
import io.github.miuzarte.fhradio.scaffolds.SectionSmallTitle
import io.github.miuzarte.fhradio.scaffolds.SuperTextField
import io.github.miuzarte.fhradio.scaffolds.flowGrid
import io.github.miuzarte.fhradio.util.fmt
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Import
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

@Composable
fun RadiosScreen(
    scrollBehavior: ScrollBehavior,
    bottomInnerPadding: Dp,
) {
    val station by rememberUpdatedState(Radio.selectedStation)
    val scope = rememberCoroutineScope()
    var parseError by remember { mutableStateOf<String?>(null) }

    // 导入电台源
    var showSourceSheet by remember { mutableStateOf(false) }
    var pendingName by remember { mutableStateOf("新电台源") }
    var pendingXmlPath by remember { mutableStateOf<String?>(null) }
    var pendingAudioPath by remember { mutableStateOf<String?>(null) }
    var pendingStationOrder by remember { mutableStateOf<List<Int>>(emptyList()) }
    var pendingStations by remember { mutableStateOf<List<RadioStation>>(emptyList()) }

    // 电台源管理
    var showManageSheet by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var sourceToDelete by remember { mutableStateOf<String?>(null) }

    // 电台编辑
    var showStationOrderSheet by remember { mutableStateOf(false) }
    var editingSourceXmlPath by remember { mutableStateOf<String?>(null) }
    var editingStationOrder by remember { mutableStateOf<List<Int>>(emptyList()) }
    var editingSourceName by remember { mutableStateOf("") }

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
                                    text = "导入",
                                    icon = { modifier -> Icon(MiuixIcons.Import, null, modifier = modifier) },
                                    onClick = {
                                        scope.launch {
                                            parseError = null
                                            try {
                                                when (val result = importRadio()) {
                                                    is ImportResult.Cancelled -> parseError = "已取消导入"
                                                    is ImportResult.Success -> {
                                                        pendingName = "新电台源"
                                                        pendingXmlPath = result.xmlPath
                                                        pendingAudioPath = result.audioPath
                                                        pendingStationOrder = result.config.stations.map { it.number }
                                                        pendingStations = result.config.stations
                                                        showSourceSheet = true
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
                            )
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
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                contentPadding = contentPadding,
                scrollBehavior = scrollBehavior,
                state = listState,
                bottomInnerPadding = bottomInnerPadding,
                limitLandscapeWidth = false,
            ) {
                AppSettings.sources.forEach { source ->
                    item { SectionSmallTitle(source.name) }
                    val stations = AppSettings.sourceStations[source.xmlFilePath] ?: emptyList()
                    val ordered = source.stationOrder.mapNotNull { num -> stations.find { it.number == num } }
                    flowGrid(ordered) { _, station ->
                        val isSelected = Radio.selectedStation == station
                        StationCard(
                            station = station,
                            selected = isSelected,
                            onClick = {
                                if (isSelected) {
                                    Radio.closeStation()
                                } else {
                                    Radio.openStation(station)
                                }
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
        show = showSourceSheet,
        title = "新建电台源",
        startAction = {
            IconButton(onClick = { showSourceSheet = false }) {
                Icon(imageVector = MiuixIcons.Close, contentDescription = "取消")
            }
        },
        endAction = {
            IconButton(onClick = {
                val path = pendingXmlPath ?: return@IconButton
                val folder = pendingAudioPath ?: return@IconButton
                val ordered = pendingStationOrder.mapNotNull { num -> pendingStations.find { it.number == num } }
                AppSettings.addSource(
                    RadioSource("新电台源", path, folder, pendingStationOrder),
                    ordered,
                )
                showSourceSheet = false
            }) {
                Icon(imageVector = MiuixIcons.Ok, contentDescription = "完成")
            }
        },
        onDismissRequest = { showSourceSheet = false },
        defaultWindowInsetsPadding = false,
    ) {
        Column(modifier = Modifier.padding(horizontal = UiSpacing.Large)) {
            TextField(
                value = pendingName,
                onValueChange = { pendingName = it },
                label = "源名称",
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            ReorderableList(
                itemsProvider = {
                    pendingStationOrder.mapNotNull { num ->
                        pendingStations.find { it.number == num }
                    }.map { s ->
                        ReorderableList.Item(
                            id = s.number.toString(),
                            title = s.name,
                            subtitle = "${s.tracks.size} 曲目 | ${s.stingers.size} Stinger | ${s.djSamples.size} DJ",
                        )
                    }
                },
                onSettle = { from, to ->
                    pendingStationOrder = pendingStationOrder.toMutableList().apply {
                        add(to, removeAt(from))
                    }
                },
            ).invoke()
            Spacer(Modifier.height(UiSpacing.SheetBottom))
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
                AppSettings.sources.map { source ->
                    ReorderableList.Item(
                        id = source.xmlFilePath,
                        title = source.name,
                        subtitle = "${AppSettings.sourceStations[source.xmlFilePath]?.size ?: 0} 电台",
                        onClick = {
                            editingSourceXmlPath = source.xmlFilePath
                            editingStationOrder = source.stationOrder
                            editingSourceName = source.name
                            showStationOrderSheet = true
                        },
                        endAction = {
                            IconButton(onClick = {
                                sourceToDelete = source.xmlFilePath
                                showDeleteDialog = true
                            }) {
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
                val reordered = AppSettings.sources.toMutableList().apply {
                    add(to, removeAt(from))
                }
                AppSettings.saveSources(reordered)
            },
        ).invoke()
        Spacer(Modifier.height(UiSpacing.SheetBottom))
    }

    sourceToDelete?.let { xmlPath ->
        val sourceName = AppSettings.sources.find { it.xmlFilePath == xmlPath }?.name ?: xmlPath
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
                        AppSettings.removeSource(xmlPath)
                        showDeleteDialog = false
                        showManageSheet = false
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }

    if (editingSourceXmlPath != null && editingStationOrder.isNotEmpty()) {
        OverlayBottomSheet(
            show = showStationOrderSheet,
            title = "电台编辑",
            onDismissRequest = { showStationOrderSheet = false },
            defaultWindowInsetsPadding = false,
        ) {
            val xmlPath = editingSourceXmlPath ?: return@OverlayBottomSheet
            val stations = AppSettings.sourceStations[xmlPath] ?: return@OverlayBottomSheet
            Column(modifier = Modifier.padding(horizontal = UiSpacing.Large)) {
                SuperTextField(
                    value = editingSourceName,
                    onValueChange = { editingSourceName = it },
                    label = "源名称",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    onFocusLost = {
                        val source = AppSettings.sources.find { it.xmlFilePath == xmlPath }
                            ?.copy(name = editingSourceName)
                            ?: return@SuperTextField
                        AppSettings.updateSource(source)
                    },
                )
                Spacer(Modifier.height(12.dp))
                ReorderableList(
                    itemsProvider = {
                        editingStationOrder.mapNotNull { num ->
                            stations.find { it.number == num }
                        }.map { s ->
                            ReorderableList.Item(
                                id = s.number.toString(),
                                title = s.name,
                                subtitle = "${s.tracks.size} 曲目 | ${s.stingers.size} Stinger | ${s.djSamples.size} DJ",
                            )
                        }
                    },
                    onSettle = { from, to ->
                        editingStationOrder = editingStationOrder.toMutableList().apply {
                            add(to, removeAt(from))
                        }
                        val source =
                            AppSettings.sources.find { it.xmlFilePath == xmlPath }
                                ?.copy(stationOrder = editingStationOrder)
                                ?: return@ReorderableList
                        AppSettings.updateSource(source)
                    },
                ).invoke()
            }
            Spacer(Modifier.height(UiSpacing.SheetBottom))
        }
    }
}

@Composable
private fun StationCard(station: RadioStation, selected: Boolean, onClick: () -> Unit) {
    Card(modifier = Modifier.clickable(onClick = onClick)) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxWidth().padding(UiSpacing.Large)) {
                Text(
                    station.name,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) colorScheme.primary else colorScheme.onSurface
                )
                InfoLine("Track", "${station.tracks.size} | ${station.tracks.totalDuration()}")
                InfoLine("Stinger", "${station.stingers.size} | ${station.stingers.totalDuration()}")
                InfoLine("DJ", "${station.djSamples.size} | ${station.djSamples.totalDuration()}")
            }
            ActiveIcon(selected)
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Text(text = "$label: $value", fontSize = 12.sp, color = colorScheme.onSurface.copy(alpha = 0.6f))
}

private fun List<Sample>.totalDuration(): String {
    val totalSec = sumOf { it.durationSec }
    return if (totalSec >= 60) "${totalSec.toInt() / 60}m${(totalSec % 60).toInt()}s" else "${totalSec.fmt()}s"
}

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
