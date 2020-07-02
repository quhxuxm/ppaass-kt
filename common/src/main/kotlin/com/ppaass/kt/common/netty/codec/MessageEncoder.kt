package com.ppaass.kt.common.netty.codec

import com.ppaass.kt.common.message.Message
import com.ppaass.kt.common.message.MessageBody
import com.ppaass.kt.common.message.MessageSerializer
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufUtil
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToByteEncoder
import org.slf4j.LoggerFactory

class MessageEncoder<T : MessageBody> : MessageToByteEncoder<Message<T>>() {
    companion object {
        private val logger = LoggerFactory.getLogger(MessageEncoder::class.java)
    }

    override fun encode(ctx: ChannelHandlerContext, msg: Message<T>, out: ByteBuf) {
        logger.debug("Begin to encode message:\n{}\n", msg)
        val encodeResult = MessageSerializer.encode(msg)
        logger.debug("Encode result:\n{}\n", ByteBufUtil.prettyHexDump(encodeResult))
        out.writeBytes(encodeResult)
    }
}