package com.ppaass.kt.common.netty.codec

import com.ppaass.kt.common.protocol.ProxyMessage
import com.ppaass.kt.common.protocol.encodeProxyMessage
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToByteEncoder
import mu.KotlinLogging

class ProxyMessageEncoder(private val agentPublicKeyString: String) :
    MessageToByteEncoder<ProxyMessage>() {
    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    override fun encode(ctx: ChannelHandlerContext, msg: ProxyMessage, out: ByteBuf) {
        if (logger.isDebugEnabled) {
            logger.debug("Begin to encode message:\n{}\n", msg)
        }
        encodeProxyMessage(message = msg, agentPublicKeyString = agentPublicKeyString, output = out)
    }
}
