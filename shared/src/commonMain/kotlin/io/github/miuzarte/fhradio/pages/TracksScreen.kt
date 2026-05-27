@file:OptIn(top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi::class)

package io.github.miuzarte.fhradio.pages

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.miuzarte.fhradio.*
import io.github.miuzarte.fhradio.constants.UiSpacing
import io.github.miuzarte.fhradio.model.Sample
import io.github.miuzarte.fhradio.model.SampleType
import io.github.miuzarte.fhradio.scaffolds.LazyColumn
import io.github.miuzarte.fhradio.scaffolds.flowGrid
import io.github.miuzarte.fhradio.util.fmt
import io.github.miuzarte.fhradio.util.format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import kotlin.time.Duration

@Composable
fun TracksScreen(
    scrollBehavior: ScrollBehavior,
    bottomInnerPadding: Dp,
) {
    val tabs = remember { SampleType.entries }
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val trackListState = rememberLazyListState()
    val stingerListState = rememberLazyListState()
    val djListState = rememberLazyListState()

    var frameCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameMillis {}
            frameCount++
        }
    }

    Scaffold(
        topBar = {
            val station = remember(Radio.selectedStation) { Radio.selectedStation }
            TopAppBar(
                title = "曲目",
                subtitle = station?.name ?: "未选中电台",
                color = colorScheme.surface,
                scrollBehavior = scrollBehavior,
                bottomContent = {
                    Column {
                        TabRow(
                            tabs = tabs.map { it.toString() },
                            selectedTabIndex = selectedTabIndex,
                            onTabSelected = { index ->
                                if (selectedTabIndex == index) {
                                    station ?: return@TabRow
                                    // 双击随机播放
                                    val playSection = when (index) {
                                        0 -> {
                                            // Track
                                            station.randomTrack()
                                                ?.let { PlaySection(track = PlayItem.Track(sample = it)) }
                                        }

                                        1 -> {
                                            // Stinger
                                            station.randomStinger()
                                                ?.let { PlaySection(stinger = PlayItem.Stinger(sample = it)) }
                                        }

                                        2 -> {
                                            // DJ
                                            station.randomDj()
                                                ?.let { PlaySection(dj = PlayItem.Dj(sample = it)) }
                                        }

                                        else -> null
                                    } ?: return@TabRow

                                    Radio.beginSection(playSection)
                                } else {
                                    selectedTabIndex = index
                                }
                            },
                            modifier = Modifier
                                .padding(bottom = UiSpacing.Medium)
                                .padding(horizontal = UiSpacing.Medium),
                            minWidth = 80.dp,
                            height = 48.dp,
                            itemSpacing = UiSpacing.Medium,
                        )
                        AnimatedVisibility(scrollBehavior.state.collapsedFraction < 0.3f) {
                            Column {
                                Radio.trackPlaying?.let { track ->
                                    val currentPos = Radio.trackCurrentPos
                                    val isPlaying = track.durationMs > 0L
                                            && currentPos?.let { it < track.duration } ?: true
                                    if (isPlaying) {
                                        @Suppress("UNUSED_EXPRESSION")
                                        frameCount
                                        val progress = currentPos
                                            ?.let { (it / track.duration).toFloat().coerceIn(0f, 1f) }
                                            ?: 0f
                                        Text(
                                            text = "${track.displayName} - ${track.artist}",
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 6.dp),
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(6.dp),
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxHeight()
                                                    .fillMaxWidth(progress)
                                                    .background(colorScheme.primary),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
            )
        },
        floatingToolbar = {
            val barCornerRadius = 16.dp
            val buttonsPadding = 8.dp
            val buttonCornerRadius = barCornerRadius - buttonsPadding
            FloatingToolbar(
                modifier = Modifier.padding(bottom = 64.dp),
                cornerRadius = barCornerRadius,
            ) {
                Row(
                    modifier = Modifier.padding(buttonsPadding),
                ) {
                    IconButton(
                        onClick = {
                            scope.launch(Dispatchers.Default) {
                                Radio.stopPlayback()
                                Scheduler.cancel()
                            }
                        },
                        cornerRadius = buttonCornerRadius
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Stop,
                            contentDescription = "停止播放",
                        )
                    }
                    IconButton(
                        onClick = {
                            scope.launch(Dispatchers.Default) {
                                Radio.nextSection()
                            }
                        },
                        cornerRadius = buttonCornerRadius
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipNext,
                            contentDescription = "下一段",
                        )
                    }
                }
            }
        },
        floatingToolbarPosition = ToolbarPosition.BottomCenter,
    ) { contentPadding ->
        val bottomInnerPadding = bottomInnerPadding + 64.dp
        val station by remember { derivedStateOf { Radio.selectedStation } }
        val tracks by remember {
            derivedStateOf {
                station?.playableTracks(AppSettings.excludedTrackSuffixes) ?: emptyList()
            }
        }
        val stingers by remember { derivedStateOf { station?.stingers ?: emptyList() } }
        val djSamples by remember { derivedStateOf { station?.djSamples ?: emptyList() } }
        when (tabs[selectedTabIndex]) {
            SampleType.Track -> SampleCardList(
                samples = tracks,
                listState = trackListState,
                contentPadding = contentPadding,
                scrollBehavior = scrollBehavior,
                bottomInnerPadding = bottomInnerPadding,
                getPlaying = { Radio.trackPlaying },
                getCurrentPos = { Radio.trackCurrentPos },
                playSectionFactory = { PlaySection(track = PlayItem.Track(sample = it)) },
                errorLabel = SampleType.Track.toString(),
                displayContent = { sample, isPlaying ->
                    val color =
                        if (isPlaying) colorScheme.primary
                        else colorScheme.onSurface
                    Text(sample.displayName, fontWeight = FontWeight.SemiBold, color = color)
                    Text(sample.artist, fontWeight = FontWeight.Normal, color = color)
                    InfoLine("SoundName", sample.soundName)
                    InfoLine("BPM", sample.bpm.fmt())
                    InfoLine("Duration", sample.duration.format())
                    // InfoLine("SampleRate", sample.sampleRate.toString())
                },
            )

            SampleType.Stinger -> SampleCardList(
                samples = stingers,
                listState = stingerListState,
                contentPadding = contentPadding,
                scrollBehavior = scrollBehavior,
                bottomInnerPadding = bottomInnerPadding,
                getPlaying = { Radio.stingerPlaying },
                getCurrentPos = { Radio.stingerCurrentPos },
                playSectionFactory = { PlaySection(stinger = PlayItem.Stinger(sample = it)) },
                errorLabel = SampleType.Stinger.toString(),
                displayContent = { sample, isPlaying ->
                    Text(
                        sample.soundName, fontWeight = FontWeight.SemiBold,
                        color =
                            if (isPlaying) colorScheme.primary
                            else colorScheme.onSurface,
                    )
                    sample.startNextTrack?.let { InfoLine("StartNextTrack", it.format()) }
                    InfoLine("Duration", sample.duration.format())
                    // InfoLine("SampleRate", sample.sampleRate.toString())
                },
            )

            SampleType.DJ -> SampleCardList(
                samples = djSamples,
                listState = djListState,
                contentPadding = contentPadding,
                scrollBehavior = scrollBehavior,
                bottomInnerPadding = bottomInnerPadding,
                getPlaying = { Radio.djPlaying },
                getCurrentPos = { Radio.djCurrentPos },
                playSectionFactory = { PlaySection(dj = PlayItem.Dj(sample = it)) },
                errorLabel = SampleType.DJ.toString(),
                displayContent = { sample, isPlaying ->
                    Text(
                        sample.soundName, fontWeight = FontWeight.SemiBold,
                        color =
                            if (isPlaying) colorScheme.primary
                            else colorScheme.onSurface,
                    )
                    InfoLine("GameEvent", sample.gameEvent)
                    InfoLine("Duration", sample.duration.format())
                    // InfoLine("SampleRate", sample.sampleRate.toString())
                },
            )
        }
    }
}

@Composable
private fun <T : Sample> SampleCardList(
    samples: List<T>,
    listState: LazyListState,
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
    bottomInnerPadding: Dp,
    getPlaying: () -> T?,
    getCurrentPos: () -> Duration?,
    playSectionFactory: (T) -> PlaySection,
    errorLabel: String,
    displayContent: @Composable ColumnScope.(T, Boolean) -> Unit,
) {
    var frameCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameMillis {}
            frameCount++
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = contentPadding,
            scrollBehavior = scrollBehavior,
            state = listState,
            bottomInnerPadding = bottomInnerPadding,
            limitLandscapeWidth = false,
        ) {
            flowGrid(samples) { _, sample ->
                val isActiveCard = remember(getPlaying()) { getPlaying() == sample }
                Card(
                    modifier = Modifier
                        .clip(RoundedCornerShape(CardDefaults.CornerRadius))
                        .clickable {
                            runCatching {
                                Radio.beginSection(playSectionFactory(sample))
                            }.onFailure { e ->
                                AppRuntime.snackbar("failed to beginSection of $errorLabel: ${e.message ?: e.toString()}")
                            }
                        },
                ) {
                    val isPlaying = isActiveCard && sample.durationMs > 0L &&
                            getCurrentPos()?.let { it < sample.duration } ?: true
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (isPlaying) {
                            @Suppress("UNUSED_EXPRESSION")
                            frameCount
                            val progress = getCurrentPos()
                                ?.let { (it / sample.duration).toFloat().coerceIn(0f, 1f) }
                                ?: 0f
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(progress)
                                    .alpha(0.1f)
                                    .background(colorScheme.primary),
                            )
                        }
                        Column(modifier = Modifier.fillMaxWidth().padding(UiSpacing.Large)) {
                            displayContent(sample, isPlaying)
                        }
                        ActiveIcon(isPlaying)
                    }
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

@Composable
private fun InfoLine(label: String, value: String) {
    Text(
        text = "$label: $value",
        fontSize = 12.sp,
        color = colorScheme.onSurface.copy(alpha = 0.6f),
    )
}

@Composable
private fun BoxScope.ActiveIcon(visible: Boolean) {
    Box(
        modifier = Modifier
            .matchParentSize()
            .offset(18.dp, 26.dp),
        contentAlignment = Alignment.BottomEnd,
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + slideIn { IntOffset(it.width, it.height) },
            exit = fadeOut() + slideOut { IntOffset(it.width, it.height) },
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayCircleOutline,
                contentDescription = "播放中",
                modifier = Modifier.size(96.dp),
                tint = colorScheme.primary.copy(alpha = 0.25f),
            )
        }
    }
}
