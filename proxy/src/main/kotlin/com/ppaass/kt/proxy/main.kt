package com.ppaass.kt.proxy

import com.ppaass.kt.proxy.api.IProxy
import org.slf4j.LoggerFactory
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.ApplicationContext

@SpringBootApplication
class ProxyMain {

    companion object {
        val logger = LoggerFactory.getLogger(ProxyMain::class.java);
    }
}

fun main(args: Array<String>) {
    val context: ApplicationContext = SpringApplication.run(ProxyMain::class.java)
    val proxy = context.getBean(IProxy::class.java);
    try {
        ProxyMain.logger.debug("Begin to initialize proxy server.")
        proxy.init();
        ProxyMain.logger.debug("Begin to start proxy server.")
        proxy.start();
        ProxyMain.logger.debug("Success to start proxy server.")
    } catch (e: Exception) {
        proxy.stop();
    }
}