package com.ppaass.agent.handler.http

import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelInitializer
import io.netty.channel.socket.SocketChannel
import org.springframework.stereotype.Service

@ChannelHandler.Sharable
@Service
internal class HttpChannelInitializer : ChannelInitializer<SocketChannel>() {
    override fun initChannel(ch: SocketChannel?) {
        TODO("Not yet implemented")
    }
}
