@file:OptIn(top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi::class)

package io.github.miuzarte.fhradio.pages

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.miuzarte.fhradio.AppRuntime
import io.github.miuzarte.fhradio.PlayItem
import io.github.miuzarte.fhradio.PlaySection
import io.github.miuzarte.fhradio.Radio
import io.github.miuzarte.fhradio.constants.UiSpacing
import io.github.miuzarte.fhradio.model.SampleType
import io.github.miuzarte.fhradio.scaffolds.LazyColumn
import io.github.miuzarte.fhradio.scaffolds.flowGrid
import io.github.miuzarte.fhradio.util.fmt
import io.github.miuzarte.fhradio.util.format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

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
                    }
                },
            )
        },
        floatingToolbar = {
            FloatingToolbar(
                modifier = Modifier.padding(bottom = 64.dp),
                cornerRadius = 16.dp,
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                ) {
                    IconButton(onClick = {
                        scope.launch(Dispatchers.Default) {
                            Radio.stopPlayback()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Rounded.Stop,
                            contentDescription = "停止播放",
                        )
                    }
                }
            }
        },
        floatingToolbarPosition = ToolbarPosition.BottomCenter,
    ) { contentPadding ->
        val bottomInnerPadding = bottomInnerPadding + 64.dp
        when (tabs[selectedTabIndex]) {
            SampleType.Track -> TrackSampleList(
                contentPadding = contentPadding,
                scrollBehavior = scrollBehavior,
                bottomInnerPadding = bottomInnerPadding,
                listState = trackListState,
            )

            SampleType.Stinger -> StingerSampleList(
                contentPadding = contentPadding,
                scrollBehavior = scrollBehavior,
                bottomInnerPadding = bottomInnerPadding,
                listState = stingerListState,
            )

            SampleType.DJ -> DjSampleList(
                contentPadding = contentPadding,
                scrollBehavior = scrollBehavior,
                bottomInnerPadding = bottomInnerPadding,
                listState = djListState,
            )
        }
    }
}

@Composable
private fun TrackSampleList(
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
    bottomInnerPadding: Dp,
    listState: LazyListState,
) {
    val station = remember(Radio.selectedStation) { Radio.selectedStation }
    val tracks = remember(station?.tracks) { station?.tracks ?: emptyList() }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = contentPadding,
            scrollBehavior = scrollBehavior,
            state = listState,
            bottomInnerPadding = bottomInnerPadding,
            limitLandscapeWidth = false,
        ) {
            flowGrid(tracks) { _, track ->
                station ?: return@flowGrid
                Card(
                    modifier = Modifier
                        .clickable {
                            runCatching {
                                Radio.beginSection(PlaySection(track = PlayItem.Track(sample = track)))
                            }.onFailure { e ->
                                AppRuntime.snackbar("failed to beginSection of Track: ${e.message ?: e.toString()}")
                            }
                        },
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        val isPlaying = remember(Radio.trackPlaying) { Radio.trackPlaying == track }
                        Column(modifier = Modifier.fillMaxWidth().padding(UiSpacing.Large)) {
                            val color =
                                if (isPlaying) colorScheme.primary
                                else colorScheme.onSurface
                            Text(
                                track.displayName,
                                fontWeight = FontWeight.SemiBold,
                                color = color,
                            )
                            Text(
                                track.artist,
                                fontWeight = FontWeight.Normal,
                                color = color,
                            )
                            InfoLine("SoundName", track.soundName)
                            InfoLine("Duration", track.duration.format())
                            InfoLine("SampleRate", "${track.sampleRate}Hz")
                            InfoLine("BPM", track.bpm.fmt())
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
private fun StingerSampleList(
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
    bottomInnerPadding: Dp,
    listState: LazyListState,
) {
    val station = remember(Radio.selectedStation) { Radio.selectedStation }
    val stingers = remember(station?.stingers) { station?.stingers ?: emptyList() }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = contentPadding,
            scrollBehavior = scrollBehavior,
            state = listState,
            bottomInnerPadding = bottomInnerPadding,
            limitLandscapeWidth = false,
        ) {
            flowGrid(stingers) { _, stinger ->
                station ?: return@flowGrid
                Card(
                    modifier = Modifier
                        .clickable {
                            runCatching {
                                Radio.beginSection(PlaySection(stinger = PlayItem.Stinger(sample = stinger)))
                            }.onFailure { e ->
                                AppRuntime.snackbar("failed to beginSection of Stinger: ${e.message ?: e.toString()}")
                            }
                        },
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        val isPlaying = remember(Radio.trackPlaying) { Radio.stingerPlaying == stinger }
                        Column(modifier = Modifier.fillMaxWidth().padding(UiSpacing.Large)) {
                            Text(
                                text = stinger.soundName,
                                fontWeight = FontWeight.SemiBold,
                                color =
                                    if (isPlaying) colorScheme.primary
                                    else colorScheme.onSurface,
                            )
                            stinger.startNextTrack?.let { InfoLine("StartNextTrack", it.format()) }
                            InfoLine("Duration", stinger.duration.format())
                            InfoLine("SampleRate", "${stinger.sampleRate}Hz")
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
private fun DjSampleList(
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
    bottomInnerPadding: Dp,
    listState: LazyListState,
) {
    val station = remember(Radio.selectedStation) { Radio.selectedStation }
    val djSamples = remember(station?.djSamples) { station?.djSamples ?: emptyList() }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = contentPadding,
            scrollBehavior = scrollBehavior,
            state = listState,
            bottomInnerPadding = bottomInnerPadding,
            limitLandscapeWidth = false,
        ) {
            flowGrid(djSamples) { _, dj ->
                station ?: return@flowGrid
                Card(
                    modifier = Modifier
                        .clickable {
                            runCatching {
                                Radio.beginSection(PlaySection(dj = PlayItem.Dj(sample = dj)))
                            }.onFailure { e ->
                                AppRuntime.snackbar("failed to beginSection of DJ: ${e.message ?: e.toString()}")
                            }
                        },
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        val isPlaying = remember(Radio.trackPlaying) { Radio.djPlaying == dj }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(UiSpacing.Large),
                        ) {
                            Text(
                                text = dj.soundName,
                                fontWeight = FontWeight.SemiBold,
                                color =
                                    if (isPlaying) colorScheme.primary
                                    else colorScheme.onSurface,
                            )
                            InfoLine("Duration", dj.duration.format())
                            InfoLine("SampleRate", "${dj.sampleRate}Hz")
                            InfoLine("GameEvent", dj.gameEvent)
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
