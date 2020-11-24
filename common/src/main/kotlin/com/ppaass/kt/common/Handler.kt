package com.ppaass.kt.common

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageDecoder
import io.netty.handler.codec.MessageToByteEncoder
import mu.KotlinLogging

/**
 * Decode input buffer to agent message.
 *
 * @param proxyPrivateKeyString The private key base 64 string in proxy side.
 */
class AgentMessageDecoder(private val proxyPrivateKeyString: String) :
    ByteToMessageDecoder() {
    private companion object {
        private val logger = KotlinLogging.logger { }
    }

    override fun decode(ctx: ChannelHandlerContext, input: ByteBuf, out: MutableList<Any>) {
        val message: AgentMessage = decodeAgentMessage(
            input, proxyPrivateKeyString)
        logger.debug("Decode result:\n{}\n", message)
        out.add(message)
    }
}

/**
 * Encode agent message to output buffer.
 *
 * @param proxyPublicKeyString The public key base 64 string in proxy side.
 */
class AgentMessageEncoder(private val proxyPublicKeyString: String) :
    MessageToByteEncoder<AgentMessage>() {
    private companion object {
        private val logger = KotlinLogging.logger { }
    }

    override fun encode(ctx: ChannelHandlerContext, msg: AgentMessage, out: ByteBuf) {
        logger.debug("Begin to encode message:\n{}\n", msg)
        encodeMessage(msg, proxyPublicKeyString, out)
    }
}

/**
 * Decode input buffer to proxy message.
 *
 * @param agentPrivateKeyString The private key base 64 string in agent side.
 */
class ProxyMessageDecoder(private val agentPrivateKeyString: String) :
    ByteToMessageDecoder() {
    private companion object {
        private val logger = KotlinLogging.logger { }
    }

    override fun decode(ctx: ChannelHandlerContext, input: ByteBuf, out: MutableList<Any>) {
        val message: ProxyMessage = decodeProxyMessage(input,
            agentPrivateKeyString)
        logger.debug("Decode result:\n{}\n", message)
        out.add(message)
    }
}

/**
 * Encode proxy message to output buffer.
 *
 * @param agentPublicKeyString The public key base 64 string in agent side.
 */
class ProxyMessageEncoder(private val agentPublicKeyString: String) :
    MessageToByteEncoder<ProxyMessage>() {
    private companion object {
        private val logger = KotlinLogging.logger { }
    }

    override fun encode(ctx: ChannelHandlerContext, msg: ProxyMessage, out: ByteBuf) {
        logger.debug("Begin to encode message:\n{}\n", msg)
        encodeMessage(msg, agentPublicKeyString, out)
    }
}
