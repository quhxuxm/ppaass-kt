package com.ppaass.agent.handler.socks

import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelInitializer
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.socksx.SocksPortUnificationServerHandler
import org.springframework.stereotype.Service

@ChannelHandler.Sharable
@Service
internal class SocksChannelInitializer : ChannelInitializer<SocketChannel>() {
    override fun initChannel(agentChannel: SocketChannel) {
        val  agentChannelPipeline=agentChannel.pipeline()
        agentChannelPipeline.addLast(SocksPortUnificationServerHandler())
        agentChannelPipeline.addLast(SAEntryHandler::class.java.getName(), saEntryHandler)
    }
}
