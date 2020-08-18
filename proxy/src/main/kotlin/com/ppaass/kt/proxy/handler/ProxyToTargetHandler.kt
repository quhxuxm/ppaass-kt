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
                targetChannel.read()
            }
            return
        }
        if (!targetChannel.isActive) {
            logger.error("Fail to transfer data from proxy to target server because of target channel is not active.")
            throw PpaassException("Fail to transfer data from proxy to target server because of target channel is not active.")
        }
        targetChannel.eventLoop().execute {
            targetChannel.writeAndFlush(Unpooled.wrappedBuffer(agentMessage.body.originalData))
                    .addListener(ChannelFutureListener { _1stFuture ->
                        if (!_1stFuture.isSuccess) {
                            logger.error("Fail to transfer data from proxy to target server in the 1st time, will try 2nd time.")
                            targetChannel.writeAndFlush(Unpooled.wrappedBuffer(agentMessage.body.originalData)).addListener { _2ndFuture ->
                                targetChannel.close()
                                proxyContext.close()
                                logger.error("Fail to transfer data from proxy to target server in the 2nd time, will close channel", _2ndFuture.cause())
                                throw PpaassException("Fail to transfer data from proxy to target server.")
                            }
                            return@ChannelFutureListener
                        }
                        if (!proxyConfiguration.autoRead) {
                            _1stFuture.channel().read()
                        }
                    })
        }
    }

    override fun channelReadComplete(proxyContext: ChannelHandlerContext) {
        targetChannel.flush()
    }
}
