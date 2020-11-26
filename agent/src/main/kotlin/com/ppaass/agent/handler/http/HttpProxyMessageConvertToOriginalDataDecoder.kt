package com.ppaass.agent.handler.http

import com.ppaass.kt.common.ProxyMessage
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToMessageDecoder
import mu.KotlinLogging

internal class HttpProxyMessageConvertToOriginalDataDecoder :
    MessageToMessageDecoder<ProxyMessage>() {
    private companion object {
        private val logger = KotlinLogging.logger { }
    }

    override fun decode(proxyChannelContext: ChannelHandlerContext, proxyMessage: ProxyMessage,
                        out: MutableList<Any>) {
        out.add(Unpooled.wrappedBuffer(proxyMessage.body.data))
    }

    override fun exceptionCaught(proxyChannelContext: ChannelHandlerContext, cause: Throwable) {
        val proxyChannel = proxyChannelContext.channel()
        val connectionInfo = proxyChannel.attr(HTTP_CONNECTION_INFO).get()
        val agentChannel = connectionInfo?.agentChannel
        logger.error("Exception happen on proxy channel, agent channel = {}, proxy channel = {}",
            agentChannel?.id()?.asLongText() ?: "", proxyChannel.id().asLongText(), cause)
    }
}
