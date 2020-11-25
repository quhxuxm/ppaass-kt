package com.ppaass.agent.handler

import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import mu.KotlinLogging
import org.springframework.stereotype.Service

@ChannelHandler.Sharable
@Service
internal class SwitchProtocolHandler : ChannelInboundHandlerAdapter() {
    private companion object {
        private val logger = KotlinLogging.logger { }
    }

    override fun channelRead(agentChannelContext: ChannelHandlerContext, agentMessage: Any) {
        super.channelRead(agentChannelContext, agentMessage)
    }
}
