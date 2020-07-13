package com.ppaass.kt.common.netty.handler

import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import org.slf4j.LoggerFactory

class ResourceClearHandler(vararg channels: Channel) : ChannelInboundHandlerAdapter() {
    companion object {
        private val logger = LoggerFactory.getLogger(ResourceClearHandler::class.java)
    }

    private val relatedChannels = channels

    override fun channelUnregistered(ctx: ChannelHandlerContext) {
        for (relatedChannel in this.relatedChannels) {
            if (relatedChannel.isActive) {
                logger.debug("Close related channel on current channel inactive, current channel: ${ctx.channel().id()
                        .asLongText()}, related channel: ${relatedChannel.id().asLongText()}")
                relatedChannel.close()
            }
        }
        logger.debug("Close current channel on inactive, current channel: ${ctx.channel().id().asLongText()}")
        ctx.close()
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        for (relatedChannel in this.relatedChannels) {
            if (relatedChannel.isActive) {
                logger.debug("Close related channel on current channel inactive, current channel: ${ctx.channel().id()
                        .asLongText()}, related channel: ${relatedChannel.id().asLongText()}")
                relatedChannel.close()
            }
        }
        logger.debug("Close current channel on inactive, current channel: ${ctx.channel().id().asLongText()}")
        ctx.close()
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        logger.error("Exception happen in current channel: ${ctx.channel().id().asLongText()}", cause)
        for (relatedChannel in relatedChannels) {
            if (relatedChannel.isActive) {
                logger.debug("Close related channel on exception happen, current channel: ${ctx.channel().id()
                        .asLongText()}, related channel: ${relatedChannel.id().asLongText()}", cause)
                relatedChannel.close()
            }
        }
        logger.debug("Close current channel on exception, current channel: ${ctx.channel().id().asLongText()}", cause)
        ctx.close()
    }
}