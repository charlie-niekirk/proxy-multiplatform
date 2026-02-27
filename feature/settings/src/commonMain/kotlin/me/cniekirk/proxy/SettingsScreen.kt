package me.cniekirk.proxy

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.cniekirk.proxy.ui.CompactButton
import me.cniekirk.proxy.ui.CompactButtonStyle
import me.cniekirk.proxy.ui.CompactSwitch
import me.cniekirk.proxy.ui.CompactTextField
import org.jetbrains.compose.resources.stringResource
import org.orbitmvi.orbit.compose.collectAsState
import proxy.feature.settings.generated.resources.*

@Composable
fun SettingsScreen(settingsViewModel: SettingsViewModel) {
    val state by settingsViewModel.collectAsState()
    var portField by remember(state.settings.proxy.port) { mutableStateOf(state.settings.proxy.port.toString()) }
    var selectedTab by remember { mutableStateOf(SettingsTab.Proxy) }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxHeight()
                .width(150.dp),
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                SettingsTabRow(
                    title = stringResource(Res.string.settings_tab_proxy),
                    selected = selectedTab == SettingsTab.Proxy,
                    onClick = { selectedTab = SettingsTab.Proxy },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                SettingsTabRow(
                    title = stringResource(Res.string.settings_tab_ssl),
                    selected = selectedTab == SettingsTab.Ssl,
                    onClick = { selectedTab = SettingsTab.Ssl },
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (selectedTab) {
                SettingsTab.Proxy -> {
                    Text(
                        text = stringResource(Res.string.settings_title_proxy),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    CompactTextField(
                        value = portField,
                        onValueChange = {
                            portField = it
                            it.toIntOrNull()?.let(settingsViewModel::updateProxyPort)
                        },
                        label = stringResource(Res.string.settings_label_proxy_port),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
                SettingsTab.Ssl -> {
                    Text(
                        text = stringResource(Res.string.settings_title_ssl),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(Res.string.settings_label_enable_ssl_decryption))
                        CompactSwitch(
                            checked = state.settings.proxy.sslDecryptionEnabled,
                            enabled = !state.isProvisioningCertificate,
                            onCheckedChange = settingsViewModel::toggleSslDecryption,
                        )
                        if (state.isProvisioningCertificate) {
                            CircularProgressIndicator()
                        }
                    }

                    val certificateState = state.settings.certificate
                    Text(
                        text = if (certificateState.generated) {
                            stringResource(Res.string.settings_certificate_generated)
                        } else {
                            stringResource(Res.string.settings_certificate_not_generated)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    certificateState.fingerprint?.let { fingerprint ->
                        Text(
                            text = stringResource(Res.string.settings_certificate_fingerprint, fingerprint),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    CertificateOnboardingCard(onboardingUrls = state.onboardingUrls)

                    state.sslToggleError?.let { errorMessage ->
                        val resolvedError = errorMessage.ifBlank {
                            stringResource(Res.string.settings_error_certificate_generation_failed)
                        }
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                            ),
                        ) {
                            Text(
                                text = resolvedError,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsTabRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val backgroundColor = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = SELECTED_TAB_BACKGROUND_ALPHA)
    } else {
        Color.Transparent
    }
    Text(
        text = title,
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
    )
}

private enum class SettingsTab {
    Proxy,
    Ssl,
}

private const val SELECTED_TAB_BACKGROUND_ALPHA = 0.14f

@Composable
private fun CertificateOnboardingCard(onboardingUrls: CertificateOnboardingUrls) {
    val clipboardManager = LocalClipboardManager.current
    val qrTargetUrl = onboardingUrls.fallbackUrl ?: onboardingUrls.friendlyUrl
    val qrCodeMatrix = remember(qrTargetUrl) {
        generateQrCodeMatrix(qrTargetUrl)
    }

    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(Res.string.settings_certificate_onboarding_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(Res.string.settings_certificate_onboarding_description),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = stringResource(Res.string.settings_certificate_qr_target, qrTargetUrl),
                style = MaterialTheme.typography.labelMedium,
            )

            if (qrCodeMatrix == null) {
                Text(
                    text = stringResource(Res.string.settings_certificate_qr_error),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                QrCodeMatrixView(
                    matrix = qrCodeMatrix,
                    modifier = Modifier.size(220.dp),
                )
            }

            CopyableUrlRow(
                label = stringResource(Res.string.settings_certificate_friendly_url),
                url = onboardingUrls.friendlyUrl,
                onCopy = { clipboardManager.setText(AnnotatedString(onboardingUrls.friendlyUrl)) },
            )
            onboardingUrls.fallbackUrl
                ?.takeUnless { fallback -> fallback == onboardingUrls.friendlyUrl }
                ?.let { fallbackUrl ->
                    CopyableUrlRow(
                        label = stringResource(Res.string.settings_certificate_fallback_url),
                        url = fallbackUrl,
                        onCopy = { clipboardManager.setText(AnnotatedString(fallbackUrl)) },
                    )
                }

            Text(
                text = stringResource(Res.string.settings_ios_trust_steps_title),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(Res.string.settings_ios_trust_steps_body),
                style = MaterialTheme.typography.bodySmall,
            )

            Text(
                text = stringResource(Res.string.settings_android_trust_steps_title),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(Res.string.settings_android_trust_steps_body),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun CopyableUrlRow(
    label: String,
    url: String,
    onCopy: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(0.8f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = url,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        CompactButton(
            label = stringResource(Res.string.settings_action_copy),
            onClick = onCopy,
            style = CompactButtonStyle.Text,
        )
    }
}

@Composable
private fun QrCodeMatrixView(
    matrix: QrCodeMatrix,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .background(
                color = Color.White,
                shape = RoundedCornerShape(12.dp),
            )
            .padding(12.dp),
    ) {
        drawRect(color = Color.White)
        val moduleSize = size.minDimension / matrix.size.toFloat()
        for (y in 0 until matrix.size) {
            for (x in 0 until matrix.size) {
                if (matrix[x, y]) {
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(
                            x = x * moduleSize,
                            y = y * moduleSize,
                        ),
                        size = Size(
                            width = moduleSize,
                            height = moduleSize,
                        ),
                    )
                }
            }
        }
    }
}
