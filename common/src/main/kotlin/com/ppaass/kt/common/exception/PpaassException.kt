package com.ppaass.kt.common.exception

class PpaassException(message: String?, cause: Throwable?,
                      enableSuppression: Boolean,
                      writableStackTrace: Boolean) :
    Exception(message, cause, enableSuppression, writableStackTrace) {
    constructor() : this(null, null, false, false)
    constructor(message: String) : this(message, null, false, false)
    constructor(message: String, e: Exception) : this(message, e, false, false)
}
