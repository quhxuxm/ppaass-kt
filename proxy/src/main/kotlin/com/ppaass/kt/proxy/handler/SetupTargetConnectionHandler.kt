package com.ppaass.kt.proxy.handler

import com.ppaass.kt.common.exception.PpaassException
import com.ppaass.kt.common.protocol.*
import com.ppaass.kt.proxy.ProxyConfiguration
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.PooledByteBufAllocator
import io.netty.channel.*
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
            proxyChannelContext.close()
            return
        }
        if (targetPort == null) {
            logger.error("Return because of targetPort is null, message id=${agentMessage.body.id}")
            proxyChannelContext.close()
            return
        }
        val targetBootstrap = createTargetBootstrap(proxyChannelContext, agentMessage)
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
            }
        }
    }

    private fun createTargetBootstrap(proxyChannelContext: ChannelHandlerContext, agentMessage: AgentMessage): Bootstrap {
        val targetAddress = agentMessage.body.targetAddress
        val targetPort = agentMessage.body.targetPort
        if (targetAddress == null) {
            logger.error("Return because of targetAddress is null, message id=${agentMessage.body.id}")
            proxyChannelContext.close()
            throw PpaassException("Return because of targetAddress is null, message id=${agentMessage.body.id}")
        }
        if (targetPort == null) {
            logger.error("Return because of targetPort is null, message id=${agentMessage.body.id}")
            proxyChannelContext.close()
            throw PpaassException("Return because of targetPort is null, message id=${agentMessage.body.id}")
        }
        val targetBootstrap = Bootstrap()
        targetBootstrap.apply {
            group(targetBootstrapIoEventLoopGroup)
            channel(NioSocketChannel::class.java)
            option(ChannelOption.CONNECT_TIMEOUT_MILLIS,
                    proxyConfiguration.targetConnectionTimeout)
            option(ChannelOption.SO_KEEPALIVE, true)
            option(ChannelOption.AUTO_CLOSE, true)
            option(ChannelOption.AUTO_READ, false)
            option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
            option(ChannelOption.TCP_NODELAY, true)
            option(ChannelOption.SO_REUSEADDR, true)
            option(ChannelOption.SO_RCVBUF, proxyConfiguration.targetSoRcvbuf)
            option(ChannelOption.SO_SNDBUF, proxyConfiguration.targetSoSndbuf)
            option(ChannelOption.RCVBUF_ALLOCATOR, AdaptiveRecvByteBufAllocator(proxyConfiguration.targetReceiveDataAverageBufferMinSize, proxyConfiguration
                    .targetReceiveDataAverageBufferInitialSize, proxyConfiguration.targetReceiveDataAverageBufferMaxSize))
            handler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(targetChannel: SocketChannel) {
                    with(targetChannel.pipeline()) {
                        logger.debug { "Initializing channel for $targetAddress:$targetPort" }
                        addLast(
                                TargetToProxyHandler(
                                        proxyChannelContext = proxyChannelContext,
                                        agentMessage = agentMessage,
                                        proxyConfiguration = proxyConfiguration
                                ))
                        addLast(resourceClearHandler)
                    }
                }
            })
        }
        return targetBootstrap
    }
}
