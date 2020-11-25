package com.ppaass.agent.handler

import com.ppaass.agent.handler.http.HttpChannelInitializer
import com.ppaass.agent.handler.socks.SocksChannelInitializer
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.handler.codec.socksx.SocksVersion
import mu.KotlinLogging
import org.springframework.stereotype.Service

@ChannelHandler.Sharable
@Service
internal class SwitchProtocolHandler(
    private val socksChannelInitializer: SocksChannelInitializer,
    private val httpChannelInitializer: HttpChannelInitializer) :
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
        val currentByte = messageBuf.getByte(readerIndex)
        val agentChannelPipeline = agentChannel.pipeline()
        if (SocksVersion.SOCKS4a.byteValue() == currentByte || SocksVersion.SOCKS5.byteValue() == currentByte) {
            logger.debug { "Incoming request is a socks request." }
            agentChannel.attr(CHANNEL_PROTOCOL_CATEGORY)
                .setIfAbsent(ChannelProtocolCategory.SOCKS)
            agentChannelPipeline.apply {
                addLast(socksChannelInitializer)
            }
            agentChannelContext.fireChannelRead(messageBuf)
            return
        }
        logger.debug { "Incoming request is a http request." }
        agentChannel.attr(CHANNEL_PROTOCOL_CATEGORY)
            .setIfAbsent(ChannelProtocolCategory.HTTP)
        agentChannelPipeline.apply {
            addLast(httpChannelInitializer)
        }
        agentChannelContext.fireChannelRead(messageBuf)
    }
}
