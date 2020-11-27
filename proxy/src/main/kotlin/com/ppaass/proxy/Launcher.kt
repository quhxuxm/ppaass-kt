package com.ppaass.proxy

import mu.KotlinLogging
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import kotlin.concurrent.thread

private val logger = KotlinLogging.logger { }

/**
 * The launcher of the proxy server
 */
@SpringBootApplication
@EnableConfigurationProperties(ProxyConfiguration::class)
class Launcher

fun main(args: Array<String>) {
    val applicationContext = SpringApplication.run(Launcher::class.java)
    val proxy = applicationContext.getBean(Proxy::class.java)
    Runtime.getRuntime().addShutdownHook(thread(start = false) {
        logger.info { "Begin to stop proxy..." }
        proxy.stop()
        logger.info { "Proxy stopped..." }
    })
    logger.info { "Begin to start proxy..." }
    proxy.start();
    logger.info { "Proxy started..." }
}
