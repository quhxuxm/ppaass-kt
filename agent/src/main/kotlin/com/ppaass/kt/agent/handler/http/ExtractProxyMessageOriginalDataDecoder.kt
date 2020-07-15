package com.ppaass.kt.agent.handler.http

import com.ppaass.kt.common.message.ProxyMessage
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToMessageDecoder

internal class ExtractProxyMessageOriginalDataDecoder : MessageToMessageDecoder<ProxyMessage>() {
    override fun decode(ctx: ChannelHandlerContext, msg: ProxyMessage, out: MutableList<Any>) {
        out.add(Unpooled.wrappedBuffer(msg.body.originalData))
    }
}