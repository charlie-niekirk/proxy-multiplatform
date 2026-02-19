package me.cniekirk.proxy

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.metroViewModel

@Composable
fun AppEntryPoint(metroViewModelFactory: MetroViewModelFactory) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Sessions", "Rules", "Settings")

    CompositionLocalProvider(LocalMetroViewModelFactory provides metroViewModelFactory) {
        Column(modifier = Modifier.fillMaxSize()) {
            SecondaryTabRow(
                selectedTab,
                tabs = {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) },
                        )
                    }
                })

            when (selectedTab) {
                0 -> PlaceholderScreen("Session capture UI will be implemented in milestone 2.")
                1 -> PlaceholderScreen("Rule CRUD UI will be implemented in milestone 5.")
                else -> {
                    val viewModel = metroViewModel<SettingsViewModel>()
                    SettingsScreen(viewModel)
                }
            }
        }
    }
}

@Composable
fun PlaceholderScreen(label: String) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(label)
    }
}