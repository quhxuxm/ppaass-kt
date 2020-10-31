package com.ppaass.kt.proxy

import com.ppaass.kt.proxy.handler.TargetChannelInitializer
import io.netty.bootstrap.Bootstrap
import io.netty.channel.AdaptiveRecvByteBufAllocator
import io.netty.channel.ChannelOption
import io.netty.channel.EventLoopGroup
import io.netty.channel.WriteBufferWaterMark
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioSocketChannel
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
internal class ProxyConfigure(
    private val proxyConfiguration: ProxyConfiguration) {
    @Bean
    fun targetBootstrapIoEventLoopGroup() =
        NioEventLoopGroup(this.proxyConfiguration.targetIoEventThreadNumber)

    @Bean
    fun dataTransferIoEventLoopGroup() =
        NioEventLoopGroup(this.proxyConfiguration.dataTransferIoEventThreadNumber)

    @Bean
    fun masterIoEventLoopGroup() =
        NioEventLoopGroup(this.proxyConfiguration.masterIoEventThreadNumber)

    @Bean
    fun workerIoEventLoopGroup() =
        NioEventLoopGroup(this.proxyConfiguration.workerIoEventThreadNumber)

    @Bean
    fun targetBootstrap(
        targetBootstrapIoEventLoopGroup: EventLoopGroup,
        targetChannelInitializer: TargetChannelInitializer
    ): Bootstrap {
        val targetBootstrap = Bootstrap()
        targetBootstrap.apply {
            group(targetBootstrapIoEventLoopGroup)
            channel(NioSocketChannel::class.java)
            option(ChannelOption.CONNECT_TIMEOUT_MILLIS,
                proxyConfiguration.targetConnectionTimeout)
            option(ChannelOption.SO_KEEPALIVE, proxyConfiguration.targetConnectionKeepAlive)
            option(ChannelOption.TCP_NODELAY, true)
            option(ChannelOption.SO_REUSEADDR, true)
            option(ChannelOption.AUTO_READ, false)
            option(ChannelOption.SO_RCVBUF, proxyConfiguration.targetSoRcvbuf)
            option(ChannelOption.SO_SNDBUF, proxyConfiguration.targetSoSndbuf)
            option(ChannelOption.WRITE_SPIN_COUNT, proxyConfiguration.targetWriteSpinCount)
            option(ChannelOption.WRITE_BUFFER_WATER_MARK,
                WriteBufferWaterMark(proxyConfiguration.targetWriteBufferWaterMarkLow,
                    proxyConfiguration.targetWriteBufferWaterMarkHigh))
            option(ChannelOption.RCVBUF_ALLOCATOR,
                AdaptiveRecvByteBufAllocator(
                    proxyConfiguration.targetReceiveDataAverageBufferMinSize,
                    proxyConfiguration
                        .targetReceiveDataAverageBufferInitialSize,
                    proxyConfiguration.targetReceiveDataAverageBufferMaxSize))
            handler(targetChannelInitializer)
        }
        return targetBootstrap
    }
}
