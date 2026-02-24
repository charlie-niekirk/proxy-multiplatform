package me.cniekirk.proxy

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.metroViewModel

@Composable
fun AppEntryPoint(metroViewModelFactory: MetroViewModelFactory) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Sessions", "Rules", "Settings")

    CompositionLocalProvider(LocalMetroViewModelFactory provides metroViewModelFactory) {
        MaterialTheme(
            typography = MaterialTheme.typography.withFontFamily(FontFamily.Monospace),
        ) {
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
                    0 -> {
                        val viewModel = metroViewModel<SessionsViewModel>()
                        SessionsScreen(viewModel)
                    }
                    1 -> PlaceholderScreen("Rule CRUD UI will be implemented in milestone 5.")
                    else -> {
                        val viewModel = metroViewModel<SettingsViewModel>()
                        SettingsScreen(viewModel)
                    }
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

private fun androidx.compose.material3.Typography.withFontFamily(fontFamily: FontFamily) =
    copy(
        displayLarge = displayLarge.copy(fontFamily = fontFamily),
        displayMedium = displayMedium.copy(fontFamily = fontFamily),
        displaySmall = displaySmall.copy(fontFamily = fontFamily),
        headlineLarge = headlineLarge.copy(fontFamily = fontFamily),
        headlineMedium = headlineMedium.copy(fontFamily = fontFamily),
        headlineSmall = headlineSmall.copy(fontFamily = fontFamily),
        titleLarge = titleLarge.copy(fontFamily = fontFamily),
        titleMedium = titleMedium.copy(fontFamily = fontFamily),
        titleSmall = titleSmall.copy(fontFamily = fontFamily),
        bodyLarge = bodyLarge.copy(fontFamily = fontFamily),
        bodyMedium = bodyMedium.copy(fontFamily = fontFamily),
        bodySmall = bodySmall.copy(fontFamily = fontFamily),
        labelLarge = labelLarge.copy(fontFamily = fontFamily),
        labelMedium = labelMedium.copy(fontFamily = fontFamily),
        labelSmall = labelSmall.copy(fontFamily = fontFamily),
    )
