package com.ppaass.kt.proxy.handler

import com.ppaass.kt.common.exception.PpaassException
import com.ppaass.kt.common.message.*
import com.ppaass.kt.common.netty.handler.ResourceClearHandler
import com.ppaass.kt.proxy.ProxyConfiguration
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufUtil
import io.netty.buffer.PooledByteBufAllocator
import io.netty.buffer.Unpooled
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.logging.LogLevel
import io.netty.handler.logging.LoggingHandler
import io.netty.util.concurrent.EventExecutorGroup
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.*

private class TransferDataFromTargetToProxyHandler(private val proxyChannel: Channel,
                                                   private val agentMessage: AgentMessage) :
        ChannelInboundHandlerAdapter() {
    companion object {
        private val logger = LoggerFactory.getLogger(TransferDataFromTargetToProxyHandler::class.java)
    }

    override fun channelRead(targetChannelContext: ChannelHandlerContext, msg: Any) {
        val proxyMessage = ProxyMessage(agentMessage.secureToken, MessageBodyEncryptionType.random(),
                proxyMessageBody(ProxyMessageBodyType.OK, agentMessage.body.id) {
                    targetAddress = agentMessage.body.targetAddress
                    targetPort = agentMessage.body.targetPort
                    originalData = ByteBufUtil.getBytes(msg as ByteBuf)
                })
        this.proxyChannel.writeAndFlush(proxyMessage).addListener {
            if (!it.isSuccess) {
                logger.error(
                        "Fail to transfer data from target to proxy, message id=${agentMessage.body.id}, " +
                                "targetAddress=${agentMessage.body.targetAddress}, targetPort=${agentMessage.body.targetPort}")
                throw PpaassException(
                        "Fail to transfer data from target to proxy, message id=${agentMessage.body.id}, " +
                                "targetAddress=${agentMessage.body.targetAddress}, targetPort=${agentMessage.body.targetPort}")
            }
            targetChannelContext.channel().read()
        }
    }

    override fun channelReadComplete(ctx: ChannelHandlerContext) {
        this.proxyChannel.flush()
    }
}

private class TransferDataFromProxyToTargetHandler(private val targetChannel: Channel) :
        SimpleChannelInboundHandler<AgentMessage>() {
    companion object {
        private val logger = LoggerFactory.getLogger(TransferDataFromProxyToTargetHandler::class.java)
    }

    override fun channelRead0(proxyContext: ChannelHandlerContext, agentMessage: AgentMessage) {
        if (AgentMessageBodyType.CONNECT === agentMessage.body.bodyType) {
            targetChannel.read()
            return
        }
        targetChannel.writeAndFlush(Unpooled.wrappedBuffer(agentMessage.body.originalData))
                .addListener {
                    if (!it.isSuccess) {
                        logger.error(
                                "Fail to transfer data from proxy to target, message id=${agentMessage.body.id}, " +
                                        "targetAddress=${agentMessage.body.targetAddress}, targetPort=${agentMessage.body.targetPort}")
                        throw PpaassException(
                                "Fail to transfer data from proxy to target, message id=${agentMessage.body.id}, " +
                                        "targetAddress=${agentMessage.body.targetAddress}, targetPort=${agentMessage.body.targetPort}")
                    }
                    targetChannel.read()
                }
    }

    override fun channelReadComplete(proxyContext: ChannelHandlerContext) {
        targetChannel.flush()
    }
}

private class TargetDataTransferChannelInitializer(private val proxyContext: ChannelHandlerContext,
                                                   private val agentMessage: AgentMessage) :
        ChannelInitializer<SocketChannel>() {
    override fun initChannel(ch: SocketChannel) {
        TODO("Not yet implemented")
    }
}

@Service
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
        this.targetDataTransferBootstrap.apply {
            group(NioEventLoopGroup(proxyConfiguration.targetDataTransferIoEventThreadNumber))
                    .channel(NioSocketChannel::class.java)
            option(ChannelOption.CONNECT_TIMEOUT_MILLIS,
                    proxyConfiguration.targetConnectionTimeout)
            option(ChannelOption.SO_KEEPALIVE, true)
            option(ChannelOption.AUTO_CLOSE, true)
            option(ChannelOption.AUTO_READ, false)
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
        this.targetDataTransferBootstrap.handler(object : ChannelInitializer<SocketChannel>() {
            override fun initChannel(targetChannel: SocketChannel) {
                with(targetChannel.pipeline()) {
                    addLast(LoggingHandler(LogLevel.INFO))
                    addLast(businessEventExecutors,
                            TransferDataFromTargetToProxyHandler(
                                    proxyContext.channel(), agentMessage))
                    addLast(ResourceClearHandler(proxyContext.channel()))
                }
            }
        })
        val targetAddress = agentMessage.body.targetAddress
        val targetPort = agentMessage.body.targetPort
        if (targetAddress == null) {
            logger.debug("Return because of targetAddress is null, message id=${agentMessage.body.id}")
            return
        }
        if (targetPort == null) {
            logger.debug("Return because of targetPort is null, message id=${agentMessage.body.id}")
            return
        }
        logger.debug("Begin to connect ${targetAddress}:${targetPort}, message id=${agentMessage.body.id}")
        this.targetDataTransferBootstrap.connect(targetAddress, targetPort).syncUninterruptibly().addListener {
            it as ChannelFuture
            if (!it.isSuccess) {
                val failProxyMessage =
                        ProxyMessage(agentMessage.secureToken, MessageBodyEncryptionType.random(),
                                proxyMessageBody(ProxyMessageBodyType.CONNECT_FAIL, UUID.randomUUID().toString()) {
                                    this.targetAddress = targetAddress
                                    this.targetPort = targetPort
                                })
                proxyContext.channel().writeAndFlush(failProxyMessage).addListener(ChannelFutureListener.CLOSE)
                logger.error("Fail connect to ${targetAddress}:${targetPort}, message id=${agentMessage.body.id}")
                return@addListener;
            }
            logger.debug("Success connect to ${targetAddress}:${targetPort}, message id=${agentMessage.body.id}")
            val targetChannel = it.channel()
            with(proxyContext.pipeline()) {
                remove(this@ProxyAndTargetConnectionHandler)
                addLast(businessEventExecutors,
                        TransferDataFromProxyToTargetHandler(
                                targetChannel))
                addLast(ResourceClearHandler(targetChannel))
                proxyContext.fireChannelRead(agentMessage)
            }
        }
    }

    override fun channelReadComplete(proxyContext: ChannelHandlerContext) {
        proxyContext.flush()
    }
}