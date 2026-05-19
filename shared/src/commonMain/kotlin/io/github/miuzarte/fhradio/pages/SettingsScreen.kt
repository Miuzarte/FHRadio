package io.github.miuzarte.fhradio.pages

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.miuzarte.fhradio.AppRuntime
import io.github.miuzarte.fhradio.AppSettings
import io.github.miuzarte.fhradio.BuildKonfig
import io.github.miuzarte.fhradio.PatternNode
import io.github.miuzarte.fhradio.model.SampleType
import io.github.miuzarte.fhradio.Radio
import io.github.miuzarte.fhradio.RadioMode
import io.github.miuzarte.fhradio.constants.UiSpacing
import io.github.miuzarte.fhradio.model.PlayMode
import io.github.miuzarte.fhradio.scaffolds.LazyColumn
import io.github.miuzarte.fhradio.scaffolds.ReorderableList
import io.github.miuzarte.fhradio.scaffolds.SectionSmallTitle
import io.github.miuzarte.fhradio.scaffolds.SuperTextField
import io.github.miuzarte.fhradio.scaffolds.SuperSlider
import io.github.miuzarte.fhradio.util.format
import kotlin.time.Duration
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

@Composable
fun SettingsScreen(
    scrollBehavior: ScrollBehavior,
    bottomInnerPadding: Dp,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = "设置",
                color = colorScheme.surface,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { contentPadding ->
        val listState = rememberLazyListState()

        val mode by rememberUpdatedState(AppSettings.radioMode)
        val isRandomMode = remember(mode) { mode == RadioMode.Random }
        val isSeedMode = remember(mode) { mode == RadioMode.Seed }
        val isPlayerMode = remember(mode) { mode == RadioMode.Player }

        var stingerChance by remember { mutableStateOf(AppSettings.radioSettings.stingerChance.toFloat()) }
        var djChance by remember { mutableStateOf(AppSettings.radioSettings.djChance.toFloat()) }
        // var playlistType by remember { mutableStateOf(AppSettings.radioSettings.playListType) }
        var playMode by remember { mutableStateOf(AppSettings.playMode) }
        var autoResume by remember { mutableStateOf(AppSettings.radioSettings.autoResume) }
        var patternEnabled by remember { mutableStateOf(AppSettings.radioSettings.patternEnabled) }
        var patternNodes by remember { mutableStateOf(AppSettings.loadPatternNodes().toMutableList()) }
        var crossLists by remember { mutableStateOf(AppSettings.crossLists.toMutableList()) }
        var crossFadeEnabled by remember { mutableStateOf(AppSettings.radioSettings.crossFadeEnabled) }

        fun saveSettings() {
            val s = AppSettings.radioSettings.copy(
                stingerChance = stingerChance.toInt(),
                djChance = djChance.toInt(),
                // playListType = playlistType,
                playMode = playMode,
                radioMode = AppSettings.radioMode,
                autoResume = autoResume,
                patternEnabled = patternEnabled,
                crossFadeEnabled = crossFadeEnabled,
            )
            AppSettings.saveSettings(s)
            AppSettings.saveCrossLists(crossLists)
            Radio.reschedule()
        }

        var showPatternSheet by remember { mutableStateOf(false) }
        var showNodeEditSheet by remember { mutableStateOf(false) }
        var editingNodeIndex by remember { mutableStateOf(-1) }

        LazyColumn(
            contentPadding = contentPadding,
            scrollBehavior = scrollBehavior,
            state = listState,
            bottomInnerPadding = bottomInnerPadding,
        ) {
            item {
                SectionSmallTitle("电台")
                TabRow(
                    modifier = Modifier.padding(bottom = 12.dp),
                    tabs = listOf("完全随机", "种子控制", "播放器"),
                    selectedTabIndex = AppSettings.radioMode.ordinal,
                    onTabSelected = {
                        when (it) {
                            RadioMode.Seed.ordinal -> {
                                AppRuntime.snackbar("未实现")
                                return@TabRow
                            }
                        }
                        AppSettings.radioMode = RadioMode.entries[it]
                        saveSettings()
                    },
                )
                Card {
                    // (多选 Dropdown) DJ 集合: 根据 DJ 的分类来指定循环/随机播放时包含的 DJ 列表
                    OverlayDropdownPreference(
                        title = "DJ 集合",
                        entry = DropdownEntry(
                            // TODO: 导入 RadioInfo 后再收集 GameEvent
                            items = listOf(
                                DropdownItem(
                                    text = "SkillSongStart",
                                    selected = "SkillSongStart" in listOf("SkillSongStart", "SkillSongEnd"),
                                ),
                                DropdownItem(
                                    text = "SkillSongEnd",
                                    selected = "SkillSongEnd" in listOf("SkillSongStart", "SkillSongEnd"),
                                )
                            )
                        ),
                        enabled = false,
                    )
                    // 完全随机
                    AnimatedVisibility(isRandomMode) {
                        Column {
                            /*
                            // 播放列表
                            OverlayDropdownPreference(
                                title = "播放列表",
                                entry = DropdownEntry(
                                    items = listOf(
                                        DropdownItem(
                                            text = "Track",
                                            selected = playlistType == PlayListType.Track,
                                            onClick = {
                                                playlistType = PlayListType.Track
                                                saveSettings()
                                            }
                                        ),
                                        DropdownItem(
                                            text = "DJ",
                                            selected = playlistType == PlayListType.DJ,
                                            onClick = {
                                                playlistType = PlayListType.DJ
                                                saveSettings()
                                            }
                                        ),
                                        DropdownItem(
                                            text = "Stinger",
                                            selected = playlistType == PlayListType.Stinger,
                                            onClick = {
                                                playlistType = PlayListType.Stinger
                                                saveSettings()
                                            }
                                        ),
                                        DropdownItem(
                                            text = "FreeRoam",
                                            selected = playlistType == PlayListType.FreeRoam,
                                            onClick = {
                                                playlistType = PlayListType.FreeRoam
                                                saveSettings()
                                            }
                                        ),
                                        DropdownItem(
                                            text = "Event",
                                            selected = playlistType == PlayListType.Event,
                                            onClick = {
                                                playlistType = PlayListType.Event
                                                saveSettings()
                                            }
                                        ),
                                        DropdownItem(
                                            text = "ShortStinger",
                                            selected = playlistType == PlayListType.ShortStinger,
                                            onClick = {
                                                playlistType = PlayListType.ShortStinger
                                                saveSettings()
                                            }
                                        ),
                                    )
                                ),
                            )
                             */
                            // Stinger 概率
                            SuperSlider(
                                title = "Stinger 概率",
                                summary = "电台标识音插入概率",
                                value = stingerChance,
                                onValueChange = {
                                    stingerChance = it
                                    saveSettings()
                                },
                                valueRange = 0f..100f,
                                steps = 100,
                                unit = "%",
                                displayFormatter = { "${it.toInt()}" },
                                inputInitialValue = "${stingerChance.toInt()}",
                                inputFilter = { it.filter(Char::isDigit) },
                                inputValueRange = 0f..100f,
                                onInputConfirm = { input ->
                                    input.toIntOrNull()?.let {
                                        stingerChance = it.coerceIn(0, 100).toFloat()
                                        saveSettings()
                                    }
                                },
                            )
                            // DJ 概率
                            SuperSlider(
                                title = "DJ 概率",
                                summary = "DJ 语音插入概率",
                                value = djChance,
                                onValueChange = {
                                    djChance = it
                                    saveSettings()
                                },
                                valueRange = 0f..100f,
                                steps = 100,
                                unit = "%",
                                displayFormatter = { "${it.toInt()}" },
                                inputInitialValue = "${djChance.toInt()}",
                                inputFilter = { it.filter(Char::isDigit) },
                                inputValueRange = 0f..100f,
                                onInputConfirm = { input ->
                                    input.toIntOrNull()?.let {
                                        val v = it.coerceIn(0, 100)
                                        djChance = v.toFloat()
                                        saveSettings()
                                    }
                                },
                            )
                        }
                    }
                    // 种子控制
                    AnimatedVisibility(isSeedMode) {
                        Column {
                        }
                    }
                    // 播放器
                    AnimatedVisibility(isPlayerMode) {
                        Column {
                            OverlayDropdownPreference(
                                title = "播放模式",
                                entry = DropdownEntry(
                                    items = listOf(
                                        DropdownItem(
                                            text = "随机播放",
                                            selected = playMode == PlayMode.Shuffle,
                                            onClick = {
                                                playMode = PlayMode.Shuffle
                                                saveSettings()
                                            }
                                        ),
                                        DropdownItem(
                                            text = "顺序播放",
                                            selected = playMode == PlayMode.Order,
                                            onClick = {
                                                playMode = PlayMode.Order
                                                saveSettings()
                                            }
                                        )
                                    )
                                ),
                                enabled = true,
                            )
                            Column {
                                // 跨列表播放
                                OverlayDropdownPreference(
                                    title = "跨列表播放",
                                    entries = listOf(
                                        DropdownEntry(
                                            items = listOf(
                                                DropdownItem(
                                                    text = "Track",
                                                    selected = SampleType.Track in crossLists,
                                                    onClick = {
                                                        if (SampleType.Track in crossLists && crossLists.size > 1)
                                                            crossLists = mutableListOf<SampleType>().apply {
                                                                addAll(crossLists - SampleType.Track)
                                                            }
                                                        else if (SampleType.Track !in crossLists)
                                                            crossLists = mutableListOf<SampleType>().apply {
                                                                addAll(crossLists + SampleType.Track)
                                                            }
                                                        saveSettings()
                                                    }
                                                ),
                                            )
                                        ),
                                        DropdownEntry(
                                            items = listOf(
                                                DropdownItem(
                                                    text = "Stinger",
                                                    selected = SampleType.Stinger in crossLists,
                                                    onClick = {
                                                        if (SampleType.Stinger in crossLists && crossLists.size > 1)
                                                            crossLists = mutableListOf<SampleType>().apply {
                                                                addAll(crossLists - SampleType.Stinger)
                                                            }
                                                        else if (SampleType.Stinger !in crossLists)
                                                            crossLists = mutableListOf<SampleType>().apply {
                                                                addAll(crossLists + SampleType.Stinger)
                                                            }
                                                        saveSettings()
                                                    }
                                                ),
                                            )
                                        ),
                                        DropdownEntry(
                                            items = listOf(
                                                DropdownItem(
                                                    text = "DJ",
                                                    selected = SampleType.DJ in crossLists,
                                                    onClick = {
                                                        if (SampleType.DJ in crossLists && crossLists.size > 1)
                                                            crossLists = mutableListOf<SampleType>().apply {
                                                                addAll(crossLists - SampleType.DJ)
                                                            }
                                                        else if (SampleType.DJ !in crossLists)
                                                            crossLists = mutableListOf<SampleType>().apply {
                                                                addAll(crossLists + SampleType.DJ)
                                                            }
                                                        saveSettings()
                                                    }
                                                ),
                                            )
                                        ),
                                    ),
                                    collapseOnSelection = false,
                                    enabled = playMode == PlayMode.Shuffle || !patternEnabled,
                                )
                            }
                            AnimatedVisibility(playMode == PlayMode.Order) {
                                Column {
                                    // 启用循环模式
                                    SwitchPreference(
                                        title = "启用循环模式",
                                        checked = patternEnabled,
                                        onCheckedChange = {
                                            patternEnabled = it
                                            saveSettings()
                                        },
                                    )
                                    // 循环模式
                                    ArrowPreference(
                                        title = "循环模式",
                                        onClick = { showPatternSheet = true },
                                        holdDownState = showPatternSheet,
                                    )
                                }
                            }
                            SwitchPreference(
                                title = "Stinger 交叉淡出",
                                summary = "在 Stinger.StartNextTrack 时切歌而不是 Stinger.End",
                                checked = crossFadeEnabled,
                                onCheckedChange = {
                                    crossFadeEnabled = it
                                    saveSettings()
                                },
                            )
                        }
                    }
                }
            }

            item {
                SectionSmallTitle("应用")
                Card {
                    SwitchPreference(
                        title = "启动应用后自动播放",
                        summary = "应用就绪后直接开始继续播放最后选中的电台",
                        checked = autoResume,
                        onCheckedChange = {
                            autoResume = it
                            saveSettings()
                        },
                    )
                }
            }

            if (BuildKonfig.DEBUG) item {
                SectionSmallTitle("已调度 Marker")
                Card {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(UiSpacing.Large),
                    ) {
                        Radio.syncDebugMarkers()
                        Radio.debugScheduledMarkers.forEach { info ->
                            // TODO: platforms timezone
                            // UTC+8
                            val localMs = info.fireAt.toEpochMilliseconds() + (8 * 3600_000L)
                            val totalSec = (localMs / 1000) % 86400
                            val h = totalSec / 3600
                            val m = (totalSec % 3600) / 60
                            val s = totalSec % 60
                            val clock = "${h}:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
                            Text(
                                text = "${info.tag} @ ${info.targetPos} (${info.total.format()}) (-${info.remain.format()}) ($clock)",
                                color =
                                    if (info.remain <= Duration.ZERO) colorScheme.primary
                                    else colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }

        OverlayBottomSheet(
            show = showPatternSheet,
            title = "循环模式",
            defaultWindowInsetsPadding = false,
            onDismissRequest = {
                showPatternSheet = false
                AppSettings.savePatternNodes(patternNodes)
            },
        ) {
            ReorderableList(
                itemsProvider = {
                    patternNodes.mapIndexed { index, node ->
                        val typeName = when (node.type) {
                            SampleType.Track -> "Track"
                            SampleType.Stinger -> "Stinger"
                            SampleType.DJ -> "DJ"
                        }
                        ReorderableList.Item(
                            id = "pn_$index",
                            title = typeName,
                            subtitle = "步进: ${node.step} / 概率: ${node.probability}%",
                            onClick = {
                                editingNodeIndex = index
                                showNodeEditSheet = true
                            },
                            endAction = {
                                IconButton(onClick = {
                                    patternNodes = patternNodes.toMutableList().also { it.removeAt(index) }
                                }) {
                                    Icon(MiuixIcons.Delete, contentDescription = "删除")
                                }
                            },
                        )
                    }
                },
                onSettle = { fromIndex, toIndex ->
                    patternNodes = patternNodes.toMutableList().also {
                        it.add(toIndex, it.removeAt(fromIndex))
                    }
                },
            ).invoke()
            Spacer(Modifier.height(UiSpacing.Large))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                TextButton("添加", onClick = {
                    patternNodes = patternNodes.toMutableList().also {
                        it.add(PatternNode())
                    }
                })
            }
            Spacer(Modifier.height(UiSpacing.SheetBottom))
        }

        if (editingNodeIndex in patternNodes.indices) {
            val node = patternNodes[editingNodeIndex]
            var editType by remember(node.type) { mutableStateOf(node.type) }
            var editStep by remember(node.step) { mutableStateOf(node.step.toString()) }
            var editProb by remember(node.probability) { mutableStateOf(node.probability.toFloat()) }

            OverlayBottomSheet(
                show = showNodeEditSheet,
                title = "编辑节点",
                defaultWindowInsetsPadding = false,
                onDismissRequest = { showNodeEditSheet = false },
            ) {
                Column(modifier = Modifier.padding(horizontal = UiSpacing.Large)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        listOf(SampleType.Track, SampleType.Stinger, SampleType.DJ).forEach { type ->
                            TextButton(
                                text = when (type) {
                                    SampleType.Track -> "Track"
                                    SampleType.Stinger -> "Stinger"
                                    SampleType.DJ -> "DJ"
                                },
                                onClick = { editType = type },
                            )
                        }
                    }
                    SuperTextField(
                        value = editStep,
                        onValueChange = { v -> editStep = v.filter { it.isDigit() }.take(4) },
                        label = "步进",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(UiSpacing.Medium))
                    SuperSlider(
                        title = "概率",
                        value = editProb,
                        onValueChange = { editProb = it },
                        valueRange = 0f..100f,
                        steps = 100,
                        unit = "%",
                        displayFormatter = { "${it.toInt()}" },
                        onInputConfirm = { input ->
                            input.toIntOrNull()?.let { editProb = it.toFloat().coerceIn(0f, 100f) }
                        },
                    )
                    Spacer(Modifier.height(UiSpacing.Large))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        TextButton("保存", onClick = {
                            patternNodes = patternNodes.toMutableList().also {
                                it[editingNodeIndex] = PatternNode(
                                    type = editType,
                                    step = editStep.toIntOrNull() ?: 0,
                                    probability = editProb.toInt().coerceIn(0, 100),
                                )
                            }
                            showNodeEditSheet = false
                        })
                    }
                }
                Spacer(Modifier.height(UiSpacing.SheetBottom))
            }
        }
    }
}
