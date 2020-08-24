package com.ppaass.kt.test

import org.apache.commons.io.IOUtils
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

fun main() {

    val url = URL("https://www.behance.net/")
    val proxyAddress = InetSocketAddress.createUnresolved("localhost", 10080)
    (1..1000).forEach {
        val connection = url.openConnection(Proxy(Proxy.Type.SOCKS, proxyAddress))
        println("The $it time to access: ")
        val inputStream = connection.getInputStream()
        IOUtils.readLines(inputStream, Charsets.UTF_8).forEach {
            println(it)
        }
        inputStream.close()
        Thread.sleep(1000)
    }
}
