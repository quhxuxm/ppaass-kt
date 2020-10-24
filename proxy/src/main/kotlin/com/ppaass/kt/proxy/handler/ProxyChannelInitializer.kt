package com.ppaass.kt.proxy.handler

import com.ppaass.kt.common.netty.codec.AgentMessageDecoder
import com.ppaass.kt.common.netty.codec.ProxyMessageEncoder
import com.ppaass.kt.common.netty.handler.ExceptionHandler
import com.ppaass.kt.proxy.ProxyConfiguration
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelInitializer
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.LengthFieldBasedFrameDecoder
import io.netty.handler.codec.LengthFieldPrepender
import io.netty.handler.codec.compression.Lz4FrameDecoder
import io.netty.handler.codec.compression.Lz4FrameEncoder
import io.netty.handler.timeout.IdleStateHandler
import io.netty.handler.traffic.GlobalChannelTrafficShapingHandler
import mu.KotlinLogging
import org.springframework.stereotype.Service

/**
 * The channel initializer for proxy
 */
@ChannelHandler.Sharable
@Service
internal class ProxyChannelInitializer(
    private val proxyConfiguration: ProxyConfiguration,
    private val globalChannelTrafficShapingHandler: GlobalChannelTrafficShapingHandler,
    private val setupTargetConnectionHandler: SetupTargetConnectionHandler,
    private val proxyChannelHeartbeatHandler: ProxyChannelHeartbeatHandler,
    private val exceptionHandler: ExceptionHandler) :
    ChannelInitializer<SocketChannel>() {
    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    override fun initChannel(proxyChannel: SocketChannel) {
        proxyChannel.pipeline().apply {
            logger.debug { "Begin to initialize proxy channel ${proxyChannel.id().asLongText()}" }
            addLast(globalChannelTrafficShapingHandler)
            addLast(IdleStateHandler(0, 0, proxyConfiguration.agentConnectionIdleSeconds))
            addLast(proxyChannelHeartbeatHandler)
            //Inbound
            if (proxyConfiguration.compressingEnable) {
                addLast(Lz4FrameDecoder())
            }
            addLast(LengthFieldBasedFrameDecoder(Int.MAX_VALUE, 0, 4, 0, 4))
            addLast(AgentMessageDecoder(proxyConfiguration.proxyPrivateKey))
            addLast(setupTargetConnectionHandler)
            //Outbound
            if (proxyConfiguration.compressingEnable) {
                addLast(Lz4FrameEncoder())
            }
            addLast(LengthFieldPrepender(4))
            addLast(ProxyMessageEncoder(proxyConfiguration.agentPublicKey))
            addLast(exceptionHandler)
        }
    }
}
