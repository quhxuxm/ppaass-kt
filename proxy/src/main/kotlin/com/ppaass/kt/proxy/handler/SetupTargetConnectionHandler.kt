package com.ppaass.kt.proxy.handler

import com.ppaass.kt.common.message.*
import com.ppaass.kt.common.netty.handler.ResourceClearHandler
import com.ppaass.kt.proxy.ProxyConfiguration
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.PooledByteBufAllocator
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.util.concurrent.EventExecutorGroup
import org.slf4j.LoggerFactory

@ChannelHandler.Sharable
internal class SetupTargetConnectionHandler(private val proxyConfiguration: ProxyConfiguration) :
        SimpleChannelInboundHandler<AgentMessage>() {
    companion object {
        private val logger = LoggerFactory.getLogger(SetupTargetConnectionHandler::class.java)
    }

    private val dataTransferExecutorGroup: EventExecutorGroup
    private val targetBootstrap: Bootstrap

    init {
        this.dataTransferExecutorGroup =
                DefaultEventLoopGroup(proxyConfiguration.dataTransferHandlerExecutorGroupThreadNumber)
        this.targetBootstrap = Bootstrap()
        with(this.targetBootstrap) {
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
        }
    }

    override fun channelRead0(proxyContext: ChannelHandlerContext, agentMessage: AgentMessage) {
        this.targetBootstrap.handler(object : ChannelInitializer<SocketChannel>() {
            override fun initChannel(targetChannel: SocketChannel) {
                with(targetChannel.pipeline()) {
                    addLast(dataTransferExecutorGroup,
                            TargetToProxyHandler(
                                    proxyChannel = proxyContext.channel(),
                                    messageId = agentMessage.body.id,
                                    secureToken = agentMessage.secureToken,
                                    targetAddress = agentMessage.body.targetAddress ?: "",
                                    targetPort = agentMessage.body.targetPort ?: -1,
                                    proxyConfiguration = proxyConfiguration
                            ))
                    addLast(ResourceClearHandler(targetChannel, proxyContext.channel()))
                }
            }
        })
        val targetAddress = agentMessage.body.targetAddress
        val targetPort = agentMessage.body.targetPort
        if (targetAddress == null) {
            logger.error("Return because of targetAddress is null, message id=${agentMessage.body.id}")
            proxyContext.close()
            return
        }
        if (targetPort == null) {
            logger.error("Return because of targetPort is null, message id=${agentMessage.body.id}")
            proxyContext.close()
            return
        }
        logger.debug("Begin to connect ${targetAddress}:${targetPort}, message id=${agentMessage.body.id}")
        val targetChannelConnectFuture = this.targetBootstrap.connect(targetAddress, targetPort).sync()
        if (!targetChannelConnectFuture.isSuccess) {
            val proxyMessageBody = ProxyMessageBody(ProxyMessageBodyType.CONNECT_FAIL, agentMessage.body.id)
            proxyMessageBody.targetAddress = agentMessage.body.targetAddress
            proxyMessageBody.targetPort = agentMessage.body.targetPort
            val failProxyMessage =
                    ProxyMessage(agentMessage.secureToken, MessageBodyEncryptionType.random(), proxyMessageBody)
            proxyContext.channel().writeAndFlush(failProxyMessage).addListener(ChannelFutureListener.CLOSE)
            logger.error("Fail to connect to: {}:{}", targetAddress, targetPort)
            return
        }
        val targetChannel = targetChannelConnectFuture.channel()
        with(proxyContext.pipeline()) {
            remove(this@SetupTargetConnectionHandler)
            addLast(dataTransferExecutorGroup,
                    ProxyToTargetHandler(
                            targetChannel = targetChannel,
                            proxyConfiguration = proxyConfiguration))
            addLast(ResourceClearHandler(targetChannel, proxyContext.channel()))
        }
        proxyContext.fireChannelRead(agentMessage)
        if (!proxyConfiguration.autoRead) {
            targetChannel.read()
        }
    }

    override fun channelReadComplete(proxyContext: ChannelHandlerContext) {
        proxyContext.flush()
    }
}