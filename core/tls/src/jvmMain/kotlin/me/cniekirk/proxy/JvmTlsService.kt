package me.cniekirk.proxy

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.ExtendedKeyUsage
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.asn1.x509.KeyPurposeId
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.math.BigInteger
import java.net.IDN
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Security
import java.security.cert.X509Certificate
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext

@ContributesBinding(AppScope::class, binding = binding<TlsService>())
@SingleIn(AppScope::class)
@Inject
class JvmTlsService(
    private val settingsRepository: SettingsRepository,
) : TlsService {

    private val materialMutex = Mutex()
    private val secureRandom = SecureRandom()
    private val hostCertificateCache = ConcurrentHashMap<String, HostCertificateMaterial>()

    @Volatile
    private var cachedRootMaterial: RootCertificateMaterial? = null

    override suspend fun ensureCertificateMaterial() {
        val rootMaterial = loadOrCreateRootMaterial()
        persistCertificateMetadata(rootMaterial.certificate)
    }

    override suspend fun readRootCertificatePem(): ByteArray? {
        return if (ROOT_CERTIFICATE_PEM_FILE.exists()) {
            ROOT_CERTIFICATE_PEM_FILE.readBytes()
        } else {
            null
        }
    }

    suspend fun createMitmServerContext(hostname: String): SSLContext {
        val normalizedHost = normalizeHost(hostname)
        val rootMaterial = loadOrCreateRootMaterial()
        val hostMaterial = hostCertificateCache.computeIfAbsent(normalizedHost) {
            createHostCertificate(hostname = normalizedHost, rootMaterial = rootMaterial)
        }
        return createServerSslContext(
            hostMaterial = hostMaterial,
            rootCertificate = rootMaterial.certificate,
        )
    }

    private suspend fun loadOrCreateRootMaterial(): RootCertificateMaterial {
        cachedRootMaterial?.let { return it }

        return materialMutex.withLock {
            cachedRootMaterial?.let { return it }
            ensureBouncyCastleProviderInstalled()

            CERTIFICATE_DIRECTORY.mkdirs()

            val rootMaterial = if (ROOT_KEYSTORE_FILE.exists()) {
                runCatching { loadRootMaterialFromDisk() }
                    .getOrElse { error ->
                        logWarn("Failed to load existing root certificate; regenerating: ${error.message}")
                        ROOT_KEYSTORE_FILE.delete()
                        ROOT_CERTIFICATE_PEM_FILE.delete()
                        createAndPersistRootMaterial()
                    }
            } else {
                createAndPersistRootMaterial()
            }

            cachedRootMaterial = rootMaterial
            rootMaterial
        }
    }

    private fun createAndPersistRootMaterial(): RootCertificateMaterial {
        val keyPair = generateRsaKeyPair()
        val certificate = createRootCertificate(keyPair)

        val keyStore = KeyStore.getInstance(KEYSTORE_TYPE).apply {
            load(null, null)
            setKeyEntry(ROOT_CERTIFICATE_ALIAS, keyPair.private, KEYSTORE_PASSWORD, arrayOf(certificate))
        }
        FileOutputStream(ROOT_KEYSTORE_FILE).use { output ->
            keyStore.store(output, KEYSTORE_PASSWORD)
        }

        FileWriter(ROOT_CERTIFICATE_PEM_FILE).use { writer ->
            JcaPEMWriter(writer).use { pemWriter ->
                pemWriter.writeObject(certificate)
            }
        }

        logInfo("Generated root certificate at ${ROOT_CERTIFICATE_PEM_FILE.absolutePath}")
        return RootCertificateMaterial(
            privateKey = keyPair.private,
            certificate = certificate,
        )
    }

    private fun loadRootMaterialFromDisk(): RootCertificateMaterial {
        val keyStore = KeyStore.getInstance(KEYSTORE_TYPE)
        ROOT_KEYSTORE_FILE.inputStream().use { input ->
            keyStore.load(input, KEYSTORE_PASSWORD)
        }

        val privateKey = keyStore.getKey(ROOT_CERTIFICATE_ALIAS, KEYSTORE_PASSWORD) as? PrivateKey
            ?: error("Missing root private key in keystore.")
        val certificate = keyStore.getCertificate(ROOT_CERTIFICATE_ALIAS) as? X509Certificate
            ?: error("Missing root certificate in keystore.")

        return RootCertificateMaterial(
            privateKey = privateKey,
            certificate = certificate,
        )
    }

    private fun createRootCertificate(keyPair: KeyPair): X509Certificate {
        val now = Instant.now()
        val notBefore = Date.from(now.minus(1, ChronoUnit.DAYS))
        val notAfter = Date.from(now.plus(ROOT_CERTIFICATE_VALIDITY_DAYS, ChronoUnit.DAYS))
        val subject = X500Name(ROOT_CERTIFICATE_SUBJECT)

        val builder = JcaX509v3CertificateBuilder(
            subject,
            BigInteger(160, secureRandom).abs(),
            notBefore,
            notAfter,
            subject,
            keyPair.public,
        )

        val extensionUtils = JcaX509ExtensionUtils()
        builder.addExtension(Extension.basicConstraints, true, BasicConstraints(true))
        builder.addExtension(
            Extension.keyUsage,
            true,
            KeyUsage(KeyUsage.keyCertSign or KeyUsage.cRLSign),
        )
        builder.addExtension(
            Extension.subjectKeyIdentifier,
            false,
            extensionUtils.createSubjectKeyIdentifier(keyPair.public),
        )
        builder.addExtension(
            Extension.authorityKeyIdentifier,
            false,
            extensionUtils.createAuthorityKeyIdentifier(keyPair.public),
        )

        val signer = JcaContentSignerBuilder(SIGNING_ALGORITHM)
            .setProvider(BOUNCY_CASTLE_PROVIDER)
            .build(keyPair.private)

        return JcaX509CertificateConverter()
            .setProvider(BOUNCY_CASTLE_PROVIDER)
            .getCertificate(builder.build(signer))
    }

    private fun createHostCertificate(
        hostname: String,
        rootMaterial: RootCertificateMaterial,
    ): HostCertificateMaterial {
        val hostKeyPair = generateRsaKeyPair()
        val rootIssuerName = X500Name.getInstance(rootMaterial.certificate.subjectX500Principal.encoded)
        val now = Instant.now()
        val notBefore = Date.from(now.minus(5, ChronoUnit.MINUTES))
        val notAfter = Date.from(now.plus(HOST_CERTIFICATE_VALIDITY_DAYS, ChronoUnit.DAYS))

        val builder = JcaX509v3CertificateBuilder(
            rootIssuerName,
            BigInteger(160, secureRandom).abs(),
            notBefore,
            notAfter,
            X500Name("CN=$hostname"),
            hostKeyPair.public,
        )

        val extensionUtils = JcaX509ExtensionUtils()
        val subjectAlternativeNames = GeneralNames(
            arrayOf(GeneralName(GeneralName.dNSName, hostname)),
        )

        builder.addExtension(Extension.basicConstraints, true, BasicConstraints(false))
        builder.addExtension(
            Extension.keyUsage,
            true,
            KeyUsage(KeyUsage.digitalSignature or KeyUsage.keyEncipherment),
        )
        builder.addExtension(
            Extension.extendedKeyUsage,
            false,
            ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth),
        )
        builder.addExtension(Extension.subjectAlternativeName, false, subjectAlternativeNames)
        builder.addExtension(
            Extension.subjectKeyIdentifier,
            false,
            extensionUtils.createSubjectKeyIdentifier(hostKeyPair.public),
        )
        builder.addExtension(
            Extension.authorityKeyIdentifier,
            false,
            extensionUtils.createAuthorityKeyIdentifier(rootMaterial.certificate),
        )

        val signer = JcaContentSignerBuilder(SIGNING_ALGORITHM)
            .setProvider(BOUNCY_CASTLE_PROVIDER)
            .build(rootMaterial.privateKey)

        val hostCertificate = JcaX509CertificateConverter()
            .setProvider(BOUNCY_CASTLE_PROVIDER)
            .getCertificate(builder.build(signer))

        return HostCertificateMaterial(
            privateKey = hostKeyPair.private,
            certificate = hostCertificate,
        )
    }

    private fun createServerSslContext(
        hostMaterial: HostCertificateMaterial,
        rootCertificate: X509Certificate,
    ): SSLContext {
        val keyStore = KeyStore.getInstance(KEYSTORE_TYPE).apply {
            load(null, null)
            setKeyEntry(HOST_CERTIFICATE_ALIAS, hostMaterial.privateKey, EMPTY_KEY_PASSWORD, arrayOf(hostMaterial.certificate, rootCertificate))
        }

        val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
            init(keyStore, EMPTY_KEY_PASSWORD)
        }

        return SSLContext.getInstance(TLS_PROTOCOL).apply {
            init(keyManagerFactory.keyManagers, null, secureRandom)
        }
    }

    private suspend fun persistCertificateMetadata(certificate: X509Certificate) {
        val fingerprint = sha256Fingerprint(certificate.encoded)
        val createdAtEpochMillis = certificate.notBefore.time

        settingsRepository.updateCertificateState { current ->
            current.copy(
                generated = true,
                fingerprint = fingerprint,
                createdAtEpochMillis = createdAtEpochMillis,
            )
        }
    }

    private fun normalizeHost(hostname: String): String {
        val trimmed = hostname.trim().trimEnd('.')
        require(trimmed.isNotEmpty()) { "CONNECT target host is blank." }
        return IDN.toASCII(trimmed, IDN.ALLOW_UNASSIGNED).lowercase(Locale.US)
    }

    private fun generateRsaKeyPair(): KeyPair {
        return KeyPairGenerator.getInstance(KEY_ALGORITHM).apply {
            initialize(KEY_SIZE_BITS, secureRandom)
        }.genKeyPair()
    }

    private fun sha256Fingerprint(encoded: ByteArray): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(encoded)
            .joinToString(separator = ":") { byte ->
                "%02X".format(byte.toInt() and BYTE_MASK)
            }
    }

    private fun ensureBouncyCastleProviderInstalled() {
        if (Security.getProvider(BOUNCY_CASTLE_PROVIDER) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    private fun logInfo(message: String) {
        println("$LOG_PREFIX $message")
    }

    private fun logWarn(message: String) {
        println("$LOG_PREFIX WARN: $message")
    }

    private data class RootCertificateMaterial(
        val privateKey: PrivateKey,
        val certificate: X509Certificate,
    )

    private data class HostCertificateMaterial(
        val privateKey: PrivateKey,
        val certificate: X509Certificate,
    )

    private companion object {
        const val LOG_PREFIX = "[cmp-proxy]"

        const val BOUNCY_CASTLE_PROVIDER = "BC"
        const val TLS_PROTOCOL = "TLS"
        const val SIGNING_ALGORITHM = "SHA256withRSA"
        const val KEY_ALGORITHM = "RSA"
        const val KEY_SIZE_BITS = 2048
        const val BYTE_MASK = 0xFF

        const val ROOT_CERTIFICATE_VALIDITY_DAYS = 3_650L
        const val HOST_CERTIFICATE_VALIDITY_DAYS = 90L
        const val ROOT_CERTIFICATE_SUBJECT = "CN=CMP Proxy Root CA, O=CMP Proxy, OU=Development"

        const val KEYSTORE_TYPE = "PKCS12"
        const val ROOT_CERTIFICATE_ALIAS = "cmp-proxy-root"
        const val HOST_CERTIFICATE_ALIAS = "cmp-proxy-host"

        val KEYSTORE_PASSWORD = "cmp-proxy".toCharArray()
        val EMPTY_KEY_PASSWORD = CharArray(0)

        val CERTIFICATE_DIRECTORY = File(System.getProperty("user.home"), ".cmp-proxy/certs")
        val ROOT_KEYSTORE_FILE = File(CERTIFICATE_DIRECTORY, "root-ca.p12")
        val ROOT_CERTIFICATE_PEM_FILE = File(CERTIFICATE_DIRECTORY, "root-ca.pem")
    }
}
