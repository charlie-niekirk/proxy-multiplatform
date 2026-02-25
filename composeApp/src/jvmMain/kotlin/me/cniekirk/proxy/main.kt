package me.cniekirk.proxy

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.cmp.proxy.app.di.CmpProxyAppGraph
import dev.zacsweers.metro.createGraph
import org.jetbrains.compose.resources.stringResource
import proxy.composeapp.generated.resources.*

fun main() = application {
    val appGraph = remember { createGraph<CmpProxyAppGraph>() }
    var isRulesWindowOpen by remember { mutableStateOf(false) }

    Window(
        onCloseRequest = ::exitApplication,
        title = stringResource(Res.string.app_window_title_main),
        state = rememberWindowState(size = DpSize(width = 1420.dp, height = 860.dp)),
    ) {
        AppEntryPoint(
            metroViewModelFactory = appGraph.metroViewModelFactory,
            onOpenRulesWindow = { isRulesWindowOpen = true },
        )
    }

    if (isRulesWindowOpen) {
        Window(
            onCloseRequest = { isRulesWindowOpen = false },
            title = stringResource(Res.string.app_window_title_rules),
            state = rememberWindowState(size = DpSize(width = 1260.dp, height = 860.dp)),
        ) {
            RulesWindowEntryPoint(
                metroViewModelFactory = appGraph.metroViewModelFactory,
                onCloseRequest = { isRulesWindowOpen = false },
            )
        }
    }
}
