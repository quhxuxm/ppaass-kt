package com.ppaass.kt.proxy.handler

import com.ppaass.kt.common.protocol.AgentMessage
import com.ppaass.kt.common.protocol.AgentMessageBodyType
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
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
            logger.debug("Discard CONNECT_WITH_KEEP_ALIVE message from agent.")
            if (targetChannel.isWritable) {
                proxyChannel.read()
            } else {
                targetChannel.flush()
            }
            return
        }
        if (AgentMessageBodyType.CONNECT_WITHOUT_KEEP_ALIVE === agentMessage.body.bodyType) {
            logger.debug("Discard CONNECT_WITHOUT_KEEP_ALIVE message from agent.")
            if (targetChannel.isWritable) {
                proxyChannel.read()
            } else {
                targetChannel.flush()
            }
            return
        }
        targetChannel.write(Unpooled.wrappedBuffer(agentMessage.body.originalData)).addListener {
            if (targetChannel.isWritable) {
                proxyChannel.read()
            } else {
                targetChannel.flush()
            }
        }
        targetChannel.flush()
    }

    override fun channelInactive(proxyChannelContext: ChannelHandlerContext) {
        val proxyChannel = proxyChannelContext.channel();
        val targetChannelContext = proxyChannel.attr(TARGET_CHANNEL_CONTEXT).get()
        if (targetChannelContext != null) {
            targetChannelContext.close()
        }
        proxyChannel.attr(TARGET_CHANNEL_CONTEXT).set(null)
    }

    override fun channelWritabilityChanged(proxyChannelContext: ChannelHandlerContext) {
        val proxyChannel = proxyChannelContext.channel();
        val targetChannelContext = proxyChannel.attr(TARGET_CHANNEL_CONTEXT).get()
        val targetChannel = targetChannelContext.channel()
        if (proxyChannel.isWritable) {
            if (logger.isDebugEnabled) {
                logger.debug { "Recover auto read on target channel: ${targetChannel.id().asLongText()}" }
            }
            targetChannel.read()
        } else {
            proxyChannel.flush()
            targetChannel.flush()
        }
    }
}
