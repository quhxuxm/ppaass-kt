package com.ppaass.kt.proxy.handler

import com.ppaass.kt.common.exception.PpaassException
import com.ppaass.kt.common.protocol.AgentMessage
import com.ppaass.kt.common.protocol.AgentMessageBodyType
import com.ppaass.kt.proxy.ProxyConfiguration
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import mu.KotlinLogging

internal class ProxyToTargetHandler(private val targetChannel: Channel,
                                    private val proxyConfiguration: ProxyConfiguration) :
        SimpleChannelInboundHandler<AgentMessage>() {
    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    override fun channelRead0(proxyContext: ChannelHandlerContext, agentMessage: AgentMessage) {
        if (AgentMessageBodyType.CONNECT === agentMessage.body.bodyType) {
            logger.debug("Discard CONNECT message from agent.")
            if (!proxyConfiguration.autoRead) {
                targetChannel.eventLoop().execute {
                    targetChannel.read()
                }
            }
            return
        }
        if (!targetChannel.isActive) {
            logger.error("Fail to transfer data from proxy to target server because of target channel is not active.")
            throw PpaassException("Fail to transfer data from proxy to target server because of target channel is not active.")
        }

        targetChannel.writeAndFlush(Unpooled.wrappedBuffer(agentMessage.body.originalData))
                .addListener(ChannelFutureListener { targetChannelWriteFuture ->
                    if (!targetChannelWriteFuture.isSuccess) {
                        targetChannel.close()
                        proxyContext.close()
                        logger.error("Fail to transfer data from proxy to target server, target=${agentMessage.body.targetAddress}:${agentMessage.body.targetPort}",
                                targetChannelWriteFuture.cause())
                        throw PpaassException("Fail to transfer data from proxy to target server, target=${agentMessage.body.targetAddress}:${agentMessage.body.targetPort}")
                        return@ChannelFutureListener
                    }
                    if (!proxyConfiguration.autoRead) {
                        targetChannelWriteFuture.channel().eventLoop().execute {
                            targetChannelWriteFuture.channel().read()
                        }
                    }
                })

    }

    override fun channelReadComplete(proxyContext: ChannelHandlerContext) {
        targetChannel.flush()
    }
}
