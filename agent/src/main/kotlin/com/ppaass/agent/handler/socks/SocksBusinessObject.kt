package com.ppaass.agent.handler.socks

import io.netty.channel.Channel
import io.netty.handler.codec.socksx.v5.Socks5AddressType
import io.netty.util.AttributeKey

internal val SOCKS_TCP_CONNECTION_INFO: AttributeKey<SocksTcpConnectionInfo> =
    AttributeKey.valueOf("SOCKS_TCP_CONNECTION_INFO")
internal val SOCKS_UDP_CONNECTION_INFO: AttributeKey<SocksUdpConnectionInfo> =
    AttributeKey.valueOf("SOCKS_UDP_CONNECTION_INFO")

data class SocksTcpConnectionInfo(
    val targetHost: String,
    val targetPort: Int,
    val targetAddressType: Socks5AddressType,
    val userToken: String,
    val agentTcpChannel: Channel,
    var keepAlive: Boolean = true,
    val proxyTcpChannel: Channel
)

data class SocksUdpConnectionInfo(
    val agentUdpPort: Int,
    val clientSenderAssociateHost: String,
    val clientSenderAssociatePort: Int,
    val agentTcpChannel: Channel,
    val agentUdpChannel: Channel,
    val proxyTcpChannel: Channel,
    var userToken: String
) {
    var targetHost: String? = null
    var targetPort: Int = -1
}
