package com.ppaass.kt.agent.handler.socks.v4

import com.ppaass.kt.agent.configuration.AgentConfiguration
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.socksx.SocksMessage
import io.netty.handler.codec.socksx.v4.Socks4CommandRequest

class SocksV4Handler(private val agentConfiguration: AgentConfiguration) : SimpleChannelInboundHandler<SocksMessage>() {
    override fun channelRead0(agentChannelContext: ChannelHandlerContext, msg: SocksMessage) {
        val channelPipeline = agentChannelContext.pipeline()
        with(channelPipeline) {
            when (msg) {
                is Socks4CommandRequest -> {
                }
            }
        }
    }
}