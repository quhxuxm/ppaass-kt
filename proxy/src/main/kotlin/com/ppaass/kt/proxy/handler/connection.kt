package com.ppaass.kt.proxy.handler

import com.ppaass.kt.common.exception.PpaassException
import com.ppaass.kt.common.message.*
import com.ppaass.kt.common.netty.handler.ResourceClearHandler
import com.ppaass.kt.proxy.ProxyConfiguration
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.ByteBuf
import io.netty.buffer.PooledByteBufAllocator
import io.netty.buffer.Unpooled
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.util.concurrent.EventExecutorGroup
import org.slf4j.LoggerFactory

private class TransferDataFromTargetToProxyHandler(private val proxyChannel: Channel,
                                                   private val targetChannel: Channel,
                                                   private val secureToken: String, private val messageId: String,
                                                   private val targetAddress: String, private val targetPort: Int,
                                                   private val proxyConfiguration: ProxyConfiguration) :
        SimpleChannelInboundHandler<ByteBuf>() {
    companion object {
        private val logger = LoggerFactory.getLogger(TransferDataFromTargetToProxyHandler::class.java)
    }

    override fun channelRead0(targetChannelContext: ChannelHandlerContext, targetMessage: ByteBuf) {
        val originalDataByteArray = ByteArray(targetMessage.readableBytes())
        targetMessage.readBytes(originalDataByteArray)
        val proxyMessage = ProxyMessage(secureToken, MessageBodyEncryptionType.random(),
                proxyMessageBody(ProxyMessageBodyType.OK, messageId) {
                    targetAddress = this@TransferDataFromTargetToProxyHandler.targetAddress
                    targetPort = this@TransferDataFromTargetToProxyHandler.targetPort
                    originalData = originalDataByteArray
                })
        logger.debug("Transfer data from target to proxy server, proxyMessage:\n{}\n", proxyMessage)
        this.proxyChannel.writeAndFlush(proxyMessage).addListener(ChannelFutureListener {
            if (!it.isSuccess) {
                logger.error("Fail to transfer data from target to proxy server.", it.cause())
                throw PpaassException("Fail to transfer data from target to proxy server.")
            }
            if (!proxyConfiguration.autoRead) {
                targetChannel.read()
            }
        })
    }

    override fun channelReadComplete(targetChannelContext: ChannelHandlerContext) {
        targetChannelContext.flush()
    }
}

private class TransferDataFromProxyToTargetHandler(private val targetChannel: Channel,
                                                   private val proxyConfiguration: ProxyConfiguration) :
        SimpleChannelInboundHandler<AgentMessage>() {
    companion object {
        private val logger = LoggerFactory.getLogger(TransferDataFromProxyToTargetHandler::class.java)
    }

    override fun channelRead0(proxyContext: ChannelHandlerContext, agentMessage: AgentMessage) {
        if (AgentMessageBodyType.CONNECT === agentMessage.body.bodyType) {
            logger.debug("Incoming request is a CONNECT message.")
            if (!proxyConfiguration.autoRead) {
                targetChannel.read()
            }
            return
        }
        targetChannel.writeAndFlush(Unpooled.wrappedBuffer(agentMessage.body.originalData))
                .addListener(ChannelFutureListener {
                    if (!it.isSuccess) {
                        logger.error("Fail to transfer data from proxy to target server.", it.cause())
                        throw PpaassException("Fail to transfer data from proxy to target server.")
                    }
                    if (!proxyConfiguration.autoRead) {
                        it.channel().read()
                    }
                })
    }

    override fun channelReadComplete(proxyContext: ChannelHandlerContext) {
        proxyContext.flush()
    }
}

private class TargetDataTransferChannelInitializer(private val proxyChannel: Channel,
                                                   private val secureToken: String, private val messageId: String,
                                                   private val targetAddress: String, private val targetPort: Int,
                                                   private val businessEventExecutors: EventExecutorGroup,
                                                   private val proxyConfiguration: ProxyConfiguration) :
        ChannelInitializer<SocketChannel>() {
    override fun initChannel(targetChannel: SocketChannel) {
        with(targetChannel.pipeline()) {
            addLast(businessEventExecutors,
                    TransferDataFromTargetToProxyHandler(
                            proxyChannel = proxyChannel,
                            targetChannel = targetChannel,
                            messageId = messageId,
                            secureToken = secureToken,
                            targetAddress = targetAddress,
                            targetPort = targetPort,
                            proxyConfiguration = proxyConfiguration
                    ))
            addLast(ResourceClearHandler(targetChannel, proxyChannel))
        }
    }
}

@ChannelHandler.Sharable
internal class ProxyAndTargetConnectionHandler(private val proxyConfiguration: ProxyConfiguration) :
        SimpleChannelInboundHandler<AgentMessage>() {
    companion object {
        private val logger = LoggerFactory.getLogger(ProxyAndTargetConnectionHandler::class.java)
    }

    private val businessEventExecutors: EventExecutorGroup
    private val targetDataTransferBootstrap: Bootstrap

    init {
        this.businessEventExecutors = DefaultEventLoopGroup(proxyConfiguration.businessEventThreadNumber)
        this.targetDataTransferBootstrap = Bootstrap()
        with(this.targetDataTransferBootstrap) {
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
            option(ChannelOption.RCVBUF_ALLOCATOR,
                    AdaptiveRecvByteBufAllocator(proxyConfiguration.targetReceiveDataAverageBufferMinSize,
                            proxyConfiguration.targetReceiveDataAverageBufferInitialSize,
                            proxyConfiguration.targetReceiveDataAverageBufferMaxSize))
            option(ChannelOption.SO_RCVBUF, proxyConfiguration.targetSoRcvbuf)
        }
    }

    override fun channelRead0(proxyContext: ChannelHandlerContext, agentMessage: AgentMessage) {
        this.targetDataTransferBootstrap.handler(
                TargetDataTransferChannelInitializer(
                        proxyChannel = proxyContext.channel(),
                        messageId = agentMessage.body.id,
                        secureToken = agentMessage.secureToken,
                        targetAddress = agentMessage.body.targetAddress ?: "",
                        targetPort = agentMessage.body.targetPort ?: -1,
                        businessEventExecutors = this.businessEventExecutors,
                        proxyConfiguration = proxyConfiguration
                ))
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
        val targetChannelConnectFuture = this.targetDataTransferBootstrap.connect(targetAddress, targetPort).sync()
        if (!targetChannelConnectFuture.isSuccess) {
            val failProxyMessage =
                    ProxyMessage(agentMessage.secureToken, MessageBodyEncryptionType.random(),
                            proxyMessageBody(ProxyMessageBodyType.CONNECT_FAIL, agentMessage.body.id) {
                                this.targetAddress = agentMessage.body.targetAddress
                                this.targetPort = agentMessage.body.targetPort
                            })
            proxyContext.channel().writeAndFlush(failProxyMessage).addListener(ChannelFutureListener.CLOSE)
            logger.error("Fail to connect to: {}:{}", targetAddress, targetPort)
            return
        }
        val targetChannel = targetChannelConnectFuture.channel()
        with(proxyContext.pipeline()) {
            addLast(businessEventExecutors,
                    TransferDataFromProxyToTargetHandler(
                            targetChannel = targetChannel,
                            proxyConfiguration = proxyConfiguration))
            addLast(ResourceClearHandler(targetChannel, proxyContext.channel()))
            remove(this@ProxyAndTargetConnectionHandler)
        }
        if (!proxyConfiguration.autoRead) {
            targetChannel.read()
        }
        proxyContext.fireChannelRead(agentMessage)
    }

    override fun channelReadComplete(proxyContext: ChannelHandlerContext) {
        proxyContext.flush()
    }
}