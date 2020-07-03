package com.ppaass.kt.common.exception

class PpaassException(message: String?, t: Throwable?) : RuntimeException(message, t) {
    constructor() : this(null, null)
    constructor(t: Throwable) : this(null, t)
    constructor(message: String) : this(message, null)
}