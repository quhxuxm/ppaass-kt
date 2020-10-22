package com.ppaass.kt.test

import org.apache.commons.io.IOUtils
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.util.concurrent.Executors

fun main() {
    val url1 = URL("https://www.behance.net/")
    val url2 = URL("https://www.youtube.com/")
    val url3 = URL("https://www.google.com.hk/")
    val url4 = URL("https://www.baidu.com/")
    val urls = arrayOf(url1, url2, url3, url4)
    val proxyAddress = InetSocketAddress.createUnresolved("localhost", 10080)
    val threadPool = Executors.newFixedThreadPool(100)
    while (true) {
        threadPool.submit {
            val connection =
                urls[(Math.random() * 1000 % 4).toInt()].openConnection(Proxy(Proxy.Type.SOCKS, proxyAddress))
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
        Thread.sleep(2000)
    }
}
