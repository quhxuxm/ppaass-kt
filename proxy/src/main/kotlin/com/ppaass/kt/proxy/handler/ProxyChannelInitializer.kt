package com.ppaass.kt.proxy.handler

import com.ppaass.kt.common.netty.codec.AgentMessageDecoder
import com.ppaass.kt.common.netty.codec.ProxyMessageEncoder
import com.ppaass.kt.proxy.ProxyConfiguration
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelInitializer
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.LengthFieldBasedFrameDecoder
import io.netty.handler.codec.LengthFieldPrepender
import io.netty.handler.codec.compression.Lz4FrameDecoder
import io.netty.handler.codec.compression.Lz4FrameEncoder
import io.netty.handler.stream.ChunkedWriteHandler
import io.netty.handler.timeout.IdleStateHandler
import org.springframework.stereotype.Service

/**
 * The channel initializer for proxy
 */
@Service
@ChannelHandler.Sharable
class ProxyChannelInitializer(private val proxyConfiguration: ProxyConfiguration) :
        ChannelInitializer<SocketChannel>() {
    private val heartbeatHandler = HeartbeatHandler()
    private val setupTargetConnectionHandler = SetupTargetConnectionHandler(proxyConfiguration)

    override fun initChannel(proxyChannel: SocketChannel) {
        with(proxyChannel.pipeline()) {
            addLast(IdleStateHandler(0, 0, proxyConfiguration.agentConnectionIdleSeconds))
            addLast(heartbeatHandler)
            //Inbound
            addLast(ChunkedWriteHandler())
            addLast(Lz4FrameDecoder())
            addLast(LengthFieldBasedFrameDecoder(Int.MAX_VALUE, 0, 4, 0, 4))
            addLast(AgentMessageDecoder())
            addLast(setupTargetConnectionHandler)
            //Outbound
            addLast(Lz4FrameEncoder())
            addLast(LengthFieldPrepender(4))
            addLast(ProxyMessageEncoder())
        }
    }
}