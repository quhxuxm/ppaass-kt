package com.ppaass.kt.agent.handler.http

import com.ppaass.kt.agent.configuration.AgentConfiguration
import com.ppaass.kt.common.netty.codec.AgentMessageEncoder
import com.ppaass.kt.common.netty.codec.ProxyMessageDecoder
import com.ppaass.kt.common.netty.handler.ExceptionHandler
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.PooledByteBufAllocator
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.EventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.LengthFieldBasedFrameDecoder
import io.netty.handler.codec.LengthFieldPrepender
import io.netty.handler.codec.compression.Lz4FrameDecoder
import io.netty.handler.codec.compression.Lz4FrameEncoder
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpResponseDecoder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
internal class HttpProxyConfigure {
    @Bean
    fun proxyBootstrapForHttp(proxyBootstrapIoEventLoopGroup: EventLoopGroup,
                              dataTransferIoEventLoopGroup: EventLoopGroup,
                              transferDataFromProxyToAgentHandler: TransferDataFromProxyToAgentHandler,
                              exceptionHandler: ExceptionHandler,
                              agentConfiguration: AgentConfiguration,
                              httpProxyMessageBodyTypeHandler: HttpProxyMessageBodyTypeHandler): Bootstrap {
        val proxyBootstrap = Bootstrap()
        proxyBootstrap.apply {
            group(proxyBootstrapIoEventLoopGroup)
            channel(NioSocketChannel::class.java)
            option(ChannelOption.CONNECT_TIMEOUT_MILLIS,
                agentConfiguration.staticAgentConfiguration.proxyConnectionTimeout)
            option(ChannelOption.SO_KEEPALIVE, true)
            option(ChannelOption.SO_REUSEADDR, true)
            option(ChannelOption.AUTO_READ, true)
            option(ChannelOption.AUTO_CLOSE, true)
            option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
            option(ChannelOption.TCP_NODELAY, true)
            option(ChannelOption.SO_RCVBUF,
                agentConfiguration.staticAgentConfiguration.proxyServerSoRcvbuf)
            option(ChannelOption.SO_SNDBUF,
                agentConfiguration.staticAgentConfiguration.proxyServerSoSndbuf)
            handler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(httpProxyChannel: SocketChannel) {
                    httpProxyChannel.pipeline().apply {
                        if (agentConfiguration.staticAgentConfiguration.compressingEnable) {
                            addLast(Lz4FrameDecoder())
                        }
                        addLast(LengthFieldBasedFrameDecoder(Integer.MAX_VALUE,
                            0, 4, 0,
                            4))
                        addLast(ProxyMessageDecoder(
                            agentConfiguration.staticAgentConfiguration.agentPrivateKey))
                        addLast(httpProxyMessageBodyTypeHandler)
                        addLast(ExtractProxyMessageOriginalDataDecoder())
                        addLast(HttpResponseDecoder())
                        addLast(HttpObjectAggregator(Int.MAX_VALUE, true))
                        addLast(dataTransferIoEventLoopGroup, transferDataFromProxyToAgentHandler)
                        if (agentConfiguration.staticAgentConfiguration.compressingEnable) {
                            addLast(Lz4FrameEncoder())
                        }
                        addLast(LengthFieldPrepender(4))
                        addLast(AgentMessageEncoder(
                            agentConfiguration.staticAgentConfiguration.proxyPublicKey))
                        addLast(exceptionHandler)
                    }
                }
            })
        }
        return proxyBootstrap
    }

    @Bean
    fun proxyBootstrapForHttps(proxyBootstrapIoEventLoopGroup: EventLoopGroup,
                               dataTransferIoEventLoopGroup: EventLoopGroup,
                               transferDataFromProxyToAgentHandler: TransferDataFromProxyToAgentHandler,
                               exceptionHandler: ExceptionHandler,
                               agentConfiguration: AgentConfiguration,
                               httpProxyMessageBodyTypeHandler: HttpProxyMessageBodyTypeHandler): Bootstrap {
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
            option(ChannelOption.SO_REUSEADDR, true)
            option(ChannelOption.TCP_NODELAY, true)
            option(ChannelOption.SO_RCVBUF,
                agentConfiguration.staticAgentConfiguration.proxyServerSoRcvbuf)
            option(ChannelOption.SO_SNDBUF,
                agentConfiguration.staticAgentConfiguration.proxyServerSoSndbuf)

            handler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(httpsProxyChannel: SocketChannel) {
                    with(httpsProxyChannel.pipeline()) {
                        if (agentConfiguration.staticAgentConfiguration.compressingEnable) {
                            addLast(Lz4FrameDecoder())
                        }

                        addLast(LengthFieldBasedFrameDecoder(Integer.MAX_VALUE,
                            0, 4, 0,
                            4))
                        addLast(ProxyMessageDecoder(
                            agentConfiguration.staticAgentConfiguration.agentPrivateKey))
                        addLast(httpProxyMessageBodyTypeHandler)
                        addLast(ExtractProxyMessageOriginalDataDecoder())
                        addLast(dataTransferIoEventLoopGroup, transferDataFromProxyToAgentHandler)
                        if (agentConfiguration.staticAgentConfiguration.compressingEnable) {
                            addLast(Lz4FrameEncoder())
                        }
                        addLast(LengthFieldPrepender(4))
                        addLast(AgentMessageEncoder(
                            agentConfiguration.staticAgentConfiguration.proxyPublicKey))
                        addLast(exceptionHandler)
                    }
                }
            })
        }
        return proxyBootstrap
    }
}
