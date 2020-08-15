package com.ppaass.kt.common.protocol

import mu.KotlinLogging
import org.apache.commons.codec.binary.Base64
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec


private val logger = KotlinLogging.logger {}
private const val ALGORITHM_RSA = "RSA"
private const val ALGORITHM_AES = "AES"
private const val ALGORITHM_BLOWFISH = "Blowfish"
private const val AES_CIPHER = "AES/ECB/PKCS5Padding"
private const val BLOWFISH_CIPHER = "Blowfish/ECB/PKCS5Padding"


fun aesEncrypt(messageBodyEncryptionToken: String, data: ByteArray): ByteArray {
    val key = SecretKeySpec(messageBodyEncryptionToken.toByteArray(Charsets.UTF_8), ALGORITHM_AES)
    val cipher = Cipher.getInstance(AES_CIPHER)
    cipher.init(Cipher.ENCRYPT_MODE, key)
    return cipher.doFinal(data)
}

fun aesDecrypt(messageBodyEncryptionToken: String, aesData: ByteArray): ByteArray {
    val key = SecretKeySpec(messageBodyEncryptionToken.toByteArray(Charsets.UTF_8), ALGORITHM_AES)
    val cipher = Cipher.getInstance(AES_CIPHER)
    cipher.init(Cipher.DECRYPT_MODE, key)
    return cipher.doFinal(aesData)
}

fun blowfishEncrypt(messageBodyEncryptionToken: String, data: ByteArray): ByteArray {
    val key = SecretKeySpec(messageBodyEncryptionToken.toByteArray(Charsets.UTF_8), ALGORITHM_BLOWFISH)
    val cipher = Cipher.getInstance(BLOWFISH_CIPHER)
    cipher.init(Cipher.ENCRYPT_MODE, key)
    return cipher.doFinal(data)
}

fun blowfishDecrypt(messageBodyEncryptionToken: String, aesData: ByteArray): ByteArray {
    val key = SecretKeySpec(messageBodyEncryptionToken.toByteArray(Charsets.UTF_8), ALGORITHM_BLOWFISH)
    val cipher = Cipher.getInstance(BLOWFISH_CIPHER)
    cipher.init(Cipher.DECRYPT_MODE, key)
    return cipher.doFinal(aesData)
}

fun rsaEncrypt(target: String, publicKeyString: String): String {
    val publicKeySpec = X509EncodedKeySpec(Base64.decodeBase64(publicKeyString))
    val keyFactory = KeyFactory.getInstance(ALGORITHM_RSA)
    val publicKey = keyFactory.generatePublic(publicKeySpec)
    val cipher = Cipher.getInstance(publicKey.algorithm)
    cipher.init(Cipher.ENCRYPT_MODE, publicKey)
    cipher.update(target.toByteArray(Charsets.UTF_8))
    return Base64.encodeBase64String(cipher.doFinal())
}

fun rsaDecrypt(target: String,
               privateKeyString: String): String {
    val privateKeySpec = PKCS8EncodedKeySpec(Base64.decodeBase64(privateKeyString))
    val keyFactory = KeyFactory.getInstance(ALGORITHM_RSA)
    val privateKey = keyFactory.generatePrivate(privateKeySpec)
    val cipher = Cipher.getInstance(privateKey.algorithm)
    cipher.init(Cipher.DECRYPT_MODE, privateKey)
    cipher.update(Base64.decodeBase64(target))
    return String(cipher.doFinal(), Charsets.UTF_8)
}

fun generateRsaKeyPair() {
    val keyPairGen = KeyPairGenerator.getInstance(ALGORITHM_RSA)
    keyPairGen.initialize(1024)
    val keyPair = keyPairGen.generateKeyPair()
    val publicKey = keyPair.public.encoded
    logger.info { "RSA key pair public key:\n${Base64.encodeBase64String(publicKey)}" }
    val privateKey = keyPair.private.encoded
    logger.info { "RSA key pair private key:\n${Base64.encodeBase64String(privateKey)}" }
}


