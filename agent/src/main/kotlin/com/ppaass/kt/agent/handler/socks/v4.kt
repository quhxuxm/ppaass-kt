package com.ppaass.kt.agent.handler.socks

import com.ppaass.kt.agent.configuration.AgentConfiguration
import io.netty.channel.ChannelInboundHandlerAdapter

class SocksV4ConnectionHandler(private val agentConfiguration: AgentConfiguration) : ChannelInboundHandlerAdapter() {
}