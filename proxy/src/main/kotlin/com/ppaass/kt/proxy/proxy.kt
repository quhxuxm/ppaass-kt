package com.ppaass.kt.proxy

import com.ppaass.kt.common.netty.codec.AgentMessageDecoder
import com.ppaass.kt.common.netty.codec.ProxyMessageEncoder
import com.ppaass.kt.common.netty.handler.ResourceClearHandler
import com.ppaass.kt.proxy.handler.HeartbeatHandler
import com.ppaass.kt.proxy.handler.ProxyAndTargetConnectionHandler
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.LengthFieldBasedFrameDecoder
import io.netty.handler.codec.LengthFieldPrepender
import io.netty.handler.codec.compression.Lz4FrameDecoder
import io.netty.handler.codec.compression.Lz4FrameEncoder
import io.netty.handler.stream.ChunkedWriteHandler
import io.netty.handler.timeout.IdleStateHandler
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
 * The channel initializer for proxy
 */
@Service
private class ProxyChannelInitializer(private val proxyConfiguration: ProxyConfiguration,
                                      private val proxyAndTargetConnectionHandler: ProxyAndTargetConnectionHandler) :
        ChannelInitializer<SocketChannel>() {
    override fun initChannel(proxyChannel: SocketChannel) {
        proxyChannel.pipeline().apply {
            addLast(IdleStateHandler(0, 0, proxyConfiguration.agentConnectionIdleSeconds))
            addLast(HeartbeatHandler())
            //Inbound
            addLast(ChunkedWriteHandler())
            addLast(Lz4FrameDecoder())
            addLast(LengthFieldBasedFrameDecoder(Int.MAX_VALUE, 0, 4, 0, 4))
            addLast(AgentMessageDecoder())
            addLast(proxyAndTargetConnectionHandler)
            addLast(ResourceClearHandler(proxyChannel))
            //Outbound
            addLast(Lz4FrameEncoder())
            addLast(LengthFieldPrepender(4))
            addLast(ProxyMessageEncoder())
        }
    }
}

/**
 * The proxy implementation
 */
@Service
private class Proxy(private val proxyConfiguration: ProxyConfiguration,
                    private val proxyChannelInitializer: ProxyChannelInitializer) :
        IProxy {
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
        this.serverBootstrap.apply {
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