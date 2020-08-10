package com.ppaass.kt.common.netty.codec

import com.ppaass.kt.common.protocol.decodeAgentMessage
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufUtil
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageDecoder
import mu.KotlinLogging

class AgentMessageDecoder : ByteToMessageDecoder() {
    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    override fun decode(ctx: ChannelHandlerContext, input: ByteBuf, out: MutableList<Any>) {
        if (logger.isTraceEnabled) {
            logger.trace("Begin to decode incoming request to message, incoming bytes:\n{}\n",
                    ByteBufUtil.prettyHexDump(input))
        }
        val message = decodeAgentMessage(input)
        logger.debug("Decode result:\n{}\n", message)
        out.add(message)
    }
}
