package me.cniekirk.proxy

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.metroViewModel
import org.jetbrains.compose.resources.stringResource
import proxy.composeapp.generated.resources.*

private val AppColorScheme = lightColorScheme(
    primary = Color(0xFF2963C2),
    onPrimary = Color.White,
    surface = Color(0xFFF7F8FA),
    onSurface = Color(0xFF1F2328),
    surfaceVariant = Color(0xFFE8EBF0),
    onSurfaceVariant = Color(0xFF4C5562),
    outline = Color(0xFFD1D5DB),
    error = Color(0xFFB42318),
    onError = Color.White,
    errorContainer = Color(0xFFFFE4E4),
    onErrorContainer = Color(0xFF661B1B),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(10.dp),
    extraLarge = RoundedCornerShape(12.dp),
)

@Composable
fun AppEntryPoint(
    metroViewModelFactory: MetroViewModelFactory,
    onOpenRulesWindow: () -> Unit,
    onOpenSettingsWindow: () -> Unit,
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf(
        stringResource(Res.string.app_tab_sessions),
        stringResource(Res.string.app_tab_rules),
        stringResource(Res.string.app_tab_settings),
    )

    CompositionLocalProvider(LocalMetroViewModelFactory provides metroViewModelFactory) {
        CmpProxyTheme {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                CompactAppTabs(
                    tabs = tabs,
                    selectedTab = selectedTab,
                    onTabSelected = { tabIndex ->
                        when (tabIndex) {
                            RULES_TAB_INDEX -> onOpenRulesWindow()
                            SETTINGS_TAB_INDEX -> onOpenSettingsWindow()
                            else -> selectedTab = tabIndex
                        }
                    },
                )

                when (selectedTab) {
                    0 -> {
                        val viewModel = metroViewModel<SessionsViewModel>()
                        SessionsScreen(viewModel)
                    }
                    else -> Unit
                }
            }
        }
    }
}

@Composable
fun RulesWindowEntryPoint(
    metroViewModelFactory: MetroViewModelFactory,
    onCloseRequest: () -> Unit,
) {
    CompositionLocalProvider(LocalMetroViewModelFactory provides metroViewModelFactory) {
        CmpProxyTheme {
            val viewModel = metroViewModel<RulesViewModel>()
            RulesScreen(
                viewModel = viewModel,
                onCloseRequest = onCloseRequest,
            )
        }
    }
}

@Composable
fun SettingsWindowEntryPoint(
    metroViewModelFactory: MetroViewModelFactory,
) {
    CompositionLocalProvider(LocalMetroViewModelFactory provides metroViewModelFactory) {
        CmpProxyTheme {
            val viewModel = metroViewModel<SettingsViewModel>()
            SettingsScreen(viewModel)
        }
    }
}

@Composable
fun CmpProxyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        shapes = AppShapes,
        content = content,
    )
}

@Composable
private fun CompactAppTabs(
    tabs: List<String>,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            tabs.forEachIndexed { index, title ->
                val isSelected = selectedTab == index
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.surface
                    } else {
                        Color.Transparent
                    },
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.9f)
                        } else {
                            Color.Transparent
                        },
                    ),
                    modifier = Modifier.clickable { onTabSelected(index) },
                ) {
                    Text(
                        text = title,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f))
    }
}

private const val RULES_TAB_INDEX = 1
private const val SETTINGS_TAB_INDEX = 2
