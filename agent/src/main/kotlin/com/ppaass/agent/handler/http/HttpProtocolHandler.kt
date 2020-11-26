package com.ppaass.agent.handler.http

import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import org.springframework.stereotype.Service

@ChannelHandler.Sharable
@Service
internal class HttpProtocolHandler : SimpleChannelInboundHandler<Any>() {
    override fun channelRead0(ctx: ChannelHandlerContext?, msg: Any?) {
        TODO("Not yet implemented")
    }
}
