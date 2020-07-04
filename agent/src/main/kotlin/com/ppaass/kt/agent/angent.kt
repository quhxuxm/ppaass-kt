package com.ppaass.kt.agent

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Service
import java.util.*

interface IStaticAgentConfiguration {
    var masterIoEventThreadNumber: Int?
    var workerIoEventThreadNumber: Int?
    var businessEventThreadNumber: Int?
    var proxyDataTransferIoEventThreadNumber: Int?
    var soBacklog: Int?
    var port: Int?
    var proxyServerAddress: String?
    var proxyServerPort: Int?
    var proxyConnectionTimeout: Int?
    var defaultLocale: Locale?
    var clientConnectionIdleSeconds: Int?
    var proxyServerReceiveDataAverageBufferMinSize: Long?
    var proxyServerReceiveDataAverageBufferInitialSize: Long?
    var proxyServerReceiveDataAverageBufferMaxSize: Long?
    var proxyServerSoRcvbuf: Long?
}

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
    var defaultLocale: Locale? = null
    var clientConnectionIdleSeconds = 0
    var proxyServerReceiveDataAverageBufferMinSize = 0
    var proxyServerReceiveDataAverageBufferInitialSize = 0
    var proxyServerReceiveDataAverageBufferMaxSize = 0
    var proxyServerSoRcvbuf = 0
}

interface IAgentConfiguration {
}
