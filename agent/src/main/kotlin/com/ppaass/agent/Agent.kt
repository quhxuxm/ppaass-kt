package com.ppaass.agent

import com.ppaass.agent.handler.SwitchProtocolHandler
import com.ppaass.kt.common.PpaassException
import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.PooledByteBufAllocator
import io.netty.channel.Channel
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.EventLoopGroup
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import mu.KotlinLogging
import org.springframework.stereotype.Service

@Service
internal class Agent(private val agentConfiguration: AgentConfiguration,
                     private val switchProtocolHandler: SwitchProtocolHandler) {
    private companion object {
        private val logger = KotlinLogging.logger { }
    }

    private var serverSocketChannel: Channel? = null
    private var masterThreadGroup: EventLoopGroup? = null
    private var workerThreadGroup: EventLoopGroup? = null

    fun start() {
        val newServerBootstrap = ServerBootstrap()
        val newMasterThreadGroup = NioEventLoopGroup(
            this.agentConfiguration.agentTcpMasterThreadNumber)
        val newWorkerThreadGroup = NioEventLoopGroup(
            agentConfiguration.agentTcpWorkerThreadNumber)
        newServerBootstrap.group(newMasterThreadGroup, newWorkerThreadGroup)
        newServerBootstrap.channel(NioServerSocketChannel::class.java)
        newServerBootstrap
            .option(ChannelOption.SO_BACKLOG,
                agentConfiguration.agentTcpSoBacklog)
        newServerBootstrap.childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
        newServerBootstrap.childOption(ChannelOption.TCP_NODELAY, true)
        newServerBootstrap.childOption(ChannelOption.AUTO_CLOSE, false)
        newServerBootstrap.childOption(ChannelOption.SO_KEEPALIVE, true)
        newServerBootstrap.childOption(ChannelOption.SO_LINGER,
            agentConfiguration.agentTcpSoLinger)
        newServerBootstrap
            .childOption(ChannelOption.SO_RCVBUF,
                agentConfiguration.agentTcpSoRcvbuf)
        newServerBootstrap
            .childOption(ChannelOption.SO_SNDBUF,
                agentConfiguration.agentTcpSoSndbuf)
        val channelInitializer = object : ChannelInitializer<SocketChannel>() {
            override fun initChannel(agentChannel: SocketChannel) {
                agentChannel.pipeline().addLast(switchProtocolHandler)
            }
        }
        newServerBootstrap.childHandler(channelInitializer)
        val agentPort = this.agentConfiguration.port
        if (agentPort == null) {
            throw PpaassException("Fail to start ppaass agent because of port is empty.")
        }
        val channelFuture = try {
            newServerBootstrap.bind(agentPort).sync()
        } catch (e: InterruptedException) {
            logger.error("Fail to start ppaass because of exception", e)
            throw PpaassException("Fail to start ppaass because of exception", e)
        }
        this.serverSocketChannel = channelFuture.channel()
        this.masterThreadGroup = newMasterThreadGroup
        this.workerThreadGroup = newWorkerThreadGroup
    }

    fun stop() {
        this.serverSocketChannel?.close()?.sync()
        this.masterThreadGroup?.shutdownGracefully()
        this.workerThreadGroup?.shutdownGracefully()
        this.serverSocketChannel = null
        this.masterThreadGroup = null
        this.workerThreadGroup = null
    }
}
