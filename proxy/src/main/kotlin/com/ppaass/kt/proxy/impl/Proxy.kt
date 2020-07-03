package com.ppaass.kt.proxy.impl

import com.ppaass.kt.common.netty.codec.AgentMessageDecoder
import com.ppaass.kt.common.netty.codec.ProxyMessageEncoder
import com.ppaass.kt.proxy.api.IProxy
import com.ppaass.kt.proxy.impl.netty.handler.HeartbeatChannelHandler
import com.ppaass.kt.proxy.impl.netty.handler.ProxyAndTargetConnectionHandler
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
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
private class ProxyChannelInitializer(private val proxyConfiguration: ProxyConfiguration,
                                      private val proxyAndTargetConnectionHandler: ProxyAndTargetConnectionHandler) :
        ChannelInitializer<SocketChannel>() {
    override fun initChannel(proxyChannel: SocketChannel) {
        proxyChannel.pipeline().apply {
            addLast(IdleStateHandler(0, 0, proxyConfiguration.agentConnectionIdleSeconds))
            addLast(HeartbeatChannelHandler())
            //Inbound
            addLast(ChunkedWriteHandler())
            addLast(Lz4FrameDecoder())
            addLast(LengthFieldBasedFrameDecoder(Int.MAX_VALUE, 0, 4, 0, 4))
            addLast(AgentMessageDecoder())
            addLast(proxyAndTargetConnectionHandler)
            //Outbound
            addLast(Lz4FrameEncoder())
            addLast(LengthFieldPrepender(4))
            addLast(ProxyMessageEncoder())
        }
    }
}

@Service
class Proxy(private val proxyConfiguration: ProxyConfiguration) :
        IProxy {
    companion object {
        val logger: Logger = LoggerFactory.getLogger(Proxy::class.java);
    }

    @Autowired
    private lateinit var proxyChannelInitializer: ProxyChannelInitializer
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