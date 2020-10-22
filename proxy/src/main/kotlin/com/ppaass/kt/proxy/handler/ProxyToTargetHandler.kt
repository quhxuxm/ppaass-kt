package com.ppaass.kt.proxy.handler

import com.ppaass.kt.common.protocol.AgentMessage
import com.ppaass.kt.common.protocol.AgentMessageBodyType
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOption
import io.netty.channel.SimpleChannelInboundHandler
import mu.KotlinLogging
import org.springframework.stereotype.Service

@ChannelHandler.Sharable
@Service
internal class ProxyToTargetHandler : SimpleChannelInboundHandler<AgentMessage>() {
    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    override fun channelRead0(proxyChannelContext: ChannelHandlerContext, agentMessage: AgentMessage) {
        val proxyChannel = proxyChannelContext.channel();
        val targetChannelContext = proxyChannel.attr(TARGET_CHANNEL_CONTEXT).get()
        val targetChannel = targetChannelContext.channel()
        if (AgentMessageBodyType.CONNECT_WITH_KEEP_ALIVE === agentMessage.body.bodyType) {
            targetChannel.config().setOption(ChannelOption.SO_KEEPALIVE, true)
            proxyChannel.config().setOption(ChannelOption.SO_KEEPALIVE, true)
            logger.debug("Discard CONNECT message from agent.")
            return
        }
        if (AgentMessageBodyType.CONNECT_WITHOUT_KEEP_ALIVE === agentMessage.body.bodyType) {
            targetChannel.config().setOption(ChannelOption.SO_KEEPALIVE, false)
            proxyChannel.config().setOption(ChannelOption.SO_KEEPALIVE, false)
            logger.debug("Discard CONNECT message from agent.")
            return
        }
        targetChannel.write(Unpooled.wrappedBuffer(agentMessage.body.originalData)).addListener {
            if (targetChannel.isWritable) {
                proxyChannelContext.channel().read()
            } else {
                targetChannel.flush()
            }
        }
        targetChannel.flush()
    }

    override fun channelInactive(proxyChannelContext: ChannelHandlerContext) {
        val proxyChannel = proxyChannelContext.channel();
        proxyChannel.attr(TARGET_CHANNEL_CONTEXT).set(null)
    }

    override fun channelWritabilityChanged(proxyChannelContext: ChannelHandlerContext) {
        val proxyChannel = proxyChannelContext.channel();
        val targetChannelContext = proxyChannel.attr(TARGET_CHANNEL_CONTEXT).get()
        val targetChannel = targetChannelContext.channel()
        if (proxyChannelContext.channel().isWritable) {
            if (logger.isDebugEnabled) {
                logger.debug { "Recover auto read on target channel: ${targetChannel.id().asLongText()}" }
            }
            targetChannel.read()
        } else {
            proxyChannelContext.channel().flush()
            targetChannel.flush()
        }
    }
}
