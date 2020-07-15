package com.ppaass.kt.agent.handler.common

import io.netty.buffer.Unpooled
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.handler.timeout.IdleState
import io.netty.handler.timeout.IdleStateEvent
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@ChannelHandler.Sharable
internal class HeartbeatHandler : ChannelInboundHandlerAdapter() {
    companion object {
        private val logger: Logger =
                LoggerFactory.getLogger(HeartbeatHandler::class.java)
    }

    override fun userEventTriggered(agentContext: ChannelHandlerContext, evt: Any) {
        if (evt !is IdleStateEvent) {
            super.userEventTriggered(agentContext, evt)
            return
        }
        if (IdleState.ALL_IDLE != evt.state()) {
            return
        }
        logger.debug("Heartbeat with agent client, current channel id: {}.", agentContext.channel().id().asLongText())
        agentContext.channel().writeAndFlush(
                Unpooled.EMPTY_BUFFER)
                .addListener(ChannelFutureListener {
                    if (!it.isSuccess) {
                        val agentChannelId = it.channel().id().asLongText()
                        it.channel().close()
                        logger.error("Close agent client channel as heartbeat fail, agentChannelId={}.", agentChannelId)
                    }
                })
    }
}