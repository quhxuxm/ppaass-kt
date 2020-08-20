package com.ppaass.kt.proxy.handler

import com.ppaass.kt.common.protocol.*
import com.ppaass.kt.proxy.ProxyConfiguration
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.PooledByteBufAllocator
import io.netty.channel.*
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import mu.KotlinLogging
import java.util.*

@ChannelHandler.Sharable
internal class SetupTargetConnectionHandler(private val proxyConfiguration: ProxyConfiguration) :
        SimpleChannelInboundHandler<AgentMessage>() {
    private companion object {
        private val logger = KotlinLogging.logger {}
    }


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
        if (targetBootstrap == null) {
            logger.error("Return because of targetBootstrap is null, message id=${agentMessage.body.id}")
            proxyChannelContext.close()
            return
        }
        logger.debug("Begin to connect ${targetAddress}:${targetPort}, message id=${agentMessage.body.id}")
        targetBootstrap.connect(targetAddress, targetPort).addListener _1stListener@{ _1stFuture ->
            if (!_1stFuture.isSuccess) {
                logger.error("Fail connect to ${targetAddress}:${targetPort}.", _1stFuture.cause())
                val proxyMessageBody = ProxyMessageBody(ProxyMessageBodyType.CONNECT_FAIL, agentMessage.body.id)
                proxyMessageBody.targetAddress = agentMessage.body.targetAddress
                proxyMessageBody.targetPort = agentMessage.body.targetPort
                val failProxyMessage =
                        ProxyMessage(UUID.randomUUID().toString(), MessageBodyEncryptionType.random(), proxyMessageBody)
                proxyChannelContext.channel().writeAndFlush(failProxyMessage).addListener {
                    proxyChannelContext.close()
                }
            }
        }
    }

    private fun createTargetBootstrap(proxyChannelContext: ChannelHandlerContext, agentMessage: AgentMessage): Bootstrap? {
        val targetAddress = agentMessage.body.targetAddress
        val targetPort = agentMessage.body.targetPort
        if (targetAddress == null) {
            logger.error("Return because of targetAddress is null, message id=${agentMessage.body.id}")
            proxyChannelContext.close()
            return null
        }
        if (targetPort == null) {
            logger.error("Return because of targetPort is null, message id=${agentMessage.body.id}")
            proxyChannelContext.close()
            return null
        }
        val targetBootstrap = Bootstrap()
        targetBootstrap.apply {
            group(proxyChannelContext.channel().eventLoop())
            channel(NioSocketChannel::class.java)
            option(ChannelOption.CONNECT_TIMEOUT_MILLIS,
                    proxyConfiguration.targetConnectionTimeout)
            option(ChannelOption.SO_KEEPALIVE, true)
            option(ChannelOption.AUTO_CLOSE, true)
            option(ChannelOption.AUTO_READ, proxyConfiguration.autoRead)
            option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
            option(ChannelOption.TCP_NODELAY, true)
            option(ChannelOption.SO_REUSEADDR, true)
            option(ChannelOption.SO_RCVBUF, proxyConfiguration.targetSoRcvbuf)
            option(ChannelOption.SO_SNDBUF, proxyConfiguration.targetSoSndbuf)
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


    override fun channelReadComplete(proxyContext: ChannelHandlerContext) {
        proxyContext.flush()
    }
}
