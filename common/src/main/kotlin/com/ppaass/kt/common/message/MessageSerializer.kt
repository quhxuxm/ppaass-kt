package com.ppaass.kt.common.message

import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufAllocator
import org.slf4j.LoggerFactory

object MessageSerializer {
    val logger = LoggerFactory.getLogger(MessageSerializer::class.java);
    fun <T : IMessageBody<T>> encode(message: Message<T>): ByteBuf {
        logger.debug("Encode message.")
        val result = ByteBufAllocator.DEFAULT.buffer()
        result.writeInt(message.secureToken.length)
        result.writeBytes(message.secureToken.toByteArray(Charsets.UTF_8))
        result.writeInt(message.encryptionType.mask.length)
        result.writeBytes(message.encryptionType.mask.toByteArray(Charsets.UTF_8))
        val messageBody = message.body;
        when (messageBody) {
            is ProxyMessageBody -> {
                result.writeBytes(MessageBodySerializer.encodeProxyMessageBody(messageBody, message.encryptionType, message.secureToken));
            }
            is AgentMessageBody -> {
                result.writeBytes(MessageBodySerializer.encodeAgentMessageBody(messageBody, message.encryptionType, message.secureToken));
            }
        }
        return result
    }

    fun <T : IMessageBody<T>> decode(messageBytes: ByteBuf, messageBodyClass: Class<T>): Message<T>? {
        val secureTokenLength = messageBytes.readInt()
        val secureToken = messageBytes.readCharSequence(secureTokenLength, Charsets.UTF_8).toString()
        val encryptionTypeMaskLength = messageBytes.readInt()
        val encryptionTypeMask = messageBytes.readCharSequence(encryptionTypeMaskLength, Charsets.UTF_8).toString()
        val encryptionType = MessageEncryptionType.fromMask(encryptionTypeMask)
        var messageBodyByteBuf = messageBytes.readBytes(messageBytes.readableBytes())
        when (messageBodyClass) {
            AgentMessageBody::class.java -> {
                val agentMessageBody = MessageBodySerializer.decodeAgentMessageBody(messageBodyByteBuf, encryptionType, secureToken)
                return Message<AgentMessageBody>(secureToken, encryptionType, agentMessageBody);
            }
        }

        return Message<T>();
    }
}

