package me.cniekirk.proxy

interface NetworkAddressRepository {
    fun detectPrimaryLanIpv4Address(): String?
}
