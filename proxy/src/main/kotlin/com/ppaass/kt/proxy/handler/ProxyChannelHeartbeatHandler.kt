package com.ppaass.kt.proxy.handler

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ppaass.kt.common.protocol.Heartbeat
import com.ppaass.kt.common.protocol.MessageBodyEncryptionType
import com.ppaass.kt.common.protocol.ProxyMessage
import com.ppaass.kt.common.protocol.ProxyMessageBody
import com.ppaass.kt.common.protocol.ProxyMessageBodyType
import com.ppaass.kt.common.protocol.generateUid
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.handler.timeout.IdleState
import io.netty.handler.timeout.IdleStateEvent
import mu.KotlinLogging
import java.text.SimpleDateFormat
import java.util.*

internal class ProxyChannelHeartbeatHandler :
    ChannelInboundHandlerAdapter() {
    private val objectMapper = jacksonObjectMapper()
    private var failureTimes = 0

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
        val utcDateString = utcDateFormat.format(Date())
        val heartbeat = Heartbeat(generateUid(), utcDateString)
        val heartBeatMessageBody = ProxyMessageBody(ProxyMessageBodyType.HEARTBEAT, generateUid())
        heartBeatMessageBody.originalData = objectMapper.writeValueAsBytes(heartbeat)
        val heartBeatMessage =
            ProxyMessage(generateUid(), MessageBodyEncryptionType.random(), heartBeatMessageBody)
        proxyChannelContext.channel().writeAndFlush(heartBeatMessage).addListener {
            val proxyChannel = proxyChannelContext.channel()
            val targetChannel = proxyChannel.attr(TARGET_CHANNEL).get()
            if (!it.isSuccess) {
                logger.error {
                    "Fail to send heartbeat message from proxy to agent, proxy channel = ${
                        proxyChannel.id().asLongText()
                    }, target channel = ${
                        targetChannel?.id()?.asLongText()
                    }, heartbeat id = ${
                        heartbeat.id
                    }, heartbeat time = ${
                        heartbeat.utcDateTime
                    }"
                }
                if (failureTimes >= 3) {
                    proxyChannelContext.close()
                    return@addListener
                }
                failureTimes++
                return@addListener
            }
            failureTimes = 0
            logger.info {
                "Send heartbeat message from proxy to agent success, proxy channel = ${
                    proxyChannel.id().asLongText()
                }, target channel = ${
                    targetChannel?.id()?.asLongText()
                }, heartbeat id = ${
                    heartbeat.id
                }, heartbeat time = ${
                    heartbeat.utcDateTime
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
