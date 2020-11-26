package com.ppaass.proxy

import com.ppaass.kt.common.AgentMessageDecoder
import com.ppaass.kt.common.PrintExceptionHandler
import com.ppaass.kt.common.ProxyMessageEncoder
import com.ppaass.proxy.handler.ProxyTcpChannelHeartbeatHandler
import com.ppaass.proxy.handler.ProxyTcpChannelToTargetHandler
import com.ppaass.proxy.handler.TargetTcpChannelHeartbeatHandler
import com.ppaass.proxy.handler.TargetTcpChannelToProxyHandler
import com.ppaass.proxy.handler.TargetUdpChannelTpProxyTcpChannelHandler
import io.netty.bootstrap.Bootstrap
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.AdaptiveRecvByteBufAllocator
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.EventLoopGroup
import io.netty.channel.WriteBufferWaterMark
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioDatagramChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.LengthFieldBasedFrameDecoder
import io.netty.handler.codec.LengthFieldPrepender
import io.netty.handler.codec.compression.Lz4FrameDecoder
import io.netty.handler.codec.compression.Lz4FrameEncoder
import io.netty.handler.timeout.IdleStateHandler
import io.netty.handler.traffic.ChannelTrafficShapingHandler
import org.apache.commons.io.FileUtils
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.Resource

@ConstructorBinding
@ConfigurationProperties("ppaass.proxy")
internal class ProxyConfiguration(
    val proxyTcpServerPort: Int,
    val proxyTcpMasterThreadNumber: Int,
    val proxyTcpWorkerThreadNumber: Int,
    val proxyUdpWorkerThreadNumber: Int,
    val proxyTcpSoLinger: Int,
    val proxyTcpSoRcvbuf: Int,
    val proxyTcpSoSndbuf: Int,
    val proxyTcpSoBacklog: Int,
    val proxyTcpReceiveDataAverageBufferMinSize: Int,
    val proxyTcpReceiveDataAverageBufferInitialSize: Int,
    val proxyTcpReceiveDataAverageBufferMaxSize: Int,
    val proxyTcpTrafficShapingWriteChannelLimit: Long,
    val proxyTcpTrafficShapingReadChannelLimit: Long,
    val proxyTcpTrafficShapingCheckInterval: Long,
    val proxyTcpWriteSpinCount: Int,
    val proxyTcpChannelReadIdleSeconds: Int,
    val proxyTcpChannelWriteIdleSeconds: Int,
    val proxyTcpChannelAllIdleSeconds: Int,
    val proxyTcpCompressEnable: Boolean,
    val proxyTcpChannelHeartbeatRetry: Int = 0,
    val proxyTcpChannelToTargetTcpChannelRetry: Int = 0,
    val targetToProxyTcpChannelRetry: Int = 0,
    val targetTcpThreadNumber: Int,
    val targetUdpThreadNumber: Int,
    val targetTcpConnectionTimeout: Int,
    val targetTcpSoLinger: Int,
    val targetTcpSoRcvbuf: Int,
    val targetTcpSoSndbuf: Int,
    val targetTcpWriteSpinCount: Int,
    val targetTcpWriteBufferWaterMarkLow: Int,
    val targetTcpWriteBufferWaterMarkHigh: Int,
    val targetTcpReceiveDataAverageBufferMinSize: Int,
    val targetTcpReceiveDataAverageBufferInitialSize: Int,
    val targetTcpReceiveDataAverageBufferMaxSize: Int,
    val targetTcpTrafficShapingWriteChannelLimit: Long,
    val targetTcpTrafficShapingReadChannelLimit: Long,
    val targetTcpTrafficShapingCheckInterval: Long,
    val targetTcpChannelReadIdleSeconds: Int,
    val targetTcpChannelWriteIdleSeconds: Int,
    val targetTcpChannelAllIdleSeconds: Int,
    proxyPrivateKeyFile: Resource,
    agentPublicKeyFile: Resource,
) {
    var proxyPrivateKey = FileUtils.readFileToString(proxyPrivateKeyFile.file, Charsets.UTF_8)
    var agentPublicKey = FileUtils.readFileToString(agentPublicKeyFile.file, Charsets.UTF_8)
}

internal val LAST_INBOUND_HANDLER = "LAST_INBOUND_HANDLER"

@Configuration
private class Configure(private val proxyConfiguration: ProxyConfiguration) {
    @Bean
    fun proxyTcpMasterLoopGroup() = NioEventLoopGroup(
        this.proxyConfiguration.proxyTcpMasterThreadNumber)

    @Bean
    fun proxyTcpWorkerLoopGroup() = NioEventLoopGroup(
        this.proxyConfiguration.proxyTcpWorkerThreadNumber)

    @Bean
    fun proxyUdpWorkerLoopGroup() = NioEventLoopGroup(
        this.proxyConfiguration.proxyUdpWorkerThreadNumber)

    @Bean
    fun targetUdpLoopGroup() = NioEventLoopGroup(
        this.proxyConfiguration.targetUdpThreadNumber)

    @Bean
    fun targetTcpLoopGroup() = NioEventLoopGroup(
        this.proxyConfiguration.targetTcpThreadNumber)

    @Bean
    fun printExceptionHandler() = PrintExceptionHandler()

    @Bean
    fun targetTcpBootstrap(
        targetTcpLoopGroup: EventLoopGroup,
        targetTcpChannelToProxyHandler: TargetTcpChannelToProxyHandler,
        targetTcpChannelHeartbeatHandler: TargetTcpChannelHeartbeatHandler,
        printExceptionHandler: PrintExceptionHandler) = Bootstrap().apply {
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
        val channelInitializer = object : ChannelInitializer<SocketChannel>() {
            override fun initChannel(targetChannel: SocketChannel) {
                targetChannel.pipeline().apply {
                    addLast(ChannelTrafficShapingHandler(
                        proxyConfiguration.targetTcpTrafficShapingWriteChannelLimit,
                        proxyConfiguration.targetTcpTrafficShapingReadChannelLimit,
                        proxyConfiguration.targetTcpTrafficShapingCheckInterval
                    ))
                    addLast(targetTcpChannelToProxyHandler)
                    addLast(
                        IdleStateHandler(proxyConfiguration.targetTcpChannelReadIdleSeconds,
                            proxyConfiguration.targetTcpChannelWriteIdleSeconds,
                            proxyConfiguration.targetTcpChannelAllIdleSeconds))
                    addLast(targetTcpChannelHeartbeatHandler)
                    addLast(LAST_INBOUND_HANDLER, printExceptionHandler)
                }
            }
        }
        handler(channelInitializer)
    }

    @Bean
    fun targetUdpBootstrap(targetUdpLoopGroup: EventLoopGroup,
                           targetUdpChannelTpProxyTcpChannelHandler: TargetUdpChannelTpProxyTcpChannelHandler,
                           printExceptionHandler: PrintExceptionHandler) =
        Bootstrap().apply {
            group(targetUdpLoopGroup)
            channel(NioDatagramChannel::class.java)
            option(ChannelOption.SO_BROADCAST, true)
            val channelInitializer = object : ChannelInitializer<NioDatagramChannel>() {
                override fun initChannel(proxyUdpChannel: NioDatagramChannel) {
                    val proxyUdpChannelPipeline = proxyUdpChannel.pipeline()
                    proxyUdpChannelPipeline.addLast(targetUdpChannelTpProxyTcpChannelHandler)
                    proxyUdpChannelPipeline.addLast(LAST_INBOUND_HANDLER, printExceptionHandler)
                }
            }
            handler(channelInitializer)
        }

    @Bean
    fun proxyTcpServerBootstrap(proxyTcpMasterLoopGroup: EventLoopGroup,
                                proxyTcpWorkerLoopGroup: EventLoopGroup,
                                proxyTcpChannelHeartbeatHandler: ProxyTcpChannelHeartbeatHandler,
                                proxyTcpChannelToTargetHandler: ProxyTcpChannelToTargetHandler,
                                printExceptionHandler: PrintExceptionHandler) =
        ServerBootstrap().apply {
            group(proxyTcpMasterLoopGroup, proxyTcpWorkerLoopGroup)
            channel(NioServerSocketChannel::class.java)
            option(ChannelOption.SO_BACKLOG, proxyConfiguration.proxyTcpSoBacklog)
            option(ChannelOption.TCP_NODELAY, true)
            childOption(ChannelOption.TCP_NODELAY, true)
            childOption(ChannelOption.SO_REUSEADDR, true)
            childOption(ChannelOption.TCP_NODELAY, true)
            childOption(ChannelOption.AUTO_CLOSE, false)
            childOption(ChannelOption.AUTO_READ, false)
            childOption(ChannelOption.SO_KEEPALIVE, true)
            childOption(ChannelOption.SO_LINGER,
                proxyConfiguration.proxyTcpSoLinger)
            childOption(ChannelOption.SO_RCVBUF, proxyConfiguration.proxyTcpSoRcvbuf)
            childOption(ChannelOption.SO_SNDBUF, proxyConfiguration.proxyTcpSoSndbuf)
            childOption(ChannelOption.WRITE_SPIN_COUNT,
                proxyConfiguration.proxyTcpWriteSpinCount)
            childOption(ChannelOption.RCVBUF_ALLOCATOR,
                AdaptiveRecvByteBufAllocator(
                    proxyConfiguration.proxyTcpReceiveDataAverageBufferMinSize,
                    proxyConfiguration.proxyTcpReceiveDataAverageBufferInitialSize,
                    proxyConfiguration.proxyTcpReceiveDataAverageBufferMaxSize))
            val channelInitializer = object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(proxyChannel: SocketChannel) {
                    proxyChannel.pipeline().apply {
                        //Inbound
                        addLast(
                            IdleStateHandler(proxyConfiguration.proxyTcpChannelReadIdleSeconds,
                                proxyConfiguration.proxyTcpChannelWriteIdleSeconds,
                                proxyConfiguration.proxyTcpChannelAllIdleSeconds))
                        addLast(proxyTcpChannelHeartbeatHandler)
                        addLast(ChannelTrafficShapingHandler(
                            proxyConfiguration.proxyTcpTrafficShapingWriteChannelLimit,
                            proxyConfiguration.proxyTcpTrafficShapingReadChannelLimit,
                            proxyConfiguration.proxyTcpTrafficShapingCheckInterval
                        ))
                        if (proxyConfiguration.proxyTcpCompressEnable) {
                            addLast(Lz4FrameDecoder())
                        }
                        addLast(
                            LengthFieldBasedFrameDecoder(Int.MAX_VALUE, 0, 4, 0, 4))
                        addLast(
                            AgentMessageDecoder(proxyConfiguration.proxyPrivateKey))
                        addLast(proxyTcpChannelToTargetHandler)
                        if (proxyConfiguration.proxyTcpCompressEnable) {
                            addLast(Lz4FrameEncoder())
                        }
                        //Outbound
                        addLast(LengthFieldPrepender(4))
                        addLast(
                            ProxyMessageEncoder(proxyConfiguration.agentPublicKey))
                        addLast(LAST_INBOUND_HANDLER, printExceptionHandler)
                    }
                }
            }
            childHandler(channelInitializer)
        }
}
