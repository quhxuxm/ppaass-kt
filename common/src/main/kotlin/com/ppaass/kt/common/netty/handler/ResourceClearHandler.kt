package com.ppaass.kt.common.netty.handler

import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.util.ReferenceCountUtil
import org.slf4j.LoggerFactory

class ResourceClearHandler(vararg channels: Channel) : ChannelInboundHandlerAdapter() {
    companion object {
        private val logger = LoggerFactory.getLogger(ResourceClearHandler::class.java)
    }

    private val relatedChannels = channels

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        if (ReferenceCountUtil.refCnt(msg) > 1) {
            logger.trace("Release buffer, current channel: ${ctx.channel().id()}")
            ReferenceCountUtil.release(msg)
        }
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        for (relatedChannel in this.relatedChannels) {
            if (relatedChannel.isActive) {
                logger.trace("Close related channel on current channel inactive, current channel: ${ctx.channel().id()
                        .asLongText()}, related channel: ${relatedChannel.id().asLongText()}")
                relatedChannel.pipeline().lastContext().close()
            }
        }
        logger.trace("Close current channel on inactive, current channel: ${ctx.channel().id()}")
        ctx.close()
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable?) {
        for (relatedChannel in relatedChannels) {
            if (relatedChannel.isActive) {
                logger.trace("Close related channel on exception happen, current channel: ${ctx.channel().id()
                        .asLongText()}, related channel: ${relatedChannel.id().asLongText()}")
                relatedChannel.pipeline().lastContext().close()
            }
        }
        logger.trace("Close current channel on exception, current channel: ${ctx.channel().id()}")
        ctx.close()
    }
}