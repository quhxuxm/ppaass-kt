package com.ppaass.kt.agent.handler.socks.v4

import com.ppaass.kt.agent.configuration.AgentConfiguration
import io.netty.channel.ChannelInboundHandlerAdapter

class SocksV4Handler(private val agentConfiguration: AgentConfiguration) : ChannelInboundHandlerAdapter() {
}