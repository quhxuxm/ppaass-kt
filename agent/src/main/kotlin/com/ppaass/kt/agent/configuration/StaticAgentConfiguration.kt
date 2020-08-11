package com.ppaass.kt.agent.configuration

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
    var proxyPublicKey: String? = null
    var agentPrivateKey: String? = null
}



