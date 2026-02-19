package me.cniekirk.proxy

interface TlsService {
    suspend fun ensureCertificateMaterial()
}