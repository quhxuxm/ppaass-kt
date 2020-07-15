package com.ppaass.kt.agent

import com.ppaass.kt.agent.configuration.AgentConfiguration
import com.ppaass.kt.agent.handler.http.SetupProxyConnectionHandler
import io.netty.channel.ChannelInitializer
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpServerCodec
import io.netty.handler.stream.ChunkedWriteHandler
import org.springframework.stereotype.Service

@Service
internal class HttpAgent(private val agentConfiguration: AgentConfiguration) : Agent(agentConfiguration) {
    final override val channelInitializer: ChannelInitializer<SocketChannel>
    private val setupProxyConnectionHandler = SetupProxyConnectionHandler(this.agentConfiguration)

    init {
        this.channelInitializer = object : ChannelInitializer<SocketChannel>() {
            override fun initChannel(agentChannel: SocketChannel) {
                with(agentChannel.pipeline()) {
                    addLast(HttpServerCodec::class.java.name, HttpServerCodec())
                    addLast(HttpObjectAggregator::class.java.name,
                            HttpObjectAggregator(Int.MAX_VALUE, true))
                    addLast(ChunkedWriteHandler::class.java.name, ChunkedWriteHandler())
                    addLast(setupProxyConnectionHandler)
                }
            }
        }
    }
}