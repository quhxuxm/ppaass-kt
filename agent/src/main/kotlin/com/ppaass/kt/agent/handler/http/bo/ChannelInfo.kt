package com.ppaass.kt.agent.handler.http.bo

import io.netty.channel.Channel

internal data class ChannelInfo(
        val channel: Channel,
        val targetHost: String,
        val targetPort: Int
) {
    override fun toString(): String {
        return "ChannelCacheInfo(channel=$channel, targetHost='$targetHost', targetPort=$targetPort)"
    }
}