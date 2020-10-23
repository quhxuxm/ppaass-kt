package com.ppaass.kt.proxy.handler

import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.handler.timeout.IdleState
import io.netty.handler.timeout.IdleStateEvent
import mu.KotlinLogging
import org.springframework.stereotype.Service

@ChannelHandler.Sharable
@Service
internal class TargetChannelHeartbeatHandler : ChannelInboundHandlerAdapter() {
    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    override fun userEventTriggered(targetChannelContext: ChannelHandlerContext, evt: Any) {
        if (evt !is IdleStateEvent) {
            logger.debug { "Ignore the event because it is not a idle event: $evt" }
            super.userEventTriggered(targetChannelContext, evt)
            return
        }
        if (IdleState.ALL_IDLE !== evt.state()) {
            logger.debug { "Ignore the idle event because it is not a valid status: ${evt.state()}" }
            return
        }
        logger.debug { "Do heartbeat." }
        val targetChannel = targetChannelContext.channel()
        val proxyChannelContext = targetChannel.attr(PROXY_CHANNEL_CONTEXT).get()
        if (proxyChannelContext == null) {
            targetChannelContext.close()
            return
        }
        val proxyChannel = proxyChannelContext.channel();
        if (proxyChannel.isActive) {
            if (proxyChannel.isWritable) {
                targetChannel.read()
            } else {
                proxyChannel.flush()
            }
            return
        }
        targetChannelContext.close()
    }
}
