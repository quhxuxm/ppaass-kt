package com.ppaass.kt.proxy

import org.apache.commons.io.IOUtils
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Service

@ConfigurationProperties("ppaass.proxy")
@Service
class ProxyConfiguration {
    var masterIoEventThreadNumber: Int = 0
    var workerIoEventThreadNumber: Int = 0
    var receiveDataFromTargetEventExecutorGroupThreadNumber: Int = 0
    var sendDataToTargetEventLoopGroupThreadNumber: Int = 0
    var soBacklog: Int = 0
    var port: Int = 0
    var targetConnectionTimeout: Int = 0
    var agentConnectionIdleSeconds: Int = 0
    var targetReceiveDataAverageBufferMinSize: Int = 0
    var targetReceiveDataAverageBufferInitialSize: Int = 0
    var targetReceiveDataAverageBufferMaxSize: Int = 0
    var targetSoRcvbuf: Int = 0
    var targetSoSndbuf: Int = 0
    var soRcvbuf: Int = 0
    var soSndbuf: Int = 0
    var autoRead: Boolean = false
    val agentPublicKey: String by lazy {
        val lines = IOUtils.readLines(
                ProxyConfiguration::class.java.classLoader.getResourceAsStream("security/agentPublicKey.txt"),
                Charsets.UTF_8)
        val result = lines.joinToString("")
        result
    }
    val proxyPrivateKey: String by lazy {
        val lines = IOUtils.readLines(
                ProxyConfiguration::class.java.classLoader.getResourceAsStream("security/proxyPrivateKey.txt"),
                Charsets.UTF_8)
        val result = lines.joinToString("")
        result
    }
}
