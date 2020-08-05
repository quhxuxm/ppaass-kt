package com.ppaass.kt.agent.handler.common

import com.ppaass.kt.common.message.ProxyMessage
import com.ppaass.kt.common.message.ProxyMessageBodyType
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.util.ReferenceCountUtil
import org.slf4j.LoggerFactory

@ChannelHandler.Sharable
internal class DiscardProxyHeartbeatHandler() :
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
        logger.debug("Receive heartbeat form proxy channel: {}, heartbeat time: {}",
                proxyChannelContext.channel().id().asLongText(),
                utcDataTimeString)
        ReferenceCountUtil.release(proxyMessage)
    }
}