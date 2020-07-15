package com.ppaass.kt.agent

import com.ppaass.kt.agent.configuration.AgentConfiguration
import com.ppaass.kt.agent.handler.socks.SwitchSocksVersionConnectionHandler
import io.netty.channel.ChannelInitializer
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.socksx.SocksPortUnificationServerHandler
import org.springframework.stereotype.Service

@Service
internal class SocksAgent(private val agentConfiguration: AgentConfiguration) : Agent(agentConfiguration) {
    final override val channelInitializer: ChannelInitializer<SocketChannel>
    private val switchSocksVersionConnectionHandler = SwitchSocksVersionConnectionHandler(this.agentConfiguration)

    init {
        this.channelInitializer = object : ChannelInitializer<SocketChannel>() {
            override fun initChannel(socketChannel: SocketChannel) {
                with(socketChannel.pipeline()) {
                    addLast(SocksPortUnificationServerHandler())
                    addLast(this@SocksAgent.switchSocksVersionConnectionHandler)
                }
            }
        }
    }
}