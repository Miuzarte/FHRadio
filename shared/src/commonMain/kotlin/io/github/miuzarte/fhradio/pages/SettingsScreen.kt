package io.github.miuzarte.fhradio.pages

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.miuzarte.fhradio.*
import io.github.miuzarte.fhradio.constants.UiSpacing
import io.github.miuzarte.fhradio.model.PatternNode
import io.github.miuzarte.fhradio.model.PlayMode
import io.github.miuzarte.fhradio.model.RadioMode
import io.github.miuzarte.fhradio.model.SampleType
import io.github.miuzarte.fhradio.pages.preference.VolumePreference
import io.github.miuzarte.fhradio.scaffolds.*
import io.github.miuzarte.fhradio.ui.contextClick
import io.github.miuzarte.fhradio.util.format
import io.github.miuzarte.fhradio.util.formatTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

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

    val haptic = LocalHapticFeedback.current

    Scaffold(
        topBar = {
            val minutes = AppSettings.totalPlaybackMinutes
            val subtitle = buildString {
                append("已播放")
                val d = minutes / 1440
                val h = (minutes % 1440) / 60
                val m = minutes % 60
                if (d > 0) append("${d}天")
                if (h > 0) append("${h}小时")
                append("${m}分钟")
            }
            TopAppBar(
                title = "设置",
                subtitle = subtitle,
                color = colorScheme.surface,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { contentPadding ->
        val listState = rememberLazyListState()

        // Radio
        val mode by rememberUpdatedState(AppSettings.radioMode)

        // RandomMode
        var stingerProbability by remember(AppSettings.stingerProbability) {
            mutableStateOf(AppSettings.stingerProbability)
        }
        var djProbability by remember(AppSettings.djProbability) {
            mutableStateOf(AppSettings.djProbability)
        }

        val djGameEvents by remember(AppSettings.djGameEvents) {
            mutableStateOf(AppSettings.djGameEvents)
        }
        var showDjGameEventsSheet by remember { mutableStateOf(false) }
        var editingDjGameEvents by remember(AppSettings.djGameEvents) {
            mutableStateOf(AppSettings.djGameEvents)
        }

        // SeedMode
        var seedText by remember { mutableStateOf(AppSettings.seedString) }
        val seedValue = remember(seedText) { AppSettings.parseSeed(seedText) }

        // PlayerMode
        val playMode by remember(AppSettings.playMode) {
            mutableStateOf(AppSettings.playMode)
        }

        val crossLists by remember(AppSettings.crossLists) {
            mutableStateOf(AppSettings.crossLists)
        }

        var maxContinuousTrack by remember { mutableStateOf(AppSettings.maxContinuousTrack.toFloat()) }
        var maxContinuousStinger by remember { mutableStateOf(AppSettings.maxContinuousStinger.toFloat()) }
        var maxContinuousDj by remember { mutableStateOf(AppSettings.maxContinuousDj.toFloat()) }

        val patternEnabled by remember(AppSettings.patternEnabled) {
            mutableStateOf(AppSettings.patternEnabled)
        }
        val patternNodes by remember(AppSettings.patternNodes) {
            mutableStateOf(AppSettings.patternNodes)
        }

        val crossFadeEnabled by remember(AppSettings.crossFadeEnabled) {
            mutableStateOf(AppSettings.crossFadeEnabled)
        }

        val excludedTrackSuffixes by remember(AppSettings.excludedTrackSuffixes) {
            mutableStateOf(AppSettings.excludedTrackSuffixes)
        }
        var editingExcludeSuffixes by remember(AppSettings.excludedTrackSuffixes) {
            mutableStateOf(AppSettings.excludedTrackSuffixes)
        }

        // Application
        val audioDucking by remember(AppSettings.audioDucking) {
            mutableStateOf(AppSettings.audioDucking)
        }
        val autoResume by remember(AppSettings.autoResume) {
            mutableStateOf(AppSettings.autoResume)
        }
        val tracksTopAppBarKeepProgressBar by remember(AppSettings.tracksTopAppBarKeepProgressBar) {
            mutableStateOf(AppSettings.tracksTopAppBarKeepProgressBar)
        }

        var showPatternSheet by remember { mutableStateOf(false) }
        var editingNodeIndex by remember { mutableStateOf(-1) }
        var showNodeEditSheet by remember { mutableStateOf(false) }

        var showExcludeSuffixDialog by remember { mutableStateOf(false) }

        // Debug
        var playList by remember { mutableStateOf<Pair<List<PlaySection>, Int?>?>(null) }
        var showPlaylistSheet by remember { mutableStateOf(false) }

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
                    onTabSelected = { index ->
                        haptic.contextClick()
                        val mode = RadioMode.entries[index]
                        if (mode == AppSettings.radioMode) {
                            Radio.reset() // 保存设置在相同时不会触发重置, 手动触发
                        } else {
                            AppSettings.radioMode = mode
                            if (mode == RadioMode.Seed)
                                Radio.selectedStation?.let { Radio.setStation(it) }
                        }
                    },
                )
                Card {
                    // 完全随机
                    AnimatedVisibility(mode == RadioMode.Random) {
                        Column {
                            // Stinger 概率
                            ArrowSlider(
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
                            ArrowSlider(
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
                            // DJ 集合
                            ArrowPreference(
                                title = "DJ 集合",
                                summary = "按 DJ.GameEvent 筛选，白名单，全不选则使用默认集合",
                                endActions = {
                                    Text(
                                        text = djGameEvents
                                            .sorted()
                                            .joinToString(limit = 3, truncated = "...")
                                            .ifEmpty { "无" },
                                        color = colorScheme.onSurfaceVariantActions,
                                    )
                                },
                                onClick = {
                                    haptic.contextClick()
                                    editingDjGameEvents = djGameEvents
                                    showDjGameEventsSheet = true
                                },
                            )
                        }
                    }
                    // 种子控制
                    AnimatedVisibility(mode == RadioMode.Seed) {
                        Column {
                            Column(
                                modifier = Modifier.padding(UiSpacing.Large),
                                verticalArrangement = Arrangement.spacedBy(UiSpacing.Medium),
                            ) {
                                SuperTextField(
                                    value = seedText,
                                    onValueChange = { seedText = it },
                                    label = "种子",
                                    singleLine = true,
                                    onFocusLost = {
                                        AppSettings.seedString = seedText
                                        if (seedText.toIntOrNull() != null && Random.nextInt(100) < 1) {
                                            AppRuntime.snackbar("你知道吗：将同样的字符串用于 Minecraft 世界生成器种子也会得到一样的值")
                                        }
                                    },
                                )
                                Text(
                                    text = "值：$seedValue",
                                    color = colorScheme.onSurfaceVariantActions,
                                )
                            }
                        }
                    }
                    // 播放器
                    AnimatedVisibility(mode == RadioMode.Player) {
                        Column {
                            // 播放模式
                            OverlayDropdownPreference(
                                title = "播放模式",
                                entry = DropdownEntry(
                                    items = listOf(
                                        DropdownItem(
                                            text = "随机播放",
                                            selected = playMode == PlayMode.Shuffle,
                                            onClick = {
                                                AppSettings.playMode = PlayMode.Shuffle
                                            },
                                        ),
                                        DropdownItem(
                                            text = "顺序播放",
                                            selected = playMode == PlayMode.Order,
                                            onClick = {
                                                AppSettings.playMode = PlayMode.Order
                                            },
                                        ),
                                    ),
                                ),
                                enabled = true,
                            )
                            AnimatedVisibility(playMode == PlayMode.Shuffle || (playMode == PlayMode.Order && !patternEnabled)) {
                                Column {
                                    // 跨列表播放
                                    OverlayDropdownPreference(
                                        title = "跨列表播放",
                                        entries = SampleType.entries.map { type ->
                                            DropdownEntry(
                                                items = listOf(
                                                    DropdownItem(
                                                        text = type.toString(),
                                                        selected = type in crossLists,
                                                        onClick = {
                                                            if (type in crossLists && crossLists.size > 1)
                                                                AppSettings.crossLists -= type
                                                            else if (type !in crossLists)
                                                                AppSettings.crossLists += type
                                                        },
                                                    ),
                                                ),
                                            )
                                        },
                                        collapseOnSelection = false,
                                    )
                                }
                            }
                            AnimatedVisibility(playMode == PlayMode.Shuffle) {
                                Column {
                                    // 最大连续 Track
                                    ArrowSlider(
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
                                    ArrowSlider(
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
                                    ArrowSlider(
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
                                        onClick = {
                                            haptic.contextClick()
                                            showPatternSheet = true
                                        },
                                        holdDownState = showPatternSheet,
                                    )
                                }
                            }
                            // Stinger 交叉淡出
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
                    AnimatedVisibility(mode != RadioMode.Seed) {
                        Column {
                            // 按后缀排除曲目
                            ArrowPreference(
                                title = "按后缀排除曲目",
                                summary = "按 SoundName 的后缀排除部分用于游戏中剧情的曲目",
                                endActions = {
                                    Text(
                                        text = excludedTrackSuffixes
                                            .sorted()
                                            .joinToString(",")
                                            .ifEmpty { "无" },
                                        color = colorScheme.onSurfaceVariantActions,
                                    )
                                },
                                onClick = {
                                    haptic.contextClick()
                                    editingExcludeSuffixes = excludedTrackSuffixes
                                    showExcludeSuffixDialog = true
                                },
                            )
                        }
                    }
                }
            }

            item {
                SectionSmallTitle("应用")
                Card {
                    VolumePreference() // 音量
                    SwitchPreference(
                        title = "音频回避",
                        summary = "DJ Sample 播放时降低 Track 音量" +
                                "\n桌面端无效，vlc 无法单独控制多个 Player 的音量",
                        checked = audioDucking,
                        onCheckedChange = {
                            AppSettings.audioDucking = it
                        },
                    )
                    SwitchPreference(
                        title = "启动应用后自动播放",
                        summary = "应用就绪后直接继续播放最后选中的电台",
                        checked = autoResume,
                        onCheckedChange = {
                            AppSettings.autoResume = it
                        },
                    )
                    SwitchPreference(
                        title = "曲目页顶栏进度条常驻",
                        summary = "曲目页下滑不再隐藏顶栏进度条",
                        checked = tracksTopAppBarKeepProgressBar,
                        onCheckedChange = {
                            AppSettings.tracksTopAppBarKeepProgressBar = it
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
                    AnimatedVisibility(AppRuntime.debug) {
                        Column {
                            ArrowPreference(
                                title = "查看播放列表",
                                onClick = {
                                    haptic.contextClick()
                                    playList = Radio.getPlayList()
                                    showPlaylistSheet = true
                                },
                                holdDownState = showPlaylistSheet,
                            )
                            var mainVol by remember { mutableStateOf(AppRuntime.mainPlayer.getVolume()) }
                            var secondaryVol by remember { mutableStateOf(AppRuntime.secondaryPlayer.getVolume()) }
                            LaunchedEffect(Unit) {
                                while (isActive) {
                                    AppRuntime.mainPlayer.getVolume().takeIf { it > 0 }?.let { mainVol = it }
                                    AppRuntime.secondaryPlayer.getVolume().takeIf { it > 0 }?.let { secondaryVol = it }
                                    delay(20.milliseconds)
                                }
                            }
                            ArrowSlider(
                                title = "主音量",
                                value = mainVol.toFloat(),
                                onValueChange = {
                                    mainVol = it.roundToInt()
                                    AppRuntime.mainPlayer.setVolume(mainVol)
                                },
                                valueRange = 0f..100f,
                                steps = 100,
                                unit = "%",
                                displayFormatter = { "${it.toInt()}" },
                                onInputConfirm = {
                                    mainVol = it.toFloat().roundToInt()
                                    AppRuntime.mainPlayer.setVolume(mainVol)
                                },
                            )
                            ArrowSlider(
                                title = "副音量",
                                value = secondaryVol.toFloat(),
                                onValueChange = {
                                    secondaryVol = it.roundToInt()
                                    AppRuntime.secondaryPlayer.setVolume(secondaryVol)
                                },
                                valueRange = 0f..100f,
                                steps = 100,
                                unit = "%",
                                displayFormatter = { "${it.toInt()}" },
                                onInputConfirm = {
                                    secondaryVol = it.toFloat().roundToInt()
                                    AppRuntime.secondaryPlayer.setVolume(secondaryVol)
                                },
                            )
                            var mainPreamp by remember { mutableStateOf(0f) }
                            var secondaryPreamp by remember { mutableStateOf(0f) }
                            ArrowSlider(
                                title = "主增益",
                                value = mainPreamp,
                                onValueChange = {
                                    mainPreamp = it
                                    AppRuntime.mainPlayer.setPreamp(it)
                                },
                                valueRange = -10f..0f,
                                steps = (0 + 10) * 10 - 1,
                                unit = "db",
                                displayFormatter = { "${it.toInt()}" },
                                onInputConfirm = {
                                    mainPreamp = it.toFloat()
                                    AppRuntime.mainPlayer.setPreamp(it.toFloat())
                                },
                            )
                            ArrowSlider(
                                title = "副增益",
                                value = secondaryPreamp,
                                onValueChange = {
                                    secondaryPreamp = it
                                    AppRuntime.secondaryPlayer.setPreamp(it)
                                },
                                valueRange = -10f..0f,
                                steps = (0 + 10) * 10 - 1,
                                unit = "db",
                                displayFormatter = { "${it.toInt()}" },
                                onInputConfirm = {
                                    secondaryPreamp = it.toFloat()
                                    AppRuntime.secondaryPlayer.setPreamp(it.toFloat())
                                },
                            )
                        }
                    }
                }
            }

            if (AppRuntime.debug) {
                item {
                    SectionSmallTitle("已调度 Marker")

                    var frameCount by remember { mutableStateOf(0) }
                    LaunchedEffect(Unit) {
                        while (isActive) {
                            frameCount++
                            delay(20.milliseconds)
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
                                val triggerPosition = job.triggerPosition
                                val remaining = job.remaining
                                val scheduledAt = job.scheduledAt
                                Text(
                                    text = "${job.tag} @ ${triggerPosition.format()} (${scheduledAt.formatTime(withMs = false)}) (-${remaining.format()})",
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
                AppSettings.patternNodes = patternNodes
                showPatternSheet = false
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
                                IconButton(
                                    onClick = {
                                        AppSettings.patternNodes =
                                            patternNodes.toMutableList().also { it.removeAt(index) }
                                    },
                                ) {
                                    Icon(
                                        imageVector = MiuixIcons.Delete,
                                        contentDescription = "删除",
                                    )
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
                        onTabSelected = {
                            haptic.contextClick()
                            editType = SampleType.entries[it]
                        },
                        modifier = Modifier
                            .padding(horizontal = UiSpacing.Large)
                            .padding(bottom = UiSpacing.Large),
                    )
                    ArrowSlider(
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
                    ArrowSlider(
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

        OverlayBottomSheet(
            show = showDjGameEventsSheet,
            title = "DJ 集合",
            startAction = {
                IconButton(onClick = { showDjGameEventsSheet = false }) {
                    Icon(
                        imageVector = MiuixIcons.Close,
                        contentDescription = "取消",
                    )
                }
            },
            endAction = {
                IconButton(
                    onClick = {
                        AppSettings.djGameEvents = editingDjGameEvents
                        showDjGameEventsSheet = false
                    },
                ) {
                    Icon(
                        imageVector = MiuixIcons.Ok,
                        contentDescription = "保存",
                    )
                }
            },
            onDismissRequest = { showDjGameEventsSheet = false },
            defaultWindowInsetsPadding = false,
        ) {
            LazyColumn {
                items(AppSettings.allDjGameEvents) { event ->
                    CheckboxPreference(
                        title = event,
                        checked = event in editingDjGameEvents,
                        onCheckedChange = { checked ->
                            editingDjGameEvents =
                                if (checked) editingDjGameEvents + event
                                else editingDjGameEvents - event
                        },
                    )
                }
            }
            Spacer(Modifier.height(UiSpacing.SheetBottom))
        }

        OverlayDialog(
            show = showExcludeSuffixDialog,
            title = "按后缀排除曲目",
            onDismissRequest = { showExcludeSuffixDialog = false },
            defaultWindowInsetsPadding = false,
        ) {
            Column {
                listOf("_ID", "_FI", "_LI").forEach { suffix ->
                    CheckboxPreference(
                        title = suffix,
                        checked = suffix in editingExcludeSuffixes,
                        onCheckedChange = { checked ->
                            editingExcludeSuffixes =
                                if (checked) editingExcludeSuffixes + suffix
                                else editingExcludeSuffixes - suffix
                        },
                    )
                }
                Row(horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(
                        text = "取消",
                        onClick = {
                            haptic.contextClick()
                            showExcludeSuffixDialog = false
                        },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(20.dp))
                    TextButton(
                        text = "确定",
                        onClick = {
                            haptic.contextClick()
                            AppSettings.excludedTrackSuffixes = editingExcludeSuffixes
                            showExcludeSuffixDialog = false
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            }
        }
    }
}
