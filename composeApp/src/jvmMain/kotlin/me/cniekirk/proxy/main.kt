package me.cniekirk.proxy

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.cmp.proxy.app.di.CmpProxyAppGraph
import dev.zacsweers.metro.createGraph

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "proxy_multiplatform",
    ) {
        val appGraph = createGraph<CmpProxyAppGraph>()
        AppEntryPoint(appGraph.metroViewModelFactory)
    }
}