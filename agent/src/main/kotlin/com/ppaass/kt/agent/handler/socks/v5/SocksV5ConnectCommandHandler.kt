package com.ppaass.kt.agent.handler.socks.v5

import com.ppaass.kt.agent.configuration.AgentConfiguration
import com.ppaass.kt.agent.handler.discardProxyHeartbeatHandler
import com.ppaass.kt.agent.handler.lengthFieldPrepender
import com.ppaass.kt.agent.handler.resourceClearHandler
import com.ppaass.kt.common.netty.codec.AgentMessageEncoder
import com.ppaass.kt.common.netty.codec.ProxyMessageDecoder
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.PooledByteBufAllocator
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.LengthFieldBasedFrameDecoder
import io.netty.handler.codec.compression.Lz4FrameDecoder
import io.netty.handler.codec.compression.Lz4FrameEncoder
import io.netty.handler.codec.socksx.v5.Socks5CommandRequest
import mu.KotlinLogging

@ChannelHandler.Sharable
internal class SocksV5ConnectCommandHandler(private val agentConfiguration: AgentConfiguration) :
        SimpleChannelInboundHandler<Socks5CommandRequest>() {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    private val proxyServerBootstrapIoEventLoopGroup = NioEventLoopGroup(agentConfiguration.staticAgentConfiguration.dataTransferIoEventThreadNumber)

    override fun channelRead0(agentChannelContext: ChannelHandlerContext, socks5CommandRequest: Socks5CommandRequest) {
        val proxyBootstrap = createProxyServerBootstrap(agentChannelContext, socks5CommandRequest)
        proxyBootstrap.connect(agentConfiguration.proxyAddress, agentConfiguration.proxyPort)
    }

    private fun createProxyServerBootstrap(agentChannelContext: ChannelHandlerContext, socks5CommandRequest: Socks5CommandRequest): Bootstrap {
        val proxyBootstrap = Bootstrap()
        proxyBootstrap.apply {
            group(proxyServerBootstrapIoEventLoopGroup)
            channel(NioSocketChannel::class.java)
            option(ChannelOption.CONNECT_TIMEOUT_MILLIS,
                    agentConfiguration.staticAgentConfiguration.proxyConnectionTimeout)
            option(ChannelOption.SO_KEEPALIVE, true)
            option(ChannelOption.AUTO_READ, true)
            option(ChannelOption.AUTO_CLOSE, true)
            option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
            option(ChannelOption.TCP_NODELAY, true)
            option(ChannelOption.SO_REUSEADDR, true)
            option(ChannelOption.SO_RCVBUF, agentConfiguration.staticAgentConfiguration.proxyServerSoRcvbuf)
            option(ChannelOption.SO_SNDBUF, agentConfiguration.staticAgentConfiguration.proxyServerSoSndbuf)
        }
        proxyBootstrap.handler(object : ChannelInitializer<Channel>() {
            override fun initChannel(proxyChannel: Channel) {
                with(proxyChannel.pipeline()) {
                    addLast(Lz4FrameDecoder())
                    addLast(LengthFieldBasedFrameDecoder(Int.MAX_VALUE,
                            0, 4, 0,
                            4))
                    addLast(ProxyMessageDecoder(
                            agentPrivateKeyString = agentConfiguration.staticAgentConfiguration.agentPrivateKey))
                    addLast(discardProxyHeartbeatHandler)
                    addLast(proxyServerBootstrapIoEventLoopGroup,
                            SocksV5ProxyToAgentHandler(
                                    agentChannel = agentChannelContext.channel(),
                                    agentConfiguration = agentConfiguration,
                                    socks5CommandRequest = socks5CommandRequest))
                    addLast(resourceClearHandler)
                    addLast(Lz4FrameEncoder())
                    addLast(lengthFieldPrepender)
                    addLast(AgentMessageEncoder(
                            proxyPublicKeyString = agentConfiguration.staticAgentConfiguration.proxyPublicKey))
                }
            }
        })
        return proxyBootstrap
    }
}
