package com.ppaass.kt.proxy

import com.ppaass.kt.proxy.handler.ProxyChannelInitializer
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.AdaptiveRecvByteBufAllocator
import io.netty.channel.Channel
import io.netty.channel.ChannelOption
import io.netty.channel.EventLoopGroup
import io.netty.channel.WriteBufferWaterMark
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import mu.KotlinLogging
import org.springframework.stereotype.Service

/**
 * The proxy implementation
 */
@Service
internal class DefaultProxy(
    private val proxyConfiguration: ProxyConfiguration,
    private val masterIoEventLoopGroup: EventLoopGroup,
    private val workerIoEventLoopGroup: EventLoopGroup,
    private val proxyChannelInitializer: ProxyChannelInitializer) :
    IProxy {
    private companion object {
        @JvmStatic
        private val logger = KotlinLogging.logger {}
    }

    private val targetBootstrapIoEventLoopGroup: NioEventLoopGroup
    private val serverBootstrap: ServerBootstrap
    private var serverChannel: Channel? = null

    init {
        this.targetBootstrapIoEventLoopGroup = NioEventLoopGroup(proxyConfiguration.dataTransferIoEventThreadNumber)
        this.serverBootstrap = ServerBootstrap()
        this.serverBootstrap.apply {
            group(masterIoEventLoopGroup, workerIoEventLoopGroup)
            channel(NioServerSocketChannel::class.java)
            option(ChannelOption.SO_BACKLOG, proxyConfiguration.soBacklog)
            option(ChannelOption.TCP_NODELAY, true)
            option(ChannelOption.SO_REUSEADDR, true)
            childOption(ChannelOption.TCP_NODELAY, true)
            childOption(ChannelOption.SO_KEEPALIVE, proxyConfiguration.connectionKeepAlive)
            childOption(ChannelOption.SO_RCVBUF, proxyConfiguration.soRcvbuf)
            childOption(ChannelOption.SO_SNDBUF, proxyConfiguration.soSndbuf)
            childOption(ChannelOption.SO_SNDBUF, proxyConfiguration.writeSpinCount)
            childOption(ChannelOption.WRITE_BUFFER_WATER_MARK,
                WriteBufferWaterMark(proxyConfiguration.writeBufferWaterMarkLow,
                    proxyConfiguration.writeBufferWaterMarkHigh))
            childOption(ChannelOption.RCVBUF_ALLOCATOR,
                AdaptiveRecvByteBufAllocator(proxyConfiguration.receiveDataAverageBufferMinSize, proxyConfiguration
                    .receiveDataAverageBufferInitialSize, proxyConfiguration.receiveDataAverageBufferMaxSize))
            childHandler(proxyChannelInitializer)
        }
    }

    override fun start() {
        logger.debug("Begin to start proxy ...")
        val channelFuture = this.serverBootstrap.bind(this.proxyConfiguration.port).sync()
        this.serverChannel = channelFuture.channel()
    }

    override fun stop() {
        logger.debug("Stop proxy ...")
        this.masterIoEventLoopGroup.shutdownGracefully()
        this.workerIoEventLoopGroup.shutdownGracefully()
        this.serverChannel?.close()
    }
}
