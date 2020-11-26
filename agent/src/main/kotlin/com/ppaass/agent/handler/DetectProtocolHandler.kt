package com.ppaass.agent.handler

import com.ppaass.agent.CHANNEL_PROTOCOL_CATEGORY
import com.ppaass.agent.ChannelProtocolCategory
import com.ppaass.agent.handler.http.HttpProtocolHandler
import com.ppaass.agent.handler.socks.SocksProtocolHandler
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.handler.codec.socksx.SocksPortUnificationServerHandler
import io.netty.handler.codec.socksx.SocksVersion
import mu.KotlinLogging
import org.springframework.stereotype.Service

@ChannelHandler.Sharable
@Service
internal class DetectProtocolHandler(
    private val socksProtocolHandler: SocksProtocolHandler,
    private val httpProtocolHandler: HttpProtocolHandler) :
    ChannelInboundHandlerAdapter() {
    private companion object {
        private val logger = KotlinLogging.logger { }
    }

    override fun channelRead(agentChannelContext: ChannelHandlerContext, msg: Any) {
        val agentChannel = agentChannelContext.channel()
        val channelProtocolType = agentChannel.attr(CHANNEL_PROTOCOL_CATEGORY).get()
        if (channelProtocolType != null) {
            logger.debug { "Incoming request protocol is: ${channelProtocolType}." }
            agentChannelContext.fireChannelRead(msg)
            return
        }
        val messageBuf = msg as ByteBuf
        val readerIndex = messageBuf.readerIndex()
        if (messageBuf.writerIndex() == readerIndex) {
            logger.debug { "Incoming request reader index is the same as writer index." }
            return
        }
        val protocolVersionByte = messageBuf.getByte(readerIndex)
        val agentChannelPipeline = agentChannelContext.pipeline()
        if (SocksVersion.SOCKS4a.byteValue() == protocolVersionByte ||
            SocksVersion.SOCKS5.byteValue() == protocolVersionByte) {
            logger.debug { "Incoming request is a socks request." }
            agentChannel.attr(CHANNEL_PROTOCOL_CATEGORY)
                .setIfAbsent(ChannelProtocolCategory.SOCKS)
            agentChannelPipeline.apply {
                addLast(SocksPortUnificationServerHandler())
                addLast(socksProtocolHandler)
            }
            agentChannelContext.fireChannelRead(messageBuf)
            return
        }
        logger.debug { "Incoming request is a http request." }
        agentChannel.attr(CHANNEL_PROTOCOL_CATEGORY)
            .setIfAbsent(ChannelProtocolCategory.HTTP)
        agentChannelPipeline.apply {
            remove(this@DetectProtocolHandler)
            addLast(httpProtocolHandler)
        }
        agentChannelContext.fireChannelRead(messageBuf)
    }
}
