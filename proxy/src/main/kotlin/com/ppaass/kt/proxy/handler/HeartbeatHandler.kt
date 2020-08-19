package com.ppaass.kt.proxy.handler

import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.handler.timeout.IdleState
import io.netty.handler.timeout.IdleStateEvent
import mu.KotlinLogging

@ChannelHandler.Sharable
internal class HeartbeatHandler : ChannelInboundHandlerAdapter() {
    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    override fun userEventTriggered(proxyContext: ChannelHandlerContext, evt: Any) {
        if (evt !is IdleStateEvent) {
            super.userEventTriggered(proxyContext, evt)
            return
        }
        if (IdleState.ALL_IDLE !== evt.state()) {
            return
        }
        logger.info { "Close channel ${proxyContext.channel().id().asLongText()} because of no I/O event happen." }
        proxyContext.close()
    }
}
