package com.ppaass.kt.common.netty.codec

import com.ppaass.kt.common.protocol.AgentMessage
import com.ppaass.kt.common.protocol.encodeAgentMessage
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToByteEncoder
import mu.KotlinLogging

class AgentMessageEncoder(private val proxyPublicKeyString: String) :
    MessageToByteEncoder<AgentMessage>() {
    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    override fun encode(ctx: ChannelHandlerContext, msg: AgentMessage, out: ByteBuf) {
        if (logger.isDebugEnabled) {
            logger.debug("Begin to encode message:\n{}\n", msg)
        }
        encodeAgentMessage(
            message = msg,
            proxyPublicKeyString = proxyPublicKeyString,
            output = out)
    }
}
