package me.cniekirk.proxy

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import me.cniekirk.proxy.ui.CompactTextField
import org.orbitmvi.orbit.compose.collectAsState

@Composable
fun SettingsScreen(settingsViewModel: SettingsViewModel) {
    val state by settingsViewModel.collectAsState()
    var portField by remember(state.settings.proxy.port) { mutableStateOf(state.settings.proxy.port.toString()) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Proxy Settings",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )

        CompactTextField(
            value = portField,
            onValueChange = {
                portField = it
                it.toIntOrNull()?.let(settingsViewModel::updateProxyPort)
            },
            label = "Proxy port",
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Enable SSL decryption")
            Switch(
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
                "Root certificate generated."
            } else {
                "Root certificate not generated."
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        certificateState.fingerprint?.let { fingerprint ->
            Text("Fingerprint (SHA-256): $fingerprint", style = MaterialTheme.typography.bodySmall)
        }

        CertificateOnboardingCard(onboardingUrls = state.onboardingUrls)

        state.sslToggleError?.let { errorMessage ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
            ) {
                Text(
                    text = errorMessage,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                )
            }
        }
    }
}

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
                text = "Certificate Onboarding",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Scan the QR code from a mobile device on the same network, then install and trust the root certificate.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "QR target: $qrTargetUrl",
                style = MaterialTheme.typography.labelMedium,
            )

            if (qrCodeMatrix == null) {
                Text(
                    text = "Unable to generate QR code for the current onboarding URL.",
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
                label = "Friendly URL",
                url = onboardingUrls.friendlyUrl,
                onCopy = { clipboardManager.setText(AnnotatedString(onboardingUrls.friendlyUrl)) },
            )
            onboardingUrls.fallbackUrl
                ?.takeUnless { fallback -> fallback == onboardingUrls.friendlyUrl }
                ?.let { fallbackUrl ->
                    CopyableUrlRow(
                        label = "LAN fallback URL",
                        url = fallbackUrl,
                        onCopy = { clipboardManager.setText(AnnotatedString(fallbackUrl)) },
                    )
                }

            Text(
                text = "iOS trust steps",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "1. Open the URL and download/install the profile.\n" +
                    "2. In Settings > General > VPN & Device Management, install it.\n" +
                    "3. In Settings > General > About > Certificate Trust Settings, enable full trust.",
                style = MaterialTheme.typography.bodySmall,
            )

            Text(
                text = "Android trust steps",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "1. Open the URL and download the certificate.\n" +
                    "2. Go to Settings > Security (or Security & privacy) > Encryption & credentials.\n" +
                    "3. Install a CA certificate and select the downloaded file.",
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
            label = "Copy",
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
