package com.ppaass.kt.common

/**
 * The encryption type.
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

    /**
     * Get the value of the encryption type.
     */
    fun value(): Byte {
        return this.value
    }
}

/**
 * Parse a encryption type form a given value.
 */
fun EncryptionType?.fromValue(value: Byte): EncryptionType? {
    for (e in EncryptionType.values()) {
        if (e.value() == value) {
            return e
        }
    }
    return null
}
