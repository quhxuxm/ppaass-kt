package com.ppaass.kt.common.protocol

import java.util.*

fun generateUid(): String {
    return UUID.randomUUID().toString().replace("-", "")
}
