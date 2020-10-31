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

    override fun decode(proxyChannelContext: ChannelHandlerContext, proxyMessage: ProxyMessage,
                        out: MutableList<Any>) {
        out.add(Unpooled.wrappedBuffer(proxyMessage.body.originalData))
    }

    override fun exceptionCaught(proxyChannelContext: ChannelHandlerContext, cause: Throwable) {
        val proxyChannel = proxyChannelContext.channel();
        val agentChannelContext = proxyChannel.attr(AGENT_CHANNEL_CONTEXT).get()
        val agentChannel = agentChannelContext?.channel()
        logger.error(cause) {
            "Exception happen on proxy channel, agent channel = ${
                agentChannel?.id()?.asLongText()
            }, proxy channel = ${
                proxyChannel.id().asLongText()
            }."
        }
    }
}
