package com.ppaass.proxy

import io.netty.bootstrap.Bootstrap
import io.netty.channel.AdaptiveRecvByteBufAllocator
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.EventLoopGroup
import io.netty.channel.WriteBufferWaterMark
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.timeout.IdleStateHandler
import io.netty.handler.traffic.ChannelTrafficShapingHandler
import mu.KotlinLogging
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import kotlin.concurrent.thread

private val logger = KotlinLogging.logger { }

@SpringBootApplication
@EnableConfigurationProperties(ProxyConfiguration::class)
class Launcher(private val proxyConfiguration: ProxyConfiguration) {
    @Bean
    fun proxyTcpMasterLoopGroup(): EventLoopGroup {
        return NioEventLoopGroup(
            this.proxyConfiguration.proxyTcpMasterThreadNumber)
    }

    @Bean
    fun proxyTcpWorkerLoopGroup(): EventLoopGroup {
        return NioEventLoopGroup(
            this.proxyConfiguration.proxyTcpWorkerThreadNumber)
    }

    @Bean
    fun proxyUdpWorkerLoopGroup(): EventLoopGroup {
        return NioEventLoopGroup(
            this.proxyConfiguration.proxyUdpWorkerThreadNumber)
    }

    @Bean
    fun targetUdpLoopGroup(): EventLoopGroup {
        return NioEventLoopGroup(
            this.proxyConfiguration.targetUdpThreadNumber)
    }

    @Bean
    fun targetTcpLoopGroup(): EventLoopGroup {
        return NioEventLoopGroup(
            this.proxyConfiguration.targetTcpThreadNumber)
    }

    @Bean
    fun targetTcpBootstrap(
        targetTcpLoopGroup: EventLoopGroup): Bootstrap {
        val targetBootstrap = Bootstrap()
        targetBootstrap.apply {
            group(targetTcpLoopGroup)
            channel(NioSocketChannel::class.java)
            option(ChannelOption.CONNECT_TIMEOUT_MILLIS,
                proxyConfiguration.targetTcpConnectionTimeout)
            option(ChannelOption.SO_KEEPALIVE, true)
            option(ChannelOption.TCP_NODELAY, true)
            option(ChannelOption.SO_REUSEADDR, true)
            option(ChannelOption.AUTO_READ, false)
            option(ChannelOption.AUTO_CLOSE, false)
            option(ChannelOption.SO_LINGER, proxyConfiguration.targetTcpSoLinger)
            option(ChannelOption.SO_RCVBUF, proxyConfiguration.targetTcpSoRcvbuf)
            option(ChannelOption.SO_SNDBUF, proxyConfiguration.targetTcpSoSndbuf)
            option(ChannelOption.WRITE_SPIN_COUNT,
                proxyConfiguration.targetTcpWriteSpinCount)
            option(ChannelOption.WRITE_BUFFER_WATER_MARK,
                WriteBufferWaterMark(proxyConfiguration.targetTcpWriteBufferWaterMarkLow,
                    proxyConfiguration.targetTcpWriteBufferWaterMarkHigh))
            option(ChannelOption.RCVBUF_ALLOCATOR,
                AdaptiveRecvByteBufAllocator(
                    proxyConfiguration.targetTcpReceiveDataAverageBufferMinSize,
                    proxyConfiguration
                        .targetTcpReceiveDataAverageBufferInitialSize,
                    proxyConfiguration.targetTcpReceiveDataAverageBufferMaxSize))
            handler(object : ChannelInitializer<SocketChannel>() {
                @Throws(Exception::class)
                override fun initChannel(targetChannel: SocketChannel) {
                    val targetChannelPipeline = targetChannel.pipeline()
                    targetChannelPipeline.addLast(ChannelTrafficShapingHandler(
                        proxyConfiguration.getTargetTrafficShapingWriteChannelLimit(),
                        proxyConfiguration.getTargetTrafficShapingReadChannelLimit(),
                        proxyConfiguration.getTargetTrafficShapingCheckInterval()
                    ))
                    targetChannelPipeline.addLast(targetToProxyHandler)
                    targetChannelPipeline.addLast(IdleStateHandler(15, 15, 30))
                    targetChannelPipeline.addLast(connectionKeepAliveHandler)
                }
            })
        }
        return targetBootstrap
    }
}

fun main(args: Array<String>) {
    val applicationContext = SpringApplication.run(Launcher::class.java)
    val proxy = applicationContext.getBean(Proxy::class.java)
    Runtime.getRuntime().addShutdownHook(thread(start = false) {
        logger.info { "Begin to stop proxy..." }
        proxy.stop()
        logger.info { "Proxy stopped..." }
    })
    logger.info { "Begin to start proxy..." }
    proxy.start();
    logger.info { "Proxy started..." }
}
