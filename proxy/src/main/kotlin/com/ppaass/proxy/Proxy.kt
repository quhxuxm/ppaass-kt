package com.ppaass.proxy

import mu.KotlinLogging
import org.springframework.stereotype.Service

@Service
class Proxy {
    private companion object {
        private val logger = KotlinLogging.logger { }
    }

    fun start() {}
    fun stop() {}
}
