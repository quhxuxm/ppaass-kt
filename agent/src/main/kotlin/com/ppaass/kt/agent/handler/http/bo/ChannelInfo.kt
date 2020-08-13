package com.ppaass.kt.agent.handler.http.bo

import io.netty.channel.Channel

internal data class ChannelInfo(
        val clientChannelId: String,
        val proxyChannel: Channel,
        val agentChannel: Channel,
        val targetHost: String,
        val targetPort: Int,
        var proxyConnectionActivated: Boolean = false
)
