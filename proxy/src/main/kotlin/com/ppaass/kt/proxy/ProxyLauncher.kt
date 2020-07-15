package com.ppaass.kt.proxy;

import org.slf4j.LoggerFactory
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties

/**
 * The proxy launcher
 */
@SpringBootApplication
@EnableConfigurationProperties
class ProxyLauncher {
    private val logger = LoggerFactory.getLogger(ProxyLauncher::class.java);

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
    val launcher = ProxyLauncher();
    launcher.launch(*args);
}