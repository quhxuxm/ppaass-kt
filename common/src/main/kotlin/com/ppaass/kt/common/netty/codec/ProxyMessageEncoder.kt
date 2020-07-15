package com.ppaass.kt.common.netty.codec

import com.ppaass.kt.common.message.MessageSerializer
import com.ppaass.kt.common.message.ProxyMessage
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToByteEncoder
import org.slf4j.LoggerFactory

class ProxyMessageEncoder : MessageToByteEncoder<ProxyMessage>() {
    companion object {
        private val logger = LoggerFactory.getLogger(ProxyMessageEncoder::class.java)
    }

    override fun encode(ctx: ChannelHandlerContext, msg: ProxyMessage, out: ByteBuf) {
        logger.debug("Begin to encode message:\n{}\n", msg)
        MessageSerializer.encodeProxyMessage(msg, out)
    }
}