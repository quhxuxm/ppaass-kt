package com.ppaass.kt.agent.handler

import com.ppaass.kt.common.message.ProxyMessage
import com.ppaass.kt.common.message.ProxyMessageBodyType
import io.netty.channel.Channel
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.util.ReferenceCountUtil
import org.slf4j.LoggerFactory

@ChannelHandler.Sharable
class DiscardHeartbeatHandler(private val agentChannel: Channel) : ChannelInboundHandlerAdapter() {
    companion object {
        private val logger = LoggerFactory.getLogger(DiscardHeartbeatHandler::class.java)
    }

    override fun channelRead(proxyChannelContext: ChannelHandlerContext, msg: Any) {
        msg as ProxyMessage
        if (ProxyMessageBodyType.HEARTBEAT === msg.body.bodyType) {
            val utcDataTimeString = String(msg.body.originalData ?: System.currentTimeMillis().toString().toByteArray())
            logger.trace("Receive heartbeat form proxy channel: {}, current agent channel: {}, heartbeat time: {}",
                    proxyChannelContext.channel().id().asLongText(), this.agentChannel.id().asLongText(),
                    utcDataTimeString)
            ReferenceCountUtil.release(msg)
            return
        }
        proxyChannelContext.fireChannelRead(msg)
    }
}