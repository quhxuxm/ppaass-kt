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

@ChannelHandler.Sharable
class AgentClientHeartbeatHandler : ChannelInboundHandlerAdapter() {
    companion object {
        private val logger: Logger =
                LoggerFactory.getLogger(AgentClientHeartbeatHandler::class.java)
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
                .addListener(ChannelFutureListener {
                    if (!it.isSuccess) {
                        val agentChannelId = it.channel().id().asLongText()
                        it.channel().close()
                        logger.error("Close agent client channel as heartbeat fail, agentChannelId={}.", agentChannelId)
                    }
                })
    }
}