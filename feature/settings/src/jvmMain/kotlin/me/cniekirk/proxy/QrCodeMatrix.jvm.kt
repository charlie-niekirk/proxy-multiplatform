package me.cniekirk.proxy

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

actual fun generateQrCodeMatrix(payload: String): QrCodeMatrix? {
    if (payload.isBlank()) {
        return null
    }

    val hints = mapOf(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.MARGIN to QR_MARGIN_MODULES,
    )

    val matrix = runCatching {
        QRCodeWriter().encode(
            payload,
            BarcodeFormat.QR_CODE,
            QR_SIZE_PIXELS,
            QR_SIZE_PIXELS,
            hints,
        )
    }.getOrNull() ?: return null

    val size = matrix.width
    val modules = BooleanArray(size * size)
    for (y in 0 until size) {
        for (x in 0 until size) {
            modules[(y * size) + x] = matrix[x, y]
        }
    }

    return QrCodeMatrix(
        size = size,
        modules = modules,
    )
}

private const val QR_SIZE_PIXELS = 256
private const val QR_MARGIN_MODULES = 1
