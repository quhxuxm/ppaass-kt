package com.ppaass.kt.proxy.impl

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Service

@ConfigurationProperties("ppaass.proxy")
@Service
class ProxyConfiguration {
    var masterIoEventThreadNumber = 0
    var workerIoEventThreadNumber = 0
    var businessEventThreadNumber = 0
    var targetDataTransferIoEventThreadNumber = 0
    var soBacklog = 0
    var port = 0
    var targetConnectionTimeout = 0
    var agentConnectionIdleSeconds = 0
    var targetReceiveDataAverageBufferMinSize = 0
    var targetReceiveDataAverageBufferInitialSize = 0
    var targetReceiveDataAverageBufferMaxSize = 0
    var targetSoRcvbuf = 0
    var remainingBytesInProxyWriteBufferToPauseTargetAutoRead: Long = 0
}