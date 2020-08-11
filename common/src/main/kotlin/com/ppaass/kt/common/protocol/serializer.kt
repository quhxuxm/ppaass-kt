package com.ppaass.kt.common.protocol

import com.ppaass.kt.common.exception.PpaassException
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.Unpooled
import io.netty.util.ReferenceCountUtil
import mu.KotlinLogging
import org.apache.commons.codec.digest.DigestUtils
import java.security.Key
import java.util.*
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.PBEParameterSpec
import javax.crypto.spec.SecretKeySpec

private val logger = KotlinLogging.logger {}

private typealias ShaMethod = (String) -> ByteArray

private fun generateMessageBodyEncryptionToken(messageBodyEncryptionType: MessageBodyEncryptionType): String {
    val shaMethod: ShaMethod = when (messageBodyEncryptionType) {
        MessageBodyEncryptionType.BASE64_AES_SHA1, MessageBodyEncryptionType.AES_BASE64_SHA1,
        MessageBodyEncryptionType.BASE64_PBE_SHA1, MessageBodyEncryptionType.PBE_BASE64_SHA1 -> {
            DigestUtils::sha1
        }
        MessageBodyEncryptionType.BASE64_AES_SHA224, MessageBodyEncryptionType.AES_BASE64_SHA224,
        MessageBodyEncryptionType.BASE64_PBE_SHA224, MessageBodyEncryptionType.PBE_BASE64_SHA224 -> {
            DigestUtils::sha3_224
        }
        MessageBodyEncryptionType.BASE64_AES_SHA256, MessageBodyEncryptionType.AES_BASE64_SHA256,
        MessageBodyEncryptionType.BASE64_PBE_SHA256, MessageBodyEncryptionType.PBE_BASE64_SHA256 -> {
            DigestUtils::sha256
        }
        MessageBodyEncryptionType.BASE64_AES_SHA384, MessageBodyEncryptionType.AES_BASE64_SHA384,
        MessageBodyEncryptionType.BASE64_PBE_SHA384, MessageBodyEncryptionType.PBE_BASE64_SHA384 -> {
            DigestUtils::sha384
        }
        MessageBodyEncryptionType.BASE64_AES_SHA512, MessageBodyEncryptionType.AES_BASE64_SHA512,
        MessageBodyEncryptionType.BASE64_PBE_SHA512, MessageBodyEncryptionType.PBE_BASE64_SHA512 -> {
            DigestUtils::sha512
        }
    }
    val messageBodyEncryptionToken = UUID.randomUUID().toString()
    return DigestUtils.md5Hex(shaMethod(messageBodyEncryptionToken))
}

private fun convertEncryptionTokenToBytes(secureToken: String, shaMethod: ShaMethod): ByteArray {
    return DigestUtils.md5(shaMethod(secureToken))
}

private fun base64Encode(data: ByteArray): ByteArray {
    return Base64.getEncoder().encode(data)
}

private fun base64Decode(data: ByteArray): ByteArray {
    return Base64.getDecoder().decode(data)
}

private fun aesEncrypt(messageBodyEncryptionToken: String, data: ByteArray, shaMethod: ShaMethod): ByteArray {
    val key = SecretKeySpec(convertEncryptionTokenToBytes(messageBodyEncryptionToken, shaMethod), "AES")
    val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
    cipher.init(Cipher.ENCRYPT_MODE, key)
    return cipher.doFinal(data)
}

private fun aesDecrypt(messageBodyEncryptionToken: String, aesData: ByteArray, shaMethod: ShaMethod): ByteArray {
    val key = SecretKeySpec(convertEncryptionTokenToBytes(messageBodyEncryptionToken, shaMethod), "AES")
    val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
    cipher.init(Cipher.DECRYPT_MODE, key)
    return cipher.doFinal(aesData)
}

private fun pbeEncrypt(messageBodyEncryptionToken: String, data: ByteArray, shaMethod: ShaMethod): ByteArray {
    val salt: ByteArray = convertEncryptionTokenToBytes(messageBodyEncryptionToken, shaMethod).copyOfRange(0, 8)
    val pbeKeySpec = PBEKeySpec(messageBodyEncryptionToken.toCharArray())
    val factory = SecretKeyFactory.getInstance("PBEWITHMD5andDES")
    val key: Key = factory.generateSecret(pbeKeySpec)
    val pbeParameterSpac = PBEParameterSpec(salt, 100)
    val cipher = Cipher.getInstance("PBEWITHMD5andDES")
    cipher.init(Cipher.ENCRYPT_MODE, key, pbeParameterSpac)
    return cipher.doFinal(data)
}

private fun pbeDecrypt(messageBodyEncryptionToken: String, aesData: ByteArray, shaMethod: ShaMethod): ByteArray {
    val salt: ByteArray = convertEncryptionTokenToBytes(messageBodyEncryptionToken, shaMethod).copyOfRange(0, 8)
    val pbeKeySpec = PBEKeySpec(messageBodyEncryptionToken.toCharArray())
    val factory = SecretKeyFactory.getInstance("PBEWITHMD5andDES")
    val key: Key = factory.generateSecret(pbeKeySpec)
    val pbeParameterSpac = PBEParameterSpec(salt, 100)
    val cipher = Cipher.getInstance("PBEWITHMD5andDES")
    cipher.init(Cipher.DECRYPT_MODE, key, pbeParameterSpac)
    return cipher.doFinal(aesData)
}

private fun encrypt(data: ByteArray, messageBodyBodyEncryptionType: MessageBodyEncryptionType,
                    messageBodyEncryptionToken: String): ByteArray {
    val encryptedByteArray: ByteArray
    when (messageBodyBodyEncryptionType) {
        MessageBodyEncryptionType.AES_BASE64_SHA1 -> {
            encryptedByteArray = base64Encode(aesEncrypt(messageBodyEncryptionToken, data, DigestUtils::sha1))
        }
        MessageBodyEncryptionType.BASE64_AES_SHA1 -> {
            encryptedByteArray = aesEncrypt(messageBodyEncryptionToken, base64Encode(data), DigestUtils::sha1)
        }
        MessageBodyEncryptionType.AES_BASE64_SHA224 -> {
            encryptedByteArray = base64Encode(aesEncrypt(messageBodyEncryptionToken, data, DigestUtils::sha3_224))
        }
        MessageBodyEncryptionType.BASE64_AES_SHA224 -> {
            encryptedByteArray = aesEncrypt(messageBodyEncryptionToken, base64Encode(data), DigestUtils::sha3_224)
        }
        MessageBodyEncryptionType.AES_BASE64_SHA256 -> {
            encryptedByteArray = base64Encode(aesEncrypt(messageBodyEncryptionToken, data, DigestUtils::sha256))
        }
        MessageBodyEncryptionType.BASE64_AES_SHA256 -> {
            encryptedByteArray = aesEncrypt(messageBodyEncryptionToken, base64Encode(data), DigestUtils::sha256)
        }
        MessageBodyEncryptionType.AES_BASE64_SHA384 -> {
            encryptedByteArray = base64Encode(aesEncrypt(messageBodyEncryptionToken, data, DigestUtils::sha384))
        }
        MessageBodyEncryptionType.BASE64_AES_SHA384 -> {
            encryptedByteArray = aesEncrypt(messageBodyEncryptionToken, base64Encode(data), DigestUtils::sha384)
        }
        MessageBodyEncryptionType.AES_BASE64_SHA512 -> {
            encryptedByteArray = base64Encode(aesEncrypt(messageBodyEncryptionToken, data, DigestUtils::sha512))
        }
        MessageBodyEncryptionType.BASE64_AES_SHA512 -> {
            encryptedByteArray = aesEncrypt(messageBodyEncryptionToken, base64Encode(data), DigestUtils::sha512)
        }
        MessageBodyEncryptionType.PBE_BASE64_SHA1 -> {
            encryptedByteArray = base64Encode(pbeEncrypt(messageBodyEncryptionToken, data, DigestUtils::sha1))
        }
        MessageBodyEncryptionType.BASE64_PBE_SHA1 -> {
            encryptedByteArray = pbeEncrypt(messageBodyEncryptionToken, base64Encode(data), DigestUtils::sha1)
        }
        MessageBodyEncryptionType.PBE_BASE64_SHA224 -> {
            encryptedByteArray = base64Encode(pbeEncrypt(messageBodyEncryptionToken, data, DigestUtils::sha3_224))
        }
        MessageBodyEncryptionType.BASE64_PBE_SHA224 -> {
            encryptedByteArray = pbeEncrypt(messageBodyEncryptionToken, base64Encode(data), DigestUtils::sha3_224)
        }
        MessageBodyEncryptionType.PBE_BASE64_SHA256 -> {
            encryptedByteArray = base64Encode(pbeEncrypt(messageBodyEncryptionToken, data, DigestUtils::sha256))
        }
        MessageBodyEncryptionType.BASE64_PBE_SHA256 -> {
            encryptedByteArray = pbeEncrypt(messageBodyEncryptionToken, base64Encode(data), DigestUtils::sha256)
        }
        MessageBodyEncryptionType.PBE_BASE64_SHA384 -> {
            encryptedByteArray = base64Encode(pbeEncrypt(messageBodyEncryptionToken, data, DigestUtils::sha384))
        }
        MessageBodyEncryptionType.BASE64_PBE_SHA384 -> {
            encryptedByteArray = pbeEncrypt(messageBodyEncryptionToken, base64Encode(data), DigestUtils::sha384)
        }
        MessageBodyEncryptionType.PBE_BASE64_SHA512 -> {
            encryptedByteArray = base64Encode(pbeEncrypt(messageBodyEncryptionToken, data, DigestUtils::sha512))
        }
        MessageBodyEncryptionType.BASE64_PBE_SHA512 -> {
            encryptedByteArray = pbeEncrypt(messageBodyEncryptionToken, base64Encode(data), DigestUtils::sha512)
        }
    }
    return encryptedByteArray
}

private fun decrypt(encryptedData: ByteArray, messageBodyBodyEncryptionType: MessageBodyEncryptionType,
                    messageBodyEncryptionToken: String): ByteArray {
    val decryptedByteArray: ByteArray
    when (messageBodyBodyEncryptionType) {
        MessageBodyEncryptionType.AES_BASE64_SHA1 -> {
            decryptedByteArray = aesDecrypt(messageBodyEncryptionToken, base64Decode(encryptedData), DigestUtils::sha1)
        }
        MessageBodyEncryptionType.BASE64_AES_SHA1 -> {
            decryptedByteArray = base64Decode(aesDecrypt(messageBodyEncryptionToken, encryptedData, DigestUtils::sha1))
        }
        MessageBodyEncryptionType.AES_BASE64_SHA224 -> {
            decryptedByteArray =
                    aesDecrypt(messageBodyEncryptionToken, base64Decode(encryptedData), DigestUtils::sha3_224)
        }
        MessageBodyEncryptionType.BASE64_AES_SHA224 -> {
            decryptedByteArray =
                    base64Decode(aesDecrypt(messageBodyEncryptionToken, encryptedData, DigestUtils::sha3_224))
        }
        MessageBodyEncryptionType.AES_BASE64_SHA256 -> {
            decryptedByteArray =
                    aesDecrypt(messageBodyEncryptionToken, base64Decode(encryptedData), DigestUtils::sha256)
        }
        MessageBodyEncryptionType.BASE64_AES_SHA256 -> {
            decryptedByteArray =
                    base64Decode(aesDecrypt(messageBodyEncryptionToken, encryptedData, DigestUtils::sha256))
        }
        MessageBodyEncryptionType.AES_BASE64_SHA384 -> {
            decryptedByteArray =
                    aesDecrypt(messageBodyEncryptionToken, base64Decode(encryptedData), DigestUtils::sha384)
        }
        MessageBodyEncryptionType.BASE64_AES_SHA384 -> {
            decryptedByteArray =
                    base64Decode(aesDecrypt(messageBodyEncryptionToken, encryptedData, DigestUtils::sha384))
        }
        MessageBodyEncryptionType.AES_BASE64_SHA512 -> {
            decryptedByteArray =
                    aesDecrypt(messageBodyEncryptionToken, base64Decode(encryptedData), DigestUtils::sha512)
        }
        MessageBodyEncryptionType.BASE64_AES_SHA512 -> {
            decryptedByteArray =
                    base64Decode(aesDecrypt(messageBodyEncryptionToken, encryptedData, DigestUtils::sha512))
        }
        MessageBodyEncryptionType.PBE_BASE64_SHA1 -> {
            decryptedByteArray = pbeDecrypt(messageBodyEncryptionToken, base64Decode(encryptedData), DigestUtils::sha1)
        }
        MessageBodyEncryptionType.BASE64_PBE_SHA1 -> {
            decryptedByteArray = base64Decode(pbeDecrypt(messageBodyEncryptionToken, encryptedData, DigestUtils::sha1))
        }
        MessageBodyEncryptionType.PBE_BASE64_SHA224 -> {
            decryptedByteArray =
                    pbeDecrypt(messageBodyEncryptionToken, base64Decode(encryptedData), DigestUtils::sha3_224)
        }
        MessageBodyEncryptionType.BASE64_PBE_SHA224 -> {
            decryptedByteArray =
                    base64Decode(pbeDecrypt(messageBodyEncryptionToken, encryptedData, DigestUtils::sha3_224))
        }
        MessageBodyEncryptionType.PBE_BASE64_SHA256 -> {
            decryptedByteArray =
                    pbeDecrypt(messageBodyEncryptionToken, base64Decode(encryptedData), DigestUtils::sha256)
        }
        MessageBodyEncryptionType.BASE64_PBE_SHA256 -> {
            decryptedByteArray =
                    base64Decode(pbeDecrypt(messageBodyEncryptionToken, encryptedData, DigestUtils::sha256))
        }
        MessageBodyEncryptionType.PBE_BASE64_SHA384 -> {
            decryptedByteArray =
                    pbeDecrypt(messageBodyEncryptionToken, base64Decode(encryptedData), DigestUtils::sha384)
        }
        MessageBodyEncryptionType.BASE64_PBE_SHA384 -> {
            decryptedByteArray =
                    base64Decode(pbeDecrypt(messageBodyEncryptionToken, encryptedData, DigestUtils::sha384))
        }
        MessageBodyEncryptionType.PBE_BASE64_SHA512 -> {
            decryptedByteArray =
                    pbeDecrypt(messageBodyEncryptionToken, base64Decode(encryptedData), DigestUtils::sha512)
        }
        MessageBodyEncryptionType.BASE64_PBE_SHA512 -> {
            decryptedByteArray =
                    base64Decode(pbeDecrypt(messageBodyEncryptionToken, encryptedData, DigestUtils::sha512))
        }
    }
    return decryptedByteArray
}

private fun encodeProxyMessageBody(message: ProxyMessageBody?, messageBodyBodyEncryptionType: MessageBodyEncryptionType,
                                   messageBodyEncryptionToken: String): ByteArray {
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
            encrypt(resultByteArray, messageBodyBodyEncryptionType, messageBodyEncryptionToken)
    return encryptedResult
}

private fun encodeAgentMessageBody(message: AgentMessageBody?, messageBodyBodyEncryptionType: MessageBodyEncryptionType,
                                   messageBodyEncryptionToken: String): ByteArray {
    logger.debug("Encode agent message body.")
    val result = ByteBufAllocator.DEFAULT.buffer()
    val bodyTypeByteArray = message?.bodyType?.name?.toByteArray(Charsets.UTF_8) ?: byteArrayOf()
    result.writeInt(bodyTypeByteArray.size)
    result.writeBytes(bodyTypeByteArray)
    val messageIdByteArray = message?.id?.toByteArray(Charsets.UTF_8) ?: byteArrayOf();
    result.writeInt(messageIdByteArray.size)
    result.writeBytes(messageIdByteArray)
    val securityTokenArray = message?.securityToken?.toByteArray(Charsets.UTF_8) ?: byteArrayOf();
    result.writeInt(securityTokenArray.size)
    result.writeBytes(securityTokenArray)
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
            encrypt(resultByteArray, messageBodyBodyEncryptionType, messageBodyEncryptionToken)
    return encryptedResult
}

private fun decodeAgentMessageBody(messageBytes: ByteArray, messageBodyBodyEncryptionType: MessageBodyEncryptionType,
                                   messageBodyEncryptionToken: String): AgentMessageBody {
    val messageBodyBytes =
            decrypt(messageBytes, messageBodyBodyEncryptionType, messageBodyEncryptionToken);
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
    val agentMessageBody = AgentMessageBody(bodyType, messageId, securityToken)
    agentMessageBody.originalData = originalData
    agentMessageBody.targetPort = targetPort
    agentMessageBody.targetAddress = targetAddress
    return agentMessageBody
}

private fun decodeProxyMessageBody(messageBytes: ByteArray, messageBodyBodyEncryptionType: MessageBodyEncryptionType,
                                   messageBodyEncryptionToken: String): ProxyMessageBody {
    val messageBodyBytes =
            decrypt(messageBytes, messageBodyBodyEncryptionType, messageBodyEncryptionToken);
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

internal fun encodeAgentMessage(message: AgentMessage, output: ByteBuf) {
    val messageBodyEncryptionToken =
            generateMessageBodyEncryptionToken(
                    message.messageBodyEncryptionType);
    output.writeInt(messageBodyEncryptionToken.length)
    output.writeBytes(messageBodyEncryptionToken.toByteArray(Charsets.UTF_8))
    output.writeInt(message.messageBodyEncryptionType.mask.length)
    output.writeBytes(message.messageBodyEncryptionType.mask.toByteArray(Charsets.UTF_8))
    val bodyByteArray = encodeAgentMessageBody(message.body,
            message.messageBodyEncryptionType,
            messageBodyEncryptionToken)
    output.writeBytes(bodyByteArray);
}

internal fun encodeProxyMessage(message: ProxyMessage, output: ByteBuf) {
    val messageBodyEncryptionToken =
            generateMessageBodyEncryptionToken(
                    message.messageBodyEncryptionType);
    output.writeInt(messageBodyEncryptionToken.length)
    output.writeBytes(messageBodyEncryptionToken.toByteArray(Charsets.UTF_8))
    output.writeInt(message.messageBodyEncryptionType.mask.length)
    output.writeBytes(message.messageBodyEncryptionType.mask.toByteArray(Charsets.UTF_8))
    val bodyByteArray = encodeProxyMessageBody(message.body,
            message.messageBodyEncryptionType,
            messageBodyEncryptionToken)
    output.writeBytes(bodyByteArray);
}

internal fun decodeAgentMessage(input: ByteBuf): AgentMessage {
    val messageBodyEncryptionTokenLength = input.readInt()
    val messageBodyEncryptionToken = input.readCharSequence(messageBodyEncryptionTokenLength, Charsets.UTF_8).toString()
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

internal fun decodeProxyMessage(input: ByteBuf): ProxyMessage {
    val messageBodyEncryptionTokenLength = input.readInt()
    val messageBodyEncryptionToken = input.readCharSequence(messageBodyEncryptionTokenLength, Charsets.UTF_8).toString()
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

