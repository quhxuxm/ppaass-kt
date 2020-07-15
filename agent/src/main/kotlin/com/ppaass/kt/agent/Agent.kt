package com.ppaass.kt.agent

import com.ppaass.kt.agent.configuration.AgentConfiguration
import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.PooledByteBufAllocator
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.EventLoopGroup
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel

/**
 * The agent interface
 */
internal abstract class Agent(private val agentConfiguration: AgentConfiguration) {
    private var serverBootstrap: ServerBootstrap? = null
    private var masterThreadGroup: EventLoopGroup? = null
    private var workerThreadGroup: EventLoopGroup? = null
    protected abstract val channelInitializer: ChannelInitializer<SocketChannel>

    /**
     * Start agent
     */
    fun start() {
        val newServerBootstrap = ServerBootstrap()
        val newMasterThreadGroup =
                NioEventLoopGroup(agentConfiguration.staticAgentConfiguration.masterIoEventThreadNumber)
        val newWorkerThreadGroup =
                NioEventLoopGroup(agentConfiguration.staticAgentConfiguration.workerIoEventThreadNumber)
        with(newServerBootstrap) {
            group(newMasterThreadGroup, newWorkerThreadGroup)
            channel(NioServerSocketChannel::class.java)
            option(ChannelOption.SO_BACKLOG, agentConfiguration.staticAgentConfiguration.soBacklog)
            option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
            option(ChannelOption.TCP_NODELAY, true)
            childOption(ChannelOption.TCP_NODELAY, true)
            childHandler(this@Agent.channelInitializer)
        }
        newServerBootstrap.bind(this.agentConfiguration.port).sync()
        this.serverBootstrap = newServerBootstrap
        this.masterThreadGroup = newMasterThreadGroup
        this.workerThreadGroup = newWorkerThreadGroup
    }

    /**
     * Stop agent
     */
    fun stop() {
        this.masterThreadGroup?.shutdownGracefully()
        this.workerThreadGroup?.shutdownGracefully()
        this.serverBootstrap = null
    }
}


