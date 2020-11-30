package com.ppaass.kt.common

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufUtil
import io.netty.buffer.Unpooled
import io.netty.util.ReferenceCountUtil
import mu.KotlinLogging
import java.util.*

private val logger = KotlinLogging.logger { }
private val MAGIC_CODE = "__PPAASS__".toByteArray(Charsets.UTF_8)

/**
 * Parse a encryption type form a given value.
 *
 * @param value A byte which can be parsed to EncryptionType
 * @return A encryption type or null if can not parse
 */
private fun Byte?.parseEncryptionType(): EncryptionType? {
    if (this == null) {
        return null
    }
    for (e in EncryptionType.values()) {
        if (e.value() == this) {
            return e
        }
    }
    return null
}

private fun Byte?.parseAgentMessageBodyType(): AgentMessageBodyType? {
    if (this == null) {
        return null
    }
    for (e in AgentMessageBodyType.values()) {
        if (e.value() == this) {
            return e
        }
    }
    return null
}

private fun Byte?.parseProxyMessageBodyType(): ProxyMessageBodyType? {
    if (this == null) {
        return null
    }
    for (e in ProxyMessageBodyType.values()) {
        if (e.value() == this) {
            return e
        }
    }
    return null
}

private fun encryptMessageBody(messageBodyByteArrayBeforeEncrypt: ByteArray,
                               messageBodyBodyEncryptionType: EncryptionType,
                               messageBodyEncryptionToken: String): ByteArray {
    return when (messageBodyBodyEncryptionType) {
        EncryptionType.AES -> aesEncrypt(messageBodyEncryptionToken,
            messageBodyByteArrayBeforeEncrypt);
        EncryptionType.BLOWFISH -> blowfishEncrypt(messageBodyEncryptionToken,
            messageBodyByteArrayBeforeEncrypt);
    };
}

private fun decryptMessageBody(messageBodyByteArrayBeforeDecrypt: ByteArray,
                               messageBodyBodyEncryptionType: EncryptionType,
                               messageBodyEncryptionToken: String): ByteArray {
    return when (messageBodyBodyEncryptionType) {
        EncryptionType.AES -> aesDecrypt(messageBodyEncryptionToken,
            messageBodyByteArrayBeforeDecrypt);
        EncryptionType.BLOWFISH -> blowfishDecrypt(messageBodyEncryptionToken,
            messageBodyByteArrayBeforeDecrypt);
    };
}

private fun <T> encodeMessageBody(messageBody: MessageBody<T>,
                                  messageBodyBodyEncryptionType: EncryptionType,
                                  messageBodyEncryptionToken: String): ByteBuf where T : MessageBodyType, T : Enum<T> {
    val tempBuffer: ByteBuf = Unpooled.buffer()
    val bodyType = messageBody.bodyType.value()
    tempBuffer.writeByte(bodyType.toInt())
    val messageIdByteArray = messageBody.id.toByteArray(Charsets.UTF_8)
    tempBuffer.writeInt(messageIdByteArray.size)
    tempBuffer.writeBytes(messageIdByteArray)
    val userTokenByteArray = messageBody.userToken.toByteArray(Charsets.UTF_8)
    tempBuffer.writeInt(userTokenByteArray.size)
    tempBuffer.writeBytes(userTokenByteArray)
    val targetAddressByteArray = messageBody.targetHost.toByteArray(Charsets.UTF_8)
    tempBuffer.writeInt(targetAddressByteArray.size)
    tempBuffer.writeBytes(targetAddressByteArray)
    tempBuffer.writeInt(messageBody.targetPort)
    val targetOriginalData = messageBody.data
    tempBuffer.writeInt(targetOriginalData.size)
    tempBuffer.writeBytes(targetOriginalData)
    return Unpooled.wrappedBuffer(encryptMessageBody(
        tempBuffer.array(),
        messageBodyBodyEncryptionType,
        messageBodyEncryptionToken))
}

private fun decodeAgentMessageBody(messageBytes: ByteArray,
                                   messageBodyBodyEncryptionType: EncryptionType,
                                   messageBodyEncryptionToken: String): MessageBody<AgentMessageBodyType> {
    val messageBodyBytes =
        decryptMessageBody(messageBytes, messageBodyBodyEncryptionType, messageBodyEncryptionToken)
    val messageBodyByteBuf = Unpooled.wrappedBuffer(messageBodyBytes)
    val bodyType: AgentMessageBodyType =
        messageBodyByteBuf.readByte().parseAgentMessageBodyType() ?: throw PpaassException(
            "Can not parse agent message body type from the message.")
    val messageIdLength = messageBodyByteBuf.readInt()
    val messageId =
        messageBodyByteBuf.readCharSequence(messageIdLength, Charsets.UTF_8).toString()
    val userTokenLength = messageBodyByteBuf.readInt()
    val userToken =
        messageBodyByteBuf.readCharSequence(userTokenLength, Charsets.UTF_8).toString()
    val targetAddressLength = messageBodyByteBuf.readInt()
    val targetAddress = messageBodyByteBuf.readCharSequence(targetAddressLength,
        Charsets.UTF_8).toString()
    val targetPort = messageBodyByteBuf.readInt()
    val originalDataLength = messageBodyByteBuf.readInt()
    val originalData = ByteArray(originalDataLength)
    messageBodyByteBuf.readBytes(originalData)
    return MessageBody(id = messageId, bodyType = bodyType, userToken = userToken,
        targetHost = targetAddress,
        targetPort = targetPort, data = originalData)
}

private fun decodeProxyMessageBody(messageBytes: ByteArray,
                                   messageBodyBodyEncryptionType: EncryptionType,
                                   messageBodyEncryptionToken: String): MessageBody<ProxyMessageBodyType> {
    val messageBodyBytes =
        decryptMessageBody(messageBytes, messageBodyBodyEncryptionType, messageBodyEncryptionToken)
    val messageBodyByteBuf = Unpooled.wrappedBuffer(messageBodyBytes)
    val bodyType: ProxyMessageBodyType =
        messageBodyByteBuf.readByte().parseProxyMessageBodyType() ?: throw PpaassException(
            "Can not parse proxy message body type from the message.")
    val messageIdLength = messageBodyByteBuf.readInt()
    val messageId =
        messageBodyByteBuf.readCharSequence(messageIdLength, Charsets.UTF_8).toString()
    val userTokenLength = messageBodyByteBuf.readInt()
    val userToken =
        messageBodyByteBuf.readCharSequence(userTokenLength, Charsets.UTF_8).toString()
    val targetAddressLength = messageBodyByteBuf.readInt()
    val targetAddress = messageBodyByteBuf.readCharSequence(targetAddressLength,
        Charsets.UTF_8).toString()
    val targetPort = messageBodyByteBuf.readInt()
    val originalDataLength = messageBodyByteBuf.readInt()
    val originalData = ByteArray(originalDataLength)
    messageBodyByteBuf.readBytes(originalData)
    return MessageBody(id = messageId, bodyType = bodyType, userToken = userToken,
        targetHost = targetAddress,
        targetPort = targetPort, data = originalData)
}

/**
 * Generate a random UUID
 */
fun generateUuid() = UUID.randomUUID().toString().replace("-", "")

/**
 * Json object mapper
 */
val JSON_OBJECT_MAPPER = jacksonObjectMapper()

/**
 * Encode a message to byte buffer.
 *
 * @param message The message to encode.
 * @param publicKeyString The public key base64 string
 * @param output The output byte buffer
 */
fun <T> encodeMessage(message: Message<T>,
                      publicKeyString: String,
                      output: ByteBuf) where T : Enum<T>, T : MessageBodyType {
    output.writeBytes(MAGIC_CODE)
    val originalMessageBodyEncryptionToken = message.encryptionToken
    val encryptedMessageBodyEncryptionToken = rsaEncrypt(originalMessageBodyEncryptionToken,
        publicKeyString)
    output.writeInt(encryptedMessageBodyEncryptionToken.length)
    output.writeCharSequence(encryptedMessageBodyEncryptionToken,Charsets.UTF_8)
    output.writeByte(message.encryptionType.value().toInt())
    val bodyByteBuf: ByteBuf = encodeMessageBody<T>(message.body,
        message.encryptionType,
        originalMessageBodyEncryptionToken)
    output.writeBytes(bodyByteBuf)
}

/**
 * Decode agent message from input byte buffer.
 * @param input The input byte buffer.
 * @param proxyPrivateKeyString The proxy private key base64 string
 * @return The agent message
 */
fun decodeAgentMessage(input: ByteBuf,
                       proxyPrivateKeyString: String): AgentMessage {
    val magicCodeByteBuf = input.readBytes(MAGIC_CODE.size)
    if (magicCodeByteBuf.compareTo(Unpooled.wrappedBuffer(MAGIC_CODE)) != 0) {
        logger.error {
            "Incoming agent message is not follow Ppaass protocol, incoming message is:\n${
                ByteBufUtil.prettyHexDump(input)
            }\n"
        }
        throw PpaassException("Incoming message is not follow Ppaass protocol.")
    }
    val encryptedMessageBodyEncryptionTokenLength = input.readInt()
    val encryptedMessageBodyEncryptionToken =
        input.readCharSequence(encryptedMessageBodyEncryptionTokenLength, Charsets.UTF_8)
            .toString()
    val messageBodyEncryptionToken: String =
        rsaDecrypt(encryptedMessageBodyEncryptionToken,
            proxyPrivateKeyString)
    val messageBodyEncryptionType = input.readByte().parseEncryptionType() ?: throw PpaassException(
        "Can not parse encryption type from the message.");
    val messageBodyByteArray = ByteArray(input.readableBytes())
    input.readBytes(messageBodyByteArray)
    return AgentMessage(encryptionToken = messageBodyEncryptionToken,
        encryptionType = messageBodyEncryptionType,
        body = decodeAgentMessageBody(
            messageBytes = messageBodyByteArray,
            messageBodyBodyEncryptionType = messageBodyEncryptionType,
            messageBodyEncryptionToken = messageBodyEncryptionToken))
}

/**
 * Decode proxy message from input byte buffer.
 * @param input The input byte buffer.
 * @param proxyPrivateKeyString The agent private key base64 string
 * @return The proxy message
 */
fun decodeProxyMessage(input: ByteBuf,
                       agentPrivateKeyString: String): ProxyMessage {
    val magicCodeByteBuf = input.readBytes(MAGIC_CODE.size)
    if (magicCodeByteBuf.compareTo(Unpooled.wrappedBuffer(MAGIC_CODE)) != 0) {
        logger.error {
            "Incoming proxy message is not follow Ppaass protocol, incoming message is:\n${
                ByteBufUtil.prettyHexDump(input)
            }\n"
        }
        throw PpaassException("Incoming message is not follow Ppaass protocol.")
    }
    ReferenceCountUtil.release(magicCodeByteBuf)
    val encryptedMessageBodyEncryptionTokenLength = input.readInt()
    val encryptedMessageBodyEncryptionToken =
        input.readCharSequence(encryptedMessageBodyEncryptionTokenLength, Charsets.UTF_8)
            .toString()
    val messageBodyEncryptionToken: String =
        rsaDecrypt(encryptedMessageBodyEncryptionToken,
            agentPrivateKeyString)
    val messageBodyEncryptionType = input.readByte().parseEncryptionType() ?: throw PpaassException(
        "Can not parse encryption type from the message.");
    val messageBodyByteArray = ByteArray(input.readableBytes())
    input.readBytes(messageBodyByteArray)
    return ProxyMessage(encryptionToken = messageBodyEncryptionToken,
        encryptionType = messageBodyEncryptionType,
        body = decodeProxyMessageBody(
            messageBytes = messageBodyByteArray,
            messageBodyBodyEncryptionType = messageBodyEncryptionType,
            messageBodyEncryptionToken = messageBodyEncryptionToken))
}
