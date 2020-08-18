package com.ppaass.kt.proxy.handler

import com.ppaass.kt.common.netty.handler.ResourceClearHandler
import com.ppaass.kt.common.protocol.*
import com.ppaass.kt.proxy.ProxyConfiguration
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.PooledByteBufAllocator
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.util.concurrent.EventExecutorGroup
import mu.KotlinLogging
import java.util.*

@ChannelHandler.Sharable
internal class SetupTargetConnectionHandler(private val proxyConfiguration: ProxyConfiguration) :
        SimpleChannelInboundHandler<AgentMessage>() {
    private companion object {
        private val logger = KotlinLogging.logger {}
        private val resourceClearHandler = ResourceClearHandler()
    }

    private val dataTransferExecutorGroup: EventExecutorGroup
    private val targetBootstrap: Bootstrap

    init {
        this.dataTransferExecutorGroup =
                DefaultEventLoopGroup(proxyConfiguration.dataTransferHandlerExecutorGroupThreadNumber)
        this.targetBootstrap = Bootstrap()
        this.targetBootstrap.apply {
            group(NioEventLoopGroup(proxyConfiguration.targetDataTransferIoEventThreadNumber))
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
        }
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
        this.targetBootstrap.handler(object : ChannelInitializer<SocketChannel>() {
            override fun initChannel(targetChannel: SocketChannel) {
                with(targetChannel.pipeline()) {
                    logger.debug { "Initializing channel for $targetAddress:$targetPort" }
                    addLast(
                            TargetToProxyHandler(
                                    proxyChannelContext = proxyChannelContext,
                                    agentMessage = agentMessage,
                                    dataTransferExecutorGroup = dataTransferExecutorGroup,
                                    proxyConfiguration = proxyConfiguration
                            ))
                    addLast(resourceClearHandler)
                }
            }
        })
        logger.debug("Begin to connect ${targetAddress}:${targetPort}, message id=${agentMessage.body.id}")
        this.targetBootstrap.connect(targetAddress, targetPort).addListener _1stListener@{ _1stFuture ->
            if (_1stFuture.isSuccess) {
                onTargetConnectSuccess(targetAddress, targetPort, proxyChannelContext)
                return@_1stListener
            }
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

    private fun onTargetConnectSuccess(targetAddress: String, targetPort: Int, proxyChannelContext: ChannelHandlerContext) {
        logger.debug { "Success connect to $targetAddress:$targetPort." }
        if (proxyChannelContext.pipeline()[SetupTargetConnectionHandler::class.java] != null) {
            logger.debug {
                "Remove ${SetupTargetConnectionHandler::class.java} because of connection to $targetAddress:$targetPort built success already, connect will " +
                        "never happen again in the channel lifecycle."
            }
            proxyChannelContext.pipeline().remove(this)
        }
    }

    override fun channelReadComplete(proxyContext: ChannelHandlerContext) {
        proxyContext.flush()
    }
}
