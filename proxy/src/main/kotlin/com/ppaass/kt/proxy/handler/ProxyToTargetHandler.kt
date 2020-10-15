package com.ppaass.kt.proxy.handler

import com.ppaass.kt.common.protocol.AgentMessage
import com.ppaass.kt.common.protocol.AgentMessageBodyType
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import mu.KotlinLogging

internal class ProxyToTargetHandler(
    private val targetChannel: Channel
) : SimpleChannelInboundHandler<AgentMessage>() {
    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    override fun channelRead0(proxyContext: ChannelHandlerContext, agentMessage: AgentMessage) {
        if (AgentMessageBodyType.CONNECT === agentMessage.body.bodyType) {
            logger.debug("Discard CONNECT message from agent.")
            return
        }
        targetChannel.writeAndFlush(Unpooled.wrappedBuffer(agentMessage.body.originalData));
        if (!targetChannel.isWritable) {
            proxyContext.channel().config().isAutoRead = false
        }
    }

    override fun channelWritabilityChanged(proxyContext: ChannelHandlerContext) {
        if (proxyContext.channel().isWritable) {
            logger.info { "Recover auto read on target channel: ${targetChannel.id().asLongText()}" }
            targetChannel.config().isAutoRead = true
        } else {
            logger.info { "Close auto read on target channel: ${targetChannel.id().asLongText()}" }
            targetChannel.config().isAutoRead = false
        }
    }
}
