package com.ppaass.kt.proxy

import com.ppaass.kt.proxy.handler.ProxyChannelInitializer
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.AdaptiveRecvByteBufAllocator
import io.netty.channel.Channel
import io.netty.channel.ChannelOption
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import mu.KotlinLogging
import org.springframework.stereotype.Service

/**
 * The proxy implementation
 */
@Service
internal class DefaultProxy(private val proxyConfiguration: ProxyConfiguration) :
        IProxy {
    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    private val masterThreadGroup: NioEventLoopGroup
    private val workerThreadGroup: NioEventLoopGroup
    private val serverBootstrap: ServerBootstrap
    private var serverChannel: Channel? = null
    private val proxyChannelInitializer = ProxyChannelInitializer(proxyConfiguration)

    init {
        this.masterThreadGroup = NioEventLoopGroup(this.proxyConfiguration.masterIoEventThreadNumber)
        this.workerThreadGroup = NioEventLoopGroup(this.proxyConfiguration.workerIoEventThreadNumber)
        this.serverBootstrap = ServerBootstrap()
        this.serverBootstrap.apply {
            group(masterThreadGroup, workerThreadGroup)
            channel(NioServerSocketChannel::class.java)
            option(ChannelOption.SO_BACKLOG, proxyConfiguration.soBacklog)
            option(ChannelOption.TCP_NODELAY, true)
            option(ChannelOption.SO_REUSEADDR, true)
            childOption(ChannelOption.TCP_NODELAY, true)
            childOption(ChannelOption.SO_RCVBUF, proxyConfiguration.soRcvbuf)
            childOption(ChannelOption.SO_SNDBUF, proxyConfiguration.soSndbuf)
            childOption(ChannelOption.SO_SNDBUF, proxyConfiguration.writeSpinCount)
            childOption(ChannelOption.RCVBUF_ALLOCATOR, AdaptiveRecvByteBufAllocator(proxyConfiguration.receiveDataAverageBufferMinSize, proxyConfiguration
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
        this.masterThreadGroup.shutdownGracefully()
        this.workerThreadGroup.shutdownGracefully()
        this.serverChannel?.close()
    }
}
