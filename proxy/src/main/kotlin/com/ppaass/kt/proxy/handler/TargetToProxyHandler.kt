package com.ppaass.kt.proxy.handler

import com.ppaass.kt.common.protocol.MessageBodyEncryptionType
import com.ppaass.kt.common.protocol.ProxyMessage
import com.ppaass.kt.common.protocol.ProxyMessageBody
import com.ppaass.kt.common.protocol.ProxyMessageBodyType
import com.ppaass.kt.common.protocol.generateUid
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.EventLoopGroup
import io.netty.channel.SimpleChannelInboundHandler
import mu.KotlinLogging
import org.springframework.stereotype.Service

@ChannelHandler.Sharable
@Service
internal class TargetToProxyHandler(
    private val dataTransferIoEventLoopGroup: EventLoopGroup,
    private val proxyToTargetHandler: ProxyToTargetHandler
) : SimpleChannelInboundHandler<ByteBuf>() {
    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    private val unWritableDataQueueMap = mapOf<ChannelHandlerContext, ArrayDeque<ProxyMessage>>()

    override fun channelActive(targetChannelContext: ChannelHandlerContext) {
        val targetChannel = targetChannelContext.channel()
        val proxyChannelContext = targetChannel.attr(PROXY_CHANNEL_CONTEXT).get()
        val agentConnectMessage = targetChannel.attr(AGENT_CONNECT_MESSAGE).get()
        val proxyChannel = proxyChannelContext.channel()
        proxyChannel.attr(TARGET_CHANNEL_CONTEXT).setIfAbsent(targetChannelContext)
        proxyChannelContext.pipeline().apply {
            if (this[SetupTargetConnectionHandler::class.java] != null) {
                remove(SetupTargetConnectionHandler::class.java)
            }
            addLast(dataTransferIoEventLoopGroup, proxyToTargetHandler)
        }
        proxyChannelContext.fireChannelRead(agentConnectMessage)
    }

    override fun channelInactive(targetChannelContext: ChannelHandlerContext) {
        val targetChannel = targetChannelContext.channel()
        val proxyChannelContext = targetChannel.attr(PROXY_CHANNEL_CONTEXT).get()
        targetChannel.attr(PROXY_CHANNEL_CONTEXT).set(null)
        val agentConnectMessage = targetChannel.attr(AGENT_CONNECT_MESSAGE).get()
        targetChannel.attr(AGENT_CONNECT_MESSAGE).set(null)
        val proxyMessageBody = ProxyMessageBody(ProxyMessageBodyType.TARGET_CHANNEL_CLOSE, generateUid())
        proxyMessageBody.targetAddress = agentConnectMessage.body.targetAddress
        proxyMessageBody.targetPort = agentConnectMessage.body.targetPort
        val proxyMessage =
            ProxyMessage(generateUid(), MessageBodyEncryptionType.random(), proxyMessageBody)
        proxyChannelContext.channel().writeAndFlush(proxyMessage).addListener(ChannelFutureListener.CLOSE)
    }

    override fun channelRead0(targetChannelContext: ChannelHandlerContext, targetMessage: ByteBuf) {
        val targetChannel = targetChannelContext.channel()
        val proxyChannelContext = targetChannel.attr(PROXY_CHANNEL_CONTEXT).get()
        val agentConnectMessage = targetChannel.attr(AGENT_CONNECT_MESSAGE).get()
        val originalDataByteArray = ByteArray(targetMessage.readableBytes())
        targetMessage.readBytes(originalDataByteArray)
        val proxyMessageBody = ProxyMessageBody(ProxyMessageBodyType.OK, generateUid())
        proxyMessageBody.targetAddress = agentConnectMessage.body.targetAddress
        proxyMessageBody.targetPort = agentConnectMessage.body.targetPort
        proxyMessageBody.originalData = originalDataByteArray
        val proxyMessage =
            ProxyMessage(generateUid(), MessageBodyEncryptionType.random(), proxyMessageBody)
        if (logger.isDebugEnabled) {
            logger.debug("Transfer data from target to proxy server, proxyMessage:\n{}\n", proxyMessage)
        }
        val unWritableDataQueue = unWritableDataQueueMap.getOrDefault(targetChannelContext, ArrayDeque())
        unWritableDataQueue.addLast(proxyMessage)
        if (!proxyChannelContext.channel().isWritable) {
            unWritableDataQueue.addLast(proxyMessage)
            return;
        }
        val proxyMessageToWrite = unWritableDataQueue.removeFirst()
        proxyChannelContext.channel().write(proxyMessageToWrite)
            .addListener {
                if (proxyChannelContext.channel().isWritable) {
                    targetChannelContext.channel().read()
                } else {
                    proxyChannelContext.channel().flush()
                }
            }
        proxyChannelContext.channel().flush()
    }

    override fun channelWritabilityChanged(targetChannelContext: ChannelHandlerContext) {
        val targetChannel = targetChannelContext.channel()
        val proxyChannelContext = targetChannel.attr(PROXY_CHANNEL_CONTEXT).get()
        if (targetChannelContext.channel().isWritable) {
            if (logger.isDebugEnabled) {
                logger.debug {
                    "Recover auto read on proxy channel: ${
                        proxyChannelContext.channel().id().asLongText()
                    }"
                }
            }
            proxyChannelContext.channel().read()
        } else {
            targetChannelContext.channel().flush()
            proxyChannelContext.channel().flush()
        }
    }
}
