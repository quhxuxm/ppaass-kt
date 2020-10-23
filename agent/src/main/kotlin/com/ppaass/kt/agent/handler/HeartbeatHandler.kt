package com.ppaass.kt.agent.handler

import io.netty.buffer.Unpooled
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.handler.timeout.IdleState
import io.netty.handler.timeout.IdleStateEvent
import mu.KotlinLogging
import org.springframework.stereotype.Service

@ChannelHandler.Sharable
@Service
internal class HeartbeatHandler : ChannelInboundHandlerAdapter() {
    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    override fun userEventTriggered(agentContext: ChannelHandlerContext, evt: Any) {
        if (evt !is IdleStateEvent) {
            super.userEventTriggered(agentContext, evt)
            return
        }
        if (IdleState.ALL_IDLE !== evt.state()) {
            return
        }
        agentContext.writeAndFlush(Unpooled.EMPTY_BUFFER)
            .addListener(ChannelFutureListener.CLOSE_ON_FAILURE)
    }
}
