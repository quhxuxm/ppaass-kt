package com.ppaass.kt.proxy;

import mu.KotlinLogging
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties

/**
 * The proxy launcher
 */
@SpringBootApplication
@EnableConfigurationProperties(ProxyConfiguration::class)
class ProxyLauncher {
    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    fun launch(vararg arguments: String) {
        val context = SpringApplication.run(ProxyLauncher::class.java)
        val proxy = context.getBean(IProxy::class.java);
        try {
            logger.debug("Begin to start proxy server.")
            proxy.start();
            logger.debug("Success to start proxy server.")
        } catch (e: Exception) {
            logger.error("Fail to stat proxy server because of exception", e)
            proxy.stop();
        }
    }
}

fun main(args: Array<String>) {
    val logger = KotlinLogging.logger {}
    logger.info { "Initializing proxy launcher ..." }
    val launcher = ProxyLauncher();
    logger.info { "Launcher is ready ..." }
    launcher.launch(*args);
}
