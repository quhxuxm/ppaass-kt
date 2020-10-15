package com.ppaass.kt.proxy.handler

import com.ppaass.kt.common.netty.codec.AgentMessageDecoder
import com.ppaass.kt.common.netty.codec.ProxyMessageEncoder
import com.ppaass.kt.proxy.ProxyConfiguration
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelInitializer
import io.netty.channel.EventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.LengthFieldBasedFrameDecoder
import io.netty.handler.codec.compression.Lz4FrameDecoder
import io.netty.handler.codec.compression.Lz4FrameEncoder
import io.netty.handler.timeout.IdleStateHandler
import io.netty.handler.traffic.GlobalChannelTrafficShapingHandler
import mu.KotlinLogging
import java.util.concurrent.Executors

/**
 * The channel initializer for proxy
 */
@ChannelHandler.Sharable
internal class ProxyChannelInitializer(private val proxyConfiguration: ProxyConfiguration,
                                       private val targetBootstrapIoEventLoopGroup: EventLoopGroup) :
    ChannelInitializer<SocketChannel>() {
    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    private val globalChannelTrafficShapingHandler = GlobalChannelTrafficShapingHandler(
        Executors.newSingleThreadScheduledExecutor(),
        proxyConfiguration.writeGlobalLimit,
        proxyConfiguration.readGlobalLimit,
        proxyConfiguration.writeChannelLimit,
        proxyConfiguration.readChannelLimit,
        proxyConfiguration.trafficShapingCheckInterval
    )
    private val targetGlobalChannelTrafficShapingHandler = GlobalChannelTrafficShapingHandler(
        Executors.newSingleThreadScheduledExecutor(),
        proxyConfiguration.targetWriteGlobalLimit,
        proxyConfiguration.targetReadGlobalLimit,
        proxyConfiguration.targetWriteChannelLimit,
        proxyConfiguration.targetReadChannelLimit,
        proxyConfiguration.targetTrafficShapingCheckInterval
    )

    override fun initChannel(proxyChannel: SocketChannel) {
        proxyChannel.pipeline().apply {
            logger.debug { "Begin to initialize proxy channel ${proxyChannel.id().asLongText()}" }
            addLast(globalChannelTrafficShapingHandler)
            addLast(IdleStateHandler(0, 0, proxyConfiguration.agentConnectionIdleSeconds))
            addLast(heartbeatHandler)
            addLast(resourceClearHandler)
            //Inbound
            addLast(Lz4FrameDecoder())
            addLast(LengthFieldBasedFrameDecoder(Int.MAX_VALUE, 0, 4, 0, 4))
            addLast(AgentMessageDecoder(proxyConfiguration.proxyPrivateKey))
            addLast(SetupTargetConnectionHandler(proxyConfiguration, targetBootstrapIoEventLoopGroup,
                targetGlobalChannelTrafficShapingHandler))
            //Outbound
            addLast(Lz4FrameEncoder())
            addLast(lengthFieldPrepender)
            addLast(ProxyMessageEncoder(proxyConfiguration.agentPublicKey))
        }
    }
}
