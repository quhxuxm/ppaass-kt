package com.ppaass.kt.test

import org.apache.commons.io.IOUtils
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.util.concurrent.Executors

fun main() {
    val url = URL("https://www.behance.net/")
    val proxyAddress = InetSocketAddress.createUnresolved("localhost", 10080)
    val threadPool = Executors.newFixedThreadPool(100)
    while (true) {
        threadPool.submit {
            val connection = url.openConnection(Proxy(Proxy.Type.SOCKS, proxyAddress))
            val startTime = System.currentTimeMillis()
            println("The ${Thread.currentThread().name} start.")
            val inputStream = connection.getInputStream()
            IOUtils.readLines(inputStream, Charsets.UTF_8).forEach {
                it.length
            }
            inputStream.close()
            val endTime = System.currentTimeMillis()
            println("The ${Thread.currentThread().name} end, use: ${endTime - startTime}ms.")
        }
        Thread.sleep(3000)
    }
}
