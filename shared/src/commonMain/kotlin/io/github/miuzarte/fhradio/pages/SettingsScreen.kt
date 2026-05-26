package io.github.miuzarte.fhradio.pages

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.miuzarte.fhradio.AppRuntime
import io.github.miuzarte.fhradio.AppSettings
import io.github.miuzarte.fhradio.BuildKonfig
import io.github.miuzarte.fhradio.Radio
import io.github.miuzarte.fhradio.PlaySection
import io.github.miuzarte.fhradio.Scheduler
import io.github.miuzarte.fhradio.constants.UiSpacing
import io.github.miuzarte.fhradio.model.PatternNode
import io.github.miuzarte.fhradio.model.PlayMode
import io.github.miuzarte.fhradio.model.RadioMode
import io.github.miuzarte.fhradio.model.SampleType
import io.github.miuzarte.fhradio.scaffolds.*
import io.github.miuzarte.fhradio.util.format
import io.github.miuzarte.fhradio.util.formatTime
import kotlinx.coroutines.isActive
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.AddCircle
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import kotlin.math.roundToInt
import kotlin.time.Duration

@Composable
fun SettingsScreen(
    scrollBehavior: ScrollBehavior,
    bottomInnerPadding: Dp,
) {
    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameMillis {}
            AppRuntime.syncVolumeFromPlayers(force = true)
        }
    }

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

        var volume by remember(AppSettings.volume) {
            mutableStateOf(AppSettings.volume.toFloat())
        }
        val autoResume by remember(AppSettings.autoResume) {
            mutableStateOf(AppSettings.autoResume)
        }

        var maxContinuousTrack by remember { mutableStateOf(AppSettings.maxContinuousTrack.toFloat()) }
        var maxContinuousStinger by remember { mutableStateOf(AppSettings.maxContinuousStinger.toFloat()) }
        var maxContinuousDj by remember { mutableStateOf(AppSettings.maxContinuousDj.toFloat()) }

        var showPatternSheet by remember { mutableStateOf(false) }
        var showNodeEditSheet by remember { mutableStateOf(false) }
        var showPlaylistSheet by remember { mutableStateOf(false) }
        var playList by remember { mutableStateOf<Pair<List<PlaySection>, Int?>?>(null) }
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
                        if (RadioMode.entries[it] == AppSettings.radioMode)
                            Radio.reset() // 保存设置在相同时不会触发重置, 手动触发
                        AppSettings.radioMode = RadioMode.entries[it]
                    },
                )
                Card {
                    // 完全随机
                    AnimatedVisibility(mode == RadioMode.Random) {
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
                            // DJ 集合(多选 Dropdown): 根据 DJ 的分类来指定循环/随机播放时包含的 DJ 列表
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
                        }
                    }
                    // 种子控制
                    AnimatedVisibility(mode == RadioMode.Seed) {
                        Column {
                        }
                    }
                    // 播放器
                    AnimatedVisibility(mode == RadioMode.Player) {
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
                            AnimatedVisibility(playMode == PlayMode.Shuffle || (playMode == PlayMode.Order && !patternEnabled)) {
                                Column {
                                    // 跨列表播放
                                    OverlayDropdownPreference(
                                        title = "跨列表播放",
                                        entries = listOf(
                                            DropdownEntry(
                                                items = listOf(
                                                    DropdownItem(
                                                        text = SampleType.Track.toString(),
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
                                                        text = SampleType.Stinger.toString(),
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
                                                        text = SampleType.DJ.toString(),
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
                                    )
                                }
                            }
                            AnimatedVisibility(playMode == PlayMode.Shuffle) {
                                Column {
                                    // 最大连续 Track
                                    SuperSlider(
                                        title = "最大连续 Track",
                                        value = maxContinuousTrack,
                                        onValueChange = { maxContinuousTrack = it },
                                        onValueChangeFinished = {
                                            AppSettings.maxContinuousTrack = maxContinuousTrack.toInt()
                                        },
                                        enabled = SampleType.Track in crossLists,
                                        valueRange = 0f..10f,
                                        steps = 10 - 0 - 1,
                                        unit = "首",
                                        zeroStateText = "不限",
                                        displayFormatter = { "${it.toInt()}" },
                                        inputInitialValue = "${AppSettings.maxContinuousTrack}",
                                        inputFilter = { it.filter(Char::isDigit) },
                                        inputValueRange = 0f..10f,
                                        onInputConfirm = { input ->
                                            input.toIntOrNull()?.let {
                                                val v = it.coerceIn(0, 10)
                                                maxContinuousTrack = v.toFloat()
                                                AppSettings.maxContinuousTrack = v
                                            }
                                        },
                                    )
                                    // 最大连续 Stinger
                                    SuperSlider(
                                        title = "最大连续 Stinger",
                                        value = maxContinuousStinger,
                                        onValueChange = { maxContinuousStinger = it },
                                        onValueChangeFinished = {
                                            AppSettings.maxContinuousStinger = maxContinuousStinger.toInt()
                                        },
                                        enabled = SampleType.Stinger in crossLists,
                                        valueRange = 0f..10f,
                                        steps = 10 - 0 - 1,
                                        unit = "首",
                                        zeroStateText = "不限",
                                        displayFormatter = { "${it.toInt()}" },
                                        inputInitialValue = "${AppSettings.maxContinuousStinger}",
                                        inputFilter = { it.filter(Char::isDigit) },
                                        inputValueRange = 0f..10f,
                                        onInputConfirm = { input ->
                                            input.toIntOrNull()?.let {
                                                val v = it.coerceIn(0, 10)
                                                maxContinuousStinger = v.toFloat()
                                                AppSettings.maxContinuousStinger = v
                                            }
                                        },
                                    )
                                    // 最大连续 DJ
                                    SuperSlider(
                                        title = "最大连续 DJ",
                                        value = maxContinuousDj,
                                        onValueChange = { maxContinuousDj = it },
                                        onValueChangeFinished = {
                                            AppSettings.maxContinuousDj = maxContinuousDj.toInt()
                                        },
                                        enabled = SampleType.DJ in crossLists,
                                        valueRange = 0f..10f,
                                        steps = 10 - 0 - 1,
                                        unit = "首",
                                        zeroStateText = "不限",
                                        displayFormatter = { "${it.toInt()}" },
                                        inputInitialValue = "${AppSettings.maxContinuousDj}",
                                        inputFilter = { it.filter(Char::isDigit) },
                                        inputValueRange = 0f..10f,
                                        onInputConfirm = { input ->
                                            input.toIntOrNull()?.let {
                                                val v = it.coerceIn(0, 10)
                                                maxContinuousDj = v.toFloat()
                                                AppSettings.maxContinuousDj = v
                                            }
                                        },
                                    )
                                }
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
                    SuperSlider(
                        title = "音量",
                        value = volume,
                        onValueChange = {
                            volume = it
                            AppRuntime.setVolume(it.toInt())
                        },
                        onValueChangeFinished = {
                            AppSettings.volume = volume.toInt()
                        },
                        valueRange = 0f..100f,
                        steps = 100,
                        unit = "%",
                        displayFormatter = { "${it.toInt()}" },
                        inputInitialValue = "${AppSettings.volume}",
                        inputFilter = { it.filter(Char::isDigit) },
                        inputValueRange = 0f..100f,
                        onInputConfirm = { input ->
                            input.toIntOrNull()?.let {
                                val v = it.coerceIn(0, 100)
                                volume = v.toFloat()
                                AppRuntime.setVolume(v)

                                AppSettings.volume = v
                            }
                        },
                    )
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

            item {
                SectionSmallTitle("调试")
                Card {
                    SwitchPreference(
                        title = "启用",
                        checked = AppRuntime.debug,
                        onCheckedChange = { AppRuntime.debug = it },
                    )
                    ArrowPreference(
                        title = "查看播放列表",
                        onClick = {
                            playList = Radio.getPlayList()
                            showPlaylistSheet = true
                        },
                        holdDownState = showPlaylistSheet,
                    )
                }
            }

            if (AppRuntime.debug) {
                item {
                    SectionSmallTitle("已调度 Marker")

                    var frameCount by remember { mutableStateOf(0) }
                    LaunchedEffect(Unit) {
                        while (true) {
                            withFrameMillis {}
                            frameCount++
                        }
                    }

                    Card {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(UiSpacing.Large),
                        ) {
                            @Suppress("UNUSED_EXPRESSION")
                            frameCount
                            Scheduler.jobs.forEach { job ->
                                // TODO: platforms timezone
                                // UTC+8
                                val delay = job.delay
                                val remaining = job.remaining()
                                val scheduledAt = job.scheduledAt
                                Text(
                                    text = "${job.tag} @ ${delay.format()} (-${remaining.format()}) (${scheduledAt.formatTime()})",
                                    color =
                                        if (remaining <= Duration.ZERO) colorScheme.primary
                                        else colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        }

        OverlayBottomSheet(
            show = showPatternSheet,
            title = "循环模式",
            endAction = {
                IconButton(
                    onClick = {
                        AppSettings.patternNodes = patternNodes.toMutableList()
                            .also { it.add(PatternNode()) }
                    },
                ) {
                    Icon(
                        imageVector = MiuixIcons.AddCircle,
                        contentDescription = "添加",
                    )
                }
            },
            onDismissRequest = {
                showPatternSheet = false
                AppSettings.patternNodes = patternNodes
            },
            defaultWindowInsetsPadding = false,
        ) {
            ReorderableList(
                itemsProvider = {
                    patternNodes.mapIndexed { index, node ->
                        ReorderableList.Item(
                            id = "pn_$index",
                            title = node.type.toString(),
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
            Spacer(Modifier.height(UiSpacing.SheetBottom))
        }

        if (editingNodeIndex in patternNodes.indices) {
            val node = patternNodes[editingNodeIndex]
            var editType by remember(node.type) { mutableStateOf(node.type) }
            var editStep by remember(node.step) { mutableStateOf(node.step.toFloat()) }
            var editProb by remember(node.probability) { mutableStateOf(node.probability) }

            fun saveNodeAndExit() {
                AppSettings.patternNodes = patternNodes.toMutableList()
                    .also {
                        it[editingNodeIndex] = PatternNode(
                            type = editType,
                            step = editStep.toInt(),
                            probability = editProb.coerceIn(0, 100),
                        )
                    }
                showNodeEditSheet = false
            }

            OverlayBottomSheet(
                show = showNodeEditSheet,
                title = "编辑节点",
                endAction = {
                    IconButton(onClick = ::saveNodeAndExit) {
                        Icon(
                            imageVector = MiuixIcons.Ok,
                            contentDescription = "确认",
                        )
                    }
                },
                onDismissRequest = ::saveNodeAndExit,
                defaultWindowInsetsPadding = false,
            ) {
                Column {
                    TabRowWithContour(
                        tabs = SampleType.entries.map { it.toString() },
                        selectedTabIndex = editType.ordinal,
                        onTabSelected = { editType = SampleType.entries[it] },
                        modifier = Modifier
                            .padding(horizontal = UiSpacing.Large)
                            .padding(bottom = UiSpacing.Large),
                    )
                    SuperSlider(
                        title = "步进",
                        value = editStep,
                        onValueChange = { editStep = it },
                        valueRange = -10f..10f,
                        steps = 10 - (-10) - 1,
                        unit = "首",
                        displayFormatter = { "${it.toInt()}" },
                        inputInitialValue = "${editStep.toInt()}",
                        inputFilter = { it.filter { ch -> ch.isDigit() || ch == '-' } },
                        inputValueRange = Int.MIN_VALUE.toFloat()..Int.MAX_VALUE.toFloat(),
                        onInputConfirm = { input ->
                            input.toIntOrNull()?.let { editStep = it.toFloat() }
                        },
                    )
                    SuperSlider(
                        title = "概率",
                        value = editProb.toFloat(),
                        onValueChange = { editProb = it.roundToInt() },
                        valueRange = 0f..100f,
                        steps = 100 - 0 - 1,
                        unit = "%",
                        displayFormatter = { "${it.toInt()}" },
                        onInputConfirm = { input ->
                            input.toIntOrNull()?.let {
                                editProb = it.coerceIn(0, 100)
                            }
                        },
                    )
                }
                Spacer(Modifier.height(UiSpacing.SheetBottom))
            }
        }

        OverlayBottomSheet(
            show = showPlaylistSheet,
            title = "播放列表",
            startAction = {
                IconButton(
                    onClick = { showPlaylistSheet = false },
                ) {
                    Icon(
                        imageVector = MiuixIcons.Back,
                        contentDescription = "返回",
                    )
                }
            },
            endAction = {
                IconButton(
                    onClick = { playList = Radio.getPlayList() },
                ) {
                    Icon(
                        imageVector = MiuixIcons.Refresh,
                        contentDescription = "刷新",
                    )
                }
            },
            onDismissRequest = { showPlaylistSheet = false },
            defaultWindowInsetsPadding = false,
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                itemSpacing = UiSpacing.Large,
            ) {
                val (sections, currentIndex) = playList ?: return@LazyColumn
                itemsIndexed(sections) { index, section ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                            if (index == currentIndex) CardColors(
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
            Spacer(Modifier.height(UiSpacing.SheetBottom))
        }
    }
}
