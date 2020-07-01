package com.ppaass.kt.proxy.impl

import com.ppaass.kt.proxy.api.IProxy
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class Proxy : IProxy {

    companion object {
        val logger: Logger = LoggerFactory.getLogger(Proxy::class.java);
    }

    override fun init() {
        logger.debug("Begin to start proxy.")
        TODO("Not yet implemented")
    }

    override fun start() {
        TODO("Not yet implemented")
    }

    override fun stop() {
        TODO("Not yet implemented")
    }
}