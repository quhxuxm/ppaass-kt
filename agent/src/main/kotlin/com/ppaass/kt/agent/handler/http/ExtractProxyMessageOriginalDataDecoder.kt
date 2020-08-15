package com.ppaass.kt.agent.handler.http

import com.ppaass.kt.common.protocol.ProxyMessage
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToMessageDecoder
import mu.KotlinLogging

internal class ExtractProxyMessageOriginalDataDecoder : MessageToMessageDecoder<ProxyMessage>() {
    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    override fun decode(ctx: ChannelHandlerContext, msg: ProxyMessage, out: MutableList<Any>) {
        out.add(Unpooled.wrappedBuffer(msg.body.originalData))
    }
}
