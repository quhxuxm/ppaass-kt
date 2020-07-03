package com.ppaass.kt.proxy.impl

import com.ppaass.kt.proxy.api.IProxy
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.timeout.IdleStateHandler
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class ProxyChannelInitializer(private val proxyConfiguration: ProxyConfiguration) : ChannelInitializer<SocketChannel>() {
    override fun initChannel(proxyChannel: SocketChannel) {
        proxyChannel.pipeline().addLast(IdleStateHandler(0, 0, proxyConfiguration.agentConnectionIdleSeconds))
    }
}

@Service
class Proxy(private val proxyConfiguration: ProxyConfiguration, proxyChannelInitializer: ProxyChannelInitializer) : IProxy {
    companion object {
        val logger: Logger = LoggerFactory.getLogger(Proxy::class.java);
    }

    private val masterThreadGroup: NioEventLoopGroup
    private val workerThreadGroup: NioEventLoopGroup
    private val serverBootstrap: ServerBootstrap

    init {
        this.masterThreadGroup = NioEventLoopGroup(this.proxyConfiguration.masterIoEventThreadNumber)
        this.workerThreadGroup = NioEventLoopGroup(this.proxyConfiguration.workerIoEventThreadNumber)
        this.serverBootstrap = ServerBootstrap()
        this.serverBootstrap.group(this.masterThreadGroup, this.workerThreadGroup)
        this.serverBootstrap.channel(NioServerSocketChannel::class.java)
        this.serverBootstrap.option(ChannelOption.SO_BACKLOG, this.proxyConfiguration.soBacklog)
        this.serverBootstrap.option(ChannelOption.TCP_NODELAY, true)
        this.serverBootstrap.option(ChannelOption.SO_REUSEADDR, true)
        this.serverBootstrap.childOption(ChannelOption.TCP_NODELAY, true)
        this.serverBootstrap.childHandler(proxyChannelInitializer)
    }

    override fun start() {
        logger.debug("Begin to start proxy ...")
        this.serverBootstrap.bind(this.proxyConfiguration.port).sync()
    }

    override fun stop() {
        logger.debug("Stop proxy ...")
        this.masterThreadGroup.shutdownGracefully()
        this.workerThreadGroup.shutdownGracefully()
    }
}