package com.ppaass.kt.agent.handler.http

import com.ppaass.kt.agent.configuration.AgentConfiguration
import com.ppaass.kt.agent.handler.http.bo.ChannelInfo
import com.ppaass.kt.common.exception.PpaassException
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

    override fun channelRead(proxyChannelContext: ChannelHandlerContext, msg: Any) {
        if (!this.agentChannel.isActive) {
            proxyChannelContext.close()
            ChannelInfoCache.removeChannelInfo(clientChannelId)
            this.agentChannel.close()
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
