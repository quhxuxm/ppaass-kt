package com.ppaass.kt.agent.handler.http

import com.ppaass.kt.agent.AgentConfiguration
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.PooledByteBufAllocator
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.util.concurrent.EventExecutorGroup

@ChannelHandler.Sharable
class HttpConnectionHandler(private val agentConfiguration: AgentConfiguration) : ChannelInboundHandlerAdapter() {
    companion object {
        private val channelMap = HashMap<String, SocketChannel>()
    }

    private val businessEventExecutorGroup: EventExecutorGroup
    private val httpProxyBootstrap: Bootstrap

    init {
        this.businessEventExecutorGroup =
                DefaultEventLoopGroup(this.agentConfiguration.staticAgentConfiguration.businessEventThreadNumber)
        this.httpProxyBootstrap = Bootstrap()
        with(this.httpProxyBootstrap) {
            group(NioEventLoopGroup(
                    agentConfiguration.staticAgentConfiguration.proxyDataTransferIoEventThreadNumber))
            channel(NioSocketChannel::class.java)
            option(ChannelOption.CONNECT_TIMEOUT_MILLIS,
                    agentConfiguration.staticAgentConfiguration.proxyConnectionTimeout)
            option(ChannelOption.SO_KEEPALIVE, true)
            option(ChannelOption.AUTO_READ, true)
            option(ChannelOption.AUTO_CLOSE, true)
            option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
            option(ChannelOption.TCP_NODELAY, true)
            option(ChannelOption.RCVBUF_ALLOCATOR,
                    AdaptiveRecvByteBufAllocator(
                            agentConfiguration.staticAgentConfiguration
                                    .proxyServerReceiveDataAverageBufferMinSize,
                            agentConfiguration.staticAgentConfiguration
                                    .proxyServerReceiveDataAverageBufferInitialSize,
                            agentConfiguration.staticAgentConfiguration
                                    .proxyServerReceiveDataAverageBufferMaxSize))
            option(ChannelOption.SO_RCVBUF,
                    agentConfiguration.staticAgentConfiguration.proxyServerSoRcvbuf)
        }
    }

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        super.channelRead(ctx, msg)
    }
}