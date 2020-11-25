package com.ppaass.proxy.handler

import com.ppaass.kt.common.ProxyMessage
import com.ppaass.proxy.ProxyConfiguration
import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelFutureListener
import mu.KotlinLogging
import java.nio.channels.ClosedChannelException

internal class WriteDataToProxyListener(
    private val targetChannel: Channel,
    private val proxyConfiguration: ProxyConfiguration,
    private val proxyMessage: ProxyMessage
) : ChannelFutureListener {
    private companion object {
        private val logger = KotlinLogging.logger { }
    }

    private var failureTimes = 0

    override fun operationComplete(proxyChannelFuture: ChannelFuture) {
        val proxyChannel = proxyChannelFuture.channel()
        if (proxyChannelFuture.isSuccess) {
            if (proxyChannel.isWritable) {
                targetChannel.read()
            } else {
                proxyChannel.flush()
            }
            return
        }
        val cause = proxyChannelFuture.cause()
        if (cause is ClosedChannelException) {
            logger.error(cause) {
                "Fail to transfer data from target to proxy because of proxy channel closed, proxy channel = ${
                    proxyChannel.id().asLongText()
                },target channel = ${
                    targetChannel.id().asLongText()
                }."
            }
            targetChannel.close()
            return
        }
        if (failureTimes >=
            proxyConfiguration.targetToProxyTcpChannelRetry) {
            logger.error(cause) {
                "Fail to transfer data from target to proxy because of exception, proxy channel = ${
                    proxyChannel.id().asLongText()
                },target channel = ${
                    targetChannel.id().asLongText()
                }."
            }
            targetChannel.close()
            return
        }
        failureTimes++
        logger.error(cause) {
            "Retry write to proxy (${failureTimes}), proxy channel = ${
                proxyChannel.id().asLongText()
            }"
        }
        proxyChannel.writeAndFlush(proxyMessage)
            .addListener(this)
        return
    }
}
