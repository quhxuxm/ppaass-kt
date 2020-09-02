package com.ppaass.kt.proxy.handler

import com.ppaass.kt.common.exception.PpaassException
import com.ppaass.kt.common.protocol.AgentMessage
import com.ppaass.kt.common.protocol.AgentMessageBodyType
import com.ppaass.kt.proxy.ProxyConfiguration
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import mu.KotlinLogging

internal class ProxyToTargetHandler(private val targetChannel: Channel,
                                    private val proxyConfiguration: ProxyConfiguration) :
        SimpleChannelInboundHandler<AgentMessage>() {
    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    override fun channelRead0(proxyContext: ChannelHandlerContext, agentMessage: AgentMessage) {
        if (AgentMessageBodyType.CONNECT === agentMessage.body.bodyType) {
            logger.debug("Discard CONNECT message from agent.")
            targetChannel.read()
            return
        }
        targetChannel.writeAndFlush(Unpooled.wrappedBuffer(agentMessage.body.originalData))
        targetChannel.read()

    }

    override fun channelReadComplete(proxyContext: ChannelHandlerContext) {
        targetChannel.flush()
    }
}
