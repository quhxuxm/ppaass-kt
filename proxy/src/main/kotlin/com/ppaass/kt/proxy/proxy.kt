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
 * Proxy interface
 */
internal interface IProxy {
    /**
     * Start proxy
     */
    fun start();

    /**
     * Stop proxy
     */
    fun stop();
}

/**
 * The proxy implementation
 */
@Service
private class Proxy(private val proxyConfiguration: ProxyConfiguration) :
        IProxy {
    companion object {
        val logger: Logger = LoggerFactory.getLogger(Proxy::class.java);
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

/**
 * The proxy launcher
 */
@SpringBootApplication
@EnableConfigurationProperties
class ProxyLauncher {
    private val logger: Logger = LoggerFactory.getLogger(ProxyLauncher::class.java);

    fun launch(vararg arguments: String) {
        val context: ApplicationContext = SpringApplication.run(ProxyLauncher::class.java)
        val proxy = context.getBean(IProxy::class.java);
        try {
            logger.debug("Begin to start proxy server.")
            proxy.start();
            logger.debug("Success to start proxy server.")
        } catch (e: Exception) {
            logger.error("Fail to stat proxy server because of exception", e)
            proxy.stop();
        }
    }
}