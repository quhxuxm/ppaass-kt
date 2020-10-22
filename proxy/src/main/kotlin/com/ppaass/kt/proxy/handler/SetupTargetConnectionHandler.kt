package com.ppaass.kt.proxy.handler

import com.ppaass.kt.common.exception.PpaassException
import com.ppaass.kt.common.protocol.AgentMessage
import com.ppaass.kt.common.protocol.MessageBodyEncryptionType
import com.ppaass.kt.common.protocol.ProxyMessage
import com.ppaass.kt.common.protocol.ProxyMessageBody
import com.ppaass.kt.common.protocol.ProxyMessageBodyType
import com.ppaass.kt.common.protocol.generateUid
import io.netty.bootstrap.Bootstrap
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import mu.KotlinLogging
import org.springframework.stereotype.Service

@ChannelHandler.Sharable
@Service
internal class SetupTargetConnectionHandler(private val targetBootstrap: Bootstrap) :
    SimpleChannelInboundHandler<AgentMessage>() {
    private companion object {
        private val logger = KotlinLogging.logger {}
    }

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
        logger.debug("Begin to connect ${targetAddress}:${targetPort}, message id=${agentMessage.body.id}")
        this.targetBootstrap.connect(targetAddress, targetPort)
            .addListener((ChannelFutureListener { targetChannelFuture ->
                val targetChannel = targetChannelFuture.channel()
                val proxyChannel = proxyChannelContext.channel()
                if (!targetChannelFuture.isSuccess) {
                    logger.error("Fail connect to ${targetAddress}:${targetPort}.", targetChannelFuture.cause())
                    val proxyMessageBody = ProxyMessageBody(ProxyMessageBodyType.CONNECT_FAIL, agentMessage.body.id)
                    proxyMessageBody.targetAddress = agentMessage.body.targetAddress
                    proxyMessageBody.targetPort = agentMessage.body.targetPort
                    val failProxyMessage =
                        ProxyMessage(generateUid(), MessageBodyEncryptionType.random(), proxyMessageBody)
                    proxyChannel.writeAndFlush(failProxyMessage).addListener(ChannelFutureListener.CLOSE)
                    targetChannel.close()
                    return@ChannelFutureListener
                }
                targetChannel.attr(PROXY_CHANNEL_CONTEXT).setIfAbsent(proxyChannelContext)
                targetChannel.attr(AGENT_CONNECT_MESSAGE).setIfAbsent(agentMessage)
                targetChannel.attr(HANDLERS_TO_REMOVE_AFTER_TARGET_ACTIVE).setIfAbsent(listOf(this))
                if (proxyChannel.isWritable) {
                    targetChannel.read()
                } else {
                    proxyChannel.flush()
                }
            }))
    }
}
