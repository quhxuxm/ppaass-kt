package com.ppaass.kt.agent.handler.http

import com.ppaass.kt.agent.configuration.AgentConfiguration
import com.ppaass.kt.agent.handler.http.bo.ChannelInfo
import com.ppaass.kt.agent.handler.http.uitl.ChannelInfoCache
import com.ppaass.kt.agent.handler.http.uitl.HttpProxyUtil
import com.ppaass.kt.common.exception.PpaassException
import com.ppaass.kt.common.message.AgentMessageBodyType
import com.ppaass.kt.common.message.MessageBodyEncryptionType
import io.netty.channel.*
import io.netty.util.concurrent.Promise
import org.slf4j.LoggerFactory

internal class TransferDataFromProxyToAgentHandler(private val agentChannel: Channel, private val targetHost: String,
                                                   private val targetPort: Int,
                                                   private val clientChannelId: String,
                                                   private val agentConfiguration: AgentConfiguration,
                                                   private val proxyChannelConnectedPromise: Promise<Channel>) :
        ChannelInboundHandlerAdapter() {
    companion object {
        private val logger = LoggerFactory.getLogger(TransferDataFromProxyToAgentHandler::class.java)
    }

    override fun channelActive(proxyChannelContext: ChannelHandlerContext) {
        val channelCacheInfo =
                ChannelInfo(proxyChannelContext.channel(),
                        this.targetHost, this.targetPort)
        ChannelInfoCache.saveChannelInfo(clientChannelId, channelCacheInfo)
        HttpProxyUtil.writeToProxy(AgentMessageBodyType.CONNECT, agentConfiguration.userToken,
                channelCacheInfo.channel, channelCacheInfo.targetHost,
                channelCacheInfo.targetPort, null,
                clientChannelId, MessageBodyEncryptionType.random())
                .addListener(ChannelFutureListener { connectCommandFuture: ChannelFuture ->
                    if (!connectCommandFuture.isSuccess) {
                        proxyChannelConnectedPromise.setFailure(connectCommandFuture.cause())
                        logger.error(
                                "Fail to send connect message from agent to proxy, clientChannelId={}, targetHost={}, targetPort={}",
                                clientChannelId, channelCacheInfo.targetHost, channelCacheInfo.targetPort)
                        throw PpaassException(
                                "Fail to send connect message from agent to proxy, clientChannelId=$clientChannelId, targetHost=${channelCacheInfo.targetHost}, targetPort =${channelCacheInfo.targetPort}",
                                connectCommandFuture.cause())
                    }
                    logger.debug("Success connect to proxy, clientChannelId={}, targetHost={}, targetPort={}",
                            clientChannelId, channelCacheInfo.targetHost, channelCacheInfo.targetPort)
                    proxyChannelConnectedPromise.setSuccess(connectCommandFuture.channel())
                })
    }

    override fun channelRead(proxyChannelContext: ChannelHandlerContext, msg: Any) {
        logger.debug(
                "Current client channel to receive the proxy response (reading), clientChannelId={}",
                this.clientChannelId)
        this.agentChannel.writeAndFlush(msg)
    }

    override fun channelReadComplete(ctx: ChannelHandlerContext) {
        logger.debug(
                "Current client channel to receive the proxy response (read complete), clientChannelId={}",
                this.clientChannelId)
        this.agentChannel.flush()
    }
}