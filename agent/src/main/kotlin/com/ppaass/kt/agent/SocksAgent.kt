package com.ppaass.kt.agent

import com.ppaass.kt.agent.configuration.AgentConfiguration
import com.ppaass.kt.agent.handler.HeartbeatHandler
import com.ppaass.kt.agent.handler.socks.SwitchSocksVersionHandler
import com.ppaass.kt.common.netty.handler.ExceptionHandler
import io.netty.channel.ChannelInitializer
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.socksx.SocksPortUnificationServerHandler
import io.netty.handler.timeout.IdleStateHandler
import mu.KotlinLogging
import org.springframework.stereotype.Service

@Service
internal class SocksAgent(
    private val agentConfiguration: AgentConfiguration,
    private val switchSocksVersionHandler: SwitchSocksVersionHandler,
    private val heartbeatHandler: HeartbeatHandler,
    private val exceptionHandler: ExceptionHandler) : Agent(agentConfiguration) {
    final override val channelInitializer: ChannelInitializer<SocketChannel>

    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    init {
        logger.info { "Initializing socks agent." }
        this.channelInitializer = object : ChannelInitializer<SocketChannel>() {
            override fun initChannel(socketChannel: SocketChannel) {
                with(socketChannel.pipeline()) {
                    addLast(IdleStateHandler(0, 0,
                        agentConfiguration.staticAgentConfiguration.clientConnectionIdleSeconds))
                    addLast(heartbeatHandler)
                    addLast(SocksPortUnificationServerHandler())
                    addLast(exceptionHandler)
                    addLast(switchSocksVersionHandler)
                }
            }
        }
    }
}
