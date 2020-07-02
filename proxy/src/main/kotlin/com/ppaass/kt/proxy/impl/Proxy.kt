package com.ppaass.kt.proxy.impl

import com.ppaass.kt.proxy.api.IProxy
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelOption
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class Proxy(proxyConfiguration: ProxyConfiguration) : IProxy {
    companion object {
        val logger: Logger = LoggerFactory.getLogger(Proxy::class.java);
    }

    private val masterThreadGroup: NioEventLoopGroup
    private val workerThreadGroup: NioEventLoopGroup
    private val serverBootstrap: ServerBootstrap

    init {
        this.masterThreadGroup = NioEventLoopGroup(proxyConfiguration.masterIoEventThreadNumber)
        this.workerThreadGroup = NioEventLoopGroup(proxyConfiguration.workerIoEventThreadNumber)
        this.serverBootstrap = ServerBootstrap()
        this.serverBootstrap.group(this.masterThreadGroup, this.workerThreadGroup)
        this.serverBootstrap.channel(NioServerSocketChannel::class.java)
        this.serverBootstrap.option(ChannelOption.SO_BACKLOG, proxyConfiguration.soBacklog)
        this.serverBootstrap.option(ChannelOption.TCP_NODELAY, true)
        this.serverBootstrap.option(ChannelOption.SO_REUSEADDR, true)
        this.serverBootstrap.childOption(ChannelOption.TCP_NODELAY, true)
        this.serverBootstrap.childHandler()
    }

    override fun start() {
        TODO("Not yet implemented")
    }

    override fun stop() {
        TODO("Not yet implemented")
    }
}