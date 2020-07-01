package com.ppaass.kt.proxy

import com.ppaass.kt.proxy.api.IProxy
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.ApplicationContext

@SpringBootApplication
class ProxyMain

fun main(args: Array<String>) {
    val context: ApplicationContext = SpringApplication.run(ProxyMain::class.java)
    val proxy = context.getBean(IProxy::class.java);
    try {
        proxy.init();
        proxy.start();
    } catch (e: Exception) {
        proxy.stop();
    }
}