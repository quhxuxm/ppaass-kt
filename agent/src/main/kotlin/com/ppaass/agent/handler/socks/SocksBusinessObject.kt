package com.ppaass.agent.handler.socks

import io.netty.buffer.ByteBufUtil
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.handler.codec.socksx.v5.Socks5AddressType
import io.netty.util.AttributeKey
import java.net.InetSocketAddress

@JvmField
internal val SOCKS_TCP_CONNECTION_INFO: AttributeKey<SocksTcpConnectionInfo> =
    AttributeKey.valueOf("SOCKS_TCP_CONNECTION_INFO")

@JvmField
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
    var clientSenderHost: String? = null
    var clientSenderPort: Int = -1
    var clientRecipientHost: String? = null
    var clientRecipientPort: Int = -1
}

data class SocksUdpRequestMessage(
    val udpMessageSender: InetSocketAddress,
    val udpMessageRecipient: InetSocketAddress,
    val rsv: Int,
    val frag: Byte,
    val addressType: Socks5AddressType,
    val targetHost: String,
    val targetPort: Int,
    val data: ByteArray
) {
    override fun toString(): String {
        return "SocksUdpRequestMessage(udpMessageSender=${
            udpMessageSender
        }, udpMessageRecipient=${
            udpMessageRecipient
        }, rsv=${
            rsv
        }, frag=${
            frag
        }, addressType=${
            addressType
        }, targetHost='${
            targetHost
        }', targetPort=${
            targetPort
        }, data=${
            ByteBufUtil.prettyHexDump(Unpooled.wrappedBuffer(data))
        })"
    }
}
