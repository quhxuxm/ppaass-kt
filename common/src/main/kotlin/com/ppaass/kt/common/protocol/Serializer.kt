package com.ppaass.kt.common.protocol

import com.ppaass.kt.common.exception.PpaassException
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import mu.KotlinLogging
import org.apache.commons.codec.digest.DigestUtils
import java.util.*

private val logger = KotlinLogging.logger {}
private const val MAGIC_CODE = "__PPAASS__"

/**
 * Encrypt the message body.
 *
 * @param messageBodyByteArrayBeforeEncrypt The message body byte array before encrypt.
 * @param messageBodyBodyEncryptionType The message body encryption type.
 * @param messageBodyEncryptionToken The message body encryption token.
 * @return The encrypt message body byte array
 */
private fun encryptMessageBody(messageBodyByteArrayBeforeEncrypt: ByteArray, messageBodyBodyEncryptionType: MessageBodyEncryptionType,
                               messageBodyEncryptionToken: String): ByteArray {
    //Read bytes from direct buffer to heap as byte array
    val encryptedByteArray: ByteArray
    when (messageBodyBodyEncryptionType) {
        MessageBodyEncryptionType.AES -> {
            encryptedByteArray = aesEncrypt(messageBodyEncryptionToken, messageBodyByteArrayBeforeEncrypt)
        }
        MessageBodyEncryptionType.BLOWFISH -> {
            encryptedByteArray = blowfishEncrypt(messageBodyEncryptionToken, messageBodyByteArrayBeforeEncrypt)
        }
    }
    //Create heap byte buffer
    return encryptedByteArray
}

/**
 * Decrypt the message body.
 *
 * @param messageBodyByteArrayBeforeDecrypt The message body byte array before decrypt.
 * @param messageBodyBodyEncryptionType The message body encryption type.
 * @param messageBodyEncryptionToken The message body encryption token.
 * return The decrypt message body byte array
 */
private fun decryptMessageBody(messageBodyByteArrayBeforeDecrypt: ByteArray,
                               messageBodyBodyEncryptionType: MessageBodyEncryptionType,
                               messageBodyEncryptionToken: String): ByteArray {
    val decryptedByteArray: ByteArray
    when (messageBodyBodyEncryptionType) {
        MessageBodyEncryptionType.AES -> {
            //Decrypt in heap
            decryptedByteArray =
                    aesDecrypt(messageBodyEncryptionToken, messageBodyByteArrayBeforeDecrypt)
        }
        MessageBodyEncryptionType.BLOWFISH -> {
            //Decrypt in heap
            decryptedByteArray =
                    blowfishDecrypt(messageBodyEncryptionToken, messageBodyByteArrayBeforeDecrypt)
        }
    }
    //Create heap byte buffer
    return decryptedByteArray
}

/**
 * Encode a heap proxy message body object to heap byte buffer
 */
private fun encodeProxyMessageBody(message: ProxyMessageBody?, messageBodyBodyEncryptionType: MessageBodyEncryptionType,
                                   messageBodyEncryptionToken: String): ByteBuf {
    logger.debug("Encode proxy message body.")
    val tempBuffer = Unpooled.buffer()
    val bodyTypeByteArray = message?.bodyType?.name?.toByteArray(Charsets.UTF_8) ?: byteArrayOf()
    tempBuffer.writeInt(bodyTypeByteArray.size)
    tempBuffer.writeBytes(bodyTypeByteArray)
    val messageIdByteArray = message?.id?.toByteArray(Charsets.UTF_8) ?: byteArrayOf();
    tempBuffer.writeInt(messageIdByteArray.size)
    tempBuffer.writeBytes(messageIdByteArray)
    val targetAddressByteArray = message?.targetAddress?.toByteArray() ?: byteArrayOf()
    tempBuffer.writeInt(targetAddressByteArray.size)
    tempBuffer.writeBytes(targetAddressByteArray)
    val targetPortWriteToByteArray = message?.targetPort ?: -1
    tempBuffer.writeInt(targetPortWriteToByteArray)
    val targetOriginalData = message?.originalData ?: ByteArray(0)
    tempBuffer.writeInt(targetOriginalData.size)
    tempBuffer.writeBytes(targetOriginalData)
    return Unpooled.wrappedBuffer(encryptMessageBody(
            tempBuffer.array(),
            messageBodyBodyEncryptionType,
            messageBodyEncryptionToken))
}

/**
 * Encode a heap agent message body object to heap byte buffer
 */
private fun encodeAgentMessageBody(message: AgentMessageBody?, messageBodyBodyEncryptionType: MessageBodyEncryptionType,
                                   messageBodyEncryptionToken: String): ByteBuf {
    logger.debug("Encode agent message body.")
    val tempBuf = Unpooled.buffer()
    val bodyTypeByteArray = message?.bodyType?.name?.toByteArray(Charsets.UTF_8) ?: byteArrayOf()
    tempBuf.writeInt(bodyTypeByteArray.size)
    tempBuf.writeBytes(bodyTypeByteArray)
    val messageIdByteArray = message?.id?.toByteArray(Charsets.UTF_8) ?: byteArrayOf();
    tempBuf.writeInt(messageIdByteArray.size)
    tempBuf.writeBytes(messageIdByteArray)
    val securityTokenArray = message?.securityToken?.toByteArray(Charsets.UTF_8) ?: byteArrayOf();
    tempBuf.writeInt(securityTokenArray.size)
    tempBuf.writeBytes(securityTokenArray)
    val targetAddressByteArray = message?.targetAddress?.toByteArray() ?: byteArrayOf()
    tempBuf.writeInt(targetAddressByteArray.size)
    tempBuf.writeBytes(targetAddressByteArray)
    val targetPortWriteToByteArray = message?.targetPort ?: -1
    tempBuf.writeInt(targetPortWriteToByteArray)
    val targetOriginalData = message?.originalData ?: ByteArray(0)
    tempBuf.writeInt(targetOriginalData.size)
    tempBuf.writeBytes(targetOriginalData)
    val encryptedResult =
            encryptMessageBody(tempBuf.array(),
                    messageBodyBodyEncryptionType,
                    messageBodyEncryptionToken)
    return Unpooled.wrappedBuffer(encryptedResult)
}

private fun decodeAgentMessageBody(messageBytes: ByteArray, messageBodyBodyEncryptionType: MessageBodyEncryptionType,
                                   messageBodyEncryptionToken: String): AgentMessageBody {
    val messageBodyBytes =
            decryptMessageBody(messageBytes, messageBodyBodyEncryptionType, messageBodyEncryptionToken);
    val messageBodyByteBuf = Unpooled.wrappedBuffer(messageBodyBytes)
    val bodyTypeNameLength = messageBodyByteBuf.readInt()
    val bodyTypeName = messageBodyByteBuf.readCharSequence(bodyTypeNameLength, Charsets.UTF_8).toString()
    val bodyType = AgentMessageBodyType.valueOf(bodyTypeName)
    val messageIdLength = messageBodyByteBuf.readInt()
    val messageId = messageBodyByteBuf.readCharSequence(messageIdLength, Charsets.UTF_8).toString()
    val securityTokenLength = messageBodyByteBuf.readInt()
    val securityToken = messageBodyByteBuf.readCharSequence(securityTokenLength, Charsets.UTF_8).toString()
    val targetAddressLength = messageBodyByteBuf.readInt()
    val targetAddress = if (targetAddressLength == 0) {
        null
    } else {
        messageBodyByteBuf.readCharSequence(targetAddressLength,
                Charsets.UTF_8).toString()
    }
    val targetPort = messageBodyByteBuf.readInt()
    val originalDataLength = messageBodyByteBuf.readInt()
    val originalData = ByteArray(originalDataLength)
    messageBodyByteBuf.readBytes(originalData)
    val agentMessageBody = AgentMessageBody(bodyType, messageId, securityToken)
    agentMessageBody.originalData = originalData
    agentMessageBody.targetPort = targetPort
    agentMessageBody.targetAddress = targetAddress
    return agentMessageBody
}

private fun decodeProxyMessageBody(messageBytes: ByteArray, messageBodyBodyEncryptionType: MessageBodyEncryptionType,
                                   messageBodyEncryptionToken: String): ProxyMessageBody {
    val messageBodyBytes =
            decryptMessageBody(messageBytes, messageBodyBodyEncryptionType, messageBodyEncryptionToken);
    val messageBodyByteBuf = Unpooled.wrappedBuffer(messageBodyBytes)
    val bodyTypeNameLength = messageBodyByteBuf.readInt()
    val bodyTypeName = messageBodyByteBuf.readCharSequence(bodyTypeNameLength,
            Charsets.UTF_8).toString()
    val bodyType = ProxyMessageBodyType.valueOf(bodyTypeName)
    val messageIdLength = messageBodyByteBuf.readInt()
    val messageId = messageBodyByteBuf.readCharSequence(messageIdLength, Charsets.UTF_8)
            .toString()
    val targetAddressLength = messageBodyByteBuf.readInt()
    val targetAddress =
            if (targetAddressLength == 0) {
                null
            } else {
                messageBodyByteBuf.readCharSequence(targetAddressLength,
                        Charsets.UTF_8).toString()
            }
    val targetPort = messageBodyByteBuf.readInt()
    val originalDataLength = messageBodyByteBuf.readInt()
    val originalData = ByteArray(originalDataLength)
    messageBodyByteBuf.readBytes(originalData)
    val proxyMessageBody = ProxyMessageBody(bodyType, messageId)
    proxyMessageBody.originalData = originalData
    proxyMessageBody.targetPort = targetPort
    proxyMessageBody.targetAddress = targetAddress
    return proxyMessageBody
}

internal fun encodeAgentMessage(message: AgentMessage, proxyPublicKeyString: String, output: ByteBuf) {
    val originalMessageBodyEncryptionToken = DigestUtils.md5Hex(UUID.randomUUID().toString())
    val encryptedMessageBodyEncryptionToken =
            rsaEncrypt(originalMessageBodyEncryptionToken, proxyPublicKeyString)
    output.writeCharSequence(MAGIC_CODE, Charsets.UTF_8)
    output.writeInt(encryptedMessageBodyEncryptionToken.length)
    output.writeBytes(encryptedMessageBodyEncryptionToken.toByteArray(Charsets.UTF_8))
    output.writeInt(message.messageBodyEncryptionType.mask.length)
    output.writeBytes(message.messageBodyEncryptionType.mask.toByteArray(Charsets.UTF_8))
    val bodyByteArray = encodeAgentMessageBody(message.body,
            message.messageBodyEncryptionType,
            originalMessageBodyEncryptionToken)
    output.writeBytes(bodyByteArray);
}

internal fun encodeProxyMessage(message: ProxyMessage, agentPublicKeyString: String, output: ByteBuf) {
    val originalMessageBodyEncryptionToken = DigestUtils.md5Hex(UUID.randomUUID().toString())
    val encryptedMessageBodyEncryptionToken =
            rsaEncrypt(originalMessageBodyEncryptionToken, agentPublicKeyString)
    output.writeCharSequence(MAGIC_CODE, Charsets.UTF_8)
    output.writeInt(encryptedMessageBodyEncryptionToken.length)
    output.writeBytes(encryptedMessageBodyEncryptionToken.toByteArray(Charsets.UTF_8))
    output.writeInt(message.messageBodyEncryptionType.mask.length)
    output.writeBytes(message.messageBodyEncryptionType.mask.toByteArray(Charsets.UTF_8))
    val bodyByteArray = encodeProxyMessageBody(message.body,
            message.messageBodyEncryptionType,
            originalMessageBodyEncryptionToken)
    output.writeBytes(bodyByteArray);
}

internal fun decodeAgentMessage(input: ByteBuf, proxyPrivateKeyString: String): AgentMessage {
    val magicCode = input.readCharSequence(MAGIC_CODE.toByteArray().size, Charsets.UTF_8)
    if (MAGIC_CODE != magicCode) {
        logger.error { "Incoming message is not follow Ppaass protocol." }
        throw PpaassException("Incoming message is not follow Ppaass protocol.")
    }
    val encryptedMessageBodyEncryptionTokenLength = input.readInt()
    val encryptedMessageBodyEncryptionToken =
            input.readCharSequence(encryptedMessageBodyEncryptionTokenLength, Charsets.UTF_8).toString()
    val messageBodyEncryptionToken =
            rsaDecrypt(encryptedMessageBodyEncryptionToken, proxyPrivateKeyString)
    val messageBodyEncryptionTypeMaskLength = input.readInt()
    val messageBodyEncryptionTypeMask =
            input.readCharSequence(messageBodyEncryptionTypeMaskLength, Charsets.UTF_8).toString()
    val messageBodyEncryptionType =
            MessageBodyEncryptionType.fromMask(messageBodyEncryptionTypeMask) ?: throw PpaassException()
    val messageBodyByteArray = ByteArray(input.readableBytes())
    input.readBytes(messageBodyByteArray)
    val agentMessage = AgentMessage(messageBodyEncryptionToken, messageBodyEncryptionType,
            decodeAgentMessageBody(messageBodyByteArray, messageBodyEncryptionType, messageBodyEncryptionToken))
    return agentMessage
}

internal fun decodeProxyMessage(input: ByteBuf, agentPrivateKeyString: String): ProxyMessage {
    val magicCode = input.readCharSequence(MAGIC_CODE.toByteArray().size, Charsets.UTF_8)
    if (MAGIC_CODE != magicCode) {
        logger.error { "Incoming message is not follow Ppaass protocol." }
        throw PpaassException("Incoming message is not follow Ppaass protocol.")
    }
    val encryptedMessageBodyEncryptionTokenLength = input.readInt()
    val encryptedMessageBodyEncryptionToken =
            input.readCharSequence(encryptedMessageBodyEncryptionTokenLength, Charsets.UTF_8).toString()
    val messageBodyEncryptionToken =
            rsaDecrypt(encryptedMessageBodyEncryptionToken, agentPrivateKeyString)
    val messageBodyEncryptionTypeMaskLength = input.readInt()
    val messageBodyEncryptionTypeMask =
            input.readCharSequence(messageBodyEncryptionTypeMaskLength, Charsets.UTF_8).toString()
    val messageBodyEncryptionType =
            MessageBodyEncryptionType.fromMask(messageBodyEncryptionTypeMask) ?: throw PpaassException()
    val messageBodyByteArray = ByteArray(input.readableBytes())
    input.readBytes(messageBodyByteArray)
    val proxyMessage = ProxyMessage(messageBodyEncryptionToken, messageBodyEncryptionType,
            decodeProxyMessageBody(messageBodyByteArray, messageBodyEncryptionType, messageBodyEncryptionToken))
    return proxyMessage
}


fun main() {
    logger.info { "Generate agent RSA key pair:" }
    generateRsaKeyPair()
    logger.info { "Generate proxy RSA key pair:" }
    generateRsaKeyPair()
}


