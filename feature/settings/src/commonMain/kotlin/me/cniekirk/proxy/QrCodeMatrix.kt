package me.cniekirk.proxy

data class QrCodeMatrix(
    val size: Int,
    val modules: BooleanArray,
) {
    init {
        require(size > 0) { "QR matrix size must be greater than zero." }
        require(modules.size == size * size) { "QR matrix module count does not match the matrix size." }
    }

    operator fun get(x: Int, y: Int): Boolean {
        return modules[(y * size) + x]
    }
}

expect fun generateQrCodeMatrix(payload: String): QrCodeMatrix?
