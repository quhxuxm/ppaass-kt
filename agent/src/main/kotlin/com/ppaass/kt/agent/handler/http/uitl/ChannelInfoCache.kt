package com.ppaass.kt.agent.handler.http.uitl

import com.ppaass.kt.agent.handler.http.bo.ChannelInfo
import java.util.*

internal object ChannelInfoCache {
    private val cache = Hashtable<String, ChannelInfo>()

    fun getChannelInfo(clientId: String): ChannelInfo? {
        return cache[clientId]
    }

    fun saveChannelInfo(clientId: String, channelInfo: ChannelInfo) {
        cache[clientId] = channelInfo
    }

    fun removeChannelInfo(clientId: String) {
        cache.remove(clientId)
    }
}