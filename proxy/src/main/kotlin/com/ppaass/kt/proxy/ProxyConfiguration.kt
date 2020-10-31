package com.ppaass.kt.proxy

import org.apache.commons.io.IOUtils
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConstructorBinding
@ConfigurationProperties("ppaass.proxy")
class ProxyConfiguration(
    val masterIoEventThreadNumber: Int = 0,
    val workerIoEventThreadNumber: Int = 0,
    val dataTransferIoEventThreadNumber: Int = 0,
    val targetIoEventThreadNumber: Int = 0,
    val soBacklog: Int = 0,
    val port: Int = 0,
    val targetConnectionTimeout: Int = 0,
    val agentConnectionIdleSeconds: Int = 0,
    val targetConnectionIdleSeconds: Int = 0,
    val targetReceiveDataAverageBufferMinSize: Int = 0,
    val targetReceiveDataAverageBufferInitialSize: Int = 0,
    val targetReceiveDataAverageBufferMaxSize: Int = 0,
    val targetWriteBufferWaterMarkLow: Int = 0,
    val targetWriteBufferWaterMarkHigh: Int = 0,
    val targetWriteChannelLimit: Long = 0,
    val targetReadChannelLimit: Long = 0,
    val targetTrafficShapingCheckInterval: Long = 0,
    val receiveDataAverageBufferMinSize: Int = 0,
    val receiveDataAverageBufferInitialSize: Int = 0,
    val receiveDataAverageBufferMaxSize: Int = 0,
    val writeBufferWaterMarkLow: Int = 0,
    val writeBufferWaterMarkHigh: Int = 0,
    val writeChannelLimit: Long = 0,
    val readChannelLimit: Long = 0,
    val trafficShapingCheckInterval: Long = 0,
    val connectionKeepAlive: Boolean = true,
    val targetSoRcvbuf: Int = 0,
    val targetSoSndbuf: Int = 0,
    val targetWriteSpinCount: Int = 0,
    val targetConnectionKeepAlive: Boolean = true,
    val soRcvbuf: Int = 0,
    val soSndbuf: Int = 0,
    val writeSpinCount: Int = 0,
    val compressingEnable: Boolean = true
) {
    val agentPublicKey: String by lazy {
        val lines = IOUtils.readLines(
            ProxyConfiguration::class.java.classLoader.getResourceAsStream(
                "security/agentPublicKey.txt"),
            Charsets.UTF_8)
        val result = lines.joinToString("")
        result
    }
    val proxyPrivateKey: String by lazy {
        val lines = IOUtils.readLines(
            ProxyConfiguration::class.java.classLoader.getResourceAsStream(
                "security/proxyPrivateKey.txt"),
            Charsets.UTF_8)
        val result = lines.joinToString("")
        result
    }
}
