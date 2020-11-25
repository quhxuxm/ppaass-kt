package com.ppaass.proxy

import io.netty.bootstrap.ServerBootstrap
import mu.KotlinLogging
import org.springframework.stereotype.Service

@Service
internal class Proxy(private val proxyConfiguration: ProxyConfiguration,
                     private val proxyServerBootstrap: ServerBootstrap) {
    private companion object {
        private val logger = KotlinLogging.logger { }
    }

    fun start() {
        logger.info {
            "Begin to start ppaass tcp proxy server on port: ${
                proxyConfiguration.proxyTcpServerPort
            }"
        }
        try {
            proxyServerBootstrap.bind(proxyConfiguration.proxyTcpServerPort).sync()
        } catch (e: Exception) {
            logger.error(e) {
                "Fail to start ppaass tcp proxy because of exception."
            }
            System.exit(1)
        }
        logger.info {
            "Ppaass tcp proxy server success to bind port: ${
                proxyConfiguration.proxyTcpServerPort
            }"
        }
    }

    fun stop() {
        logger.info { "Begin to stop ppaass tcp proxy server..." }
        proxyServerBootstrap.config().group().shutdownGracefully()
        proxyServerBootstrap.config().childGroup().shutdownGracefully()
        logger.info { "Ppaass tcp proxy server stopped gracefully." }
    }
}


