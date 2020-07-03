package com.ppaass.kt.common.netty.handler

import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter

class ResourceClearHandler(vararg channels: Channel) : ChannelInboundHandlerAdapter() {
    private val relatedChannels = channels

    override fun channelInactive(ctx: ChannelHandlerContext) {
        for (relatedChannel in this.relatedChannels) {
            if (relatedChannel.isActive) {
                relatedChannel.pipeline().lastContext().close()
            }
        }
        ctx.close()
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable?) {
        for (relatedChannel in relatedChannels) {
            if (relatedChannel.isActive) {
                relatedChannel.pipeline().lastContext().close()
            }
        }
        ctx.close()
    }
}