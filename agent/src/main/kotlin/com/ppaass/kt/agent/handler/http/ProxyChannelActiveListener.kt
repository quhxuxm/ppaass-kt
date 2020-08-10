package com.ppaass.kt.agent.handler.http

import com.ppaass.kt.agent.configuration.AgentConfiguration
import com.ppaass.kt.common.exception.PpaassException
import com.ppaass.kt.common.netty.handler.ResourceClearHandler
import com.ppaass.kt.common.protocol.AgentMessageBodyType
import com.ppaass.kt.common.protocol.MessageBodyEncryptionType
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.util.concurrent.Future
import io.netty.util.concurrent.GenericFutureListener
import mu.KotlinLogging

internal class ProxyChannelActiveListener(private val message: Any,
                                          private val agentChannelContext: ChannelHandlerContext,
                                          private val clientChannelId: String,
                                          private val agentConfiguration: AgentConfiguration) :
        GenericFutureListener<Future<Channel>> {
    private companion object {
        private val logger = KotlinLogging.logger {}
        private val resourceClearHandler = ResourceClearHandler()
    }

    override fun operationComplete(future: Future<Channel>) {
        if (!future.isSuccess) {
            return
        }
        with(agentChannelContext.pipeline()) {
            addLast(resourceClearHandler)
        }
        val channelCacheInfo = ChannelInfoCache.getChannelInfo(clientChannelId)
        if (channelCacheInfo == null) {
            logger.error("Fail to find channel cache information, clientChannelId={}", clientChannelId)
            throw PpaassException()
        }
        writeAgentMessageToProxy(AgentMessageBodyType.DATA, this.agentConfiguration.userToken,
                channelCacheInfo.channel, channelCacheInfo.targetHost, channelCacheInfo.targetPort,
                message, clientChannelId, MessageBodyEncryptionType.random())
    }
}
