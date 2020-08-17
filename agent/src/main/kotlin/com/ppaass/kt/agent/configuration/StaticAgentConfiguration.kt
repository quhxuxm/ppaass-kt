package com.ppaass.kt.agent.configuration

import org.apache.commons.io.IOUtils
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Service
import java.util.*

@ConfigurationProperties(prefix = "ppaass.agent")
@Service
class StaticAgentConfiguration {
    var masterIoEventThreadNumber = 0
    var workerIoEventThreadNumber = 0
    var businessEventThreadNumber = 0
    var proxyDataTransferIoEventThreadNumber = 0
    var soBacklog = 0
    var soRcvbuf = 0
    var soSndbuf = 0
    var port = 0
    var proxyServerAddress: String? = null
    var proxyServerPort = 0
    var proxyConnectionTimeout = 0
    var defaultLocale: Locale? = Locale.getDefault()
    var clientConnectionIdleSeconds = 0
    var proxyServerReceiveDataAverageBufferMinSize = 0
    var proxyServerReceiveDataAverageBufferInitialSize = 0
    var proxyServerReceiveDataAverageBufferMaxSize = 0
    var proxyServerSoRcvbuf = 0
    var proxyServerSoSndbuf = 0
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



