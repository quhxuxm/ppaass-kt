package com.ppaass.kt.common.netty.codec

import com.ppaass.kt.common.message.Message
import com.ppaass.kt.common.message.MessageBody
import com.ppaass.kt.common.message.MessageSerializer
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufUtil
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageDecoder
import org.slf4j.LoggerFactory

class MessageDecoder<T : MessageBody>(private val messageBodyClass: Class<T>) : ByteToMessageDecoder() {
    companion object {
        private val logger = LoggerFactory.getLogger(MessageDecoder::class.java)
    }

    override fun decode(ctx: ChannelHandlerContext, input: ByteBuf, out: MutableList<Any>) {
        logger.debug("Begin to decode incoming request to message, incoming bytes:\n{}\n", ByteBufUtil.prettyHexDump(input))
        val message: Message<T> = MessageSerializer.decode(input, this.messageBodyClass)
        logger.debug("Decode result:\n{}\n", message)
        out.add(message)
    }
}