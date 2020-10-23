package com.ppaass.kt.common.netty.handler

import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import mu.KotlinLogging

@ChannelHandler.Sharable
class ResourceClearHandler : ChannelInboundHandlerAdapter() {
    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        logger.error("Exception happen in current channel: ${
            ctx.channel().id()
                .asLongText()
        }, remote address: ${ctx.channel().remoteAddress()}", cause)
        logger.debug("Close current channel on exception, current channel: ${
            ctx.channel().id().asLongText()
        }", cause)
        ctx.close()
    }
}
