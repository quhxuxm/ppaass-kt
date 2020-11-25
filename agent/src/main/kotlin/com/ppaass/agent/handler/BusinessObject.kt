package com.ppaass.agent.handler

import io.netty.channel.Channel
import io.netty.util.AttributeKey

internal val CHANNEL_PROTOCOL_CATEGORY: AttributeKey<ChannelProtocolCategory> =
    AttributeKey.valueOf("CHANNEL_PROTOCOL_TYPE")
internal val TCP_CONNECTION_INFO: AttributeKey<TcpConnectionInfo> =
    AttributeKey.valueOf("TCP_CONNECTION_INFO")
internal val UDP_CONNECTION_INFO: AttributeKey<UdpConnectionInfo> =
    AttributeKey.valueOf("UDP_CONNECTION_INFO")

enum class ChannelProtocolCategory {
    HTTP, SOCKS
}

data class TcpConnectionInfo(
    val targetHost: String,
    val targetPort: Int,
    val userToken: String,
    val agentTcpChannel: Channel,
    var keepAlive: Boolean = true,
    val proxyTcpChannel: Channel
)

data class UdpConnectionInfo(
    val targetHost: String,
    val targetPort: Int,
    val userToken: String,
    val agentTcpChannel: Channel,
    var keepAlive: Boolean = true,
    val proxyTcpChannel: Channel
)
