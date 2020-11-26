package com.ppaass.agent.handler.socks

import com.ppaass.agent.AgentConfiguration
import com.ppaass.kt.common.AgentMessageEncoder
import com.ppaass.kt.common.PrintExceptionHandler
import com.ppaass.kt.common.ProxyMessageDecoder
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.PooledByteBufAllocator
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.EventLoopGroup
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioDatagramChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.LengthFieldBasedFrameDecoder
import io.netty.handler.codec.LengthFieldPrepender
import io.netty.handler.codec.compression.Lz4FrameDecoder
import io.netty.handler.codec.compression.Lz4FrameEncoder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

const val LOCAL_IP_ADDRESS = "127.0.0.1"

@Configuration
private class SocksConfigure(private val agentConfiguration: AgentConfiguration) {
    @Bean
    fun socksProxyUdpBootstrap(
        socksForwardUdpMessageToProxyTcpChannelHandler: SocksForwardUdpMessageToProxyTcpChannelHandler,
        printExceptionHandler: PrintExceptionHandler,
    ) =
        Bootstrap().apply {
            val socksProxyUdpLoopGroup = NioEventLoopGroup(
                agentConfiguration.agentUdpThreadNumber)
            group(socksProxyUdpLoopGroup)
                .channel(NioDatagramChannel::class.java)
                .option(ChannelOption.SO_BROADCAST, true)
                .handler(object : ChannelInitializer<NioDatagramChannel>() {
                    override fun initChannel(agentUdpChannel: NioDatagramChannel) {
                        val agentUdpChannelPipeline = agentUdpChannel.pipeline()
                        agentUdpChannelPipeline.apply {
                            addLast(SocksUdpMessageDecoder())
                            addLast(socksForwardUdpMessageToProxyTcpChannelHandler)
                            addLast(printExceptionHandler)
                        }
                    }
                })
        }

    @Bean
    fun socksProxyBootstrap(proxyTcpLoopGroup: EventLoopGroup,
                            socksProxyToAgentTcpChannelHandler: SocksProxyToAgentTcpChannelHandler,
                            printExceptionHandler: PrintExceptionHandler,
                            agentConfiguration: AgentConfiguration) = Bootstrap().apply {
        group(proxyTcpLoopGroup)
        channel(NioSocketChannel::class.java)
        option(ChannelOption.CONNECT_TIMEOUT_MILLIS,
            agentConfiguration.proxyTcpConnectionTimeout)
        option(ChannelOption.SO_KEEPALIVE, true)
        option(ChannelOption.AUTO_READ, true)
        option(ChannelOption.AUTO_CLOSE, false)
        option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
        option(ChannelOption.TCP_NODELAY, true)
        option(ChannelOption.SO_REUSEADDR, true)
        option(ChannelOption.SO_LINGER,
            agentConfiguration.proxyTcpSoLinger)
        option(ChannelOption.SO_RCVBUF,
            agentConfiguration.proxyTcpSoRcvbuf)
        option(ChannelOption.SO_SNDBUF,
            agentConfiguration.proxyTcpSoSndbuf)
        val channelInitializer = object : ChannelInitializer<SocketChannel>() {
            override fun initChannel(proxyChannel: SocketChannel) {
                val proxyChannelPipeline = proxyChannel.pipeline()
                proxyChannelPipeline.apply {
                    if (agentConfiguration.proxyTcpCompressEnable) {
                        addLast(Lz4FrameDecoder())
                    }
                    addLast(LengthFieldBasedFrameDecoder(Int.MAX_VALUE, 0, 4, 0, 4))
                    addLast(ProxyMessageDecoder(agentConfiguration.agentPrivateKey))
                    addLast(socksProxyToAgentTcpChannelHandler)
                    if (agentConfiguration.proxyTcpCompressEnable) {
                        addLast(Lz4FrameEncoder())
                    }
                    addLast(LengthFieldPrepender(4))
                    addLast(AgentMessageEncoder(agentConfiguration.proxyPublicKey))
                    addLast(printExceptionHandler)
                }
            }
        }
        handler(channelInitializer)
    }
}
