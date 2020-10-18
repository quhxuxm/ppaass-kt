package com.ppaass.kt.proxy.handler

import com.ppaass.kt.common.protocol.AgentMessage
import com.ppaass.kt.common.protocol.MessageBodyEncryptionType
import com.ppaass.kt.common.protocol.ProxyMessage
import com.ppaass.kt.common.protocol.ProxyMessageBody
import com.ppaass.kt.common.protocol.ProxyMessageBodyType
import com.ppaass.kt.common.protocol.generateUid
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import mu.KotlinLogging

internal class TargetToProxyHandler(
    private val proxyChannelHandlerContext: ChannelHandlerContext,
    private val agentMessage: AgentMessage) :
    SimpleChannelInboundHandler<ByteBuf>() {
    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    override fun channelActive(targetChannelContext: ChannelHandlerContext) {
        val targetChannel = targetChannelContext.channel()
        proxyChannelHandlerContext.pipeline().apply {
            if (this[SetupTargetConnectionHandler::class.java] != null) {
                remove(SetupTargetConnectionHandler::class.java)
            }
            addLast(targetChannelContext.executor(),
                ProxyToTargetHandler(
                    targetChannel = targetChannel))
        }
        proxyChannelHandlerContext.fireChannelRead(agentMessage)
    }

    override fun channelInactive(targetChannelContext: ChannelHandlerContext) {
        val proxyMessageBody = ProxyMessageBody(ProxyMessageBodyType.TARGET_CHANNEL_CLOSE, generateUid())
        proxyMessageBody.targetAddress = agentMessage.body.targetAddress
        proxyMessageBody.targetPort = agentMessage.body.targetPort
        val proxyMessage =
            ProxyMessage(generateUid(), MessageBodyEncryptionType.random(), proxyMessageBody)
        proxyChannelHandlerContext.channel().writeAndFlush(proxyMessage).addListener(ChannelFutureListener.CLOSE)
    }

    override fun channelRead0(targetChannelContext: ChannelHandlerContext, targetMessage: ByteBuf) {
        val originalDataByteArray = ByteArray(targetMessage.readableBytes())
        targetMessage.readBytes(originalDataByteArray)
        val proxyMessageBody = ProxyMessageBody(ProxyMessageBodyType.OK, generateUid())
        proxyMessageBody.targetAddress = agentMessage.body.targetAddress
        proxyMessageBody.targetPort = agentMessage.body.targetPort
        proxyMessageBody.originalData = originalDataByteArray
        val proxyMessage =
            ProxyMessage(generateUid(), MessageBodyEncryptionType.random(), proxyMessageBody)
        logger.debug("Transfer data from target to proxy server, proxyMessage:\n{}\n", proxyMessage)
        proxyChannelHandlerContext.channel().write(proxyMessage)
            .addListener {
                if (proxyChannelHandlerContext.channel().isWritable) {
                    targetChannelContext.channel().read()
                } else {
                    proxyChannelHandlerContext.flush()
                }
            }
        proxyChannelHandlerContext.flush()
    }

    override fun channelWritabilityChanged(targetChannelContext: ChannelHandlerContext) {
        if (targetChannelContext.channel().isWritable) {
            if (logger.isDebugEnabled) {
                logger.debug {
                    "Recover auto read on proxy channel: ${
                        proxyChannelHandlerContext.channel().id().asLongText()
                    }"
                }
            }
            proxyChannelHandlerContext.channel().read()
        } else {
            targetChannelContext.flush()
        }
    }
}
