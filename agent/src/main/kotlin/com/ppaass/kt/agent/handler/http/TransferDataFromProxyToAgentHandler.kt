package com.ppaass.kt.agent.handler.http

import com.ppaass.kt.agent.configuration.AgentConfiguration
import com.ppaass.kt.agent.handler.http.bo.ChannelInfo
import com.ppaass.kt.common.exception.PpaassException
import com.ppaass.kt.common.protocol.AgentMessageBodyType
import com.ppaass.kt.common.protocol.MessageBodyEncryptionType
import io.netty.channel.*
import io.netty.util.concurrent.Promise
import mu.KotlinLogging

internal class TransferDataFromProxyToAgentHandler(private val agentChannel: Channel, private val targetHost: String,
                                                   private val targetPort: Int,
                                                   private val clientChannelId: String,
                                                   private val agentConfiguration: AgentConfiguration,
                                                   private val proxyChannelActivePromise: Promise<Channel>) :
        ChannelInboundHandlerAdapter() {
    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    override fun channelActive(proxyChannelContext: ChannelHandlerContext) {
        val channelCacheInfo =
                ChannelInfo(
                        clientChannelId = clientChannelId,
                        agentChannel = agentChannel,
                        proxyChannel = proxyChannelContext.channel(),
                        targetHost = this.targetHost,
                        targetPort = this.targetPort)
        ChannelInfoCache.saveChannelInfo(clientChannelId, channelCacheInfo)
        writeAgentMessageToProxy(AgentMessageBodyType.CONNECT, agentConfiguration.userToken,
                proxyChannelContext.channel(), channelCacheInfo.targetHost,
                channelCacheInfo.targetPort, null,
                clientChannelId, MessageBodyEncryptionType.random())
                .addListener(ChannelFutureListener { connectCommandFuture: ChannelFuture ->
                    if (!connectCommandFuture.isSuccess) {
                        proxyChannelActivePromise.setFailure(connectCommandFuture.cause())
                        ChannelInfoCache.removeChannelInfo(clientChannelId)
                        proxyChannelContext.close()
                        logger.error(
                                "Fail to send connect message from agent to proxy, clientChannelId={}, targetHost={}, targetPort={}",
                                clientChannelId, channelCacheInfo.targetHost, channelCacheInfo.targetPort)
                        throw PpaassException(
                                "Fail to send connect message from agent to proxy, clientChannelId=$clientChannelId, targetHost=${channelCacheInfo.targetHost}, targetPort =${channelCacheInfo.targetPort}",
                                connectCommandFuture.cause())
                    }
                    logger.debug("Success connect to proxy, clientChannelId={}, targetHost={}, targetPort={}",
                            clientChannelId, channelCacheInfo.targetHost, channelCacheInfo.targetPort)
                    if (proxyChannelActivePromise.now == null) {
                        proxyChannelActivePromise.setSuccess(connectCommandFuture.channel())
                    }
                    channelCacheInfo.proxyConnectionActivated = true
                })
    }

    override fun channelRead(proxyChannelContext: ChannelHandlerContext, msg: Any) {
        if (!this.agentChannel.isActive) {
            proxyChannelContext.close()
            logger.error(
                    "Fail to send message from proxy to agent because of agent channel not active.")
            throw PpaassException(
                    "Fail to send message from proxy to agent because of agent channel not active.")
        }
        this.agentChannel.writeAndFlush(msg)
    }

    override fun channelInactive(proxyChannelContext: ChannelHandlerContext) {
        this.agentChannel.close()
    }

    override fun channelReadComplete(ctx: ChannelHandlerContext) {
        logger.debug(
                "Current client channel to receive the proxy response (read complete), clientChannelId={}",
                this.clientChannelId)
        this.agentChannel.flush()
    }
}
