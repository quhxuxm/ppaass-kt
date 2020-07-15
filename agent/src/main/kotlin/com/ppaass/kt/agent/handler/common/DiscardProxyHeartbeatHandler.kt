package com.ppaass.kt.agent.handler.common

import com.ppaass.kt.common.message.ProxyMessage
import com.ppaass.kt.common.message.ProxyMessageBodyType
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.util.ReferenceCountUtil
import org.slf4j.LoggerFactory

class DiscardProxyHeartbeatHandler(private val agentChannel: Channel) :
        SimpleChannelInboundHandler<ProxyMessage>(false) {
    companion object {
        private val logger = LoggerFactory.getLogger(DiscardProxyHeartbeatHandler::class.java)
    }

    override fun channelRead0(proxyChannelContext: ChannelHandlerContext, proxyMessage: ProxyMessage) {
        if (ProxyMessageBodyType.HEARTBEAT !== proxyMessage.body.bodyType) {
            proxyChannelContext.fireChannelRead(proxyMessage)
            return
        }
        val utcDataTimeString =
                String(proxyMessage.body.originalData ?: System.currentTimeMillis().toString().toByteArray())
        logger.debug("Receive heartbeat form proxy channel: {}, current agent channel: {}, heartbeat time: {}",
                proxyChannelContext.channel().id().asLongText(), this.agentChannel.id().asLongText(),
                utcDataTimeString)
        ReferenceCountUtil.release(proxyMessage)
    }
}