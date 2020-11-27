package com.ppaass.proxy.handler

import com.ppaass.kt.common.EncryptionType
import com.ppaass.kt.common.Heartbeat
import com.ppaass.kt.common.JSON_OBJECT_MAPPER
import com.ppaass.kt.common.ProxyMessage
import com.ppaass.kt.common.ProxyMessageBody
import com.ppaass.kt.common.ProxyMessageBodyType
import com.ppaass.kt.common.generateUuid
import com.ppaass.proxy.ProxyConfiguration
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.handler.timeout.IdleState
import io.netty.handler.timeout.IdleStateEvent
import mu.KotlinLogging
import org.springframework.stereotype.Service
import java.util.*

/**
 * Do heartbeat from proxy to agent handler
 */
@Service
@ChannelHandler.Sharable
internal class ProxyTcpChannelHeartbeatHandler(private val proxyConfiguration: ProxyConfiguration) :
    ChannelInboundHandlerAdapter() {
    private companion object {
        private val UTC_TIME_ZONE = TimeZone.getTimeZone("UTC");
        private val logger = KotlinLogging.logger { }
    }

    override fun userEventTriggered(proxyChannelContext: ChannelHandlerContext, evt: Any) {
        if (evt !is IdleStateEvent) {
            logger.debug {
                "Skip non-idle event, proxy channel = ${
                    proxyChannelContext.channel().id()
                }"
            }
            proxyChannelContext.fireUserEventTriggered(evt)
            return
        }
        if (IdleState.ALL_IDLE != evt.state()) {
            logger.debug {
                "Skip non-ALL_IDLE event, proxy channel = ${
                    proxyChannelContext.channel().id()
                }"
            }
            proxyChannelContext.fireUserEventTriggered(evt)
            return
        }
        logger.debug {
            "ALL_IDLE event happen, proxy channel = ${
                proxyChannelContext.channel().id()
            }"
        }
        val heartbeat = Heartbeat(UUID.randomUUID().toString().replace("-", ""),
            Calendar.getInstance(UTC_TIME_ZONE).time.time)
        val proxyChannel = proxyChannelContext.channel()
        val udpConnectionInfo =
            proxyChannel.attr(UDP_CONNECTION_INFO).get()
        if (udpConnectionInfo != null) {
            return
        }
        val tcpConnectionInfo =
            proxyChannel.attr(TCP_CONNECTION_INFO).get()
        if (tcpConnectionInfo == null) {
            logger.error {
                "No target connection information attached to proxy channel, proxy channel = ${
                    proxyChannel.id().asLongText()
                }"
            }
            return
        }
        val messageBody = ProxyMessageBody(
            targetHost = tcpConnectionInfo.targetHost,
            targetPort = tcpConnectionInfo.targetPort,
            userToken = tcpConnectionInfo.userToken,
            bodyType = ProxyMessageBodyType.HEARTBEAT,
            data = JSON_OBJECT_MAPPER.writeValueAsBytes(heartbeat)
        )
        val heartbeatMessage =
            ProxyMessage(
                encryptionToken = generateUuid(),
                encryptionType = EncryptionType.choose(),
                body = messageBody
            )
        proxyChannel.writeAndFlush(heartbeatMessage)
            .addListener(ChannelFutureListener { proxyChannelFuture: ChannelFuture ->
                if (proxyChannelFuture.isSuccess) {
                    tcpConnectionInfo.heartBeatFailureTimes = 0
                    return@ChannelFutureListener
                }
                if (tcpConnectionInfo.heartBeatFailureTimes >= proxyConfiguration.proxyTcpChannelHeartbeatRetry) {
                    logger.error {
                        "Fail to do heartbeat for proxy tcp channel ${
                            tcpConnectionInfo.heartBeatFailureTimes
                        } times, close proxy channel, proxy channel = ${
                            proxyChannel.id().asLongText()
                        }"
                    }
                    proxyChannelContext.close()
                    return@ChannelFutureListener
                }
                logger.warn {
                    "Fail to do heartbeat from proxy tcp channel ${
                        tcpConnectionInfo.heartBeatFailureTimes
                    } times, proxy channel = ${
                        proxyChannel.id().asLongText()
                    }"
                }
                tcpConnectionInfo.heartBeatFailureTimes++
                return@ChannelFutureListener
            })
    }
}
