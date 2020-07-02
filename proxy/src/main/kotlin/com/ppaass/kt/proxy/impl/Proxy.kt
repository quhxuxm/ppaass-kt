package com.ppaass.kt.proxy.impl

import com.ppaass.kt.proxy.api.IProxy
import io.netty.channel.nio.NioEventLoopGroup
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class Proxy(proxyConfiguration: ProxyConfiguration) : IProxy {
    companion object {
        val logger: Logger = LoggerFactory.getLogger(Proxy::class.java);
    }

    private val masterThreadGroup: NioEventLoopGroup

    init {
        this.masterThreadGroup = NioEventLoopGroup(proxyConfiguration.masterIoEventThreadNumber);
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