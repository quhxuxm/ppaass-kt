package com.ppaass.kt.proxy.handler

import com.ppaass.kt.common.protocol.AgentMessageBodyType
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelOption
import io.netty.handler.timeout.IdleState
import io.netty.handler.timeout.IdleStateEvent
import mu.KotlinLogging
import org.springframework.stereotype.Service

@ChannelHandler.Sharable
@Service
internal class TargetChannelHeartbeatHandler : ChannelInboundHandlerAdapter() {
    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    override fun userEventTriggered(targetChannelContext: ChannelHandlerContext, evt: Any) {
        if (evt !is IdleStateEvent) {
            logger.debug { "Ignore the event because it is not a idle event: $evt" }
            super.userEventTriggered(targetChannelContext, evt)
            return
        }
        if (IdleState.ALL_IDLE !== evt.state()) {
            logger.debug { "Ignore the idle event because it is not a valid status: ${evt.state()}" }
            return
        }
        val targetChannel = targetChannelContext.channel()
        logger.debug { "Do heartbeat for target channel ${targetChannel.id().asLongText()}." }
        val proxyChannelContext = targetChannel.attr(PROXY_CHANNEL_CONTEXT).get()
        if (proxyChannelContext == null) {
            logger.info {
                "No proxy channel context attached to target channel ${
                    targetChannel.id().asLongText()
                }, close target channel on heartbeat."
            }
            targetChannelContext.close()
            return
        }
        val proxyChannel = proxyChannelContext.channel();
        if (!proxyChannel.isActive) {
            logger.info {
                "Proxy channel ${
                    proxyChannel.id().asLongText()
                } attached to target channel ${
                    targetChannel.id().asLongText()
                } is not active, close target channel on heartbeat."
            }
            targetChannelContext.close()
            return
        }
        logger.info {
            "Proxy channel ${
                proxyChannel.id().asLongText()
            } attached to target channel ${
                targetChannel.id().asLongText()
            } is active, keep alive target channel."
        }
        val agentConnectMessage = targetChannel.attr(AGENT_CONNECT_MESSAGE).get()
        if (agentConnectMessage == null) {
            return
        }
        if (agentConnectMessage.body.bodyType == AgentMessageBodyType.CONNECT_WITH_KEEP_ALIVE) {
            targetChannel.config().setOption(ChannelOption.SO_KEEPALIVE, true)
            targetChannelContext.writeAndFlush(Unpooled.EMPTY_BUFFER);
        }
        return
    }
}
