package com.ppaass.kt.proxy.handler

import com.ppaass.kt.proxy.ProxyConfiguration
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelInitializer
import io.netty.channel.EventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.handler.timeout.IdleStateHandler
import io.netty.handler.traffic.GlobalChannelTrafficShapingHandler
import org.springframework.stereotype.Service

@ChannelHandler.Sharable
@Service
internal class TargetChannelInitializer(
    private val dataTransferIoEventLoopGroup: EventLoopGroup,
    private val targetGlobalChannelTrafficShapingHandler: GlobalChannelTrafficShapingHandler,
    private val targetToProxyHandler: TargetToProxyHandler,
    private val proxyConfiguration: ProxyConfiguration,
    private val targetChannelKeepAliveHandler: TargetChannelKeepAliveHandler
) : ChannelInitializer<SocketChannel>() {
    override fun initChannel(targetChannel: SocketChannel) {
        with(targetChannel.pipeline()) {
            addLast(targetGlobalChannelTrafficShapingHandler)
            addLast(IdleStateHandler(0, 0, proxyConfiguration.targetConnectionIdleSeconds))
            addLast(targetChannelKeepAliveHandler)
            addLast(dataTransferIoEventLoopGroup, targetToProxyHandler)
        }
    }
}
