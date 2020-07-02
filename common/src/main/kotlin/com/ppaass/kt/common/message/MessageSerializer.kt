package com.ppaass.kt.common.message

import com.ppaass.kt.common.exception.PpaassException
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.ByteBufUtil
import io.netty.buffer.Unpooled
import org.apache.commons.codec.digest.DigestUtils
import org.slf4j.LoggerFactory
import java.security.Key
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

private object MessageBodyEncryptionUtil {
    private fun secureTokenToBytes(secureToken: String): ByteArray? {
        return DigestUtils.md5(secureToken)
    }

    fun encrypt(bytes: ByteBuf, messageBodyEncryptionType: MessageEncryptionType, secureToken: String): ByteBuf {
        val key: Key = SecretKeySpec(this.secureTokenToBytes(secureToken), "AES")
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val data = ByteBufUtil.getBytes(bytes)
        val encryptedByteArray: ByteArray
        when (messageBodyEncryptionType) {
            MessageEncryptionType.AES_BASE64 -> {
                encryptedByteArray = Base64.getEncoder().encode(cipher.doFinal(data))
            }
            MessageEncryptionType.BASE64_AES -> {
                encryptedByteArray = cipher.doFinal(Base64.getEncoder().encode(data))
            }
        }
        return Unpooled.wrappedBuffer(encryptedByteArray)
    }

    fun decrypt(encryptedBytes: ByteBuf, messageBodyEncryptionType: MessageEncryptionType, secureToken: String): ByteBuf {
        val key: Key = SecretKeySpec(this.secureTokenToBytes(secureToken), "AES")
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, key)
        val encryptedData = ByteBufUtil.getBytes(encryptedBytes)
        val decryptedByteArray: ByteArray
        when (messageBodyEncryptionType) {
            MessageEncryptionType.AES_BASE64 -> {
                val aesData = Base64.getDecoder().decode(encryptedData)
                decryptedByteArray = cipher.doFinal(aesData)
            }
            MessageEncryptionType.BASE64_AES -> {
                val bas64Data = cipher.doFinal(encryptedData)
                decryptedByteArray = Base64.getDecoder().decode(bas64Data)
            }
        }
        return Unpooled.wrappedBuffer(decryptedByteArray)
    }
}

private object MessageBodySerializer {
    val logger = LoggerFactory.getLogger(MessageBodySerializer::class.java);
    fun encodeProxyMessageBody(message: ProxyMessageBody, messageBodyEncryptionType: MessageEncryptionType, secureToken: String): ByteBuf {
        logger.debug("Encode proxy message body.")
        val result = ByteBufAllocator.DEFAULT.buffer()
        val bodyTypeByteArray = message.bodyType.name.toByteArray(Charsets.UTF_8)
        result.writeInt(bodyTypeByteArray.size)
        result.writeBytes(bodyTypeByteArray)
        val messageIdByteArray = message.id.toByteArray(Charsets.UTF_8);
        result.writeInt(messageIdByteArray.size)
        result.writeBytes(messageIdByteArray)
        val targetAddressByteArray = message.targetAddress?.toByteArray() ?: byteArrayOf()
        result.writeInt(targetAddressByteArray.size)
        result.writeBytes(targetAddressByteArray)
        val targetPortWriteToByteArray = message.targetPort ?: -1
        result.writeInt(targetPortWriteToByteArray)
        val targetOriginalDataByteArray = message.originalData ?: byteArrayOf()
        result.writeInt(targetOriginalDataByteArray.size)
        result.writeBytes(targetOriginalDataByteArray)
        return MessageBodyEncryptionUtil.encrypt(result, messageBodyEncryptionType, secureToken)
    }

    fun encodeAgentMessageBody(message: AgentMessageBody, messageBodyEncryptionType: MessageEncryptionType, secureToken: String): ByteBuf {
        logger.debug("Encode agent message body.")
        val result = ByteBufAllocator.DEFAULT.buffer()
        val bodyTypeByteArray = message.bodyType.name.toByteArray(Charsets.UTF_8)
        result.writeInt(bodyTypeByteArray.size)
        result.writeBytes(bodyTypeByteArray)
        val messageIdByteArray = message.id.toByteArray(Charsets.UTF_8);
        result.writeInt(messageIdByteArray.size)
        result.writeBytes(messageIdByteArray)
        val targetAddressByteArray = message.targetAddress?.toByteArray() ?: byteArrayOf()
        result.writeInt(targetAddressByteArray.size)
        result.writeBytes(targetAddressByteArray)
        val targetPortWriteToByteArray = message.targetPort ?: -1
        result.writeInt(targetPortWriteToByteArray)
        val targetOriginalDataByteArray = message.originalData ?: byteArrayOf()
        result.writeInt(targetOriginalDataByteArray.size)
        result.writeBytes(targetOriginalDataByteArray)
        return MessageBodyEncryptionUtil.encrypt(result, messageBodyEncryptionType, secureToken)
    }

    fun decodeAgentMessageBody(messageBytes: ByteBuf, messageBodyEncryptionType: MessageEncryptionType, secureToken: String): AgentMessageBody {
        val messageBodyByteBuf = MessageBodyEncryptionUtil.decrypt(messageBytes, messageBodyEncryptionType, secureToken);
        val bodyTypeNameLength = messageBodyByteBuf.readInt()
        val bodyTypeName = messageBodyByteBuf.readCharSequence(bodyTypeNameLength, Charsets.UTF_8).toString()
        val bodyType = AgentMessageBodyType.valueOf(bodyTypeName)
        val messageIdLength = messageBodyByteBuf.readInt()
        val messageId = messageBodyByteBuf.readCharSequence(messageIdLength, Charsets.UTF_8).toString()
        val targetAddressLength = messageBodyByteBuf.readInt()
        val targetAddress = messageBodyByteBuf.readCharSequence(targetAddressLength, Charsets.UTF_8).toString()
        val targetPort = messageBodyByteBuf.readInt()
        val originalDataLength = messageBodyByteBuf.readInt()
        val originalData = ByteArray(originalDataLength)
        messageBodyByteBuf.readBytes(originalData)
        return AgentMessageBody(originalData, bodyType, messageId, targetAddress, targetPort)
    }

    fun decodeProxyMessageBody(messageBytes: ByteBuf, messageBodyEncryptionType: MessageEncryptionType, secureToken: String): ProxyMessageBody {
        val messageBodyByteBuf = MessageBodyEncryptionUtil.decrypt(messageBytes, messageBodyEncryptionType, secureToken);
        val bodyTypeNameLength = messageBodyByteBuf.readInt()
        val bodyTypeName = messageBodyByteBuf.readCharSequence(bodyTypeNameLength, Charsets.UTF_8).toString()
        val bodyType = ProxyMessageBodyType.valueOf(bodyTypeName)
        val messageIdLength = messageBodyByteBuf.readInt()
        val messageId = messageBodyByteBuf.readCharSequence(messageIdLength, Charsets.UTF_8).toString()
        val targetAddressLength = messageBodyByteBuf.readInt()
        val targetAddress = messageBodyByteBuf.readCharSequence(targetAddressLength, Charsets.UTF_8).toString()
        val targetPort = messageBodyByteBuf.readInt()
        val originalDataLength = messageBodyByteBuf.readInt()
        val originalData = ByteArray(originalDataLength)
        messageBodyByteBuf.readBytes(originalData)
        return ProxyMessageBody(originalData, bodyType, messageId, targetAddress, targetPort)
    }
}

object MessageSerializer {
    val logger = LoggerFactory.getLogger(MessageSerializer::class.java);
    fun <T : MessageBody> encode(message: Message<T>): ByteBuf {
        logger.debug("Encode message.")
        val result = ByteBufAllocator.DEFAULT.buffer()
        result.writeInt(message.secureToken.length)
        result.writeBytes(message.secureToken.toByteArray(Charsets.UTF_8))
        result.writeInt(message.encryptionType.mask.length)
        result.writeBytes(message.encryptionType.mask.toByteArray(Charsets.UTF_8))
        when (val messageBody = message.body) {
            is ProxyMessageBody -> {
                result.writeBytes(MessageBodySerializer.encodeProxyMessageBody(messageBody, message.encryptionType, message.secureToken));
            }
            is AgentMessageBody -> {
                result.writeBytes(MessageBodySerializer.encodeAgentMessageBody(messageBody, message.encryptionType, message.secureToken));
            }
        }
        return result
    }

    fun <T : MessageBody> decode(messageBytes: ByteBuf, messageBodyClass: Class<T>): Message<T> {
        val secureTokenLength = messageBytes.readInt()
        val secureToken = messageBytes.readCharSequence(secureTokenLength, Charsets.UTF_8).toString()
        val encryptionTypeMaskLength = messageBytes.readInt()
        val encryptionTypeMask = messageBytes.readCharSequence(encryptionTypeMaskLength, Charsets.UTF_8).toString()
        val encryptionType = MessageEncryptionType.fromMask(encryptionTypeMask)
        val messageBodyByteBuf = messageBytes.readBytes(messageBytes.readableBytes())
        val messageBody = when (messageBodyClass) {
            AgentMessageBody::class.java -> {
                MessageBodySerializer.decodeAgentMessageBody(messageBodyByteBuf, encryptionType, secureToken)
            }
            ProxyMessageBody::class.java -> {
                MessageBodySerializer.decodeProxyMessageBody(messageBodyByteBuf, encryptionType, secureToken)
            }
            else -> {
                throw PpaassException()
            }
        }
        return Message(secureToken, encryptionType, messageBodyClass.cast(messageBody));
    }
}

