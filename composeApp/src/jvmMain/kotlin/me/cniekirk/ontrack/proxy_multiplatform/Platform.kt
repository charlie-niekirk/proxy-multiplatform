package me.cniekirk.ontrack.proxy_multiplatform

class JVMPlatform {
    val name: String = "Java ${System.getProperty("java.version")}"
}

fun getPlatform() = JVMPlatform()