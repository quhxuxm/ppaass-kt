package com.ppaass.kt.agent.handler.http

import com.ppaass.kt.agent.configuration.AgentConfiguration
import com.ppaass.kt.agent.handler.http.uitl.ChannelInfoCache
import com.ppaass.kt.agent.handler.http.uitl.HttpProxyUtil
import com.ppaass.kt.common.exception.PpaassException
import com.ppaass.kt.common.message.AgentMessageBodyType
import com.ppaass.kt.common.message.MessageBodyEncryptionType
import com.ppaass.kt.common.netty.handler.ResourceClearHandler
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.util.concurrent.Future
import io.netty.util.concurrent.GenericFutureListener
import org.slf4j.LoggerFactory

internal class HttpDataRequestPromiseListener(private val message: Any,
                                              private val agentChannelContext: ChannelHandlerContext,
                                              private val clientChannelId: String,
                                              private val agentConfiguration: AgentConfiguration) :
        GenericFutureListener<Future<Channel>> {
    companion object {
        private val logger = LoggerFactory.getLogger(HttpDataRequestPromiseListener::class.java)
    }

    override fun operationComplete(future: Future<Channel>) {
        if (!future.isSuccess) {
            return
        }
        with(agentChannelContext.pipeline()) {
            addLast(ResourceClearHandler(future.now))
        }
        val channelCacheInfo = ChannelInfoCache.getChannelInfo(clientChannelId)
        if (channelCacheInfo == null) {
            logger.error("Fail to find channel cache information, clientChannelId={}", clientChannelId)
            throw PpaassException()
        }
        HttpProxyUtil.writeToProxy(AgentMessageBodyType.DATA, this.agentConfiguration.userToken,
                channelCacheInfo.channel, channelCacheInfo.targetHost, channelCacheInfo.targetPort,
                message, clientChannelId, MessageBodyEncryptionType.random())
    }
}