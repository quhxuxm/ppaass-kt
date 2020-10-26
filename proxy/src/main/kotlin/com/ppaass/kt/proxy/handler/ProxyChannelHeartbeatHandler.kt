package com.ppaass.kt.proxy.handler

import com.ppaass.kt.common.protocol.MessageBodyEncryptionType
import com.ppaass.kt.common.protocol.ProxyMessage
import com.ppaass.kt.common.protocol.ProxyMessageBody
import com.ppaass.kt.common.protocol.ProxyMessageBodyType
import com.ppaass.kt.common.protocol.generateUid
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.handler.timeout.IdleState
import io.netty.handler.timeout.IdleStateEvent
import mu.KotlinLogging
import org.springframework.stereotype.Service

@ChannelHandler.Sharable
@Service
internal class ProxyChannelHeartbeatHandler : ChannelInboundHandlerAdapter() {
    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    override fun userEventTriggered(proxyChannelContext: ChannelHandlerContext, evt: Any) {
        if (evt !is IdleStateEvent) {
            logger.debug { "Ignore the event because it is not a idle event: $evt" }
            super.userEventTriggered(proxyChannelContext, evt)
            return
        }
        if (IdleState.ALL_IDLE !== evt.state()) {
            logger.debug { "Ignore the idle event because it is not a valid status: ${evt.state()}" }
            return
        }
        logger.debug {
            "Do heartbeat on proxy channel ${
                proxyChannelContext.channel().id().asLongText()
            }."
        }
        val heartBeatMessageBody = ProxyMessageBody(ProxyMessageBodyType.HEARTBEAT, generateUid())
        val heartBeatMessage =
            ProxyMessage(generateUid(), MessageBodyEncryptionType.random(), heartBeatMessageBody)
        proxyChannelContext.writeAndFlush(heartBeatMessage).addListener {
            if (!it.isSuccess) {
                logger.error {
                    "Fail to write heartbeat message from proxy to agent, close the proxy channel. Proxy channel = ${
                        proxyChannelContext.channel().id().asLongText()
                    }"
                }
                proxyChannelContext.close()
                return@addListener
            }
            val proxyChannel = proxyChannelContext.channel()
            val targetChannel = proxyChannel.attr(TARGET_CHANNEL).get()
            if (targetChannel == null) {
                logger.error {
                    "No target channel attached to proxy channel, close the proxy channel. Proxy channel = ${
                        proxyChannelContext.channel().id().asLongText()
                    }"
                }
                proxyChannelContext.close()
                return@addListener
            }
            if (targetChannel.isWritable) {
                proxyChannel.read()
            }
        }
    }
}
