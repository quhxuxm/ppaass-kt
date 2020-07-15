package com.ppaass.kt.proxy.handler

import com.ppaass.kt.common.exception.PpaassException
import com.ppaass.kt.common.message.AgentMessage
import com.ppaass.kt.common.message.AgentMessageBodyType
import com.ppaass.kt.proxy.ProxyConfiguration
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import org.slf4j.LoggerFactory

internal class ProxyToTargetHandler(private val targetChannel: Channel,
                                    private val proxyConfiguration: ProxyConfiguration) :
        SimpleChannelInboundHandler<AgentMessage>() {
    companion object {
        private val logger = LoggerFactory.getLogger(ProxyToTargetHandler::class.java)
    }

    override fun channelRead0(proxyContext: ChannelHandlerContext, agentMessage: AgentMessage) {
        if (AgentMessageBodyType.CONNECT === agentMessage.body.bodyType) {
            logger.debug("Discard CONNECT message from agent.")
            if (!proxyConfiguration.autoRead) {
                targetChannel.read()
            }
            return
        }
        targetChannel.writeAndFlush(Unpooled.wrappedBuffer(agentMessage.body.originalData))
                .addListener(ChannelFutureListener {
                    if (!it.isSuccess) {
                        logger.error("Fail to transfer data from proxy to target server.", it.cause())
                        throw PpaassException("Fail to transfer data from proxy to target server.")
                    }
                    if (!proxyConfiguration.autoRead) {
                        it.channel().read()
                    }
                })
    }

    override fun channelReadComplete(proxyContext: ChannelHandlerContext) {
        targetChannel.flush()
    }
}