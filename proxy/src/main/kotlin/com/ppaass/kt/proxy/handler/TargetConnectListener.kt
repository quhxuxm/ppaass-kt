package com.ppaass.kt.proxy.handler

import com.ppaass.kt.common.protocol.AgentMessage
import com.ppaass.kt.common.protocol.MessageBodyEncryptionType
import com.ppaass.kt.common.protocol.ProxyMessage
import com.ppaass.kt.common.protocol.ProxyMessageBody
import com.ppaass.kt.common.protocol.ProxyMessageBodyType
import com.ppaass.kt.common.protocol.generateUid
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import mu.KotlinLogging

internal class TargetConnectListener(
    private val targetChannelFuture: ChannelFuture,
    private val proxyChannelContext: ChannelHandlerContext,
    private val targetAddress: String,
    private val targetPort: Int,
    private val agentMessage: AgentMessage,
    private val handlersToRemove: List<ChannelHandler>) :
    ChannelFutureListener {
    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    override fun operationComplete(future: ChannelFuture?) {
        val targetChannel = targetChannelFuture.channel()
        val proxyChannel = proxyChannelContext.channel()
        if (!targetChannelFuture.isSuccess) {
            logger.error(
                "Fail connect to ${targetAddress}:${targetPort}.",
                targetChannelFuture.cause())
            val proxyMessageBody =
                ProxyMessageBody(ProxyMessageBodyType.CONNECT_FAIL, agentMessage.body.id)
            proxyMessageBody.targetAddress = agentMessage.body.targetAddress
            proxyMessageBody.targetPort = agentMessage.body.targetPort
            val failProxyMessage =
                ProxyMessage(generateUid(), MessageBodyEncryptionType.random(),
                    proxyMessageBody)
            proxyChannel.writeAndFlush(failProxyMessage)
                .addListener(ChannelFutureListener.CLOSE)
            targetChannel.close()
            return
        }
        targetChannel.attr(PROXY_CHANNEL_CONTEXT).setIfAbsent(proxyChannelContext)
        targetChannel.attr(AGENT_CONNECT_MESSAGE).setIfAbsent(agentMessage)
        targetChannel.attr(HANDLERS_TO_REMOVE_AFTER_TARGET_ACTIVE).setIfAbsent(handlersToRemove)
        if (proxyChannel.isWritable) {
            targetChannel.read()
        } else {
            proxyChannel.flush()
        }
    }
}
