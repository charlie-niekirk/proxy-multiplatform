package me.cniekirk.proxy

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.net.NetworkInterface

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class JvmNetworkAddressRepository : NetworkAddressRepository {

    override fun detectPrimaryLanIpv4Address(): String? {
        return runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .asSequence()
                .filter { networkInterface ->
                    !networkInterface.isLoopback && networkInterface.isUp
                }
                .flatMap { networkInterface ->
                    networkInterface.inetAddresses.toList().asSequence()
                }
                .firstOrNull { address ->
                    !address.isLoopbackAddress &&
                        !address.hostAddress.contains(":")
                }
                ?.hostAddress
        }.getOrNull()
    }
}
