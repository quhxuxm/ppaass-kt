package com.ppaass.kt.common.message

import com.ppaass.kt.common.exception.PpaassException
import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.Unpooled
import io.netty.util.ReferenceCountUtil
import org.apache.commons.codec.digest.DigestUtils
import org.slf4j.LoggerFactory
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * The encryption util to encrypt the message
 */
private object MessageBodyEncryptionUtil {
    private fun secureTokenToBytes(secureToken: String): ByteArray? {
        return DigestUtils.md5(secureToken)
    }

    fun encrypt(data: ByteArray, messageBodyBodyEncryptionType: MessageBodyEncryptionType,
                secureToken: String): ByteArray {
        val key = SecretKeySpec(this.secureTokenToBytes(secureToken), "AES")
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val encryptedByteArray: ByteArray
        when (messageBodyBodyEncryptionType) {
            MessageBodyEncryptionType.AES_BASE64 -> {
                encryptedByteArray = Base64.getEncoder().encode(cipher.doFinal(data))
            }
            MessageBodyEncryptionType.BASE64_AES -> {
                encryptedByteArray = cipher.doFinal(Base64.getEncoder().encode(data))
            }
        }
        return encryptedByteArray
    }

    fun decrypt(encryptedData: ByteArray, messageBodyBodyEncryptionType: MessageBodyEncryptionType,
                secureToken: String): ByteArray {
        val key = SecretKeySpec(this.secureTokenToBytes(secureToken), "AES")
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, key)
        val decryptedByteArray: ByteArray
        when (messageBodyBodyEncryptionType) {
            MessageBodyEncryptionType.AES_BASE64 -> {
                val aesData = Base64.getDecoder().decode(encryptedData)
                decryptedByteArray = cipher.doFinal(aesData)
            }
            MessageBodyEncryptionType.BASE64_AES -> {
                val bas64Data = cipher.doFinal(encryptedData)
                decryptedByteArray = Base64.getDecoder().decode(bas64Data)
            }
        }
        return decryptedByteArray
    }
}

/**
 * The serializer to serialize the message body
 */
private object MessageBodySerializer {
    val logger = LoggerFactory.getLogger(MessageBodySerializer::class.java);

    fun encodeProxyMessageBody(message: ProxyMessageBody?, messageBodyBodyEncryptionType: MessageBodyEncryptionType,
                               secureToken: String): ByteArray {
        logger.debug("Encode proxy message body.")
        val result = ByteBufAllocator.DEFAULT.buffer()
        val bodyTypeByteArray = message?.bodyType?.name?.toByteArray(Charsets.UTF_8) ?: byteArrayOf()
        result.writeInt(bodyTypeByteArray.size)
        result.writeBytes(bodyTypeByteArray)
        val messageIdByteArray = message?.id?.toByteArray(Charsets.UTF_8) ?: byteArrayOf();
        result.writeInt(messageIdByteArray.size)
        result.writeBytes(messageIdByteArray)
        val targetAddressByteArray = message?.targetAddress?.toByteArray() ?: byteArrayOf()
        result.writeInt(targetAddressByteArray.size)
        result.writeBytes(targetAddressByteArray)
        val targetPortWriteToByteArray = message?.targetPort ?: -1
        result.writeInt(targetPortWriteToByteArray)
        val targetOriginalDataByteArray = message?.originalData ?: byteArrayOf()
        result.writeInt(targetOriginalDataByteArray.size)
        result.writeBytes(targetOriginalDataByteArray)
        val resultByteArray = ByteArray(result.readableBytes())
        result.readBytes(resultByteArray)
        ReferenceCountUtil.release(result)
        val encryptedResult =
                MessageBodyEncryptionUtil.encrypt(resultByteArray, messageBodyBodyEncryptionType, secureToken)
        return encryptedResult
    }

    fun encodeAgentMessageBody(message: AgentMessageBody?, messageBodyBodyEncryptionType: MessageBodyEncryptionType,
                               secureToken: String): ByteArray {
        logger.debug("Encode agent message body.")
        val result = ByteBufAllocator.DEFAULT.buffer()
        val bodyTypeByteArray = message?.bodyType?.name?.toByteArray(Charsets.UTF_8) ?: byteArrayOf()
        result.writeInt(bodyTypeByteArray.size)
        result.writeBytes(bodyTypeByteArray)
        val messageIdByteArray = message?.id?.toByteArray(Charsets.UTF_8) ?: byteArrayOf();
        result.writeInt(messageIdByteArray.size)
        result.writeBytes(messageIdByteArray)
        val targetAddressByteArray = message?.targetAddress?.toByteArray() ?: byteArrayOf()
        result.writeInt(targetAddressByteArray.size)
        result.writeBytes(targetAddressByteArray)
        val targetPortWriteToByteArray = message?.targetPort ?: -1
        result.writeInt(targetPortWriteToByteArray)
        val targetOriginalDataByteArray = message?.originalData ?: byteArrayOf()
        result.writeInt(targetOriginalDataByteArray.size)
        result.writeBytes(targetOriginalDataByteArray)
        val resultByteArray = ByteArray(result.readableBytes())
        result.readBytes(resultByteArray)
        ReferenceCountUtil.release(result)
        val encryptedResult =
                MessageBodyEncryptionUtil.encrypt(resultByteArray, messageBodyBodyEncryptionType, secureToken)
        return encryptedResult
    }

    fun decodeAgentMessageBody(messageBytes: ByteArray, messageBodyBodyEncryptionType: MessageBodyEncryptionType,
                               secureToken: String): AgentMessageBody {
        val messageBodyBytes =
                MessageBodyEncryptionUtil.decrypt(messageBytes, messageBodyBodyEncryptionType, secureToken);
        val messageBodyByteBuf = Unpooled.wrappedBuffer(messageBodyBytes)
        val bodyTypeNameLength = messageBodyByteBuf.readInt()
        val bodyTypeName = messageBodyByteBuf.readCharSequence(bodyTypeNameLength, Charsets.UTF_8).toString()
        val bodyType = AgentMessageBodyType.valueOf(bodyTypeName)
        val messageIdLength = messageBodyByteBuf.readInt()
        val messageId = messageBodyByteBuf.readCharSequence(messageIdLength, Charsets.UTF_8).toString()
        val targetAddressLength = messageBodyByteBuf.readInt()
        val targetAddress = if (targetAddressLength == 0) {
            null
        } else {
            messageBodyByteBuf.readCharSequence(targetAddressLength, Charsets.UTF_8).toString()
        }
        val targetPort = messageBodyByteBuf.readInt()
        val originalDataLength = messageBodyByteBuf.readInt()
        val originalData = if (originalDataLength == 0) {
            null
        } else {
            val tempOriginalData = ByteArray(originalDataLength)
            messageBodyByteBuf.readBytes(tempOriginalData)
            tempOriginalData
        }
        ReferenceCountUtil.release(messageBodyByteBuf)
        val agentMessageBody = AgentMessageBody(bodyType, messageId)
        agentMessageBody.originalData = originalData
        agentMessageBody.targetPort = targetPort
        agentMessageBody.targetAddress = targetAddress
        return agentMessageBody
    }

    fun decodeProxyMessageBody(messageBytes: ByteArray, messageBodyBodyEncryptionType: MessageBodyEncryptionType,
                               secureToken: String): ProxyMessageBody {
        val messageBodyBytes =
                MessageBodyEncryptionUtil.decrypt(messageBytes, messageBodyBodyEncryptionType, secureToken);
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
        val originalData = if (originalDataLength == 0) {
            null
        } else {
            val tempOriginalData = ByteArray(originalDataLength)
            messageBodyByteBuf.readBytes(tempOriginalData)
            tempOriginalData
        }
        ReferenceCountUtil.release(messageBodyByteBuf)
        val proxyMessageBody = ProxyMessageBody(bodyType, messageId)
        proxyMessageBody.originalData = originalData
        proxyMessageBody.targetPort = targetPort
        proxyMessageBody.targetAddress = targetAddress
        return proxyMessageBody
    }
}

/**
 * The serializer to serialize the message
 */
internal object MessageSerializer {
    val logger = LoggerFactory.getLogger(MessageSerializer::class.java);

    fun encodeAgentMessage(message: AgentMessage): ByteArray {
        val result = ByteBufAllocator.DEFAULT.buffer()
        result.writeInt(message.secureToken.length)
        result.writeBytes(message.secureToken.toByteArray(Charsets.UTF_8))
        result.writeInt(message.messageBodyEncryptionType.mask.length)
        result.writeBytes(message.messageBodyEncryptionType.mask.toByteArray(Charsets.UTF_8))
        val bodyByteBuf = MessageBodySerializer.encodeAgentMessageBody(message.body,
                message.messageBodyEncryptionType,
                message.secureToken)
        result.writeBytes(bodyByteBuf);
        val resultByteArray = ByteArray(result.readableBytes())
        result.readBytes(resultByteArray)
        ReferenceCountUtil.release(result)
        return resultByteArray
    }

    fun encodeProxyMessage(message: ProxyMessage): ByteArray {
        val result = ByteBufAllocator.DEFAULT.buffer()
        result.writeInt(message.secureToken.length)
        result.writeBytes(message.secureToken.toByteArray(Charsets.UTF_8))
        result.writeInt(message.messageBodyEncryptionType.mask.length)
        result.writeBytes(message.messageBodyEncryptionType.mask.toByteArray(Charsets.UTF_8))
        val bodyByteBuf = MessageBodySerializer.encodeProxyMessageBody(message.body,
                message.messageBodyEncryptionType,
                message.secureToken)
        result.writeBytes(bodyByteBuf);
        val resultByteArray = ByteArray(result.readableBytes())
        result.readBytes(resultByteArray)
        ReferenceCountUtil.release(result)
        return resultByteArray
    }

    fun decodeAgentMessage(messageBytesArray: ByteArray): AgentMessage {
        val messageBytes = Unpooled.wrappedBuffer(messageBytesArray)
        val secureTokenLength = messageBytes.readInt()
        val secureToken = messageBytes.readCharSequence(secureTokenLength, Charsets.UTF_8).toString()
        val encryptionTypeMaskLength = messageBytes.readInt()
        val encryptionTypeMask = messageBytes.readCharSequence(encryptionTypeMaskLength, Charsets.UTF_8).toString()
        val encryptionType = MessageBodyEncryptionType.fromMask(encryptionTypeMask) ?: throw PpaassException()
        val messageBodyByteArray = ByteArray(messageBytes.readableBytes())
        messageBytes.readBytes(messageBodyByteArray)
        ReferenceCountUtil.release(messageBytes)
        val agentMessage = AgentMessage(secureToken, encryptionType,
                MessageBodySerializer.decodeAgentMessageBody(messageBodyByteArray, encryptionType, secureToken))
        return agentMessage
    }

    fun decodeProxyMessage(messageBytesArray: ByteArray): ProxyMessage {
        val messageBytes = Unpooled.wrappedBuffer(messageBytesArray)
        val secureTokenLength = messageBytes.readInt()
        val secureToken = messageBytes.readCharSequence(secureTokenLength, Charsets.UTF_8).toString()
        val encryptionTypeMaskLength = messageBytes.readInt()
        val encryptionTypeMask = messageBytes.readCharSequence(encryptionTypeMaskLength, Charsets.UTF_8).toString()
        val encryptionType = MessageBodyEncryptionType.fromMask(encryptionTypeMask) ?: throw PpaassException()
        val messageBodyByteArray = ByteArray(messageBytes.readableBytes())
        messageBytes.readBytes(messageBodyByteArray)
        ReferenceCountUtil.release(messageBytes)
        val proxyMessage = ProxyMessage(secureToken, encryptionType,
                MessageBodySerializer.decodeProxyMessageBody(messageBodyByteArray, encryptionType, secureToken))
        return proxyMessage
    }
}

