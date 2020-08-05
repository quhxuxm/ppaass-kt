package com.ppaass.kt.agent.handler.common

import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.handler.timeout.IdleState
import io.netty.handler.timeout.IdleStateEvent
import io.netty.util.ReferenceCountUtil
import org.slf4j.LoggerFactory

@ChannelHandler.Sharable
internal class HeartbeatHandler : ChannelInboundHandlerAdapter() {
    companion object {
        private val logger = LoggerFactory.getLogger(HeartbeatHandler::class.java)
    }

    override fun userEventTriggered(agentContext: ChannelHandlerContext, evt: Any) {
        if (evt !is IdleStateEvent) {
            super.userEventTriggered(agentContext, evt)
            return
        }
        if (IdleState.ALL_IDLE !== evt.state()) {
            return
        }
        agentContext.close()
        ReferenceCountUtil.release(evt)
    }
}