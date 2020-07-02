package com.ppaass.kt.common.message

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

object MessageBodySerializer {
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