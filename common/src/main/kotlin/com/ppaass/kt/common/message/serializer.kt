package com.ppaass.kt.common.message

import com.ppaass.kt.common.exception.PpaassException
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.ByteBufUtil
import io.netty.buffer.Unpooled
import io.netty.util.ReferenceCountUtil
import org.apache.commons.codec.digest.DigestUtils
import org.slf4j.LoggerFactory
import java.security.Key
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

    fun encrypt(bytes: ByteBuf, messageBodyBodyEncryptionType: MessageBodyEncryptionType,
                secureToken: String): ByteBuf {
        val key: Key = SecretKeySpec(this.secureTokenToBytes(secureToken), "AES")
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val data = ByteBufUtil.getBytes(bytes)
        val encryptedByteArray: ByteArray
        when (messageBodyBodyEncryptionType) {
            MessageBodyEncryptionType.AES_BASE64 -> {
                encryptedByteArray = Base64.getEncoder().encode(cipher.doFinal(data))
            }
            MessageBodyEncryptionType.BASE64_AES -> {
                encryptedByteArray = cipher.doFinal(Base64.getEncoder().encode(data))
            }
        }
        return Unpooled.wrappedBuffer(encryptedByteArray)
    }

    fun decrypt(encryptedBytes: ByteBuf, messageBodyBodyEncryptionType: MessageBodyEncryptionType,
                secureToken: String): ByteBuf {
        val key: Key = SecretKeySpec(this.secureTokenToBytes(secureToken), "AES")
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, key)
        val encryptedData = ByteBufUtil.getBytes(encryptedBytes)
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
        return Unpooled.wrappedBuffer(decryptedByteArray)
    }
}

/**
 * The serializer to serialize the message body
 */
private object MessageBodySerializer {
    val logger = LoggerFactory.getLogger(MessageBodySerializer::class.java);

    fun encodeProxyMessageBody(message: ProxyMessageBody?, messageBodyBodyEncryptionType: MessageBodyEncryptionType,
                               secureToken: String): ByteBuf {
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
        val encryptedResult = MessageBodyEncryptionUtil.encrypt(result, messageBodyBodyEncryptionType, secureToken)
        ReferenceCountUtil.release(result)
        return encryptedResult
    }

    fun encodeAgentMessageBody(message: AgentMessageBody?, messageBodyBodyEncryptionType: MessageBodyEncryptionType,
                               secureToken: String): ByteBuf {
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
        val encryptedResult = MessageBodyEncryptionUtil.encrypt(result, messageBodyBodyEncryptionType, secureToken)
        ReferenceCountUtil.release(result)
        return encryptedResult
    }

    fun decodeAgentMessageBody(messageBytes: ByteBuf, messageBodyBodyEncryptionType: MessageBodyEncryptionType,
                               secureToken: String): AgentMessageBody {
        val messageBodyByteBuf =
                MessageBodyEncryptionUtil.decrypt(messageBytes, messageBodyBodyEncryptionType, secureToken);
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
        return agentMessageBody(bodyType, messageId) {
            this.originalData = originalData
            this.targetAddress = targetAddress
            this.targetPort = targetPort
        }
    }

    fun decodeProxyMessageBody(messageBytes: ByteBuf, messageBodyBodyEncryptionType: MessageBodyEncryptionType,
                               secureToken: String): ProxyMessageBody {
        val messageBodyByteBuf =
                MessageBodyEncryptionUtil.decrypt(messageBytes, messageBodyBodyEncryptionType, secureToken);
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
        return proxyMessageBody(bodyType, messageId) {
            this.originalData = originalData
            this.targetAddress = targetAddress
            this.targetPort = targetPort
        }
    }
}

/**
 * The serializer to serialize the message
 */
internal object MessageSerializer {
    val logger = LoggerFactory.getLogger(MessageSerializer::class.java);

    fun encodeAgentMessage(message: AgentMessage): ByteBuf {
        val result = ByteBufAllocator.DEFAULT.buffer()
        result.writeInt(message.secureToken.length)
        result.writeBytes(message.secureToken.toByteArray(Charsets.UTF_8))
        result.writeInt(message.messageBodyEncryptionType.mask.length)
        result.writeBytes(message.messageBodyEncryptionType.mask.toByteArray(Charsets.UTF_8))
        result.writeBytes(
                MessageBodySerializer.encodeAgentMessageBody(message.body,
                        message.messageBodyEncryptionType,
                        message.secureToken));
        return result
    }

    fun encodeProxyMessage(message: ProxyMessage): ByteBuf {
        val result = ByteBufAllocator.DEFAULT.buffer()
        result.writeInt(message.secureToken.length)
        result.writeBytes(message.secureToken.toByteArray(Charsets.UTF_8))
        result.writeInt(message.messageBodyEncryptionType.mask.length)
        result.writeBytes(message.messageBodyEncryptionType.mask.toByteArray(Charsets.UTF_8))
        result.writeBytes(
                MessageBodySerializer.encodeProxyMessageBody(message.body,
                        message.messageBodyEncryptionType,
                        message.secureToken));
        return result
    }

    fun decodeAgentMessage(messageBytes: ByteBuf): AgentMessage {
        val secureTokenLength = messageBytes.readInt()
        val secureToken = messageBytes.readCharSequence(secureTokenLength, Charsets.UTF_8).toString()
        val encryptionTypeMaskLength = messageBytes.readInt()
        val encryptionTypeMask = messageBytes.readCharSequence(encryptionTypeMaskLength, Charsets.UTF_8).toString()
        val encryptionType = MessageBodyEncryptionType.fromMask(encryptionTypeMask) ?: throw PpaassException()
        val messageBodyByteBuf = messageBytes.readBytes(messageBytes.readableBytes())
        return AgentMessage(secureToken, encryptionType,
                MessageBodySerializer.decodeAgentMessageBody(messageBodyByteBuf, encryptionType, secureToken))
    }

    fun decodeProxyMessage(messageBytes: ByteBuf): ProxyMessage {
        val secureTokenLength = messageBytes.readInt()
        val secureToken = messageBytes.readCharSequence(secureTokenLength, Charsets.UTF_8).toString()
        val encryptionTypeMaskLength = messageBytes.readInt()
        val encryptionTypeMask = messageBytes.readCharSequence(encryptionTypeMaskLength, Charsets.UTF_8).toString()
        val encryptionType = MessageBodyEncryptionType.fromMask(encryptionTypeMask) ?: throw PpaassException()
        val messageBodyByteBuf = messageBytes.readBytes(messageBytes.readableBytes())
        return ProxyMessage(secureToken, encryptionType,
                MessageBodySerializer.decodeProxyMessageBody(messageBodyByteBuf, encryptionType, secureToken))
    }
}

