package com.ppaass.kt.common

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.handler.codec.ByteToMessageDecoder
import io.netty.handler.codec.MessageToByteEncoder
import mu.KotlinLogging

/**
 * Decode input buffer to agent message.
 *
 * @param proxyPrivateKey The private key in proxy side.
 */
class AgentMessageDecoder(private val proxyPrivateKey: ByteArray) :
    ByteToMessageDecoder() {
    private companion object {
        private val logger = KotlinLogging.logger { }
    }

    override fun decode(ctx: ChannelHandlerContext, input: ByteBuf, out: MutableList<Any>) {
        val message: AgentMessage = decodeAgentMessage(
            input, proxyPrivateKey)
        logger.debug("Decode result:\n{}\n", message)
        out.add(message)
    }
}

/**
 * Encode agent message to output buffer.
 *
 * @param proxyPublicKey The public key in proxy side.
 */
class AgentMessageEncoder(private val proxyPublicKey: ByteArray) :
    MessageToByteEncoder<AgentMessage>() {
    private companion object {
        private val logger = KotlinLogging.logger { }
    }

    override fun encode(ctx: ChannelHandlerContext, msg: AgentMessage, out: ByteBuf) {
        logger.debug("Begin to encode message:\n{}\n", msg)
        encodeMessage(msg, proxyPublicKey, out)
    }
}

/**
 * Decode input buffer to proxy message.
 *
 * @param agentPrivateKey The private key in agent side.
 */
class ProxyMessageDecoder(private val agentPrivateKey: ByteArray) :
    ByteToMessageDecoder() {
    private companion object {
        private val logger = KotlinLogging.logger { }
    }

    override fun decode(ctx: ChannelHandlerContext, input: ByteBuf, out: MutableList<Any>) {
        val message: ProxyMessage = decodeProxyMessage(input,
            agentPrivateKey)
        logger.debug("Decode result:\n{}\n", message)
        out.add(message)
    }
}

/**
 * Encode proxy message to output buffer.
 *
 * @param agentPublicKey The public key in agent side.
 */
class ProxyMessageEncoder(private val agentPublicKey: ByteArray) :
    MessageToByteEncoder<ProxyMessage>() {
    private companion object {
        private val logger = KotlinLogging.logger { }
    }

    override fun encode(ctx: ChannelHandlerContext, msg: ProxyMessage, out: ByteBuf) {
        logger.debug("Begin to encode message:\n{}\n", msg)
        encodeMessage(msg, agentPublicKey, out)
    }
}

@ChannelHandler.Sharable
class PrintExceptionHandler : ChannelInboundHandlerAdapter() {
    private companion object {
        private val logger = KotlinLogging.logger { }
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        logger.error(cause) {
            "Exception in the channel pipeline, channel = ${
                ctx.channel().id()
            }"
        }
        ctx.fireExceptionCaught(cause)
    }
}
