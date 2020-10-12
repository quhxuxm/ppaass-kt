package com.ppaass.kt.agent.handler.http

import com.ppaass.kt.agent.configuration.AgentConfiguration
import com.ppaass.kt.agent.handler.http.bo.ChannelInfo
import com.ppaass.kt.common.protocol.AgentMessageBodyType
import com.ppaass.kt.common.protocol.MessageBodyEncryptionType
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import mu.KotlinLogging

internal class TransferDataFromProxyToAgentHandler(private val agentChannel: Channel, private val targetHost: String,
                                                   private val targetPort: Int,
                                                   private val clientChannelId: String,
                                                   private val agentConfiguration: AgentConfiguration,
                                                   private val initOnChannelActivate: (proxyChannelContext: ChannelHandlerContext) -> Unit) :
    ChannelInboundHandlerAdapter() {
    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    override fun channelActive(proxyChannelContext: ChannelHandlerContext) {
        writeAgentMessageToProxy(
            bodyType = AgentMessageBodyType.CONNECT,
            secureToken = agentConfiguration.userToken,
            proxyChannel = proxyChannelContext.channel(),
            host = this.targetHost,
            port = this.targetPort,
            input = null,
            clientChannelId = clientChannelId,
            messageBodyEncryptionType = MessageBodyEncryptionType.random()) {
            if (!it.isSuccess) {
                ChannelInfoCache.removeChannelInfo(clientChannelId)
                proxyChannelContext.close()
                agentChannel.close()
                logger.debug(
                    "Fail to send connect message from agent to proxy, clientChannelId=$clientChannelId, " +
                        "targetHost=$targetHost, targetPort =$targetPort",
                    it.cause())
                return@writeAgentMessageToProxy
            }
            val channelCacheInfo =
                ChannelInfo(
                    clientChannelId = clientChannelId,
                    agentChannel = agentChannel,
                    proxyChannel = proxyChannelContext.channel(),
                    targetHost = this.targetHost,
                    targetPort = this.targetPort)
            channelCacheInfo.proxyConnectionActivated = true
            ChannelInfoCache.saveChannelInfo(clientChannelId, channelCacheInfo)
            this.initOnChannelActivate(proxyChannelContext)
        }
    }

    override fun channelRead(proxyChannelContext: ChannelHandlerContext, msg: Any) {
        if (!this.agentChannel.isActive) {
            proxyChannelContext.close()
            this.agentChannel.close()
            ChannelInfoCache.removeChannelInfo(clientChannelId)
            logger.debug(
                "Fail to send message from proxy to agent because of agent channel not active.")
            return
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
