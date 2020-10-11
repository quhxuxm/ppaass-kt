package com.ppaass.kt.proxy.handler

import com.ppaass.kt.common.exception.PpaassException
import com.ppaass.kt.common.protocol.AgentMessage
import com.ppaass.kt.common.protocol.MessageBodyEncryptionType
import com.ppaass.kt.common.protocol.ProxyMessage
import com.ppaass.kt.common.protocol.ProxyMessageBody
import com.ppaass.kt.common.protocol.ProxyMessageBodyType
import com.ppaass.kt.common.protocol.generateUid
import com.ppaass.kt.proxy.ProxyConfiguration
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.PooledByteBufAllocator
import io.netty.channel.AdaptiveRecvByteBufAllocator
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import mu.KotlinLogging

@ChannelHandler.Sharable
internal class SetupTargetConnectionHandler(private val proxyConfiguration: ProxyConfiguration) :
        SimpleChannelInboundHandler<AgentMessage>() {
    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    private val targetBootstrapIoEventLoopGroup = NioEventLoopGroup(proxyConfiguration.dataTransferIoEventThreadNumber)

    override fun channelRead0(proxyChannelContext: ChannelHandlerContext, agentMessage: AgentMessage) {
        val targetAddress = agentMessage.body.targetAddress
        val targetPort = agentMessage.body.targetPort
        if (targetAddress == null) {
            logger.error("Return because of targetAddress is null, message id=${agentMessage.body.id}")
            throw PpaassException("Return because of targetAddress is null, message id=${agentMessage.body.id}")
        }
        if (targetPort == null) {
            logger.error("Return because of targetPort is null, message id=${agentMessage.body.id}")
            throw PpaassException("Return because of targetPort is null, message id=${agentMessage.body.id}")
        }
        val targetBootstrap = createTargetBootstrap(proxyChannelContext, targetAddress, targetPort, agentMessage)
        logger.debug("Begin to connect ${targetAddress}:${targetPort}, message id=${agentMessage.body.id}")
        targetBootstrap.connect(targetAddress, targetPort).addListener {
            if (!it.isSuccess) {
                logger.error("Fail connect to ${targetAddress}:${targetPort}.", it.cause())
                val proxyMessageBody = ProxyMessageBody(ProxyMessageBodyType.CONNECT_FAIL, agentMessage.body.id)
                proxyMessageBody.targetAddress = agentMessage.body.targetAddress
                proxyMessageBody.targetPort = agentMessage.body.targetPort
                val failProxyMessage =
                        ProxyMessage(generateUid(), MessageBodyEncryptionType.random(), proxyMessageBody)
                proxyChannelContext.channel().writeAndFlush(failProxyMessage).addListener(ChannelFutureListener.CLOSE)
                throw PpaassException("Fail connect to ${targetAddress}:${targetPort}.", it.cause())
            }
        }
    }

    private fun createTargetBootstrap(proxyChannelHandlerContext: ChannelHandlerContext, targetAddress: String, targetPort: Int, agentMessage: AgentMessage): Bootstrap {
        val targetBootstrap = Bootstrap()
        targetBootstrap.apply {
            group(targetBootstrapIoEventLoopGroup)
            channel(NioSocketChannel::class.java)
            option(ChannelOption.CONNECT_TIMEOUT_MILLIS,
                    proxyConfiguration.targetConnectionTimeout)
            option(ChannelOption.SO_KEEPALIVE, proxyConfiguration.targetConnectionKeepAlive)
            option(ChannelOption.AUTO_CLOSE, true)
            option(ChannelOption.AUTO_READ, proxyConfiguration.targetAutoRead)
            option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
            option(ChannelOption.TCP_NODELAY, true)
            option(ChannelOption.SO_REUSEADDR, true)
            option(ChannelOption.SO_RCVBUF, proxyConfiguration.targetSoRcvbuf)
            option(ChannelOption.SO_SNDBUF, proxyConfiguration.targetSoSndbuf)
            option(ChannelOption.WRITE_SPIN_COUNT, proxyConfiguration.targetWriteSpinCount)
            option(ChannelOption.RCVBUF_ALLOCATOR, AdaptiveRecvByteBufAllocator(proxyConfiguration.targetReceiveDataAverageBufferMinSize, proxyConfiguration
                    .targetReceiveDataAverageBufferInitialSize, proxyConfiguration.targetReceiveDataAverageBufferMaxSize))
            handler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(targetChannel: SocketChannel) {
                    with(targetChannel.pipeline()) {
                        logger.debug { "Initializing channel for $targetAddress:$targetPort" }
                        addLast(
                                TargetToProxyHandler(
                                        proxyChannelHandlerContext = proxyChannelHandlerContext,
                                        proxyConfiguration = proxyConfiguration,
                                        agentMessage = agentMessage
                                ))
                        addLast(resourceClearHandler)
                    }
                }
            })
        }
        return targetBootstrap
    }
}
