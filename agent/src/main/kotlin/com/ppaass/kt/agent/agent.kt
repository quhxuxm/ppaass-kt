package com.ppaass.kt.agent

import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.PooledByteBufAllocator
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.EventLoopGroup
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Service
import java.awt.EventQueue

/**
 * The agent interface
 */
internal sealed class Agent(private val agentConfiguration: AgentConfiguration) {
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
        }
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

@Service
internal class HttpAgent(private val agentConfiguration: AgentConfiguration) : Agent(agentConfiguration) {
    final override val channelInitializer: ChannelInitializer<SocketChannel>

    init {
        this.channelInitializer = object : ChannelInitializer<SocketChannel>() {
            override fun initChannel(socketChannel: SocketChannel) {
            }
        }
    }
}

@Service
internal class SocksAgent(private val agentConfiguration: AgentConfiguration) : Agent(agentConfiguration) {
    final override val channelInitializer: ChannelInitializer<SocketChannel>

    init {
        this.channelInitializer = object : ChannelInitializer<SocketChannel>() {
            override fun initChannel(ch: SocketChannel?) {
                TODO("Not yet implemented")
            }
        }
    }
}

/**
 * The proxy launcher
 */
@SpringBootApplication
@EnableConfigurationProperties
class AgentLauncher {
    private val logger: Logger = LoggerFactory.getLogger(AgentLauncher::class.java);

    fun launch(vararg arguments: String) {
        logger.info("Begin to launch agent.")
        val context: ApplicationContext = SpringApplicationBuilder(AgentLauncher::class.java)
                .headless(false).run(*arguments)
        EventQueue.invokeLater {
            val mainFrame = context.getBean(MainFrame::class.java)
            Runtime.getRuntime().addShutdownHook(Thread(Runnable { mainFrame.stop() }))
            mainFrame.start()
        }
    }
}