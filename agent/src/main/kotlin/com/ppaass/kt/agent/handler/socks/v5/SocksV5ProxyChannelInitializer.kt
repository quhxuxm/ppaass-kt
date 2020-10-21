package com.ppaass.kt.agent.handler.socks.v5

import com.ppaass.kt.agent.configuration.AgentConfiguration
import com.ppaass.kt.agent.handler.PreForwardProxyMessageHandler
import com.ppaass.kt.common.netty.codec.AgentMessageEncoder
import com.ppaass.kt.common.netty.codec.ProxyMessageDecoder
import com.ppaass.kt.common.netty.handler.ResourceClearHandler
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelInitializer
import io.netty.channel.EventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.LengthFieldBasedFrameDecoder
import io.netty.handler.codec.LengthFieldPrepender
import io.netty.handler.codec.compression.Lz4FrameDecoder
import io.netty.handler.codec.compression.Lz4FrameEncoder
import org.springframework.stereotype.Service

@ChannelHandler.Sharable
@Service
internal class SocksV5ProxyChannelInitializer(
    private val agentConfiguration: AgentConfiguration,
    private val dataTransferIoEventLoopGroup: EventLoopGroup,
    private val socksV5ProxyToAgentHandler: SocksV5ProxyToAgentHandler,
    private val preForwardProxyMessageHandler: PreForwardProxyMessageHandler,
    private val resourceClearHandler: ResourceClearHandler
) : ChannelInitializer<SocketChannel>() {
    override fun initChannel(proxyChannel: SocketChannel) {
        with(proxyChannel.pipeline()) {
            if (agentConfiguration.staticAgentConfiguration.compressingEnable) {
                addLast(Lz4FrameDecoder())
            }
            addLast(LengthFieldBasedFrameDecoder(Int.MAX_VALUE,
                0, 4, 0,
                4))
            addLast(ProxyMessageDecoder(agentConfiguration.staticAgentConfiguration.agentPrivateKey))
            addLast(preForwardProxyMessageHandler)
            addLast(dataTransferIoEventLoopGroup, socksV5ProxyToAgentHandler)
            if (agentConfiguration.staticAgentConfiguration.compressingEnable) {
                addLast(Lz4FrameEncoder())
            }
            addLast(LengthFieldPrepender(4))
            addLast(AgentMessageEncoder(agentConfiguration.staticAgentConfiguration.proxyPublicKey))
            addLast(resourceClearHandler)
        }
    }
}
