package com.ppaass.kt.agent

import com.ppaass.kt.agent.configuration.AgentConfiguration
import com.ppaass.kt.agent.handler.HeartbeatHandler
import com.ppaass.kt.agent.handler.http.HttpProxySetupConnectionHandler
import com.ppaass.kt.common.netty.handler.ResourceClearHandler
import io.netty.channel.ChannelInitializer
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpServerCodec
import io.netty.handler.timeout.IdleStateHandler
import mu.KotlinLogging
import org.springframework.stereotype.Service

@Service
internal class HttpAgent(
    private val agentConfiguration: AgentConfiguration,
    private val httpProxySetupConnectionHandler: HttpProxySetupConnectionHandler,
    private val heartbeatHandler: HeartbeatHandler,
    private val resourceClearHandler: ResourceClearHandler) : Agent(agentConfiguration) {
    final override val channelInitializer: ChannelInitializer<SocketChannel>

    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    init {
        logger.info { "Initializing http agent." }
        this.channelInitializer = object : ChannelInitializer<SocketChannel>() {
            override fun initChannel(agentChannel: SocketChannel) {
                with(agentChannel.pipeline()) {
                    addLast(IdleStateHandler(0, 0,
                        agentConfiguration.staticAgentConfiguration.clientConnectionIdleSeconds))
                    addLast(heartbeatHandler)
                    addLast(resourceClearHandler)
                    addLast(HttpServerCodec::class.java.name, HttpServerCodec())
                    addLast(HttpObjectAggregator::class.java.name,
                        HttpObjectAggregator(Int.MAX_VALUE, true))
                    addLast(httpProxySetupConnectionHandler)
                }
            }
        }
    }
}
