package com.ppaass.kt.common

import mu.KotlinLogging
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

private const val ALGORITHM_RSA = "RSA"
private const val ALGORITHM_AES = "AES"
private const val ALGORITHM_BLOWFISH = "Blowfish"
private const val AES_CIPHER = "AES/ECB/PKCS5Padding"
private const val BLOWFISH_CIPHER = "Blowfish/ECB/PKCS5Padding"
private val logger = KotlinLogging.logger { }

/**
 * The encryption type.
 *
 * @param value A byte to identify the encryption type in the message.
 */
enum class EncryptionType(private val value: Byte) {
    /**
     * The aes encryption
     */
    AES(0),

    /**
     * The blowfish encryption
     */
    BLOWFISH(1);

    companion object {
        /**
         * Choose one of the encryption type randomly
         *
         * @return One of the encryption type
         */
        fun choose(): EncryptionType {
            val index = Random.nextInt(values().size)
            return values()[index]
        }
    }

    /**
     * Get the value of the encryption type.
     *
     * @return The byte value in the message to identify the encryption type.
     */
    fun value(): Byte {
        return this.value
    }
}

/**
 * Do AES encryption with encryption token.
 *
 * @param encryptionToken Encryption token.
 * @param data The data to do encryption.
 * @return The encrypt result
 */
fun aesEncrypt(encryptionToken: String, data: ByteArray): ByteArray {
    return try {
        val key = SecretKeySpec(encryptionToken.toByteArray(Charsets.UTF_8), ALGORITHM_AES)
        val cipher = Cipher.getInstance(AES_CIPHER)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.doFinal(data)
    } catch (e: Exception) {
        logger.error(e) {
            "Fail to encrypt data with encryption token in AES because of exception. Encryption token: \n$encryptionToken\n"
        }
        throw PpaassException(
            "Fail to encrypt data with encryption token in AES because of exception. Encryption token: $encryptionToken",
            e)
    }
}

/**
 * Decrypt AES data with encryption token.
 *
 * @param encryptionToken Encryption token.
 * @param data The data encrypted.
 * @return The original data
 */
fun aesDecrypt(encryptionToken: String, aesData: ByteArray): ByteArray {
    return try {
        val key = SecretKeySpec(encryptionToken.toByteArray(Charsets.UTF_8), ALGORITHM_AES)
        val cipher = Cipher.getInstance(AES_CIPHER)
        cipher.init(Cipher.DECRYPT_MODE, key)
        cipher.doFinal(aesData)
    } catch (e: Exception) {
        logger.error(e) {
            "Fail to decrypt data with encryption token in AES because of exception. Encryption token: \n$encryptionToken\n"
        }
        throw PpaassException(
            "Fail to decrypt data with encryption token in AES because of exception. Encryption token: $encryptionToken",
            e)
    }
}

/**
 * Do Blowfish encryption with encryption token.
 *
 * @param encryptionToken Encryption token.
 * @param data The data to do encryption.
 * @return The encrypt result
 */
fun blowfishEncrypt(encryptionToken: String, data: ByteArray): ByteArray {
    return try {
        val key =
            SecretKeySpec(encryptionToken.toByteArray(Charsets.UTF_8), ALGORITHM_BLOWFISH)
        val cipher = Cipher.getInstance(BLOWFISH_CIPHER)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.doFinal(data)
    } catch (e: Exception) {
        logger.error(e) {
            "Fail to encrypt data with encryption token in Blowfish because of exception. Encryption token: \n$encryptionToken\n"
        }
        throw PpaassException(
            "Fail to encrypt data with encryption token in Blowfish because of exception. Encryption token: $encryptionToken",
            e)
    }
}

/**
 * Decrypt Blowfish data with encryption token.
 *
 * @param encryptionToken Encryption token.
 * @param data The data encrypted.
 * @return The original data
 */
fun blowfishDecrypt(encryptionToken: String, aesData: ByteArray): ByteArray {
    return try {
        val key =
            SecretKeySpec(encryptionToken.toByteArray(Charsets.UTF_8), ALGORITHM_BLOWFISH)
        val cipher = Cipher.getInstance(BLOWFISH_CIPHER)
        cipher.init(Cipher.DECRYPT_MODE, key)
        cipher.doFinal(aesData)
    } catch (e: Exception) {
        logger.error(e) {
            "Fail to decrypt data with encryption token in Blowfish because of exception. Encryption token: \n$encryptionToken\n"
        }
        throw PpaassException(
            "Fail to decrypt data with encryption token in Blowfish because of exception. Encryption token: $encryptionToken",
            e)
    }
}

/**
 * Do RSA encryption with public key.
 *
 * @param target Target data to do encrypt.
 * @param publicKeyString The public key.
 * @return The encrypt result
 */
fun rsaEncrypt(target: String, publicKeyString: String): String {
    return try {
        val publicKeySpec = X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyString))
        val keyFactory = KeyFactory.getInstance(ALGORITHM_RSA)
        val publicKey = keyFactory.generatePublic(publicKeySpec)
        val cipher = Cipher.getInstance(publicKey.algorithm)
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        cipher.update(target.toByteArray(Charsets.UTF_8))
        Base64.getEncoder().encodeToString(cipher.doFinal())
    } catch (e: Exception) {
        logger.error(e) {
            "Fail to encrypt data with rsa public key because of exception. Target data: \n${
                target
            }\nRSA public key: \n${
                publicKeyString
            }\n"
        }
        throw PpaassException("Fail to encrypt data with rsa public key because of exception.", e)
    }
}

/**
 * Do RSA decryption with private key.
 *
 * @param target Target data to do decrypt.
 * @param publicKeyString The private key.
 * @return The decrypt result
 */
fun rsaDecrypt(target: String, privateKeyString: String): String {
    return try {
        val privateKeySpec = PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKeyString))
        val keyFactory = KeyFactory.getInstance(ALGORITHM_RSA)
        val privateKey = keyFactory.generatePrivate(privateKeySpec)
        val cipher = Cipher.getInstance(privateKey.algorithm)
        cipher.init(Cipher.DECRYPT_MODE, privateKey)
        cipher.update(Base64.getDecoder().decode(target))
        String(cipher.doFinal(), Charsets.UTF_8)
    } catch (e: java.lang.Exception) {
        logger.error(e) {
            "Fail to decrypt data with rsa private key because of exception. Target data:\n${
                target
            }\nRSA private key: \n${
                privateKeyString
            }\n"
        }
        throw PpaassException("Fail to decrypt data with rsa private key because of exception.", e)
    }
}

private class RsaKeyPair {
    var publicKey: String? = null
    var privateKey: String? = null
    override fun toString(): String {
        return "PUBLIC KEY:\n[$publicKey]\nPRIVATE KEY:\n[$privateKey]"
    }
}

private fun generateRsaKeyPair(): RsaKeyPair {
    val result = RsaKeyPair()
    try {
        val keyPairGen = KeyPairGenerator.getInstance(ALGORITHM_RSA)
        keyPairGen.initialize(1024)
        val keyPair = keyPairGen.generateKeyPair()
        val publicKey = keyPair.public.encoded
        result.publicKey = Base64.getEncoder().encodeToString(publicKey)
        logger.info("RSA key pair public key:\n\${}", result.publicKey)
        val privateKey = keyPair.private.encoded
        result.privateKey = Base64.getEncoder().encodeToString(privateKey)
        logger.info("RSA key pair private key:\n\${}", result.privateKey)
    } catch (e: java.lang.Exception) {
        throw PpaassException()
    }
    return result
}

fun main(args: Array<String>) {
    println("\nGenerate agent RSA key pair:\n")
    val agentKeyPair: RsaKeyPair = generateRsaKeyPair()
    System.out.println(agentKeyPair)
    println("\nGenerate proxy RSA key pair:\n")
    val proxyKeyPair: RsaKeyPair = generateRsaKeyPair()
    System.out.println(proxyKeyPair)
}
