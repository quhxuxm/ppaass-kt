package com.ppaass.kt.agent.handler.socks.v5

import com.ppaass.kt.agent.configuration.AgentConfiguration
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.PooledByteBufAllocator
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOption
import io.netty.channel.EventLoopGroup
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.socksx.v5.Socks5CommandRequest
import mu.KotlinLogging
import org.springframework.stereotype.Service

@ChannelHandler.Sharable
@Service
internal class SocksV5ConnectCommandHandler(
    private val agentConfiguration: AgentConfiguration,
    private val proxyBootstrapIoEventLoopGroup: EventLoopGroup,
    private val socksV5ProxyChannelInitializer: SocksV5ProxyChannelInitializer) :
    SimpleChannelInboundHandler<Socks5CommandRequest>() {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    override fun channelRead0(agentChannelContext: ChannelHandlerContext, socks5CommandRequest: Socks5CommandRequest) {
        logger.debug {
            "Create new connection to proxy server, agentChannelId=${
                agentChannelContext.channel().id().asLongText()
            }"
        }
        val proxyBootstrap = createProxyServerBootstrap(agentChannelContext, socks5CommandRequest)
        proxyBootstrap.connect(agentConfiguration.proxyAddress, agentConfiguration.proxyPort)
    }

    private fun createProxyServerBootstrap(agentChannelContext: ChannelHandlerContext,
                                           socks5CommandRequest: Socks5CommandRequest): Bootstrap {
        val proxyBootstrap = Bootstrap()
        proxyBootstrap.apply {
            group(proxyBootstrapIoEventLoopGroup)
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
            attr(AGENT_CHANNEL_CONTEXT, agentChannelContext)
            attr(SOCKS_V5_COMMAND_REQUEST, socks5CommandRequest)
        }
        proxyBootstrap.handler(socksV5ProxyChannelInitializer)
        return proxyBootstrap
    }
}
