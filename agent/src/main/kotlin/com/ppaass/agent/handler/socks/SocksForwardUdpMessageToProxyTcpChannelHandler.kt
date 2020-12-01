package com.ppaass.agent.handler.socks

import com.ppaass.agent.AgentConfiguration
import com.ppaass.kt.common.AgentMessage
import com.ppaass.kt.common.AgentMessageBody
import com.ppaass.kt.common.AgentMessageBodyType
import com.ppaass.kt.common.EncryptionType
import com.ppaass.kt.common.generateUuid
import com.ppaass.kt.common.generateUuidInBytes
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import mu.KotlinLogging
import org.springframework.stereotype.Service

@Sharable
@Service
internal class SocksForwardUdpMessageToProxyTcpChannelHandler(
    private val agentConfiguration: AgentConfiguration) :
    SimpleChannelInboundHandler<SocksUdpRequestMessage>() {
    private companion object {
        private val logger = KotlinLogging.logger { }
    }

    override fun channelRead0(agentUdpChannelContext: ChannelHandlerContext,
                              socks5UdpMessage: SocksUdpRequestMessage) {
        val udpConnectionInfo =
            agentUdpChannelContext.channel().attr(SOCKS_UDP_CONNECTION_INFO).get()
        udpConnectionInfo.clientSenderHost = socks5UdpMessage.udpMessageSender.getHostName()
        udpConnectionInfo.clientSenderPort = socks5UdpMessage.udpMessageSender.getPort()
        udpConnectionInfo.clientRecipientHost = socks5UdpMessage.targetHost
        udpConnectionInfo.clientRecipientPort = socks5UdpMessage.targetPort
        agentUdpChannelContext.channel().attr(SOCKS_UDP_CONNECTION_INFO).set(udpConnectionInfo)
        val udpData: ByteArray = socks5UdpMessage.data
        val agentMessageBody =
            AgentMessageBody(
                id = generateUuid(),
                bodyType = AgentMessageBodyType.UDP_DATA,
                userToken = agentConfiguration.userToken!!,
                targetHost = socks5UdpMessage.targetHost,
                targetPort = socks5UdpMessage.targetPort, data = udpData)
        val agentMessage =
            AgentMessage(
                encryptionToken = generateUuidInBytes(),
                encryptionType = EncryptionType.choose(),
                body = agentMessageBody)
        logger.debug { "Send udp message through tcp, agent message = ${agentMessage}" }
        val proxyTcpChannelForUdpTransfer = udpConnectionInfo.proxyTcpChannel
        proxyTcpChannelForUdpTransfer.writeAndFlush(agentMessage)
    }
}

