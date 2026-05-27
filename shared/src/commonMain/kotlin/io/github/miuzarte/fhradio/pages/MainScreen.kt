package io.github.miuzarte.fhradio.pages

import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import io.github.miuzarte.fhradio.AppRuntime
import io.github.miuzarte.fhradio.AppSettings
import io.github.miuzarte.fhradio.Radio
import io.github.miuzarte.fhradio.constants.UiMotion
import kotlinx.coroutines.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

enum class MainTab(val label: String, val icon: ImageVector) {
    Radio("电台", Icons.Rounded.Radio),
    Tracks("曲目", Icons.AutoMirrored.Rounded.QueueMusic),
    Settings("设置", Icons.Rounded.Settings),
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun MainScreen() {
    DisposableEffect(Unit) {
        val job = AppSettings.restoreFromPaths()
        val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        scope.launch {
            job?.join()
            val sourceStations = AppSettings.radioSources

            if (!AppSettings.autoResume) return@launch
            val xmlPath = AppSettings.lastStationXmlPath ?: return@launch
            val name = AppSettings.lastStationName ?: return@launch
            val station = sourceStations[xmlPath]?.find { it.name == name } ?: return@launch
            Radio.setStation(station)
        }

        onDispose {
            scope.cancel()
        }
    }

    val snackHostState = remember { SnackbarHostState() }
    DisposableEffect(snackHostState) {
        val unregister = AppRuntime.registerSnackbarHostState(snackHostState)
        onDispose(unregister)
    }

    val scope = rememberCoroutineScope()
    val tabs = remember { MainTab.entries }
    var selectedTab by remember { mutableStateOf(MainTab.Radio) }

    val pagerState = rememberPagerState(
        initialPage = MainTab.Radio.ordinal,
        pageCount = { tabs.size },
    )
    var pagerNavigationJob by remember { mutableStateOf<Job?>(null) }
    var isPagerNavigating by remember { mutableStateOf(false) }

    val radioScrollBehavior = MiuixScrollBehavior(
        canScroll = { selectedTab == MainTab.Radio },
    )
    val tracksScrollBehavior = MiuixScrollBehavior(
        canScroll = { selectedTab == MainTab.Tracks },
    )
    val settingsScrollBehavior = MiuixScrollBehavior(
        canScroll = { selectedTab == MainTab.Settings },
    )

    fun navigateToTab(tab: MainTab) {
        val targetIndex = tab.ordinal
        if (targetIndex == selectedTab.ordinal) return
        pagerNavigationJob?.cancel()
        selectedTab = tab
        isPagerNavigating = true
        scope.launch {
            val job = coroutineContext[Job]
            pagerNavigationJob = job
            try {
                pagerState.animateScrollToPage(
                    page = targetIndex,
                    animationSpec = spring(
                        dampingRatio = UiMotion.PAGE_SWITCH_DAMPING_RATIO,
                        stiffness = UiMotion.PAGE_SWITCH_STIFFNESS,
                    ),
                )
            } finally {
                if (pagerNavigationJob == job) {
                    isPagerNavigating = false
                    pagerNavigationJob = null
                    if (pagerState.currentPage != targetIndex) {
                        selectedTab = tabs[pagerState.currentPage]
                    }
                }
            }
        }
    }

    fun handleBackNavigation() {
        if (selectedTab != MainTab.Radio) {
            navigateToTab(MainTab.Radio)
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        if (!isPagerNavigating && selectedTab.ordinal != pagerState.currentPage) {
            selectedTab = tabs[pagerState.currentPage]
        }
    }

    MiuixTheme {
        Scaffold(
            modifier = Modifier
                .onPreviewKeyEvent { event ->
                    if (
                        event.key == Key.Escape &&
                        event.type == KeyEventType.KeyUp
                    ) {
                        handleBackNavigation()
                        true
                    } else false
                },
            snackbarHost = { SnackbarHost(snackHostState) },
            bottomBar = {
                NavigationBar(color = colorScheme.surface) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = selectedTab == tab,
                            onClick = { navigateToTab(tab) },
                            icon = tab.icon,
                            label = tab.label,
                        )
                    }
                }
            },
        ) { contentPadding ->
            val bottomInnerPadding = contentPadding.calculateBottomPadding()

            HorizontalPager(
                modifier = Modifier.fillMaxSize(),
                state = pagerState,
                beyondViewportPageCount = 1,
            ) { page ->
                when (tabs[page]) {
                    MainTab.Radio -> RadiosScreen(
                        scrollBehavior = radioScrollBehavior,
                        bottomInnerPadding = bottomInnerPadding,
                    )

                    MainTab.Tracks -> TracksScreen(
                        scrollBehavior = tracksScrollBehavior,
                        bottomInnerPadding = bottomInnerPadding,
                    )

                    MainTab.Settings -> SettingsScreen(
                        scrollBehavior = settingsScrollBehavior,
                        bottomInnerPadding = bottomInnerPadding,
                    )
                }
            }
        }
    }
}
