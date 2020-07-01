package com.ppaass.kt.common.message

enum class MessageEncryptionType(val mask: String) {
    BASE64_AES("T1"), AES_BASE64("T2");

    companion object {
        fun fromMask(mask: String): MessageEncryptionType? {
            values().forEach {
                if (it.mask == mask) {
                    return it;
                }
            }
            return null;
        }
    }
}

interface IMessageBody<T : IMessageBody<T>> {
    val originalData: ByteArray?;

    fun decode(bytes: ByteArray): T;

    fun encode(messageBody: T): ByteArray;
}

data class Message<T : IMessageBody<T>>(val secureToken: String, val encryptionType: MessageEncryptionType, val body: T) {
}