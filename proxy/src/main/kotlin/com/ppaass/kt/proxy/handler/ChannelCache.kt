package com.ppaass.kt.proxy.handler

import io.netty.channel.Channel
import java.util.concurrent.ConcurrentHashMap

object ChannelCache {
    private val cache: MutableMap<String, Channel>;

    init {
        this.cache = ConcurrentHashMap()
    }

    fun get(targetAddress: String, targetPort: Int): Channel? {
        val key = "$targetAddress:$targetPort"
        return cache[key]
    }

    fun put(targetAddress: String, targetPort: Int, channel: Channel) {
        val key = "$targetAddress:$targetPort"
        cache[key] = channel
    }
}
