package com.ppaass.kt.common.netty.codec

import com.ppaass.kt.common.protocol.AgentMessage
import com.ppaass.kt.common.protocol.encodeAgentMessage
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToByteEncoder
import org.slf4j.LoggerFactory

class AgentMessageEncoder : MessageToByteEncoder<AgentMessage>() {
    companion object {
        private val logger = LoggerFactory.getLogger(AgentMessageEncoder::class.java)
    }

    override fun encode(ctx: ChannelHandlerContext, msg: AgentMessage, out: ByteBuf) {
        logger.debug("Begin to encode message:\n{}\n", msg)
        encodeAgentMessage(msg, out)
    }
}