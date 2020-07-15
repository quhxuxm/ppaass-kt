package com.ppaass.kt.agent.handler.http

import com.ppaass.kt.agent.handler.http.bo.HttpConnectionInfo
import com.ppaass.kt.common.exception.PpaassException
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import org.slf4j.LoggerFactory

internal class ProxyChannelConnectedListener(private val agentChannelContext: ChannelHandlerContext,
                                             private val httpConnectionInfo: HttpConnectionInfo) :
        ChannelFutureListener {
    companion object {
        private val logger = LoggerFactory.getLogger(ProxyChannelConnectedListener::class.java)
    }

    override fun operationComplete(future: ChannelFuture) {
        if (!future.isSuccess) {
            agentChannelContext.close()
            logger.error("Fail to connect to proxy server because of exception.",
                    future.cause())
            throw PpaassException("Fail to connect to proxy server because of exception.", future.cause())
        }
        logger.debug(
                "Success connect to proxy server for target server, targetAddress={}, targetPort={}",
                httpConnectionInfo.host, httpConnectionInfo.port)
    }
}