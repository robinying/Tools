package com.robin.tools.feature.lightlux.presentation

import androidx.compose.runtime.*
import com.robin.tools.feature.lightlux.data.*

@Composable
fun LightLuxScreen(
    mainViewModel: MainViewModel,
    snapshotViewModel: SnapshotListViewModel,
    onBack: () -> Unit = {}
) {
    var currentScreen by remember { mutableStateOf<LightLuxNavHost>(LightLuxNavHost.Meter) }

    when (currentScreen) {
        is LightLuxNavHost.Meter -> {
            LightMeterScreen(
                viewModel = mainViewModel,
                onNavigateToSnapshots = { currentScreen = LightLuxNavHost.SnapshotList },
                onBack = onBack
            )
        }
        is LightLuxNavHost.SnapshotList -> {
            SnapshotListScreen(
                viewModel = snapshotViewModel,
                onBack = { currentScreen = LightLuxNavHost.Meter }
            )
        }
    }
}
