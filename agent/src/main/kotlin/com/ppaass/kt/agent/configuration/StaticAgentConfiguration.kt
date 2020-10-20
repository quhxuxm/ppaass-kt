package com.ppaass.kt.agent.configuration

import org.apache.commons.io.IOUtils
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import java.util.*

@ConstructorBinding
@ConfigurationProperties(prefix = "ppaass.agent")
class StaticAgentConfiguration(
    val masterIoEventThreadNumber: Int = 0,
    val workerIoEventThreadNumber: Int = 0,
    val proxyBootstrapIoEventThreadNumber: Int = 0,
    val dataTransferIoEventThreadNumber: Int = 0,
    val soBacklog: Int = 0,
    val soRcvbuf: Int = 0,
    val soSndbuf: Int = 0,
    val port: Int = 0,
    val proxyServerAddress: String? = null,
    val proxyServerPort: Int = 0,
    val proxyConnectionTimeout: Int = 0,
    val defaultLocale: Locale? = Locale.getDefault(),
    val clientConnectionIdleSeconds: Int = 0,
    val proxyServerReceiveDataAverageBufferMinSize: Int = 0,
    val proxyServerReceiveDataAverageBufferInitialSize: Int = 0,
    val proxyServerReceiveDataAverageBufferMaxSize: Int = 0,
    val proxyServerSoRcvbuf: Int = 0,
    val proxyServerSoSndbuf: Int = 0
) {
    val proxyPublicKey: String by lazy {
        val lines = IOUtils.readLines(
            StaticAgentConfiguration::class.java.classLoader.getResourceAsStream("security/proxyPublicKey.txt"),
            Charsets.UTF_8)
        val result = lines.joinToString("")
        result
    }
    val agentPrivateKey: String by lazy {
        val lines = IOUtils.readLines(
            StaticAgentConfiguration::class.java.classLoader.getResourceAsStream("security/agentPrivateKey.txt"),
            Charsets.UTF_8)
        val result = lines.joinToString("")
        result
    }
}



