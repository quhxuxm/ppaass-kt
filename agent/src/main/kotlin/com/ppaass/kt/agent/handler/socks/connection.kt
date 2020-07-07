package com.ppaass.kt.agent.handler.socks

import com.ppaass.kt.agent.AgentConfiguration
import io.netty.bootstrap.Bootstrap
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.socket.SocketChannel
import io.netty.util.concurrent.EventExecutorGroup

@ChannelHandler.Sharable
class SocksConnectionHandler(private val agentConfiguration: AgentConfiguration) : ChannelInboundHandlerAdapter() {

}