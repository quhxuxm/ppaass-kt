package com.ppaass.kt.common

/**
 * Parse a encryption type form a given value.
 *
 * @param value A byte which can be parsed to EncryptionType
 * @return A encryption type or null if can not parse
 */
private fun Byte?.parseEncryptionType(value: Byte): EncryptionType? {
    if (this == null) {
        return null
    }
    for (e in EncryptionType.values()) {
        if (e.value() == value) {
            return e
        }
    }
    return null
}

