package com.ppaass.proxy

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConstructorBinding
@ConfigurationProperties("ppaass.proxy")
class ProxyConfiguration(
    val proxyTcpMasterThreadNumber: Int,
    val proxyTcpWorkerThreadNumber: Int,
    val proxyUdpWorkerThreadNumber: Int,
    val targetTcpThreadNumber: Int,
    val targetUdpThreadNumber: Int,
    val targetTcpConnectionTimeout: Int,
    val targetTcpSoLinger: Int,
    val targetTcpSoRcvbuf: Int,
    val targetTcpSoSndbuf: Int,
    val targetTcpWriteSpinCount: Int,
    val targetTcpWriteBufferWaterMarkLow: Int,
    val targetTcpWriteBufferWaterMarkHigh: Int,
    val targetTcpReceiveDataAverageBufferMinSize: Int,
    val targetTcpReceiveDataAverageBufferInitialSize: Int,
    val targetTcpReceiveDataAverageBufferMaxSize: Int
)
