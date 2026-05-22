package io.github.miuzarte.fhradio.pages

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.miuzarte.fhradio.AppRuntime
import io.github.miuzarte.fhradio.AppSettings
import io.github.miuzarte.fhradio.BuildKonfig
import io.github.miuzarte.fhradio.Radio
import io.github.miuzarte.fhradio.constants.UiSpacing
import io.github.miuzarte.fhradio.model.PatternNode
import io.github.miuzarte.fhradio.model.PlayMode
import io.github.miuzarte.fhradio.model.RadioMode
import io.github.miuzarte.fhradio.model.SampleType
import io.github.miuzarte.fhradio.scaffolds.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import kotlin.math.roundToInt

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

        val playMode by remember(AppSettings.playMode) {
            mutableStateOf(AppSettings.playMode)
        }

        var stingerProbability by remember(AppSettings.stingerProbability) {
            mutableStateOf(AppSettings.stingerProbability)
        }
        var djProbability by remember(AppSettings.djProbability) {
            mutableStateOf(AppSettings.djProbability)
        }

        val crossLists by remember(AppSettings.crossLists) {
            mutableStateOf(AppSettings.crossLists)
        }

        val patternEnabled by remember(AppSettings.patternEnabled) {
            mutableStateOf(AppSettings.patternEnabled)
        }
        val patternNodes by remember(AppSettings.patternNodes) {
            mutableStateOf(AppSettings.patternNodes)
        }

        val crossFadeEnabled by remember(AppSettings.crossFadeEnabled) {
            mutableStateOf(AppSettings.crossFadeEnabled)
        }

        val autoResume by remember(AppSettings.autoResume) {
            mutableStateOf(AppSettings.autoResume)
        }

        var showPatternSheet by remember { mutableStateOf(false) }
        var showNodeEditSheet by remember { mutableStateOf(false) }
        var showPlaylistSheet by remember { mutableStateOf(false) }
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
                            // Stinger 概率
                            SuperSlider(
                                title = "Stinger 概率",
                                summary = "电台标识音插入概率",
                                value = stingerProbability.toFloat(),
                                onValueChange = {
                                    stingerProbability = it.roundToInt()
                                },
                                onValueChangeFinished = {
                                    AppSettings.stingerProbability = stingerProbability
                                },
                                valueRange = 0f..100f,
                                steps = 100,
                                unit = "%",
                                displayFormatter = { "${it.toInt()}" },
                                inputInitialValue = "$stingerProbability",
                                inputFilter = { it.filter(Char::isDigit) },
                                inputValueRange = 0f..100f,
                                onInputConfirm = { input ->
                                    input.toIntOrNull()?.let {
                                        stingerProbability = it.coerceIn(0, 100)
                                        AppSettings.stingerProbability = stingerProbability
                                    }
                                },
                            )
                            // DJ 概率
                            SuperSlider(
                                title = "DJ 概率",
                                summary = "DJ 语音插入概率",
                                value = djProbability.toFloat(),
                                onValueChange = {
                                    djProbability = it.roundToInt()
                                },
                                onValueChangeFinished = {
                                    AppSettings.djProbability = djProbability
                                },
                                valueRange = 0f..100f,
                                steps = 100,
                                unit = "%",
                                displayFormatter = { "${it.toInt()}" },
                                inputInitialValue = "$djProbability",
                                inputFilter = { it.filter(Char::isDigit) },
                                inputValueRange = 0f..100f,
                                onInputConfirm = { input ->
                                    input.toIntOrNull()?.let {
                                        djProbability = it.coerceIn(0, 100)
                                        AppSettings.djProbability = djProbability
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
                                                AppSettings.playMode = PlayMode.Shuffle
                                            }
                                        ),
                                        DropdownItem(
                                            text = "顺序播放",
                                            selected = playMode == PlayMode.Order,
                                            onClick = {
                                                AppSettings.playMode = PlayMode.Order
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
                                                            AppSettings.crossLists -= SampleType.Track
                                                        else if (SampleType.Track !in crossLists)
                                                            AppSettings.crossLists += SampleType.Track
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
                                                            AppSettings.crossLists -= SampleType.Stinger
                                                        else if (SampleType.Stinger !in crossLists)
                                                            AppSettings.crossLists += SampleType.Stinger
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
                                                            AppSettings.crossLists -= SampleType.DJ
                                                        else if (SampleType.DJ !in crossLists)
                                                            AppSettings.crossLists += SampleType.DJ
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
                                            AppSettings.patternEnabled = it
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
                                    AppSettings.crossFadeEnabled = it
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
                            AppSettings.autoResume = it
                        },
                    )
                }
            }

            if (BuildKonfig.DEBUG) {
                item {
                    SectionSmallTitle("调试")
                    Card {
                        ArrowPreference(
                            title = "查看播放列表",
                            onClick = { showPlaylistSheet = true },
                            holdDownState = showPlaylistSheet,
                        )
                    }
                }

                /*
                item {
                    SectionSmallTitle("已调度 Marker")
                    Card {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(UiSpacing.Large),
                        ) {
                            Radio.syncDebugMarkers()
                            Scheduler.debugMarkers.forEach { info ->
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
                */
            }
        }

        OverlayBottomSheet(
            show = showPatternSheet,
            title = "循环模式",
            defaultWindowInsetsPadding = false,
            onDismissRequest = {
                showPatternSheet = false
                AppSettings.patternNodes = patternNodes
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
                                    AppSettings.patternNodes = patternNodes.toMutableList().also { it.removeAt(index) }
                                }) {
                                    Icon(MiuixIcons.Delete, contentDescription = "删除")
                                }
                            },
                        )
                    }
                },
                onSettle = { fromIndex, toIndex ->
                    AppSettings.patternNodes = patternNodes.toMutableList().also {
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
                    AppSettings.patternNodes = patternNodes.toMutableList().also {
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
            var editProb by remember(node.probability) { mutableStateOf(node.probability) }

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
                        value = editProb.toFloat(),
                        onValueChange = { editProb = it.roundToInt() },
                        valueRange = 0f..100f,
                        steps = 100,
                        unit = "%",
                        displayFormatter = { "${it.toInt()}" },
                        onInputConfirm = { input ->
                            input.toIntOrNull()?.let {
                                editProb = it.coerceIn(0, 100)
                            }
                        },
                    )
                    Spacer(Modifier.height(UiSpacing.Large))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        TextButton("保存", onClick = {
                            AppSettings.patternNodes = patternNodes.toMutableList().also {
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

        val playList = remember(showPlaylistSheet) {
            if (showPlaylistSheet) Radio.getPlayList()
            else null
        }
        OverlayBottomSheet(
            show = showPlaylistSheet,
            title = "播放列表",
            defaultWindowInsetsPadding = false,
            onDismissRequest = { showPlaylistSheet = false },
        ) {
            if (playList != null) {
                val (sections, currentIndex) = playList
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    itemSpacing = UiSpacing.Large,
                ) {
                    itemsIndexed(sections) { index, section ->
                        val isCurrent = index == currentIndex

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors =
                                if (isCurrent) CardColors(
                                    color = colorScheme.primary,
                                    contentColor = colorScheme.onPrimary,
                                )
                                else CardDefaults.defaultColors(),
                            insideMargin = PaddingValues(12.dp),
                        ) {
                            Text("""Track: ${section.track?.sample?.let { it.displayName + " - " + it.artist } ?: "NULL"}""")
                            Text("""Stinger: ${section.stinger?.sample?.soundName ?: "NULL"}""")
                            Text("""DJ: ${section.dj?.sample?.soundName ?: "NULL"}""")
                        }
                    }
                }
            }
            Spacer(Modifier.height(UiSpacing.SheetBottom))
        }
    }
}
