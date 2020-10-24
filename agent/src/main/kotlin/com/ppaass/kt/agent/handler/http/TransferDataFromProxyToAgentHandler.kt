package com.ppaass.kt.agent.handler.http

import com.ppaass.kt.agent.configuration.AgentConfiguration
import com.ppaass.kt.agent.handler.http.bo.ChannelInfo
import com.ppaass.kt.common.protocol.AgentMessageBodyType
import com.ppaass.kt.common.protocol.MessageBodyEncryptionType
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelOption
import mu.KotlinLogging
import org.springframework.stereotype.Service

@ChannelHandler.Sharable
@Service
internal class TransferDataFromProxyToAgentHandler(
    private val agentConfiguration: AgentConfiguration) :
    ChannelInboundHandlerAdapter() {
    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    override fun channelActive(proxyChannelContext: ChannelHandlerContext) {
        val proxyChannel = proxyChannelContext.channel();
        val agentChannelContext = proxyChannel.attr(AGENT_CHANNEL_CONTEXT).get()
        val agentChannel = agentChannelContext.channel()
        val agentChannelId = agentChannel.id().asLongText()
        val httpConnectionInfo = proxyChannel.attr(HTTP_CONNECTION_INFO).get()
        val httpConnectionKeepalive = proxyChannel.attr(HTTP_CONNECTION_KEEP_ALIVE).get()
        val initOnChannelActivateCallback = proxyChannel.attr(PROXY_CHANNEL_ACTIVE_CALLBACK).get()
        val bodyType = if (httpConnectionKeepalive) {
            if (proxyChannel.isOpen) {
                proxyChannel.config().setOption(ChannelOption.SO_KEEPALIVE, true)
            }
            if (agentChannel.isOpen) {
                agentChannel.config().setOption(ChannelOption.SO_KEEPALIVE, true)
            }
            AgentMessageBodyType.CONNECT_WITH_KEEP_ALIVE
        } else {
            if (proxyChannel.isOpen) {
                proxyChannel.config().setOption(ChannelOption.SO_KEEPALIVE, false)
            }
            if (agentChannel.isOpen) {
                agentChannel.config().setOption(ChannelOption.SO_KEEPALIVE, false)
            }
            AgentMessageBodyType.CONNECT_WITHOUT_KEEP_ALIVE
        }
        writeAgentMessageToProxy(
            bodyType = bodyType,
            secureToken = agentConfiguration.userToken,
            proxyChannel = proxyChannelContext.channel(),
            host = httpConnectionInfo.host,
            port = httpConnectionInfo.port,
            input = null,
            clientChannelId = agentChannelId,
            messageBodyEncryptionType = MessageBodyEncryptionType.random()) {
            if (!it.isSuccess) {
                ChannelInfoCache.removeChannelInfo(agentChannelId)
                proxyChannelContext.close()
                agentChannel.close()
                logger.debug(
                    "Fail to send connect message from agent to proxy, clientChannelId=$agentChannelId, " +
                        "targetHost=${httpConnectionInfo.host}, targetPort =${httpConnectionInfo.port}",
                    it.cause())
                return@writeAgentMessageToProxy
            }
            val channelCacheInfo =
                ChannelInfo(
                    clientChannelId = agentChannelId,
                    agentChannel = agentChannel,
                    proxyChannel = proxyChannelContext.channel(),
                    targetHost = httpConnectionInfo.host,
                    targetPort = httpConnectionInfo.port)
            channelCacheInfo.proxyConnectionActivated = true
            ChannelInfoCache.saveChannelInfo(agentChannelId, channelCacheInfo)
            initOnChannelActivateCallback(proxyChannelContext)
        }
    }

    override fun channelRead(proxyChannelContext: ChannelHandlerContext, msg: Any) {
        val proxyChannel = proxyChannelContext.channel();
        val agentChannelContext = proxyChannel.attr(AGENT_CHANNEL_CONTEXT).get()
        val agentChannel = agentChannelContext.channel()
        val agentChannelId = agentChannel.id().asLongText()
        if (!agentChannel.isActive) {
            proxyChannelContext.close()
            agentChannel.close()
            ChannelInfoCache.removeChannelInfo(agentChannelId)
            logger.debug(
                "Fail to send message from proxy to agent because of agent channel not active.")
            return
        }
        agentChannel.writeAndFlush(msg)
    }

    override fun channelInactive(proxyChannelContext: ChannelHandlerContext) {
        val proxyChannel = proxyChannelContext.channel();
        val agentChannelContext = proxyChannel.attr(AGENT_CHANNEL_CONTEXT).get()
        val agentChannel = agentChannelContext.channel()
        agentChannel.close()
        proxyChannel.attr(AGENT_CHANNEL_CONTEXT).set(null)
    }

    override fun channelReadComplete(proxyChannelContext: ChannelHandlerContext) {
        val proxyChannel = proxyChannelContext.channel();
        val agentChannelContext = proxyChannel.attr(AGENT_CHANNEL_CONTEXT).get()
        val agentChannel = agentChannelContext.channel()
        val agentChannelId = agentChannel.id().asLongText()
        logger.debug(
            "Current client channel to receive the proxy response (read complete), clientChannelId=$agentChannelId")
        agentChannel.flush()
    }
}
