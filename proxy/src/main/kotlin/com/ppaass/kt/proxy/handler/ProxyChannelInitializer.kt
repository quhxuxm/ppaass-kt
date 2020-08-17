package com.ppaass.kt.proxy.handler

import com.ppaass.kt.common.netty.codec.AgentMessageDecoder
import com.ppaass.kt.common.netty.codec.ProxyMessageEncoder
import com.ppaass.kt.common.netty.handler.ResourceClearHandler
import com.ppaass.kt.proxy.ProxyConfiguration
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelInitializer
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.LengthFieldBasedFrameDecoder
import io.netty.handler.codec.LengthFieldPrepender
import io.netty.handler.codec.compression.Lz4FrameDecoder
import io.netty.handler.codec.compression.Lz4FrameEncoder
import io.netty.handler.timeout.IdleStateHandler
import mu.KotlinLogging
import org.springframework.stereotype.Service

/**
 * The channel initializer for proxy
 */
@Service
@ChannelHandler.Sharable
internal class ProxyChannelInitializer(private val proxyConfiguration: ProxyConfiguration) :
        ChannelInitializer<SocketChannel>() {
    private val heartbeatHandler = HeartbeatHandler()
    private val setupTargetConnectionHandler = SetupTargetConnectionHandler(proxyConfiguration)

    private companion object {
        private val logger = KotlinLogging.logger {}
        private val lengthFieldPrepender = LengthFieldPrepender(4)
    }

    override fun initChannel(proxyChannel: SocketChannel) {
        proxyChannel.pipeline().apply {
            logger.debug { "Begin to initialize proxy channel ${proxyChannel.id().asLongText()}" }
            addLast(IdleStateHandler(0, 0, proxyConfiguration.agentConnectionIdleSeconds))
            addLast(heartbeatHandler)
            addLast(ResourceClearHandler())
            //Inbound
            addLast(Lz4FrameDecoder())
            addLast(LengthFieldBasedFrameDecoder(Int.MAX_VALUE, 0, 4, 0, 4))
            addLast(AgentMessageDecoder(
                    proxyPrivateKeyString = proxyConfiguration.proxyPrivateKey))
            addLast(setupTargetConnectionHandler)
            //Outbound
            addLast(Lz4FrameEncoder())
            addLast(lengthFieldPrepender)
            addLast(ProxyMessageEncoder(
                    agentPublicKeyString = proxyConfiguration.agentPublicKey))
        }
    }
}
