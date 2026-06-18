package com.robin.tools.feature.lightlux.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.robin.tools.core.widget.SwipeBackContainer
import com.robin.tools.feature.lightlux.data.*

@Composable
fun LightLuxScreen(
    mainViewModel: MainViewModel,
    snapshotViewModel: SnapshotListViewModel,
    onBack: () -> Unit = {}
) {
    // 使用 rememberSaveable 让导航状态在配置变更（如旋转屏幕）后保留；
    // sealed object 不可直接 saveable，用基于类名的 Saver 保存。
    var currentScreen by rememberSaveable(stateSaver = LightLuxNavHostSaver) {
        mutableStateOf<LightLuxNavHost>(LightLuxNavHost.Meter)
    }

    when (currentScreen) {
        is LightLuxNavHost.Meter -> {
            // 系统返回键兜底：与右滑一致，回 Home
            BackHandler { onBack() }
            SwipeBackContainer(onBack = onBack) {
                LightMeterScreen(
                    viewModel = mainViewModel,
                    onNavigateToSnapshots = { currentScreen = LightLuxNavHost.SnapshotList },
                    onBack = onBack
                )
            }
        }
        is LightLuxNavHost.SnapshotList -> {
            // 系统返回键兜底：从快照列表返回测光页，而非退出整个 lightlux
            BackHandler { currentScreen = LightLuxNavHost.Meter }
            SwipeBackContainer(onBack = { currentScreen = LightLuxNavHost.Meter }) {
                SnapshotListScreen(
                    viewModel = snapshotViewModel,
                    onBack = { currentScreen = LightLuxNavHost.Meter }
                )
            }
        }
    }
}

/** 把 [LightLuxNavHost] 的两个 object 子类映射为字符串保存，便于 rememberSaveable。 */
private val LightLuxNavHostSaver = androidx.compose.runtime.saveable.Saver<LightLuxNavHost, String>(
    save = { it::class.java.name },
    restore = { name ->
        when (name) {
            LightLuxNavHost.SnapshotList::class.java.name -> LightLuxNavHost.SnapshotList
            else -> LightLuxNavHost.Meter
        }
    }
)
