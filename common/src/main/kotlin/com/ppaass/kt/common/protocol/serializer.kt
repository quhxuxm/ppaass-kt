package com.ppaass.kt.common.protocol

import com.ppaass.kt.common.exception.PpaassException
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.Unpooled
import io.netty.util.ReferenceCountUtil
import mu.KotlinLogging
import org.apache.commons.codec.binary.Base64
import org.apache.commons.codec.digest.DigestUtils
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

private val logger = KotlinLogging.logger {}
private const val ALGORITHM_RSA = "RSA"
private const val ALGORITHM_AES = "AES"
private const val ALGORITHM_BLOWFISH = "Blowfish"
private const val AES_CIPHER = "AES/ECB/PKCS5Padding"
private const val BLOWFISH_CIPHER = "Blowfish/ECB/PKCS5Padding"

private fun encryptMessageBodyEncryptionToken(messageBodyEncryptionToken: String, publicKeyString: String): String {
    val publicKeySpec = X509EncodedKeySpec(Base64.decodeBase64(publicKeyString))
    val keyFactory = KeyFactory.getInstance(ALGORITHM_RSA)
    val publicKey = keyFactory.generatePublic(publicKeySpec)
    val cipher = Cipher.getInstance(publicKey.algorithm)
    cipher.init(Cipher.ENCRYPT_MODE, publicKey)
    cipher.update(messageBodyEncryptionToken.toByteArray(Charsets.UTF_8))
    return Base64.encodeBase64String(cipher.doFinal())
}

private fun decryptMessageBodyEncryptionToken(encryptedMessageBodyEncryptionToken: String,
                                              privateKeyString: String): String {
    val privateKeySpec = PKCS8EncodedKeySpec(Base64.decodeBase64(privateKeyString))
    val keyFactory = KeyFactory.getInstance(ALGORITHM_RSA)
    val privateKey = keyFactory.generatePrivate(privateKeySpec)
    val cipher = Cipher.getInstance(privateKey.algorithm)
    cipher.init(Cipher.DECRYPT_MODE, privateKey)
    cipher.update(Base64.decodeBase64(encryptedMessageBodyEncryptionToken))
    return String(cipher.doFinal(), Charsets.UTF_8)
}

private fun aesEncrypt(messageBodyEncryptionToken: String, data: ByteArray): ByteArray {
    val key = SecretKeySpec(messageBodyEncryptionToken.toByteArray(Charsets.UTF_8), ALGORITHM_AES)
    val cipher = Cipher.getInstance(AES_CIPHER)
    cipher.init(Cipher.ENCRYPT_MODE, key)
    return cipher.doFinal(data)
}

private fun aesDecrypt(messageBodyEncryptionToken: String, aesData: ByteArray): ByteArray {
    val key = SecretKeySpec(messageBodyEncryptionToken.toByteArray(Charsets.UTF_8), ALGORITHM_AES)
    val cipher = Cipher.getInstance(AES_CIPHER)
    cipher.init(Cipher.DECRYPT_MODE, key)
    return cipher.doFinal(aesData)
}

private fun blowfishEncrypt(messageBodyEncryptionToken: String, data: ByteArray): ByteArray {
    val key = SecretKeySpec(messageBodyEncryptionToken.toByteArray(Charsets.UTF_8), ALGORITHM_BLOWFISH)
    val cipher = Cipher.getInstance(BLOWFISH_CIPHER)
    cipher.init(Cipher.ENCRYPT_MODE, key)
    return cipher.doFinal(data)
}

private fun blowfishDecrypt(messageBodyEncryptionToken: String, aesData: ByteArray): ByteArray {
    val key = SecretKeySpec(messageBodyEncryptionToken.toByteArray(Charsets.UTF_8), ALGORITHM_BLOWFISH)
    val cipher = Cipher.getInstance(BLOWFISH_CIPHER)
    cipher.init(Cipher.DECRYPT_MODE, key)
    return cipher.doFinal(aesData)
}

private fun encryptMessageBody(data: ByteArray, messageBodyBodyEncryptionType: MessageBodyEncryptionType,
                               messageBodyEncryptionToken: String): ByteArray {
    val encryptedByteArray: ByteArray
    when (messageBodyBodyEncryptionType) {
        MessageBodyEncryptionType.AES -> {
            encryptedByteArray = aesEncrypt(messageBodyEncryptionToken, data)
        }
        MessageBodyEncryptionType.BLOWFISH -> {
            encryptedByteArray = blowfishEncrypt(messageBodyEncryptionToken, data)
        }
    }
    return encryptedByteArray
}

private fun decryptMessageBody(encryptedData: ByteArray,
                               messageBodyBodyEncryptionType: MessageBodyEncryptionType,
                               messageBodyEncryptionToken: String): ByteArray {
    val decryptedByteArray: ByteArray
    when (messageBodyBodyEncryptionType) {
        MessageBodyEncryptionType.AES -> {
            decryptedByteArray =
                    aesDecrypt(messageBodyEncryptionToken, encryptedData)
        }
        MessageBodyEncryptionType.BLOWFISH -> {
            decryptedByteArray =
                    blowfishDecrypt(messageBodyEncryptionToken, encryptedData)
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
            encryptMessageBody(resultByteArray, messageBodyBodyEncryptionType, messageBodyEncryptionToken)
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
            encryptMessageBody(resultByteArray, messageBodyBodyEncryptionType, messageBodyEncryptionToken)
    return encryptedResult
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

internal fun encodeAgentMessage(message: AgentMessage, proxyPublicKeyString: String, output: ByteBuf) {
    val originalMessageBodyEncryptionToken = DigestUtils.md5Hex(UUID.randomUUID().toString())
    val encryptedMessageBodyEncryptionToken =
            encryptMessageBodyEncryptionToken(originalMessageBodyEncryptionToken, proxyPublicKeyString)
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
            encryptMessageBodyEncryptionToken(originalMessageBodyEncryptionToken, agentPublicKeyString)
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
    val encryptedMessageBodyEncryptionTokenLength = input.readInt()
    val encryptedMessageBodyEncryptionToken =
            input.readCharSequence(encryptedMessageBodyEncryptionTokenLength, Charsets.UTF_8).toString()
    val messageBodyEncryptionToken =
            decryptMessageBodyEncryptionToken(encryptedMessageBodyEncryptionToken, proxyPrivateKeyString)
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
    val encryptedMessageBodyEncryptionTokenLength = input.readInt()
    val encryptedMessageBodyEncryptionToken =
            input.readCharSequence(encryptedMessageBodyEncryptionTokenLength, Charsets.UTF_8).toString()
    val messageBodyEncryptionToken =
            decryptMessageBodyEncryptionToken(encryptedMessageBodyEncryptionToken, agentPrivateKeyString)
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

private fun generateAgentKeyPair() {
    val keyPairGen = KeyPairGenerator.getInstance(ALGORITHM_RSA)
    keyPairGen.initialize(1024)
    val keyPair = keyPairGen.generateKeyPair()
    val publicKey = keyPair.public.encoded
    println("Generate agent public key:\n${Base64.encodeBase64String(publicKey)}")
    val privateKey = keyPair.private.encoded
    println("Generate agent private key:\n${Base64.encodeBase64String(privateKey)}")
}

private fun generateProxyKeyPair() {
    val keyPairGen = KeyPairGenerator.getInstance(ALGORITHM_RSA)
    keyPairGen.initialize(1024)
    val keyPair = keyPairGen.generateKeyPair()
    val publicKey = keyPair.public.encoded
    println("Generate proxy public key:\n${Base64.encodeBase64String(publicKey)}")
    val privateKey = keyPair.private.encoded
    println("Generate proxy private key:\n${Base64.encodeBase64String(privateKey)}")
}

fun main() {
    generateAgentKeyPair()
    generateProxyKeyPair()
}


