package com.ppaass.proxy.handler

import io.netty.channel.Channel
import io.netty.util.AttributeKey

internal val TCP_CONNECTION_INFO: AttributeKey<TcpConnectionInfo> =
    AttributeKey.valueOf("TCP_CONNECTION_INFO")
internal val UDP_CONNECTION_INFO: AttributeKey<UdpConnectionInfo> =
    AttributeKey.valueOf("UDP_CONNECTION_INFO")

data class UdpConnectionInfo(
    val targetHost: String,
    val targetPort: Int,
    val userToken: String,
    val proxyTcpChannel: Channel,
    val targetUdpChannel: Channel
)

data class TcpConnectionInfo(
    val targetHost: String,
    val targetPort: Int,
    val userToken: String,
    val proxyTcpChannel: Channel,
    val targetTcpChannel: Channel,
    val targetTcpConnectionKeepAlive: Boolean,
    var heartBeatFailureTimes: Int = 0
)
