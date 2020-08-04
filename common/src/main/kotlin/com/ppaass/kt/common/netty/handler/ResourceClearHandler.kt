package com.ppaass.kt.common.netty.handler

import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import org.slf4j.LoggerFactory

class ResourceClearHandler() : ChannelInboundHandlerAdapter() {
    companion object {
        private val logger = LoggerFactory.getLogger(ResourceClearHandler::class.java)
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        logger.error("Exception happen in current channel: ${ctx.channel().id()
                .asLongText()}, remote address: ${ctx.channel().remoteAddress()}", cause)
        logger.debug("Close current channel on exception, current channel: ${ctx.channel().id().asLongText()}", cause)
        ctx.close()
    }
}