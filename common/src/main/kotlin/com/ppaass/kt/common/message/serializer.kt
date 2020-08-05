package com.ppaass.kt.common.message

import com.ppaass.kt.common.exception.PpaassException
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.Unpooled
import io.netty.util.ReferenceCountUtil
import org.apache.commons.codec.digest.DigestUtils
import org.slf4j.LoggerFactory
import java.security.Key
import java.util.*
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.PBEParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The encryption util to encrypt the message
 */
private object MessageBodyEncryptionUtil {
    private fun convertSecureTokenToBytes(secureToken: String): ByteArray {
        return DigestUtils.md5(DigestUtils.sha1(secureToken))
    }

    private fun base64Encode(data: ByteArray): ByteArray {
        return Base64.getEncoder().encode(data)
    }

    private fun base64Decode(data: ByteArray): ByteArray {
        return Base64.getDecoder().decode(data)
    }

    private fun aesEncrypt(secureToken: String, data: ByteArray): ByteArray {
        val key = SecretKeySpec(this.convertSecureTokenToBytes(secureToken), "AES")
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return cipher.doFinal(data)
    }

    private fun aesDecrypt(secureToken: String, aesData: ByteArray): ByteArray {
        val key = SecretKeySpec(this.convertSecureTokenToBytes(secureToken), "AES")
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, key)
        return cipher.doFinal(aesData)
    }

    private fun pbeEncrypt(secureToken: String, data: ByteArray): ByteArray {
        val salt: ByteArray = this.convertSecureTokenToBytes(secureToken)
        val pbeKeySpec = PBEKeySpec(secureToken.toCharArray())
        val factory = SecretKeyFactory.getInstance("PBEWITHMD5andDES")
        val key: Key = factory.generateSecret(pbeKeySpec)
        val pbeParameterSpac = PBEParameterSpec(salt, 100)
        val cipher = Cipher.getInstance("PBEWITHMD5andDES")
        cipher.init(Cipher.ENCRYPT_MODE, key, pbeParameterSpac)
        return cipher.doFinal(data)
    }

    private fun pbeDecrypt(secureToken: String, aesData: ByteArray): ByteArray {
        val salt: ByteArray = this.convertSecureTokenToBytes(secureToken)
        val pbeKeySpec = PBEKeySpec(secureToken.toCharArray())
        val factory = SecretKeyFactory.getInstance("PBEWITHMD5andDES")
        val key: Key = factory.generateSecret(pbeKeySpec)
        val pbeParameterSpac = PBEParameterSpec(salt, 100)
        val cipher = Cipher.getInstance("PBEWITHMD5andDES")
        cipher.init(Cipher.DECRYPT_MODE, key, pbeParameterSpac)
        return cipher.doFinal(aesData)
    }

    fun encrypt(data: ByteArray, messageBodyBodyEncryptionType: MessageBodyEncryptionType,
                secureToken: String): ByteArray {
        val encryptedByteArray: ByteArray
        when (messageBodyBodyEncryptionType) {
            MessageBodyEncryptionType.AES_BASE64 -> {
                encryptedByteArray = base64Encode(aesEncrypt(secureToken, data))
            }
            MessageBodyEncryptionType.BASE64_AES -> {
                encryptedByteArray = aesEncrypt(secureToken, base64Encode(data))
            }
            MessageBodyEncryptionType.PBE_BASE64 -> {
                encryptedByteArray = base64Encode(pbeEncrypt(secureToken, data))
            }
            MessageBodyEncryptionType.BASE64_PBE -> {
                encryptedByteArray = pbeEncrypt(secureToken, base64Encode(data))
            }
        }
        return encryptedByteArray
    }

    fun decrypt(encryptedData: ByteArray, messageBodyBodyEncryptionType: MessageBodyEncryptionType,
                secureToken: String): ByteArray {
        val decryptedByteArray: ByteArray
        when (messageBodyBodyEncryptionType) {
            MessageBodyEncryptionType.AES_BASE64 -> {
                decryptedByteArray = aesDecrypt(secureToken, base64Decode(encryptedData))
            }
            MessageBodyEncryptionType.BASE64_AES -> {
                decryptedByteArray = base64Decode(aesDecrypt(secureToken, encryptedData))
            }
            MessageBodyEncryptionType.PBE_BASE64 -> {
                decryptedByteArray = pbeDecrypt(secureToken, base64Decode(encryptedData))
            }
            MessageBodyEncryptionType.BASE64_PBE -> {
                decryptedByteArray = base64Decode(pbeDecrypt(secureToken, encryptedData))
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

    fun encodeAgentMessage(message: AgentMessage, output: ByteBuf) {
        output.writeInt(message.secureToken.length)
        output.writeBytes(message.secureToken.toByteArray(Charsets.UTF_8))
        output.writeInt(message.messageBodyEncryptionType.mask.length)
        output.writeBytes(message.messageBodyEncryptionType.mask.toByteArray(Charsets.UTF_8))
        val bodyByteArray = MessageBodySerializer.encodeAgentMessageBody(message.body,
                message.messageBodyEncryptionType,
                message.secureToken)
        output.writeBytes(bodyByteArray);
    }

    fun encodeProxyMessage(message: ProxyMessage, output: ByteBuf) {
        output.writeInt(message.secureToken.length)
        output.writeBytes(message.secureToken.toByteArray(Charsets.UTF_8))
        output.writeInt(message.messageBodyEncryptionType.mask.length)
        output.writeBytes(message.messageBodyEncryptionType.mask.toByteArray(Charsets.UTF_8))
        val bodyByteArray = MessageBodySerializer.encodeProxyMessageBody(message.body,
                message.messageBodyEncryptionType,
                message.secureToken)
        output.writeBytes(bodyByteArray);
    }

    fun decodeAgentMessage(input: ByteBuf): AgentMessage {
        val secureTokenLength = input.readInt()
        val secureToken = input.readCharSequence(secureTokenLength, Charsets.UTF_8).toString()
        val encryptionTypeMaskLength = input.readInt()
        val encryptionTypeMask = input.readCharSequence(encryptionTypeMaskLength, Charsets.UTF_8).toString()
        val encryptionType = MessageBodyEncryptionType.fromMask(encryptionTypeMask) ?: throw PpaassException()
        val messageBodyByteArray = ByteArray(input.readableBytes())
        input.readBytes(messageBodyByteArray)
        val agentMessage = AgentMessage(secureToken, encryptionType,
                MessageBodySerializer.decodeAgentMessageBody(messageBodyByteArray, encryptionType, secureToken))
        return agentMessage
    }

    fun decodeProxyMessage(input: ByteBuf): ProxyMessage {
        val secureTokenLength = input.readInt()
        val secureToken = input.readCharSequence(secureTokenLength, Charsets.UTF_8).toString()
        val encryptionTypeMaskLength = input.readInt()
        val encryptionTypeMask = input.readCharSequence(encryptionTypeMaskLength, Charsets.UTF_8).toString()
        val encryptionType = MessageBodyEncryptionType.fromMask(encryptionTypeMask) ?: throw PpaassException()
        val messageBodyByteArray = ByteArray(input.readableBytes())
        input.readBytes(messageBodyByteArray)
        val proxyMessage = ProxyMessage(secureToken, encryptionType,
                MessageBodySerializer.decodeProxyMessageBody(messageBodyByteArray, encryptionType, secureToken))
        return proxyMessage
    }
}

