package com.ppaass.kt.proxy

import com.ppaass.kt.proxy.handler.ProxyChannelInitializer
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelOption
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Service

/**
 * The proxy implementation
 */
@Service
internal class DefaultProxy(private val proxyConfiguration: ProxyConfiguration) :
        IProxy {
    companion object {
        val logger: Logger = LoggerFactory.getLogger(DefaultProxy::class.java);
    }

    private val proxyChannelInitializer = ProxyChannelInitializer(this.proxyConfiguration)
    private val masterThreadGroup: NioEventLoopGroup
    private val workerThreadGroup: NioEventLoopGroup
    private val serverBootstrap: ServerBootstrap

    init {
        this.masterThreadGroup = NioEventLoopGroup(this.proxyConfiguration.masterIoEventThreadNumber)
        this.workerThreadGroup = NioEventLoopGroup(this.proxyConfiguration.workerIoEventThreadNumber)
        this.serverBootstrap = ServerBootstrap()
        with(this.serverBootstrap) {
            group(masterThreadGroup, workerThreadGroup)
            channel(NioServerSocketChannel::class.java)
            option(ChannelOption.SO_BACKLOG, proxyConfiguration.soBacklog)
            option(ChannelOption.TCP_NODELAY, true)
            option(ChannelOption.SO_REUSEADDR, true)
            childOption(ChannelOption.TCP_NODELAY, true)
            childHandler(proxyChannelInitializer)
        }
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
