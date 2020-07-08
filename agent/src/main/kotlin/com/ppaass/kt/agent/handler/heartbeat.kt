package com.ppaass.kt.agent.handler

import com.ppaass.kt.common.message.ProxyMessage
import com.ppaass.kt.common.message.ProxyMessageBodyType
import io.netty.buffer.Unpooled
import io.netty.channel.*
import io.netty.handler.timeout.IdleState
import io.netty.handler.timeout.IdleStateEvent
import io.netty.util.ReferenceCountUtil
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@ChannelHandler.Sharable
class DiscardProxyHeartbeatHandler(private val agentChannel: Channel) : ChannelInboundHandlerAdapter() {
    companion object {
        private val logger = LoggerFactory.getLogger(DiscardProxyHeartbeatHandler::class.java)
    }

    override fun channelRead(proxyChannelContext: ChannelHandlerContext, msg: Any) {
        try {
            val proxyMessage = msg as ProxyMessage
            if (ProxyMessageBodyType.HEARTBEAT !== proxyMessage.body.bodyType) {
                super.channelRead(proxyChannelContext, msg)
                return
            }
            val utcDataTimeString =
                    String(proxyMessage.body.originalData ?: System.currentTimeMillis().toString().toByteArray())
            logger.debug("Receive heartbeat form proxy channel: {}, current agent channel: {}, heartbeat time: {}",
                    proxyChannelContext.channel().id().asLongText(), this.agentChannel.id().asLongText(),
                    utcDataTimeString)
            ReferenceCountUtil.release(msg)
        } catch (classCaseExcep: ClassCastException) {
            super.channelRead(proxyChannelContext, msg)
            return
        }
    }
}

@ChannelHandler.Sharable
class HeartbeatHandler : ChannelInboundHandlerAdapter() {
    companion object {
        private val logger: Logger =
                LoggerFactory.getLogger(HeartbeatHandler::class.java)
    }

    override fun userEventTriggered(agentContext: ChannelHandlerContext, evt: Any) {
        if (evt !is IdleStateEvent) {
            super.userEventTriggered(agentContext, evt)
            return
        }
        if (IdleState.ALL_IDLE != evt.state()) {
            return
        }
        logger.debug("Heartbeat with agent client, current channel id: {}.", agentContext.channel().id().asLongText())
        agentContext.channel().writeAndFlush(
                Unpooled.EMPTY_BUFFER)
                .addListener(ChannelFutureListener.CLOSE_ON_FAILURE)
        ReferenceCountUtil.release(evt)
    }
}