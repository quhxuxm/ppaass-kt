package com.ppaass.kt.proxy.handler

import io.netty.buffer.Unpooled
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
        logger.debug {
            "Do heartbeat on proxy channel ${
                proxyChannelContext.channel().id().asLongText()
            }."
        }
        proxyChannelContext.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener {
            if (!it.isSuccess) {
                proxyChannelContext.close()
                return@addListener
            }
            val proxyChannel = proxyChannelContext.channel()
            val targetChannel = proxyChannel.attr(TARGET_CHANNEL).get()
            if (targetChannel == null) {
                proxyChannelContext.close()
                return@addListener
            }
            if (targetChannel.isWritable) {
                proxyChannel.read()
            }
        }
    }
}
