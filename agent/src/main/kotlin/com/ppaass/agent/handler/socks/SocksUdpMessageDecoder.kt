package com.ppaass.agent.handler.socks

import io.netty.channel.ChannelHandlerContext
import io.netty.channel.socket.DatagramPacket
import io.netty.handler.codec.MessageToMessageDecoder
import io.netty.handler.codec.socksx.v5.Socks5AddressDecoder
import io.netty.handler.codec.socksx.v5.Socks5AddressType
import mu.KotlinLogging

internal class SocksUdpMessageDecoder : MessageToMessageDecoder<DatagramPacket>() {
    private companion object {
        private val logger = KotlinLogging.logger { }
    }

    override fun decode(agentUdpChannelContext: ChannelHandlerContext, udpMessage: DatagramPacket,
                        out: MutableList<Any>) {
        val udpMessageContent = udpMessage.content()
        val rsv = udpMessageContent.readUnsignedShort()
        val frag = udpMessageContent.readByte()
        val addrType = Socks5AddressType.valueOf(udpMessageContent.readByte())
        val targetAddress = Socks5AddressDecoder.DEFAULT.decodeAddress(addrType, udpMessageContent)
        val targetPort = udpMessageContent.readShort().toInt()
        val data = ByteArray(udpMessageContent.readableBytes())
        udpMessageContent.readBytes(data)
        val socks5UdpMessage = SocksUdpRequestMessage(
            udpMessageSender = udpMessage.sender(),
            udpMessageRecipient = udpMessage.recipient(),
            rsv = rsv,
            frag = frag,
            addressType = addrType,
            targetHost = targetAddress,
            targetPort = targetPort,
            data = data
        )
        logger.debug(
            "Decode socks5 udp message:\n{}\n", socks5UdpMessage)
        out.add(socks5UdpMessage)
    }

    override fun exceptionCaught(agentUdpChannelContext: ChannelHandlerContext, cause: Throwable) {
        logger.error(cause) {
            "Fail to decode socks5 udp message because of exception."
        }
    }
}
