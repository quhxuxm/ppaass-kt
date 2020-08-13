package com.ppaass.kt.agent.handler.http.bo

import io.netty.channel.Channel

internal data class ChannelInfo(
        val channel: Channel,
        val targetHost: String,
        val targetPort: Int,
        var proxyConnectionActivated: Boolean = false
) {
    override fun toString(): String {
        return "ChannelInfo(channel=$channel, targetHost='$targetHost', targetPort=$targetPort, proxyConnectionActivated=$proxyConnectionActivated)"
    }
}
