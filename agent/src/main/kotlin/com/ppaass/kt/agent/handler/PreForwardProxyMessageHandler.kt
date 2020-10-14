package com.ppaass.kt.agent.handler

import com.ppaass.kt.common.exception.PpaassException
import com.ppaass.kt.common.protocol.ProxyMessage
import com.ppaass.kt.common.protocol.ProxyMessageBodyType
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.util.ReferenceCountUtil
import mu.KotlinLogging

@ChannelHandler.Sharable
internal class PreForwardProxyMessageHandler(private val agentChannelContext: ChannelHandlerContext) :
    SimpleChannelInboundHandler<ProxyMessage>(false) {
    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    override fun channelRead0(proxyChannelContext: ChannelHandlerContext, proxyMessage: ProxyMessage) {
        if (ProxyMessageBodyType.HEARTBEAT == proxyMessage.body.bodyType) {
            val originalData = proxyMessage.body.originalData
            if (originalData == null) {
                throw PpaassException()
            }
            val utcDataTimeString = String(originalData, Charsets.UTF_8)
            logger.debug("Receive heartbeat form proxy channel: {}, heartbeat time: {}",
                proxyChannelContext.channel().id().asLongText(),
                utcDataTimeString)
            ReferenceCountUtil.release(proxyMessage)
            return
        }
        if (ProxyMessageBodyType.TARGET_CHANNEL_CLOSE === proxyMessage.body.bodyType) {
            this.agentChannelContext.close()
            proxyChannelContext.close()
            return
        }
        proxyChannelContext.fireChannelRead(proxyMessage)
    }
}
