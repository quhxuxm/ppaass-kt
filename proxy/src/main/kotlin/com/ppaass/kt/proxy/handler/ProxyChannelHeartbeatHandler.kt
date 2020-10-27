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
import java.text.SimpleDateFormat
import java.util.*

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
        val utcDateFormat = SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
        utcDateFormat.timeZone = TimeZone.getTimeZone("UTC")
        val heartBeatOriginalData = utcDateFormat.format(Date()).toByteArray(Charsets.UTF_8)
        val heartBeatMessageBody = ProxyMessageBody(ProxyMessageBodyType.HEARTBEAT, generateUid())
        heartBeatMessageBody.originalData = heartBeatOriginalData
        val heartBeatMessage =
            ProxyMessage(generateUid(), MessageBodyEncryptionType.random(), heartBeatMessageBody)
        proxyChannelContext.channel().writeAndFlush(heartBeatMessage).addListener {
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
            logger.info {
                "Send heartbeat message from proxy to agent success, proxy channel = ${
                    proxyChannel.id().asLongText()
                }, target channel = ${
                    targetChannel?.id()?.asLongText()
                }"
            }
            if (targetChannel == null) {
                logger.error {
                    "No target channel attached to proxy channel, close the proxy channel, proxy channel = ${
                        proxyChannel.id().asLongText()
                    }, target channel = ${
                        targetChannel?.id()?.asLongText()
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
