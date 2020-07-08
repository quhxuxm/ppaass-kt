package com.ppaass.kt.common.netty.codec

import com.ppaass.kt.common.message.AgentMessage
import com.ppaass.kt.common.message.MessageSerializer
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufUtil
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageDecoder
import org.slf4j.LoggerFactory

class AgentMessageDecoder : ByteToMessageDecoder() {
    companion object {
        private val logger = LoggerFactory.getLogger(AgentMessageDecoder::class.java)
    }

    override fun decode(ctx: ChannelHandlerContext, input: ByteBuf, out: MutableList<Any>) {
        logger.trace("Begin to decode incoming request to message, incoming bytes:\n{}\n",
                ByteBufUtil.prettyHexDump(input))
        val message: AgentMessage = MessageSerializer.decodeAgentMessage(input)
        logger.debug("Decode result:\n{}\n", message)
        out.add(message)
    }
}