package com.ppaass.kt.proxy

import io.netty.channel.nio.NioEventLoopGroup
import io.netty.handler.traffic.GlobalChannelTrafficShapingHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.Executors

@Configuration
internal class ProxyConfigure(private val proxyConfiguration: ProxyConfiguration) {
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
    fun globalChannelTrafficShapingHandler() =
        GlobalChannelTrafficShapingHandler(
            Executors.newSingleThreadScheduledExecutor(),
            proxyConfiguration.writeGlobalLimit,
            proxyConfiguration.readGlobalLimit,
            proxyConfiguration.writeChannelLimit,
            proxyConfiguration.readChannelLimit,
            proxyConfiguration.trafficShapingCheckInterval
        )

    @Bean
    fun targetGlobalChannelTrafficShapingHandler() =
        GlobalChannelTrafficShapingHandler(
            Executors.newSingleThreadScheduledExecutor(),
            proxyConfiguration.targetWriteGlobalLimit,
            proxyConfiguration.targetReadGlobalLimit,
            proxyConfiguration.targetWriteChannelLimit,
            proxyConfiguration.targetReadChannelLimit,
            proxyConfiguration.targetTrafficShapingCheckInterval
        )
}
