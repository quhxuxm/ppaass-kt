package com.ppaass.kt.common.netty.codec

import com.ppaass.kt.common.protocol.AgentMessage
import com.ppaass.kt.common.protocol.encodeAgentMessage
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToByteEncoder
import mu.KotlinLogging

class AgentMessageEncoder : MessageToByteEncoder<AgentMessage>() {
    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    override fun encode(ctx: ChannelHandlerContext, msg: AgentMessage, out: ByteBuf) {
        logger.debug("Begin to encode message:\n{}\n", msg)
        encodeAgentMessage(msg, out)
    }
}
