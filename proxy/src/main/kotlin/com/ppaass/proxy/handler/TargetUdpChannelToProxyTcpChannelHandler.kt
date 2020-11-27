package com.ppaass.proxy.handler

import com.ppaass.kt.common.EncryptionType
import com.ppaass.kt.common.ProxyMessage
import com.ppaass.kt.common.ProxyMessageBody
import com.ppaass.kt.common.ProxyMessageBodyType
import io.netty.buffer.ByteBufUtil
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.socket.DatagramPacket
import mu.KotlinLogging
import org.springframework.stereotype.Service

/**
 * Forward the target udp channel data to proxy tcp channel.
 */
@Sharable
@Service
internal class TargetUdpChannelToProxyTcpChannelHandler :
    SimpleChannelInboundHandler<DatagramPacket>() {
    private companion object {
        private val logger = KotlinLogging.logger { }
    }

    override fun channelRead0(targetUdpChannelContext: ChannelHandlerContext,
                              targetUdpMessage: DatagramPacket) {
        val udpConnectionInfo =
            targetUdpChannelContext.channel().attr(UDP_CONNECTION_INFO)
                .get()
        val targetUdpMessageContent = targetUdpMessage.content()
        val sender = targetUdpMessage.sender()
        val proxyMessageBody =
            ProxyMessageBody(bodyType = ProxyMessageBodyType.OK_UDP,
                userToken = udpConnectionInfo.userToken,
                targetHost = sender.hostName,
                targetPort = sender.port,
                data = ByteBufUtil.getBytes(targetUdpMessageContent))
        val proxyMessage =
            ProxyMessage(encryptionType = EncryptionType.choose(),
                body =
                proxyMessageBody)
        logger.debug(
            "Receive udp package from target: {}, data:\n{}\n\nproxy message: \n{}\n",
            targetUdpMessage,
            ByteBufUtil.prettyHexDump(targetUdpMessageContent), proxyMessage)
        udpConnectionInfo.proxyTcpChannel.writeAndFlush(proxyMessage)
    }
}
