package com.ppaass.kt.proxy

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Service

@ConfigurationProperties("ppaass.proxy")
@Service
class ProxyConfiguration {
    var masterIoEventThreadNumber: Int = 0
    var workerIoEventThreadNumber: Int = 0
    var businessEventThreadNumber: Int = 0
    var targetDataTransferIoEventThreadNumber: Int = 0
    var soBacklog: Int = 0
    var port: Int = 0
    var targetConnectionTimeout: Int = 0
    var agentConnectionIdleSeconds: Int = 0
    var targetReceiveDataAverageBufferMinSize: Int = 0
    var targetReceiveDataAverageBufferInitialSize: Int = 0
    var targetReceiveDataAverageBufferMaxSize: Int = 0
    var targetSoRcvbuf: Int = 0
    var autoRead: Boolean = false
}
