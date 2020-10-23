package com.ppaass.kt.proxy.handler

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
internal class ProxyChannelHeartbeatHandler : ChannelInboundHandlerAdapter() {
    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    override fun userEventTriggered(proxyChannelContext: ChannelHandlerContext, evt: Any) {
        if (evt !is IdleStateEvent) {
            logger.debug { "Ignore the event because it is not a idle event: $evt" }
            super.userEventTriggered(proxyChannelContext, evt)
            return
        }
        if (IdleState.ALL_IDLE !== evt.state()) {
            logger.debug { "Ignore the idle event because it is not a valid status: ${evt.state()}" }
            return
        }
        logger.debug { "Do heartbeat." }
        val proxyChannel = proxyChannelContext.channel();
        val targetChannelContext = proxyChannel.attr(TARGET_CHANNEL_CONTEXT).get()
        if(targetChannelContext!=null) {
            val targetChannel = targetChannelContext.channel()
            targetChannel.close()
        }
        proxyChannelContext.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(
            ChannelFutureListener.CLOSE_ON_FAILURE)
    }
}
